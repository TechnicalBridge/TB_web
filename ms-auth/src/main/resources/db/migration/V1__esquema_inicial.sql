-- =============================================================================
--  tb_auth — como entra cada quien a DataBridge
-- =============================================================================
--  Motor : MySQL 8.4 (InnoDB) · utf8mb4 · migraciones con Flyway
--
--  DOS PUERTAS DISTINTAS, Y NO SE PARECEN
--
--  1. EL DEUDOR entra con un CODIGO y **sin cuenta**. No hay usuario, no hay
--     contrasena, no hay registro. Esa es la tesis de §13.3 y §13.4 del
--     documento de APOFYX: el codigo le llega por WhatsApp y por correo, y el
--     deudor entra escribiendo la direccion del portal por su cuenta.
--
--     Por que importa tanto: un phisher necesita que hagas clic en SU enlace.
--     Si el mensaje te dice que vayas por tu cuenta a un sitio que puedes
--     escribir, buscar o verificar antes de entrar, el atacante pierde el
--     control del destino, que era todo lo que tenia.
--
--     El magic link queda como respaldo configurable (decision I9), para el
--     deudor que no logra entrar con el codigo. Es la excepcion, no el camino.
--
--  2. EL PERSONAL de una organizacion —el operador de la agencia, el
--     administrativo del acreedor— si tiene cuenta, porque vuelve todos los
--     dias y necesita permisos. Tampoco usa contrasena: entra por enlace.
--
--  NI UN SECRETO EN CLARO
--  Ni los codigos ni los tokens se guardan tal cual: se guarda su huella. Un
--  respaldo de esta base no le sirve a nadie para entrar al portal de nadie.
-- =============================================================================

-- -----------------------------------------------------------------------------
--  access_codes — el codigo que abre el portal del deudor.
--
--  El codigo NO identifica una deuda sino a una persona: al entrar ve todo lo
--  que debe, que es lo que un deudor espera y lo que evita mandarle cinco
--  codigos a quien debe cinco cosas.
--
--  attempts y max_attempts estan porque un codigo corto se puede adivinar a
--  fuerza bruta. Pasados los intentos, el codigo muere y hay que pedir otro.
-- -----------------------------------------------------------------------------
CREATE TABLE access_codes (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    code_hash      CHAR(64)      NOT NULL,
    debtor_rut     VARCHAR(12)   NOT NULL,
    -- Por donde se mando: ['whatsapp','email']. Dos canales es la prueba de
    -- §13.3; uno solo funciona igual pero prueba menos.
    channels       JSON          NOT NULL,
    issued_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at     DATETIME(6)   NOT NULL,
    consumed_at    DATETIME(6)       NULL,
    attempts       SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts   SMALLINT UNSIGNED NOT NULL DEFAULT 5,
    issued_for     VARCHAR(64)       NULL,

    CONSTRAINT pk_access_code      PRIMARY KEY (id),
    CONSTRAINT uq_access_code_hash UNIQUE (code_hash),

    CONSTRAINT ck_access_code_rut CHECK (debtor_rut REGEXP '^[0-9]{7,8}-[0-9K]$'),
    CONSTRAINT ck_access_code_vigencia CHECK (expires_at > issued_at),
    CONSTRAINT ck_access_code_intentos CHECK (attempts <= max_attempts),

    -- Para encontrar el codigo vigente de un deudor sin recorrer la tabla.
    INDEX ix_access_code_deudor (debtor_rut, expires_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  magic_links — el respaldo cuando el codigo no funciona.
--
--  Se conserva a proposito y con una advertencia: es justo el patron que el
--  documento critica, porque el destino lo elige quien manda el mensaje. Por
--  eso vive mas corto que el codigo y se usa solo cuando el deudor lo pide.
-- -----------------------------------------------------------------------------
CREATE TABLE magic_links (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    token_hash   CHAR(64)     NOT NULL,
    debtor_rut   VARCHAR(12)      NULL,
    email        VARCHAR(254) NOT NULL,
    issued_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at   DATETIME(6)  NOT NULL,
    consumed_at  DATETIME(6)      NULL,

    CONSTRAINT pk_magic_link      PRIMARY KEY (id),
    CONSTRAINT uq_magic_link_hash UNIQUE (token_hash),
    CONSTRAINT ck_magic_link_vigencia CHECK (expires_at > issued_at),

    INDEX ix_magic_link_correo (email, expires_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  staff_users — quienes entran al portal de una organizacion.
--
--  org_rut es una referencia logica a organizations.rut de tb_debt. No hay
--  clave foranea porque son bases de servicios distintos, y el RUT es
--  justamente la identidad que sirve entre sistemas.
-- -----------------------------------------------------------------------------
CREATE TABLE staff_users (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    org_rut       VARCHAR(12)   NOT NULL,
    email         VARCHAR(254)  NOT NULL,
    full_name     VARCHAR(160)  NOT NULL,
    role          VARCHAR(10)   NOT NULL DEFAULT 'operator',
    created_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_login_at DATETIME(6)       NULL,
    disabled_at   DATETIME(6)       NULL,

    CONSTRAINT pk_staff_user       PRIMARY KEY (id),
    CONSTRAINT uq_staff_user_email UNIQUE (email),

    CONSTRAINT ck_staff_user_rut CHECK (org_rut REGEXP '^[0-9]{7,8}-[0-9K]$'),
    CONSTRAINT ck_staff_user_role CHECK (role IN ('operator', 'admin')),

    INDEX ix_staff_user_org (org_rut, disabled_at)
) ENGINE=InnoDB;


-- -----------------------------------------------------------------------------
--  access_log — quien entro, cuando y por donde.
--
--  Sirve para dos cosas distintas: para que el acreedor sepa que su deudor si
--  vio la deuda (es la metrica que reemplaza al clic en el enlace), y para
--  detectar a alguien probando codigos.
--
--  ip_hash y no la IP: alcanza para contar intentos desde un mismo origen sin
--  guardar de donde se conecta una persona que solo venia a pagar.
-- -----------------------------------------------------------------------------
CREATE TABLE access_log (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    debtor_rut   VARCHAR(12)      NULL,
    method       VARCHAR(12)  NOT NULL,
    outcome      VARCHAR(12)  NOT NULL,
    ip_hash      CHAR(64)         NULL,
    occurred_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT pk_access_log PRIMARY KEY (id),
    CONSTRAINT ck_access_log_method CHECK (method IN ('code', 'magic_link')),
    CONSTRAINT ck_access_log_outcome CHECK (
        outcome IN ('granted', 'expired', 'invalid', 'exhausted')
    ),

    INDEX ix_access_log_deudor (debtor_rut, occurred_at),
    INDEX ix_access_log_origen (ip_hash, occurred_at)
) ENGINE=InnoDB;
