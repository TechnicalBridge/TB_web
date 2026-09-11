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
  if (typeof FormData !== "undefined" && config.data instanceof FormData) {
    delete config.headers["Content-Type"];
  }
  return config;
});

client.interceptors.response.use(
  (res) => res,
  (err) => {
    const data = err.response?.data;
    const error = new Error(data?.error || data?.detail || err.message || "Error de red");
    error.status = err.response?.status;
    return Promise.reject(error);
  }
);

export async function api(path, { method = "GET", body, headers } = {}) {
  const res = await client.request({
    url: path,
    method,
    data: body,
    headers,
  });
  return res.data;
}

export function clp(value) {
  const n = Number(value || 0);
  return new Intl.NumberFormat("es-CL", {
    style: "currency",
    currency: "CLP",
    maximumFractionDigits: 0,
  }).format(n);
}

export async function downloadCertificate(debtId) {
  const res = await client.get(`/debts/${debtId}/certificate`, { responseType: "blob" });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = "certificado-deuda-cero.pdf";
  a.click();
  URL.revokeObjectURL(url);
}
