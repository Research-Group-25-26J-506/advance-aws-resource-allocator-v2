# Service onboarding — the PaaStry-mini model (with ECS as a first-class choice)

Modeled on Sysco's PaaStry IDP flow (`.paastry/config.yaml` → Concourse → Harbor → ArgoCD →
EKS), improved where PaaStry is weakest: **deployment type is a declared choice** — including
batch/scheduled shapes — and the runtime is **ECS Fargate** (no Kubernetes re-platforming tax).

## Onboarding = one file in the service repo

`.platform/service.yaml`:

```yaml
service: list-synchronizer
team: atlas
deployment:
  type: scheduled-task        # service | scheduled-task | job (see below)
  schedule: "rate(6 hours)"   # scheduled-task only
  cpu: "256"
  memory: "512"
build:
  dockerfile: ./Dockerfile    # or `buildpack: java` once source builds land
environments: [dev, qa]       # where it auto-provisions; stg/prod via promotion
```

## Deployment types (PaaStry equivalent → ours)

| type | PaaStry shape | Our template | Status |
| --- | --- | --- | --- |
| `service` | K8s Deployment (`deployment.type: standard`) | `ecs-service` | ✅ live |
| `scheduled-task` | K8s CronJob (List Synchronizer shape) | `ecs-scheduled-task` (EventBridge Scheduler → RunTask) | ✅ live |
| `job` | K8s Job (Asset Transfer shape) | ECS RunTask on demand | 🔜 needs trigger design (same open question PaaStry has: EventBridge event / API call / manual run button on the request page) |

Where PaaStry's onboarding pack "only shows deployment.type: standard" and batch is
undocumented, here the batch shapes are explicit templates with the same lifecycle as
everything else: requests, approvals for prod, promotion, audit, env-suffixed names.

## Capability mapping

| PaaStry capability | Here |
| --- | --- |
| Concourse auto-pipelines (buildpack/Dockerfile, scans) | GitHub Actions: build → Trivy scan → promote tag (`build-images` pattern; per-service repos reuse it as a reusable workflow) |
| Harbor registry | ECR |
| ArgoCD + central Helm manifest repo (two-repo model) | Template registry: Git (`templates/`) → validated sync → S3 (immutable versions). The platform repo *is* the manifest repo. |
| Vault (OIDC) secrets | Secrets Manager (task-def `Secrets`, e.g. DB creds) |
| Consul config metadata | SSM Parameter Store (`/platform/...`) |
| Datadog metrics/logs, Traceable | Grafana + Tempo traces + CloudWatch logs (deep-linked per request) |
| ServiceNow change ticket on prod deploys | PROD approvals inbox (IAM-backed, audited) |
| Canary rollouts | ECS deployment circuit breaker + rolling; canary via CodeDeploy is a future template option |
| Fastly Edge WAF | CloudFront (WAF WebACL attachable) |

## Onboarding sequence (mini version of the PaaStry guide)

1. Service owner adds `.platform/service.yaml` + Dockerfile to their repo.
2. CI in the service repo builds + scans + pushes the image to ECR (reusable workflow).
3. Provision through the platform: catalog → ECS Service / Scheduled Task with that image
   (today manual via the wizard; the discovery step that reads `service.yaml` and opens the
   request automatically is the next build item, alongside the GitHub App).
4. Validate in dev (request page: events, logs, traces). Promote dev → qa → stg → prod;
   prod waits in approvals.

## Open items (deliberately mirrored from the PaaStry migration analysis)

- **Job trigger model** — how on-demand jobs get invoked (EventBridge event, API, run button).
- **Source-to-image** — GitHub App + CodeBuild so the platform builds repos itself
  (today the service repo's own CI builds).
- **Java 8-era workloads** — containerization is the owner's job here too; buildpacks later.
- **`service.yaml` discovery** — webhook or scheduled scan that turns config changes into
  platform requests automatically.
