import { useCallback, useEffect, useState } from "react";
import Alert from "@cloudscape-design/components/alert";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ContentLayout from "@cloudscape-design/components/content-layout";
import ExpandableSection from "@cloudscape-design/components/expandable-section";
import Header from "@cloudscape-design/components/header";
import Modal from "@cloudscape-design/components/modal";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { SyncEvent, SyncRun } from "../api/types";
import PlatformStatus, { type SyncStatus } from "../components/PlatformStatus";
import { relativeTime } from "../util/time";

/** Sync admin (2.09/2.10 scoped): dry-run preview modal, apply trigger, history + event detail. */
export default function SyncAdminPage() {
  const [runs, setRuns] = useState<SyncRun[] | null>(null);
  const [selected, setSelected] = useState<(SyncRun & { events: SyncEvent[] }) | null>(null);
  const [confirmApply, setConfirmApply] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.listSyncs().then(setRuns).catch((e) => setError(String(e)));
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 10_000);
    return () => clearInterval(timer);
  }, [refresh]);

  const trigger = async (mode: "DRY_RUN" | "APPLY") => {
    setBusy(true);
    setError(null);
    try {
      await api.triggerSync(mode);
      refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
      setConfirmApply(false);
    }
  };

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="Pull the templates repo, validate every template, publish new versions to S3. Versions are immutable once published."
          actions={
            <SpaceBetween direction="horizontal" size="xs">
              <Button onClick={() => trigger("DRY_RUN")} loading={busy}>
                Dry run
              </Button>
              <Button variant="primary" onClick={() => setConfirmApply(true)} loading={busy}>
                Sync now
              </Button>
            </SpaceBetween>
          }
        >
          Template sync
        </Header>
      }
    >
      <SpaceBetween size="l">
        {error && (
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        )}
        <Table
          header={<Header counter={runs ? `(${runs.length})` : undefined}>Sync history</Header>}
          loading={runs === null}
          items={runs ?? []}
          onRowClick={(e) => api.getSync(e.detail.item.id).then(setSelected).catch((err) => setError(String(err)))}
          columnDefinitions={[
            { id: "id", header: "Run", cell: (r) => r.id.slice(0, 8) },
            { id: "mode", header: "Mode", cell: (r) => r.mode },
            { id: "branch", header: "Branch", cell: (r) => r.branch },
            { id: "status", header: "Status", cell: (r) => <PlatformStatus status={r.status as SyncStatus} /> },
            { id: "actor", header: "Triggered by", cell: (r) => r.actorId },
            {
              id: "started",
              header: "Started",
              cell: (r) => <span title={r.startedAt}>{relativeTime(r.startedAt)}</span>,
            },
          ]}
          empty={
            <Box textAlign="center" padding="l">
              No syncs yet — run a dry run to preview what would publish.
            </Box>
          }
        />
        {selected && (
          <ExpandableSection
            headerText={`Run ${selected.id.slice(0, 8)} — ${selected.status}`}
            defaultExpanded
            variant="container"
          >
            <SpaceBetween size="s">
              {(selected.summary_json || selected.error_json) && (
                <Alert type={selected.error_json ? "error" : "success"}>
                  {selected.error_json ?? selected.summary_json}
                </Alert>
              )}
              {selected.events.map((event, index) => (
                <Box key={index} fontSize="body-s">
                  <b>{event.to_status}</b> — {event.detail ?? ""}{" "}
                  <Box variant="span" color="text-status-inactive">
                    ({String(event.occurred_at)})
                  </Box>
                </Box>
              ))}
            </SpaceBetween>
          </ExpandableSection>
        )}
      </SpaceBetween>

      <Modal
        visible={confirmApply}
        onDismiss={() => setConfirmApply(false)}
        header="Publish templates"
        footer={
          <Box float="right">
            <SpaceBetween direction="horizontal" size="xs">
              <Button variant="link" onClick={() => setConfirmApply(false)}>
                Cancel
              </Button>
              <Button variant="primary" onClick={() => trigger("APPLY")} loading={busy}>
                Sync and publish
              </Button>
            </SpaceBetween>
          </Box>
        }
      >
        This validates every template in the repo and publishes new versions to the live catalog.
        Published versions are immutable. Run a dry run first if unsure.
      </Modal>
    </ContentLayout>
  );
}
