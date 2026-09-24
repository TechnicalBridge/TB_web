import axios from "axios";

/**
 * La sesion vive en dos piezas.
 *
 * El JWT, que dura quince minutos, se guarda EN MEMORIA: en una variable de
 * este modulo, que muere al cerrar la pestana. Antes vivia en localStorage,
 * donde cualquier script de la pagina lo podia leer y llevarselo, y duraba una
 * semana.
 *
 * La llave de renovacion la guarda el navegador en una cookie que este codigo
 * no puede leer: solo sabe pedir "dame el JWT siguiente".
 */
let token = null;

//  Las versiones anteriores dejaban aca un JWT de siete dias. Se borra al
//  cargar, para que no quede uno viejo y vigente en ningun navegador.
try {
  localStorage.removeItem("tb_session");
} catch {
  /* sin acceso al almacenamiento: no hay nada que borrar */
}

export function getSessionToken() {
  return token;
}

export function setSessionToken(nuevo) {
  token = nuevo || null;
}

let alPerderLaSesion = () => {};

/** Lo que hay que hacer cuando la sesion ya no se puede renovar. */
export function onSesionPerdida(fn) {
  alPerderLaSesion = fn;
}

let renovando = null;

/**
 * Pide un JWT nuevo con la cookie.
 *
 * Si varias peticiones vencen a la vez, todas esperan la MISMA renovacion: una
 * sola llamada, no una por peticion. Y si responde 409 —otra pestana la acaba
 * de renovar— se reintenta una vez: para entonces el navegador ya tiene la
 * cookie nueva.
 */
export function renovarSesion() {
  if (!renovando) {
    const pedir = () => axios.post("/api/auth/refresh");
    renovando = pedir()
      .catch((err) => {
        if (err.response?.status !== 409) throw err;
        return new Promise((listo) => setTimeout(listo, 300)).then(pedir);
      })
      .then((res) => {
        setSessionToken(res.data.token);
        return res.data;
      })
      .finally(() => {
        renovando = null;
      });
  }
  return renovando;
}

export const client = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

/** Las rutas de /auth consiguen la sesion: no la llevan ni la renuevan. */
const esDeAutenticacion = (config) => String(config?.url || "").startsWith("/auth/");

client.interceptors.request.use((config) => {
  if (token && !esDeAutenticacion(config)) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  // Un archivo va como multipart: el navegador pone el Content-Type con su
  // separador, y el JSON por defecto lo romperia.
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    delete config.headers["Content-Type"];
  }
  return config;
});

/**
 * Los servicios responden los errores de dos formas: { error: "texto" } y,
 * en el borde de integracion, { error: { codigo, mensaje } }. La pantalla
 * solo necesita el texto.
 */
client.interceptors.response.use(
  (res) => res,
  async (err) => {
    //  Un 401 en medio de algo casi siempre es el JWT que vencio: se renueva
    //  y se repite la peticion UNA vez. Si renovar tampoco funciona, la sesion
    //  se termino de verdad y la pantalla vuelve al inicio.
    const original = err.config;
    if (err.response?.status === 401 && original && !original._reintento && !esDeAutenticacion(original)) {
      original._reintento = true;
      try {
        await renovarSesion();
        return client.request(original);
      } catch {
        setSessionToken(null);
        alPerderLaSesion();
      }
    }

    const data = err.response?.data;
    const mensaje =
      data?.error?.mensaje || (typeof data?.error === "string" ? data.error : null) ||
      data?.message || err.message || "Error de red";
    const error = new Error(mensaje);
    error.status = err.response?.status;
    return Promise.reject(error);
  }
);

export async function api(path, { method = "GET", body, headers } = {}) {
  const res = await client.request({ url: path, method, data: body, headers });
  return res.data;
}

const PESOS = new Intl.NumberFormat("es-CL", {
  style: "currency", currency: "CLP", maximumFractionDigits: 0,
});
const UF = new Intl.NumberFormat("es-CL", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/**
 * Un monto en su moneda. Las deudas de arriendo pueden estar en UF, y mostrar
 * UF 38,50 como "$39" era decirle al deudor que debia otra cosa.
 */
export function dinero(valor, moneda = "CLP") {
  const n = Number(valor || 0);
  return moneda === "UF" ? `UF ${UF.format(n)}` : PESOS.format(n);
}

/** 16482337-7 -> 16.482.337-7, como se escribe en Chile. */
export function rutLegible(rut) {
  const limpio = String(rut || "").replace(/[^0-9kK]/g, "").toUpperCase();
  if (limpio.length < 2) return limpio;
  const cuerpo = limpio.slice(0, -1).replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${cuerpo}-${limpio.slice(-1)}`;
}

export function fecha(iso) {
  if (!iso) return "";
  const d = new Date(String(iso).length === 10 ? `${iso}T12:00:00` : iso);
  return d.toLocaleDateString("es-CL", { day: "2-digit", month: "short", year: "numeric" });
}

/** Como se le dice a una persona el estado de su deuda. */
export const ESTADO_DEUDA = {
  open: { texto: "Pendiente", clase: "badge-warn" },
  repacted: { texto: "En convenio", clase: "badge-wait" },
  paid: { texto: "Pagada", clase: "badge-ok" },
  withdrawn: { texto: "Retirada por el acreedor", clase: "badge-muted" },
  disputed: { texto: "En revision", clase: "badge-muted" },
};

export const ESTADO_CUOTA = {
  pending: { texto: "Pendiente", clase: "badge-warn" },
  paid: { texto: "Pagada", clase: "badge-ok" },
  anulada: { texto: "Reemplazada", clase: "badge-muted" },
};

export async function downloadCertificate(debtId) {
  const res = await client.get(`/debts/${debtId}/certificate`, { responseType: "blob" });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = "certificado-deuda-pagada.pdf";
  a.click();
  URL.revokeObjectURL(url);
}
