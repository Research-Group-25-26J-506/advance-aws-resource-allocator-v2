import { useCallback, useEffect, useState } from "react";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Select from "@cloudscape-design/components/select";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Spinner from "@cloudscape-design/components/spinner";
import Toggle from "@cloudscape-design/components/toggle";
import { api } from "../api/client";

interface AppEntry {
  logGroup: string;
  name: string;
  storedBytes: number;
}
interface LogEvent {
  timestamp: number;
  message: string;
}

/** Service Logs: full visibility into any deployed app service's CloudWatch logs, in-platform. */
export default function ServiceLogsPage() {
  const [apps, setApps] = useState<AppEntry[] | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [events, setEvents] = useState<LogEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [live, setLive] = useState(false);

  useEffect(() => {
    api.listApps().then((a) => {
      setApps(a);
      if (a.length && !selected) setSelected(a[0].logGroup);
    }).catch(() => setApps([]));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadLogs = useCallback(() => {
    if (!selected) return;
    setLoading(true);
    api.tailAppLogs(selected).then(setEvents).catch(() => setEvents([])).finally(() => setLoading(false));
  }, [selected]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  useEffect(() => {
    if (!live) return;
    const timer = setInterval(loadLogs, 5000);
    return () => clearInterval(timer);
  }, [live, loadLogs]);

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="Live CloudWatch logs for services deployed on the platform cluster."
          actions={
            <SpaceBetween direction="horizontal" size="s" alignItems="center">
              <Toggle checked={live} onChange={(e) => setLive(e.detail.checked)}>
                Live (5s)
              </Toggle>
              <Button iconName="refresh" onClick={loadLogs} loading={loading} ariaLabel="Refresh" />
            </SpaceBetween>
          }
        >
          Service Logs
        </Header>
      }
    >
      <SpaceBetween size="m">
        {apps === null ? (
          <Box textAlign="center" padding="l"><Spinner /></Box>
        ) : apps.length === 0 ? (
          <Box color="text-status-inactive">
            No app services deployed yet. Deploy an ECS Service or Scheduled Task from the catalog.
          </Box>
        ) : (
          <>
            <Select
              selectedOption={selected ? { value: selected, label: apps.find((a) => a.logGroup === selected)?.name } : null}
              options={apps.map((a) => ({ value: a.logGroup, label: a.name }))}
              onChange={(e) => setSelected(e.detail.selectedOption.value ?? null)}
            />
            <Box
              variant="code"
              padding="s"
              fontSize="body-s"
              color="text-status-inactive"
            >
              <div style={{ maxHeight: 560, overflowY: "auto", fontFamily: "monospace", whiteSpace: "pre-wrap" }}>
                {events.length === 0 && <div>No log events in the last hour.</div>}
                {events.map((ev, i) => (
                  <div key={i}>
                    <span style={{ opacity: 0.6 }}>{new Date(ev.timestamp).toLocaleTimeString()} </span>
                    {ev.message.trimEnd()}
                  </div>
                ))}
              </div>
            </Box>
          </>
        )}
      </SpaceBetween>
    </ContentLayout>
  );
}
