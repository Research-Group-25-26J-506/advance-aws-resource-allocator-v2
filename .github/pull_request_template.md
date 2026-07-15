## What

<!-- One-paragraph summary of the change and why. Link the issue. -->

## Checklist

- [ ] Tests added/updated at the appropriate layer (unit / integration / E2E if user-visible)
- [ ] CFN changes are cfn-lint + cfn-nag clean
- [ ] New/changed endpoints reflected in `backend/api/src/main/resources/openapi.yaml` (Spectral clean)
- [ ] New states mapped in `PlatformStatus` (frontend compiles = mapped)
- [ ] Structured logs carry `trace_id` + `request_id`; ≥1 metric for any new flow
- [ ] Audit log entry for every state-changing action
- [ ] ADR filed if an architectural decision was made (`docs/adr/`)
- [ ] Runbook added/updated if an alert was added (`docs/runbooks/`)

## Screenshots / evidence

<!-- For UI: both themes. For infra: change-set summary. For backend: test output. -->
