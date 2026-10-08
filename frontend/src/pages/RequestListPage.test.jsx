import { afterEach, it, expect, vi } from "vitest";
import {
  render,
  screen,
  cleanup,
  within,
  fireEvent,
  waitFor,
} from "@testing-library/react";
import { MemoryRouter } from "react-router";
import { api } from "../api/client";
import RequestListPage from "./RequestListPage";
import { leaveDate, submittedDate } from "../utils/domain";

vi.mock("../api/client", () => ({
  api: { leaves: vi.fn(), types: vi.fn(), archive: vi.fn(), tasks: vi.fn() },
}));
afterEach(() => {
  cleanup();
  vi.resetAllMocks();
});

it("shows the six presentation columns, readable dates and unchanged View identity", async () => {
  api.types.mockResolvedValue([{ id: 1, name: "Annual Leave" }]);
  api.leaves.mockResolvedValue({
    content: [
      {
        id: 7,
        title: "holiday",
        leaveTypeId: 1,
        startDate: "2026-10-14T00:00:00",
        endDate: "2026-10-16T00:00:00",
        days: 3,
        submittedAt: "2026-10-05T17:39:00",
        status: "IN_APPROVAL",
      },
    ],
    totalPages: 1,
    page: 0,
  });
  render(
    <MemoryRouter>
      <RequestListPage />
    </MemoryRouter>,
  );
  const table = await screen.findByRole("table");
  expect(
    within(table)
      .getAllByRole("columnheader")
      .map((cell) => cell.textContent),
  ).toEqual(["Leave type", "Dates", "Days", "Submitted", "Status", "Actions"]);
  expect(within(table).getByText("Annual Leave")).toBeInTheDocument();
  expect(
    within(table).getByText("14 Oct 2026 – 16 Oct 2026"),
  ).toBeInTheDocument();
  expect(within(table).getByText("5 Oct 2026, 17:39")).toBeInTheDocument();
  expect(within(table).queryByText("holiday")).not.toBeInTheDocument();
  expect(within(table).queryByText("#7")).not.toBeInTheDocument();
  expect(within(table).queryByText(/00:00/)).not.toBeInTheDocument();
  expect(within(table).getByRole("link", { name: "View" })).toHaveAttribute(
    "href",
    "/leave-requests/7",
  );
});
it("formats calendar dates without timezone conversion and preserves submission minutes", () => {
  expect(leaveDate("2026-10-14T00:00:00+14:00")).toBe("14 Oct 2026");
  expect(submittedDate("2026-10-05T17:39:58")).toBe("5 Oct 2026, 17:39");
  expect(leaveDate(null)).toBe("Not available");
  expect(leaveDate("2026-02-31T00:00:00")).toBe("Not available");
  expect(submittedDate(null)).toBe("Not recorded");
});

it.each(["COMPLETED", "REJECTED", "WITHDRAWN"])(
  "archives terminal %s only after confirmation and refetches the list",
  async (status) => {
    api.types.mockResolvedValue([{ id: 1, name: "Annual Leave" }]);
    api.leaves
      .mockResolvedValueOnce({
        content: [{ id: 7, leaveTypeId: 1, days: 3, status }],
        totalPages: 1,
        page: 0,
      })
      .mockResolvedValue({ content: [], totalPages: 0, page: 0 });
    api.archive.mockResolvedValue();
    render(
      <MemoryRouter>
        <RequestListPage />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole("button", { name: "Archive" }));
    const dialog = screen.getByRole("dialog", {
      name: "Archive this request?",
    });
    expect(
      within(dialog).getByText(/approval history will be retained/),
    ).toBeInTheDocument();
    expect(api.archive).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(api.archive).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Archive" }));
    fireEvent.click(
      within(screen.getByRole("dialog")).getByRole("button", {
        name: "Archive",
      }),
    );
    await screen.findByText("Leave request archived.");
    await waitFor(() => expect(api.leaves).toHaveBeenCalledTimes(2));
    expect(api.archive).toHaveBeenCalledWith(7);
    expect(
      await screen.findByText("You have no leave requests yet."),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /Delete/ }),
    ).not.toBeInTheDocument();
  },
);
it.each(["SUBMITTED", "IN_APPROVAL"])(
  "does not show Archive for %s",
  async (status) => {
    api.types.mockResolvedValue([]);
    api.leaves.mockResolvedValue({
      content: [{ id: 7, days: 3, status }],
      totalPages: 1,
      page: 0,
    });
    render(
      <MemoryRouter>
        <RequestListPage />
      </MemoryRouter>,
    );
    await screen.findByRole("link", { name: "View" });
    expect(
      screen.queryByRole("button", { name: "Archive" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();
  },
);

it("keeps pending Review task navigation without displaying the request ID", async () => {
  api.types.mockResolvedValue([{ id: 1, name: "Annual Leave" }]);
  api.tasks.mockResolvedValue({
    content: [
      {
        taskId: "task-17",
        name: "HR approval",
        leaveRequest: {
          id: 17,
          title: "Family leave",
          leaveTypeId: 1,
          days: 2,
        },
      },
    ],
    page: 0,
    totalPages: 1,
    totalElements: 1,
  });
  render(
    <MemoryRouter>
      <RequestListPage approvals />
    </MemoryRouter>,
  );
  expect(await screen.findByText("Family leave")).toBeInTheDocument();
  expect(screen.queryByText("#17")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Review" })).toHaveAttribute(
    "href",
    "/approvals/task-17",
  );
});
