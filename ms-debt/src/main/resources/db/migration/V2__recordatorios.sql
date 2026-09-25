-- =============================================================================
--  V2 — Recordatorios de cuota
-- =============================================================================
--  Unos dias antes de que venza una cuota, al deudor le llega un correo con un
--  codigo de acceso nuevo. Sin montos y sin enlaces, igual que el primero: un
--  correo que llega a quien no es no debe decir cuanto debe alguien ni a quien.
--
--  installments.reminded_at  Cuando se le aviso. El aviso sale una sola vez
--                            por cuota, aunque la tarea corra de nuevo o el
--                            servicio se reinicie.
--  debtors.reminders         Si el deudor quiere esos avisos. Parte encendido,
--                            y el deudor lo apaga desde "Mis datos".
-- =============================================================================

ALTER TABLE installments ADD COLUMN reminded_at DATETIME(6) NULL AFTER paid_at;

ALTER TABLE debtors ADD COLUMN reminders BOOL NOT NULL DEFAULT TRUE AFTER phone;
