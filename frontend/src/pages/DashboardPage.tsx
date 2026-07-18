import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ColumnLayout from "@cloudscape-design/components/column-layout";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Link from "@cloudscape-design/components/link";
import SpaceBetween from "@cloudscape-design/components/space-between";
import StatusIndicator from "@cloudscape-design/components/status-indicator";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { CostSummary, EnvironmentHealth, Kpi, PlatformRequest } from "../api/types";
import PlatformStatus from "../components/PlatformStatus";
import { useMe } from "../auth/useMe";
import { relativeTime } from "../util/time";

/** Dashboard (2.02). Each container fetches independently — one failing widget never blanks the page. */
export default function DashboardPage() {
  const me = useMe();
  const navigate = useNavigate();
  const [kpis, setKpis] = useState<Record<string, Kpi> | null>(null);
  const [requests, setRequests] = useState<PlatformRequest[] | null>(null);
  const [health, setHealth] = useState<EnvironmentHealth[] | null>(null);
  const [spend, setSpend] = useState<CostSummary | null>(null);

  useEffect(() => {
    api.kpis().then(setKpis).catch(() => setKpis({}));
    api.listMyRequests(10).then(setRequests).catch(() => setRequests([]));
    api.costSummary().then(setSpend).catch(() => setSpend(null));
    const loadHealth = () => api.environmentsHealth().then(setHealth).catch(() => setHealth([]));
    loadHealth();
    const timer = setInterval(loadHealth, 60_000); // poll health every 60s (2.02 enhancement)
    return () => clearInterval(timer);
  }, []);

  const firstName = me?.email?.split("@")[0] ?? "there";

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          actions={
            <SpaceBetween direction="horizontal" size="xs">
              <Button variant="primary" onClick={() => navigate("/catalog")}>
                Provision a resource
              </Button>
              <Button onClick={() => navigate("/deployments")}>Deploy a service</Button>
            </SpaceBetween>
          }
        >
          Welcome back, {firstName}
        </Header>
      }
    >
      <SpaceBetween size="l">
        <ColumnLayout columns={4}>
          <KpiCard title="Active requests" kpi={kpis?.active_requests} href="/requests" />
          <KpiCard title="Successful (30d)" kpi={kpis?.successful_30d} href="/requests" />
          <KpiCard title="Failed (30d)" kpi={kpis?.failed_30d} href="/requests" />
          <KpiCard title="Avg completion (s)" kpi={kpis?.avg_completion_seconds} href="/requests" />
        </ColumnLayout>

        {spend?.available && (
          <Container>
            <SpaceBetween size="xs">
              <Box variant="awsui-key-label">Month-to-date spend · all environments</Box>
              <SpaceBetween direction="horizontal" size="l">
                <Link href="/costs" fontSize="display-l" variant="primary">
                  {money(spend.monthToDate, spend.currency)}
                </Link>
                {spend.previousMonth > 0 && (
                  <Box
                    color={spend.monthToDate > spend.previousMonth ? "text-status-error" : "text-status-success"}
                    fontSize="body-s"
                    fontWeight="bold"
                    padding={{ top: "xl" }}
                  >
                    {spend.monthToDate > spend.previousMonth ? "▲" : "▼"}{" "}
                    {Math.abs(((spend.monthToDate - spend.previousMonth) / spend.previousMonth) * 100).toFixed(1)}% vs
                    last month
                  </Box>
                )}
              </SpaceBetween>
            </SpaceBetween>
          </Container>
        )}

        <Table
          header={<Header counter={requests ? `(${requests.length})` : undefined}>My recent requests</Header>}
          loading={requests === null}
          items={requests ?? []}
          columnDefinitions={[
            {
              id: "id",
              header: "Request",
              cell: (r) => (
                <RouterLink to={`/requests/${r.id}`}>{r.id.slice(0, 8)}</RouterLink>
              ),
            },
            { id: "resource", header: "Resource", cell: (r) => r.resourceName },
            { id: "template", header: "Template", cell: (r) => r.templateId },
            { id: "env", header: "Environment", cell: (r) => r.environment },
            { id: "region", header: "Region", cell: (r) => r.region },
            { id: "status", header: "Status", cell: (r) => <PlatformStatus status={r.status} /> },
            {
              id: "submitted",
              header: "Submitted",
              cell: (r) => <span title={r.submittedAt}>{relativeTime(r.submittedAt)}</span>,
            },
          ]}
          empty={
            <Box textAlign="center" padding="l">
              <SpaceBetween size="s">
                <b>No requests yet</b>
                <Button onClick={() => navigate("/catalog")}>Browse the catalog</Button>
              </SpaceBetween>
            </Box>
          }
        />

        <ColumnLayout columns={2}>
          <Container header={<Header>Environment health</Header>}>
            <SpaceBetween size="s">
              {(health ?? []).map((row) => (
                <div key={row.environment} aria-live="polite">
                  <SpaceBetween direction="horizontal" size="s">
                    <Box fontWeight="bold">{row.environment}</Box>
                    <StatusIndicator
                      type={row.status === "HEALTHY" ? "success" : row.status === "DEGRADED" ? "warning" : "error"}
                    >
                      {row.status}
                    </StatusIndicator>
                  </SpaceBetween>
                </div>
              ))}
              {health === null && <StatusIndicator type="loading">Loading</StatusIndicator>}
            </SpaceBetween>
          </Container>
          <Container header={<Header>What's new</Header>}>
            <Box color="text-status-inactive">
              Recently published template versions appear here once template sync (3.09) is live.
            </Box>
          </Container>
        </ColumnLayout>
      </SpaceBetween>
    </ContentLayout>
  );
}

function money(n: number, currency = "USD"): string {
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(n);
}

function KpiCard({ title, kpi, href }: { title: string; kpi?: Kpi; href: string }) {
  const direction = kpi?.direction ?? "flat";
  const arrow = direction === "up" ? "▲" : direction === "down" ? "▼" : "–";
  const deltaColor =
    direction === "up" ? "text-status-success" : direction === "down" ? "text-status-error" : "text-status-inactive";
  return (
    <Container fitHeight>
      <SpaceBetween size="xs">
        <Box variant="awsui-key-label">{title}</Box>
        <Link href={href} fontSize="display-l" variant="primary">
          {kpi ? String(kpi.value) : "—"}
        </Link>
        {kpi ? (
          <SpaceBetween direction="horizontal" size="xxs">
            <Box color={deltaColor} fontSize="body-s" fontWeight="bold">
              {arrow} {Math.abs(kpi.delta_pct)}%
            </Box>
            <Box color="text-status-inactive" fontSize="body-s">
              vs previous period
            </Box>
          </SpaceBetween>
        ) : (
          <Box color="text-status-inactive" fontSize="body-s">
            loading…
          </Box>
        )}
      </SpaceBetween>
    </Container>
  );
}
