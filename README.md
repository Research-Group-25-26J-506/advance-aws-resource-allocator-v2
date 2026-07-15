# AWS Self-Service Deployment Platform

An internal platform that lets developers provision approved AWS resources and deploy ECS
workloads through a web UI — without writing CloudFormation themselves.

Built from the prompt pack in [.claude/](.claude/README%20(1).md) (v2.1). This repo is the
**platform monorepo**; resource templates live in a separate `platform-templates` repo
(a starter copy is under [templates/](templates/) until that repo is split out).

## Architecture (short version)

| Layer | Technology |
| --- | --- |
| Frontend | React 18 + TypeScript + Vite + AWS Cloudscape, served via CloudFront + ALB |
| Backend | Spring Boot 3 / Java 21 / Gradle — `api` + `worker` services on ECS Fargate |
| Data | Aurora MySQL 8, SQS + DLQ, DynamoDB (idempotency + locks), S3 (template registry) |
| Eventing | EventBridge (CFN stack events) → Lambda → Aurora |
| Auth | Cognito user pool + OIDC (JWT bearer on every API call) |
| Execution | CloudFormation with per-template execution roles; cross-account via STS |
| Observability | OpenTelemetry → OTel Collector → AMP (metrics), Loki (logs), Tempo (traces), Grafana |
| CI/CD | GitHub Actions with OIDC federation — no long-lived AWS keys |

Source-of-truth rules: **CloudFormation** = what exists · **Aurora** = what was asked for ·
**Git** = template truth · **S3** = downstream cache.

## Repository layout

```
backend/          Spring Boot multi-module Gradle project (api, worker, domain, …)
frontend/         React + Cloudscape web UI
infrastructure/   CloudFormation stacks (bootstrap, network, data, auth, compute)
templates/        Starter resource templates (seed for the platform-templates repo)
dev/              docker-compose local stack + Makefile
docs/             ADRs, runbooks
.github/          CI/CD workflows, CODEOWNERS, PR template
```

## Getting started (local)

Prereqs: Docker, JDK 21, Node 20+, pnpm (or npm).

```bash
cd dev && make up          # MySQL + LocalStack + observability stack
cd backend && ./gradlew :api:bootRun --args='--spring.profiles.active=local'
cd backend && ./gradlew :worker:bootRun --args='--spring.profiles.active=local'
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api → :8090
```

The `local` Spring profile uses a dev-header auth bypass (compiled out of prod builds) and a
stubbed CloudFormation driver — LocalStack CFN is not faithful enough for real lifecycles.

## Deploying to AWS

1. **Bootstrap once** (fresh account): `infrastructure/bootstrap/apply.sh dev` — S3 buckets, ECR,
   GitHub OIDC provider, CI roles. Everything downstream reads its wiring from SSM under
   `/platform/{env}/...`; nothing is hardcoded to an account.
2. Merge to `main` → GitHub Actions builds images and deploys stacks to `platform-dev`
   automatically, then `platform-stg`; `platform-prod` needs a manual environment approval.

Cost note: the full posture (multi-AZ Aurora, 3× NAT, 12+ endpoints) runs $400–700+/month.
All stacks default to the **dev-lite** switches (single NAT, Serverless v2 0.5 ACU, min-1 tasks);
flip the `Scale`/`DbTier`/`NatStrategy` parameters for prod.

## Conventions

- Conventional Commits, signed; PRs only (branch protection is code: `.github/setup-branch-protection.sh`).
- Every state-creating POST takes an `Idempotency-Key` (UUIDv7) header.
- Errors are RFC 9457 `application/problem+json`.
- Mandatory tags on every provisioned resource: Owner, CostCenter, Environment, Application,
  ManagedBy=Platform, PlatformRequestId.
- Template versions are immutable once PUBLISHED.
