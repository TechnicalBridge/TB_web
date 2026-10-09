-- =============================================================================
--  V5 — El descuento del pago
-- =============================================================================
--  Quien paga toda su deuda durante una campana con descuento paga menos mora
--  (contrato §7.1). ms-debt dice cuanto al abrir el cobro, y queda fijo como
--  el interes: si la pasarela confirma dias despues, no cambia. ms-debt se lo
--  informa al acreedor como intereses condonados.
--
--  interest_amount ya viene descontado: amount es el capital mas el interes.
--  NULL en un pago sin descuento.
-- =============================================================================

ALTER TABLE payments ADD COLUMN discount_amount DECIMAL(18,2) NULL AFTER interest_amount;
ALTER TABLE payments ADD CONSTRAINT ck_payment_discount CHECK (discount_amount IS NULL OR discount_amount > 0);
