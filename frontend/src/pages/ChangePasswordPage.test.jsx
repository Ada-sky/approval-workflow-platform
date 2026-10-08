import { afterEach, beforeEach, it, expect, vi } from "vitest";
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from "@testing-library/react";
import { MemoryRouter, Routes, Route, useLocation } from "react-router";
import { useAuth } from "../context/AuthContext";
import ChangePasswordPage from "./ChangePasswordPage";
vi.mock("../context/AuthContext", () => ({ useAuth: vi.fn() }));
const changePassword = vi.fn();
function Result() {
  const location = useLocation();
  return <p>{location.state?.message}</p>;
}
function mount() {
  render(
    <MemoryRouter initialEntries={["/change-password"]}>
      <Routes>
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route path="/sign-in" element={<Result />} />
      </Routes>
    </MemoryRouter>,
  );
}
function fill(newPassword = "new-safe-passphrase", confirmation = newPassword) {
  fireEvent.change(screen.getByLabelText("Current password *"), {
    target: { value: "old-safe-passphrase" },
  });
  fireEvent.change(screen.getByLabelText("New password *"), {
    target: { value: newPassword },
  });
  fireEvent.change(screen.getByLabelText("Confirm new password *"), {
    target: { value: confirmation },
  });
}
beforeEach(() => {
  vi.resetAllMocks();
  useAuth.mockReturnValue({ changePassword });
});
afterEach(cleanup);
it("renders own-password fields and validates confirmation before sending", () => {
  mount();
  fill("new-safe-passphrase", "mismatch");
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
  expect(
    screen.getByText("Password confirmation must match."),
  ).toBeInTheDocument();
  expect(changePassword).not.toHaveBeenCalled();
});
it("validates policy including BCrypt UTF-8 byte limit", () => {
  mount();
  fill("é".repeat(40));
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
  expect(
    screen.getByText("Use 12–72 characters, at most 72 UTF-8 bytes."),
  ).toBeInTheDocument();
  expect(changePassword).not.toHaveBeenCalled();
});
it("changes only own password and redirects to sign-in with a success message", async () => {
  changePassword.mockResolvedValue(undefined);
  mount();
  fill();
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
  await waitFor(() =>
    expect(changePassword).toHaveBeenCalledWith({
      currentPassword: "old-safe-passphrase",
      newPassword: "new-safe-passphrase",
      confirmation: "new-safe-passphrase",
    }),
  );
  expect(
    await screen.findByText(
      "Password changed successfully. Please sign in again.",
    ),
  ).toBeInTheDocument();
});
it("shows safe backend errors and clears password input state", async () => {
  changePassword.mockRejectedValue({
    status: 409,
    message: "Operation conflicts with existing data or business rules",
  });
  mount();
  fill();
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
  expect(
    await screen.findByText(
      "Operation conflicts with existing data or business rules",
    ),
  ).toBeInTheDocument();
  for (const label of [
    "Current password *",
    "New password *",
    "Confirm new password *",
  ])
    expect(screen.getByLabelText(label)).toHaveValue("");
});
it("rejects reusing the current password", () => {
  mount();
  fill("old-safe-passphrase");
  fireEvent.click(screen.getByRole("button", { name: "Change password" }));
  expect(
    screen.getByText("Choose a password different from your current password."),
  ).toBeInTheDocument();
  expect(changePassword).not.toHaveBeenCalled();
});
