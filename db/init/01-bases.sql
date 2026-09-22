-- =============================================================================
--  Crea las tres bases de DataBridge y le da acceso al usuario de la
--  aplicacion. El esquema de cada una lo ponen las migraciones de Flyway del
--  servicio correspondiente, no este archivo.
--
--  Docker lo ejecuta SOLO la primera vez, cuando el volumen esta vacio. Para
--  volver a correrlo hace falta `docker compose down -v`, que borra los datos.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS tb_auth     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS tb_debt     CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS tb_payments CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

--  El usuario de la aplicacion necesita poder crear tablas: Flyway corre con
--  esta misma credencial. Lo que no tiene es acceso a nada fuera de estas tres.
GRANT ALL PRIVILEGES ON tb_auth.*     TO 'tbridge'@'%';
GRANT ALL PRIVILEGES ON tb_debt.*     TO 'tbridge'@'%';
GRANT ALL PRIVILEGES ON tb_payments.* TO 'tbridge'@'%';
FLUSH PRIVILEGES;
