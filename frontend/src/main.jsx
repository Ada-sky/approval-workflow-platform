import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Routes, Route, Link } from "react-router";
import "bootstrap/dist/css/bootstrap.min.css";
import "./styles.css";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./routes/ProtectedRoute";
import AppLayout from "./layouts/AppLayout";
import LoginPage from "./pages/LoginPage";
import ChangePasswordPage from "./pages/ChangePasswordPage";
import DashboardPage from "./pages/DashboardPage";
import RequestListPage from "./pages/RequestListPage";
import NewRequestPage from "./pages/NewRequestPage";
import RequestDetailsPage from "./pages/RequestDetailsPage";
import ApprovalHistoryPage from "./pages/ApprovalHistoryPage";
import AdminPage from "./admin/AdminPage";
import AdminEditorPage from "./admin/AdminEditorPage";
import RolePermissionsPage from "./admin/RolePermissionsPage";
import { resources } from "./admin/resources";
// Sign-in is public; session discovery gates the shared application layout.
// Approval routes add a capability gate, while administration pages check their
// numeric grants. All resource authorization still belongs to the backend.
createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <AuthProvider>
      <Routes>
        <Route path="sign-in" element={<LoginPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
            <Route path="change-password" element={<ChangePasswordPage />} />
            <Route index element={<DashboardPage />} />
            <Route path="leave-requests" element={<RequestListPage />} />
            <Route path="leave-requests/new" element={<NewRequestPage />} />
            <Route path="leave-requests/:id" element={<RequestDetailsPage />} />
            <Route element={<ProtectedRoute approver />}>
              <Route
                path="approvals/history"
                element={<ApprovalHistoryPage />}
              />
              <Route
                path="approvals/history/:id"
                element={<RequestDetailsPage historical />}
              />
              <Route path="approvals" element={<RequestListPage approvals />} />
              <Route
                path="approvals/:id"
                element={<RequestDetailsPage approval />}
              />
            </Route>
            {Object.keys(resources).map((resource) => (
              <React.Fragment key={resource}>
                <Route
                  path={`admin/${resource}`}
                  element={<AdminPage resource={resource} />}
                />
                {!resources[resource].readonly && (
                  <>
                    <Route
                      path={`admin/${resource}/new`}
                      element={<AdminEditorPage resource={resource} />}
                    />
                    <Route
                      path={`admin/${resource}/:id/edit`}
                      element={<AdminEditorPage resource={resource} />}
                    />
                  </>
                )}
              </React.Fragment>
            ))}
            <Route
              path="admin/employees/:id"
              element={<AdminEditorPage resource="employees" details />}
            />
            <Route
              path="admin/roles/:id/permissions"
              element={<RolePermissionsPage />}
            />
            <Route
              path="*"
              element={
                <>
                  <h1>Not Found</h1>
                  <p>This page does not exist.</p>
                  <Link to="/">Go to Dashboard</Link>
                </>
              }
            />
          </Route>
        </Route>
      </Routes>
    </AuthProvider>
  </BrowserRouter>,
);
