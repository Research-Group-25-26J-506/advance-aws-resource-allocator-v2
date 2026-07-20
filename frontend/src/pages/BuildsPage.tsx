import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import Alert from "@cloudscape-design/components/alert";
import Autosuggest from "@cloudscape-design/components/autosuggest";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import Container from "@cloudscape-design/components/container";
import ContentLayout from "@cloudscape-design/components/content-layout";
import FormField from "@cloudscape-design/components/form-field";
import Header from "@cloudscape-design/components/header";
import Input from "@cloudscape-design/components/input";
import Popover from "@cloudscape-design/components/popover";
import SpaceBetween from "@cloudscape-design/components/space-between";
import StatusIndicator from "@cloudscape-design/components/status-indicator";
import Table from "@cloudscape-design/components/table";
import { api } from "../api/client";
import type { BuildRun } from "../api/types";
import { relativeTime } from "../util/time";

/**
 * Build from repo: the platform triggers CodeBuild to build the image from a git repo and push
 * to ECR - no local Docker. On success, deploy the service with the produced image tag.
 */
export default function BuildsPage() {
  const navigate = useNavigate();
  const [repo, setRepo] = useState("");
  const [ref, setRef] = useState("main");
  const [serviceName, setServiceName] = useState("");
  const [builds, setBuilds] = useState<BuildRun[] | null>(null);
  const [branches, setBranches] = useState<string[]>([]);
  const [loadingBranches, setLoadingBranches] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const refresh = useCallback(() => {
    api.listBuilds().then(setBuilds).catch(() => setBuilds([]));
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, 8000);
    return () => clearInterval(timer);
  }, [refresh]);

  // Load the repo's branches once a valid GitHub URL is entered, so the ref field becomes a picker.
  useEffect(() => {
    const valid = /^https:\/\/github\.com\/[\w.-]+\/[\w.-]+/.test(repo.trim());
    if (!valid) {
      setBranches([]);
      return;
    }
    setLoadingBranches(true);
    const t = setTimeout(() => {
      api
        .listBranches(repo.trim())
        .then(setBranches)
        .catch(() => setBranches([]))
        .finally(() => setLoadingBranches(false));
    }, 400);
    return () => clearTimeout(t);
  }, [repo]);

  const trigger = async () => {
    setBusy(true);
    setError(null);
    try {
      await api.triggerBuild(repo, ref, serviceName);
      setRepo("");
      setServiceName("");
      refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setBusy(false);
    }
  };

  const statusType = (s: string) =>
    s === "SUCCEEDED" ? "success" : s === "FAILED" ? "error" : "in-progress";

  return (
    <ContentLayout
      header={
        <Header
          variant="h1"
          description="The platform builds the container image from your git repo via CodeBuild and pushes it to ECR — no local Docker. The repo's org must be on the allowlist."
        >
          Build from repo
        </Header>
      }
    >
      <SpaceBetween size="l">
        {error && (
          <Alert type="error" dismissible onDismiss={() => setError(null)}>
            {error}
          </Alert>
        )}
        <Container header={<Header variant="h2">Trigger a build</Header>}>
          <SpaceBetween size="m">
            <FormField label="Git repository URL" description="https://github.com/<org>/<repo> — org must be allowlisted">
              <Input
                value={repo}
                onChange={(e) => setRepo(e.detail.value)}
                placeholder="https://github.com/Research-Group-25-26J-506/pastry-orders-api"
              />
            </FormField>
            <FormField
              label="Branch / ref"
              description={
                branches.length > 0
                  ? `${branches.length} branch${branches.length > 1 ? "es" : ""} loaded — pick one or type a tag/SHA`
                  : "Enter the repo above to load its branches (public repos), or type a ref"
              }
            >
              <Autosuggest
                value={ref}
                onChange={(e) => setRef(e.detail.value)}
                options={branches.map((b) => ({ value: b }))}
                statusType={loadingBranches ? "loading" : "finished"}
                loadingText="Loading branches…"
                placeholder="main"
                enteredTextLabel={(v) => `Use "${v}"`}
                empty="No branches loaded (private repo? just type a ref)"
              />
            </FormField>
            <FormField label="Service name" constraintText="Names the image tag.">
              <Input value={serviceName} onChange={(e) => setServiceName(e.detail.value)} placeholder="pastry-orders-api" />
            </FormField>
            <Button variant="primary" loading={busy} onClick={trigger} disabled={!repo || !serviceName}>
              Build image
            </Button>
          </SpaceBetween>
        </Container>

        <Table
          header={
            <Header counter={builds ? `(${builds.length})` : undefined} actions={<Button iconName="refresh" onClick={refresh} ariaLabel="Refresh" />}>
              Builds
            </Header>
          }
          loading={builds === null}
          items={builds ?? []}
          columnDefinitions={[
            { id: "service", header: "Service", cell: (b) => b.serviceName },
            { id: "repo", header: "Repo", cell: (b) => b.repo.replace("https://github.com/", "") },
            { id: "ref", header: "Ref", cell: (b) => b.ref },
            {
              id: "image",
              header: "Image URI",
              cell: (b) => (
                <SpaceBetween direction="horizontal" size="xs">
                  <Box variant="code" fontSize="body-s">
                    {b.imageUri}
                  </Box>
                  <Popover
                    dismissButton={false}
                    position="top"
                    size="small"
                    triggerType="custom"
                    content="Copied to clipboard"
                  >
                    <Button
                      iconName="copy"
                      variant="inline-icon"
                      ariaLabel="Copy image URI"
                      onClick={() => navigator.clipboard?.writeText(b.imageUri)}
                    />
                  </Popover>
                </SpaceBetween>
              ),
            },
            {
              id: "status",
              header: "Status",
              cell: (b) => <StatusIndicator type={statusType(b.status)}>{b.status}</StatusIndicator>,
            },
            { id: "started", header: "Started", cell: (b) => <span title={b.startedAt}>{relativeTime(b.startedAt)}</span> },
            {
              id: "actions",
              header: "",
              cell: (b) => (
                <Button
                  disabled={b.status !== "SUCCEEDED"}
                  onClick={() =>
                    navigate(
                      `/deployments?image=${encodeURIComponent(b.imageUri)}&serviceName=${encodeURIComponent(
                        b.serviceName,
                      )}`,
                    )
                  }
                >
                  Deploy this image
                </Button>
              ),
            },
          ]}
          empty={<Box textAlign="center" padding="l">No builds yet — trigger one above.</Box>}
        />
      </SpaceBetween>
    </ContentLayout>
  );
}
