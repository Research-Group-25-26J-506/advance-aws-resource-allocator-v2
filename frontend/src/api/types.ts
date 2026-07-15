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

export interface CreateRequestPayload {
  templateId: string;
  environment: Environment;
  region: string;
  resourceName: string;
  description?: string;
  configuration: Record<string, unknown>;
}
