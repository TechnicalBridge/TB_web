import { create } from "zustand";
import { api, getSessionToken, setSessionToken } from "../api";

/**
 * La sesion.
 *
 * El deudor entra con su RUT y el codigo que le llego por correo o WhatsApp:
 * sin cuenta, sin contrasena y sin hacer clic en ningun enlace. El enlace al
 * correo queda como respaldo, y es tambien como entra el personal de las
 * empresas.
 */
export const useAuth = create((set, get) => ({
  user: null,
  ready: false,

  bootstrap: async () => {
    if (!getSessionToken()) {
      set({ ready: true, user: null });
      return;
    }
    try {
      const data = await api("/me");
      set({ user: data.user, ready: true });
    } catch {
      setSessionToken(null);
      set({ user: null, ready: true });
    }
  },

  entrarConCodigo: async (rut, codigo) => {
    const data = await api("/auth/acceso", { method: "POST", body: { rut, codigo } });
    return get().abrirSesion(data);
  },

  pedirEnlace: (correo, rut) =>
    api("/auth/enlace", { method: "POST", body: { correo, rut: rut || null } }),

  entrarConEnlace: async (token) => {
    const data = await api("/auth/verify", { method: "POST", body: { token } });
    return get().abrirSesion(data);
  },

  // La respuesta del login trae el usuario resumido; /me lo trae completo.
  abrirSesion: async (data) => {
    setSessionToken(data.token);
    const me = await api("/me");
    set({ user: me.user });
    return me.user;
  },

  logout: () => {
    setSessionToken(null);
    set({ user: null });
  },

  homeFor: (user = get().user) => {
    if (!user) return "/";
    return user.role === "CREDITOR" ? "/databridge" : "/app";
  },
}));
