import StatusIndicator, {
  StatusIndicatorProps,
} from "@cloudscape-design/components/status-indicator";

/**
 * PlatformStatus (2.14a): typed, exhaustive mapping of every request/sync status to the correct
 * Cloudscape StatusIndicator type. Adding a status to the backend enum without mapping it here
 * fails TypeScript compilation (never-check in the default branch).
 */

export const REQUEST_STATUSES = [
  "DRAFT",
  "PENDING_VALIDATION",
  "FAILED_VALIDATION",
  "PENDING_APPROVAL",
  "REJECTED",
  "QUEUED",
  "CREATE_IN_PROGRESS",
  "CREATE_COMPLETE",
  "CREATE_FAILED",
  "ROLLBACK_IN_PROGRESS",
  "ROLLBACK_COMPLETE",
  "ROLLBACK_FAILED",
  "UPDATE_IN_PROGRESS",
  "UPDATE_COMPLETE",
  "UPDATE_FAILED",
  "UPDATE_ROLLBACK_IN_PROGRESS",
  "UPDATE_ROLLBACK_COMPLETE",
  "DELETE_IN_PROGRESS",
  "DELETE_COMPLETE",
  "DELETE_FAILED",
] as const;

export const SYNC_STATUSES = [
  "SYNC_PENDING",
  "SYNC_VALIDATING",
  "SYNC_UPLOADING",
  "SYNC_COMPLETE",
  "SYNC_FAILED",
] as const;

export type RequestStatus = (typeof REQUEST_STATUSES)[number];
export type SyncStatus = (typeof SYNC_STATUSES)[number];
export type PlatformStatusValue = RequestStatus | SyncStatus;

function assertNever(value: never): never {
  throw new Error(`Unmapped platform status: ${value}`);
}

export function statusIndicatorType(status: PlatformStatusValue): StatusIndicatorProps.Type {
  switch (status) {
    case "DRAFT":
      return "stopped";
    case "PENDING_VALIDATION":
    case "PENDING_APPROVAL":
    case "QUEUED":
    case "SYNC_PENDING":
      return "pending";
    case "CREATE_IN_PROGRESS":
    case "UPDATE_IN_PROGRESS":
    case "DELETE_IN_PROGRESS":
    case "SYNC_VALIDATING":
    case "SYNC_UPLOADING":
      return "in-progress";
    case "ROLLBACK_IN_PROGRESS":
    case "UPDATE_ROLLBACK_IN_PROGRESS":
      return "warning";
    case "CREATE_COMPLETE":
    case "UPDATE_COMPLETE":
    case "ROLLBACK_COMPLETE":
    case "UPDATE_ROLLBACK_COMPLETE":
    case "SYNC_COMPLETE":
      return "success";
    case "FAILED_VALIDATION":
    case "REJECTED":
    case "CREATE_FAILED":
    case "UPDATE_FAILED":
    case "ROLLBACK_FAILED":
    case "DELETE_FAILED":
    case "SYNC_FAILED":
      return "error";
    case "DELETE_COMPLETE":
      return "stopped";
    default:
      return assertNever(status);
  }
}

/** Human label map — consistent copy everywhere (2.14a enhancement). */
export function statusLabel(status: PlatformStatusValue): string {
  switch (status) {
    case "DRAFT":
      return "Draft";
    case "PENDING_VALIDATION":
      return "Validating…";
    case "FAILED_VALIDATION":
      return "Validation failed";
    case "PENDING_APPROVAL":
      return "Awaiting approval";
    case "REJECTED":
      return "Rejected";
    case "QUEUED":
      return "Queued";
    case "CREATE_IN_PROGRESS":
      return "Creating…";
    case "CREATE_COMPLETE":
      return "Created";
    case "CREATE_FAILED":
      return "Create failed";
    case "ROLLBACK_IN_PROGRESS":
      return "Rolling back…";
    case "ROLLBACK_COMPLETE":
      return "Rolled back";
    case "ROLLBACK_FAILED":
      return "Rollback failed";
    case "UPDATE_IN_PROGRESS":
      return "Updating…";
    case "UPDATE_COMPLETE":
      return "Updated";
    case "UPDATE_FAILED":
      return "Update failed";
    case "UPDATE_ROLLBACK_IN_PROGRESS":
      return "Rolling back update…";
    case "UPDATE_ROLLBACK_COMPLETE":
      return "Update rolled back";
    case "DELETE_IN_PROGRESS":
      return "Deleting…";
    case "DELETE_COMPLETE":
      return "Deleted";
    case "DELETE_FAILED":
      return "Delete failed";
    case "SYNC_PENDING":
      return "Sync pending";
    case "SYNC_VALIDATING":
      return "Validating templates…";
    case "SYNC_UPLOADING":
      return "Publishing…";
    case "SYNC_COMPLETE":
      return "Sync complete";
    case "SYNC_FAILED":
      return "Sync failed";
    default:
      return assertNever(status);
  }
}

export function isInProgress(status: PlatformStatusValue): boolean {
  return statusIndicatorType(status) === "in-progress" || statusIndicatorType(status) === "warning";
}

export function isTerminal(status: PlatformStatusValue): boolean {
  const type = statusIndicatorType(status);
  return type === "success" || type === "error" || status === "DELETE_COMPLETE";
}

export default function PlatformStatus({ status }: { status: PlatformStatusValue }) {
  return <StatusIndicator type={statusIndicatorType(status)}>{statusLabel(status)}</StatusIndicator>;
}
