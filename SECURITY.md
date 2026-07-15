# Security Policy

## Reporting a vulnerability

Email the platform team security alias (see internal directory) or open a **private** GitHub
security advisory on this repository. Do not open public issues for security reports.

## Handling expectations

- Acknowledgement within 1 business day; triage within 3.
- Platform threat model lives at `docs/threat-model.md` (prompt 8.01).

## Ground rules baked into this repo

- No long-lived AWS credentials anywhere — CI uses GitHub OIDC federation only.
- Third-party GitHub Actions are pinned to full commit SHAs.
- Default workflow `GITHUB_TOKEN` permission is `contents: read`; jobs escalate explicitly.
- Template authors are semi-trusted: the renderer runs sandboxed (no filesystem/module access),
  and every template version passes the validator pipeline before publication.
- All S3 buckets: Block Public Access on, TLS-only bucket policies, SSE enabled.
