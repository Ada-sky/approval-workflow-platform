import { createContext, useContext, useEffect, useState } from "react";
import { api, onUnauthorized } from "../api/client";
const AuthContext = createContext(null);
/**
 * Loads the current server-session identity and shares it with routes and pages.
 * A missing session is normal signed-out state; other load failures remain
 * visible and retryable rather than being mistaken for a login requirement.
 */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(null),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(null),
    [successMessage, setSuccessMessage] = useState("");
  async function load() {
    setLoading(true);
    setError(null);
    try {
      setUser(await api.me());
    } catch (e) {
      setUser(null);
      if (e.status !== 401) setError(e);
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => {
    const unsubscribe = onUnauthorized(() => setUser(null));
    load();
    return unsubscribe;
  }, []);
  async function login(name, password) {
    const current = await api.login(name, password);
    setSuccessMessage("");
    setUser(current);
    setError(null);
  }
  // Clear the displayed identity only after the backend accepts session logout.
  async function logout() {
    await api.logout();
    setSuccessMessage("");
    setUser(null);
  }
  // Successful password change invalidates the session and requires a new login.
  async function changePassword(body) {
    await api.changePassword(body);
    setSuccessMessage("Password changed successfully. Please sign in again.");
    setUser(null);
  }
  return (
    <AuthContext.Provider
      value={{
        user,
        loading,
        error,
        login,
        logout,
        changePassword,
        successMessage,
        retry: load,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}
export const useAuth = () => useContext(AuthContext);
