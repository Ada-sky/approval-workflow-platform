import { request } from "./client";
const path = (resource, id) =>
  `/api/${resource}${id === undefined ? "" : `/${encodeURIComponent(id)}`}`;
/**
 * Adapts administration endpoints to the shared session/CSRF request helper.
 * IDs remain API references; pages use display names for ordinary selections.
 */
export const adminApi = {
  list: (resource, page = 0, name = "", size = 20) =>
    request(
      `${path(resource)}?page=${page}&size=${size}&name=${encodeURIComponent(name)}`,
    ),
  get: (resource, id) => request(path(resource, id)),
  save: (resource, id, body) =>
    request(path(resource, id), {
      method: id === undefined ? "POST" : "PUT",
      body,
    }),
  remove: (resource, id) => request(path(resource, id), { method: "DELETE" }),
  all: async (resource) => {
    const rows = [];
    let page = 0,
      result;
    do {
      result = await adminApi.list(resource, page++, "", 100);
      rows.push(...result.content);
    } while (page < result.totalPages);
    return rows;
  },
  employeeOptions: () => request("/api/employees/form-options"),
  assignments: (roleId, page = 0) =>
    request(`${path("roles", roleId)}/assignments?page=${page}&size=20`),
  // Assignment accepts an employee ID; the backend resolves its linked account.
  assign: (roleId, employeeId) =>
    request(
      `${path("roles", roleId)}/employees/${encodeURIComponent(employeeId)}`,
      { method: "POST" },
    ),
  unassign: (roleId, accountId) =>
    request(
      `${path("roles", roleId)}/accounts/${encodeURIComponent(accountId)}`,
      { method: "DELETE" },
    ),
  grants: (roleId) => request(`${path("roles", roleId)}/permissions`),
  saveGrants: (roleId, menuIds) =>
    request(`${path("roles", roleId)}/permissions`, {
      method: "PUT",
      body: { menuIds },
    }),
};
