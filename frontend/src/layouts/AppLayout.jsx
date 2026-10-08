import { useEffect, useRef, useState } from "react";
import { Link, NavLink, Outlet } from "react-router";
import { useAuth } from "../context/AuthContext";
import { ErrorNotice } from "../components/UI";
import { adminGroups } from "../admin/resources";
import Dropdown from "bootstrap/js/dist/dropdown";
/**
 * Hosts authenticated pages and permission-aware responsive navigation.
 * Backend capabilities and numeric grants hide unavailable actions for UX;
 * hiding links is never the application security boundary.
 */
export default function AppLayout() {
  const { user, logout } = useAuth();
  const profileToggle = useRef(null);
  const displayName = user.displayName || user.username;
  useEffect(() => {
    const dropdown = new Dropdown(profileToggle.current, { display: "static" });
    return () => dropdown.dispose();
  }, []);
  const [open, setOpen] = useState(false),
    [error, setError] = useState(null),
    [busy, setBusy] = useState(false);
  async function signOut() {
    setBusy(true);
    try {
      await logout();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="app-shell">
      <aside className="app-sidebar">
        <div className="brand">
          <span className="brand-mark">AW</span>
          <span>
            Approval Workflow
            <br />
            <small>Workflow workspace</small>
          </span>
        </div>
        <button
          className="btn btn-outline-light d-lg-none mb-3"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          Menu
        </button>
        <nav
          className={`${open ? "d-flex" : "d-none"} d-lg-flex flex-column gap-1`}
          aria-label="Main navigation"
        >
          <NavLink to="/" end onClick={() => setOpen(false)}>
            Dashboard
          </NavLink>
          {user.canAccessApprovals && (
            <div className="d-flex flex-column gap-1">
              {user.canApplyForLeave !== false && (
                <div className="text-uppercase small text-white-50 mt-4 mb-1">
                  Approvals
                </div>
              )}
              <NavLink to="/approvals" end onClick={() => setOpen(false)}>
                Pending Approvals
              </NavLink>
              <NavLink
                to="/approvals/history"
                end
                onClick={() => setOpen(false)}
              >
                Approval History
              </NavLink>
            </div>
          )}
          {user.canApplyForLeave !== false && (
            <div className="d-flex flex-column gap-1">
              <div className="text-uppercase small text-white-50 mt-4 mb-1">
                My Leave
              </div>
              <NavLink to="/leave-requests" end onClick={() => setOpen(false)}>
                My Leave Requests
              </NavLink>
              <NavLink
                to="/leave-requests/new"
                end
                onClick={() => setOpen(false)}
              >
                New Leave Request
              </NavLink>
            </div>
          )}
          {adminGroups(user).map((group) => (
            <div key={group.label} className="d-flex flex-column gap-1">
              <div className="text-uppercase small text-white-50 mt-4 mb-1">
                {group.label}
              </div>
              {group.entries.map(([resource, config]) => (
                <NavLink
                  key={resource}
                  to={`/admin/${resource}`}
                  onClick={() => setOpen(false)}
                >
                  {config.title}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>
      <div className="workspace">
        <header className="workspace-header">
          <span>Approval Workflow Platform</span>
          <div className="dropdown profile-dropdown ms-auto">
            <button
              ref={profileToggle}
              type="button"
              className="btn btn-sm btn-outline-secondary dropdown-toggle profile-toggle"
              data-bs-toggle="dropdown"
              aria-expanded="false"
              aria-label={`User menu for ${displayName}`}
            >
              <span className="text-truncate">{displayName}</span>
            </button>
            <ul className="dropdown-menu dropdown-menu-end">
              <li className="px-3 py-2 profile-identity">
                <div className="fw-semibold">{displayName}</div>
                <div className="small text-secondary">{user.username}</div>
              </li>
              <li>
                <hr className="dropdown-divider" />
              </li>
              <li>
                <Link className="dropdown-item" to="/change-password">
                  Change Password
                </Link>
              </li>
              <li>
                <button
                  type="button"
                  className="dropdown-item"
                  disabled={busy}
                  onClick={signOut}
                >
                  {busy ? "Signing out…" : "Logout"}
                </button>
              </li>
            </ul>
          </div>
        </header>
        <main className="workspace-content">
          <ErrorNotice error={error} />
          <Outlet />
        </main>
      </div>
    </div>
  );
}
