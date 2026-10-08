import { embebidos, pedir } from "./client";

/** Las campañas que gestiona la empresa, con su avance. */
export const listarCampanas = () => pedir("/debts/campanas").then((data) => embebidos(data, "campanas"));

/** Crear o cambiar una campaña: los mismos campos del contrato. Sin id_externo, DataBridge le pone uno. */
export const guardarCampana = (campana) => pedir("/debts/campanas", { method: "POST", body: campana });

/** Pausar (pausada), reanudar (en_curso) o terminar (terminada). */
export const cambiarEstadoCampana = (idExterno, estado) =>
  pedir(`/debts/campanas/${encodeURIComponent(idExterno)}/estado`, { method: "POST", body: { estado } });
