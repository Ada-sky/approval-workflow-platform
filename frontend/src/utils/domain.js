export const statuses = {
  SUBMITTED: ["Pending", "warning"],
  IN_APPROVAL: ["In Review", "info"],
  REJECTED: ["Rejected", "danger"],
  COMPLETED: ["Approved", "success"],
  WITHDRAWN: ["Withdrawn", "secondary"],
};
export const stages = {
  DEPARTMENT_MANAGER: "Department Manager",
  GENERAL_MANAGER: "General Manager",
  HR: "HR",
};
export const archivable = (status) =>
  ["REJECTED", "COMPLETED", "WITHDRAWN"].includes(status);
export function date(value) {
  if (!value) return "Not available";
  return value.replace("T", " ").slice(0, 16);
}
/**
 * Formats the supplied calendar date without shifting it through browser-local
 * time zones. This is presentation only; stored leave dates remain unchanged.
 */
export function leaveDate(value) {
  const parts =
    typeof value === "string" &&
    /^(\d{4})-(\d{2})-(\d{2})(?:T| |$)/.exec(value);
  if (!parts) return "Not available";
  const [, year, month, day] = parts;
  const calendarDate = new Date(
    Date.UTC(Number(year), Number(month) - 1, Number(day)),
  );
  if (
    calendarDate.getUTCFullYear() !== Number(year) ||
    calendarDate.getUTCMonth() !== Number(month) - 1 ||
    calendarDate.getUTCDate() !== Number(day)
  )
    return "Not available";
  return new Intl.DateTimeFormat("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  }).format(calendarDate);
}
export function submittedDate(value) {
  const calendarDate = leaveDate(value);
  const time = typeof value === "string" && /[T ](\d{2}:\d{2})/.exec(value);
  return calendarDate === "Not available" || !time
    ? "Not recorded"
    : `${calendarDate}, ${time[1]}`;
}
export function validateLeave(values) {
  const errors = {};
  if (!values.title.trim()) errors.title = "Enter a title.";
  if (!values.leaveTypeId) errors.leaveTypeId = "Select a leave type.";
  if (!values.reason.trim()) errors.reason = "Enter a reason.";
  if (!values.startDate) errors.startDate = "Select a start date.";
  if (!values.endDate) errors.endDate = "Select an end date.";
  else if (values.startDate && values.endDate < values.startDate)
    errors.endDate = "End date must be on or after start date.";
  return errors;
}
