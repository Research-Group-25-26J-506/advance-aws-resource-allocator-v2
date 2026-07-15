#!/usr/bin/env bash
# Branch protection as code (7.01). Idempotent — re-run to reconcile drift.
# Usage: GH_REPO=your-org/platform ./setup-branch-protection.sh
set -euo pipefail

REPO="${GH_REPO:?Set GH_REPO=org/repo}"

gh api -X PUT "repos/${REPO}/branches/main/protection" \
  --input - <<'JSON'
{
  "required_status_checks": {
    "strict": true,
    "contexts": [
      "frontend-lint",
      "backend-build",
      "backend-integration",
      "cfn-validate",
      "openapi-spec",
      "security-scan",
      "workflow-lint",
      "pr-validation-summary"
    ]
  },
  "enforce_admins": true,
  "required_pull_request_reviews": {
    "dismiss_stale_reviews": true,
    "require_code_owner_reviews": true,
    "required_approving_review_count": 1
  },
  "restrictions": null,
  "required_linear_history": true,
  "allow_force_pushes": false,
  "allow_deletions": false,
  "required_signatures": true
}
JSON

# Repo-level default: workflows get read-only tokens; jobs escalate explicitly.
gh api -X PUT "repos/${REPO}/actions/permissions/workflow" \
  -f default_workflow_permissions=read \
  -F can_approve_pull_request_reviews=false

echo "Branch protection + workflow permissions applied to ${REPO}."
