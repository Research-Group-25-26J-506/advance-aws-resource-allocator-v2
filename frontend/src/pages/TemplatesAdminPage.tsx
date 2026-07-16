import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { Template } from "../api/types";

/** Templates admin (2.08 scoped): registry inventory; publishing happens via sync. */
export default function TemplatesAdminPage() {
  const navigate = useNavigate();
  const [templates, setTemplates] = useState<Template[] | null>(null);

  useEffect(() => {
    api.listTemplates().then(setTemplates).catch(() => setTemplates([]));
  }, []);

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="Registry inventory. New versions arrive exclusively through template sync — versions are immutable once published."
          actions={<Button onClick={() => navigate("/admin/sync")}>Go to sync</Button>}
        >
          Templates
        </Header>
      }
    >
      <Table
        loading={templates === null}
        items={templates ?? []}
        columnDefinitions={[
          { id: "id", header: "Template ID", cell: (t) => t.id },
          { id: "name", header: "Name", cell: (t) => t.displayName },
          { id: "category", header: "Category", cell: (t) => <Badge color="blue">{t.category}</Badge> },
          {
            id: "maturity",
            header: "Maturity",
            cell: (t) => <Badge color={t.maturity === "stable" ? "green" : "grey"}>{t.maturity}</Badge>,
          },
          { id: "version", header: "Latest version", cell: (t) => t.latestVersion ?? "—" },
          { id: "description", header: "Description", cell: (t) => t.description },
        ]}
        empty={<Box textAlign="center" padding="l">No templates registered yet — run a sync.</Box>}
      />
    </ContentLayout>
  );
}
