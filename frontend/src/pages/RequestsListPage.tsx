import { useEffect, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Select from "@cloudscape-design/components/select";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Table from "@cloudscape-design/components/table";
import TextFilter from "@cloudscape-design/components/text-filter";
import { api } from "../api/client";
import type { PlatformRequest } from "../api/types";
import PlatformStatus from "../components/PlatformStatus";
import { relativeTime } from "../util/time";

export default function RequestsListPage() {
  const navigate = useNavigate();
  const [requests, setRequests] = useState<PlatformRequest[] | null>(null);
  const [filter, setFilter] = useState("");
  const [groupFilter, setGroupFilter] = useState("");

  const refresh = () => {
    setRequests(null);
    api.listMyRequests(100).then(setRequests).catch(() => setRequests([]));
  };
  useEffect(refresh, []);

  const filtered = (requests ?? []).filter(
    (r) =>
      (groupFilter === "" || r.resourceName === groupFilter) &&
      (filter === "" ||
        r.resourceName.includes(filter) ||
        r.templateId.includes(filter) ||
        r.status.toLowerCase().includes(filter.toLowerCase())),
  );

  return (
    <ContentLayout header={<Header variant="h1">My requests</Header>}>
      <Table
        header={
          <Header
            counter={requests ? `(${filtered.length})` : undefined}
            actions={
              <SpaceBetween direction="horizontal" size="xs">
                <Button iconName="refresh" onClick={refresh} ariaLabel="Refresh" />
                <Button onClick={() => navigate("/catalog")}>Provision a resource</Button>
              </SpaceBetween>
            }
          >
            Requests
          </Header>
        }
        filter={
          <SpaceBetween direction="horizontal" size="s">
            <Select
              selectedOption={groupFilter ? { value: groupFilter, label: groupFilter } : { value: "", label: "All groups" }}
              options={[
                { value: "", label: "All groups" },
                ...[...new Set((requests ?? []).map((r) => r.resourceName))].map((g) => ({ value: g, label: g })),
              ]}
              onChange={(e) => setGroupFilter(e.detail.selectedOption.value ?? "")}
            />
            <TextFilter
              filteringText={filter}
              filteringPlaceholder="Filter by resource, template, or status"
              onChange={(e) => setFilter(e.detail.filteringText)}
            />
          </SpaceBetween>
        }
        loading={requests === null}
        items={filtered}
        columnDefinitions={[
          {
            id: "id",
            header: "Request",
            cell: (r) => <RouterLink to={`/requests/${r.id}`}>{r.id.slice(0, 8)}</RouterLink>,
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
    </ContentLayout>
  );
}
