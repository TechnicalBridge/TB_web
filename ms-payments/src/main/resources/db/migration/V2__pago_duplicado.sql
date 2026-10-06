-- =============================================================================
--  V2 — el pago duplicado
-- =============================================================================
--  Dos pagos pueden cubrir las mismas cuotas: el deudor paga con Khipu y,
--  mientras Khipu verifica la transferencia, paga otra vez con otra pasarela.
--  El segundo no se abona, porque la cuota ya esta pagada: queda `duplicated`,
--  a la vista de la empresa, para devolverlo en la pasarela.
--
--  Para saber si dos pagos se pisan, cada pago guarda las cuotas que cubre,
--  separadas por coma, tal como las cobro ms-debt. Antes solo se guardaba la
--  cuota cuando era una sola (installment_id).
-- =============================================================================

ALTER TABLE payments
    ADD COLUMN installment_ids VARCHAR(2000) NULL AFTER installment_id;

ALTER TABLE payments DROP CHECK ck_payment_status;
ALTER TABLE payments ADD CONSTRAINT ck_payment_status CHECK (
    status IN ('created', 'authorized', 'paid', 'failed', 'expired', 'refunded', 'duplicated')
);

ALTER TABLE payment_events DROP CHECK ck_payment_event_type;
ALTER TABLE payment_events ADD CONSTRAINT ck_payment_event_type CHECK (type IN (
    'created', 'authorized', 'paid', 'failed', 'expired', 'refunded', 'duplicated'
));
