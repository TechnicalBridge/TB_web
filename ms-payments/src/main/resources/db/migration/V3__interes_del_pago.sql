-- =============================================================================
--  V3 — El interes del pago
-- =============================================================================
--  Una deuda puede generar intereses: los que pacto el acreedor. Al abrir el
--  cobro, ms-debt dice cuanto de amount es mora, y queda aca para avisarle a
--  ms-debt, que se lo informa al acreedor aparte del capital. El interes se
--  fija al abrir el cobro, como los pesos de una deuda en UF.
--
--  NULL en un pago de antes, o de una deuda sin intereses.
-- =============================================================================

ALTER TABLE payments ADD COLUMN interest_amount DECIMAL(18,2) NULL AFTER amount;
ALTER TABLE payments ADD CONSTRAINT ck_payment_interest
    CHECK (interest_amount IS NULL OR (interest_amount >= 0 AND interest_amount <= amount));
