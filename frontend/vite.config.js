import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
export default defineConfig(() => {
  const backendTarget = process.env.BACKEND_URL || "http://127.0.0.1:8080";
  return {
    plugins: [react()],
    server: {
      proxy: {
        "/api": { target: backendTarget },
        "/login": { target: backendTarget },
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test-setup.js"],
      clearMocks: true,
    },
  };
});
