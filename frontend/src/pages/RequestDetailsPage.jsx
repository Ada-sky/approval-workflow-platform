import { useState } from "react";
import ApprovalProgress from "../components/ApprovalProgress";
import { Link, useLocation, useNavigate, useParams } from "react-router";
import { api } from "../api/client";
import { useAuth } from "../context/AuthContext";
import {
  useResource,
  Loading,
  ErrorNotice,
  LeaveSummary,
  History,
  Confirmation,
} from "../components/UI";
/**
 * Displays an owned request or an authorized approval task, including backend
 * progress and actual decision history. Approval routes use task IDs; employee
 * routes use request IDs. The backend verifies their relationship on decisions.
 */
export default function RequestDetailsPage({
  approval = false,
  historical = false,
}) {
  const { id } = useParams(),
    { user } = useAuth(),
    location = useLocation(),
    navigate = useNavigate();
  const [withdrawConfirm, setWithdrawConfirm] = useState(false),
    [success, setSuccess] = useState(null),
    [comment, setComment] = useState(""),
    [decision, setDecision] = useState(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  const state = useResource(
    () =>
      historical
        ? Promise.all([api.historicalLeave(id), api.types()])
        : approval
          ? Promise.all([api.task(id), api.types()])
          : Promise.all([api.leave(id), api.types(), api.history(id)]),
    [id, approval, historical],
  );
  async function withdraw() {
    setBusy(true);
    setError(null);
    try {
      await api.withdraw(id);
      setWithdrawConfirm(false);
      setSuccess("Leave request withdrawn successfully.");
      // Reload authoritative progress and capabilities after terminating execution.
      state.retry();
    } catch (e) {
      setError(e);
      setWithdrawConfirm(false);
    } finally {
      setBusy(false);
    }
  }
  // Confirmation precedes mutation; returning to the task list avoids keeping
  // an approval form open for a task the workflow may have already completed.
  async function decide() {
    setBusy(true);
    setError(null);
    try {
      await api.decide(id, decision, {
        leaveRequestId: state.data[0].leaveRequest.id,
        comment,
      });
      navigate("/approvals", {
        state: {
          message: `Request ${decision === "approve" ? "approved" : "rejected"} successfully.`,
        },
      });
    } catch (e) {
      setError(e);
      setDecision(null);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <Link
        className="small text-decoration-none"
        to={
          historical
            ? "/approvals/history"
            : approval
              ? "/approvals"
              : "/leave-requests"
        }
      >
        ←{" "}
        {historical
          ? "Approval History"
          : approval
            ? "Pending Approvals"
            : "My Leave Requests"}
      </Link>
      <h1 className="my-3">
        {approval ? "Approval Task" : "Leave Request"} Details
      </h1>
      {location.state?.message && (
        <div role="status" className="alert alert-success">
          {location.state.message}
        </div>
      )}
      {success && (
        <div role="status" className="alert alert-success">
          {success}
        </div>
      )}
      <ErrorNotice error={error} />
      {state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <>
          <LeaveSummary
            leave={
              approval || historical
                ? state.data[0].leaveRequest
                : state.data[0]
            }
            types={state.data[1]}
            applicant={approval || historical ? undefined : user.username}
          />
          <ApprovalProgress
            progress={
              (approval || historical
                ? state.data[0].leaveRequest
                : state.data[0]
              ).workflowProgress
            }
          />
          {!approval &&
            !historical &&
            state.data[0].canWithdraw &&
            user.accountId != null &&
            state.data[0].applicantAccountId === user.accountId && (
              <button
                className="btn btn-outline-danger mt-3"
                disabled={busy}
                onClick={() => setWithdrawConfirm(true)}
              >
                Withdraw Request
              </button>
            )}
          <History
            items={
              approval || historical
                ? state.data[0].approvalHistory
                : state.data[2]
            }
          />
          {approval && !historical && (
            <section className="card mt-4">
              <div className="card-body p-4">
                <h2 className="h5">Your decision</h2>
                <label htmlFor="decision-comment" className="form-label">
                  Comment / reason (optional)
                </label>
                <textarea
                  id="decision-comment"
                  className="form-control mb-3"
                  maxLength={1000}
                  rows={3}
                  value={comment}
                  onChange={(e) => setComment(e.target.value)}
                />
                <div className="d-flex gap-2">
                  <button
                    className="btn btn-primary"
                    disabled={busy}
                    onClick={() => setDecision("approve")}
                  >
                    Approve
                  </button>
                  <button
                    className="btn btn-outline-danger"
                    disabled={busy}
                    onClick={() => setDecision("reject")}
                  >
                    Reject
                  </button>
                </div>
              </div>
            </section>
          )}
        </>
      )}
      {withdrawConfirm && (
        <Confirmation
          title="Withdraw Request"
          danger
          busy={busy}
          onCancel={() => setWithdrawConfirm(false)}
          onConfirm={withdraw}
        >
          Are you sure you want to withdraw this leave request? This action
          cannot be undone.
        </Confirmation>
      )}
      {decision && (
        <Confirmation
          title={`${decision === "approve" ? "Approve" : "Reject"} this request?`}
          danger={decision === "reject"}
          busy={busy}
          onCancel={() => setDecision(null)}
          onConfirm={decide}
        >
          Your decision will be recorded and applied to this workflow task.
        </Confirmation>
      )}
    </>
  );
}
