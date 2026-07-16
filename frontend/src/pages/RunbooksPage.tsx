import { useEffect, useState } from "react";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Link from "@cloudscape-design/components/link";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { Runbook } from "../api/types";

const REPO_BASE = "https://github.com/Research-Group-25-26J-506/advance-aws-resource-allocator-v2/blob/develop/";

/** Runbook viewer (2.13 scoped): registry list linking to the runbook markdown in the repo. */
export default function RunbooksPage() {
  const [runbooks, setRunbooks] = useState<Runbook[] | null>(null);

  useEffect(() => {
    api.listRunbooks().then(setRunbooks).catch(() => setRunbooks([]));
  }, []);

  return (
    <ContentLayout
      header={
        <Header variant="h1" description="Operational runbooks — one per alert, kept in the repo.">
          Runbooks
        </Header>
      }
    >
      <Table
        loading={runbooks === null}
        items={runbooks ?? []}
        columnDefinitions={[
          {
            id: "title",
            header: "Runbook",
            cell: (r) => (
              <Link href={REPO_BASE + r.path} external>
                {r.title}
              </Link>
            ),
          },
          {
            id: "severity",
            header: "Severity",
            cell: (r) => <Badge color={r.severity === "SEV1" ? "red" : "grey"}>{r.severity}</Badge>,
          },
          { id: "path", header: "Path", cell: (r) => r.path },
        ]}
        empty={<Box textAlign="center" padding="l">No runbooks registered.</Box>}
      />
    </ContentLayout>
  );
}
