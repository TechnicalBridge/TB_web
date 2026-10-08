-- =============================================================================
--  V4 — El token de Webpay se manda una sola vez
-- =============================================================================
--  En Webpay un token sirve una sola vez: si la pagina que lleva al deudor a
--  Webpay se abre de nuevo (recargar, volver atras, "Abrirla de nuevo"), el
--  mismo token llega dos veces y Transbank responde con el Error 21. Aca queda
--  cuando se llevo al deudor a la pasarela, para no volver a mandarlo.
--
--  NULL en un pago de antes, en uno que todavia no se abrio, y en los de las
--  otras pasarelas, cuyas paginas si se pueden abrir de nuevo.
-- =============================================================================

ALTER TABLE payments ADD COLUMN redirected_at DATETIME(6) NULL AFTER created_at;
