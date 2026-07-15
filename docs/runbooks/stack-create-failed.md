# Runbook: Stack creation failures

- **Severity:** SEV3 (single request) / SEV2 (failure rate alert firing)
- **Alerts covered:** PlatformStackCreateFailureRate

## Symptoms

Requests land in CREATE_FAILED or FAILED_VALIDATION; `stack_create_failed` counter climbing;
users report stuck "Creating…" states.

## Triage (5 minutes)

1. Open the request in the UI → Events tab. The CFN reason is on the last event.
2. Classify:
   - **FAILED_VALIDATION** → template/manifest problem (unmapped fields, bad manifest). Not an
     incident; route to the template owner.
   - **AccessDenied** on CreateStack → the per-template exec role is missing a permission or the
     worker can't assume it. Check `sts_assume_failed` metric.
   - **Throttling / 5xx spikes** → messages retry automatically; check DLQ depth before acting.
3. `aws cloudformation describe-stack-events --stack-name <stack>` for the first FAILED event —
   CFN reports the root cause on the earliest failure, not the last.

## Remediation

- Exec role gap: update `infrastructure/iam/exec-role-<template>.yaml`, deploy, then Retry the
  request from the UI (retry re-enqueues with the same idempotency key — safe).
- Poison messages in DLQ: inspect with `aws sqs receive-message` on the DLQ; re-drive with
  `aws sqs start-message-move-task` once the cause is fixed.
- Widespread failures after a deploy: roll back via the deploy workflow (7.12) and check the
  `/platform/{env}/deployed-sha` parameter for the last good SHA.

## Escalation

Platform team on-call → #platform-alerts. Page only if failure rate > 20% for 15+ minutes.
