import { pedir, publico } from "./client";

/**
 * Abrir el cobro. No lleva monto: ms-payments se lo pregunta a ms-debt. Si lo
 * mandara el navegador, bastaria editar la peticion para pagar un peso.
 */
export const abrirCobro = (debtId, installmentId, pasarela) =>
  pedir("/payments/checkout", { method: "POST", body: { debtId, installmentId, gateway: pasarela } });

export const obtenerPago = (id) => pedir(`/payments/${id}`);

/** La pasarela simulada no tiene sesion: la protege la firma del enlace. */
export const pagoPublico = (id, sig) => publico.get(`/payments/public/${id}`, { params: { sig } }).then((r) => r.data);

export const confirmarPagoPublico = (id, sig) =>
  publico.post(`/payments/public/${id}/confirm`, null, { params: { sig } }).then((r) => r.data);
