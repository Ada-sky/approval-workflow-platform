import { useState } from "react";
import { Link, useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";
import { adminApi } from "../api/admin";
import { resources, hasPermission, allowedAction, labels } from "./resources";
import {
  useResource,
  Loading,
  Empty,
  ErrorNotice,
  Pagination,
  Confirmation,
} from "../components/UI";
export function PageHeader({ title, children, subtitle }) {
  return (
    <div className="d-flex flex-wrap justify-content-between gap-3 mb-4">
      <div>
        <p className="eyebrow">Administration</p>
        <h1>{title}</h1>
        {subtitle && <p className="text-secondary mb-0">{subtitle}</p>}
      </div>
      <div className="align-self-center">{children}</div>
    </div>
  );
}
/**
 * Gates administration rendering using existing numeric permission identities.
 * Direct API access is independently authorized by the backend.
 */
export function AdminAccess({ codes, children }) {
  const { user } = useAuth();
  return hasPermission(user, codes) ? (
    children
  ) : (
    <ErrorNotice
      error={{
        status: 403,
        message: "You do not have access to this administration page.",
      }}
    />
  );
}
/**
 * Hosts resource-configured administration lists, search and confirmed deletion.
 * Failed deletions preserve the displayed data; reference/business conflicts
 * are decided by the backend rather than by the confirmation dialog.
 */
export default function AdminPage({ resource }) {
  const config = resources[resource];
  return (
    <AdminAccess codes={config.permissions}>
      <AdminCollection key={resource} resource={resource} config={config} />
    </AdminAccess>
  );
}
function AdminCollection({ resource, config }) {
  const { user } = useAuth();
  const location = useLocation();
  const [page, setPage] = useState(0),
    [search, setSearch] = useState(""),
    [name, setName] = useState("");
  const [selected, setSelected] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null),
    [message, setMessage] = useState(location.state?.message || "");
  const canRead = hasPermission(user, config.read || config.permissions);
  const state = useResource(
    () =>
      canRead
        ? adminApi.list(config.endpoint, page, name)
        : Promise.resolve(null),
    [page, name, canRead],
  );
  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await adminApi.remove(config.endpoint, selected.id);
      setSelected(null);
      setMessage(`${config.singular} removed.`);
      if (state.data.content.length === 1 && page > 0) setPage(page - 1);
      else state.retry();
    } catch (e) {
      setSelected(null);
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title={config.title}
        subtitle={
          resource === "leave-types"
            ? "Leave types available when employees submit a request."
            : undefined
        }
      >
        {allowedAction(user, config, "create") && (
          <Link className="btn btn-primary" to={`/admin/${resource}/new`}>
            {config.createLabel || `Create ${config.singular.toLowerCase()}`}
          </Link>
        )}
      </PageHeader>
      {message && (
        <div role="status" className="alert alert-success">
          {message}
        </div>
      )}
      <ErrorNotice error={error} />
      {canRead && resource !== "menus" && (
        <form
          className="d-flex gap-2 mb-3"
          onSubmit={(e) => {
            e.preventDefault();
            setPage(0);
            setName(search);
          }}
        >
          <label className="visually-hidden" htmlFor="search">
            Search by name
          </label>
          <input
            id="search"
            className="form-control"
            placeholder="Search by name"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
          <button className="btn btn-outline-secondary">Search</button>
        </form>
      )}
      {!canRead ? (
        <ErrorNotice
          error={{
            status: 403,
            message:
              "You can use your permitted actions, but cannot view the employee list.",
          }}
        />
      ) : state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <>
          {!state.data.content.length ? (
            <Empty>No {config.title.toLowerCase()} found.</Empty>
          ) : (
            <div className="card table-responsive">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    {config.columns.map((col) => (
                      <th key={col}>
                        {config.columnLabels?.[col] || labels[col]}
                      </th>
                    ))}
                    {!config.readonly && <th>Actions</th>}
                  </tr>
                </thead>
                <tbody>
                  {state.data.content.map((row) => (
                    <tr key={row.id}>
                      {config.columns.map((col) => (
                        <td key={col}>
                          {resource === "employees" && col === "name" ? (
                            <>
                              <span>{row.name}</span>
                              {row.number && (
                                <small className="d-block text-secondary">
                                  {row.number}
                                </small>
                              )}
                            </>
                          ) : (
                            (row[col] ?? "—")
                          )}
                        </td>
                      ))}
                      {!config.readonly && (
                        <td>
                          <div className="d-flex flex-wrap gap-2">
                            {resource === "employees" && (
                              <Link
                                className="btn btn-sm btn-outline-secondary"
                                to={`/admin/${resource}/${row.id}`}
                              >
                                View
                              </Link>
                            )}
                            {allowedAction(user, config, "update") && (
                              <Link
                                className="btn btn-sm btn-outline-primary"
                                to={`/admin/${resource}/${row.id}/edit`}
                              >
                                Edit
                              </Link>
                            )}
                            {resource === "roles" && (
                              <Link
                                className="btn btn-sm btn-outline-primary"
                                to={`/admin/roles/${row.id}/permissions`}
                              >
                                Assignments & grants
                              </Link>
                            )}
                            {resource !== "employees" &&
                              allowedAction(user, config, "remove") && (
                                <button
                                  className="btn btn-sm btn-outline-danger"
                                  onClick={() => setSelected(row)}
                                >
                                  Delete
                                </button>
                              )}
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination data={state.data} onChange={setPage} />
        </>
      )}
      {selected && (
        <Confirmation
          title={`Delete ${config.singular.toLowerCase()}?`}
          danger
          busy={busy}
          onCancel={() => setSelected(null)}
          onConfirm={remove}
        >
          Remove “{selected.name}”? The backend will reject deletion if this
          record is still required.
        </Confirmation>
      )}
    </>
  );
}
