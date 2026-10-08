import { Navigate, Outlet } from "react-router";
import { useAuth } from "../context/AuthContext";
import { Loading, ErrorNotice } from "../components/UI";
/**
 * Waits for session discovery before redirecting unauthenticated visitors.
 * Approval capability gates navigation UX, not authorization of an individual
 * task; each API operation is still checked by the backend.
 */
export default function ProtectedRoute({ approver = false }) {
  const { user, loading, error, retry } = useAuth();
  if (loading) return <Loading />;
  if (error)
    return (
      <div className="container py-5">
        <ErrorNotice error={error} retry={retry} />
      </div>
    );
  if (!user) return <Navigate to="/sign-in" replace />;
  if (approver && !user.canAccessApprovals)
    return (
      <ErrorNotice
        error={{
          status: 403,
          message: "You do not have access to approval functionality.",
        }}
      />
    );
  return <Outlet />;
}
