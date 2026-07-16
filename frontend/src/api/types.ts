// Hand-maintained mirror of openapi.yaml component schemas until openapi-generator is wired
// into the build (2.01 enhancement). Keep in lockstep with backend/api openapi.yaml.
import type { RequestStatus, SyncStatus } from "../components/PlatformStatus";

export type Environment = "DEV" | "STG" | "PROD";

export interface PlatformRequest {
  id: string;
  templateId: string;
  environment: Environment;
  region: string;
  resourceName: string;
  status: RequestStatus;
  requesterEmail: string;
  stackId?: string | null;
  failureReason?: string | null;
  submittedAt: string;
}

export interface RequestEvent {
  from: string | null;
  to: RequestStatus;
  reason: string | null;
  source: "PLATFORM" | "CLOUDFORMATION";
  occurredAt: string;
}

export interface Template {
  id: string;
  displayName: string;
  description: string;
  category: string;
  maturity: "stable" | "beta" | "deprecated";
  application?: string | null;
  latestVersion?: string | null;
}

export interface TemplateDetail {
  template: Template;
  latestVersion: string;
  schema: string; // JSON Schema draft-07, stringified
}

export interface Kpi {
  value: number;
  delta_pct: number;
  direction: "up" | "down" | "flat";
}

export interface EnvironmentHealth {
  environment: Environment;
  status: "HEALTHY" | "DEGRADED" | "DOWN";
  lastIncidentAt: string;
}

export interface Me {
  id: string;
  email: string;
  roles: string[];
}

export interface TemplateSync {
  id: string;
  actorId: string;
  commitSha: string;
  branch: string;
  mode: "APPLY" | "DRY_RUN";
  status: SyncStatus;
  startedAt: string;
  completedAt?: string | null;
}

export interface AuditEntry {
  actorId: string;
  action: string;
  subjectType: string;
  subjectId: string;
  detail: string;
  traceId: string;
  occurredAt: string;
}

export interface Runbook {
  id: string;
  path: string;
  title: string;
  severity: string;
}

export interface PendingApproval {
  id: string;
  templateId: string;
  environment: Environment;
  region: string;
  resourceName: string;
  requesterEmail: string;
  submittedAt: string;
}

export interface SyncRun {
  id: string;
  actorId: string;
  branch: string;
  mode: "APPLY" | "DRY_RUN";
  status: string;
  startedAt: string;
  completedAt?: string | null;
  summary_json?: string | null;
  error_json?: string | null;
}

export interface SyncEvent {
  from_status: string | null;
  to_status: string;
  detail: string | null;
  occurred_at: string;
}

export interface CreateRequestPayload {
  templateId: string;
  environment: Environment;
  region: string;
  resourceName: string;
  description?: string;
  configuration: Record<string, unknown>;
}
