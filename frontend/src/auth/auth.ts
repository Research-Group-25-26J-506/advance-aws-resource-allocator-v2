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
  environments?: string;
}

let config: RuntimeConfig = { authMode: "dev-bypass", issuer: "", clientId: "", cognitoDomain: "" };

/** Ordered environment chain, SSM-configured server-side (e.g. "DEV,QA,STG,PROD"). */
export function platformEnvironments(): string[] {
  return (config.environments ?? "DEV,QA,STG,PROD").split(",").map((e) => e.trim());
}
let manager: UserManager | null = null;
let currentUser: User | null = null;
let initPromise: Promise<AuthMode> | null = null;

/**
 * Redirect loop breaker. A stale session, an iframe renew, or a token the API keeps rejecting could
 * otherwise bounce the browser between the app and the Cognito Hosted UI forever ("just keeps
 * refreshing"). Cap sign-in redirects within a short window; past the cap we stop redirecting and
 * let the app render its auth error instead of looping.
 */
const REDIRECT_GUARD_KEY = "auth.redirects";
function loopingSignin(): boolean {
  const now = Date.now();
  let rec = { n: 0, t: now };
  try {
    const raw = sessionStorage.getItem(REDIRECT_GUARD_KEY);
    if (raw) rec = JSON.parse(raw);
  } catch {
    /* ignore malformed guard */
  }
  if (now - rec.t > 30_000) rec = { n: 0, t: now }; // window elapsed — reset
  rec.n += 1;
  sessionStorage.setItem(REDIRECT_GUARD_KEY, JSON.stringify(rec));
  return rec.n > 3;
}
function clearSigninGuard(): void {
  sessionStorage.removeItem(REDIRECT_GUARD_KEY);
}

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
    // NO automaticSilentRenew: it renews via a hidden iframe that loads the /auth/callback route,
    // which this SPA can't service (it runs signinRedirectCallback + a full replace) and which
    // loops. Expiry is handled by the global 401 handler (redirect — seamless via Cognito's cookie).
    automaticSilentRenew: false,
  });
  manager.events.addUserLoaded((user) => {
    currentUser = user;
  });

  if (window.location.pathname === "/auth/callback") {
    return "cognito"; // the callback route completes the flow
  }
  currentUser = await manager.getUser();
  if (!currentUser || currentUser.expired) {
    if (loopingSignin()) {
      // Loop breaker: stop bouncing to the Hosted UI. The app renders with no identity and its
      // own "couldn't load identity" notice, instead of an endless refresh.
      return "cognito";
    }
    await manager.signinRedirect({ state: window.location.pathname + window.location.search });
    return new Promise<AuthMode>(() => {}); // redirecting — freeze the app shell
  }
  clearSigninGuard(); // authenticated — reset the loop guard
  return "cognito";
}

/**
 * Called by the /auth/callback route. Returns the path the user originally asked for.
 * Memoised: the authorization code + state are single-use, so signinRedirectCallback() must run
 * exactly once — a second call (StrictMode, a remount) would throw "No matching state in storage".
 */
let completion: Promise<string> | null = null;
export function completeLogin(): Promise<string> {
  if (!completion) {
    completion = doCompleteLogin();
  }
  return completion;
}

async function doCompleteLogin(): Promise<string> {
  await initAuth(); // wait for the manager — the callback page mounts before init finishes
  if (!manager) {
    throw new Error("auth not initialised (dev-bypass mode has no callback)");
  }
  const user = await manager.signinRedirectCallback();
  currentUser = user;
  clearSigninGuard(); // sign-in succeeded — reset the loop guard
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
    if (loopingSignin()) {
      return; // loop breaker: the API keeps 401ing — don't bounce to the Hosted UI forever
    }
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
