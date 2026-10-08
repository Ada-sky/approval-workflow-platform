import { afterEach, beforeEach, it, expect, vi } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router";
import { AuthProvider } from "./context/AuthContext";
import ProtectedRoute from "./routes/ProtectedRoute";
import { api } from "./api/client";
import RequestDetailsPage from "./pages/RequestDetailsPage";
import ChangePasswordPage from "./pages/ChangePasswordPage";
import LoginPage from "./pages/LoginPage";
import NewRequestPage from "./pages/NewRequestPage";
import { LeaveSummary } from "./components/UI";
import { validateLeave, archivable } from "./utils/domain";
vi.mock("./api/client", () => ({
  api: {
    me: vi.fn(),
    changePassword: vi.fn(),
    task: vi.fn(),
    types: vi.fn(),
    decide: vi.fn(),
    create: vi.fn(),
    withdraw: vi.fn(),
    leave: vi.fn(),
    history: vi.fn(),
  },
  onUnauthorized: () => () => {},
}));
const leave = {
  id: 7,
  title: "Family leave",
  leaveTypeId: 1,
  reason: "Family time",
  days: 2,
  status: "IN_APPROVAL",
  startDate: "2026-11-01T09:00:00",
  endDate: "2026-11-02T09:00:00",
};
afterEach(cleanup);
beforeEach(() => {
  vi.resetAllMocks();
  api.me.mockResolvedValue({ username: "employee", canApplyForLeave: true });
  api.types.mockResolvedValue([{ id: 1, name: "Annual leave" }]);
});
it("redirects unauthenticated users to login", async () => {
  api.me.mockRejectedValue({ status: 401 });
  render(
    <MemoryRouter>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<p>Private</p>} />
          </Route>
          <Route path="/sign-in" element={<p>Login required</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  expect(await screen.findByText("Login required")).toBeInTheDocument();
  expect(screen.queryByText("Private")).not.toBeInTheDocument();
});
it("renders authenticated protected content", async () => {
  api.me.mockResolvedValue({ username: "employee" });
  render(
    <MemoryRouter>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<p>Private</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  expect(await screen.findByText("Private")).toBeInTheDocument();
});
it("denies approval routes without capability", async () => {
  api.me.mockResolvedValue({ username: "employee", canAccessApprovals: false });
  render(
    <MemoryRouter>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute approver />}>
            <Route path="/" element={<p>Private</p>} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  expect(await screen.findByText("Forbidden")).toBeInTheDocument();
});
it("renders English leave status without exposing editable actions", () => {
  render(
    <LeaveSummary
      leave={leave}
      types={[{ id: 1, name: "Annual leave" }]}
      applicant="employee"
    />,
  );
  expect(screen.getByText("In Review")).toBeInTheDocument();
  expect(screen.getByText("Annual leave")).toBeInTheDocument();
  expect(screen.queryByText("Edit")).not.toBeInTheDocument();
});
it("validates required fields and leave date order", () => {
  expect(
    Object.keys(
      validateLeave({
        title: "",
        reason: "",
        leaveTypeId: "",
        startDate: "",
        endDate: "",
      }),
    ),
  ).toHaveLength(5);
  expect(
    validateLeave({
      title: "Leave",
      reason: "Rest",
      leaveTypeId: 1,
      startDate: "2026-11-02T09:00",
      endDate: "2026-11-01T09:00",
    }).endDate,
  ).toBeTruthy();
  expect(archivable("IN_APPROVAL")).toBe(false);
  expect(archivable("REJECTED")).toBe(true);
});
it.each([
  ["2026-11-01", "2026-11-01"],
  ["2026-11-01", "2026-11-03"],
  ["2026-11-01", "2026-11-04"],
])(
  "submits date-only selections %s to %s as midnight API values",
  async (start, end) => {
    api.create.mockResolvedValue({ id: 7 });
    render(
      <MemoryRouter>
        <Routes>
          <Route
            path="/"
            element={
              <AuthProvider>
                <NewRequestPage />
              </AuthProvider>
            }
          />
          <Route path="/leave-requests/7" element={<p>Request created</p>} />
        </Routes>
      </MemoryRouter>,
    );
    await screen.findByRole("button", { name: "Submit Leave Request" });
    expect(screen.getByLabelText("Reason for leave")).toHaveAttribute(
      "placeholder",
      "Briefly describe the reason for your leave",
    );
    expect(
      screen.getByLabelText("Additional comment (optional)"),
    ).toHaveAttribute(
      "placeholder",
      "Add any additional information for the approver",
    );
    expect(screen.getByText("Before you submit")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Dates are counted inclusively. A request with the same start and end date counts as one day. Once submitted, the request enters the approval workflow and cannot be edited.",
      ),
    ).toBeInTheDocument();
    const startInput = screen.getByLabelText("Start date");
    const endInput = screen.getByLabelText("End date");
    expect(startInput).toHaveAttribute("type", "date");
    expect(endInput).toHaveAttribute("type", "date");
    fireEvent.change(screen.getByLabelText("Title"), {
      target: { value: "Leave" },
    });
    fireEvent.change(screen.getByLabelText("Leave type"), {
      target: { value: "1" },
    });
    fireEvent.change(screen.getByLabelText("Reason for leave"), {
      target: { value: "Rest" },
    });
    fireEvent.change(startInput, { target: { value: start } });
    fireEvent.change(endInput, { target: { value: end } });
    fireEvent.click(
      screen.getByRole("button", { name: "Submit Leave Request" }),
    );
    await screen.findByText("Request created");
    expect(api.create).toHaveBeenCalledWith(
      expect.objectContaining({
        startDate: `${start}T00:00:00`,
        endDate: `${end}T00:00:00`,
        leaveTypeId: 1,
      }),
    );
  },
);
it("renders applicant name and submission time from the DTO", () => {
  render(
    <LeaveSummary
      leave={{
        ...leave,
        applicantName: "Alice Employee",
        submittedAt: "2026-10-04T09:30:00",
      }}
      applicant="legacy-username"
    />,
  );
  expect(screen.getByText("Alice Employee")).toBeInTheDocument();
  expect(screen.getByText("4 Oct 2026, 09:30")).toBeInTheDocument();
  expect(screen.queryByText("legacy-username")).not.toBeInTheDocument();
});
it("uses safe fallbacks for incomplete historical DTOs", () => {
  render(
    <LeaveSummary
      leave={{ ...leave, applicantName: null, submittedAt: null }}
    />,
  );
  expect(screen.getByText("Unknown applicant")).toBeInTheDocument();
  expect(screen.getByText("Not recorded")).toBeInTheDocument();
});
it("blocks invalid submission before API request", async () => {
  render(
    <MemoryRouter>
      <AuthProvider>
        <NewRequestPage />
      </AuthProvider>
    </MemoryRouter>,
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Submit Leave Request" }),
  );
  expect(await screen.findByText("Enter a title.")).toBeInTheDocument();
  expect(api.create).not.toHaveBeenCalled();
});
it.each(["Approve", "Reject"])(
  "requires confirmation before %s and sends task/request IDs",
  async (label) => {
    api.me.mockResolvedValue({ username: "manager", canAccessApprovals: true });
    api.task.mockResolvedValue({
      taskId: "task-1",
      leaveRequest: leave,
      approvalHistory: [],
    });
    api.decide.mockResolvedValue(null);
    render(
      <MemoryRouter initialEntries={["/approvals/task-1"]}>
        <AuthProvider>
          <Routes>
            <Route
              path="/approvals/:id"
              element={<RequestDetailsPage approval />}
            />
            <Route path="/approvals" element={<p>Pending list</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole("button", { name: label }));
    expect(api.decide).not.toHaveBeenCalled();
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
    await waitFor(() =>
      expect(api.decide).toHaveBeenCalledWith("task-1", label.toLowerCase(), {
        leaveRequestId: 7,
        comment: "",
      }),
    );
    expect(await screen.findByText("Pending list")).toBeInTheDocument();
  },
);

it("places backend Approval Progress between request information and actual history", async () => {
  api.me.mockResolvedValue({ username: "employee" });
  api.leave.mockResolvedValue({
    ...leave,
    startDate: "2026-10-07T00:00:00",
    endDate: "2026-10-08T00:00:00",
    submittedAt: "2026-10-05T20:57:00",
    workflowProgress: {
      currentStage: "HR",
      message: "Awaiting HR approval",
      stages: [
        { stage: "SUBMITTED", label: "Submitted", status: "COMPLETED" },
        { stage: "HR", label: "HR", status: "CURRENT" },
      ],
    },
  });
  api.history.mockResolvedValue([
    {
      id: 1,
      stage: "DEPARTMENT_MANAGER",
      decision: "APPROVED",
      approverUsername: "manager",
      approvedAt: "2026-10-05T20:58:00",
    },
  ]);
  render(
    <MemoryRouter initialEntries={["/leave-requests/7"]}>
      <AuthProvider>
        <Routes>
          <Route path="/leave-requests/:id" element={<RequestDetailsPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  const progressHeading = await screen.findByRole("heading", {
    name: "Approval Progress",
  });
  const historyHeading = screen.getByRole("heading", {
    name: "Approval History",
  });
  expect(
    progressHeading.compareDocumentPosition(historyHeading) &
      Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(
    screen.getByText("Family leave").compareDocumentPosition(progressHeading) &
      Node.DOCUMENT_POSITION_FOLLOWING,
  ).toBeTruthy();
  expect(screen.getByText("Awaiting HR approval")).toBeInTheDocument();
  expect(screen.getByText("7 Oct 2026 — 8 Oct 2026")).toBeInTheDocument();
  expect(screen.getByText("5 Oct 2026, 20:57")).toBeInTheDocument();
  expect(screen.getByText("manager · 5 Oct 2026, 20:58")).toBeInTheDocument();
  expect(screen.queryByText(/00:00/)).not.toBeInTheDocument();
});

it("blocks the system-only administrator from the leave submission page", async () => {
  api.me.mockResolvedValue({ username: "admin", canApplyForLeave: false });
  render(
    <MemoryRouter>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route
              path="/"
              element={
                <AuthProvider>
                  <NewRequestPage />
                </AuthProvider>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  expect(
    await screen.findByText(
      "System administration accounts cannot apply for leave.",
    ),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Submit Request" }),
  ).not.toBeInTheDocument();
  expect(api.create).not.toHaveBeenCalled();
});

function withdrawalDetails({ owner = true, canWithdraw = true } = {}) {
  api.me.mockResolvedValue({
    username: owner ? "employee" : "admin",
    accountId: owner ? 11 : 16,
  });
  const request = { ...leave, applicantAccountId: 11, canWithdraw };
  api.leave.mockResolvedValue(request);
  api.history.mockResolvedValue([
    {
      id: 1,
      stage: "DEPARTMENT_MANAGER",
      decision: "APPROVED",
      approverUsername: "manager",
      approvedAt: "2026-11-01T10:00:00",
    },
  ]);
  render(
    <MemoryRouter initialEntries={["/leave-requests/7"]}>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route
              path="/leave-requests/:id"
              element={<RequestDetailsPage />}
            />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  return request;
}
it("owner sees Withdraw only when backend allows it and must confirm", async () => {
  withdrawalDetails();
  fireEvent.click(
    await screen.findByRole("button", { name: "Withdraw Request" }),
  );
  expect(
    screen.getByText(
      "Are you sure you want to withdraw this leave request? This action cannot be undone.",
    ),
  ).toBeInTheDocument();
  expect(api.withdraw).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
});
it.each([{ canWithdraw: false }, { owner: false }])(
  "hides withdrawal from ineligible owner or non-owner/Admin: %o",
  async (options) => {
    withdrawalDetails(options);
    await screen.findByText("Family leave");
    expect(
      screen.queryByRole("button", { name: "Withdraw Request" }),
    ).not.toBeInTheDocument();
  },
);
it("successful withdrawal reloads details/progress and preserves approval history", async () => {
  const request = withdrawalDetails();
  api.withdraw.mockResolvedValue(undefined);
  await screen.findByRole("button", { name: "Withdraw Request" });
  api.leave.mockResolvedValue({
    ...request,
    status: "WITHDRAWN",
    canWithdraw: false,
    workflowProgress: {
      currentStage: "WITHDRAWN",
      message: "Request withdrawn by applicant",
      stages: [
        { stage: "SUBMITTED", label: "Submitted", status: "COMPLETED" },
        {
          stage: "DEPARTMENT_MANAGER",
          label: "Department Manager",
          status: "COMPLETED",
        },
        { stage: "HR", label: "HR", status: "NOT_REACHED" },
        { stage: "WITHDRAWN", label: "Withdrawn", status: "WITHDRAWN" },
      ],
    },
  });
  fireEvent.click(screen.getByRole("button", { name: "Withdraw Request" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  expect(
    await screen.findByText("Request withdrawn by applicant"),
  ).toBeInTheDocument();
  expect(
    screen.getByText("Leave request withdrawn successfully."),
  ).toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Withdraw Request" }),
  ).not.toBeInTheDocument();
  expect(screen.getByText(/manager ·/)).toBeInTheDocument();
  expect(api.withdraw).toHaveBeenCalledWith("7");
  expect(api.leave).toHaveBeenCalledTimes(2);
});
it("withdrawal API conflict is shown without claiming success", async () => {
  withdrawalDetails();
  api.withdraw.mockRejectedValue({
    status: 409,
    message: "This request cannot be withdrawn",
  });
  fireEvent.click(
    await screen.findByRole("button", { name: "Withdraw Request" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  expect(
    await screen.findByText("This request cannot be withdrawn"),
  ).toBeInTheDocument();
  expect(
    screen.queryByText("Leave request withdrawn successfully."),
  ).not.toBeInTheDocument();
  expect(api.leave).toHaveBeenCalledTimes(1);
});

it("password-change success clears real auth state and displays the sign-in message", async () => {
  api.changePassword.mockResolvedValue(undefined);
  render(
    <MemoryRouter initialEntries={["/change-password"]}>
      <AuthProvider>
        <Routes>
          <Route element={<ProtectedRoute />}>
            <Route path="/change-password" element={<ChangePasswordPage />} />
          </Route>
          <Route path="/sign-in" element={<LoginPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  await screen.findByLabelText("Current password *");
  for (const [label, value] of [
    ["Current password *", "old-safe-passphrase"],
    ["New password *", "new-safe-passphrase"],
    ["Confirm new password *", "new-safe-passphrase"],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
  expect(
    await screen.findByText(
      "Password changed successfully. Please sign in again.",
    ),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("heading", { name: "Welcome back" }),
  ).toBeInTheDocument();
});
