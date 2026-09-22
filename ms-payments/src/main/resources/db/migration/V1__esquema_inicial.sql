-- =============================================================================
--  tb_payments — el dinero
-- =============================================================================
--  Motor : MySQL 8.4 (InnoDB) · utf8mb4 · migraciones con Flyway
--
--  TRES REGLAS QUE EXPLICAN TODO EL ARCHIVO
--
--  1. UN PAGO NO SE EDITA, SE LE AGREGAN HECHOS.
--     El modelo anterior sobreescribia `status` en la fila del pago, asi que
--     al final solo se sabia como termino, nunca como llego ahi. Aca cada
--     transicion es una fila nueva en payment_events y nada se pisa. La
--     columna `status` del pago existe, pero es una proyeccion del ultimo
--     evento y se escribe en la misma transaccion que lo inserta.
--
--  2. LA PASARELA PUEDE AVISAR DOS VECES.
--     Los webhooks se reintentan: el mismo pago puede llegar repetido. El
--     UNIQUE (gateway, gateway_txn_id) es lo que hace que el segundo aviso no
--     abone de nuevo. El modelo anterior confiaba en un `if` de Java, y un
--     reintento con otro identificador interno duplicaba el abono.
--
--  3. LA UF CAMBIA TODOS LOS DIAS.
--     Una deuda en UF se paga en pesos al valor del dia. Ese valor se guarda
--     junto al pago, porque sin el nadie puede reconstruir despues por que
--     UF 38,5 fueron esos pesos y no otros.
--
--  SIN CLAVES FORANEAS HACIA tb_debt
--  debt_id e installment_id son referencias logicas a otra base, de otro
--  servicio. Lo que las valida es el servicio de deudas, no el motor.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  uf_values — el valor de la UF por dia.
--
--  Se guarda en vez de consultarse al vuelo por dos razones: un pago tiene que
--  poder reconstruirse anos despues, y si la fuente esta caida el cobro no
--  puede detenerse.
--
--  De donde se trae —Banco Central o CMF— es la decision I10, todavia abierta;
--  `source` esta para poder cambiar de fuente sin perder lo ya guardado.
-- -----------------------------------------------------------------------------
CREATE TABLE uf_values (
    day         DATE           NOT NULL,
    value       DECIMAL(12,2)  NOT NULL,
    source      VARCHAR(40)    NOT NULL,
    fetched_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_uf_value PRIMARY KEY (day),
    CONSTRAINT ck_uf_value CHECK (value > 0)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  payments — un intento de pago.
--
--  Se crea al iniciar el pago, no al confirmarlo: un pago que fracasa tambien
--  es informacion, y es la unica forma de saber cuanta gente llego hasta la
--  pasarela y no pudo.
--
--  amount va en la moneda de la deuda; amount_clp es lo que efectivamente se
--  cobro en pesos. En una deuda en pesos los dos son iguales, y en una en UF
--  amount_clp = amount x uf_value.
-- -----------------------------------------------------------------------------
CREATE TABLE payments (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    debt_id          BIGINT         NOT NULL,
    installment_id   BIGINT             NULL,
    debtor_rut       VARCHAR(12)    NOT NULL,
    creditor_rut     VARCHAR(12)    NOT NULL,
    amount           DECIMAL(18,2)  NOT NULL,
    currency         VARCHAR(3)     NOT NULL,
    amount_clp       BIGINT             NULL,
    uf_value         DECIMAL(12,2)      NULL,
    gateway          VARCHAR(20)    NOT NULL,
    gateway_txn_id   VARCHAR(80)        NULL,
    status           VARCHAR(12)    NOT NULL DEFAULT 'created',
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    paid_at          DATETIME(6)        NULL,

    CONSTRAINT pk_payment PRIMARY KEY (id),
    -- La idempotencia de verdad: el mismo pago de la misma pasarela entra una
    -- sola vez, aunque el webhook se reintente. Los NULL no chocan entre si,
    -- asi que un pago recien creado todavia sin id de pasarela cabe igual.
    CONSTRAINT uq_payment_gateway UNIQUE (gateway, gateway_txn_id),

    CONSTRAINT ck_payment_amount CHECK (amount > 0),
    CONSTRAINT ck_payment_currency CHECK (currency IN ('CLP', 'UF')),
    CONSTRAINT ck_payment_gateway CHECK (
        gateway IN ('mercadopago', 'khipu', 'webpay')
    ),
    CONSTRAINT ck_payment_status CHECK (
        status IN ('created', 'authorized', 'paid', 'failed', 'expired', 'refunded')
    ),
    --  Un pago en UF sin el valor de la UF no se puede explicar despues.
    CONSTRAINT ck_payment_uf CHECK (
        currency <> 'UF' OR status <> 'paid' OR (uf_value IS NOT NULL AND amount_clp IS NOT NULL)
    ),
    CONSTRAINT ck_payment_paid CHECK (
        (status = 'paid' AND paid_at IS NOT NULL) OR (status <> 'paid' AND paid_at IS NULL)
    ),

    INDEX ix_payment_debt (debt_id, status),
    INDEX ix_payment_deudor (debtor_rut, created_at),
    INDEX ix_payment_acreedor (creditor_rut, paid_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  payment_events — el libro. Solo se inserta.
--
--  signature_ok guarda si la firma HMAC del webhook estaba bien. Un aviso con
--  firma invalida NO se aplica, pero se guarda igual: alguien mandando avisos
--  falsos es algo que hay que poder ver.
-- -----------------------------------------------------------------------------
CREATE TABLE payment_events (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    payment_id       BIGINT       NOT NULL,
    type             VARCHAR(12)  NOT NULL,
    source           VARCHAR(10)  NOT NULL,
    signature_ok     BOOL             NULL,
    gateway_payload  JSON             NULL,
    occurred_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_payment_event PRIMARY KEY (id),
    CONSTRAINT fk_payment_event_payment FOREIGN KEY (payment_id)
        REFERENCES payments (id) ON DELETE RESTRICT,

    CONSTRAINT ck_payment_event_type CHECK (type IN (
        'created', 'authorized', 'paid', 'failed', 'expired', 'refunded'
    )),
    CONSTRAINT ck_payment_event_source CHECK (
        source IN ('portal', 'webhook', 'manual')
    ),

    INDEX ix_payment_event_payment (payment_id, occurred_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  debt_notifications — el aviso hacia el servicio de deudas.
--
--  Misma idea que la bandeja de salida de tb_debt: el aviso se guarda en la
--  misma transaccion que confirma el pago y se entrega despues, con
--  reintentos. Si ms-debt esta caido, el pago igual quedo registrado y el
--  aviso sale cuando vuelva. Avisar dentro de la transaccion dejaria pagos
--  cobrados que la deuda nunca supo.
-- -----------------------------------------------------------------------------
CREATE TABLE debt_notifications (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    payment_id       BIGINT        NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'pending',
    attempts         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delivered_at     DATETIME(6)       NULL,
    last_error       VARCHAR(300)      NULL,

    CONSTRAINT pk_debt_notification PRIMARY KEY (id),
    CONSTRAINT uq_debt_notification UNIQUE (payment_id),
    CONSTRAINT fk_debt_notification_payment FOREIGN KEY (payment_id)
        REFERENCES payments (id) ON DELETE CASCADE,

    CONSTRAINT ck_debt_notification_status CHECK (
        status IN ('pending', 'delivered', 'failed')
    ),

    INDEX ix_debt_notification_por_enviar (status, next_attempt_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  Lo recaudado, para el panel del acreedor.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_collection AS
SELECT
    creditor_rut,
    DATE_FORMAT(paid_at, '%Y-%m')          AS mes,
    COUNT(*)                                AS pagos,
    SUM(COALESCE(amount_clp, amount))       AS recaudado_clp
FROM payments
WHERE status = 'paid'
GROUP BY creditor_rut, DATE_FORMAT(paid_at, '%Y-%m');
