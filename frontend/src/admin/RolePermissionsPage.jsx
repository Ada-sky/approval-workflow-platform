import { useState } from "react";
import { Link, useParams } from "react-router";
import { permissionLabel } from "./resources";
import { adminApi } from "../api/admin";
import { PageHeader, AdminAccess } from "./AdminPage";
import {
  useResource,
  Loading,
  Empty,
  ErrorNotice,
  Pagination,
  Confirmation,
} from "../components/UI";
/**
 * Edits account assignments and existing numeric permission-resource grants.
 * Saving grants replaces the selected set rather than appending permissions;
 * the confirmation warns that deselection revokes access.
 */
export default function RolePermissionsPage() {
  return (
    <AdminAccess codes={["10022"]}>
      <RolePermissions />
    </AdminAccess>
  );
}
function RolePermissions() {
  const { id } = useParams();
  const [page, setPage] = useState(0),
    [revision, setRevision] = useState(0),
    [feedback, setFeedback] = useState("");
  const state = useResource(
    () =>
      Promise.all([
        adminApi.get("roles", id),
        adminApi.assignments(id, page),
        adminApi.all("employees"),
        adminApi.all("permissions"),
        adminApi.grants(id),
      ]),
    [id, page, revision],
  );
  return (
    <>
      <PageHeader title="Role assignments & permissions">
        <Link className="btn btn-outline-secondary" to="/admin/roles">
          Back to roles
        </Link>
      </PageHeader>
      {state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <RoleForm
          key={`${id}-${revision}-${page}`}
          id={id}
          data={state.data}
          onPage={setPage}
          feedback={feedback}
          refresh={(message) => {
            setFeedback(message);
            setRevision((x) => x + 1);
          }}
        />
      )}
    </>
  );
}
function RoleForm({ id, data, onPage, refresh, feedback }) {
  const [role, assignments, employees, menus, grants] = data;
  const [employeeId, setEmployeeId] = useState(""),
    [selected, setSelected] = useState(grants.map((g) => g.id));
  const [error, setError] = useState(null),
    [busy, setBusy] = useState(false),
    [confirmation, setConfirmation] = useState(null),
    [message, setMessage] = useState(feedback);
  async function mutate(action, success, reload = true) {
    setBusy(true);
    setError(null);
    try {
      await action();
      setConfirmation(null);
      setMessage(success);
      if (reload) refresh(success);
    } catch (e) {
      setConfirmation(null);
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <h2 className="h4 mb-3">{role.name}</h2>
      <ErrorNotice error={error} />
      {message && (
        <div role="status" className="alert alert-success">
          {message}
        </div>
      )}
      <section className="card mb-4">
        <div className="card-body">
          <h3 className="h5">Assigned accounts</h3>
          <form
            className="d-flex flex-wrap gap-2 my-3"
            onSubmit={(e) => {
              e.preventDefault();
              if (!employeeId) {
                setError({ status: 400, message: "Select an employee." });
                return;
              }
              mutate(() => adminApi.assign(id, employeeId), "Role assigned.");
            }}
          >
            <label className="visually-hidden" htmlFor="employee">
              Employee
            </label>
            <select
              id="employee"
              className="form-select w-auto"
              value={employeeId}
              disabled={busy}
              onChange={(e) => setEmployeeId(e.target.value)}
            >
              <option value="">Select an employee</option>
              {employees.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name} — {e.email}
                </option>
              ))}
            </select>
            <button className="btn btn-primary" disabled={busy}>
              Assign role
            </button>
          </form>
          {!assignments.content.length ? (
            <Empty>No accounts assigned.</Empty>
          ) : (
            <div className="table-responsive">
              <table className="table">
                <thead>
                  <tr>
                    <th>Employee / account</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {assignments.content.map((a) => (
                    <tr key={a.id}>
                      <td>
                        {a.accountDisplayName ||
                          a.accountEmail ||
                          "Unavailable account"}
                        <small className="d-block text-secondary">
                          {a.accountEmail}
                        </small>
                      </td>
                      <td>
                        <button
                          className="btn btn-sm btn-outline-danger"
                          disabled={busy}
                          onClick={() =>
                            setConfirmation({
                              type: "unassign",
                              accountId: a.accountId,
                              label:
                                a.accountDisplayName ||
                                a.accountEmail ||
                                "this account",
                            })
                          }
                        >
                          Remove assignment
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination data={assignments} onChange={onPage} />
        </div>
      </section>
      <section className="card">
        <div className="card-body">
          <h3 className="h5">Permission grants</h3>
          <p className="text-secondary">
            Choose what this role can access. Saving replaces this role's
            permissions.
          </p>
          {!menus.length ? (
            <Empty>No permission resources available.</Empty>
          ) : (
            <div className="table-responsive">
              <table className="table align-middle">
                <thead>
                  <tr>
                    <th>Granted</th>
                    <th>Permission</th>
                  </tr>
                </thead>
                <tbody>
                  {menus.map((m) => (
                    <tr key={m.id}>
                      <td>
                        <input
                          className="form-check-input"
                          type="checkbox"
                          aria-label={`Grant ${permissionLabel(m)}`}
                          disabled={busy}
                          checked={selected.includes(m.id)}
                          onChange={(e) =>
                            setSelected(
                              e.target.checked
                                ? [...selected, m.id]
                                : selected.filter((value) => value !== m.id),
                            )
                          }
                        />
                      </td>
                      <td>{permissionLabel(m)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <button
            className="btn btn-primary mt-3"
            disabled={busy}
            onClick={() => setConfirmation({ type: "grants" })}
          >
            Save grants
          </button>
        </div>
      </section>
      {confirmation && (
        <Confirmation
          title={
            confirmation.type === "grants"
              ? "Replace role grants?"
              : "Remove role assignment?"
          }
          danger
          busy={busy}
          onCancel={() => setConfirmation(null)}
          onConfirm={() =>
            confirmation.type === "grants"
              ? mutate(
                  () => adminApi.saveGrants(id, selected),
                  "Permission grants saved.",
                  false,
                )
              : mutate(
                  () => adminApi.unassign(id, confirmation.accountId),
                  "Role assignment removed.",
                )
          }
        >
          {confirmation.type === "grants"
            ? "Deselected permissions will be revoked. Existing sessions may lose access immediately."
            : `Remove this role from ${confirmation.label}?`}
        </Confirmation>
      )}
    </>
  );
}
