-- =============================================================================
--  tb_auth · V3 — el pedido de enlace queda en la bitacora
-- =============================================================================
--
--  El enlace de acceso va solo al correo que registro el acreedor, o a una
--  cuenta de personal habilitada (#59). A quien lo pide se le responde siempre
--  lo mismo, salga o no el correo, para no revelar quien es deudor. Pero la
--  bitacora si lo anota: 'sent' si salio el enlace, 'refused' si no.
-- =============================================================================

ALTER TABLE access_log DROP CHECK ck_access_log_outcome;
ALTER TABLE access_log ADD CONSTRAINT ck_access_log_outcome CHECK (
    outcome IN ('granted', 'expired', 'invalid', 'exhausted', 'sent', 'refused')
);
