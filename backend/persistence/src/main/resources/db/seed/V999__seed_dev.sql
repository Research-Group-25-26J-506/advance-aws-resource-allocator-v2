-- Dev/E2E seed data. Applied ONLY via the `local` profile (flyway.locations adds db/seed).
-- UUIDs are fixed so E2E tests can reference them.

INSERT INTO teams (id, name, cost_center) VALUES
  (UNHEX(REPLACE('01900000-0000-7000-8000-000000000001','-','')), 'platform-team', 'CC-0001'),
  (UNHEX(REPLACE('01900000-0000-7000-8000-000000000002','-','')), 'payments', 'CC-1234');

INSERT INTO users (id, cognito_sub, email, display_name, team_id) VALUES
  (UNHEX(REPLACE('01900000-0000-7000-8000-00000000000a','-','')), 'dev@local', 'dev@local', 'Local Dev',
   UNHEX(REPLACE('01900000-0000-7000-8000-000000000001','-','')));

INSERT INTO user_roles (user_id, role) VALUES
  (UNHEX(REPLACE('01900000-0000-7000-8000-00000000000a','-','')), 'PLATFORM_ADMIN'),
  (UNHEX(REPLACE('01900000-0000-7000-8000-00000000000a','-','')), 'USER');

INSERT INTO templates (id, display_name, description, category, maturity, application) VALUES
  ('s3-bucket', 'S3 Bucket', 'Versioned, encrypted S3 bucket with Block Public Access enforced.', 'Storage', 'stable', NULL),
  ('sqs-queue', 'SQS Queue', 'Standard SQS queue with dead-letter queue and encryption.', 'Integration', 'beta', NULL),
  ('dynamodb-table', 'DynamoDB Table', 'On-demand DynamoDB table with PITR.', 'Database', 'beta', NULL),
  ('sns-topic', 'SNS Topic', 'Encrypted SNS topic, standard or FIFO, TLS-only publishing.', 'Integration', 'beta', NULL),
  ('lambda-function', 'Lambda Function', 'Lambda scaffold with least-privilege role and log retention.', 'Compute', 'beta', NULL),
  ('rds-mysql', 'RDS MySQL', 'MySQL 8 in private data subnets with managed master password.', 'Database', 'beta', NULL);

INSERT INTO template_versions
  (id, template_id, version, commit_sha, s3_key_body, s3_key_schema, s3_key_manifest, status, published_at) VALUES
  (UNHEX(REPLACE('01900000-0000-7000-8000-0000000000b1','-','')), 's3-bucket', '1.0.0', 'deadbeefcafe',
   'templates/s3-bucket/1.0.0/template.yaml', 'templates/s3-bucket/1.0.0/schema.json',
   'templates/s3-bucket/1.0.0/manifest.json', 'PUBLISHED', NOW(6)),
  (UNHEX(REPLACE('01900000-0000-7000-8000-0000000000b2','-','')), 'sqs-queue', '0.1.0', 'deadbeefcafe',
   'templates/sqs-queue/0.1.0/template.yaml', 'templates/sqs-queue/0.1.0/schema.json',
   'templates/sqs-queue/0.1.0/manifest.json', 'PUBLISHED', NOW(6)),
  (UNHEX(REPLACE('01900000-0000-7000-8000-0000000000b3','-','')), 'dynamodb-table', '0.1.0', 'deadbeefcafe',
   'templates/dynamodb-table/0.1.0/template.yaml', 'templates/dynamodb-table/0.1.0/schema.json',
   'templates/dynamodb-table/0.1.0/manifest.json', 'PUBLISHED', NOW(6)),
  (UNHEX(REPLACE('01900000-0000-7000-8000-0000000000b4','-','')), 'sns-topic', '0.1.0', 'deadbeefcafe',
   'templates/sns-topic/0.1.0/template.yaml', 'templates/sns-topic/0.1.0/schema.json',
   'templates/sns-topic/0.1.0/manifest.json', 'PUBLISHED', NOW(6)),
  (UNHEX(REPLACE('01900000-0000-7000-8000-0000000000b5','-','')), 'lambda-function', '0.1.0', 'deadbeefcafe',
   'templates/lambda-function/0.1.0/template.yaml', 'templates/lambda-function/0.1.0/schema.json',
   'templates/lambda-function/0.1.0/manifest.json', 'PUBLISHED', NOW(6)),
  (UNHEX(REPLACE('01900000-0000-7000-8000-0000000000b6','-','')), 'rds-mysql', '0.1.0', 'deadbeefcafe',
   'templates/rds-mysql/0.1.0/template.yaml', 'templates/rds-mysql/0.1.0/schema.json',
   'templates/rds-mysql/0.1.0/manifest.json', 'PUBLISHED', NOW(6));

INSERT INTO runbooks (id, path_in_repo, title, severity, alerts_covered) VALUES
  ('stack-create-failed', 'docs/runbooks/stack-create-failed.md', 'Stack creation failures', 'SEV3',
   JSON_ARRAY('PlatformStackCreateFailureRate'));
