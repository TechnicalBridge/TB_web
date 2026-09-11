import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { api, getSessionToken, setSessionToken } from "../api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    const token = getSessionToken();
    if (!token) {
      setReady(true);
      return;
    }
    api("/me")
      .then((data) => setUser(data.user))
      .catch(() => {
        setSessionToken(null);
        setUser(null);
      })
      .finally(() => setReady(true));
  }, []);

  const value = useMemo(() => {
    async function login(email, password) {
      setError("");
      const data = await api("/auth/login", { method: "POST", body: { email, password } });
      setSessionToken(data.token);
      setUser(data.user);
      return data.user;
    }
    async function register(name, email, password) {
      setError("");
      const data = await api("/auth/register", {
        method: "POST",
        body: { name, email, password },
      });
      setSessionToken(data.token);
      setUser(data.user);
      return data.user;
    }
    async function guest() {
      setError("");
      const data = await api("/auth/guest", { method: "POST" });
      setSessionToken(data.token);
      setUser(data.user);
      return data.user;
    }
    function logout() {
      setSessionToken(null);
      setUser(null);
    }
    return { user, setUser, ready, error, setError, login, register, guest, logout };
  }, [user, ready, error]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth fuera de AuthProvider");
  return ctx;
}
