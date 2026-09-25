/**
 * Como se escriben montos, RUT, fechas y estados en Chile.
 */

const PESOS = new Intl.NumberFormat("es-CL", { style: "currency", currency: "CLP", maximumFractionDigits: 0 });
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

/** Una fecha (o fecha y hora) como la lee una persona: 24 sept 2026. */
export function fecha(iso) {
  if (!iso) return "";
  const d = new Date(String(iso).length === 10 ? `${iso}T12:00:00` : iso);
  return d.toLocaleDateString("es-CL", { day: "2-digit", month: "short", year: "numeric" });
}

/** Hoy en Chile, como 2026-09-24. */
export const hoyEnChile = () => new Date().toLocaleDateString("sv-SE", { timeZone: "America/Santiago" });

/** Como se le dice a una persona el estado de su deuda. */
export const ESTADO_DEUDA = {
  open: "Pendiente",
  repacted: "En convenio",
  paid: "Pago conciliado",
  withdrawn: "Retirada",
  disputed: "En revisión",
};
