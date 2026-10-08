import { readFileSync } from "node:fs";
import { URL } from "node:url";
import { describe, expect, it } from "vitest";
const read = (path) => readFileSync(new URL(path, import.meta.url), "utf8");
describe("Docker configuration", () => {
  it("preserves same-origin authentication, CSRF and API errors", () => {
    const config = read("../nginx.conf");
    for (const path of [
      "location = /api",
      "location ^~ /api/",
      "location = /login",
      "location = /logout",
    ])
      expect(config).toContain(path);
    expect(config).toContain("proxy_pass http://backend:8080;");
    expect(config).toContain("proxy_intercept_errors off;");
    expect(config).toContain("try_files $uri $uri/ /index.html;");
    expect(config).not.toContain("proxy_cookie_domain");
  });
  it("builds static files without database secrets", () => {
    const config = read("../Dockerfile");
    expect(config).toContain("npm ci");
    expect(config).toContain("npm run build");
    expect(config).toContain("/app/dist");
    expect(config).not.toContain("DB_PASSWORD");
    expect(read("../.dockerignore")).toContain("**/.env");
  });
});
