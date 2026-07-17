import { uuidv7 } from "uuidv7";
import type {
  AuditEntry,
  CreateRequestPayload,
  EnvironmentHealth,
  Kpi,
  Me,
  PendingApproval,
  Runbook,
  PlatformRequest,
  RequestEvent,
  SyncEvent,
  SyncRun,
  Template,
  TemplateDetail,
} from "./types";
import { accessToken, handleUnauthorized } from "../auth/auth";
import { mockHandler } from "../mocks/mockApi";

const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === "true";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    detail: string,
  ) {
    super(detail);
  }
}

/** Random W3C traceparent for RUM until the OTel web SDK (4.06) owns propagation. */
function traceparent(): string {
  const bytes = (n: number) =>
    Array.from(crypto.getRandomValues(new Uint8Array(n)), (b) => b.toString(16).padStart(2, "0")).join("");
  return `00-${bytes(16)}-${bytes(8)}-01`;
}

async function call<T>(method: string, path: string, body?: unknown, idempotencyKey?: string): Promise<T> {
  if (USE_MOCKS) {
    return mockHandler(method, path, body) as Promise<T>;
  }
  const headers: Record<string, string> = {
    "Content-Type": "application/json",
    traceparent: traceparent(),
  };
  const token = accessToken();
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  if (idempotencyKey) {
    headers["Idempotency-Key"] = idempotencyKey;
  }
  const response = await fetch(`/api/v1${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!response.ok) {
    if (response.status === 401) {
      handleUnauthorized(); // session expired — kick off re-login (no-op in dev-bypass)
      throw new ApiError(401, "UNAUTHENTICATED", "Your session expired — signing you back in…");
    }
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(response.status, problem.code ?? "UNKNOWN", problem.detail ?? response.statusText);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  me: () => call<Me>("GET", "/me"),
  regions: (env: string) => call<string[]>("GET", `/regions?env=${env}`),
  environmentsHealth: () => call<EnvironmentHealth[]>("GET", "/environments/health"),
  kpis: () => call<Record<string, Kpi>>("GET", "/dashboards/kpis"),

  listTemplates: () => call<Template[]>("GET", "/templates?maturity=stable,beta"),
  getTemplate: (id: string) => call<TemplateDetail>("GET", `/templates/${id}`),

  listGroups: () => call<{ name: string; description: string }[]>("GET", "/groups"),
  createGroup: (name: string, description: string) =>
    call<{ name: string }>("POST", "/groups", { name, description }, uuidv7()),
  listAudit: () => call<AuditEntry[]>("GET", "/audit"),
  listRunbooks: () => call<Runbook[]>("GET", "/runbooks"),
  listApprovals: () => call<PendingApproval[]>("GET", "/approvals"),
  approveRequest: (id: string) => call<PlatformRequest>("POST", `/approvals/${id}/approve`, undefined, uuidv7()),
  rejectRequest: (id: string, reason: string) =>
    call<PlatformRequest>("POST", `/approvals/${id}/reject`, { reason }, uuidv7()),

  triggerSync: (mode: "DRY_RUN" | "APPLY", branch = "develop") =>
    call<{ id: string; status: string }>("POST", "/admin/templates/sync", { mode, branch }, uuidv7()),
  listSyncs: () => call<SyncRun[]>("GET", "/admin/templates/sync"),
  getSync: (id: string) => call<SyncRun & { events: SyncEvent[] }>("GET", `/admin/templates/sync/${id}`),

  listMyRequests: (limit = 20) => call<PlatformRequest[]>("GET", `/requests?requester=me&limit=${limit}`),
  getRequest: (id: string) => call<PlatformRequest>("GET", `/requests/${id}`),
  getRequestEvents: (id: string) => call<RequestEvent[]>("GET", `/requests/${id}/events`),
  getRequestOutputs: (id: string) => call<{ key: string; value: string }[]>("GET", `/requests/${id}/outputs`),
  createRequest: (payload: CreateRequestPayload, idempotencyKey: string) =>
    call<PlatformRequest>("POST", "/requests", payload, idempotencyKey),
  retryRequest: (id: string) => call<PlatformRequest>("POST", `/requests/${id}/retry`, undefined, uuidv7()),
  reconcileRequest: (id: string) => call<PlatformRequest>("POST", `/requests/${id}/reconcile`, undefined, uuidv7()),
  promoteRequest: (id: string) => call<PlatformRequest>("POST", `/requests/${id}/promote`, undefined, uuidv7()),
  deleteRequest: (id: string) => call<PlatformRequest>("DELETE", `/requests/${id}`, undefined, uuidv7()),
};

export { uuidv7 };
