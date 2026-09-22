-- =============================================================================
--  tb_debt — deudas, carteras y el borde de integracion de DataBridge
-- =============================================================================
--  Motor   : MySQL 8.4 (InnoDB) · utf8mb4
--  Migra   : Flyway. Este archivo es la linea base y NO se edita nunca mas:
--            todo cambio posterior entra como V2, V3, ...
--
--  POR QUE ESTE ESQUEMA REEMPLAZA AL ANTERIOR
--  El modelo viejo guardaba al deudor y al acreedor como texto suelto dentro
--  de la tabla de deudas (debtorEmail, creditorName). Eso tenia tres
--  consecuencias que no se podian arreglar sin cambiar el esquema:
--
--    1. No habia por quien filtrar. Cualquier acreedor veia las deudas de
--       todos, porque no existia la columna con la que separar una cartera de
--       otra. La fuga era de esquema antes que de codigo.
--    2. Si un deudor cambiaba de correo, sus deudas quedaban huerfanas.
--    3. Sin RUT no habia forma de cruzar una deuda con el acreedor que la
--       entrego, y la integracion completa depende de ese cruce.
--
--  QUE HAY QUE SABER ANTES DE LEER
--  · La identidad entre sistemas es el RUT mas el id externo del emisor.
--    Ningun id autoincremental de aca viaja hacia afuera.
--  · El dinero se guarda en DECIMAL, nunca en coma flotante.
--  · Una deuda en UF se guarda en UF. La conversion a pesos ocurre el dia del
--    pago y con el valor de ese dia, y vive en tb_payments.
--  · Este esquema no tiene claves foraneas hacia tb_auth ni tb_payments: son
--    bases distintas, de servicios distintos. Lo que las une son ids logicos,
--    y eso esta dicho en cada columna donde pasa.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  organizations — acreedores y agencias de cobranza.
--
--  Las dos cosas viven en la misma tabla porque son lo mismo para DataBridge:
--  una empresa con RUT que se autentica y opera. Lo que cambia es el rol, y
--  una empresa puede tener los dos (una inmobiliaria que ademas cobre para
--  terceros).
-- -----------------------------------------------------------------------------
CREATE TABLE organizations (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    rut           VARCHAR(12)   NOT NULL,
    legal_name    VARCHAR(160)  NOT NULL,
    trade_name    VARCHAR(120)  NOT NULL,
    kind          VARCHAR(10)   NOT NULL DEFAULT 'creditor',
    status        VARCHAR(10)   NOT NULL DEFAULT 'active',
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                         ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_organization     PRIMARY KEY (id),
    CONSTRAINT uq_organization_rut UNIQUE (rut),

    -- El formato lo obliga la base; el digito verificador lo valida la
    -- aplicacion, porque el modulo 11 es aritmetica y no cabe en un regex.
    CONSTRAINT ck_organization_rut CHECK (rut REGEXP '^[0-9]{7,8}-[0-9K]$'),
    CONSTRAINT ck_organization_kind CHECK (kind IN ('creditor', 'agency', 'both')),
    CONSTRAINT ck_organization_status CHECK (status IN ('active', 'suspended'))
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  api_keys — credenciales de maquina para entregar cartera.
--
--  Se guarda la huella SHA-256, no la clave. Si alguien se lleva esta tabla no
--  se lleva las claves, y DataBridge tampoco puede recordarsela a nadie.
--  `prefix` existe para poder decir cual es sin revelarla.
-- -----------------------------------------------------------------------------
CREATE TABLE api_keys (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    organization_id  BIGINT       NOT NULL,
    name             VARCHAR(80)  NOT NULL,
    key_hash         CHAR(64)     NOT NULL,
    prefix           VARCHAR(12)  NOT NULL,
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_used_at     DATETIME(6)      NULL,
    revoked_at       DATETIME(6)      NULL,

    CONSTRAINT pk_api_key       PRIMARY KEY (id),
    CONSTRAINT uq_api_key_hash  UNIQUE (key_hash),
    CONSTRAINT fk_api_key_org FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE CASCADE,

    INDEX ix_api_key_org (organization_id)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  mandates — una agencia cobra por cuenta de un acreedor.
--
--  Es lo que autoriza a APOFYX a entregar cartera de Patrimonio. Sin mandato
--  vigente, una cartera a nombre de otro se rechaza.
--
--  max_overdue_days lo fija la agencia, no DataBridge: los 120 dias son la
--  regla de APOFYX y otra agencia podria usar otra.
-- -----------------------------------------------------------------------------
CREATE TABLE mandates (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    agency_id         BIGINT       NOT NULL,
    creditor_id       BIGINT       NOT NULL,
    valid_from        DATE         NOT NULL,
    valid_to          DATE             NULL,
    max_overdue_days  SMALLINT UNSIGNED NOT NULL DEFAULT 120,
    status            VARCHAR(10)  NOT NULL DEFAULT 'active',
    declared_by       VARCHAR(160)     NULL,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_mandate PRIMARY KEY (id),
    CONSTRAINT uq_mandate_slot UNIQUE (agency_id, creditor_id, valid_from),

    CONSTRAINT fk_mandate_agency FOREIGN KEY (agency_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mandate_creditor FOREIGN KEY (creditor_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,

    CONSTRAINT ck_mandate_partes CHECK (agency_id <> creditor_id),
    CONSTRAINT ck_mandate_fechas CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_mandate_status CHECK (status IN ('active', 'ended')),

    INDEX ix_mandate_creditor (creditor_id, status)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  campaigns — la estrategia de contacto que define la agencia.
--
--  DataBridge la ejecuta pero no la decide: los canales, los intentos y la
--  cadencia son de la agencia (reparto de §13.6 del documento de APOFYX).
--  external_id es el id de la campana en el sistema de la agencia.
-- -----------------------------------------------------------------------------
CREATE TABLE campaigns (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    agency_id     BIGINT        NOT NULL,
    creditor_id   BIGINT        NOT NULL,
    external_id   VARCHAR(64)   NOT NULL,
    name          VARCHAR(120)  NOT NULL,
    starts_on     DATE          NOT NULL,
    ends_on       DATE              NULL,
    channels      JSON          NOT NULL,
    attempts      SMALLINT UNSIGNED NOT NULL DEFAULT 3,
    cadence_days  JSON              NULL,
    status        VARCHAR(10)   NOT NULL DEFAULT 'running',
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_campaign PRIMARY KEY (id),
    CONSTRAINT uq_campaign_external UNIQUE (agency_id, external_id),

    CONSTRAINT fk_campaign_agency FOREIGN KEY (agency_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_campaign_creditor FOREIGN KEY (creditor_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,

    CONSTRAINT ck_campaign_fechas CHECK (ends_on IS NULL OR ends_on >= starts_on),
    CONSTRAINT ck_campaign_attempts CHECK (attempts BETWEEN 1 AND 10),
    CONSTRAINT ck_campaign_status CHECK (status IN ('running', 'paused', 'finished'))
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  batches — cada Cartera v1 recibida.
--
--  sender_id es quien la envio (el acreedor mismo o su agencia) y creditor_id
--  de quien es la deuda. Se separan porque el unico de idempotencia es por
--  EMISOR: dos emisores distintos pueden numerar sus lotes igual sin chocar.
--
--  payload_hash distingue un reenvio identico —al que se le responde lo
--  mismo— de un lote con el mismo id y otro contenido, que se rechaza con 409.
-- -----------------------------------------------------------------------------
CREATE TABLE batches (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    sender_id        BIGINT        NOT NULL,
    creditor_id      BIGINT        NOT NULL,
    campaign_id      BIGINT            NULL,
    external_id      VARCHAR(64)   NOT NULL,
    cut_off          DATE          NOT NULL,
    source           VARCHAR(10)   NOT NULL DEFAULT 'api',
    status           VARCHAR(12)   NOT NULL DEFAULT 'processed',
    received_count   INT UNSIGNED  NOT NULL DEFAULT 0,
    accepted_count   INT UNSIGNED  NOT NULL DEFAULT 0,
    rejected_count   INT UNSIGNED  NOT NULL DEFAULT 0,
    payload_hash     CHAR(64)      NOT NULL,
    response         JSON          NOT NULL,
    received_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_batch PRIMARY KEY (id),
    CONSTRAINT uq_batch_external UNIQUE (sender_id, external_id),

    CONSTRAINT fk_batch_sender FOREIGN KEY (sender_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_creditor FOREIGN KEY (creditor_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_batch_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaigns (id) ON DELETE SET NULL,

    CONSTRAINT ck_batch_source CHECK (source IN ('api', 'file')),
    CONSTRAINT ck_batch_status CHECK (status IN ('processed', 'rejected')),

    INDEX ix_batch_creditor (creditor_id, cut_off)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  debtors — quien debe.
--
--  Una fila por RUT aunque deba a varios acreedores: es la misma persona. El
--  portal se lo muestra todo junto cuando entra con su codigo, y por eso el
--  deudor no puede estar repetido.
--
--  Sin correo ni telefono no hay como mandarle el codigo de acceso, asi que la
--  base no deja entrar a alguien sin ningun canal.
-- -----------------------------------------------------------------------------
CREATE TABLE debtors (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    rut         VARCHAR(12)   NOT NULL,
    kind        VARCHAR(10)   NOT NULL DEFAULT 'person',
    full_name   VARCHAR(160)  NOT NULL,
    email       VARCHAR(254)      NULL,
    phone       VARCHAR(20)       NULL,
    created_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                       ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_debtor     PRIMARY KEY (id),
    CONSTRAINT uq_debtor_rut UNIQUE (rut),

    CONSTRAINT ck_debtor_rut CHECK (rut REGEXP '^[0-9]{7,8}-[0-9K]$'),
    CONSTRAINT ck_debtor_kind CHECK (kind IN ('person', 'company')),
    CONSTRAINT ck_debtor_canal CHECK (email IS NOT NULL OR phone IS NOT NULL)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  debts — lo que un deudor le debe a un acreedor.
--
--  creditor_id es la columna que arregla la fuga del modelo anterior: es por
--  donde se filtra para que un acreedor vea lo suyo y nada mas. El indice
--  ix_debt_creditor_status existe para eso.
--
--  external_id es el id que le puso el acreedor y viaja intacto por toda la
--  cadena: es lo que permite que un pago vuelva hasta el contrato de arriendo
--  que lo origino.
-- -----------------------------------------------------------------------------
CREATE TABLE debts (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    creditor_id      BIGINT         NOT NULL,
    debtor_id        BIGINT         NOT NULL,
    external_id      VARCHAR(64)    NOT NULL,
    currency         VARCHAR(3)     NOT NULL DEFAULT 'CLP',
    concept          VARCHAR(200)   NOT NULL,
    refs             JSON               NULL,
    original_amount  DECIMAL(18,2)  NOT NULL,
    status           VARCHAR(12)    NOT NULL DEFAULT 'open',
    mandate_id       BIGINT             NULL,
    campaign_id      BIGINT             NULL,
    first_batch_id   BIGINT         NOT NULL,
    last_batch_id    BIGINT         NOT NULL,
    withdrawn_reason VARCHAR(30)        NULL,
    created_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_debt PRIMARY KEY (id),
    CONSTRAINT uq_debt_external UNIQUE (creditor_id, external_id),

    CONSTRAINT fk_debt_creditor FOREIGN KEY (creditor_id)
        REFERENCES organizations (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_debtor FOREIGN KEY (debtor_id)
        REFERENCES debtors (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_mandate FOREIGN KEY (mandate_id)
        REFERENCES mandates (id) ON DELETE SET NULL,
    CONSTRAINT fk_debt_campaign FOREIGN KEY (campaign_id)
        REFERENCES campaigns (id) ON DELETE SET NULL,
    CONSTRAINT fk_debt_first_batch FOREIGN KEY (first_batch_id)
        REFERENCES batches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_debt_last_batch FOREIGN KEY (last_batch_id)
        REFERENCES batches (id) ON DELETE RESTRICT,

    CONSTRAINT ck_debt_currency CHECK (currency IN ('CLP', 'UF')),
    CONSTRAINT ck_debt_amount CHECK (original_amount > 0),
    CONSTRAINT ck_debt_status CHECK (
        status IN ('open', 'repacted', 'paid', 'withdrawn', 'disputed')
    ),
    -- El monto en pesos no lleva decimales. Los que vienen en UF, si.
    CONSTRAINT ck_debt_clp_entero CHECK (
        currency <> 'CLP' OR original_amount = ROUND(original_amount, 0)
    ),

    INDEX ix_debt_creditor_status (creditor_id, status),
    INDEX ix_debt_debtor (debtor_id),
    INDEX ix_debt_campaign (campaign_id)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  debt_charges — el desglose con que llego la deuda.
--
--  Es lo que el deudor ve en el portal para reconocer que esta pagando: los
--  meses de arriendo, las cuotas, las boletas. No se toca cuando hay un plan
--  de pago: para eso estan las cuotas de mas abajo.
-- -----------------------------------------------------------------------------
CREATE TABLE debt_charges (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    debt_id     BIGINT         NOT NULL,
    concept     VARCHAR(120)   NOT NULL,
    period      VARCHAR(7)         NULL,
    amount      DECIMAL(18,2)  NOT NULL,
    due_date    DATE           NOT NULL,

    CONSTRAINT pk_debt_charge PRIMARY KEY (id),
    CONSTRAINT fk_charge_debt FOREIGN KEY (debt_id)
        REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT ck_charge_amount CHECK (amount > 0),

    INDEX ix_charge_debt_due (debt_id, due_date)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  repactations — cada plan de pago que el deudor acepto.
--
--  No se sobreescribe el anterior: se marca superseded_at y se crea otro. Un
--  plan aceptado es un compromiso con fecha, y borrarlo seria perder por que
--  las cuotas son las que son.
-- -----------------------------------------------------------------------------
CREATE TABLE repactations (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    debt_id         BIGINT         NOT NULL,
    months          SMALLINT UNSIGNED NOT NULL,
    monthly_amount  DECIMAL(18,2)  NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    accepted_at     DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    superseded_at   DATETIME(6)        NULL,

    CONSTRAINT pk_repactation PRIMARY KEY (id),
    CONSTRAINT fk_repactation_debt FOREIGN KEY (debt_id)
        REFERENCES debts (id) ON DELETE CASCADE,

    CONSTRAINT ck_repactation_months CHECK (months BETWEEN 1 AND 24),
    CONSTRAINT ck_repactation_amount CHECK (monthly_amount > 0),
    CONSTRAINT ck_repactation_currency CHECK (currency IN ('CLP', 'UF')),

    INDEX ix_repactation_debt (debt_id)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  installments — las cuotas por pagar.
--
--  Una deuda sin repactar tiene una sola cuota. Al repactar se anulan las
--  pendientes y se emiten las nuevas: anular deja el rastro, borrar lo
--  perderia.
--
--  paid_at lo escribe el aviso de pago que llega de ms-payments. Aca no se
--  guarda ningun dato de la pasarela: eso vive en tb_payments.
-- -----------------------------------------------------------------------------
CREATE TABLE installments (
    id               BIGINT         NOT NULL AUTO_INCREMENT,
    debt_id          BIGINT         NOT NULL,
    repactation_id   BIGINT             NULL,
    number           SMALLINT UNSIGNED NOT NULL,
    due_date         DATE           NOT NULL,
    amount           DECIMAL(18,2)  NOT NULL,
    status           VARCHAR(10)    NOT NULL DEFAULT 'pending',
    paid_at          DATETIME(6)        NULL,

    CONSTRAINT pk_installment PRIMARY KEY (id),
    CONSTRAINT uq_installment_number UNIQUE (debt_id, number),

    CONSTRAINT fk_installment_debt FOREIGN KEY (debt_id)
        REFERENCES debts (id) ON DELETE CASCADE,
    CONSTRAINT fk_installment_repactation FOREIGN KEY (repactation_id)
        REFERENCES repactations (id) ON DELETE SET NULL,

    CONSTRAINT ck_installment_amount CHECK (amount > 0),
    CONSTRAINT ck_installment_status CHECK (status IN ('pending', 'paid', 'void')),
    -- Una cuota pagada tiene fecha de pago, y una que no lo esta no la tiene.
    CONSTRAINT ck_installment_paid CHECK (
        (status = 'paid' AND paid_at IS NOT NULL) OR
        (status <> 'paid' AND paid_at IS NULL)
    ),

    INDEX ix_installment_debt_status (debt_id, status)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  debt_events — la auditoria de la deuda, en columnas.
--
--  El modelo anterior guardaba esto como texto armado a mano:
--      "pago_exitoso 50000 CLP via webpay (paymentId=abc)"
--  Eso no se puede consultar: no se puede sumar, ni filtrar por medio, ni
--  cruzar con la pasarela. Aca cada dato tiene su columna y lo que sobra va
--  en `detail`.
-- -----------------------------------------------------------------------------
CREATE TABLE debt_events (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    debt_id     BIGINT         NOT NULL,
    type        VARCHAR(30)    NOT NULL,
    actor       VARCHAR(10)    NOT NULL,
    amount      DECIMAL(18,2)      NULL,
    currency    VARCHAR(3)         NULL,
    reference   VARCHAR(80)        NULL,
    detail      JSON               NULL,
    occurred_at DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_debt_event PRIMARY KEY (id),
    CONSTRAINT fk_debt_event_debt FOREIGN KEY (debt_id)
        REFERENCES debts (id) ON DELETE CASCADE,

    CONSTRAINT ck_debt_event_type CHECK (type IN (
        'registered', 'updated', 'withdrawn', 'code_sent', 'portal_entered',
        'repacted', 'payment_applied', 'settled', 'disputed'
    )),
    CONSTRAINT ck_debt_event_actor CHECK (
        actor IN ('debtor', 'creditor', 'agency', 'system')
    ),
    CONSTRAINT ck_debt_event_currency CHECK (
        currency IS NULL OR currency IN ('CLP', 'UF')
    ),

    INDEX ix_debt_event_debt (debt_id, occurred_at),
    INDEX ix_debt_event_type (type, occurred_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  subscriptions — a donde se avisan los eventos de vuelta.
--
--  NOTA DE SEGURIDAD: `secret` se guarda en claro porque hay que firmar con
--  el, no compararlo. Una huella no sirve para firmar. En produccion esta
--  columna va cifrada con una llave fuera de la base; mientras tanto, queda
--  dicho aqui para que nadie lo lea como un descuido.
-- -----------------------------------------------------------------------------
CREATE TABLE subscriptions (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    organization_id  BIGINT        NOT NULL,
    url              VARCHAR(300)  NOT NULL,
    secret           VARCHAR(120)  NOT NULL,
    events           JSON              NULL,
    active           BOOL          NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_subscription PRIMARY KEY (id),
    CONSTRAINT uq_subscription_url UNIQUE (organization_id, url),
    CONSTRAINT fk_subscription_org FOREIGN KEY (organization_id)
        REFERENCES organizations (id) ON DELETE CASCADE,

    INDEX ix_subscription_org (organization_id, active)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  outbox — los eventos por entregar.
--
--  POR QUE UNA BANDEJA DE SALIDA Y NO UN POST DIRECTO
--  Si se enviara el aviso dentro de la misma operacion que confirma el pago,
--  una caida del receptor dejaria el pago aplicado y el aviso perdido para
--  siempre. Aca el evento se guarda en la misma transaccion que lo origina, y
--  el envio ocurre despues, con reintentos. Si el otro lado esta caido, se
--  entrega cuando vuelva.
--
--  event_id es el UUID que viaja en el cuerpo: el receptor deduplica por el.
-- -----------------------------------------------------------------------------
CREATE TABLE outbox (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    event_id         CHAR(36)      NOT NULL,
    subscription_id  BIGINT        NOT NULL,
    type             VARCHAR(30)   NOT NULL,
    payload          JSON          NOT NULL,
    occurred_at      DATETIME(6)   NOT NULL,
    status           VARCHAR(10)   NOT NULL DEFAULT 'pending',
    attempts         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    delivered_at     DATETIME(6)       NULL,
    last_error       VARCHAR(300)      NULL,

    CONSTRAINT pk_outbox PRIMARY KEY (id),
    -- El mismo evento no se le manda dos veces al mismo suscriptor.
    CONSTRAINT uq_outbox_evento UNIQUE (event_id, subscription_id),
    CONSTRAINT fk_outbox_subscription FOREIGN KEY (subscription_id)
        REFERENCES subscriptions (id) ON DELETE CASCADE,

    CONSTRAINT ck_outbox_status CHECK (
        status IN ('pending', 'delivered', 'failed')
    ),

    -- Por aca busca el despachador: lo pendiente que ya toca reintentar.
    INDEX ix_outbox_por_enviar (status, next_attempt_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  Vistas: el saldo se calcula, no se guarda.
--
--  Un total almacenado al lado de sus partes termina discrepando de ellas. Lo
--  que se debe hoy sale siempre de las cuotas vigentes.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW v_debt_balance AS
SELECT
    d.id            AS debt_id,
    d.creditor_id,
    d.debtor_id,
    d.external_id,
    d.currency,
    d.status,
    d.original_amount,
    COALESCE(SUM(CASE WHEN i.status = 'pending' THEN i.amount END), 0) AS saldo,
    COALESCE(SUM(CASE WHEN i.status = 'paid'    THEN i.amount END), 0) AS pagado,
    MIN(CASE WHEN i.status = 'pending' THEN i.due_date END) AS proximo_vencimiento
FROM debts d
LEFT JOIN installments i ON i.debt_id = d.id
GROUP BY d.id, d.creditor_id, d.debtor_id, d.external_id, d.currency,
         d.status, d.original_amount;


--  Lo que ve el acreedor de su propia cartera. Siempre filtrado por
--  creditor_id: es la consulta que el modelo anterior no podia escribir.
CREATE OR REPLACE VIEW v_creditor_portfolio AS
SELECT
    o.id   AS creditor_id,
    o.rut  AS creditor_rut,
    COUNT(DISTINCT d.id)                                        AS deudas,
    COUNT(DISTINCT CASE WHEN d.status = 'open' THEN d.id END)   AS activas,
    COUNT(DISTINCT CASE WHEN d.status = 'paid' THEN d.id END)   AS pagadas,
    COALESCE(SUM(CASE WHEN d.currency = 'CLP' THEN b.saldo END), 0)  AS saldo_clp,
    COALESCE(SUM(CASE WHEN d.currency = 'UF'  THEN b.saldo END), 0)  AS saldo_uf
FROM organizations o
LEFT JOIN debts d ON d.creditor_id = o.id
LEFT JOIN v_debt_balance b ON b.debt_id = d.id
GROUP BY o.id, o.rut;
