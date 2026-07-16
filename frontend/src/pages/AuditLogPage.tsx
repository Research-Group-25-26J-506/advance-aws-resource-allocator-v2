import { useEffect, useState } from "react";
import Box from "@cloudscape-design/components/box";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Table from "@cloudscape-design/components/table";
import TextFilter from "@cloudscape-design/components/text-filter";
import { api } from "../api/client";
import type { AuditEntry } from "../api/types";

/** Audit log (2.11 scoped): last 200 state-changing actions, text-filterable. */
export default function AuditLogPage() {
  const [entries, setEntries] = useState<AuditEntry[] | null>(null);
  const [filter, setFilter] = useState("");

  useEffect(() => {
    api.listAudit().then(setEntries).catch(() => setEntries([]));
  }, []);

  const filtered = (entries ?? []).filter(
    (e) =>
      filter === "" ||
      e.action.toLowerCase().includes(filter.toLowerCase()) ||
      e.actorId.toLowerCase().includes(filter.toLowerCase()) ||
      e.subjectId.includes(filter),
  );

  return (
    <ContentLayout header={<Header variant="h1">Audit log</Header>}>
      <Table
        header={<Header counter={entries ? `(${filtered.length})` : undefined}>State-changing actions</Header>}
        filter={
          <TextFilter
            filteringText={filter}
            filteringPlaceholder="Filter by action, actor, or subject"
            onChange={(e) => setFilter(e.detail.filteringText)}
          />
        }
        loading={entries === null}
        items={filtered}
        columnDefinitions={[
          { id: "at", header: "When", cell: (e) => e.occurredAt },
          { id: "actor", header: "Actor", cell: (e) => e.actorId },
          { id: "action", header: "Action", cell: (e) => e.action },
          { id: "subject", header: "Subject", cell: (e) => `${e.subjectType} ${e.subjectId.slice(0, 8)}` },
          { id: "detail", header: "Detail", cell: (e) => e.detail },
        ]}
        empty={<Box textAlign="center" padding="l">No audit entries yet.</Box>}
      />
    </ContentLayout>
  );
}
