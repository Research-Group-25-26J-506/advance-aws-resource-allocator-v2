import { defineConfig } from "vite";
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
});
