# AWS Self-Service Deployment Platform — Prompt Pack v2.1

Individual, standalone prompt files broken out from `aws_platform_prompt_pack_v2.docx`, enhanced and
extended with a **portable "drag-and-drop" deployment story** (Section 10 + `/quickstart`).

## How to use

1. Paste `prompts/1-shared-context/1.0-shared-platform-context.md` **once** at the top of any AI session.
2. For UI work, also paste `prompts/2-ui/2.01-cloudscape-global-context.md` once.
3. Then paste any single prompt file. Every prompt is standalone given the shared context.
4. Every file carries YAML frontmatter (`id`, `workstream`, `tool`, `depends_on`) so you can script
   ordering, or feed the frontmatter to an agent to plan a build sequence.

## Layout

| Folder | Contents |
| --- | --- |
| `prompts/1-shared-context` | Shared platform context (paste once per session) |
| `prompts/2-ui` | 13 screens + 2 reusable components (Cloudscape/React) |
| `prompts/3-backend` | Spring Boot services, workers, Lambdas, API design |
| `prompts/4-observability` | OTel, Loki, Tempo, AMP, Grafana, alerting, k6 |
| `prompts/5-template-sync` | platform-templates repo, manifest schema, sync API |
| `prompts/6-infrastructure` | CloudFormation for every stack |
| `prompts/7-github-actions` | CI, CD, OIDC, environments, security workflows |
| `prompts/8-cross-cutting` | Threat model, tests, runbooks, ADRs, local dev |
| `prompts/9-checklist` | End-to-end implementation checklist + definition of done |
| `prompts/10-portable-deploy` | **NEW** — pluggable single-CFT install for any AWS account |
| `quickstart/` | **Working reference implementation**: `platform-quickstart.yaml` (deploy via console upload — genuinely drag-and-drop) |

## What changed vs the .docx (v2.0 → v2.1)

- Every prompt converted to a standalone `.md` with frontmatter, dependency links, and a
  consistent Context / Goal / Inputs / Constraints / Outputs / Acceptance structure.
- Each prompt gained an **Enhancements** section: concrete additions (security hardening,
  operability, edge cases) that were implicit or missing in v2.0.
- New Section 10: portability. All resource names/ARNs flow through SSM Parameter Store under
  `/platform/{env}/...`, nothing is hardcoded to one account, and a single quickstart CFT
  bootstraps a fresh account **and seeds + keeps the S3 template registry in sync with the
  platform-templates GitHub repo** via a Lambda-backed custom resource + scheduled sync.

## Suggested build order

Bootstrap (10.x quickstart or 6.1) → 7.1–7.2 → 6.2–6.5 → 4.7–4.9 → 6.4 → 7.3–7.5 →
Phase 1 thin slice (3.3, 3.1, 3.2, 3.13, 4.3–4.6, 3.4, 3.5, 3.6, 3.8, 2.1–2.5) → breadth →
ECS deploy → multi-env → day-2 ops. See `prompts/9-checklist/9.2-implementation-checklist.md`.
