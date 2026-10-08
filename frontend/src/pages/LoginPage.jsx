import { useState } from "react";
import { Navigate, useNavigate, useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";
import { ErrorNotice, Loading } from "../components/UI";
/**
 * Submits credentials through the session/CSRF API flow and redirects only
 * after the current-user response establishes the authenticated UI state.
 */
export default function LoginPage() {
  const { login, user, loading, successMessage } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [name, setName] = useState(""),
    [password, setPassword] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  if (loading) return <Loading />;
  if (user) return <Navigate to="/" replace />;
  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(name, password);
      setPassword("");
      navigate("/", { replace: true });
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className="login-shell">
      <section className="login-intro">
        <span className="brand-mark">AW</span>
        <p className="eyebrow mt-4">Approval Workflow Platform</p>
        <h1>
          Less paperwork.
          <br />
          Clearer decisions.
        </h1>
        <p className="mt-4">
          Manage your leave requests and keep approvals moving in one secure
          workspace.
        </p>
      </section>
      <section className="login-card card border-0 shadow-sm">
        <div className="card-body p-4 p-sm-5">
          <h2 className="h3">Welcome back</h2>
          <p className="text-secondary mb-4">
            Sign in to your employee workspace.
          </p>
          {(successMessage || location.state?.message) && (
            <div role="status" className="alert alert-success">
              {successMessage || location.state?.message}
            </div>
          )}
          <ErrorNotice error={error} />
          <form onSubmit={submit}>
            <label className="form-label" htmlFor="username">
              Username / Email
            </label>
            <input
              className="form-control mb-3"
              id="username"
              autoComplete="username"
              required
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
            <label className="form-label" htmlFor="password">
              Password
            </label>
            <input
              className="form-control mb-4"
              id="password"
              type="password"
              autoComplete="current-password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
            <button className="btn btn-primary w-100" disabled={busy}>
              {busy ? "Signing in…" : "Sign in"}
            </button>
          </form>
          <p className="small text-secondary mt-4 mb-0">
            Use the account provided by your organization.
          </p>
        </div>
      </section>
    </main>
  );
}
