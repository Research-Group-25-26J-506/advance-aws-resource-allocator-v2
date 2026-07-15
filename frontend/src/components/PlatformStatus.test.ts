import { describe, expect, it } from "vitest";
import {
  REQUEST_STATUSES,
  SYNC_STATUSES,
  isInProgress,
  isTerminal,
  statusIndicatorType,
  statusLabel,
} from "./PlatformStatus";

describe("PlatformStatus mapping (2.14a)", () => {
  it("maps every status without throwing (exhaustiveness at runtime too)", () => {
    for (const status of [...REQUEST_STATUSES, ...SYNC_STATUSES]) {
      expect(statusIndicatorType(status)).toBeTruthy();
      expect(statusLabel(status)).toBeTruthy();
    }
  });

  it("classifies key states correctly", () => {
    expect(statusIndicatorType("QUEUED")).toBe("pending");
    expect(statusIndicatorType("CREATE_IN_PROGRESS")).toBe("in-progress");
    expect(statusIndicatorType("ROLLBACK_IN_PROGRESS")).toBe("warning");
    expect(statusIndicatorType("CREATE_COMPLETE")).toBe("success");
    expect(statusIndicatorType("CREATE_FAILED")).toBe("error");
    expect(statusIndicatorType("DELETE_COMPLETE")).toBe("stopped");
  });

  it("terminal/in-progress helpers agree with the lifecycle", () => {
    expect(isTerminal("CREATE_COMPLETE")).toBe(true);
    expect(isTerminal("DELETE_COMPLETE")).toBe(true);
    expect(isTerminal("QUEUED")).toBe(false);
    expect(isInProgress("UPDATE_IN_PROGRESS")).toBe(true);
    expect(isInProgress("REJECTED")).toBe(false);
  });
});
