import { useCallback, useEffect, useState } from "react";
import Alert from "@cloudscape-design/components/alert";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ColumnLayout from "@cloudscape-design/components/column-layout";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Popover from "@cloudscape-design/components/popover";
import SpaceBetween from "@cloudscape-design/components/space-between";
import StatusIndicator from "@cloudscape-design/components/status-indicator";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { DlqMessage, DlqSummary } from "../api/types";

/**
 * Operator DLQ console (Phase C — Operate). A request whose work fails maxReceiveCount times lands
 * in the dead-letter queue. This page makes that queue visible to platform admins and lets them
 * redrive everything back to the source queue in one click via a native SQS message-move task.
 */
export default function OperationsPage() {
  const [summary, setSummary] = useState<DlqSummary | null>(null);
  const [messages, setMessages] = useState<DlqMessage[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.dlqSummary().then(setSummary).catch((e) => setError(String(e)));
    api.dlqMessages().then(setMessages).catch(() => setMessages([]));
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 15_000);
    return () => clearInterval(timer);
  }, [refresh]);

  const redrive = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.dlqRedrive();
      refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  const move = summary?.redrive;
  const moveActive = move && (move.status === "RUNNING" || move.status === "CANCELLING");
  const depth = summary?.visible ?? 0;

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="Requests whose work failed every retry land in the dead-letter queue. Redrive sends them back to the source queue to be processed again."
          actions={
            <SpaceBetween direction="horizontal" size="xs">
              <Button iconName="refresh" onClick={refresh}>
                Refresh
              </Button>
              <Button
                variant="primary"
                loading={busy}
                disabled={!summary?.configured || depth === 0 || Boolean(moveActive)}
                onClick={redrive}
              >
                Redrive all ({depth})
              </Button>
            </SpaceBetween>
          }
        >
          Operations · Dead-letter queue
        </Header>
      }
    >
      <SpaceBetween size="l">
        {error && (
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        )}

        {summary && !summary.configured && (
          <Alert type="info">No dead-letter queue is configured in this environment.</Alert>
        )}

        <Container header={<Header variant="h2">Queue depth</Header>}>
          <ColumnLayout columns={3} variant="text-grid">
            <div>
              <Box variant="awsui-key-label">Waiting (visible)</Box>
              <Box variant="awsui-value-large">{depth}</Box>
            </div>
            <div>
              <Box variant="awsui-key-label">In flight</Box>
              <Box variant="awsui-value-large">{summary?.notVisible ?? 0}</Box>
            </div>
            <div>
              <Box variant="awsui-key-label">Last redrive</Box>
              <Box>{move ? <RedriveStatus move={move} /> : "—"}</Box>
            </div>
          </ColumnLayout>
        </Container>

        <Table
          header={
            <Header
              counter={messages ? `(${messages.length})` : undefined}
              description="A non-destructive peek — messages stay in the queue."
            >
              Stuck messages
            </Header>
          }
          loading={messages === null}
          items={messages ?? []}
          columnDefinitions={[
            { id: "id", header: "Message", cell: (m) => m.messageId },
            {
              id: "receiveCount",
              header: "Attempts",
              cell: (m) => <Badge color={Number(m.receiveCount) >= 5 ? "red" : "grey"}>{m.receiveCount}</Badge>,
            },
            { id: "firstSentAt", header: "First seen", cell: (m) => formatEpoch(m.firstSentAt) },
            {
              id: "body",
              header: "Payload",
              cell: (m) => (
                <Popover header="Message body" content={<pre style={{ margin: 0 }}>{m.body}</pre>} size="large">
                  <Box variant="code">{truncate(m.body, 60)}</Box>
                </Popover>
              ),
            },
          ]}
          empty={
            <Box textAlign="center" padding="l">
              The dead-letter queue is empty.
            </Box>
          }
        />
      </SpaceBetween>
    </ContentLayout>
  );
}

function RedriveStatus({ move }: { move: NonNullable<DlqSummary["redrive"]> }) {
  if (move.status === "NONE") return <>None yet</>;
  const type =
    move.status === "COMPLETED"
      ? "success"
      : move.status === "FAILED"
        ? "error"
        : move.status === "RUNNING" || move.status === "CANCELLING"
          ? "in-progress"
          : "stopped";
  const moved = move.moved ?? 0;
  const total = (move.toMove ?? 0) + moved;
  return (
    <StatusIndicator type={type}>
      {move.status}
      {total > 0 ? ` · ${moved}/${total}` : ""}
      {move.failureReason ? ` · ${move.failureReason}` : ""}
    </StatusIndicator>
  );
}

function formatEpoch(ms: string): string {
  const n = Number(ms);
  if (!Number.isFinite(n) || n <= 0) return ms;
  return new Date(n).toLocaleString();
}

function truncate(s: string, n: number): string {
  return s.length > n ? `${s.slice(0, n)}…` : s;
}
