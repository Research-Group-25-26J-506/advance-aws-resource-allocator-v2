#!/usr/bin/env bash
# One-time (idempotent) bootstrap apply. Usage: ./apply.sh <env> <github-org> [repo]
# NOTE: GitHub OIDC subjects embed immutable IDs: pass org/repo as "Name@id", e.g.
#   ./apply.sh dev "Research-Group-25-26J-506@222281801" "advance-aws-resource-allocator-v2@1301328053"
# Find the ids at https://api.github.com/orgs/<org> and /repos/<org>/<repo> ("id" field). [deploy-branch]
set -euo pipefail
export MSYS_NO_PATHCONV=1   # Git Bash on Windows: stop mangling /platform/... into C:/...

ENV="${1:?Usage: apply.sh <dev|stg|prod> <github-org> [repo]}"
ORG="${2:?Usage: apply.sh <dev|stg|prod> <github-org> [repo]}"
REPO="${3:-platform}"
STACK="platform-bootstrap-${ENV}"

aws cloudformation deploy \
  --stack-name "${STACK}" \
  --template-file "$(dirname "$0")/bootstrap.yaml" \
  --capabilities CAPABILITY_NAMED_IAM \
  --no-fail-on-empty-changeset \
  --parameter-overrides \
    "EnvironmentName=${ENV}" \
    "GitHubOrg=${ORG}" \
    "GitHubRepo=${REPO}"

echo ""
echo "Bootstrap applied. SSM parameters written under /platform/${ENV}/:"
aws ssm get-parameters-by-path --path "/platform/${ENV}" --recursive \
  --query 'Parameters[].[Name,Value]' --output table
