import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import Alert from "@cloudscape-design/components/alert";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ButtonDropdown from "@cloudscape-design/components/button-dropdown";
import ColumnLayout from "@cloudscape-design/components/column-layout";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import ExpandableSection from "@cloudscape-design/components/expandable-section";
import Header from "@cloudscape-design/components/header";
import Input from "@cloudscape-design/components/input";
import Modal from "@cloudscape-design/components/modal";
import ProgressBar from "@cloudscape-design/components/progress-bar";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Spinner from "@cloudscape-design/components/spinner";
import Tabs from "@cloudscape-design/components/tabs";
import { api } from "../api/client";
import type { PlatformRequest, RequestEvent } from "../api/types";
import PlatformStatus, { isInProgress, isTerminal } from "../components/PlatformStatus";
import { localWithUtcTitle } from "../util/time";

/**
 * Request detail (2.05): live lifecycle via SSE with automatic fallback to 5s polling; tabs are
 * deep-linkable (?tab=events); delete confirmation requires typing the resource name.
 */
export default function RequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [request, setRequest] = useState<PlatformRequest | null>(null);
  const [events, setEvents] = useState<RequestEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [deleteVisible, setDeleteVisible] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState("");
  const pollTimer = useRef<ReturnType<typeof setInterval> | null>(null);

  const refresh = useCallback(() => {
    if (!id) return;
    api.getRequest(id).then(setRequest).catch((e) => setError(String(e)));
    api.getRequestEvents(id).then(setEvents).catch(() => undefined);
  }, [id]);

  // SSE preferred; fall back to 5s polling while in progress (stop on terminal state)
  useEffect(() => {
    refresh();
    if (!id) return;

    let source: EventSource | null = null;
    const startPolling = () => {
      if (pollTimer.current) return;
      pollTimer.current = setInterval(refresh, 5000);
    };
    if (import.meta.env.VITE_USE_MOCKS !== "true" && typeof EventSource !== "undefined") {
      source = new EventSource(`/api/v1/requests/${id}/events`);
      source.addEventListener("status", (e) => {
        setRequest(JSON.parse((e as MessageEvent).data));
        api.getRequestEvents(id).then(setEvents).catch(() => undefined);
      });
      source.onerror = () => {
        source?.close();
        startPolling(); // SSE died (proxy, ALB timeout) — degrade gracefully
      };
    } else {
      startPolling();
    }
    return () => {
      source?.close();
      if (pollTimer.current) clearInterval(pollTimer.current);
    };
  }, [id, refresh]);

  useEffect(() => {
    if (request && isTerminal(request.status) && pollTimer.current) {
      clearInterval(pollTimer.current);
      pollTimer.current = null;
    }
  }, [request]);

  if (!request) {
    return (
      <Box textAlign="center" padding="xxl">
        {error ? <Alert type="error">{error}</Alert> : <Spinner size="large" />}
      </Box>
    );
  }

  const failed = request.status.endsWith("_FAILED") || request.status === "FAILED_VALIDATION";
  const activeTab = searchParams.get("tab") ?? "overview";

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          actions={
            <ButtonDropdown
              items={[
                { id: "retry", text: "Retry", disabled: !failed },
                { id: "delete", text: "Delete", disabled: isInProgress(request.status) },
                { id: "console", text: "View in AWS Console", external: true, disabled: !request.stackId },
              ]}
              onItemClick={async (e) => {
                if (e.detail.id === "retry") {
                  await api.retryRequest(request.id).then(setRequest).catch((err) => setError(String(err)));
                }
                if (e.detail.id === "delete") setDeleteVisible(true);
                if (e.detail.id === "console" && request.stackId) {
                  window.open(
                    `https://console.aws.amazon.com/cloudformation/home?region=${request.region}#/stacks/stackinfo?stackId=${encodeURIComponent(request.stackId)}`,
                    "_blank",
                    "noopener",
                  );
                }
              }}
            >
              Actions
            </ButtonDropdown>
          }
        >
          Request {request.id.slice(0, 8)}
        </Header>
      }
    >
      <SpaceBetween size="l">
        {error && (
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        )}
        {failed && request.failureReason && (
          <Alert
            type="error"
            header="This request failed"
            action={<Button onClick={() => api.retryRequest(request.id).then(setRequest)}>Retry</Button>}
          >
            {request.failureReason}
          </Alert>
        )}

        <ColumnLayout columns={4} variant="text-grid">
          <div>
            <Box variant="awsui-key-label">Template</Box>
            <Box>{request.templateId}</Box>
          </div>
          <div>
            <Box variant="awsui-key-label">Environment</Box>
            <Badge color={request.environment === "PROD" ? "red" : "blue"}>{request.environment}</Badge>
          </div>
          <div>
            <Box variant="awsui-key-label">Region</Box>
            <Box>{request.region}</Box>
          </div>
          <div>
            <Box variant="awsui-key-label">Owner</Box>
            <Box>{request.requesterEmail}</Box>
          </div>
        </ColumnLayout>

        <Container>
          <SpaceBetween size="s">
            <PlatformStatus status={request.status} />
            {isInProgress(request.status) && (
              <ProgressBar value={50} variant="key-value" label="Working…" description="CloudFormation in progress" />
            )}
          </SpaceBetween>
        </Container>

        <Tabs
          activeTabId={activeTab}
          onChange={(e) => {
            const next = new URLSearchParams(searchParams);
            next.set("tab", e.detail.activeTabId);
            setSearchParams(next, { replace: true }); // deep-linkable tabs (2.05 enhancement)
          }}
          tabs={[
            {
              id: "overview",
              label: "Overview",
              content: (
                <ColumnLayout columns={2} variant="text-grid">
                  <div>
                    <Box variant="awsui-key-label">Resource name</Box>
                    <Box>{request.resourceName}</Box>
                  </div>
                  <div>
                    <Box variant="awsui-key-label">Stack ID</Box>
                    <Box>{request.stackId ?? "not created yet"}</Box>
                  </div>
                  <div>
                    <Box variant="awsui-key-label">Submitted</Box>
                    <Box>
                      <span title={localWithUtcTitle(request.submittedAt).title}>
                        {localWithUtcTitle(request.submittedAt).text}
                      </span>
                    </Box>
                  </div>
                </ColumnLayout>
              ),
            },
            {
              id: "events",
              label: "Events",
              content: (
                <SpaceBetween size="s">
                  {events.length === 0 && <Box color="text-status-inactive">No events yet.</Box>}
                  {events.map((event, index) => (
                    <Container key={index}>
                      <SpaceBetween direction="horizontal" size="s">
                        <Box fontSize="body-s" color="text-status-inactive">
                          {localWithUtcTitle(event.occurredAt).text}
                        </Box>
                        <Box>
                          {event.from ?? "—"} → <b>{event.to}</b>
                        </Box>
                        <Badge color={event.source === "CLOUDFORMATION" ? "blue" : "grey"}>{event.source}</Badge>
                      </SpaceBetween>
                      {event.reason && (
                        <ExpandableSection headerText="Reason">
                          <Box fontSize="body-s">{event.reason}</Box>
                        </ExpandableSection>
                      )}
                    </Container>
                  ))}
                </SpaceBetween>
              ),
            },
            {
              id: "logs",
              label: "Logs",
              content: (
                <SpaceBetween size="s">
                  <Button href="/grafana/explore" target="_blank" iconAlign="right" iconName="external">
                    Open Grafana Explore (filter request_id: {request.id.slice(0, 8)}…)
                  </Button>
                  <iframe
                    title="Grafana logs"
                    src="/grafana/explore"
                    style={{ width: "100%", height: 480, border: "1px solid #333", borderRadius: 8 }}
                  />
                </SpaceBetween>
              ),
            },
            {
              id: "trace",
              label: "Trace",
              content: (
                <SpaceBetween size="s">
                  <Button href="/grafana/explore" target="_blank" iconAlign="right" iconName="external">
                    Open Tempo trace search
                  </Button>
                  <iframe
                    title="Grafana traces"
                    src="/grafana/explore"
                    style={{ width: "100%", height: 480, border: "1px solid #333", borderRadius: 8 }}
                  />
                </SpaceBetween>
              ),
            },
          ]}
        />
      </SpaceBetween>

      <Modal
        visible={deleteVisible}
        onDismiss={() => setDeleteVisible(false)}
        header="Delete resource"
        footer={
          <Box float="right">
            <SpaceBetween direction="horizontal" size="xs">
              <Button variant="link" onClick={() => setDeleteVisible(false)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                disabled={deleteConfirmText !== request.resourceName}
                onClick={async () => {
                  setDeleteVisible(false);
                  await api.deleteRequest(request.id).then(setRequest).catch((e) => setError(String(e)));
                }}
              >
                Delete
              </Button>
            </SpaceBetween>
          </Box>
        }
      >
        <SpaceBetween size="m">
          <Alert type="warning">
            This deletes the real AWS resource behind this request. This action cannot be undone.
          </Alert>
          <Box>
            Type <b>{request.resourceName}</b> to confirm:
          </Box>
          <Input value={deleteConfirmText} onChange={(e) => setDeleteConfirmText(e.detail.value)} />
        </SpaceBetween>
      </Modal>
    </ContentLayout>
  );
}
