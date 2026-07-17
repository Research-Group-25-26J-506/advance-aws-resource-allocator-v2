import { useEffect, useMemo, useState } from "react";
import { Link as RouterLink, useNavigate } from "react-router-dom";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import Container from "@cloudscape-design/components/container";
import FormField from "@cloudscape-design/components/form-field";
import Input from "@cloudscape-design/components/input";
import Modal from "@cloudscape-design/components/modal";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Spinner from "@cloudscape-design/components/spinner";
import { api } from "../api/client";
import type { PlatformRequest } from "../api/types";
import PlatformStatus from "../components/PlatformStatus";
import { platformEnvironments } from "../auth/auth";

/**
 * Resource groups: a group = every resource sharing one name, across templates and
 * environments (delta = its S3 bucket in DEV + QA + the queue you name delta later).
 * Derived entirely from requests — the latest request per (name, template, env) wins.
 */
export default function ResourceGroupsPage() {
  const navigate = useNavigate();
  const [requests, setRequests] = useState<PlatformRequest[] | null>(null);
  const [declaredGroups, setDeclaredGroups] = useState<{ name: string; description: string }[]>([]);
  const [createVisible, setCreateVisible] = useState(false);
  const [newName, setNewName] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = () => {
    api.listMyRequests(100).then(setRequests).catch(() => setRequests([]));
    api.listGroups().then(setDeclaredGroups).catch(() => undefined);
  };
  useEffect(refresh, []);

  const createGroup = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.createGroup(newName, newDescription);
      setCreateVisible(false);
      setNewName("");
      setNewDescription("");
      refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  const groups = useMemo(() => {
    const byName = new Map<string, Map<string, Map<string, PlatformRequest>>>();
    // declared-but-empty groups appear first-class, ready to receive resources
    for (const group of declaredGroups) {
      byName.set(group.name, new Map());
    }
    for (const request of requests ?? []) {
      const templates = byName.get(request.resourceName) ?? new Map();
      const envs = templates.get(request.templateId) ?? new Map();
      const existing = envs.get(request.environment);
      if (!existing || request.submittedAt > existing.submittedAt) {
        envs.set(request.environment, request);
      }
      templates.set(request.templateId, envs);
      byName.set(request.resourceName, templates);
    }
    return [...byName.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [requests, declaredGroups]);

  const envChain = platformEnvironments();

  if (requests === null) {
    return (
      <Box textAlign="center" padding="xxl">
        <Spinner size="large" />
      </Box>
    );
  }

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="One group per resource name — its resources across every environment. Promote from any environment's request page."
          actions={
            <SpaceBetween direction="horizontal" size="xs">
              <Button iconName="refresh" onClick={refresh} ariaLabel="Refresh" />
              <Button onClick={() => setCreateVisible(true)}>Create resource group</Button>
              <Button variant="primary" onClick={() => navigate("/catalog")}>
                Provision a resource
              </Button>
            </SpaceBetween>
          }
        >
          Resources
        </Header>
      }
    >
      <SpaceBetween size="l">
        {groups.length === 0 && (
          <Box textAlign="center" padding="l" color="text-status-inactive">
            Nothing provisioned yet — start in the catalog.
          </Box>
        )}
        {groups.map(([name, templates]) => (
          <Container
            key={name}
            header={
              <Header
                variant="h2"
                counter={`(${templates.size} resource${templates.size > 1 ? "s" : ""})`}
                actions={
                  <Button onClick={() => navigate(`/catalog?group=${encodeURIComponent(name)}`)}>
                    Provision in this group
                  </Button>
                }
              >
                {name}
              </Header>
            }
          >
            <SpaceBetween size="s">
              {templates.size === 0 && (
                <Box color="text-status-inactive">
                  Empty group — use "Provision in this group" to attach its first resource.
                </Box>
              )}
              {[...templates.entries()].map(([templateId, envs]) => (
                <SpaceBetween key={templateId} direction="horizontal" size="m" alignItems="center">
                  <Box fontWeight="bold" padding={{ right: "s" }}>
                    <Badge color="blue">{templateId}</Badge>
                  </Box>
                  {envChain.map((env) => {
                    const request = envs.get(env);
                    return request ? (
                      <RouterLink key={env} to={`/requests/${request.id}`} style={{ textDecoration: "none" }}>
                        <SpaceBetween direction="horizontal" size="xxs" alignItems="center">
                          <Box fontSize="body-s" color="text-status-inactive">
                            {env}:
                          </Box>
                          <PlatformStatus status={request.status} />
                        </SpaceBetween>
                      </RouterLink>
                    ) : (
                      <Box key={env} fontSize="body-s" color="text-status-inactive">
                        {env}: —
                      </Box>
                    );
                  })}
                </SpaceBetween>
              ))}
            </SpaceBetween>
          </Container>
        ))}
      </SpaceBetween>

      <Modal
        visible={createVisible}
        onDismiss={() => setCreateVisible(false)}
        header="Create resource group"
        footer={
          <Box float="right">
            <SpaceBetween direction="horizontal" size="xs">
              <Button variant="link" onClick={() => setCreateVisible(false)}>
                Cancel
              </Button>
              <Button variant="primary" loading={busy} onClick={createGroup}>
                Create group
              </Button>
            </SpaceBetween>
          </Box>
        }
      >
        <SpaceBetween size="m">
          {error && <Box color="text-status-error">{error}</Box>}
          <FormField label="Group name" constraintText="Lowercase letters, digits, hyphens; 3–63 chars.">
            <Input value={newName} onChange={(e) => setNewName(e.detail.value)} placeholder="pastry-plus" />
          </FormField>
          <FormField label="Description — optional">
            <Input value={newDescription} onChange={(e) => setNewDescription(e.detail.value)} />
          </FormField>
          <Box color="text-status-inactive" fontSize="body-s">
            Step 2: open the group and use "Provision in this group" — every resource provisioned
            under this name becomes a member, per environment.
          </Box>
        </SpaceBetween>
      </Modal>
    </ContentLayout>
  );
}
