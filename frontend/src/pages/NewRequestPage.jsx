import { useAuth } from "../context/AuthContext";
import { useState } from "react";
import { useNavigate } from "react-router";
import { api } from "../api/client";
import { validateLeave } from "../utils/domain";
import { useResource, Loading, ErrorNotice, Empty } from "../components/UI";
/**
 * Collects calendar-day leave dates and submits them to start a backend workflow.
 * Capability and field checks improve UX; eligibility, duration and applicant-
 * aware approval routing are validated and determined by the backend.
 */
export default function NewRequestPage() {
  const { user } = useAuth();
  const navigate = useNavigate(),
    types = useResource(api.types, []);
  const [values, setValues] = useState({
      title: "",
      leaveTypeId: "",
      reason: "",
      startDate: "",
      endDate: "",
      comment: "",
    }),
    [errors, setErrors] = useState({}),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  function change(e) {
    setValues({ ...values, [e.target.name]: e.target.value });
  }
  async function submit(e) {
    e.preventDefault();
    if (user?.canApplyForLeave === false) return;
    const validation = validateLeave(values);
    setErrors(validation);
    if (Object.keys(validation).length) return;
    setBusy(true);
    setError(null);
    try {
      // Midnight preserves the datetime request contract without asking users
      // to choose a time or calculating the approval route in the browser.
      const leave = await api.create({
        ...values,
        leaveTypeId: Number(values.leaveTypeId),
        startDate: values.startDate + "T00:00:00",
        endDate: values.endDate + "T00:00:00",
      });
      navigate(`/leave-requests/${leave.id}`, {
        state: { message: "Leave request submitted successfully." },
      });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  if (user?.canApplyForLeave === false)
    return (
      <ErrorNotice
        error={{
          status: 403,
          message: "System administration accounts cannot apply for leave.",
        }}
      />
    );
  return (
    <>
      <p className="eyebrow">Employee workspace</p>
      <h1 className="mb-4">New Leave Request</h1>
      {types.loading ? (
        <Loading />
      ) : types.error ? (
        <ErrorNotice error={types.error} retry={types.retry} />
      ) : !types.data.length ? (
        <Empty>
          No leave types are configured. Contact your administrator.
        </Empty>
      ) : (
        <form className="card" onSubmit={submit} noValidate>
          <div className="card-body p-4">
            <ErrorNotice error={error} />
            <div className="row g-3">
              {[
                ["title", "Title", "text"],
                ["leaveTypeId", "Leave type", "select"],
                ["startDate", "Start date", "date"],
                ["endDate", "End date", "date"],
                ["reason", "Reason for leave", "textarea"],
                ["comment", "Additional comment (optional)", "textarea"],
              ].map(([name, label, type]) => (
                <div
                  key={name}
                  className={
                    name === "startDate" || name === "endDate"
                      ? "col-md-6"
                      : "col-12"
                  }
                >
                  <label className="form-label" htmlFor={name}>
                    {label}
                  </label>
                  {type === "select" ? (
                    <select
                      id={name}
                      name={name}
                      className={`form-select ${errors[name] ? "is-invalid" : ""}`}
                      value={values[name]}
                      onChange={change}
                      aria-describedby={
                        errors[name] ? `${name}-error` : undefined
                      }
                    >
                      <option value="">Select a leave type</option>
                      {types.data.map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.name}
                        </option>
                      ))}
                    </select>
                  ) : type === "textarea" ? (
                    <textarea
                      id={name}
                      name={name}
                      className={`form-control ${errors[name] ? "is-invalid" : ""}`}
                      placeholder={
                        name === "reason"
                          ? "Briefly describe the reason for your leave"
                          : "Add any additional information for the approver"
                      }
                      rows={3}
                      maxLength={1000}
                      value={values[name]}
                      onChange={change}
                    />
                  ) : (
                    <input
                      id={name}
                      name={name}
                      type={type}
                      className={`form-control ${errors[name] ? "is-invalid" : ""}`}
                      maxLength={name === "title" ? 200 : undefined}
                      value={values[name]}
                      onChange={change}
                    />
                  )}
                  <div id={`${name}-error`} className="invalid-feedback">
                    {errors[name]}
                  </div>
                </div>
              ))}
            </div>
            <div className="text-secondary small mt-3 mb-3">
              <p className="fw-semibold mb-1">Before you submit</p>
              <p className="mb-0">
                Dates are counted inclusively. A request with the same start and
                end date counts as one day. Once submitted, the request enters
                the approval workflow and cannot be edited.
              </p>
            </div>
            <button disabled={busy} className="btn btn-primary">
              {busy ? "Submitting…" : "Submit Leave Request"}
            </button>
          </div>
        </form>
      )}
    </>
  );
}
