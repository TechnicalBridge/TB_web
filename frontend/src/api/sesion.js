import { pedir } from "./client";

/** Entrar con RUT y el codigo de acceso. */
export const entrarConCodigo = (rut, codigo) =>
  pedir("/auth/acceso", { method: "POST", body: { rut, codigo } });

/** El enlace de respaldo al correo. El personal de las empresas entra asi. */
export const pedirEnlace = (correo, rut) =>
  pedir("/auth/enlace", { method: "POST", body: { correo, rut: rut || null } });

/** Canjear el enlace del correo por una sesion. */
export const entrarConEnlace = (token) => pedir("/auth/verify", { method: "POST", body: { token } });

/** Quien es el dueno del JWT. */
export const yo = () => pedir("/me").then((data) => data.user);

/** Cerrar la sesion en el servidor: revoca la llave de renovacion. */
export const cerrarSesion = () => pedir("/auth/logout", { method: "POST" });
