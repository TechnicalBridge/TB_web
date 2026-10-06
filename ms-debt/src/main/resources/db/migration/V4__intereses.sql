-- =============================================================================
--  V4 — Intereses
-- =============================================================================
--  Algunas deudas generan interes: las que el acreedor pacto asi. La tasa la
--  manda el acreedor en la cartera (tasa_interes_mensual) y es una sola, en
--  porcentaje mensual, para la mora y para el convenio. Sin tasa, sin
--  intereses: la deuda vale lo que mando el acreedor, como hasta ahora.
--
--  debts.interest_rate          La tasa mensual pactada. El interes de mora no
--                               se guarda: se calcula sobre el capital vencido,
--                               dia a dia, y sin capitalizar (Ley 18.010).
--  installments.interest_amount El interes del convenio que va dentro de la
--                               cuota (sistema frances). El capital de la cuota
--                               es amount - interest_amount, y la mora de una
--                               cuota vencida corre solo sobre ese capital.
--  repactations.interest_rate   La tasa con que se armo el convenio, y
--  repactations.principal       lo que se repacto: el capital mas la mora que
--                               se habia acumulado hasta ese dia.
-- =============================================================================

ALTER TABLE debts ADD COLUMN interest_rate DECIMAL(5,2) NULL AFTER original_amount;
ALTER TABLE debts ADD CONSTRAINT ck_debt_interest_rate CHECK (interest_rate IS NULL OR interest_rate > 0);

ALTER TABLE installments ADD COLUMN interest_amount DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER amount;
ALTER TABLE installments ADD CONSTRAINT ck_installment_interest
    CHECK (interest_amount >= 0 AND interest_amount <= amount);

ALTER TABLE repactations ADD COLUMN interest_rate DECIMAL(5,2) NULL AFTER monthly_amount;
ALTER TABLE repactations ADD COLUMN principal DECIMAL(18,2) NULL AFTER interest_rate;
ALTER TABLE repactations ADD CONSTRAINT ck_repactation_interest_rate
    CHECK (interest_rate IS NULL OR interest_rate > 0);
