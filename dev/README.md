# Local development

## Quick start

```bash
cd dev
make up                     # MySQL + LocalStack (queues/tables/bucket auto-seeded)

# Terminal 2 — API (owns Flyway migrations + V999 dev seed)
cd backend && gradle :api:bootRun --args='--spring.profiles.active=local'

# Terminal 3 — worker
cd backend && gradle :worker:bootRun --args='--spring.profiles.active=local'

# Terminal 4 — frontend (proxies /api → :8090)
cd frontend && npm install && npm run dev
```

Local ports: api **8090** (8080 is often held by Docker Desktop's wslrelay on Windows),
worker actuator **8082**, frontend **5173**. Quick checks once up:

```powershell
curl http://localhost:8090/actuator/health     # {"status":"UP"}
curl http://localhost:8090/api/v1/templates    # 6 seeded templates
```

Open http://localhost:5173. The `local` profile uses a **dev-header auth bypass**
(`X-Dev-User` / `X-Dev-Groups`, defaulting to an admin user) — it is `@Profile("local")`-gated
and a test asserts it can't ship in other profiles.

No backend running? `VITE_USE_MOCKS=true npm run dev` serves the UI against fixtures.

## Known divergences from real AWS (by design, 8.06)

- **CloudFormation is stubbed** in the `local` profile (`StubStackLauncher`): stack creation
  "completes" ~8s after initiation. LocalStack CFN is not faithful enough for real lifecycles.
- No Cognito: JWT auth is replaced by the dev-header filter.
- Observability stack (Loki/Tempo/Grafana/collector) joins this compose file with prompts 4.x;
  until then `otel.sdk.disabled=true` locally.

## Smoke checklist (Phase-1 thin slice)

1. Catalog shows the seeded `s3-bucket` template.
2. Wizard renders the S3 schema dynamically, validates with Ajv, submits with a UUIDv7
   Idempotency-Key, and redirects to the request detail page.
3. Request goes QUEUED → CREATE_IN_PROGRESS → CREATE_COMPLETE (stub) with events visible.
4. Double-submitting the wizard (retry the POST with the same key) does not create a second row.
