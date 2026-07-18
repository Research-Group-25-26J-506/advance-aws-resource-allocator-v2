import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// Dev server proxies /api to the local Spring Boot api service (8.06).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      "/api": {
        target: "http://localhost:8090", // api's `local` profile port (8080 clashes with wslrelay)
        changeOrigin: true,
      },
    },
  },
  build: {
    sourcemap: true,
  },
  test: {
    // Vitest runs unit tests only. The Playwright e2e specs live under e2e/ and import
    // @playwright/test — they must NOT be collected here or Playwright's test() throws.
    include: ["src/**/*.{test,spec}.{ts,tsx}"],
    exclude: ["e2e/**", "node_modules/**", "dist/**"],
  },
});
