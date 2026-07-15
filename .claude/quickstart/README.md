# Platform Quickstart — plug the platform into ANY AWS account

One CloudFormation template. No prerequisites. Upload it through the console, fill in four
fields, and you get:

1. A **versioned, encrypted S3 template registry** (`platform-templates-{account}-{env}`),
   **already seeded with the contents of your `platform-templates` GitHub repo** — the seed sync
   runs *during* stack creation, so a wrong org/repo/token fails the stack immediately instead
   of surprising you later.
2. An **hourly repo→S3 sync** (EventBridge + Lambda) that keeps the bucket following the repo
   until the platform's real sync service (prompt 3.9) takes over.
3. Optionally, the **GitHub Actions OIDC provider** plus `platform-ci-deploy-{env}` and
   `platform-ci-plan-{env}` roles, so your monorepo's workflows can deploy everything else with
   zero long-lived AWS keys.
4. **SSM parameters** under `/platform/{env}/...` that every downstream stack uses to discover
   these resources — nothing is ever hardcoded (see prompt 10.3).

## Deploy — console ("drag and drop")

1. AWS Console → **CloudFormation → Create stack → With new resources**.
2. Choose **Upload a template file** → select `platform-quickstart.yaml`.
3. Stack name: `platform-quickstart-dev`.
4. Fill parameters:

| Parameter | What to put | Default |
| --- | --- | --- |
| `EnvironmentName` | dev / stg / prod | `dev` |
| `GitHubOrg` | Your GitHub org or username | — (required) |
| `TemplatesRepo` | The templates repo name | `platform-templates` |
| `TemplatesBranch` | Branch to mirror | `main` |
| `TemplatesPathPrefix` | Folder in the repo to mirror | `templates/` |
| `GitHubTokenSecretArn` | Secrets Manager ARN of a GitHub token — **leave blank for public repos** | empty |
| `EnableScheduledSync` | Keep syncing hourly | `true` |
| `SyncScheduleExpression` | Sync cadence | `rate(1 hour)` |
| `SyncNonce` | Bump this + update stack to force a re-sync now | `1` |
| `EnableGitHubOidc` | Create OIDC provider + CI roles | `true` |
| `PlatformRepo` | `org/repo` of your platform monorepo (needed if OIDC on) | empty |

5. Acknowledge the IAM capability checkbox → **Create stack**.
6. When it hits `CREATE_COMPLETE`, the **Outputs** tab shows the bucket name, the number of
   template files seeded, and the CI role ARNs.

## Deploy — CLI (one command)

```bash
aws cloudformation deploy \
  --template-file platform-quickstart.yaml \
  --stack-name platform-quickstart-dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
      EnvironmentName=dev \
      GitHubOrg=your-org \
      PlatformRepo=your-org/platform
```

## Verify

```bash
# The registry is seeded:
aws s3 ls s3://platform-templates-$(aws sts get-caller-identity --query Account --output text)-dev/ --recursive

# The wiring layer exists:
aws ssm get-parameters-by-path --path /platform/dev --recursive

# Force an immediate re-sync after pushing to the repo:
aws cloudformation deploy --template-file platform-quickstart.yaml \
  --stack-name platform-quickstart-dev --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides SyncNonce=$(date +%s) \
  --no-fail-on-empty-changeset
```

## Private repos

Create a secret first, then pass its ARN as `GitHubTokenSecretArn`:

```bash
aws secretsmanager create-secret \
  --name platform/github-templates-token \
  --secret-string '{"token":"ghp_xxxxxxxxxxxx"}'
```

A fine-grained PAT with read-only **Contents** permission on the templates repo is enough.

## Using the CI roles in GitHub Actions

```yaml
permissions:
  id-token: write
  contents: read
steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: arn:aws:iam::<ACCOUNT_ID>:role/platform-ci-deploy-dev
      aws-region: <REGION>
```

The deploy role trusts only `refs/heads/main` of `PlatformRepo`; the plan role trusts only
`pull_request` events (read-only). Both pin `aud: sts.amazonaws.com`.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| Stack fails: `no files found under prefix 'templates/'` | Wrong `TemplatesPathPrefix`, wrong branch, or empty repo — check the repo layout. |
| Stack fails: HTTP 404 during create | Repo is private and no token was given, or org/repo name typo. |
| Stack fails: HTTP 401 | Token in the secret is invalid/expired or lacks Contents read. |
| Re-sync doesn't pick up a push | The schedule is hourly — bump `SyncNonce` to sync now. |
| Can't delete the stack's bucket | Intentional: the bucket is `Retain`ed and versioned. Empty all versions + delete markers, then delete it manually (see prompt 10.4 teardown). |

## What this quickstart is NOT

The hourly Lambda mirrors files verbatim — no validation, no immutability enforcement, no DB
registration. That's the job of the platform's real 8-stage sync pipeline (prompts 3.9–3.12,
5.x). Once that's live, set `EnableScheduledSync=false` on a stack update and let the platform
own publishing. This quickstart's job is only to make a fresh account usable in five minutes.
