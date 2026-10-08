const states = {
  COMPLETED: { symbol: "✓", text: "Completed", color: "success" },
  CURRENT: { symbol: "●", text: "Current", color: "primary" },
  UPCOMING: { symbol: "○", text: "Upcoming", color: "secondary" },
  REJECTED: { symbol: "×", text: "Rejected", color: "danger" },
  NOT_REACHED: { symbol: "—", text: "Not reached", color: "secondary" },
  WITHDRAWN: { symbol: "×", text: "Withdrawn", color: "secondary" },
  UNKNOWN: { symbol: "?", text: "Unavailable", color: "secondary" },
};

/**
 * Renders backend workflowProgress stages without reconstructing Activiti state
 * or applicant-aware routing. Omitted stages stay omitted; terminal and
 * not-reached states come from the response, not approval-history guesses.
 * Bootstrap stacks the same stages vertically on mobile and across on desktop.
 */
export default function ApprovalProgress({ progress }) {
  return (
    <section className="card mt-4" aria-labelledby="approval-progress-heading">
      <div className="card-body p-4">
        <h2 id="approval-progress-heading" className="h5">
          Approval Progress
        </h2>
        <p className="text-secondary" role="status">
          {progress?.message || "Workflow progress is unavailable"}
        </p>
        {progress?.stages?.length > 0 && (
          <ol className="approval-progress-list list-unstyled d-flex flex-column flex-md-row mb-0">
            {progress.stages.map((step) => {
              const state = states[step.status] || states.UNKNOWN;
              return (
                <li
                  key={step.stage}
                  className="approval-progress-step"
                  aria-current={step.status === "CURRENT" ? "step" : undefined}
                >
                  <span
                    className={`approval-progress-marker text-bg-${state.color}`}
                    aria-hidden="true"
                  >
                    {state.symbol}
                  </span>
                  <div>
                    <span
                      className={
                        step.status === "CURRENT"
                          ? "fw-bold text-primary"
                          : "fw-semibold"
                      }
                    >
                      {step.label}
                    </span>
                    <span className={`d-block small text-${state.color}`}>
                      {state.text}
                    </span>
                  </div>
                </li>
              );
            })}
          </ol>
        )}
      </div>
    </section>
  );
}
