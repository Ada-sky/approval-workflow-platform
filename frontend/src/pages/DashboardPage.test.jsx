import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { useAuth } from "../context/AuthContext";
import { api } from "../api/client";
import DashboardPage from "./DashboardPage";
vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../api/client", () => ({ api: { leaves: vi.fn(), tasks: vi.fn() } }));
beforeEach(() => {
  vi.resetAllMocks();
  api.leaves.mockResolvedValue({ totalElements: 2 });
  api.tasks.mockResolvedValue({ totalElements: 1 });
});
afterEach(cleanup);
function mount(user) {
  useAuth.mockReturnValue({ user });
  render(
    <MemoryRouter>
      <DashboardPage />
    </MemoryRouter>,
  );
}
it("shows authorized administrator cards with existing routes and responsive columns", () => {
  mount({
    canApplyForLeave: false,
    canAccessApprovals: true,
    authorities: ["10021", "10011", "10012", "10022", "10023"],
  });
  expect(
    screen.getByRole("heading", { name: "Administration Dashboard" }),
  ).toBeInTheDocument();
  expect(
    screen.getByText(
      "Manage employees, organization settings, and access control.",
    ),
  ).toBeInTheDocument();
  for (const [title, route] of [
    ["Employees", "employees"],
    ["Departments", "departments"],
    ["Job Titles", "job-titles"],
    ["Roles & Permissions", "roles"],
    ["Leave Types", "leave-types"],
  ]) {
    const link = screen.getByRole("link", { name: new RegExp(title) });
    expect(link).toHaveAttribute("href", `/admin/${route}`);
    expect(link.parentElement).toHaveClass("col-12", "col-md-6", "col-lg-4");
  }
  expect(screen.queryByText(/Keep track of requests/)).not.toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "New Leave Request" }),
  ).not.toBeInTheDocument();
  expect(api.leaves).not.toHaveBeenCalled();
  expect(api.tasks).not.toHaveBeenCalled();
});
it("filters cards using existing grants and keeps hidden resources hidden", () => {
  mount({ canApplyForLeave: false, authorities: ["10011", "10013"] });
  expect(screen.getAllByRole("link")).toHaveLength(1);
  expect(screen.getByRole("link", { name: /Departments/ })).toHaveAttribute(
    "href",
    "/admin/departments",
  );
  expect(screen.queryByText("Employee Status")).not.toBeInTheDocument();
});
it("does not invent access for an administrator without grants", () => {
  mount({ canApplyForLeave: false, authorities: [] });
  expect(
    screen.getByRole("heading", { name: "Administration Dashboard" }),
  ).toBeInTheDocument();
  expect(screen.queryByRole("link")).not.toBeInTheDocument();
  expect(api.leaves).not.toHaveBeenCalled();
  expect(api.tasks).not.toHaveBeenCalled();
});
it("preserves the employee dashboard", async () => {
  mount({
    displayName: "Employee A",
    username: "employee",
    canApplyForLeave: true,
    canAccessApprovals: false,
    authorities: [],
  });
  expect(await screen.findByText("2")).toBeInTheDocument();
  expect(screen.getByText("Welcome, Employee A.")).toBeInTheDocument();
  expect(
    screen.getByText("Keep track of your leave requests and upcoming actions."),
  ).toBeInTheDocument();
  const summary = screen.getByRole("link", { name: /My Leave Requests/ });
  expect(summary).toHaveAttribute("href", "/leave-requests");
  expect(summary.parentElement).toHaveClass("col-12", "col-md-6", "col-lg-4");
  expect(screen.getByText("View all →")).toBeInTheDocument();
  expect(screen.getByText("Planning time away?")).toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: "New Leave Request" }),
  ).toHaveAttribute("href", "/leave-requests/new");
  expect(
    screen.queryByText("Administration Dashboard"),
  ).not.toBeInTheDocument();
  expect(api.tasks).not.toHaveBeenCalled();
});
it("preserves the approver dashboard and pending task count", async () => {
  mount({
    username: "manager",
    canApplyForLeave: true,
    canAccessApprovals: true,
    authorities: [],
  });
  expect(await screen.findByText("Pending Approvals")).toBeInTheDocument();
  expect(screen.getByText("1")).toBeInTheDocument();
  const pending = screen.getByRole("link", { name: /Pending Approvals/ });
  const personal = screen.getByRole("link", { name: /My Leave Requests/ });
  expect(
    pending.compareDocumentPosition(personal) &
      Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(pending.parentElement.className).toBe(
    personal.parentElement.className,
  );
  expect(
    screen.getByText(
      "Manage your leave requests and review requests awaiting your approval.",
    ),
  ).toBeInTheDocument();
  expect(
    screen.queryByText(
      "Keep track of your leave requests and upcoming actions.",
    ),
  ).not.toBeInTheDocument();
  expect(api.tasks).toHaveBeenCalledTimes(1);
});

it("uses the existing username when display name is unavailable", () => {
  mount({
    username: "employee.a@example.test",
    canApplyForLeave: true,
    authorities: [],
  });
  expect(
    screen.getByText("Welcome, employee.a@example.test."),
  ).toBeInTheDocument();
});
