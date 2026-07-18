# Service onboarding v2 — groups, declared infrastructure, env progression

Evolution of the PaaStry-mini model. Three pillars: **resource groups** as the unit of an
application, **declared infrastructure with ensure-exists semantics**, and **environment
progression defined in config** (non-prod first; PROD hardening next iteration).

## 1. Resource groups

- A group is the application: its services, queues, buckets, DBs — across environments.
- Provisioning always happens *into* a group (picked or created in wizard Step 1; the group
  name keys the resources and the env-suffixed physical names).
- The group page maps every member resource × environment with status, and offers
  "Provision in this group" so an application grows in place.

## 2. `.platform/service.yaml` v2 — declared infrastructure

```yaml
service: orders-api
group: pastry-plus                # the application this service belongs to
environments: [dev, qa, stg]     # where this service should exist (promotion order)

deployment:
  type: service                  # service | scheduled-task | job
  cpu: "512"
  memory: "1024"
  port: 8080
  healthCheckPath: /healthz

infrastructure:                  # DECLARED dependencies - ensure-exists semantics
  vpc: default                   # "default" = the platform VPC; or a named vpc resource
  subnets: private               # private | public (from the VPC's subnet sets)
  alb:
    attach: true                 # join the shared ALB
    path: /orders/*              # listener rule path -> this service's target group
  logs:
    destination: s3              # CloudWatch group + subscription/export to the logs bucket
    retentionDays: 30

build:
  dockerfile: ./Dockerfile
```

### Ensure-exists semantics (the core behavior)

For each declared dependency the platform resolves in order — **use → create → fail loudly**:

1. **Exists?** Look up via SSM wiring (`/platform/{env}/network/*`, ALB listener, logs bucket).
   An account typically has ONE VPC and a few public/private subnets — the default. Use them.
2. **Missing?** Provision it first from the corresponding catalog template (vpc-network,
   alb-listener-rule, log-forwarding) as a member of the same group, then continue.
3. Only then create/update the service itself.

VPC/subnets are themselves provisionable (a `vpc-network` template) for accounts that need
more than the platform default — but the default path never forces anyone to think about
networking.

### Change detection (what an update means)

| What changed in the repo | Platform action |
| --- | --- |
| `infrastructure:` / `deployment:` section | CloudFormation update of the affected stacks (infra first, then service) |
| Application code (anything else) | Build image → scan → push to ECR → new task-def revision → ECS rolls |
| `environments:` list grew | Provision the service into the newly listed env (promotion applies from the lowest existing env) |

## 3. Environment progression (non-prod focus this iteration)

- The config's `environments:` list *is* the progression definition for that service.
- Promotion moves the service to the next listed env (same config, env-suffixed names,
  env-specific wiring resolved per env).
- **Visualization**: the group page renders each service as a pipeline —
  `dev ✅ → qa ✅ → stg ⏳ → (prod — next iteration)` — the env chips in chain order are
  exactly this; upcoming: promote button inline on the chip row, and greyed chips for envs
  the config declares but that aren't provisioned yet.
- PROD stays out of scope this iteration; when it lands it inherits the approvals gate and
  adds the hardening pass (change windows, canary option).

## 4. How ECS deployment works TODAY (and where it's going)

**Today — image-in, wizard-driven.** An ECS service is a CloudFormation template
(`templates/ecs-service/`) provisioned through the normal request pipeline:

```
wizard/config params → TemplateRenderer maps them to CFN parameters
  (image, port, cpu, memory, attachAlb, albPath, forwardLogsToS3)
  + worker injects Environment
→ worker STS-assumes the exec role → CreateStack
→ CFN builds: TaskDefinition + ExecutionRole + Service (+ TargetGroup + ListenerRule
  if attachAlb) (+ Firehose + subscription if forwardLogsToS3)
→ ECS pulls the image from ECR/registry and runs it in the private app subnets
```

You give it an **image URI** and it runs. VPC, subnets, cluster, and security groups are
**resolved from SSM** (`/platform/{env}/network/*`, `/ecs-cluster`) — they must already exist
(they do: the platform stacks created them). The template never creates a VPC; it *consumes*
the platform's.

**Try it:** catalog → ECS Service → name `hello-web`, image `public.ecr.aws/nginx/nginx:1.27-alpine`,
port 80, attachAlb=true, path `/hello/*`, priority 300 → the ServiceUrl output is your live URL.

**Where it's going — config-in, git-driven (the target).** Instead of an image URI, the service
declares a **git repo**; the platform builds the image itself. The onboarding artifact becomes:

```yaml
# .platform/service.yaml in the service repo
service: orders-api
group: pastry-plus
repo: https://github.com/Research-Group-25-26J-506/orders-api   # org must match allowlist
environments: [dev, qa]                                          # visualized as a pipeline

deployment:
  type: service
  port: 8080
  healthCheckPath: /healthz

infrastructure:            # ensure-exists; the "next" preview surfaces the RESOLVED values
  vpc: default             # -> resolves to vpc-0abc... (shown before submit)
  subnets: private         # -> subnet-0a.., subnet-0b.. (shown)
  alb: { attach: true, path: /orders/* }
  logs: { destination: s3 }

build:
  dockerfile: ./Dockerfile
```

Design rules for this path:
- **Org allowlist**: `repo` must belong to an approved GitHub org (checked dynamically against a
  configurable allowlist, e.g. `Research-Group-25-26J-506`), else onboarding is rejected — no
  building arbitrary repos.
- **Config preview on "Next"**: before submit, the wizard shows the *resolved* infrastructure —
  the actual VPC id, subnet ids, ALB, exec role that will be used — so the chosen configuration
  is explicit and reviewable (your "note down the VPC and subnets" ask). Mandatory pieces
  (VPC/subnets present, task definition, exec role, ALB when `attach`) are validated at this
  step and block submit if unresolvable.
- **Build then deploy**: GitHub App webhook → CodeBuild builds the Dockerfile → pushes to ECR →
  the platform provisions/updates the ecs-service with that image. App change = rebuild+roll;
  infra change = stack update (§ change detection above).

## Source-to-image: LIVE (the platform builds images itself)

`infrastructure/platform/codebuild.yaml` deploys a CodeBuild project the platform triggers with
per-build overrides (repo URL, ref, image tag, Dockerfile). It clones the repo, `docker build`s,
and pushes to ECR — **on AWS, not on anyone's laptop**. Proven end-to-end:
`Research-Group-25-26J-506/pastry-orders-api` → CodeBuild → ECR (`pastry-orders-api-v1`) →
ecs-service on the cluster, ALB `/orders/*`, logs streaming — zero local Docker.

- **Build from repo** page + `POST /api/v1/builds` (org allowlist enforced) → StartBuild →
  status tracked (CodeBuild polled). `GET /apps` + Service Logs page tail any service's logs.
- Org allowlist: `platform.build.allowed-orgs` (default `Research-Group-25-26J-506`).
- Next: auto-deploy on build success (build → deploy the ecs-service with the produced tag in
  one flow); GitHub App webhook so a push triggers it; buildpack option (no Dockerfile).

## Revamp roadmap — full ECS + EKS + visualization

The platform now matches PaaStry's build+registry layer on AWS-native services. Remaining to
reach parity + the "Figma-board" experience:

| PaaStry | Here — status |
| --- | --- |
| Concourse auto-pipelines | CodeBuild source-to-image ✅ (webhook auto-trigger 🔜) |
| Harbor | ECR ✅ |
| Sonarqube/Veracode scans | Trivy in CI ✅; SAST 🔜 |
| ArgoCD GitOps → **EKS** | ECS deploy ✅; **EKS runtime 🔜** (see below) |
| Canary rollouts | ECS circuit breaker ✅; CodeDeploy canary 🔜 |

**EKS support (next major runtime).** Add `deployment.runtime: ecs | eks` to service.yaml.
For `eks`: a platform EKS cluster (or reuse), the build stays identical (image → ECR), and
deploy renders a Deployment+Service+Ingress (Helm/manifest) applied via a Kubernetes provider
custom resource or a Flux/ArgoCD bridge. The Job/CronJob shapes map to K8s Job/CronJob natively
(the Asset Transfer / List Synchronizer workloads land cleanly). Scheduled-task and service
templates gain an EKS variant; the request pipeline, approvals, promotion, groups all stay.

**Service topology visualization (the Figma-board view).** A React diagram per group/service
showing: repo → build → image → the deployed resources (service, task def, ALB rule, DB, queue)
× environment, as a live board (react-flow or mermaid). Nodes are clickable → the request /
logs / outputs. This makes "what is present" visible at a glance — the group env-chip rows are
the text version; the board is the visual one.

## Build order for the next iteration

1. **`vpc-network` template** — provision a VPC + public/private subnets for accounts that need
   more than the platform default (the ensure-exists fallback target).
2. **`service.yaml` reader + config preview** — parse the repo config, resolve infra from SSM,
   show the resolved VPC/subnets/roles on wizard "Next", validate mandatory pieces.
3. **Org allowlist** check on `repo`.
4. **GitHub App + CodeBuild** — build the image from the repo so `repo:` replaces `image:`.
5. Group page pipeline visualization with inline promote; reconciliation sweep for stuck requests.
6. PROD iteration: approvals + canary + change windows.

Done in the current iteration: resource groups (create → attach → cascade delete → restore),
env-suffixed names, dynamic env chain, promotion, ecs-service with ALB + logs-to-S3,
ecs-scheduled-task, rds placement + connection outputs.
