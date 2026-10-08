import { afterEach, expect, it } from "vitest";
import { cleanup, render, screen, within } from "@testing-library/react";
import ApprovalProgress from "./ApprovalProgress";
afterEach(cleanup);
const labels = {
  SUBMITTED: "Submitted",
  DEPARTMENT_MANAGER: "Department Manager",
  GENERAL_MANAGER: "General Manager",
  HR: "HR",
  COMPLETED: "Completed",
};
function data(currentStage, statuses, message) {
  return {
    currentStage,
    message,
    stages: Object.entries(statuses).map(([stage, status]) => ({
      stage,
      label: labels[stage],
      status,
    })),
  };
}
it("uses the short backend route and highlights Department Manager", () => {
  render(
    <ApprovalProgress
      progress={data(
        "DEPARTMENT_MANAGER",
        {
          SUBMITTED: "COMPLETED",
          DEPARTMENT_MANAGER: "CURRENT",
          HR: "UPCOMING",
          COMPLETED: "UPCOMING",
        },
        "Awaiting Department Manager approval",
      )}
    />,
  );
  expect(screen.queryByText("General Manager")).not.toBeInTheDocument();
  expect(screen.getByRole("status")).toHaveTextContent(
    "Awaiting Department Manager approval",
  );
  expect(screen.getByText("Department Manager").closest("li")).toHaveAttribute(
    "aria-current",
    "step",
  );
});
it("shows the long backend route and General Manager current stage", () => {
  render(
    <ApprovalProgress
      progress={data(
        "GENERAL_MANAGER",
        {
          SUBMITTED: "COMPLETED",
          DEPARTMENT_MANAGER: "COMPLETED",
          GENERAL_MANAGER: "CURRENT",
          HR: "UPCOMING",
          COMPLETED: "UPCOMING",
        },
        "Awaiting General Manager approval",
      )}
    />,
  );
  expect(screen.getByText("General Manager").closest("li")).toHaveAttribute(
    "aria-current",
    "step",
  );
  expect(screen.getAllByRole("listitem")).toHaveLength(5);
  expect(screen.getByRole("status")).toHaveTextContent(
    "Awaiting General Manager approval",
  );
});
it("highlights HR from backend state", () => {
  render(
    <ApprovalProgress
      progress={data(
        "HR",
        {
          SUBMITTED: "COMPLETED",
          DEPARTMENT_MANAGER: "COMPLETED",
          HR: "CURRENT",
          COMPLETED: "UPCOMING",
        },
        "Awaiting HR approval",
      )}
    />,
  );
  expect(screen.getByText("HR").closest("li")).toHaveAttribute(
    "aria-current",
    "step",
  );
  expect(screen.getByRole("status")).toHaveTextContent("Awaiting HR approval");
});
it("shows successful completion without pending approvals", () => {
  render(
    <ApprovalProgress
      progress={data(
        "COMPLETED",
        {
          SUBMITTED: "COMPLETED",
          DEPARTMENT_MANAGER: "COMPLETED",
          HR: "COMPLETED",
          COMPLETED: "COMPLETED",
        },
        "Approval workflow completed",
      )}
    />,
  );
  expect(screen.getByRole("status")).toHaveTextContent(
    "Approval workflow completed",
  );
  expect(screen.queryByText("Current")).not.toBeInTheDocument();
  expect(screen.queryByText("Upcoming")).not.toBeInTheDocument();
});
it.each(["DEPARTMENT_MANAGER", "GENERAL_MANAGER", "HR"])(
  "shows rejection at %s with later stages not reached",
  (rejected) => {
    const stages = Object.keys(labels),
      position = stages.indexOf(rejected);
    render(
      <ApprovalProgress
        progress={data(
          "REJECTED",
          Object.fromEntries(
            stages.map((stage, i) => [
              stage,
              i < position
                ? "COMPLETED"
                : i === position
                  ? "REJECTED"
                  : "NOT_REACHED",
            ]),
          ),
          `Request rejected by ${labels[rejected]}`,
        )}
      />,
    );
    expect(screen.getByRole("status")).toHaveTextContent(
      `Request rejected by ${labels[rejected]}`,
    );
    expect(
      within(screen.getByText(labels[rejected]).closest("li")).getByText(
        "Rejected",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText("Current")).not.toBeInTheDocument();
    expect(screen.queryByText("Upcoming")).not.toBeInTheDocument();
    expect(screen.getAllByText("Not reached").length).toBeGreaterThan(0);
  },
);
it("stacks mobile steps and uses horizontal desktop Bootstrap layout", () => {
  render(
    <ApprovalProgress
      progress={data(
        "HR",
        { SUBMITTED: "COMPLETED", HR: "CURRENT" },
        "Awaiting HR approval",
      )}
    />,
  );
  expect(screen.getByRole("list")).toHaveClass("flex-column", "flex-md-row");
});
it("does not invent progress when the DTO is unavailable", () => {
  render(<ApprovalProgress />);
  expect(screen.getByRole("status")).toHaveTextContent(
    "Workflow progress is unavailable",
  );
  expect(screen.queryByRole("list")).not.toBeInTheDocument();
});
