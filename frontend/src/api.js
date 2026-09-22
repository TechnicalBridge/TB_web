import axios from "axios";

const TOKEN_KEY = "tb_session";

export function getSessionToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setSessionToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export const client = axios.create({
  baseURL: "/api",
  headers: { "Content-Type": "application/json" },
});

client.interceptors.request.use((config) => {
  const token = getSessionToken();
  if (token) {
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
  (err) => {
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
