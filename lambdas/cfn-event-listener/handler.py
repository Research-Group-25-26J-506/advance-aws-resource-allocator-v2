"""CloudFormation event listener (3.05).

EventBridge -> this Lambda -> Aurora (via RDS Proxy). Translates "CloudFormation Stack Status
Change" events into request status transitions. Idempotent and order-safe:
- conditional UPDATE (WHERE status != new AND last event_timestamp older) handles EventBridge's
  at-least-once, unordered delivery;
- unknown stacks (not platform-owned) are logged and skipped;
- terminal success pulls stack Outputs into resource_outputs.

Failures land on the SQS on-failure destination configured in events.yaml — a dropped event
would otherwise strand a request in *_IN_PROGRESS forever.
"""

import json
import logging
import os
import uuid
from datetime import datetime, timezone
from functools import lru_cache

import boto3
import pymysql

logger = logging.getLogger()
logger.setLevel(logging.INFO)

# CFN stack status -> platform RequestStatus (statuses that don't map 1:1 are platform-internal)
STATUS_MAP = {
    "CREATE_IN_PROGRESS": "CREATE_IN_PROGRESS",
    "CREATE_COMPLETE": "CREATE_COMPLETE",
    "CREATE_FAILED": "CREATE_FAILED",
    "ROLLBACK_IN_PROGRESS": "ROLLBACK_IN_PROGRESS",
    "ROLLBACK_COMPLETE": "ROLLBACK_COMPLETE",
    "ROLLBACK_FAILED": "ROLLBACK_FAILED",
    "UPDATE_IN_PROGRESS": "UPDATE_IN_PROGRESS",
    "UPDATE_COMPLETE": "UPDATE_COMPLETE",
    "UPDATE_FAILED": "UPDATE_FAILED",
    "UPDATE_ROLLBACK_IN_PROGRESS": "UPDATE_ROLLBACK_IN_PROGRESS",
    "UPDATE_ROLLBACK_COMPLETE": "UPDATE_ROLLBACK_COMPLETE",
    "UPDATE_ROLLBACK_FAILED": "ROLLBACK_FAILED",
    "DELETE_IN_PROGRESS": "DELETE_IN_PROGRESS",
    "DELETE_COMPLETE": "DELETE_COMPLETE",
    "DELETE_FAILED": "DELETE_FAILED",
}

TERMINAL_SUCCESS = {"CREATE_COMPLETE", "UPDATE_COMPLETE"}


@lru_cache(maxsize=1)
def _db_credentials():
    secret_arn = os.environ["DB_SECRET_ARN"]
    payload = boto3.client("secretsmanager").get_secret_value(SecretId=secret_arn)
    return json.loads(payload["SecretString"])


def _connect():
    creds = _db_credentials()
    # Short-lived connections through RDS Proxy — avoids pinning, proxy does the pooling.
    return pymysql.connect(
        host=os.environ["DB_HOST"],
        user=creds["username"],
        password=creds["password"],
        database=os.environ.get("DB_NAME", "platform"),
        connect_timeout=5,
        autocommit=False,
    )


def handler(event, _context):
    detail = event.get("detail", {})
    stack_id = detail.get("stack-id")
    status_detail = detail.get("status-details", {})
    cfn_status = status_detail.get("status") or detail.get("status")
    reason = status_detail.get("status-reason") or ""
    event_time = event.get("time")  # ISO8601 from EventBridge

    if not stack_id or not cfn_status:
        logger.info("Not a stack status event, skipping: %s", json.dumps(event)[:500])
        return {"skipped": "malformed"}

    new_status = STATUS_MAP.get(cfn_status)
    if new_status is None:
        logger.info("Unmapped CFN status %s for %s — ignoring (REVIEW_IN_PROGRESS etc.)", cfn_status, stack_id)
        return {"skipped": "unmapped-status"}

    occurred_at = datetime.fromisoformat(event_time.replace("Z", "+00:00")) if event_time else datetime.now(timezone.utc)

    connection = _connect()
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT id, status FROM requests WHERE stack_id = %s FOR UPDATE", (stack_id,)
            )
            row = cursor.fetchone()
            if row is None:
                logger.info("Stack %s not owned by platform — ignoring", stack_id)
                return {"skipped": "not-platform-owned"}
            request_id, current_status = row

            if current_status == new_status:
                return {"skipped": "already-applied"}  # idempotent replay

            # Ordering guard: never apply an event older than the last one recorded for this request
            cursor.execute(
                "SELECT MAX(occurred_at) FROM request_events WHERE request_id = %s AND source = 'CLOUDFORMATION'",
                (request_id,),
            )
            last_applied = cursor.fetchone()[0]
            if last_applied is not None and last_applied.replace(tzinfo=timezone.utc) > occurred_at:
                logger.info("Out-of-order event for %s (%s) — ignoring", stack_id, cfn_status)
                return {"skipped": "out-of-order"}

            cursor.execute(
                "UPDATE requests SET status = %s, failure_reason = %s WHERE id = %s AND status != %s",
                (new_status, reason if "FAILED" in new_status else None, request_id, new_status),
            )
            cursor.execute(
                "INSERT INTO request_events (request_id, from_status, to_status, reason, source, occurred_at)"
                " VALUES (%s, %s, %s, %s, 'CLOUDFORMATION', %s)",
                (request_id, current_status, new_status, reason or cfn_status, occurred_at),
            )

            if new_status in TERMINAL_SUCCESS:
                _store_outputs(cursor, request_id, stack_id, detail.get("region") or os.environ["AWS_REGION"])

        connection.commit()
        logger.info("Request %s: %s -> %s (stack %s)", request_id.hex(), current_status, new_status, stack_id)
        return {"applied": new_status}
    except Exception:
        connection.rollback()
        raise  # let the on-failure destination capture it
    finally:
        connection.close()


def _store_outputs(cursor, request_id, stack_id, region):
    cfn = boto3.client("cloudformation", region_name=region)
    stacks = cfn.describe_stacks(StackName=stack_id)["Stacks"]
    if not stacks:
        return
    for output in stacks[0].get("Outputs", []):
        cursor.execute(
            "INSERT INTO resource_outputs (id, request_id, output_key, output_value)"
            " VALUES (%s, %s, %s, %s)"
            " ON DUPLICATE KEY UPDATE output_value = VALUES(output_value)",
            (uuid.uuid4().bytes, request_id, output["OutputKey"], output["OutputValue"]),
        )
