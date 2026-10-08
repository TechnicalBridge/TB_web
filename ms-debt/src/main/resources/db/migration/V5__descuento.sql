-- =============================================================================
--  V5 — Descuento por pronto pago
-- =============================================================================
--  Una campana puede condonar parte de los intereses de mora a quien paga toda
--  la deuda de una vez (contrato §7.1). Lo autoriza el acreedor con un maximo
--  en el mandato, y la campana lo ofrece por tramo de mora sin pasarlo.
--
--  mandates.max_mora_discount  El % de los intereses de mora que el acreedor
--                              autoriza condonar. NULL es 0: sin descuento.
--  campaigns.mora_discount     El % que ofrece la campana por tramo, como
--                              {"1-30": 0, "31-90": 50, "91-120": 100}. NULL:
--                              la campana no ofrece descuento.
--
--  El maximo se aplica al cobrar, no al guardar la campana: si el acreedor lo
--  baja, sus campanas quedan recortadas desde ese momento.
-- =============================================================================

ALTER TABLE mandates ADD COLUMN max_mora_discount DECIMAL(5,2) NULL AFTER max_overdue_days;
ALTER TABLE mandates ADD CONSTRAINT ck_mandate_mora_discount
    CHECK (max_mora_discount IS NULL OR (max_mora_discount >= 0 AND max_mora_discount <= 100));

ALTER TABLE campaigns ADD COLUMN mora_discount JSON NULL AFTER cadence_days;
