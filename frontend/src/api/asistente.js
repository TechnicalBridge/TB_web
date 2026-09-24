import { pedir } from "./client";

/** Una pregunta al asistente, con la conversacion hasta ahora. Solo lee: no modifica ninguna deuda. */
export const preguntarAlAsistente = (mensaje, historia) =>
  pedir("/ai/chat", { method: "POST", body: { message: mensaje, messages: historia } }).then((data) => data.reply);
