import { Suspense, lazy, useEffect, useState } from "react";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import AppLayout from "@cloudscape-design/components/app-layout";
import Box from "@cloudscape-design/components/box";
import BreadcrumbGroup from "@cloudscape-design/components/breadcrumb-group";
import Flashbar, { FlashbarProps } from "@cloudscape-design/components/flashbar";
import SideNavigation, { SideNavigationProps } from "@cloudscape-design/components/side-navigation";
import Spinner from "@cloudscape-design/components/spinner";
import TopNavigation from "@cloudscape-design/components/top-navigation";
import { api } from "./api/client";
import type { Me } from "./api/types";
import { initAuth, signOut } from "./auth/auth";
import { MeContext, hasRole } from "./auth/useMe";
import RequireRole from "./components/RequireRole";
import { getDensity, getMode, toggleDensity, toggleMode } from "./theme";

// Route-level code splitting (2.01 enhancement); suspense fallback is a Cloudscape Spinner.
const DashboardPage = lazy(() => import("./pages/DashboardPage"));
const CatalogPage = lazy(() => import("./pages/CatalogPage"));
const CreateResourceWizard = lazy(() => import("./pages/CreateResourceWizard"));
const RequestsListPage = lazy(() => import("./pages/RequestsListPage"));
const RequestDetailPage = lazy(() => import("./pages/RequestDetailPage"));
const AuthCallbackPage = lazy(() => import("./pages/AuthCallbackPage"));
const SyncAdminPage = lazy(() => import("./pages/SyncAdminPage"));
const ApprovalsPage = lazy(() => import("./pages/ApprovalsPage"));
const AuditLogPage = lazy(() => import("./pages/AuditLogPage"));
const RunbooksPage = lazy(() => import("./pages/RunbooksPage"));
const TemplatesAdminPage = lazy(() => import("./pages/TemplatesAdminPage"));

export default function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [notifications, setNotifications] = useState<FlashbarProps.MessageDefinition[]>([]);
  const [mode, setMode] = useState(getMode());
  const [density, setDensity] = useState(getDensity());
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    if (window.location.pathname === "/auth/callback") {
      return; // no identity yet — the callback page finishes login and reloads
    }
    // Auth gate first: in cognito mode this may redirect to the Hosted UI and never resolve.
    initAuth()
      .then(() => api.me())
      .then(setMe)
      .catch(() =>
        setNotifications([
          {
            type: "error",
            header: "Could not load your identity",
            content: "Check that the API is reachable (or set VITE_USE_MOCKS=true).",
            id: "me-error",
          },
        ]),
      );
  }, []);

  const navItems: SideNavigationProps.Item[] = [
    { type: "link", text: "Dashboard", href: "/" },
    { type: "link", text: "Catalog", href: "/catalog" },
    { type: "link", text: "My Requests", href: "/requests" },
    { type: "link", text: "Deployments", href: "/deployments" },
    ...(hasRole(me, "APPROVER")
      ? [{ type: "link", text: "Approvals", href: "/approvals" } satisfies SideNavigationProps.Item]
      : []),
    ...(hasRole(me, "TEMPLATE_ADMIN", "PLATFORM_ADMIN")
      ? [
          { type: "link", text: "Templates", href: "/admin/templates" } satisfies SideNavigationProps.Item,
          { type: "link", text: "Sync history", href: "/admin/sync" } satisfies SideNavigationProps.Item,
        ]
      : []),
    { type: "divider" },
    { type: "link", text: "Observability (Grafana)", href: "https://grafana.example.internal", external: true },
    { type: "link", text: "Runbooks", href: "/runbooks" },
    { type: "link", text: "Audit Log", href: "/audit" },
  ];

  return (
    <MeContext.Provider value={me}>
      <TopNavigation
        identity={{ href: "/", title: "AWS Self-Service Platform" }}
        utilities={[
          {
            type: "menu-dropdown",
            iconName: "settings",
            ariaLabel: "Display settings",
            title: "Display",
            items: [
              { id: "mode", text: mode === "dark" ? "Switch to light mode" : "Switch to dark mode" },
              {
                id: "density",
                text: density === "compact" ? "Switch to comfortable density" : "Switch to compact density",
              },
            ],
            onItemClick: (e) => {
              if (e.detail.id === "mode") setMode(toggleMode());
              if (e.detail.id === "density") setDensity(toggleDensity());
            },
          },
          {
            type: "menu-dropdown",
            text: me?.email ?? "…",
            iconName: "user-profile",
            items: [{ id: "signout", text: "Sign out" }],
            onItemClick: (e) => {
              if (e.detail.id === "signout") signOut();
            },
          },
        ]}
      />
      <AppLayout
        toolsHide
        breadcrumbs={
          <BreadcrumbGroup
            items={breadcrumbsFor(location.pathname)}
            onFollow={(e) => {
              e.preventDefault();
              navigate(e.detail.href);
            }}
          />
        }
        notifications={<Flashbar items={notifications} />}
        navigation={
          <SideNavigation
            activeHref={location.pathname}
            items={navItems}
            onFollow={(e) => {
              if (!e.detail.external) {
                e.preventDefault();
                navigate(e.detail.href);
              }
            }}
          />
        }
        content={
          <Suspense
            fallback={
              <Box textAlign="center" padding="xxl">
                <Spinner size="large" />
              </Box>
            }
          >
            <Routes>
              <Route path="/auth/callback" element={<AuthCallbackPage />} />
              <Route path="/" element={<DashboardPage />} />
              <Route path="/catalog" element={<CatalogPage />} />
              <Route path="/catalog/:templateId" element={<CreateResourceWizard />} />
              <Route path="/requests" element={<RequestsListPage />} />
              <Route path="/requests/:id" element={<RequestDetailPage />} />
              <Route
                path="/approvals"
                element={
                  <RequireRole roles={["APPROVER", "PLATFORM_ADMIN"]}>
                    <ApprovalsPage />
                  </RequireRole>
                }
              />
              <Route
                path="/admin/templates"
                element={
                  <RequireRole roles={["TEMPLATE_ADMIN", "PLATFORM_ADMIN"]}>
                    <TemplatesAdminPage />
                  </RequireRole>
                }
              />
              <Route
                path="/admin/sync"
                element={
                  <RequireRole roles={["TEMPLATE_ADMIN", "PLATFORM_ADMIN"]}>
                    <SyncAdminPage />
                  </RequireRole>
                }
              />
              <Route path="/deployments" element={<ComingSoon title="ECS deployments (2.06 — phase 3)" />} />
              <Route path="/runbooks" element={<RunbooksPage />} />
              <Route
                path="/audit"
                element={
                  <RequireRole roles={["PLATFORM_ADMIN", "APPROVER"]}>
                    <AuditLogPage />
                  </RequireRole>
                }
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        }
      />
    </MeContext.Provider>
  );
}

function ComingSoon({ title }: { title: string }) {
  return (
    <Box textAlign="center" padding="xxl" color="text-status-inactive">
      <b>{title}</b>
      <Box variant="p">This screen lands in a later phase of the implementation checklist (9.2).</Box>
    </Box>
  );
}

function breadcrumbsFor(pathname: string) {
  const crumbs = [{ text: "Platform", href: "/" }];
  if (pathname.startsWith("/catalog")) crumbs.push({ text: "Catalog", href: "/catalog" });
  if (pathname.startsWith("/requests")) crumbs.push({ text: "My Requests", href: "/requests" });
  const parts = pathname.split("/").filter(Boolean);
  if (parts.length > 1) crumbs.push({ text: parts[1], href: pathname });
  return crumbs;
}
