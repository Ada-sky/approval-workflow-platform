import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { useAuth } from "../context/AuthContext";
import { adminApi } from "../api/admin";
import { resources, allowedAction, validateForm, formBody } from "./resources";
import { PageHeader, AdminAccess } from "./AdminPage";
import { useResource, Loading, ErrorNotice } from "../components/UI";
/**
 * Selects create, edit or read access for the shared administration form.
 * Departments and job titles use named reference options; employee status
 * is assigned or preserved by the backend instead of this profile editor.
 */
export default function AdminEditorPage({ resource, details = false }) {
  const { id } = useParams();
  const config = resources[resource];
  const codes = details
    ? config.read || config.permissions
    : id
      ? config.update || config.permissions
      : config.create || config.permissions;
  return (
    <AdminAccess codes={codes}>
      <Editor
        key={`${resource}-${id}-${details}`}
        resource={resource}
        config={config}
        id={id}
        details={details}
      />
    </AdminAccess>
  );
}
async function loadForm(resource, id, details) {
  const config = resources[resource];
  const entity = id ? await adminApi.get(config.endpoint, id) : {};
  let options = {};
  if (!details && resource === "employees") {
    const refs = await adminApi.employeeOptions();
    options = {
      departmentId: refs.departments,
      jobTitleId: refs.jobTitles,
    };
  } else if (
    !details &&
    ["departments", "job-titles", "menus"].includes(resource)
  ) {
    options.parentId = (await adminApi.all(config.endpoint)).filter(
      (row) => String(row.id) !== String(id),
    );
    if (resource === "departments")
      options.managerEmployeeId = await adminApi.all(
        "departments/manager-options",
      );
  }
  return { entity, options };
}
function Editor({ resource, config, id, details }) {
  const state = useResource(
    () => loadForm(resource, id, details),
    [resource, id, details],
  );
  return (
    <>
      <PageHeader
        title={
          details
            ? "Employee details"
            : `${id ? "Edit" : "Create"} ${config.singular.toLowerCase()}`
        }
      >
        <Link className="btn btn-outline-secondary" to={`/admin/${resource}`}>
          Back to {config.title.toLowerCase()}
        </Link>
      </PageHeader>
      {state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <EditorForm
          resource={resource}
          config={config}
          id={id}
          details={details}
          data={state.data}
        />
      )}
    </>
  );
}
function EditorForm({ resource, config, id, details, data }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState(() => ({
    ...data.entity,
    permissionCode: data.entity.code || "",
    level: data.entity.level ?? 0,
    grade: data.entity.grade ?? 0,
  }));
  const [errors, setErrors] = useState({}),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false),
    [created, setCreated] = useState(null),
    [copyMessage, setCopyMessage] = useState("");
  async function submit(e) {
    e.preventDefault();
    const validation = validateForm(config, values, !id);
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setBusy(true);
    setError(null);
    try {
      const response = await adminApi.save(
        config.endpoint,
        id,
        formBody(config, values, !id),
      );
      if (resource === "employees" && !id) {
        // Plaintext exists only in this successful creation response and component
        // state for immediate copying. It is not saved in browser storage or
        // retrievable later through the normal employee details API.
        setCreated({ temporaryPassword: response.temporaryPassword });
        setValues({});
        return;
      }
      navigate(`/admin/${resource}`, {
        state: {
          message: `${config.singular} ${id ? "updated" : "created"} successfully.`,
        },
      });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (details)
    return (
      <section className="card">
        <div className="card-body">
          <h2 className="h4">{data.entity.name}</h2>
          <dl className="row">
            {config.fields.map((f) => (
              <div className="col-md-6" key={f.key}>
                <dt>{f.label}</dt>
                <dd>
                  {data.entity[
                    {
                      departmentId: "departmentName",
                      jobTitleId: "jobTitleName",
                      employeeStatusId: "employeeStatusName",
                    }[f.key] || f.key
                  ] ?? "—"}
                </dd>
              </div>
            ))}
            <div className="col-md-6">
              <dt>Employee status</dt>
              <dd>{data.entity.employeeStatusName ?? "—"}</dd>
            </div>
          </dl>
          {allowedAction(user, config, "update") && (
            <Link
              className="btn btn-primary"
              to={`/admin/${resource}/${id}/edit`}
            >
              Edit employee
            </Link>
          )}
        </div>
      </section>
    );
  if (created)
    return (
      <section className="card">
        <div className="card-body">
          <h2 className="h4">Employee created successfully</h2>
          <p>Temporary password:</p>
          <output className="font-monospace">
            {created.temporaryPassword}
          </output>
          <button
            className="btn btn-sm btn-outline-secondary ms-3"
            type="button"
            onClick={async () => {
              try {
                await navigator.clipboard.writeText(created.temporaryPassword);
                setCopyMessage("Copied.");
              } catch {
                setCopyMessage(
                  "Copy unavailable. Select and copy the password manually.",
                );
              }
            }}
          >
            Copy
          </button>
          {copyMessage && (
            <p role="status" className="small mt-2">
              {copyMessage}
            </p>
          )}
          <p className="text-secondary mt-3">
            Please provide this temporary password to the employee. It will not
            be shown again.
          </p>
          <Link className="btn btn-primary" to="/admin/employees">
            Back to employees
          </Link>
        </div>
      </section>
    );
  const fields = config.fields;
  return (
    <form className="card" onSubmit={submit} noValidate>
      <div className="card-body">
        <ErrorNotice error={error} />
        <div className="row g-3">
          {fields.map((f) => (
            <div className="col-md-6" key={f.key}>
              <label className="form-label" htmlFor={f.key}>
                {f.label}
                {f.required ? " *" : ""}
              </label>
              {f.type === "select" ? (
                <select
                  id={f.key}
                  className={`form-select ${errors[f.key] ? "is-invalid" : ""}`}
                  disabled={busy}
                  value={values[f.key] ?? ""}
                  onChange={(e) =>
                    setValues({ ...values, [f.key]: e.target.value })
                  }
                >
                  <option value="">
                    {f.key === "parentId" ? "Root (no parent)" : "Not assigned"}
                  </option>
                  {values[f.key] &&
                    !(data.options[f.key] || []).some(
                      (o) => String(o.id) === String(values[f.key]),
                    ) && (
                      <option value={values[f.key]}>
                        Current selection (unavailable)
                      </option>
                    )}
                  {(data.options[f.key] || []).map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.displayName || o.name}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  id={f.key}
                  type={f.type}
                  className={`form-control ${errors[f.key] ? "is-invalid" : ""}`}
                  value={values[f.key] ?? ""}
                  disabled={busy}
                  maxLength={f.max}
                  min={f.type === "number" ? 0 : undefined}
                  max={f.upper}
                  autoComplete={
                    f.type === "password" ? "new-password" : undefined
                  }
                  aria-invalid={!!errors[f.key]}
                  aria-describedby={
                    errors[f.key] ? `${f.key}-error` : undefined
                  }
                  onChange={(e) =>
                    setValues({ ...values, [f.key]: e.target.value })
                  }
                />
              )}
              {errors[f.key] && (
                <div id={`${f.key}-error`} className="invalid-feedback">
                  {errors[f.key]}
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="mt-4 d-flex gap-2">
          <button disabled={busy} className="btn btn-primary">
            {busy ? "Saving…" : "Save"}
          </button>
          <Link className="btn btn-outline-secondary" to={`/admin/${resource}`}>
            Cancel
          </Link>
        </div>
      </div>
    </form>
  );
}
