#!/bin/bash
# LocalStack ready-hook: create the queues/tables/buckets the local profile expects, and seed
# the template registry from ../templates so the wizard has real schemas to render.
set -euo pipefail

awslocal sqs create-queue --queue-name platform-requests-dlq-dev
DLQ_ARN=$(awslocal sqs get-queue-attributes \
  --queue-url http://localhost:4566/000000000000/platform-requests-dlq-dev \
  --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)
awslocal sqs create-queue --queue-name platform-requests-dev \
  --attributes "{\"VisibilityTimeout\":\"300\",\"ReceiveMessageWaitTimeSeconds\":\"20\",\"RedrivePolicy\":\"{\\\"deadLetterTargetArn\\\":\\\"${DLQ_ARN}\\\",\\\"maxReceiveCount\\\":\\\"5\\\"}\"}"

awslocal dynamodb create-table \
  --table-name platform-idempotency-dev \
  --attribute-definitions AttributeName=pk,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST
awslocal dynamodb update-time-to-live --table-name platform-idempotency-dev \
  --time-to-live-specification Enabled=true,AttributeName=expires_at

awslocal dynamodb create-table \
  --table-name platform-locks-dev \
  --attribute-definitions AttributeName=name,AttributeType=S \
  --key-schema AttributeName=name,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

awslocal s3 mb s3://platform-templates-local
# Seed the registry: templates/<id>/<version>/{template.yaml,schema.json,manifest.json}
for dir in /seed-templates/*/*/; do
  [ -d "$dir" ] || continue
  id=$(basename "$(dirname "$dir")")
  version=$(basename "$dir")
  for file in template.yaml schema.json manifest.json; do
    [ -f "${dir}${file}" ] && awslocal s3 cp "${dir}${file}" "s3://platform-templates-local/templates/${id}/${version}/${file}"
  done
done

echo "LocalStack seeded: queues, tables, template registry."
