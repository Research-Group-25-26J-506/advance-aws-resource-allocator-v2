import { defineConfig, devices } from "@playwright/test";

/**
 * E2E config (Phase A). Runs against the Vite dev server in mock mode (VITE_USE_MOCKS=true),
 * so no backend or AWS is needed — it proves the UI's core flows in CI.
 */
export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  fullyParallel: true,
  reporter: process.env.CI ? "github" : "list",
  use: { baseURL: "http://localhost:5173", trace: "on-first-retry" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    env: { VITE_USE_MOCKS: "true" },
    timeout: 60_000,
  },
});
