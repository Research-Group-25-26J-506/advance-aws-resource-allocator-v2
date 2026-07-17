import { User, UserManager, WebStorageStateStore } from "oidc-client-ts";

/**
 * Runtime auth (2.01): mode comes from the public /api/v1/config endpoint, so one build serves
 * every environment. "dev-bypass" (local profile / mocks) needs no tokens; "cognito" runs the
 * OIDC code+PKCE flow against the Hosted UI via oidc-client-ts.
 */
export type AuthMode = "dev-bypass" | "cognito";

interface RuntimeConfig {
  authMode: AuthMode;
  issuer: string;
  clientId: string;
  cognitoDomain: string;
}

let config: RuntimeConfig = { authMode: "dev-bypass", issuer: "", clientId: "", cognitoDomain: "" };
let manager: UserManager | null = null;
let currentUser: User | null = null;
let initPromise: Promise<AuthMode> | null = null;

/** Idempotent: concurrent callers (app shell + callback page) share one initialisation. */
export function initAuth(): Promise<AuthMode> {
  if (!initPromise) {
    initPromise = doInit();
  }
  return initPromise;
}

/** Resolves with the active mode; never resolves if a login redirect is in flight. */
async function doInit(): Promise<AuthMode> {
  if (import.meta.env.VITE_USE_MOCKS === "true") {
    return "dev-bypass";
  }
  try {
    const response = await fetch("/api/v1/config");
    if (response.ok) {
      config = await response.json();
    }
  } catch {
    // API unreachable — leave dev-bypass; the shell will surface the fetch error itself
  }
  if (config.authMode !== "cognito") {
    return "dev-bypass";
  }

  manager = new UserManager({
    authority: config.issuer,
    client_id: config.clientId,
    redirect_uri: `${window.location.origin}/auth/callback`,
    response_type: "code",
    scope: "openid email profile",
    userStore: new WebStorageStateStore({ store: window.sessionStorage }),
    automaticSilentRenew: true,
  });
  manager.events.addUserLoaded((user) => {
    currentUser = user;
  });

  if (window.location.pathname === "/auth/callback") {
    return "cognito"; // the callback route completes the flow
  }
  currentUser = await manager.getUser();
  if (!currentUser || currentUser.expired) {
    await manager.signinRedirect({ state: window.location.pathname + window.location.search });
    return new Promise<AuthMode>(() => {}); // redirecting — freeze the app shell
  }
  return "cognito";
}

/** Called by the /auth/callback route. Returns the path the user originally asked for. */
export async function completeLogin(): Promise<string> {
  await initAuth(); // wait for the manager — the callback page mounts before init finishes
  if (!manager) {
    throw new Error("auth not initialised (dev-bypass mode has no callback)");
  }
  const user = await manager.signinRedirectCallback();
  currentUser = user;
  return typeof user.state === "string" && user.state.startsWith("/") ? user.state : "/";
}

export function accessToken(): string | null {
  return currentUser && !currentUser.expired ? currentUser.access_token : null;
}

/**
 * Global 401 handler: an expired/missing session in cognito mode re-runs the login redirect
 * (usually silent — Cognito's own session cookie survives token expiry). Dev-bypass: no-op.
 */
export function handleUnauthorized(): void {
  if (config.authMode === "cognito" && manager) {
    manager.signinRedirect({ state: window.location.pathname + window.location.search }).catch(() => {
      window.location.href = "/"; // last resort: full reload restarts the auth gate
    });
  }
}

export function signOut(): void {
  if (config.authMode !== "cognito") {
    return; // nothing to sign out of locally
  }
  sessionStorage.clear();
  const logoutUri = encodeURIComponent(window.location.origin);
  window.location.href = `${config.cognitoDomain}/logout?client_id=${config.clientId}&logout_uri=${logoutUri}`;
}
