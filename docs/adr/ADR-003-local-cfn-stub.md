# ADR-003: CloudFormation is stubbed in local dev, not emulated

- **Status:** Accepted
- **Date:** 2026-07-15

## Context

LocalStack's CloudFormation emulation is not faithful for the lifecycles this platform depends
on (rollback states, stack events, drift). Local dev still needs the full request lifecycle
(QUEUED → CREATE_IN_PROGRESS → CREATE_COMPLETE) to exercise the UI and worker end to end.

## Decision

The `local` Spring profile binds `StubStackLauncher` — a fake driver that records the launch and
flips the request to CREATE_COMPLETE after ~8 seconds, standing in for both CFN and the
EventBridge status listener (3.05). Real SQS/DynamoDB/S3 still run in LocalStack.

## Consequences

- E2E lifecycle testable offline in seconds; no AWS bill.
- Local CANNOT catch CFN-specific failures (parameter type errors, IAM capability issues) —
  those surface in the dev account. Documented in dev/README.md as a known divergence.
