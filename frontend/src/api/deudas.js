import { client, embebidos, pedir } from "./client";

/** Las deudas de quien tiene la sesion: al deudor, las suyas; a la empresa, su cartera. */
export const listarDeudas = () => pedir("/debts").then((data) => embebidos(data, "debts"));

/** Una deuda con sus cargos, cuotas e historia. */
export const obtenerDeuda = (id) => pedir(`/debts/${id}`);

/** El plan que resultaria con ese plazo. No compromete nada. */
export const simularPlan = (id, meses) =>
  pedir(`/debts/${id}/simulate`, { params: { months: meses } }).then((data) => data.plan);

/** Aceptar el plan: las cuotas pendientes se reemplazan por las del plan. */
export const repactar = (id, meses) => pedir(`/debts/${id}/repact`, { method: "POST", body: { months: meses } });

/** La empresa le hace llegar al deudor su codigo de acceso, al correo. */
export const enviarCodigo = (id) => pedir(`/debts/${id}/codigo`, { method: "POST" });

/** Por cuenta de quien puede cargar cartera la empresa de la sesion. */
export const opcionesDeCarga = () => pedir("/debts/cartera/opciones");

/** Cargar la cartera en el CSV del contrato. */
export const cargarCartera = (formulario) => pedir("/debts/cartera", { method: "POST", body: formulario });

/** El certificado de deuda pagada, descargado como archivo. */
export async function descargarCertificado(id) {
  const res = await client.get(`/debts/${id}/certificate`, { responseType: "blob" });
  const url = URL.createObjectURL(res.data);
  const a = document.createElement("a");
  a.href = url;
  a.download = "certificado-deuda-pagada.pdf";
  a.click();
  URL.revokeObjectURL(url);
}
