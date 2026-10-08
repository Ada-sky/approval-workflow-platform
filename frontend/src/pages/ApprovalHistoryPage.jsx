import { useState } from "react";
import { Link } from "react-router";
import { api } from "../api/client";
import { leaveDate, submittedDate, stages } from "../utils/domain";
import {
  useResource,
  Loading,
  ErrorNotice,
  Empty,
  Pagination,
} from "../components/UI";

/** Lists personal decisions; historical detail authorization remains on the server. */
export default function ApprovalHistoryPage() {
  const [page, setPage] = useState(0);
  const state = useResource(() => api.approvalHistory(page), [page]);
  return (
    <>
      <p className="eyebrow">Approval workbench</p>
      <h1 className="mb-4">Approval History</h1>
      {state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <>
          {!state.data.content.length ? (
            <Empty>No approval decisions yet.</Empty>
          ) : (
            <div className="card table-responsive">
              <table className="table align-middle mb-0">
                <thead>
                  <tr>
                    <th>Request</th>
                    <th>Applicant</th>
                    <th>Leave type</th>
                    <th>Dates</th>
                    <th>Decision</th>
                    <th>Stage</th>
                    <th>Decided at</th>
                    <th>Action</th>
                  </tr>
                </thead>
                <tbody>
                  {state.data.content.map((row, index) => (
                    <tr key={index}>
                      <td>
                        <strong>{row.title}</strong>
                      </td>
                      <td>{row.applicantName || "Unknown applicant"}</td>
                      <td>{row.leaveType || "Unknown leave type"}</td>
                      <td className="small">
                        {leaveDate(row.startDate)} — {leaveDate(row.endDate)}
                      </td>
                      <td>
                        <span
                          className={`badge text-bg-${row.decision === "APPROVED" ? "success" : "danger"}`}
                        >
                          {row.decision === "APPROVED"
                            ? "Approved"
                            : "Rejected"}
                        </span>
                      </td>
                      <td>{stages[row.stage] || "Unknown stage"}</td>
                      <td className="small">{submittedDate(row.decidedAt)}</td>
                      <td>
                        <Link
                          className="btn btn-sm btn-outline-primary"
                          to={`/approvals/history/${encodeURIComponent(row.requestId)}`}
                        >
                          View
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <Pagination data={state.data} onChange={setPage} />
        </>
      )}
    </>
  );
}
