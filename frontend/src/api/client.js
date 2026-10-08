/**
 * Keeps CSRF state in memory; authentication relies on the server session,
 * not an authentication token in localStorage or sessionStorage.
 */
let csrf;
let unauthorized = () => {};
export function onUnauthorized(handler) {
  unauthorized = handler;
  return () => {
    unauthorized = () => {};
  };
}
export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}
export function resetCsrf() {
  csrf = undefined;
}
export async function refreshCsrf() {
  csrf = await request("/api/auth/csrf");
  return csrf;
}
/**
 * Sends session cookies and adds the backend-provided CSRF header to mutations.
 * Normalizes HTTP/network failures into ApiError while preserving cancellation.
 * A 401 clears local session UI through the registered callback; the backend
 * remains authoritative for authorization and business-state checks.
 */
export async function request(
  path,
  { method = "GET", body, form = false, signal } = {},
) {
  const headers = { Accept: "application/json" };
  if (!["GET", "HEAD", "OPTIONS"].includes(method)) {
    const token = csrf || (await refreshCsrf());
    headers[token.headerName] = token.token;
  }
  if (body !== undefined)
    headers["Content-Type"] = form
      ? "application/x-www-form-urlencoded"
      : "application/json";
  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      credentials: "include",
      signal,
      body:
        body === undefined
          ? undefined
          : form
            ? new URLSearchParams(body)
            : JSON.stringify(body),
    });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new ApiError(0, "Unable to connect. Please try again.");
  }
  if (!response.ok) {
    if (response.status === 401) {
      resetCsrf();
      unauthorized();
    }
    // 403, 404 and 409 use stable client messages; only 400 uses a backend
    // validation message. Server failures never expose response internals.
    const messages = {
      401: "Your session has ended. Please sign in.",
      403: "You do not have permission, or your security token has expired. Refresh and try again.",
      404: "The requested resource was not found.",
      409: "This operation conflicts with the current request state.",
    };
    let data;
    try {
      data = await response.json();
    } catch {
      /* A proxy may return HTML. */
    }
    throw new ApiError(
      response.status,
      response.status >= 500
        ? "The server could not complete this request. Please try again."
        : path === "/login"
          ? "Unable to sign in. Check your credentials."
          : response.status === 400
            ? data?.message || "Please check the submitted values."
            : messages[response.status] ||
              "The request could not be completed.",
    );
  }
  if (response.status === 204) return null;
  return response.json();
}
export const api = {
  me: () => request("/api/auth/me"),
  login: async (userName, password) => {
    resetCsrf();
    await refreshCsrf();
    await request("/login", {
      method: "POST",
      form: true,
      body: { userName, password },
    });
    // Login changes session security state, so do not reuse the pre-login token.
    await refreshCsrf();
    return request("/api/auth/me");
  },
  logout: async () => {
    await request("/api/auth/logout", { method: "POST" });
    resetCsrf();
  },
  changePassword: async (body) => {
    await request("/api/auth/password", { method: "PUT", body });
    resetCsrf();
  },
  leaves: (page = 0) => request(`/api/leave-requests?page=${page}&size=10`),
  leave: (id) => request(`/api/leave-requests/${encodeURIComponent(id)}`),
  history: (id) =>
    request(`/api/leave-requests/${encodeURIComponent(id)}/approval-history`),
  withdraw: (id) =>
    request(`/api/leave/${encodeURIComponent(id)}/withdraw`, {
      method: "POST",
    }),
  create: (body) => request("/api/leave-requests", { method: "POST", body }),
  archive: (id) =>
    request(`/api/leave-requests/${encodeURIComponent(id)}/archive`, {
      method: "POST",
    }),
  tasks: (page = 0) => request(`/api/approvals/tasks?page=${page}&size=10`),
  task: (id) => request(`/api/approvals/tasks/${encodeURIComponent(id)}`),
  approvalHistory: (page = 0) =>
    request(`/api/approvals/history?page=${page}&size=10`),
  historicalLeave: (id) =>
    request(`/api/approvals/history/${encodeURIComponent(id)}`),
  decide: (id, decision, body) =>
    request(`/api/approvals/tasks/${encodeURIComponent(id)}/${decision}`, {
      method: "POST",
      body,
    }),
  types: async () => {
    let page = 0,
      all = [],
      result;
    do {
      result = await request(`/api/leave-types?page=${page++}&size=100`);
      all.push(...result.content);
    } while (page < result.totalPages);
    return all;
  },
};
