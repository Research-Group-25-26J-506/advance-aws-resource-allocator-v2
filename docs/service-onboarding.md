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

## Build order for the next iteration

1. `vpc-network` + `alb-attach` + `logs-to-s3` catalog templates (the ensure-exists targets)
2. Group column on requests (explicit `group` distinct from resource name) + group-aware wizard ✅ (name-keyed version live)
3. `service.yaml` reader: repo webhook/scan → diff → the change-detection actions above
4. GitHub App + CodeBuild for the build-image-on-app-change path
5. Group page pipeline visualization with inline promote
6. PROD iteration: approvals + canary + change windows
