import { beforeEach, afterEach, describe, it, expect, vi } from "vitest";
import { request, api, resetCsrf, onUnauthorized } from "./client";
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
describe("session API client", () => {
  it("sends session cookies", async () => {
    fetch.mockResolvedValue(response({ id: 1 }));
    await request("/api/auth/me");
    expect(fetch.mock.calls[0][1].credentials).toBe("include");
  });
  it("obtains CSRF for writes and sends only the supplied body", async () => {
    fetch
      .mockResolvedValueOnce(
        response({ headerName: "X-CSRF-TOKEN", token: "secure" }),
      )
      .mockResolvedValueOnce(response(null, 204));
    await api.decide("task/1", "approve", { leaveRequestId: 9, comment: "OK" });
    expect(fetch.mock.calls[1][0]).toBe(
      "/api/approvals/tasks/task%2F1/approve",
    );
    expect(fetch.mock.calls[1][1].headers["X-CSRF-TOKEN"]).toBe("secure");
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({
      leaveRequestId: 9,
      comment: "OK",
    });
  });
  it("rotates CSRF after form login before loading the user", async () => {
    fetch
      .mockResolvedValueOnce(
        response({ headerName: "X-CSRF-TOKEN", token: "before" }),
      )
      .mockResolvedValueOnce(response({ message: "legacy" }))
      .mockResolvedValueOnce(
        response({ headerName: "X-CSRF-TOKEN", token: "after" }),
      )
      .mockResolvedValueOnce(response({ username: "employee" }));
    expect(await api.login("employee", "secret")).toEqual({
      username: "employee",
    });
    expect(fetch.mock.calls.map(([url]) => url)).toEqual([
      "/api/auth/csrf",
      "/login",
      "/api/auth/csrf",
      "/api/auth/me",
    ]);
    expect(fetch.mock.calls[1][1].body.get("userName")).toBe("employee");
  });
  it("notifies authentication state on 401", async () => {
    const handler = vi.fn();
    const dispose = onUnauthorized(handler);
    fetch.mockResolvedValue(response({}, 401));
    await expect(api.me()).rejects.toMatchObject({ status: 401 });
    expect(handler).toHaveBeenCalledOnce();
    dispose();
  });
  it.each([403, 404, 409, 500])(
    "uses English safe feedback for %s",
    async (status) => {
      fetch.mockResolvedValue(response({ message: "SQL secret" }, status));
      await expect(api.me()).rejects.toMatchObject({ status });
      try {
        await api.me();
      } catch (e) {
        expect(e.message).not.toContain("SQL secret");
      }
    },
  );
  it("does not retry failed decisions", async () => {
    fetch
      .mockResolvedValueOnce(
        response({ headerName: "X-CSRF-TOKEN", token: "x" }),
      )
      .mockResolvedValueOnce(response({}, 409));
    await expect(
      api.decide("1", "reject", { leaveRequestId: 2 }),
    ).rejects.toMatchObject({ status: 409 });
    expect(fetch).toHaveBeenCalledTimes(2);
  });
});

it("withdrawal uses the exact POST endpoint with session and CSRF", async () => {
  fetch
    .mockResolvedValueOnce(
      response({ headerName: "X-CSRF-TOKEN", token: "test-csrf" }),
    )
    .mockResolvedValueOnce(response(null, 204));
  await api.withdraw(7);
  expect(fetch.mock.calls[1][0]).toBe("/api/leave/7/withdraw");
  expect(fetch.mock.calls[1][1]).toMatchObject({
    method: "POST",
    credentials: "include",
    headers: { "X-CSRF-TOKEN": "test-csrf" },
  });
});

it("changes own password through the existing CSRF-protected endpoint", async () => {
  fetch
    .mockResolvedValueOnce(
      response({ headerName: "X-CSRF-TOKEN", token: "csrf" }),
    )
    .mockResolvedValueOnce(response(null, 204));
  await api.changePassword({
    currentPassword: "old-safe-passphrase",
    newPassword: "new-safe-passphrase",
    confirmation: "new-safe-passphrase",
  });
  expect(fetch.mock.calls[1][0]).toBe("/api/auth/password");
  expect(fetch.mock.calls[1][1]).toMatchObject({
    method: "PUT",
    credentials: "include",
    headers: { "X-CSRF-TOKEN": "csrf" },
  });
  expect(JSON.parse(fetch.mock.calls[1][1].body)).not.toHaveProperty(
    "accountId",
  );
});

it("archive uses the explicit POST endpoint with session and CSRF", async () => {
  fetch
    .mockResolvedValueOnce(
      response({ headerName: "X-CSRF-TOKEN", token: "csrf" }),
    )
    .mockResolvedValueOnce(response(null, 204));
  await api.archive(7);
  expect(fetch.mock.calls[1][0]).toBe("/api/leave-requests/7/archive");
  expect(fetch.mock.calls[1][1]).toMatchObject({
    method: "POST",
    credentials: "include",
    headers: { "X-CSRF-TOKEN": "csrf" },
  });
});
