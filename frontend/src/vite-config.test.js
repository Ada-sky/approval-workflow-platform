// @vitest-environment node
import { afterEach, expect, test, vi } from "vitest";
import createConfig from "../vite.config.js";

afterEach(() => vi.unstubAllEnvs());

test("both proxies default to the local backend on port 8080", () => {
  vi.stubEnv("BACKEND_URL", undefined);
  const { proxy } = createConfig().server;
  expect(proxy["/api"].target).toBe("http://127.0.0.1:8080");
  expect(proxy["/login"].target).toBe(proxy["/api"].target);
});

test("BACKEND_URL overrides both proxy targets", () => {
  vi.stubEnv("BACKEND_URL", "http://127.0.0.1:18080");
  const { proxy } = createConfig().server;
  expect(proxy["/api"].target).toBe("http://127.0.0.1:18080");
  expect(proxy["/login"].target).toBe(proxy["/api"].target);
});
