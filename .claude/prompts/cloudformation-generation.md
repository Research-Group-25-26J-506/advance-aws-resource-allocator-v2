# CloudFormation Generation Prompt — Competitive Code-Fixing Platform (Infra)

> Use this **after** the service builds and runs in a container locally. Feed it to your coding agent
> to generate the AWS infrastructure as CloudFormation. It targets the simplified, cost-conscious
> ECS-Fargate architecture in `ARCHITECTURE.md`.

## Your role
You are a **senior cloud/platform engineer**. You write clean, parameterized, least-privilege
CloudFormation that a teammate can deploy in order without surprises. You optimize for **low idle
cost** (this platform runs in bursts) and you never hardcode secrets or account-specific values.

## Goal
Produce CloudFormation (YAML) that provisions everything to run the NestJS modular-monolith on ECS
Fargate behind an ALB, backed by Aurora Serverless v2 (PostgreSQL), with secrets, a container
registry, the S3+CloudFront frontend, and the archive bucket. Split into **separate stacks** so they
deploy and tear down cleanly:

1. **`01-network.yaml`** — VPC, two public + two private subnets across two AZs, IGW, route tables.
   - **Cost note:** to avoid the ~$32/mo NAT Gateway, run Fargate tasks in **public** subnets with
     `assignPublicIp: ENABLED` and tight security groups, and skip NAT. Expose this as a parameter
     `UseNatGateway` (default `false`); when `false`, no NAT/EIP resources are created.
2. **`02-data.yaml`** — Aurora Serverless v2 PostgreSQL cluster + writer instance
   (`ServerlessV2ScalingConfiguration` `MinCapacity` parameter defaulting to **0** ACU so it can
   pause when idle, `MaxCapacity` e.g. 2), a DB subnet group in the private subnets, a DB security
   group, and the **Secrets Manager** secret holding DB credentials (generated, not literal). Add a
   `DeletionProtection` parameter (default `false` for dev).
3. **`03-secrets.yaml`** (or fold into data) — a Secrets Manager secret for the **GitHub App private
   key + OAuth client secret + webhook secret + JWT secret**. Values supplied at deploy time or
   rotated out-of-band; the template creates the secret container and IAM access, not the literal values.
4. **`04-ecr.yaml`** — an ECR repository for the app image (lifecycle policy to expire untagged images).
5. **`05-app.yaml`** — the runtime:
   - **ALB** (internet-facing) in public subnets, HTTPS:443 listener using an **ACM certificate**
     (parameter `CertificateArn`), HTTP:80 → redirect to 443. Target group with
     `HealthCheckPath: /readyz`, **WebSocket-friendly** settings (`stickiness` enabled or long
     `deregistration_delay`/idle timeout; set ALB idle timeout ≥ 300s).
   - **ECS cluster** (Fargate, enable Container Insights optional).
   - **Task definition**: one container from the ECR image, `cpu`/`memory` parameters (default
     256/512), port 3000, **non-root**, env vars for non-secret config, and **`secrets`** pulling
     `DATABASE_URL`/GitHub/JWT values from Secrets Manager via `ValueFrom` ARNs (use dynamic
     references — never inline secrets). CloudWatch log group with retention parameter.
   - **ECS service**: `DesiredCount` **parameter (default 1, settable to 0 to pause between events)**,
     in the chosen subnets, behind the target group, with circuit breaker + rollback enabled.
   - **Application Auto Scaling**: target-tracking on CPU (e.g. 60%), min = `DesiredCount`, max param.
   - **IAM**: a task **execution role** (pull ECR, read the specific secrets, write logs) and a task
     **role** (least-privilege: `s3:PutObject` to the archive bucket prefix, `secretsmanager:GetSecretValue`
     on the named secrets only — no wildcards).
   - Security groups: ALB SG (443/80 from internet) → app SG (3000 from ALB SG only) → DB SG (5432 from app SG only).
6. **`06-frontend.yaml`** — a **private** S3 bucket for the SPA, a CloudFront distribution with
   **Origin Access Control** (not legacy OAI), SPA error routing (403/404 → `/index.html`), and the
   separate **archive** S3 bucket (private, lifecycle to Glacier/expiry parameter). Optional Route 53
   alias behind a `DomainName` parameter.

## Hard requirements
- **Parameterize** every environment- or account-specific value: `EnvName`, `VpcCidr`, `CertificateArn`,
  `DomainName?`, `ImageUri`/`ImageTag`, `DesiredCount`, `MinAcu`, `MaxAcu`, `ContainerCpu`,
  `ContainerMemory`, `LogRetentionDays`, `UseNatGateway`, `DeletionProtection`. No literals for these.
- **No secret values in templates.** Create secret containers; reference by ARN with dynamic references.
- **Least-privilege IAM** — name the exact secret/bucket ARNs; never `Resource: "*"` for data/secrets.
- **Cross-stack wiring** via `Outputs` + `Fn::ImportValue` (export VPC/subnet/SG ids, DB endpoint &
  secret ARN, ECR uri, ALB DNS, CloudFront domain). Use a consistent export-name scheme like
  `${EnvName}-network-VpcId`.
- **Tag everything** with `EnvName`, `Project=ccp`, and a `ManagedBy=cloudformation` tag (use stack-level
  `Tags` where possible).
- Enable ECS **deployment circuit breaker** with automatic rollback.
- Health: target group health check on `/readyz`; container has no SSH, runs as non-root.

## Cost-effectiveness (call these out in comments)
- Aurora Serverless v2 `MinCapacity: 0` so the DB pauses when idle.
- `DesiredCount` settable to **0** to stop all Fargate charges between competitions; ALB still bills
  (~$16/mo) — note that the ALB stack can be deleted entirely between events if desired.
- `UseNatGateway: false` default keeps tasks in public subnets and avoids NAT cost; document the
  security trade-off (tasks have public IPs but only the ALB SG can reach the app port).
- Right-size: default 256 CPU / 512 MB; scale up via parameters only when load needs it.

## Deliverables
- The six YAML templates above, each self-contained and deployable in order.
- A short **`deploy.md`**: prerequisites (ACM cert, image pushed to ECR, secret values populated),
  the deploy order, and example `aws cloudformation deploy` commands per stack with sample parameters,
  plus the **migration step** (run `prisma migrate deploy` as a one-off ECS run-task or via the
  container entrypoint) and how to **pause** (`DesiredCount=0`, `MinAcu=0`) and **resume** for an event.

## Validate before finishing
- `cfn-lint` clean (or `aws cloudformation validate-template`).
- No circular imports between stacks; outputs/imports line up.
- Re-deploying is idempotent; deleting in reverse order leaves nothing orphaned (no retained ENIs/EIPs
  when `UseNatGateway=false`).
