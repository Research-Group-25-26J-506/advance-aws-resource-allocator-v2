# Implementation status vs. prompt pack v2.1

Last updated: 2026-07-15. Legend: ✅ done (scoped per 9.2 "smallest working version") ·
🟡 partial · ⬜ not started. Build order follows `.claude/README (1).md`; phases follow
`9.2-implementation-checklist.md`.

## 2 — UI (Cloudscape)

| Prompt | Deliverable | Status | Notes |
| --- | --- | --- | --- |
| 2.01 | Global shell (AppLayout, nav, auth, code-splitting) | ✅ | Cloudscape visual refresh; light/dark + density toggles persisted; RequireRole gating |
| 2.02 | Dashboard | ✅ | KPI cards (server-side deltas), recent requests, env health 60s poll; sparklines pending real KPI history |
| 2.03 | Resource catalog | ✅ | Category+text filters compose, URL round-trip, clear-filters empty state |
| 2.04 | Dynamic wizard | ✅ | Schema-driven fields, Ajv + error mapping, sessionStorage drafts keyed by id@version, UUIDv7 idempotency, sensitive-field masking; `AttributeEditor` for array-of-object fields pending |
| 2.05 | Request detail | ✅ | SSE with polling fallback, deep-linkable tabs, type-name-to-delete modal; Logs/Trace tabs are placeholders until 4.x |
| 2.06 | ECS deployment wizard | ⬜ | Phase 3 |
| 2.07 | Approvals inbox | ✅ | Inbox UI + approve/reject endpoints; approve requeues provision |
| 2.08–2.10 | Templates admin, sync preview, sync history | ✅ | Scoped: inventory page + sync page (dry-run/apply, history, event timeline) |
| 2.11 | Audit log screen | ⬜ | audit_log table + writes exist; screen pending |
| 2.12 | Observability portal | ⬜ | Needs 4.x |
| 2.13 | Runbook viewer | ⬜ | runbooks table seeded; screen pending |
| 2.14a | PlatformStatus | ✅ | Exhaustive union + never-check, isTerminal/isInProgress, label map, unit-tested |
| 2.14b | Grafana embed component | ⬜ | Needs 4.07–4.08 |

## 3 — Backend

| Prompt | Deliverable | Status | Notes |
| --- | --- | --- | --- |
| 3.01 | Multi-module scaffold | ✅ | 10 modules, hexagonal rule enforced by ArchUnit, version catalog, graceful shutdown, layered jars; `gradle build` green |
| 3.02 | OpenAPI 3.1 | ✅ | Spectral 0 errors; Idempotency-Key, RFC 9457, cursor pagination, SSE documented; enums single-sourced |
| 3.03 | Flyway V001–V006 + V999 | ✅ | utf8mb4, JSON_VALID checks, incidents table, UUIDv7 BINARY(16) PKs |
| 3.04 | Worker orchestrator | ✅ | Long-poll, visibility heartbeat, graceful drain, MDC hygiene, retryable-vs-terminal classifier (tested); DEPLOY/TEMPLATE_SYNC/DRIFT handlers stubbed |
| 3.05 | CFN event listener Lambda | ✅ | `lambdas/cfn-event-listener` + `infrastructure/platform/events.yaml`: ordering guard, conditional update, outputs capture, on-failure DLQ + alarm |
| 3.06 | Per-template exec IAM | 🟡 | s3-bucket exec role done as the pattern; one role per remaining template to add |
| 3.07 | GitHub App integration | ⬜ | Phase 3 |
| 3.08 | Template renderer | ✅ | parameterMap + sandboxed Jinja modes, defaults merge, unmapped-field hard error, 51,200-byte limit constant; golden-file tests via unit tests |
| 3.09–3.12 | Template sync pipeline | ✅ | Scoped: lock + tarball fetcher (guards) + 5-stage validator + atomic publish; cfn-lint/nag + changeset dry-run stay in CI |
| 3.10 | Distributed lock | ✅ | DynamoDB conditional-write lock with heartbeat + AutoCloseable |
| 3.13 | Idempotency filter | ✅ | actor+endpoint-scoped keys, replay header, 422 on payload mismatch, stale-reservation recovery |
| 3.14 | Runbook registry | 🟡 | Table + seed exist; API endpoints pending |

## 4 — Observability

| Prompt | Status | Notes |
| --- | --- | --- |
| 4.01–4.02 (collector sidecar/gateway) | 🟡 | Sidecar container in task defs reads config from SSM `/platform/otel/sidecar/config`; the config itself + gateway service pending |
| 4.03 (Spring instrumentation) | 🟡 | OTel spring starter + JSON logs with trace/request MDC wired; custom spans/metrics per flow pending |
| 4.04–4.05, 4.07–4.15 | ⬜ | Loki/Tempo/AMP/Grafana/alerts — next major workstream after Phase 2 |
| 4.06 (frontend RUM) | 🟡 | traceparent injected on fetch; full OTel web SDK pending |

## 5 / 6 / 10 — Templates + Infrastructure

| Prompt | Status | Notes |
| --- | --- | --- |
| 5.01 templates repo | 🟡 | Starter lives in `templates/` in the monorepo (6 templates); split to `platform-templates` repo when 3.09 sync lands |
| 5.02 manifest schema | ✅ | draft-07, `manifestSchemaVersion`, regex-validated; parser validates in `:templatesync` |
| 5.03/5.05 sync controller/playbook | ⬜ | Phase 2 |
| 6.01 bootstrap | ✅ | cfn-lint clean; TLS-only buckets, OIDC provider, CI roles, SSM outputs |
| 6.02 network | ✅ | NatStrategy + EndpointProfile cost switches; SSM outputs |
| 6.03 data | ✅ | DbTier switch (Serverless v2 dev), RDS Proxy, rotation, SQS+DLQ, DDB tables |
| 6.04 compute | ✅ | ALB 120s idle (SSE), circuit breaker, Scale switch, ECS Exec dev-only; Fluent Bit sidecar + WAF pending with 4.x |
| 6.05 auth | ✅ | Cognito pool, 4 groups, PKCE web client, system-sync client-credentials client, no self-signup |
| 6.06 observability stack | ⬜ | With 4.07–4.09 |
| 6.07/6.08 exec roles / StackSets | 🟡/⬜ | s3-bucket exec role as pattern; cross-account is Phase 4 |
| 10.01–10.04 quickstart | ✅/🟡 | Reference quickstart copied to `infrastructure/quickstart/`; teardown script (10.04) pending |

**Templates in the catalog (6):** s3-bucket (stable) · sqs-queue · dynamodb-table · sns-topic ·
lambda-function · rds-mysql — each with template.yaml + schema.json + manifest.json, cfn-lint
clean, seeded in V999 and the LocalStack registry.

## 7 — GitHub Actions

| Prompt | Status | Notes |
| --- | --- | --- |
| 7.01 conventions | ✅ | CODEOWNERS, PR template, protection-as-code script, SECURITY/CONTRIBUTING; commitlint hook pending |
| 7.02 OIDC | ✅ | aud pinned, prod role conditions on the protected environment, session names traceable |
| 7.03 PR validation | ✅ | Path filters + skipped=success summary job, least-privilege permissions, no secrets (fork-safe) |
| 7.04 build images | ✅ | candidate-tag → scan → promote flow, multi-arch, GHA cache; cosign/SBOM + observability image mirroring pending |
| 7.05 deploy | ✅ | dev fully wired (stacks → ECS roll → smoke → deployed-sha SSM); stg/prod jobs are gated placeholders until Phase 4 accounts exist |
| 7.06 migrations job | ⬜ | Flyway currently runs at api startup; dedicated job when zero-downtime matters |
| 7.07–7.09, 7.11–7.14 | ⬜ | E2E, sync trigger, CodeQL, environments doc, rollback, release notes — backlog |
| 7.10 dependabot | ✅ | Grouped weekly, all 4 ecosystems |

## 8 — Cross-cutting

| Prompt | Status | Notes |
| --- | --- | --- |
| 8.01 threat model | ⬜ | Ground rules captured in SECURITY.md; full STRIDE doc pending |
| 8.02 test strategy | 🟡 | Unit + ArchUnit layers in place; Testcontainers integration + Playwright E2E pending |
| 8.03 runbooks | 🟡 | stack-create-failed done; grows with each alert (4.12) |
| 8.04 ADRs | ✅ | Template + ADR-001 (SSM wiring), 002 (UUIDv7 PKs), 003 (local CFN stub) |
| 8.05 onboarding | ⬜ | dev/README covers the core loop |
| 8.06 local dev | ✅ | Compose + LocalStack seeding, Makefile with doctor, stub CFN driver, profile-gated auth bypass (test-enforced) |
| 8.07 k6 load tests | ⬜ | Phase 5 |

## Suggested next steps (in pack order)

1. **3.09–3.12 + 5.03**: template sync end-to-end (tarball fetch → validate → publish), then 2.08–2.10 admin UI.
2. **4.07–4.09**: Loki/Tempo/Grafana stacks so the Logs/Trace tabs and 2.14b go live.
3. **2.07 approvals** + approve/reject endpoints (worker already honours PENDING_APPROVAL).
4. **7.06/7.07**: migration job + Playwright E2E for the S3 golden path in CI.
