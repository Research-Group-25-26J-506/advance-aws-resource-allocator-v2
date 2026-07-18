import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Header from "@cloudscape-design/components/header";
import SpaceBetween from "@cloudscape-design/components/space-between";
import Spinner from "@cloudscape-design/components/spinner";
import StatusIndicator from "@cloudscape-design/components/status-indicator";
import { api } from "../api/client";
import type { PlatformRequest } from "../api/types";
import { statusIndicatorType } from "../components/PlatformStatus";
import { platformEnvironments } from "../auth/auth";

/**
 * Service topology board (the "Figma-board" view): each group rendered as a pipeline —
 * a source node, then one stage per environment showing the resources deployed there and their
 * status, with promotion arrows between stages. Nodes are clickable → the request.
 */
export default function TopologyPage() {
  const navigate = useNavigate();
  const [requests, setRequests] = useState<PlatformRequest[] | null>(null);
  const [groups, setGroups] = useState<{ name: string }[]>([]);

  useEffect(() => {
    api.listMyRequests(200).then(setRequests).catch(() => setRequests([]));
    api.listGroups().then(setGroups).catch(() => undefined);
  }, []);

  const envChain = platformEnvironments();

  // group -> env -> [latest request per template]
  const model = useMemo(() => {
    const byGroup = new Map<string, Map<string, Map<string, PlatformRequest>>>();
    for (const g of groups) byGroup.set(g.name, new Map());
    for (const r of requests ?? []) {
      const envs = byGroup.get(r.resourceName) ?? new Map();
      const perTemplate = envs.get(r.environment) ?? new Map();
      const cur = perTemplate.get(r.templateId);
      if (!cur || r.submittedAt > cur.submittedAt) perTemplate.set(r.templateId, r);
      envs.set(r.environment, perTemplate);
      byGroup.set(r.resourceName, envs);
    }
    return [...byGroup.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [requests, groups]);

  if (requests === null) {
    return (
      <Box textAlign="center" padding="xxl">
        <Spinner size="large" />
      </Box>
    );
  }

  const stageStyle = (active: boolean): React.CSSProperties => ({
    minWidth: 180,
    border: `1px solid ${active ? "#0972d3" : "#444"}`,
    borderRadius: 10,
    padding: 12,
    background: active ? "rgba(9,114,211,0.08)" : "transparent",
  });

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="Each group as a deployment pipeline — source → environments → resources. Click any node to open it."
          actions={<Button iconName="refresh" onClick={() => api.listMyRequests(200).then(setRequests)} ariaLabel="Refresh" />}
        >
          Topology
        </Header>
      }
    >
      <SpaceBetween size="l">
        {model.length === 0 && (
          <Box textAlign="center" padding="l" color="text-status-inactive">
            Nothing to visualize yet — provision a resource or create a group.
          </Box>
        )}
        {model.map(([group, envs]) => (
          <Container key={group} header={<Header variant="h2">{group}</Header>}>
            <div style={{ display: "flex", alignItems: "stretch", gap: 8, overflowX: "auto", paddingBottom: 8 }}>
              {/* source node */}
              <div style={{ ...stageStyle(false), display: "flex", flexDirection: "column", justifyContent: "center" }}>
                <Box variant="awsui-key-label">Source</Box>
                <Box fontWeight="bold">{group}</Box>
                <Box fontSize="body-s" color="text-status-inactive">repo → build → image</Box>
              </div>
              {envChain.map((env, idx) => {
                const perTemplate = envs.get(env);
                const nodes = perTemplate ? [...perTemplate.values()] : [];
                return (
                  <div key={env} style={{ display: "flex", alignItems: "center", gap: 8 }}>
                    <Box color="text-status-inactive" fontSize="heading-l">→</Box>
                    <div style={stageStyle(nodes.length > 0)}>
                      <Box variant="awsui-key-label">{env}</Box>
                      {nodes.length === 0 ? (
                        <Box fontSize="body-s" color="text-status-inactive" padding={{ top: "xs" }}>
                          not deployed
                        </Box>
                      ) : (
                        <SpaceBetween size="xxs">
                          {nodes.map((r) => (
                            <div
                              key={r.templateId}
                              onClick={() => navigate(`/requests/${r.id}`)}
                              style={{ cursor: "pointer", padding: "4px 6px", borderRadius: 6, background: "rgba(255,255,255,0.03)" }}
                            >
                              <SpaceBetween direction="horizontal" size="xxs" alignItems="center">
                                <Badge color="blue">{r.templateId}</Badge>
                                <StatusIndicator type={statusIndicatorType(r.status)}> </StatusIndicator>
                              </SpaceBetween>
                            </div>
                          ))}
                        </SpaceBetween>
                      )}
                    </div>
                    {idx === envChain.length - 1 && <div />}
                  </div>
                );
              })}
            </div>
          </Container>
        ))}
      </SpaceBetween>
    </ContentLayout>
  );
}
