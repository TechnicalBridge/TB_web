// La ida: Patrimonio le entrega su cartera a APOFYX, y APOFYX, sin que nadie
// haga nada, se la pasa a DataBridge. Despues un arrendatario paga en la
// oficina y el retiro tiene que llegar hasta DataBridge para que deje de
// cobrarle.
//
//     node pruebas/ida.mjs --limpiar-bases
import {
  URLS, RUT, ok, titulo, hasta, exigirPermiso, servicio, patrimonio, apofyx, terminar, http,
  enDataBridge, enApofyx, limpiarBases, clavePatrimonioEnApofyx, claveApofyxEnDataBridge,
  campanaDePatrimonio, loginPatrimonio,
} from './lib.mjs';

exigirPermiso();

try {
  titulo('0. Arrancando DataBridge, APOFYX y Patrimonio');
  limpiarBases();
  await servicio('ms-debt');
  const claveDataBridge = await claveApofyxEnDataBridge();
  const clavePatrimonio = clavePatrimonioEnApofyx();
  await Promise.all([
    patrimonio(),
    apofyx({ DATABRIDGE_URL: URLS.debt, DATABRIDGE_CLAVE: claveDataBridge, DATABRIDGE_REENVIO_INMEDIATO: '1' }),
  ]);
  const token = await loginPatrimonio();
  const aApofyx = (cartera) => http(`${URLS.apofyx}/api/v1/carteras`, { method: 'POST', clave: clavePatrimonio, body: cartera });
  const lotes = `${URLS.patrimonio}/api/admin/arriendos/lotes`;

  titulo('1. Patrimonio -> APOFYX -> DataBridge');
  const emitido = (await http(lotes, { method: 'POST', token, body: { corte: '2026-09-18' } })).body;
  const respuesta = (await aApofyx(emitido.cartera)).body;
  ok(respuesta.aceptadas === 3, `APOFYX acepta ${respuesta.aceptadas} de ${respuesta.recibidas} de ${emitido.lote.id_externo}`);

  const [[idReenvio, estado]] = enApofyx(`SELECT f.external_id, f.status FROM integracion_forward f
    JOIN cartera_batch b ON b.id = f.batch_id WHERE b.external_id = '${emitido.lote.id_externo}'`);
  ok(estado === 'sent', `APOFYX lo reenvia solo como ${idReenvio}: ${estado}`);

  const [lote] = enDataBridge(`SELECT b.status, b.accepted_count, o.rut FROM batches b
    JOIN organizations o ON o.id = b.sender_id WHERE b.external_id = '${idReenvio}'`);
  ok(lote?.[0] === 'processed' && lote?.[1] === '3', `DataBridge recibe ${idReenvio}: ${lote?.[1]} aceptadas`);
  ok(lote?.[2] === RUT.apofyx, 'y lo recibe de APOFYX, no de Patrimonio');

  const deudas = enDataBridge(`SELECT d.external_id, c.rut, SUM(ch.amount) FROM debts d
    JOIN organizations c ON c.id = d.creditor_id JOIN debt_charges ch ON ch.debt_id = d.id
    GROUP BY d.id ORDER BY d.external_id`);
  ok(deudas.length === 3 && deudas.every((d) => d[1] === RUT.patrimonio), 'tres deudas, todas con Patrimonio como acreedor');
  ok(Number(deudas.find((d) => d[0] === 'CTR-2025-014')?.[2]) === 1040000, 'los montos llegan intactos: $1.040.000');
  ok(Number(deudas.find((d) => d[0] === 'CTR-2024-007')?.[2]) === 115.5, 'la UF conserva sus decimales: 3 x 38,50 = 115,50');

  const [campana] = enDataBridge(`SELECT channels FROM campaigns WHERE external_id = '${campanaDePatrimonio()}'`);
  ok(campana?.[0] === '["whatsapp", "correo"]', `la campana llega con los canales traducidos: ${campana?.[0]}`);

  titulo('2. Reenviar lo mismo no duplica');
  ok((await aApofyx(emitido.cartera)).body.repetido === true, 'APOFYX responde "repetido"');
  ok(enApofyx('SELECT COUNT(*) FROM integracion_forward')[0][0] === '1', 'y no encola otro reenvio');

  titulo('3. Un pago en la oficina llega como retiro a DataBridge');
  await http(`${lotes}/${emitido.lote.id}/enviado`, { method: 'POST', token });
  const morosos = (await http(`${URLS.patrimonio}/api/admin/arriendos/morosos?corte=2026-09-18`, { token })).body;
  const valentina = morosos.find((m) => m.codigo === 'CTR-2026-031');
  await http(`${URLS.patrimonio}/api/admin/arriendos/pagos`, { method: 'POST', token,
    body: { charge_id: valentina.cargos[0].id, monto: valentina.deuda, medio: 'efectivo' } });
  const segunda = (await http(lotes, { method: 'POST', token, body: { corte: '2026-09-18' } })).body;
  const r2 = (await aApofyx(segunda.cartera)).body;
  ok(r2.resultados.find((r) => r.id_externo === 'CTR-2026-031')?.resultado === 'retirada', 'APOFYX retira CTR-2026-031');
  const retirada = await hasta(() =>
    enDataBridge(`SELECT status FROM debts WHERE external_id = 'CTR-2026-031'`)[0]?.[0] === 'withdrawn', 10);
  ok(retirada, 'DataBridge deja de cobrarle');
} catch (e) {
  ok(false, e.message);
} finally {
  terminar();
}
