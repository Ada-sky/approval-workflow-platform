import { Link } from "react-router";
import { useAuth } from "../context/AuthContext";
import { api } from "../api/client";
import { adminGroups } from "../admin/resources";
import { useResource, Loading, ErrorNotice } from "../components/UI";
const administrationDescriptions = {
  employees: "Manage employee records and accounts.",
  departments: "Manage the organization structure.",
  "job-titles": "Manage job titles and hierarchy.",
  roles: "Manage roles, assignments, and access permissions.",
  "leave-types": "Manage available leave categories.",
};
export default function DashboardPage() {
  const { user } = useAuth();
  if (user.canApplyForLeave === false) {
    const entries = adminGroups(user).flatMap((group) => group.entries);
    return (
      <>
        <p className="eyebrow">Administration</p>
        <h1>Administration Dashboard</h1>
        <p className="text-secondary mb-4">
          Manage employees, organization settings, and access control.
        </p>
        <div className="row g-4">
          {entries.map(([resource, config]) => (
            <div className="col-12 col-md-6 col-lg-4" key={resource}>
              <Link
                className="card h-100 text-decoration-none"
                to={`/admin/${resource}`}
              >
                <div className="card-body p-4">
                  <h2 className="h5">{config.title}</h2>
                  <p className="text-secondary mb-0">
                    {administrationDescriptions[resource]}
                  </p>
                </div>
              </Link>
            </div>
          ))}
        </div>
      </>
    );
  }
  return <EmployeeDashboard user={user} />;
}
function EmployeeDashboard({ user }) {
  const state = useResource(
    () =>
      Promise.all([
        user.canApplyForLeave !== false ? api.leaves() : Promise.resolve(null),
        user.canAccessApprovals ? api.tasks() : Promise.resolve(null),
      ]),
    [user.canAccessApprovals, user.canApplyForLeave],
  );
  return (
    <>
      <p className="eyebrow">Your workspace</p>
      <h1>Dashboard</h1>
      <p className="mb-1">Welcome, {user.displayName || user.username}.</p>
      <p className="text-secondary mb-4">
        {user.canAccessApprovals
          ? "Manage your leave requests and review requests awaiting your approval."
          : "Keep track of your leave requests and upcoming actions."}
      </p>
      {state.loading ? (
        <Loading />
      ) : state.error ? (
        <ErrorNotice error={state.error} retry={state.retry} />
      ) : (
        <div className="row g-4">
          {[
            ...(state.data[1]
              ? [[state.data[1], "Pending Approvals", "/approvals"]]
              : []),
            ...(state.data[0]
              ? [[state.data[0], "My Leave Requests", "/leave-requests"]]
              : []),
          ].map(([data, label, to]) => (
            <div
              className={
                !user.canAccessApprovals && to === "/leave-requests"
                  ? "col-12 col-md-6 col-lg-4"
                  : "col-12 col-md-6"
              }
              key={to}
            >
              <Link className="card summary-card text-decoration-none" to={to}>
                <div className="card-body p-4">
                  <span className="text-secondary">{label}</span>
                  <div className="display-5 fw-semibold mt-2">
                    {data.totalElements}
                  </div>
                  <span className="small">View all →</span>
                </div>
              </Link>
            </div>
          ))}
        </div>
      )}
      {user.canApplyForLeave !== false && (
        <section className="card mt-4">
          <div className="card-body p-4">
            <h2 className="h5">Planning time away?</h2>
            <p className="text-secondary">
              Submit your dates and reason. Your request will follow the
              organization's approval workflow.
            </p>
            <Link className="btn btn-primary" to="/leave-requests/new">
              New Leave Request
            </Link>
          </div>
        </section>
      )}
    </>
  );
}
