import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import Alert from "@cloudscape-design/components/alert";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import Modal from "@cloudscape-design/components/modal";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Table from "@cloudscape-design/components/table";
import Textarea from "@cloudscape-design/components/textarea";
import { api } from "../api/client";
import type { PendingApproval } from "../api/types";
import { relativeTime } from "../util/time";

/** Approvals inbox (2.07): PROD requests park here until an approver decides. */
export default function ApprovalsPage() {
  const [items, setItems] = useState<PendingApproval[] | null>(null);
  const [rejecting, setRejecting] = useState<PendingApproval | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.listApprovals().then(setItems).catch((e) => setError(String(e)));
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 15_000);
    return () => clearInterval(timer);
  }, [refresh]);

  const decide = async (action: "approve" | "reject", item: PendingApproval) => {
    setBusy(true);
    setError(null);
    try {
      if (action === "approve") await api.approveRequest(item.id);
      else await api.rejectRequest(item.id, reason || "No reason given");
      setRejecting(null);
      setReason("");
      refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <ContentLayout
      header={
        <Header variant="h1" description="PROD provisioning waits here until an approver signs off.">
          Approvals
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
          header={<Header counter={items ? `(${items.length})` : undefined}>Pending requests</Header>}
          loading={items === null}
          items={items ?? []}
          columnDefinitions={[
            {
              id: "id",
              header: "Request",
              cell: (r) => <RouterLink to={`/requests/${r.id}`}>{r.id.slice(0, 8)}</RouterLink>,
            },
            { id: "resource", header: "Resource", cell: (r) => r.resourceName },
            { id: "template", header: "Template", cell: (r) => r.templateId },
            { id: "env", header: "Environment", cell: (r) => <Badge color="red">{r.environment}</Badge> },
            { id: "requester", header: "Requested by", cell: (r) => r.requesterEmail },
            {
              id: "submitted",
              header: "Waiting",
              cell: (r) => <span title={r.submittedAt}>{relativeTime(r.submittedAt)}</span>,
            },
            {
              id: "actions",
              header: "Decision",
              cell: (r) => (
                <SpaceBetween direction="horizontal" size="xs">
                  <Button variant="primary" onClick={() => decide("approve", r)} loading={busy}>
                    Approve
                  </Button>
                  <Button onClick={() => setRejecting(r)}>Reject</Button>
                </SpaceBetween>
              ),
            },
          ]}
          empty={<Box textAlign="center" padding="l">Nothing waiting for approval.</Box>}
        />
      </SpaceBetween>

      <Modal
        visible={rejecting !== null}
        onDismiss={() => setRejecting(null)}
        header={`Reject ${rejecting?.resourceName ?? ""}`}
        footer={
          <Box float="right">
            <SpaceBetween direction="horizontal" size="xs">
              <Button variant="link" onClick={() => setRejecting(null)}>
                Cancel
              </Button>
              <Button
                variant="primary"
                loading={busy}
                onClick={() => rejecting && decide("reject", rejecting)}
              >
                Reject request
              </Button>
            </SpaceBetween>
          </Box>
        }
      >
        <SpaceBetween size="s">
          <Box>The requester will see this reason on the request page.</Box>
          <Textarea value={reason} onChange={(e) => setReason(e.detail.value)} rows={3} />
        </SpaceBetween>
      </Modal>
    </ContentLayout>
  );
}
