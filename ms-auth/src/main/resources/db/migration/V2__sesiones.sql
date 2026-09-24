-- =============================================================================
--  tb_auth · V2 — sesiones revocables
-- =============================================================================
--
--  Hasta aca el JWT duraba siete dias y nadie lo podia revocar: se validaba
--  solo con la firma. Cerrar sesion lo borraba del navegador, pero quien lo
--  hubiera copiado seguia adentro una semana.
--
--  Ahora el JWT dura quince minutos, y lo que mantiene a una persona adentro
--  es una LLAVE DE RENOVACION: un valor al azar de 256 bits que viaja en una
--  cookie que JavaScript no puede leer, y que cada quince minutos se cambia
--  por un JWT nuevo. Esa llave si vive en la base, y por eso si se revoca.
-- =============================================================================


-- -----------------------------------------------------------------------------
--  sessions — una fila por llave de renovacion.
--
--  Cada vez que se usa una llave, se marca como rotada y nace otra en la
--  misma FAMILIA. La familia es un inicio de sesion: el codigo o el enlace
--  que alguien uso para entrar. Tres reglas salen de ahi:
--
--    · Cerrar sesion revoca la familia entera.
--
--    · Una llave ya rotada que vuelve a aparecer significa que alguien mas la
--      tiene: el dueno legitimo ya la cambio por otra. Se revoca la familia
--      entera, la del ladron y la del dueno, y los dos tienen que volver a
--      entrar. El dueno lo nota; el ladron pierde el acceso.
--
--    · La rotacion NO alarga la vida. Todas las llaves de una familia vencen
--      a la hora que vencio la primera: estar activo no mantiene a nadie
--      adentro para siempre.
--
--  De la llave se guarda su huella SHA-256, nunca la llave: quien lea esta
--  tabla no puede entrar con nada de lo que encuentre.
--
--  El deudor se identifica por RUT; el personal por correo, y al renovar se
--  vuelve a mirar staff_users, asi que dar de baja a alguien lo deja afuera
--  en quince minutos aunque tenga una llave vigente.
-- -----------------------------------------------------------------------------
CREATE TABLE sessions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    family_id     CHAR(36)      NOT NULL,
    refresh_hash  CHAR(64)      NOT NULL,
    role          VARCHAR(10)   NOT NULL,
    rut           VARCHAR(12)       NULL,
    email         VARCHAR(254)      NULL,
    issued_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at    DATETIME(6)   NOT NULL,
    rotated_at    DATETIME(6)       NULL,
    revoked_at    DATETIME(6)       NULL,
    ip_hash       CHAR(64)          NULL,

    CONSTRAINT pk_session         PRIMARY KEY (id),
    CONSTRAINT uq_session_refresh UNIQUE (refresh_hash),

    CONSTRAINT ck_session_role     CHECK (role IN ('DEBTOR', 'CREDITOR')),
    CONSTRAINT ck_session_vigencia CHECK (expires_at > issued_at),
    --  Un deudor sin RUT o una persona del personal sin correo no tendrian a
    --  quien volverle a emitir el JWT.
    CONSTRAINT ck_session_identidad CHECK (
        (role = 'DEBTOR'   AND rut   IS NOT NULL) OR
        (role = 'CREDITOR' AND email IS NOT NULL)
    ),
    CONSTRAINT ck_session_rut CHECK (rut IS NULL OR rut REGEXP '^[0-9]{7,8}-[0-9K]$'),

    --  Para revocar una familia entera de una vez.
    INDEX ix_session_familia (family_id)
) ENGINE=InnoDB;
