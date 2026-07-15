#!/usr/bin/env bash
# One-time (idempotent) bootstrap apply. Usage: ./apply.sh <env> <github-org> [repo]
set -euo pipefail

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
