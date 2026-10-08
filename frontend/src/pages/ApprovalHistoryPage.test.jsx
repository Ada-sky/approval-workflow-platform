import { afterEach, beforeEach, expect, it, vi } from "vitest";
import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { api } from "../api/client";
import { useAuth } from "../context/AuthContext";
import ApprovalHistoryPage from "./ApprovalHistoryPage";
import RequestDetailsPage from "./RequestDetailsPage";
vi.mock("../api/client", () => ({
  api: { approvalHistory: vi.fn(), historicalLeave: vi.fn(), types: vi.fn() },
}));
vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
const entry = {
  requestId: 17,
  title: "Family time",
  applicantName: "Employee A",
  leaveType: "Annual Leave",
  startDate: "2026-12-15T00:00:00",
  endDate: "2026-12-20T00:00:00",
  decision: "APPROVED",
  stage: "DEPARTMENT_MANAGER",
  decidedAt: "2026-10-07T22:15:00",
};
beforeEach(() => {
  vi.resetAllMocks();
  useAuth.mockReturnValue({
    user: { username: "approver", accountId: 8, canAccessApprovals: true },
  });
  api.approvalHistory.mockResolvedValue({
    content: [entry],
    page: 0,
    totalPages: 2,
    totalElements: 11,
  });
  api.types.mockResolvedValue([{ id: 1, name: "Annual Leave" }]);
});
afterEach(cleanup);
function mount() {
  render(
    <MemoryRouter initialEntries={["/approvals/history"]}>
      <Routes>
        <Route path="/approvals/history" element={<ApprovalHistoryPage />} />
        <Route
          path="/approvals/history/:id"
          element={<RequestDetailsPage historical />}
        />
      </Routes>
    </MemoryRouter>,
  );
}
it.each(["APPROVED", "REJECTED"])(
  "renders %s history with readable dates and no visible ID",
  async (decision) => {
    api.approvalHistory.mockResolvedValue({
      content: [{ ...entry, decision }],
      page: 0,
      totalPages: 1,
      totalElements: 1,
    });
    mount();
    expect(await screen.findByText("Family time")).toBeInTheDocument();
    for (const text of [
      "Employee A",
      "Annual Leave",
      "15 Dec 2026 — 20 Dec 2026",
      "7 Oct 2026, 22:15",
      "Department Manager",
      decision === "APPROVED" ? "Approved" : "Rejected",
    ])
      expect(screen.getByText(text)).toBeInTheDocument();
    expect(screen.queryByText("#17")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "View" })).toHaveAttribute(
      "href",
      "/approvals/history/17",
    );
  },
);
it("paginates using the existing history API", async () => {
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Next" }));
  await waitFor(() => expect(api.approvalHistory).toHaveBeenCalledWith(1));
});
it("opens historical details read-only with progress and actual history", async () => {
  api.historicalLeave.mockResolvedValue({
    leaveRequest: {
      id: 17,
      title: "Family time",
      applicantName: "Employee A",
      applicantAccountId: 8,
      canWithdraw: true,
      leaveTypeId: 1,
      status: "COMPLETED",
      days: 6,
      reason: "Family",
      startDate: entry.startDate,
      endDate: entry.endDate,
      workflowProgress: {
        currentStage: "COMPLETED",
        message: "Approval workflow completed",
        stages: [],
      },
    },
    approvalHistory: [
      {
        id: 1,
        stage: "DEPARTMENT_MANAGER",
        decision: "APPROVED",
        approverUsername: "approver",
        approvedAt: entry.decidedAt,
      },
    ],
  });
  mount();
  fireEvent.click(await screen.findByRole("link", { name: "View" }));
  expect(
    await screen.findByText("Approval workflow completed"),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "Approval History" }),
  ).toBeInTheDocument();
  expect(api.historicalLeave).toHaveBeenCalledWith("17");
  for (const name of ["Approve", "Reject", "Withdraw Request", "Archive"])
    expect(screen.queryByRole("button", { name })).not.toBeInTheDocument();
});
it("shows historical details denial rather than exposing request data", async () => {
  api.historicalLeave.mockRejectedValue({
    status: 403,
    message: "Access denied",
  });
  mount();
  fireEvent.click(await screen.findByRole("link", { name: "View" }));
  expect(await screen.findByText("Forbidden")).toBeInTheDocument();
  expect(screen.queryByText("Family time")).not.toBeInTheDocument();
});
