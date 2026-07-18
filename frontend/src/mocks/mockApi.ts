// Offline mock API (2.01: "Mock API at /api/v1 for offline dev"). Enable with VITE_USE_MOCKS=true.
import type { PlatformRequest, RequestEvent, Template, TemplateDetail } from "../api/types";

const templates: Template[] = [
  {
    id: "s3-bucket",
    displayName: "S3 Bucket",
    description: "Versioned, encrypted S3 bucket with Block Public Access enforced.",
    category: "Storage",
    maturity: "stable",
    latestVersion: "1.0.0",
  },
  {
    id: "sqs-queue",
    displayName: "SQS Queue",
    description: "Standard SQS queue with dead-letter queue and encryption.",
    category: "Integration",
    maturity: "beta",
    latestVersion: "0.1.0",
  },
  {
    id: "dynamodb-table",
    displayName: "DynamoDB Table",
    description: "On-demand DynamoDB table with point-in-time recovery.",
    category: "Database",
    maturity: "beta",
    latestVersion: "0.1.0",
  },
  {
    id: "sns-topic",
    displayName: "SNS Topic",
    description: "Encrypted SNS topic, standard or FIFO, TLS-only publishing.",
    category: "Integration",
    maturity: "beta",
    latestVersion: "0.1.0",
  },
  {
    id: "lambda-function",
    displayName: "Lambda Function",
    description: "Lambda scaffold with least-privilege role and log retention.",
    category: "Compute",
    maturity: "beta",
    latestVersion: "0.1.0",
  },
  {
    id: "rds-mysql",
    displayName: "RDS MySQL",
    description: "MySQL 8 in private data subnets with managed master password.",
    category: "Database",
    maturity: "beta",
    latestVersion: "0.1.0",
  },
];

const s3Schema = {
  $schema: "http://json-schema.org/draft-07/schema#",
  type: "object",
  required: ["bucketName"],
  additionalProperties: false,
  properties: {
    bucketName: {
      type: "string",
      title: "Bucket name",
      pattern: "^[a-z0-9.-]{3,63}$",
      "x-help": "Globally unique; account id and env suffix are appended automatically.",
    },
    versioning: { type: "boolean", title: "Enable versioning", default: true },
    storageClass: {
      type: "string",
      title: "Default storage class",
      enum: ["STANDARD", "STANDARD_IA", "INTELLIGENT_TIERING"],
      default: "STANDARD",
    },
    expireAfterDays: { type: "integer", title: "Expire objects after (days)", minimum: 1, maximum: 3650 },
  },
};

const requests: PlatformRequest[] = [
  {
    id: "0190a1b2-7d3e-7000-8000-3fb1c0ffee00",
    templateId: "s3-bucket",
    environment: "DEV",
    region: "us-east-1",
    resourceName: "analytics-raw-data",
    status: "CREATE_COMPLETE",
    requesterEmail: "dev@local",
    submittedAt: new Date(Date.now() - 3600e3).toISOString(),
  },
  {
    id: "0190a1b2-7d3e-7000-8000-3fb1c0ffee01",
    templateId: "sqs-queue",
    environment: "DEV",
    region: "us-east-1",
    resourceName: "orders-events",
    status: "CREATE_IN_PROGRESS",
    requesterEmail: "dev@local",
    submittedAt: new Date(Date.now() - 120e3).toISOString(),
  },
];

const events: RequestEvent[] = [
  { from: null, to: "PENDING_VALIDATION", reason: "Submitted", source: "PLATFORM", occurredAt: new Date().toISOString() },
  { from: "PENDING_VALIDATION", to: "QUEUED", reason: "Validation passed", source: "PLATFORM", occurredAt: new Date().toISOString() },
  { from: "QUEUED", to: "CREATE_IN_PROGRESS", reason: "CreateStack initiated", source: "PLATFORM", occurredAt: new Date().toISOString() },
];

export async function mockHandler(method: string, path: string, body?: unknown): Promise<unknown> {
  await new Promise((r) => setTimeout(r, 250)); // simulate latency
  if (path.startsWith("/me")) return { id: "dev@local", email: "dev@local", roles: ["USER", "PLATFORM_ADMIN", "APPROVER"] };
  if (path.startsWith("/regions")) return ["us-east-1", "us-west-2", "eu-west-1"];
  if (path.startsWith("/environments/health"))
    return ["DEV", "STG", "PROD"].map((environment) => ({ environment, status: "HEALTHY", lastIncidentAt: "" }));
  if (path.startsWith("/dashboards/kpis"))
    return {
      active_requests: { value: 1, delta_pct: 0, direction: "flat" },
      successful_30d: { value: 12, delta_pct: 20, direction: "up" },
      failed_30d: { value: 1, delta_pct: -50, direction: "down" },
      avg_completion_seconds: { value: 94, delta_pct: -10, direction: "down" },
    };
  if (path.startsWith("/templates?")) return templates;
  if (path.startsWith("/templates/")) {
    const id = path.split("/")[2];
    const template = templates.find((t) => t.id === id) ?? templates[0];
    return { template, latestVersion: template.latestVersion, schema: JSON.stringify(s3Schema) } as TemplateDetail;
  }
  if (method === "POST" && path === "/requests") {
    const payload = body as { templateId: string; environment: string; region: string; resourceName: string };
    const created: PlatformRequest = {
      id: crypto.randomUUID(),
      templateId: payload.templateId,
      environment: payload.environment as PlatformRequest["environment"],
      region: payload.region,
      resourceName: payload.resourceName,
      status: "QUEUED",
      requesterEmail: "dev@local",
      submittedAt: new Date().toISOString(),
    };
    requests.unshift(created);
    return created;
  }
  if (path.match(/^\/requests\/[^/]+\/events$/)) return events;
  if (path.match(/^\/requests\/[^/]+$/)) {
    const id = path.split("/")[2];
    return requests.find((r) => r.id === id) ?? requests[0];
  }
  if (method === "POST" && path === "/admin/dlq/redrive") return { taskHandle: "mock-move-task" };
  if (path === "/admin/dlq/messages")
    return [
      {
        messageId: "m-1",
        receiveCount: "5",
        firstSentAt: String(Date.now() - 3_600_000),
        body: '{"requestId":"018f...","type":"PROVISION"}',
      },
    ];
  if (path === "/admin/dlq") return { configured: true, visible: 1, notVisible: 0, redrive: { status: "NONE" } };
  if (path === "/costs/summary")
    return {
      available: true,
      currency: "USD",
      monthToDate: 412.87,
      previousMonth: 638.19,
      trend: [
        { month: "2026-02", amount: 501.2 },
        { month: "2026-03", amount: 548.9 },
        { month: "2026-04", amount: 602.4 },
        { month: "2026-05", amount: 571.05 },
        { month: "2026-06", amount: 638.19 },
        { month: "2026-07", amount: 412.87 },
      ],
      updatedAt: new Date().toISOString(),
      note: null,
    };
  if (path === "/costs/by-team")
    return [
      { costCenter: "CC-1001", team: "Payments", amount: 221.4 },
      { costCenter: "CC-2050", team: "Research", amount: 131.02 },
      { costCenter: "CC-3007", team: "Platform", amount: 60.45 },
    ];
  if (path === "/costs/by-environment")
    return [
      { key: "PROD", amount: 254.11 },
      { key: "STG", amount: 88.3 },
      { key: "QA", amount: 41.2 },
      { key: "DEV", amount: 29.26 },
    ];
  if (path.startsWith("/costs/resource/")) return { available: true, currency: "USD", monthToDate: 18.44 };
  if (path.startsWith("/requests")) return requests;
  throw new Error(`No mock for ${method} ${path}`);
}
