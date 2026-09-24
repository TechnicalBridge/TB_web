import { create } from "zustand";
import { api, onSesionPerdida, renovarSesion, setSessionToken } from "../api";

/**
 * La sesion.
 *
 * El deudor entra con su RUT y el codigo que le llego por correo o WhatsApp:
 * sin cuenta, sin contrasena y sin hacer clic en ningun enlace. El enlace al
 * correo queda como respaldo, y es tambien como entra el personal de las
 * empresas.
 *
 * El JWT vive en memoria (ver api.js), asi que una recarga de la pagina lo
 * pierde. Lo que sobrevive es la cookie de renovacion: al arrancar, se le pide
 * un JWT nuevo, y si la cookie ya no sirve, no hay sesion.
 */
export const useAuth = create((set, get) => {
  //  Si una renovacion falla en medio de algo, la sesion se termino: se
  //  vacia el usuario y las rutas protegidas mandan al inicio.
  onSesionPerdida(() => set({ user: null }));

  return {
    user: null,
    ready: false,

    /**
     * Recupera la sesion al cargar la pagina.
     *
     * Si no hay cookie, o la que hay ya no sirve, solo marca que termino: no
     * borra nada. Antes vaciaba el token y el usuario, y si alguien habia
     * entrado mientras tanto (la pagina /magic entra apenas carga), su sesion
     * recien abierta se perdia.
     */
    bootstrap: async () => {
      try {
        await renovarSesion();
        const data = await api("/me");
        set({ user: data.user });
      } catch {
        /* no habia sesion que recuperar */
      } finally {
        set({ ready: true });
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

    /**
     * Cerrar sesion de verdad: el servidor revoca la llave de renovacion.
     * Antes solo se borraba el token del navegador, y quien lo hubiera copiado
     * seguia adentro una semana.
     *
     * La pantalla sale al instante; si el servidor no alcanza a responder, la
     * llave vence sola, pero se avisa en la consola porque no es lo esperado.
     */
    logout: async () => {
      setSessionToken(null);
      set({ user: null });
      try {
        await api("/auth/logout", { method: "POST" });
      } catch (e) {
        console.warn("No se pudo cerrar la sesion en el servidor:", e.message);
      }
    },

    homeFor: (user = get().user) => {
      if (!user) return "/";
      return user.role === "CREDITOR" ? "/databridge" : "/app";
    },
  };
});
