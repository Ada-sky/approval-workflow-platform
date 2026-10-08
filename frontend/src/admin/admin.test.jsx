import { afterEach, beforeEach, it, expect, vi } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter, Routes, Route } from "react-router";
import { useAuth } from "../context/AuthContext";
import { adminApi } from "../api/admin";
import AppLayout from "../layouts/AppLayout";
import AdminPage from "./AdminPage";
import AdminEditorPage from "./AdminEditorPage";
import RolePermissionsPage from "./RolePermissionsPage";
import { resources, validateForm } from "./resources";
vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
vi.mock("../api/admin", () => ({
  adminApi: {
    list: vi.fn(),
    get: vi.fn(),
    save: vi.fn(),
    remove: vi.fn(),
    all: vi.fn(),
    employeeOptions: vi.fn(),
    assignments: vi.fn(),
    assign: vi.fn(),
    unassign: vi.fn(),
    grants: vi.fn(),
    saveGrants: vi.fn(),
  },
}));
const page = (content = []) => ({
  content,
  page: 0,
  size: 20,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
});
const employee = {
  id: 2,
  name: "Alice",
  email: "alice@example.test",
  mobile: "555123",
  number: "E02",
};
function mount(element, url = "/", route = "*") {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path={route} element={element} />
        <Route path="/admin/saved" element={<p>Saved</p>} />
      </Routes>
    </MemoryRouter>,
  );
}
beforeEach(() => {
  vi.resetAllMocks();
  useAuth.mockReturnValue({
    user: {
      username: "admin",
      authorities: [
        "10021",
        "100211",
        "100212",
        "100213",
        "100214",
        "10011",
        "10012",
        "10013",
        "10022",
        "10023",
      ],
    },
    logout: vi.fn(),
  });
  adminApi.list.mockResolvedValue(page([employee]));
  adminApi.get.mockResolvedValue(employee);
  adminApi.save.mockResolvedValue({
    ...employee,
    temporaryPassword: "test-only-temporary-value",
  });
  adminApi.remove.mockResolvedValue(null);
  adminApi.all.mockResolvedValue([]);
  adminApi.employeeOptions.mockResolvedValue({
    departments: [],
    jobTitles: [],
  });
});
afterEach(cleanup);
it("shows authorized administration navigation", () => {
  mount(<AppLayout />);
  expect(screen.getByRole("link", { name: "Employees" })).toHaveAttribute(
    "href",
    "/admin/employees",
  );
  expect(
    screen.getByRole("link", { name: "Roles & Permissions" }),
  ).toBeInTheDocument();
});
it("hides administration navigation for normal employees", () => {
  useAuth.mockReturnValue({ user: { username: "employee", authorities: [] } });
  mount(<AppLayout />);
  expect(screen.queryByText("Administration")).not.toBeInTheDocument();
  expect(
    screen.getByRole("link", { name: "My Leave Requests" }),
  ).toBeInTheDocument();
});
it("only shows permitted administration navigation", () => {
  useAuth.mockReturnValue({
    user: { username: "department admin", authorities: ["10011"] },
  });
  mount(<AppLayout />);
  expect(screen.getByRole("link", { name: "Departments" })).toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "Employees" }),
  ).not.toBeInTheDocument();
});
it("denies direct administration navigation without authority", async () => {
  useAuth.mockReturnValue({ user: { authorities: [] } });
  mount(<AdminPage resource="employees" />);
  expect(screen.getByText("Forbidden")).toBeInTheDocument();
  expect(adminApi.list).not.toHaveBeenCalled();
});
it("renders employee list and pagination", async () => {
  adminApi.list.mockResolvedValue({
    ...page([employee]),
    totalElements: 21,
    totalPages: 2,
  });
  mount(<AdminPage resource="employees" />);
  expect(await screen.findByText("Alice")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Next" }));
  await waitFor(() =>
    expect(adminApi.list).toHaveBeenCalledWith("employees", 1, ""),
  );
});
it("hides employee mutation actions from a read-only administrator", async () => {
  useAuth.mockReturnValue({ user: { authorities: ["100214"] } });
  mount(<AdminPage resource="employees" />);
  await screen.findByText("Alice");
  expect(
    screen.queryByRole("button", { name: "Delete" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "Create employee" }),
  ).not.toBeInTheDocument();
});
it("validates employee creation before persistence", async () => {
  mount(<AdminEditorPage resource="employees" />, "/admin/employees/new");
  fireEvent.click(await screen.findByRole("button", { name: "Save" }));
  expect(await screen.findByText("Name is required.")).toBeInTheDocument();
  expect(adminApi.save).not.toHaveBeenCalled();
});
it("creates an employee without a client-supplied password", async () => {
  mount(<AdminEditorPage resource="employees" />, "/admin/employees/new");
  await screen.findByLabelText("Name *");
  fireEvent.change(screen.getByLabelText("Name *"), {
    target: { value: "Alice" },
  });
  fireEvent.change(screen.getByLabelText("Email *"), {
    target: { value: "alice@example.test" },
  });
  fireEvent.change(screen.getByLabelText("Mobile *"), {
    target: { value: "555" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(adminApi.save).toHaveBeenCalledWith(
      "employees",
      undefined,
      expect.objectContaining({
        name: "Alice",
      }),
    ),
  );
});
it("loads department manager choices and creates a department", async () => {
  adminApi.all.mockImplementation((resource) =>
    Promise.resolve(
      resource === "departments/manager-options"
        ? [{ id: 3, name: "Manager" }]
        : [],
    ),
  );
  mount(<AdminEditorPage resource="departments" />, "/admin/departments/new");
  await screen.findByLabelText("Department number *");
  fireEvent.change(screen.getByLabelText("Department number *"), {
    target: { value: "D03" },
  });
  fireEvent.change(screen.getByLabelText("Name *"), {
    target: { value: "Support" },
  });
  fireEvent.change(screen.getByLabelText("Department manager"), {
    target: { value: "3" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(adminApi.save).toHaveBeenCalledWith(
      "departments",
      undefined,
      expect.objectContaining({
        number: "D03",
        managerEmployeeId: 3,
        parentId: 0,
      }),
    ),
  );
});
it("updates a department", async () => {
  adminApi.get.mockResolvedValue({
    id: 3,
    number: "D03",
    name: "Support",
    level: 1,
  });
  mount(
    <AdminEditorPage resource="departments" />,
    "/admin/departments/3/edit",
    "/admin/departments/:id/edit",
  );
  await screen.findByLabelText("Name *");
  fireEvent.change(screen.getByLabelText("Name *"), {
    target: { value: "Customer Support" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(adminApi.save).toHaveBeenCalledWith(
      "departments",
      "3",
      expect.objectContaining({ name: "Customer Support" }),
    ),
  );
});
it("confirms department deletion before sending a mutation", async () => {
  adminApi.list.mockResolvedValue(
    page([{ id: 3, name: "Support", number: "D03" }]),
  );
  mount(<AdminPage resource="departments" />);
  fireEvent.click(await screen.findByRole("button", { name: "Delete" }));
  expect(adminApi.remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Cancel" }));
  expect(adminApi.remove).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Delete" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  await waitFor(() =>
    expect(adminApi.remove).toHaveBeenCalledWith("departments", 3),
  );
  expect(await screen.findByText("Department removed.")).toBeInTheDocument();
});
it("shows API 403 even when navigation permits access", async () => {
  adminApi.list.mockRejectedValue({
    status: 403,
    message: "Permission revoked",
  });
  mount(<AdminPage resource="employees" />);
  expect(await screen.findByText("Forbidden")).toBeInTheDocument();
  expect(screen.getByText("Permission revoked")).toBeInTheDocument();
});
it("shows loading state", () => {
  adminApi.list.mockReturnValue(new Promise(() => {}));
  mount(<AdminPage resource="employees" />);
  expect(screen.getByText("Loading…")).toBeInTheDocument();
});
it("shows empty state", async () => {
  adminApi.list.mockResolvedValue(page());
  mount(<AdminPage resource="employees" />);
  expect(await screen.findByText("No employees found.")).toBeInTheDocument();
});
it("shows API validation feedback on save", async () => {
  adminApi.get.mockResolvedValue({ id: 1, name: "Active" });
  adminApi.save.mockRejectedValue({
    status: 400,
    message: "Duplicate status name",
  });
  mount(
    <AdminEditorPage resource="employee-statuses" />,
    "/admin/employee-statuses/1/edit",
    "/admin/employee-statuses/:id/edit",
  );
  fireEvent.click(await screen.findByRole("button", { name: "Save" }));
  expect(await screen.findByText("Duplicate status name")).toBeInTheDocument();
});
it("shows leave type administration actions", async () => {
  mount(<AdminPage resource="leave-types" />);
  await screen.findByText("Alice");
  expect(screen.getByRole("button", { name: "Delete" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Add leave type" })).toHaveAttribute(
    "href",
    "/admin/leave-types/new",
  );
  expect(screen.getByText(/Leave types available/)).toBeInTheDocument();
});
function roleMocks() {
  adminApi.get.mockResolvedValue({ id: 2, name: "Manager" });
  adminApi.assignments.mockResolvedValue(
    page([
      {
        id: 7,
        accountId: 13,
        roleId: 2,
        accountDisplayName: "Alice",
        accountEmail: "alice@example.test",
      },
    ]),
  );
  adminApi.all.mockImplementation((resource) =>
    Promise.resolve(
      resource === "employees"
        ? [employee]
        : [{ id: 300, name: "Workbench", code: "workbench", parentId: 0 }],
    ),
  );
  adminApi.grants.mockResolvedValue([]);
  adminApi.assign.mockResolvedValue(null);
  adminApi.unassign.mockResolvedValue(null);
  adminApi.saveGrants.mockResolvedValue(null);
}
it("assigns a role using employee ID", async () => {
  roleMocks();
  mount(
    <RolePermissionsPage />,
    "/admin/roles/2/permissions",
    "/admin/roles/:id/permissions",
  );
  await screen.findByLabelText("Employee");
  fireEvent.change(screen.getByLabelText("Employee"), {
    target: { value: "2" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Assign role" }));
  await waitFor(() => expect(adminApi.assign).toHaveBeenCalledWith("2", "2"));
  expect(await screen.findByText("Role assigned.")).toBeInTheDocument();
});
it("confirms grant replacement and sends menu IDs", async () => {
  roleMocks();
  mount(
    <RolePermissionsPage />,
    "/admin/roles/2/permissions",
    "/admin/roles/:id/permissions",
  );
  fireEvent.click(
    await screen.findByRole("checkbox", { name: "Grant Workbench" }),
  );
  fireEvent.click(screen.getByRole("button", { name: "Save grants" }));
  expect(adminApi.saveGrants).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  await waitFor(() =>
    expect(adminApi.saveGrants).toHaveBeenCalledWith("2", [300]),
  );
});
it("removes role assignment using actual account ID", async () => {
  roleMocks();
  mount(
    <RolePermissionsPage />,
    "/admin/roles/2/permissions",
    "/admin/roles/:id/permissions",
  );
  fireEvent.click(
    await screen.findByRole("button", { name: "Remove assignment" }),
  );
  expect(adminApi.unassign).not.toHaveBeenCalled();
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  await waitFor(() => expect(adminApi.unassign).toHaveBeenCalledWith("2", 13));
});

it("hides applicant navigation for system-only admin while preserving numeric administration grants", () => {
  useAuth.mockReturnValue({
    user: {
      username: "admin",
      authorities: ["10021", "100211"],
      canApplyForLeave: false,
    },
    logout: vi.fn(),
  });
  mount(<AppLayout />);
  expect(
    screen.queryByRole("link", { name: "New Leave Request" }),
  ).not.toBeInTheDocument();
  expect(
    screen.queryByRole("link", { name: "My Leave Requests" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Employees" })).toBeInTheDocument();
});

it("groups business navigation and hides infrastructure pages", () => {
  mount(<AppLayout />);
  for (const label of ["People", "Access Control", "Settings"])
    expect(screen.getByText(label)).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Job Titles" })).toBeInTheDocument();
  for (const label of ["Employee Status", "Menus"])
    expect(screen.queryByRole("link", { name: label })).not.toBeInTheDocument();
});
it("renders employee reference names without IDs and retains route identity", async () => {
  adminApi.list.mockResolvedValue(
    page([
      {
        ...employee,
        id: 987,
        departmentName: "Operations",
        jobTitleName: "HR",
        employeeStatusName: "Active",
      },
    ]),
  );
  mount(<AdminPage resource="employees" />);
  await screen.findByText("Operations");
  expect(screen.getByText("HR")).toBeInTheDocument();
  expect(screen.getByText("Active")).toBeInTheDocument();
  expect(screen.queryByText("987")).not.toBeInTheDocument();
  expect(
    screen.queryByRole("button", { name: "Delete" }),
  ).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "View" })).toHaveAttribute(
    "href",
    "/admin/employees/987",
  );
  expect(screen.getAllByRole("columnheader").map((x) => x.textContent)).toEqual(
    ["Employee", "Department", "Job Title", "Status", "Email", "Actions"],
  );
});
it("renders named department relations and root parents", async () => {
  adminApi.list.mockResolvedValue(
    page([
      {
        id: 987,
        name: "Operations",
        number: "D001",
        managerName: "Manager",
        parentDepartmentName: "Head Office",
      },
      { id: 988, name: "Root", number: "D002" },
    ]),
  );
  mount(<AdminPage resource="departments" />);
  await screen.findByText("Head Office");
  expect(screen.getByRole("cell", { name: "Manager" })).toBeInTheDocument();
  expect(screen.getAllByText("—")).toHaveLength(2);
  expect(screen.queryByText("987")).not.toBeInTheDocument();
});
it("shows English job title display while retaining raw names for edits", async () => {
  adminApi.list.mockResolvedValue(
    page([
      {
        id: 987,
        number: "T001",
        name: "\u603b\u7ecf\u7406",
        displayName: "General Manager",
      },
    ]),
  );
  mount(<AdminPage resource="job-titles" />);
  await screen.findByText("General Manager");
  expect(
    screen.getByRole("heading", { name: "Job Titles" }),
  ).toBeInTheDocument();
  expect(screen.queryByText("\u603b\u7ecf\u7406")).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Edit" })).toHaveAttribute(
    "href",
    "/admin/job-titles/987/edit",
  );
});
it("shows assignment identities and business permission labels without legacy details", async () => {
  roleMocks();
  adminApi.all.mockImplementation((resource) =>
    Promise.resolve(
      resource === "employees"
        ? [employee]
        : [
            {
              id: 300,
              name: "Legacy",
              code: "10011",
              url: "/dept/index",
              parentId: 997,
            },
          ],
    ),
  );
  mount(
    <RolePermissionsPage />,
    "/admin/roles/2/permissions",
    "/admin/roles/:id/permissions",
  );
  await screen.findByText("alice@example.test");
  expect(
    screen.getByRole("checkbox", { name: "Grant Manage Departments" }),
  ).toBeInTheDocument();
  for (const text of ["Account #13", "10011", "/dept/index", "997"])
    expect(screen.queryByText(text)).not.toBeInTheDocument();
});
it("renders leave types without primary keys", async () => {
  adminApi.list.mockResolvedValue(page([{ id: 987, name: "Annual Leave" }]));
  mount(<AdminPage resource="leave-types" />);
  await screen.findByText("Annual Leave");
  expect(screen.getAllByRole("columnheader").map((x) => x.textContent)).toEqual(
    ["Leave Type", "Actions"],
  );
  expect(screen.queryByText("987")).not.toBeInTheDocument();
});

it("creates a leave type through the admin endpoint", async () => {
  mount(<AdminEditorPage resource="leave-types" />, "/admin/leave-types/new");
  fireEvent.click(await screen.findByRole("button", { name: "Save" }));
  expect(adminApi.save).not.toHaveBeenCalled();
  expect(screen.getByText("Leave type name is required.")).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText("Leave type name *"), {
    target: { value: "Personal Leave" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(adminApi.save).toHaveBeenCalledWith("admin/leave-types", undefined, {
      name: "Personal Leave",
    }),
  );
});
it("edits a leave type using its internal identity", async () => {
  adminApi.get.mockResolvedValue({ id: 987, name: "Annual Leave" });
  mount(
    <AdminEditorPage resource="leave-types" />,
    "/admin/leave-types/987/edit",
    "/admin/leave-types/:id/edit",
  );
  fireEvent.change(await screen.findByLabelText("Leave type name *"), {
    target: { value: "Vacation" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() =>
    expect(adminApi.save).toHaveBeenCalledWith("admin/leave-types", "987", {
      name: "Vacation",
    }),
  );
  expect(adminApi.get).toHaveBeenCalledWith("admin/leave-types", "987");
});
it("confirms unused leave type deletion and refreshes the page", async () => {
  adminApi.list.mockResolvedValue(page([{ id: 987, name: "Annual Leave" }]));
  mount(<AdminPage resource="leave-types" />);
  fireEvent.click(await screen.findByRole("button", { name: "Delete" }));
  expect(adminApi.remove).not.toHaveBeenCalled();
  adminApi.list.mockResolvedValue(page([]));
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  await waitFor(() =>
    expect(adminApi.remove).toHaveBeenCalledWith("admin/leave-types", 987),
  );
  expect(await screen.findByText("No leave types found.")).toBeInTheDocument();
});
it("preserves a referenced leave type row after a 409 error", async () => {
  adminApi.list.mockResolvedValue(page([{ id: 987, name: "Annual Leave" }]));
  adminApi.remove.mockRejectedValue({
    status: 409,
    message:
      "This leave type is already used by existing requests and cannot be deleted.",
  });
  mount(<AdminPage resource="leave-types" />);
  fireEvent.click(await screen.findByRole("button", { name: "Delete" }));
  fireEvent.click(screen.getByRole("button", { name: "Confirm" }));
  expect(
    await screen.findByText(
      "This leave type is already used by existing requests and cannot be deleted.",
    ),
  ).toBeInTheDocument();
  expect(
    screen.getByRole("cell", { name: "Annual Leave" }),
  ).toBeInTheDocument();
  expect(screen.queryByText("987")).not.toBeInTheDocument();
});
it("searches leave types on the server and hides management without its grant", async () => {
  mount(<AdminPage resource="leave-types" />);
  fireEvent.change(screen.getByLabelText("Search by name"), {
    target: { value: "Sick" },
  });
  fireEvent.click(screen.getByRole("button", { name: "Search" }));
  await waitFor(() =>
    expect(adminApi.list).toHaveBeenCalledWith("admin/leave-types", 0, "Sick"),
  );
});
it("does not grant leave type administration through unrelated permissions", () => {
  useAuth.mockReturnValue({
    user: { username: "dept-admin", authorities: ["10011"] },
  });
  mount(<AppLayout />);
  expect(
    screen.queryByRole("link", { name: "Leave Types" }),
  ).not.toBeInTheDocument();
});

it("creates employees without a status selector or status payload", async () => {
  mount(<AdminEditorPage resource="employees" />, "/admin/employees/new");
  await screen.findByLabelText("Name *");
  expect(screen.queryByLabelText("Employee status")).not.toBeInTheDocument();
  for (const [label, value] of [
    ["Name *", "Alice"],
    ["Email *", "alice@example.test"],
    ["Mobile *", "555"],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() => expect(adminApi.save).toHaveBeenCalled());
  expect(adminApi.save.mock.calls[0][2]).not.toHaveProperty("employeeStatusId");
});
it("edits employee profiles without exposing or resubmitting their status", async () => {
  adminApi.get.mockResolvedValue({
    ...employee,
    employeeStatusId: 77,
    employeeStatusName: "Active",
  });
  mount(
    <AdminEditorPage resource="employees" />,
    "/admin/employees/2/edit",
    "/admin/employees/:id/edit",
  );
  await screen.findByLabelText("Name *");
  expect(screen.queryByLabelText("Employee status")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  await waitFor(() => expect(adminApi.save).toHaveBeenCalled());
  expect(adminApi.save.mock.calls[0][2]).not.toHaveProperty("employeeStatusId");
  expect(adminApi.save.mock.calls[0][2]).not.toHaveProperty("initialPassword");
});
it("retains read-only status in employee details", async () => {
  adminApi.get.mockResolvedValue({ ...employee, employeeStatusName: "Active" });
  mount(
    <AdminEditorPage resource="employees" details />,
    "/admin/employees/2",
    "/admin/employees/:id",
  );
  expect(await screen.findByText("Active")).toBeInTheDocument();
  expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
});

it("shows a temporary password only in the mounted employee creation success view", async () => {
  mount(<AdminEditorPage resource="employees" />, "/admin/employees/new");
  await screen.findByLabelText("Name *");
  expect(screen.queryByLabelText("Initial password *")).not.toBeInTheDocument();
  for (const [label, value] of [
    ["Name *", "Alice"],
    ["Email *", "alice@example.test"],
    ["Mobile *", "555"],
  ])
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  fireEvent.click(screen.getByRole("button", { name: "Save" }));
  expect(
    await screen.findByText("Employee created successfully"),
  ).toBeInTheDocument();
  expect(screen.getByText("test-only-temporary-value")).toBeInTheDocument();
  const writeText = vi.fn().mockResolvedValue(undefined);
  const originalClipboard = Object.getOwnPropertyDescriptor(
    navigator,
    "clipboard",
  );
  Object.defineProperty(navigator, "clipboard", {
    configurable: true,
    value: { writeText },
  });
  fireEvent.click(screen.getByRole("button", { name: "Copy" }));
  await waitFor(() =>
    expect(writeText).toHaveBeenCalledWith("test-only-temporary-value"),
  );
  if (originalClipboard)
    Object.defineProperty(navigator, "clipboard", originalClipboard);
  else delete navigator.clipboard;
  expect(adminApi.save.mock.calls[0][2]).not.toHaveProperty("initialPassword");
  cleanup();
  mount(<AdminPage resource="employees" />);
  await screen.findByText("Alice");
  expect(
    screen.queryByText("test-only-temporary-value"),
  ).not.toBeInTheDocument();
});
it("does not retrieve a temporary password after remounting creation or editing", async () => {
  mount(<AdminEditorPage resource="employees" />, "/admin/employees/new");
  await screen.findByLabelText("Name *");
  expect(
    screen.queryByText("test-only-temporary-value"),
  ).not.toBeInTheDocument();
  expect(adminApi.get).not.toHaveBeenCalled();
  cleanup();
  mount(
    <AdminEditorPage resource="employees" />,
    "/admin/employees/2/edit",
    "/admin/employees/:id/edit",
  );
  await screen.findByLabelText("Name *");
  expect(screen.queryByLabelText("Initial password *")).not.toBeInTheDocument();
  expect(
    screen.queryByText("test-only-temporary-value"),
  ).not.toBeInTheDocument();
});
it("shows Change Password to ordinary authenticated employees", () => {
  useAuth.mockReturnValue({ user: { username: "employee", authorities: [] } });
  mount(<AppLayout />);
  expect(screen.getByRole("link", { name: "Change Password" })).toHaveAttribute(
    "href",
    "/change-password",
  );
  expect(
    screen.queryByRole("link", { name: "Employees" }),
  ).not.toBeInTheDocument();
});
