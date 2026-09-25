import { embebidos, pedir } from "./client";

/** Las claves de API de la empresa: las activas y las revocadas, sin la clave. */
export const listarClaves = () => pedir("/claves").then((data) => embebidos(data, "claves"));

/** Emitir una clave. La respuesta la trae una sola vez. */
export const emitirClave = (nombre) => pedir("/claves", { method: "POST", body: { nombre } });

/** Revocar una clave: deja de servir en el acto. */
export const revocarClave = (id) => pedir(`/claves/${id}`, { method: "DELETE" });
