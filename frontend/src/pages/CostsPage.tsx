import { useEffect, useState } from "react";
import Alert from "@cloudscape-design/components/alert";
import BarChart from "@cloudscape-design/components/bar-chart";
import Box from "@cloudscape-design/components/box";
import ColumnLayout from "@cloudscape-design/components/column-layout";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { CostEnvSlice, CostSummary, CostTeamSlice } from "../api/types";

/**
 * Cost visibility (2.x governance-by-visibility). A team owning the platform sees its own AWS
 * spend — month-to-date, trend, and a breakdown by team (CostCenter tag) and environment — so
 * cost is managed by ownership, not hard quotas.
 */
export default function CostsPage() {
  const [summary, setSummary] = useState<CostSummary | null>(null);
  const [byTeam, setByTeam] = useState<CostTeamSlice[] | null>(null);
  const [byEnv, setByEnv] = useState<CostEnvSlice[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.costSummary().then(setSummary).catch((e) => setError(String(e)));
    api.costByTeam().then(setByTeam).catch(() => setByTeam([]));
    api.costByEnvironment().then(setByEnv).catch(() => setByEnv([]));
  }, []);

  const currency = summary?.currency ?? "USD";
  const mtd = summary?.monthToDate ?? 0;
  const prev = summary?.previousMonth ?? 0;
  const deltaPct = prev > 0 ? ((mtd - prev) / prev) * 100 : 0;
  const breakdownsEmpty =
    summary?.available && (byTeam?.length ?? 0) === 0 && (byEnv?.length ?? 0) === 0;

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="AWS spend for this account, sliced by the Team and Environment tags the platform applies to every resource."
        >
          Costs
        </Header>
      }
    >
      <SpaceBetween size="l">
        {error && (
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        )}

        {summary && !summary.available && (
          <Alert type="info" header="Cost data unavailable">
            Cost Explorer could not be reached{summary.note ? `: ${summary.note}` : ""}. In a
            deployed environment the API needs Cost Explorer read access (granted to the api task
            role).
          </Alert>
        )}

        {breakdownsEmpty && (
          <Alert type="info" header="Activate cost-allocation tags to see breakdowns">
            The total is live, but per-team and per-environment breakdowns are empty until the{" "}
            <b>CostCenter</b> and <b>Environment</b> tags are activated as cost-allocation tags in
            the Billing console (one-time, ~24h to backfill).
          </Alert>
        )}

        <ColumnLayout columns={3} variant="text-grid">
          <Metric label="This month to date" value={money(mtd, currency)} />
          <Metric label="Previous month" value={money(prev, currency)} />
          <Metric
            label="Change vs last month"
            value={`${deltaPct >= 0 ? "▲" : "▼"} ${Math.abs(deltaPct).toFixed(1)}%`}
            color={deltaPct > 0 ? "text-status-error" : "text-status-success"}
          />
        </ColumnLayout>

        <Container header={<Header variant="h2">Monthly trend</Header>}>
          <BarChart
            height={220}
            hideFilter
            hideLegend
            xTitle="Month"
            yTitle={currency}
            series={[
              {
                title: "Spend",
                type: "bar",
                data: (summary?.trend ?? []).map((t) => ({ x: t.month, y: t.amount })),
                valueFormatter: (v) => money(Number(v), currency),
              },
            ]}
            xDomain={(summary?.trend ?? []).map((t) => t.month)}
            ariaLabel="Monthly spend"
            empty={
              <Box textAlign="center" color="inherit">
                No cost data yet.
              </Box>
            }
          />
        </Container>

        <ColumnLayout columns={2}>
          <Table
            header={<Header variant="h2">By team</Header>}
            loading={byTeam === null}
            items={byTeam ?? []}
            columnDefinitions={[
              { id: "team", header: "Team", cell: (r) => r.team },
              { id: "cc", header: "Cost center", cell: (r) => r.costCenter },
              {
                id: "amount",
                header: `Spend (${currency})`,
                cell: (r) => <ShareCell amount={r.amount} max={maxAmount(byTeam)} currency={currency} />,
              },
            ]}
            empty={<Empty />}
          />
          <Table
            header={<Header variant="h2">By environment</Header>}
            loading={byEnv === null}
            items={byEnv ?? []}
            columnDefinitions={[
              { id: "env", header: "Environment", cell: (r) => r.key },
              {
                id: "amount",
                header: `Spend (${currency})`,
                cell: (r) => <ShareCell amount={r.amount} max={maxAmount(byEnv)} currency={currency} />,
              },
            ]}
            empty={<Empty />}
          />
        </ColumnLayout>

        {summary && (
          <Box color="text-status-inactive" fontSize="body-s" textAlign="right">
            Data from AWS Cost Explorer · cached · updated {new Date(summary.updatedAt).toLocaleString()}
          </Box>
        )}
      </SpaceBetween>
    </ContentLayout>
  );
}

function Metric({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div>
      <Box variant="awsui-key-label">{label}</Box>
      <Box fontSize="display-l" fontWeight="bold" color={color as never}>
        {value}
      </Box>
    </div>
  );
}

function ShareCell({ amount, max, currency }: { amount: number; max: number; currency: string }) {
  const pct = max > 0 ? Math.max(3, Math.round((amount / max) * 100)) : 0;
  return (
    <SpaceBetween size="xxs">
      <Box fontWeight="bold">{money(amount, currency)}</Box>
      <div
        style={{
          height: 6,
          width: `${pct}%`,
          borderRadius: 3,
          background: "var(--color-charts-palette-categorical-1, #688ae8)",
        }}
      />
    </SpaceBetween>
  );
}

function Empty() {
  return (
    <Box textAlign="center" padding="l" color="inherit">
      No tagged spend for this month yet.
    </Box>
  );
}

function maxAmount(rows: { amount: number }[] | null): number {
  return rows && rows.length > 0 ? Math.max(...rows.map((r) => r.amount)) : 0;
}

function money(n: number, currency = "USD"): string {
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(n);
}
