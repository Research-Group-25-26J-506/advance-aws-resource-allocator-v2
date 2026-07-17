import { useEffect, useMemo, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import Badge from "@cloudscape-design/components/badge";
import Box from "@cloudscape-design/components/box";
import Button from "@cloudscape-design/components/button";
import Cards from "@cloudscape-design/components/cards";
import ContentLayout from "@cloudscape-design/components/content-layout";
import Grid from "@cloudscape-design/components/grid";
import Header from "@cloudscape-design/components/header";
import SideNavigation from "@cloudscape-design/components/side-navigation";
import SpaceBetween from "@cloudscape-design/components/space-between";
import TextFilter from "@cloudscape-design/components/text-filter";
import { api } from "../api/client";
import type { Template } from "../api/types";

const CATEGORIES = ["All", "Storage", "Compute", "Database", "Networking", "Integration", "Security", "Monitoring"];

/**
 * Resource catalog (2.03). Filter state round-trips through URL query params so views are
 * shareable; category AND text filters compose.
 */
export default function CatalogPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const [templates, setTemplates] = useState<Template[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const category = searchParams.get("category") ?? "All";
  const text = searchParams.get("q") ?? "";

  useEffect(() => {
    api.listTemplates().then(setTemplates).catch((e) => setError(String(e)));
  }, []);

  const filtered = useMemo(
    () =>
      (templates ?? []).filter(
        (t) =>
          (category === "All" || t.category === category) &&
          (text === "" ||
            t.displayName.toLowerCase().includes(text.toLowerCase()) ||
            t.description.toLowerCase().includes(text.toLowerCase())),
      ),
    [templates, category, text],
  );

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(searchParams);
    if (value && value !== "All") next.set(key, value);
    else next.delete(key);
    setSearchParams(next, { replace: true });
  };

  return (
    <ContentLayout
      header={
        <Header variant="h1" actions={<Button>Request a new template</Button>}>
          Resource catalog
        </Header>
      }
    >
      <Grid gridDefinition={[{ colspan: { default: 12, s: 3 } }, { colspan: { default: 12, s: 9 } }]}>
        <SideNavigation
          activeHref={`#${category}`}
          items={CATEGORIES.map((c) => ({ type: "link", text: c, href: `#${c}` }))}
          onFollow={(e) => {
            e.preventDefault();
            setParam("category", e.detail.href.slice(1));
          }}
        />
        <SpaceBetween size="m">
          <TextFilter
            filteringText={text}
            filteringPlaceholder="Find a template"
            onChange={(e) => setParam("q", e.detail.filteringText)}
          />
          {error && <Box color="text-status-error">{error}</Box>}
          <Cards
            loading={templates === null}
            items={filtered}
            cardDefinition={{
              header: (t) => (
                <SpaceBetween direction="horizontal" size="xs">
                  <Box fontWeight="bold">{t.displayName}</Box>
                </SpaceBetween>
              ),
              sections: [
                { id: "description", content: (t) => t.description },
                {
                  id: "badges",
                  content: (t) => (
                    <SpaceBetween direction="horizontal" size="xs">
                      <Badge color="blue">{t.category}</Badge>
                      <Badge color={t.maturity === "stable" ? "green" : "grey"}>{t.maturity}</Badge>
                      {t.latestVersion && <Badge>{`v${t.latestVersion}`}</Badge>}
                    </SpaceBetween>
                  ),
                },
                {
                  id: "actions",
                  content: (t) => (
                    <Button
                      variant="inline-link"
                      onClick={() => {
                        const group = searchParams.get("group");
                        navigate(`/catalog/${t.id}${group ? `?group=${encodeURIComponent(group)}` : ""}`);
                      }}
                    >
                      Provision this
                    </Button>
                  ),
                },
              ],
            }}
            cardsPerRow={[{ cards: 1 }, { minWidth: 500, cards: 2 }, { minWidth: 800, cards: 3 }, { minWidth: 1200, cards: 4 }]}
            empty={
              <Box textAlign="center" padding="l">
                <SpaceBetween size="s">
                  <b>No templates match the current filters</b>
                  <Button
                    onClick={() => {
                      setSearchParams(new URLSearchParams(), { replace: true });
                    }}
                  >
                    Clear filters
                  </Button>
                </SpaceBetween>
              </Box>
            }
          />
        </SpaceBetween>
      </Grid>
    </ContentLayout>
  );
}
