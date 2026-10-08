import { useEffect, useState, useRef } from "react";
import { statuses, stages, leaveDate, submittedDate } from "../utils/domain";
export function Loading() {
  return (
    <div role="status" className="py-5 text-center">
      <span className="spinner-border spinner-border-sm me-2" />
      Loading…
    </div>
  );
}
export function ErrorNotice({ error, retry }) {
  if (!error) return null;
  return (
    <div role="alert" className="alert alert-danger">
      <strong>
        {error.status === 403
          ? "Forbidden"
          : error.status === 404
            ? "Not Found"
            : "Unable to complete request"}
      </strong>
      <div>{error.message}</div>
      {retry && (
        <button className="btn btn-sm btn-outline-danger mt-2" onClick={retry}>
          Try again
        </button>
      )}
    </div>
  );
}
export function Empty({ children }) {
  return (
    <div className="p-5 text-center text-secondary border rounded bg-white">
      {children}
    </div>
  );
}
export function Status({ value }) {
  const [label, color] = statuses[value] || ["Unknown", "secondary"];
  return <span className={`badge text-bg-${color}`}>{label}</span>;
}
export function Pagination({ data, onChange }) {
  return (
    <div className="d-flex justify-content-between align-items-center mt-3">
      <small className="text-secondary">
        {data.totalElements} total · Page {data.page + 1} of{" "}
        {Math.max(1, data.totalPages)}
      </small>
      <div className="btn-group">
        <button
          className="btn btn-outline-secondary"
          disabled={data.page === 0}
          onClick={() => onChange(data.page - 1)}
        >
          Previous
        </button>
        <button
          className="btn btn-outline-secondary"
          disabled={data.page + 1 >= data.totalPages}
          onClick={() => onChange(data.page + 1)}
        >
          Next
        </button>
      </div>
    </div>
  );
}
/**
 * Loads a resource when its explicit dependencies or retry revision change.
 * Cleanup ignores late results from an obsolete load; it does not cancel the
 * underlying request. Callers supply dependencies that identify their resource.
 */
export function useResource(loader, dependencies) {
  const [state, setState] = useState({ loading: true }),
    [revision, setRevision] = useState(0);
  useEffect(() => {
    let active = true;
    setState({ loading: true });
    Promise.resolve()
      .then(loader)
      .then((data) => {
        if (active) setState({ data, loading: false });
      })
      .catch((error) => {
        if (active) setState({ error, loading: false });
      });
    return () => {
      active = false;
    };
  }, [...dependencies, revision]);
  return { ...state, retry: () => setRevision((x) => x + 1) };
}
/** Displays recorded decisions, separate from current/future workflow progress. */
export function History({ items = [] }) {
  return (
    <section className="card mt-4">
      <div className="card-header bg-white">
        <h2 className="h5 mb-0">Approval History</h2>
      </div>
      <ul className="list-group list-group-flush">
        {items.length ? (
          items.map((item) => (
            <li className="list-group-item py-3" key={item.id}>
              <div className="d-flex justify-content-between">
                <strong>{stages[item.stage] || "Unknown stage"}</strong>
                <span
                  className={`badge text-bg-${item.decision === "APPROVED" ? "success" : item.decision === "REJECTED" ? "danger" : "secondary"}`}
                >
                  {item.decision === "APPROVED"
                    ? "Approved"
                    : item.decision === "REJECTED"
                      ? "Rejected"
                      : item.decision === "WITHDRAWN"
                        ? "Withdrawn"
                        : "Unknown"}
                </span>
              </div>
              <small className="text-secondary">
                {item.approverUsername || "Unknown approver"} ·{" "}
                {item.approvedAt
                  ? submittedDate(item.approvedAt)
                  : "Not available"}
              </small>
              {item.comment && (
                <p className="mb-0 mt-2 text-break">{item.comment}</p>
              )}
            </li>
          ))
        ) : (
          <li className="list-group-item text-secondary py-4">
            No approval decisions yet.
          </li>
        )}
      </ul>
    </section>
  );
}
export function LeaveSummary({ leave, types = [], applicant }) {
  return (
    <section className="card">
      <div className="card-body">
        <div className="d-flex justify-content-between gap-3">
          <h2 className="h4">{leave.title}</h2>
          <Status value={leave.status} />
        </div>
        <dl className="row mt-4">
          <dt className="col-sm-3">Applicant</dt>
          <dd className="col-sm-9">
            {leave.applicantName || applicant || "Unknown applicant"}
          </dd>
          <dt className="col-sm-3">Leave type</dt>
          <dd className="col-sm-9">
            {types.find((t) => t.id === leave.leaveTypeId)?.name ||
              `Type #${leave.leaveTypeId}`}
          </dd>
          <dt className="col-sm-3">Dates</dt>
          <dd className="col-sm-9">
            {leaveDate(leave.startDate)} — {leaveDate(leave.endDate)}
          </dd>
          <dt className="col-sm-3">Duration</dt>
          <dd className="col-sm-9">{leave.days} days</dd>
          <dt className="col-sm-3">Submitted at</dt>
          <dd className="col-sm-9">
            {leave.submittedAt
              ? submittedDate(leave.submittedAt)
              : "Not recorded"}
          </dd>
          <dt className="col-sm-3">Reason</dt>
          <dd className="col-sm-9 preserve-lines text-break">{leave.reason}</dd>
          {leave.comment && (
            <>
              <dt className="col-sm-3">Comment</dt>
              <dd className="col-sm-9 preserve-lines">{leave.comment}</dd>
            </>
          )}
        </dl>
      </div>
    </section>
  );
}
/**
 * Requires explicit confirmation for a mutation and keeps keyboard focus within
 * the dialog buttons, restoring the previous focus when it closes.
 */
export function Confirmation({
  title,
  children,
  busy,
  onCancel,
  onConfirm,
  danger = false,
  confirmLabel = "Confirm",
}) {
  const dialog = useRef(null);
  useEffect(() => {
    const previous = document.activeElement;
    dialog.current?.querySelector("button")?.focus();
    return () => previous?.focus();
  }, []);
  return (
    <div className="confirmation-backdrop">
      <section
        ref={dialog}
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirmation-title"
        className="card shadow confirmation"
        tabIndex={-1}
        onKeyDown={(e) => {
          if (e.key === "Escape" && !busy) onCancel();
          if (e.key === "Tab") {
            const buttons = [
              ...dialog.current.querySelectorAll("button:not(:disabled)"),
            ];
            if (!buttons.length) {
              e.preventDefault();
              return;
            }
            const first = buttons[0],
              last = buttons[buttons.length - 1];
            if (e.shiftKey && document.activeElement === first) {
              e.preventDefault();
              last.focus();
            } else if (!e.shiftKey && document.activeElement === last) {
              e.preventDefault();
              first.focus();
            }
          }
        }}
      >
        <div className="card-body p-4">
          <h2 id="confirmation-title" className="h5">
            {title}
          </h2>
          <div className="my-3">{children}</div>
          <div className="d-flex gap-2 justify-content-end">
            <button
              autoFocus
              disabled={busy}
              className="btn btn-outline-secondary"
              onClick={onCancel}
            >
              Cancel
            </button>
            <button
              disabled={busy}
              className={`btn btn-${danger ? "danger" : "primary"}`}
              onClick={onConfirm}
            >
              {busy ? "Submitting…" : confirmLabel}
            </button>
          </div>
        </div>
      </section>
    </div>
  );
}
