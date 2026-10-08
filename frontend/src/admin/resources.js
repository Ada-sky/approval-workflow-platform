// Navigation hints only. Every request is authorized by the backend.
export const hasPermission = (user, codes) =>
  !codes || codes.some((code) => user?.authorities?.includes(code));
const field = (key, label, type = "text", required = false, extra = {}) => ({
  key,
  label,
  type,
  required,
  ...extra,
});
/**
 * Describes shared administration fields, display columns and numeric grants.
 * Hidden navigation does not remove a route or replace backend authorization.
 */
export const resources = {
  employees: {
    title: "Employees",
    singular: "Employee",
    endpoint: "employees",
    permissions: ["10021", "100214", "100211", "100212", "100213", "10022"],
    read: ["10021", "100214", "10022"],
    create: ["100211"],
    update: ["100212"],
    remove: ["100213"],
    columns: [
      "name",
      "departmentName",
      "jobTitleName",
      "employeeStatusName",
      "email",
    ],
    columnLabels: { name: "Employee" },
    fields: [
      field("name", "Name", "text", true, { max: 100 }),
      field("email", "Email", "email", true, { max: 254 }),
      field("mobile", "Mobile", "text", true, { max: 30 }),
      field("gender", "Gender", "text", false, { max: 20 }),
      field("departmentId", "Department", "select"),
      field("jobTitleId", "Job title", "select"),
      field("birthday", "Birthday", "date"),
      field("employmentDate", "Employment date", "date"),
      field("location", "Location", "text", false, { max: 200 }),
    ],
  },
  departments: {
    title: "Departments",
    singular: "Department",
    endpoint: "departments",
    permissions: ["10011"],
    columns: ["name", "number", "managerName", "parentDepartmentName"],
    columnLabels: { name: "Department", number: "Code" },
    fields: [
      field("number", "Department number", "text", true, { max: 50 }),
      field("name", "Name", "text", true, { max: 100 }),
      field("parentId", "Parent department", "select"),
      field("level", "Level", "number", true),
      field("managerEmployeeId", "Department manager", "select"),
    ],
  },
  "job-titles": {
    title: "Job Titles",
    singular: "Job title",
    endpoint: "job-titles",
    permissions: ["10012"],
    columns: ["number", "displayName"],
    columnLabels: { number: "Code", displayName: "Job Title" },
    fields: [
      field("number", "Job title code", "text", true, { max: 50 }),
      field("name", "Name", "text", true, { max: 100 }),
      field("parentId", "Parent job title", "select"),
      field("level", "Level", "number", true),
    ],
  },
  "employee-statuses": {
    title: "Employee Status",
    navigation: false,
    singular: "Employee status",
    endpoint: "employee-statuses",
    permissions: ["10013"],
    columns: ["name"],
    fields: [field("name", "Name", "text", true, { max: 100 })],
  },
  "leave-types": {
    title: "Leave Types",
    singular: "Leave type",
    endpoint: "admin/leave-types",
    createLabel: "Add leave type",
    columnLabels: { name: "Leave Type" },
    permissions: ["10023"],
    columns: ["name"],
    fields: [field("name", "Leave type name", "text", true, { max: 255 })],
  },
  roles: {
    title: "Roles & Permissions",
    singular: "Role",
    endpoint: "roles",
    permissions: ["10022"],
    columns: ["name"],
    fields: [field("name", "Name", "text", true, { max: 100 })],
  },
  menus: {
    title: "Menus",
    navigation: false,
    singular: "Menu",
    endpoint: "menus",
    permissions: ["10023"],
    columns: ["name", "code", "url", "parentId", "grade"],
    fields: [
      field("name", "Name", "text", true, { max: 100 }),
      field("permissionCode", "Permission code", "text", true, { max: 100 }),
      field("url", "URL", "text", false, { max: 500 }),
      field("parentId", "Parent menu", "select"),
      field("grade", "Grade", "number", true, { upper: 2 }),
    ],
  },
};
export const adminEntries = (user) => {
  const allowed = Object.entries(resources).filter(
    ([, config]) =>
      config.permissions && hasPermission(user, config.permissions),
  );
  return allowed.filter(([, config]) => config.navigation !== false);
};
export const allowedAction = (user, config, action) =>
  !config.readonly && hasPermission(user, config[action] || config.permissions);
export const labels = {
  number: "Number",
  name: "Name",
  email: "Email",
  mobile: "Mobile",
  departmentName: "Department",
  jobTitleName: "Job Title",
  employeeStatusName: "Status",
  managerName: "Manager",
  parentDepartmentName: "Parent Department",
  parentId: "Parent",
  level: "Level",
  managerEmployeeId: "Manager",
  code: "Permission code",
  url: "URL",
  grade: "Grade",
};
/** Provides early form feedback; backend validation remains authoritative. */
export function validateForm(config, values, creating) {
  const errors = {};
  for (const f of config.fields) {
    const value = String(values[f.key] ?? "").trim();
    if (f.required && !value) errors[f.key] = `${f.label} is required.`;
    else if (f.max && value.length > f.max)
      errors[f.key] = `Use at most ${f.max} characters.`;
    else if (
      f.type === "email" &&
      value &&
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)
    )
      errors[f.key] = "Enter a valid email.";
    else if (
      f.type === "number" &&
      value &&
      (!/^\d+$/.test(value) ||
        Number(value) > (f.upper ?? Number.MAX_SAFE_INTEGER))
    )
      errors[f.key] =
        `Enter a whole number between 0 and ${f.upper ?? "the supported maximum"}.`;
  }
  return errors;
}
/**
 * Serializes only configured fields, excluding display enrichment and unrelated
 * entity properties. Empty parent selection uses the existing root sentinel.
 */
export function formBody(config, values, creating) {
  const body = {};
  for (const f of config.fields) {
    const value = values[f.key] ?? "";
    body[f.key] = ["number", "select"].includes(f.type)
      ? value === ""
        ? f.key === "parentId"
          ? 0
          : null
        : Number(value)
      : value || null;
  }
  return body;
}

export const adminGroups = (user) => {
  const entries = adminEntries(user);
  return [
    ["People", ["employees", "departments", "job-titles"]],
    ["Access Control", ["roles"]],
    ["Settings", ["leave-types"]],
  ]
    .map(([label, keys]) => ({
      label,
      entries: entries.filter(([key]) => keys.includes(key)),
    }))
    .filter((group) => group.entries.length);
};
export const permissionLabel = (permission) =>
  ({
    10011: "Manage Departments",
    10012: "Manage Job Titles",
    10013: "Manage Employee Status",
    10021: "View Employees",
    100214: "View Employee Details",
    100211: "Create Employees",
    100212: "Update Employees",
    100213: "Delete Employees",
    10022: "Manage Roles",
    10023: "Manage Permission Resources",
  })[permission.code] ||
  (permission.url?.startsWith("/workBench")
    ? "Access Approvals"
    : permission.name);
