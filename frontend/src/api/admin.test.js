import { afterEach, beforeEach, it, expect, vi } from "vitest";
import { adminApi } from "./admin";
import { resetCsrf } from "./client";
const response = (data, status = 200) => ({
  ok: status < 400,
  status,
  json: async () => data,
});
beforeEach(() => {
  resetCsrf();
  vi.stubGlobal("fetch", vi.fn());
});
afterEach(() => vi.unstubAllGlobals());
it("collects permission resources across pages without using menu admin access", async () => {
  fetch
    .mockResolvedValueOnce(response({ content: [{ id: 1 }], totalPages: 2 }))
    .mockResolvedValueOnce(response({ content: [{ id: 2 }], totalPages: 2 }));
  expect(await adminApi.all("permissions")).toEqual([{ id: 1 }, { id: 2 }]);
  expect(fetch.mock.calls.map(([url]) => url)).toEqual([
    "/api/permissions?page=0&size=100&name=",
    "/api/permissions?page=1&size=100&name=",
  ]);
});
it("uses centralized session and CSRF handling for grant replacement", async () => {
  fetch
    .mockResolvedValueOnce(
      response({ token: "csrf", headerName: "X-CSRF-TOKEN" }),
    )
    .mockResolvedValueOnce(response(null, 204));
  await adminApi.saveGrants(2, [300]);
  expect(fetch.mock.calls[1][0]).toBe("/api/roles/2/permissions");
  expect(fetch.mock.calls[1][1]).toMatchObject({
    method: "PUT",
    credentials: "include",
    headers: { "X-CSRF-TOKEN": "csrf" },
    body: '{"menuIds":[300]}',
  });
});
it("encodes search input instead of allowing query injection", async () => {
  fetch.mockResolvedValue(response({ content: [] }));
  await adminApi.list("employees", 0, "Alice&size=999");
  expect(fetch.mock.calls[0][0]).toContain("name=Alice%26size%3D999");
});
it("propagates backend authorization denial to administration UI", async () => {
  fetch.mockResolvedValue(response({ message: "Internal details" }, 403));
  await expect(adminApi.list("employees")).rejects.toMatchObject({
    status: 403,
  });
});

it("uses session and CSRF for leave type mutations", async () => {
  fetch
    .mockResolvedValueOnce(
      response({ token: "csrf", headerName: "X-CSRF-TOKEN" }),
    )
    .mockResolvedValueOnce(response({ id: 9, name: "Annual Leave" }, 201));
  await adminApi.save("admin/leave-types", undefined, { name: "Annual Leave" });
  expect(fetch.mock.calls[1][0]).toBe("/api/admin/leave-types");
  expect(fetch.mock.calls[1][1]).toMatchObject({
    method: "POST",
    credentials: "include",
    headers: { "X-CSRF-TOKEN": "csrf" },
  });
});
