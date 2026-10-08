import { useState } from "react";
import { useNavigate } from "react-router";
import { useAuth } from "../context/AuthContext";
import { ErrorNotice } from "../components/UI";

/**
 * Collects the current password and a confirmed replacement for self-service
 * change. Client checks provide feedback; the backend validates and invalidates
 * the session. Submitted fields are cleared after either request outcome.
 */
export default function ChangePasswordPage() {
  const { changePassword } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState({
    currentPassword: "",
    newPassword: "",
    confirmation: "",
  });
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(null);
  async function submit(event) {
    event.preventDefault();
    setError(null);
    if (!values.currentPassword.trim()) {
      setError({ status: 400, message: "Current password is required." });
      return;
    }
    if (
      values.newPassword.length < 12 ||
      values.newPassword.length > 72 ||
      new TextEncoder().encode(values.newPassword).length > 72
    ) {
      setError({
        status: 400,
        message: "Use 12–72 characters, at most 72 UTF-8 bytes.",
      });
      return;
    }
    if (values.newPassword !== values.confirmation) {
      setError({ status: 400, message: "Password confirmation must match." });
      return;
    }
    if (values.newPassword === values.currentPassword) {
      setError({
        status: 400,
        message: "Choose a password different from your current password.",
      });
      return;
    }
    setBusy(true);
    try {
      await changePassword(values);
      setValues({ currentPassword: "", newPassword: "", confirmation: "" });
      navigate("/sign-in", {
        replace: true,
        state: {
          message: "Password changed successfully. Please sign in again.",
        },
      });
    } catch (failure) {
      setValues({ currentPassword: "", newPassword: "", confirmation: "" });
      setError(failure);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="card">
      <div className="card-body">
        <h1 className="h3">Change Password</h1>
        <p className="text-secondary">
          Minimum 12 characters. You will sign in again after changing your
          password.
        </p>
        <ErrorNotice error={error} />
        <form onSubmit={submit}>
          {[
            ["currentPassword", "Current password", "current-password"],
            ["newPassword", "New password", "new-password"],
            ["confirmation", "Confirm new password", "new-password"],
          ].map(([key, label, complete]) => (
            <div className="mb-3" key={key}>
              <label className="form-label" htmlFor={key}>
                {label} *
              </label>
              <input
                className="form-control"
                id={key}
                type="password"
                autoComplete={complete}
                required
                disabled={busy}
                value={values[key]}
                onChange={(event) =>
                  setValues({ ...values, [key]: event.target.value })
                }
              />
            </div>
          ))}
          <button className="btn btn-primary" disabled={busy}>
            {busy ? "Changing password…" : "Change password"}
          </button>
        </form>
      </div>
    </section>
  );
}
