import { useState } from "react";
import { Link, useLocation } from "react-router";
import { api } from "../api/client";
import { date, leaveDate, submittedDate, archivable } from "../utils/domain";
import {
  useResource,
  Loading,
  ErrorNotice,
  Empty,
  Status,
  Pagination,
  Confirmation,
} from "../components/UI";
/**
 * Lists owned requests or backend-filtered approval tasks using the same table.
 * Request and task identifiers remain routing references. Archive visibility is
 * a status-based UX hint; the backend rechecks ownership and allowed state.
 */
export default function RequestListPage({ approvals = false }) {
  const location = useLocation();
  const [page, setPage] = useState(0),
    [selected, setSelected] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null),
    [feedback, setFeedback] = useState(location.state?.message || "");
  const state = useResource(
    () =>
      Promise.all([
        approvals ? api.tasks(page) : api.leaves(page),
        api.types(),
      ]),
    [page, approvals],
  );
  async function archive() {
    setBusy(true);
    setError(null);
    try {
      await api.archive(selected.id);
      setSelected(null);
      setFeedback("Leave request archived.");
      if (state.data[0].content.length === 1 && page > 0) setPage(page - 1);
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
      <div className="d-flex justify-content-between gap-3 mb-4">
        <div>
          <p className="eyebrow">
            {approvals ? "Approval workbench" : "Employee workspace"}
          </p>
          <h1>{approvals ? "Pending Approvals" : "My Leave Requests"}</h1>
        </div>
        {!approvals && (
          <Link
            className="btn btn-primary align-self-center"
            to="/leave-requests/new"
          >
            New request
          </Link>
        )}
      </div>
      {feedback && (
        <div role="status" className="alert alert-success">
          {feedback}
        </div>
      )}
      <ErrorNotice error={error} />
      {state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <>
          {!state.data[0].content.length ? (
            <Empty>
              {approvals
                ? "No approvals are waiting for you."
                : "You have no leave requests yet."}
            </Empty>
          ) : (
            <div className="card table-responsive">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    {approvals && <th>Request</th>}
                    {approvals && <th>Applicant</th>}
                    <th>Leave type</th>
                    <th>Dates</th>
                    <th>Days</th>
                    <th>Submitted</th>
                    <th>{approvals ? "Current stage" : "Status"}</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {state.data[0].content.map((row) => {
                    const leave = approvals ? row.leaveRequest : row;
                    return (
                      <tr key={approvals ? row.taskId : leave.id}>
                        {approvals && (
                          <td>
                            <strong>{leave.title}</strong>
                          </td>
                        )}
                        {approvals && (
                          <td>{leave.applicantName || "Unknown applicant"}</td>
                        )}
                        <td>
                          {state.data[1].find((t) => t.id === leave.leaveTypeId)
                            ?.name || "Unknown leave type"}
                        </td>
                        <td className="small">
                          {approvals ? (
                            <>
                              {date(leave.startDate)}
                              <br />
                              {date(leave.endDate)}
                            </>
                          ) : (
                            `${leaveDate(leave.startDate)} – ${leaveDate(leave.endDate)}`
                          )}
                        </td>
                        <td>{leave.days}</td>
                        <td className="small">
                          {leave.submittedAt
                            ? approvals
                              ? date(leave.submittedAt)
                              : submittedDate(leave.submittedAt)
                            : "Not recorded"}
                        </td>
                        <td>
                          {approvals ? (
                            row.name
                          ) : (
                            <Status value={leave.status} />
                          )}
                        </td>
                        <td>
                          <div className="d-flex gap-2">
                            <Link
                              className="btn btn-sm btn-outline-primary"
                              to={
                                approvals
                                  ? `/approvals/${encodeURIComponent(row.taskId)}`
                                  : `/leave-requests/${leave.id}`
                              }
                            >
                              {approvals ? "Review" : "View"}
                            </Link>
                            {!approvals && archivable(leave.status) && (
                              <button
                                className="btn btn-sm btn-outline-secondary"
                                onClick={() => setSelected(leave)}
                              >
                                Archive
                              </button>
                            )}
                          </div>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          <Pagination data={state.data[0]} onChange={setPage} />
        </>
      )}
      {selected && (
        <Confirmation
          title="Archive this request?"
          busy={busy}
          confirmLabel="Archive"
          onCancel={() => setSelected(null)}
          onConfirm={archive}
        >
          It will be removed from your leave request list, but its approval
          history will be retained.
        </Confirmation>
      )}
    </>
  );
}
