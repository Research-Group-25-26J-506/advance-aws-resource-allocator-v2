# ADR-001: Cross-stack wiring via SSM Parameter Store, not CloudFormation Exports

- **Status:** Accepted
- **Date:** 2026-07-15

## Context

Stacks need each other's outputs (VPC id, queue ARNs, bucket names). CloudFormation Exports
create hard deletion ordering: a stack cannot be deleted or have its export value changed while
any importer exists. The prompt pack's portability rule (1.0 v2.1) requires every artefact to
deploy into a brand-new account with only the quickstart as a precondition.

## Decision

Every stack publishes its outputs to SSM under `/platform/{env}/...` and consumers resolve them
with `{{resolve:ssm:...}}` (or SDK reads at runtime). CloudFormation Exports are banned.

## Consequences

- Stacks can be torn down and rebuilt independently; no export-lock deadlocks.
- Values resolve at deploy time, so a consumer stack must be redeployed to pick up a changed
  parameter — acceptable, and the deploy workflow always deploys in order.
- The SSM namespace is the platform's public wiring contract; parameter names are API.
