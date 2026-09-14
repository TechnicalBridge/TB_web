import { create } from "zustand";
import { api, getSessionToken, setSessionToken } from "../api";

export const useAuth = create((set, get) => ({
  user: null,
  ready: false,
  error: "",

  setError: (error) => set({ error }),

  bootstrap: async () => {
    const token = getSessionToken();
    if (!token) {
      set({ ready: true, user: null });
      return;
    }
    try {
      const data = await api("/me");
      set({ user: data.user, ready: true, error: "" });
    } catch {
      setSessionToken(null);
      set({ user: null, ready: true });
    }
  },

  requestMagicLink: async (email, name) => {
    set({ error: "" });
    return api("/auth/magic-link", { method: "POST", body: { email, name } });
  },

  verifyMagic: async (token, name) => {
    set({ error: "" });
    const data = await api("/auth/verify", { method: "POST", body: { token, name } });
    setSessionToken(data.token);
    set({ user: data.user });
    return data.user;
  },

  logout: () => {
    setSessionToken(null);
    set({ user: null, error: "" });
  },

  homeFor: (user = get().user) => {
    if (!user) return "/";
    return user.role === "CREDITOR" ? "/databridge" : "/app";
  },
}));
