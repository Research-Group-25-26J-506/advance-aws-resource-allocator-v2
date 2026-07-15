# Contributing

## Workflow

1. Branch from `main` (`feat/…`, `fix/…`, `infra/…`). Direct pushes to `main` are rejected.
2. Conventional Commits, signed (`git config commit.gpgsign true`).
3. Open a PR — the template checklist is the definition of done (see `.claude/prompts/9-checklist`).
4. Required checks must be green: lint, tests, cfn-lint, cfn-nag, Spectral, actionlint.
5. CODEOWNERS review required; stale approvals are dismissed on new pushes; linear history.

Branch protection is applied as code: `.github/setup-branch-protection.sh` (run once by an admin,
re-run to reconcile). Clickops protection drifts and is unauditable.

## Local checks before pushing

```bash
cd backend && ./gradlew check                 # build, unit + ArchUnit, Spotless
cd frontend && npm run lint && npm run typecheck && npm test
cfn-lint infrastructure/**/*.yaml
npx @stoplight/spectral-cli lint backend/api/src/main/resources/openapi.yaml
actionlint
```

## Never edit an applied Flyway migration

Forward-fix only. Checksum mismatches break every environment at once.
