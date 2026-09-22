// Cada sistema funciona solo: con DataBridge apagado, Patrimonio igual le
// entrega a APOFYX y APOFYX igual le responde. El reenvio queda en la bandeja
// y sale cuando DataBridge vuelve.
//
//     node pruebas/sin-databridge.mjs --limpiar-bases
import {
  URLS, ok, titulo, esperar, exigirPermiso, servicio, patrimonio, apofyx, terminar, http, manage,
  enDataBridge, enApofyx, limpiarBases, clavePatrimonioEnApofyx, loginPatrimonio,
} from './lib.mjs';

exigirPermiso();

try {
  titulo('0. DataBridge apagado');
  limpiarBases();
  let arriba = true;
  try { await fetch(URLS.debt); } catch { arriba = false; }
  ok(!arriba, 'ms-debt no esta corriendo');

  const clavePatrimonio = clavePatrimonioEnApofyx();
  // Mientras DataBridge esta caido la clave da igual: la conexion falla antes.
  const env = { DATABRIDGE_URL: URLS.debt, DATABRIDGE_CLAVE: 'tbk_se_emite_cuando_vuelva' };
  await Promise.all([patrimonio(), apofyx(env)]);
  const token = await loginPatrimonio();
  const emitido = (await http(`${URLS.patrimonio}/api/admin/arriendos/lotes`, { method: 'POST', token,
    body: { corte: '2026-09-18' } })).body;

  titulo('1. Patrimonio entrega igual');
  const inicio = Date.now();
  const r = await http(`${URLS.apofyx}/api/v1/carteras`, { method: 'POST', clave: clavePatrimonio, body: emitido.cartera });
  ok(r.status === 200 && r.body.aceptadas === 3, `APOFYX responde ${r.status} con ${r.body.aceptadas} aceptadas`);
  console.log(`     la respuesta tardo ${Date.now() - inicio} ms`);
  const [[id, estado, intentos]] = enApofyx('SELECT external_id, status, attempts FROM integracion_forward');
  ok(estado === 'pending' && intentos === '1', `el reenvio ${id} queda pendiente tras ${intentos} intento`);

  titulo('2. DataBridge vuelve');
  await servicio('ms-debt');
  console.log('     se espera el minuto del primer reintento');
  await esperar(61000);
  const clave = (await http(`${URLS.debt}/internal/claves`, { method: 'POST', interna: true,
    body: { rut: '77305118-6', nombre: 'Reenvio' } })).body.clave;
  console.log('     ' + manage(['despachar_reenvios'], { ...env, DATABRIDGE_CLAVE: clave }).trim().split('\n').join('\n     '));
  const [[, estado2]] = enApofyx('SELECT external_id, status FROM integracion_forward');
  ok(estado2 === 'sent', `el despachador lo entrega: ${estado2}`);
  const [lote] = enDataBridge(`SELECT status, accepted_count FROM batches WHERE external_id = '${id}'`);
  ok(lote?.[0] === 'processed' && lote?.[1] === '3', `DataBridge recibe ${id}`);
} catch (e) {
  ok(false, e.message);
} finally {
  terminar();
}
