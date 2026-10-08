import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { useAuth } from "../context/AuthContext";
import AppLayout from "./AppLayout";
vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
let logout;
beforeEach(() => {
  logout = vi.fn().mockResolvedValue();
  useAuth.mockReturnValue({
    user: {
      displayName: "Employee A",
      username: "employee.a@example.test",
      authorities: [],
    },
    logout,
  });
});
afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});
function mount() {
  render(
    <MemoryRouter>
      <AppLayout />
    </MemoryRouter>,
  );
}
it("shows the display name and hides profile actions until opened", () => {
  mount();
  const toggle = screen.getByRole("button", {
    name: "User menu for Employee A",
  });
  expect(toggle).toHaveTextContent("Employee A");
  expect(
    screen.queryByText("A clear path from request to decision."),
  ).not.toBeInTheDocument();
  expect(toggle).not.toHaveTextContent("employee.a@example.test");
  const menu = toggle.nextElementSibling;
  expect(menu).not.toHaveClass("show");
  fireEvent.click(toggle);
  expect(menu).toHaveClass("show", "dropdown-menu-end");
  expect(toggle).toHaveAttribute("aria-expanded", "true");
  expect(screen.getByText("employee.a@example.test")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Change Password" })).toHaveAttribute(
    "href",
    "/change-password",
  );
  fireEvent.keyDown(toggle, { key: "Escape" });
  expect(menu).not.toHaveClass("show");
  expect(toggle).toHaveFocus();
});
it("supports Bootstrap arrow-key navigation and outside-click dismissal", () => {
  vi.spyOn(HTMLElement.prototype, "getClientRects").mockReturnValue([
    { width: 10, height: 10 },
  ]);
  mount();
  const toggle = screen.getByRole("button", {
    name: "User menu for Employee A",
  });
  fireEvent.keyDown(toggle, { key: "ArrowDown" });
  expect(screen.getByRole("link", { name: "Change Password" })).toHaveFocus();
  fireEvent.keyDown(document.activeElement, { key: "ArrowDown" });
  expect(screen.getByRole("button", { name: "Logout" })).toHaveFocus();
  fireEvent.click(document.body);
  expect(toggle).toHaveAttribute("aria-expanded", "false");
});
it("uses the existing logout action and preserves pending feedback", async () => {
  let finish;
  logout.mockReturnValue(
    new Promise((resolve) => {
      finish = resolve;
    }),
  );
  mount();
  fireEvent.click(
    screen.getByRole("button", { name: "User menu for Employee A" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Logout" }));
  expect(logout).toHaveBeenCalledTimes(1);
  expect(screen.getByRole("button", { name: "Signing out…" })).toBeDisabled();
  finish();
  await waitFor(() =>
    expect(screen.getByRole("button", { name: "Logout" })).not.toBeDisabled(),
  );
});
it("preserves logout failure feedback", async () => {
  logout.mockRejectedValue(new Error("Unable to sign out"));
  mount();
  fireEvent.click(
    screen.getByRole("button", { name: "User menu for Employee A" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Logout" }));
  expect(await screen.findByText("Unable to sign out")).toBeInTheDocument();
});
it("falls back to the real username and retains long secondary identity text", () => {
  const username = "a-very-long-existing-account-address@example.test";
  useAuth.mockReturnValue({ user: { username, authorities: [] }, logout });
  mount();
  const toggle = screen.getByRole("button", {
    name: `User menu for ${username}`,
  });
  expect(toggle).toHaveTextContent(username);
  expect(toggle).toHaveClass("profile-toggle");
  expect(
    toggle.nextElementSibling.querySelector(".profile-identity"),
  ).toHaveTextContent(username);
});

it("prioritizes approval navigation using the existing capability", () => {
  useAuth.mockReturnValue({
    user: {
      username: "approver",
      canApplyForLeave: true,
      canAccessApprovals: true,
      authorities: [],
    },
    logout,
  });
  mount();
  const approvals = screen.getByText("Approvals");
  const myLeave = screen.getByText("My Leave");
  expect(
    approvals.compareDocumentPosition(myLeave) &
      Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(approvals.parentElement).toContainElement(
    screen.getByRole("link", { name: "Pending Approvals" }),
  );
  expect(myLeave.parentElement).toContainElement(
    screen.getByRole("link", { name: "My Leave Requests" }),
  );
  expect(
    screen.getByRole("link", { name: "Approval History" }),
  ).toHaveAttribute("href", "/approvals/history");
  expect(approvals.parentElement).toContainElement(
    screen.getByRole("link", { name: "Approval History" }),
  );
  expect(
    screen
      .getByRole("link", { name: "Pending Approvals" })
      .compareDocumentPosition(
        screen.getByRole("link", { name: "Approval History" }),
      ) & Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
});
it("groups employee leave navigation without an approvals section", () => {
  mount();
  expect(screen.getByText("My Leave")).toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "Approval History" }),
  ).not.toBeInTheDocument();
  expect(screen.queryByText("Approvals")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "Pending Approvals" }),
  ).not.toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: "New Leave Request" }),
  ).toHaveAttribute("href", "/leave-requests/new");
});
it("preserves system administrator navigation without applicant sections", () => {
  useAuth.mockReturnValue({
    user: {
      username: "admin",
      canApplyForLeave: false,
      canAccessApprovals: true,
      authorities: ["10021"],
    },
    logout,
  });
  mount();
  expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute(
    "href",
    "/",
  );
  expect(
    screen.getByRole("link", { name: "Pending Approvals" }),
  ).toHaveAttribute("href", "/approvals");
  expect(screen.getByRole("link", { name: "Employees" })).toHaveAttribute(
    "href",
    "/admin/employees",
  );
  expect(screen.queryByText("My Leave")).not.toBeInTheDocument();
  expect(screen.queryByText("Approvals")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "My Leave Requests" }),
  ).not.toBeInTheDocument();
});
