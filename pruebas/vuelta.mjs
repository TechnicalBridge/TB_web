// La vuelta: el deudor paga en DataBridge, DataBridge avisa a APOFYX, APOFYX
// avisa a Patrimonio, y el contrato de arriendo queda al dia. El pago entra
// por la puerta real: codigo de acceso, cobro, pasarela simulada.
//
// Deja la cartera como la esperan las pruebas del portal:
//   CTR-2026-031 pagada, CTR-2025-014 en convenio, CTR-2024-007 (UF) pendiente.
//
//     node pruebas/vuelta.mjs --limpiar-bases
import {
  URLS, ok, titulo, hasta, exigirPermiso, servicio, patrimonio, apofyx, terminar, http, manage,
  enDataBridge, enApofyx, limpiarBases, clavePatrimonioEnApofyx, claveApofyxEnDataBridge, loginPatrimonio,
} from './lib.mjs';

exigirPermiso();

async function entrarComo(rut, paraQue) {
  const { codigo } = (await http(`${URLS.auth}/internal/codigos`, { method: 'POST', interna: true,
    body: { rut, canales: ['correo'], correo: 'deudor@correo.cl', acreedor: 'Patrimonio Inmuebles', paraQue } })).body;
  return (await http(`${URLS.auth}/api/auth/acceso`, { method: 'POST', body: { rut, codigo } })).body.token;
}

try {
  titulo('0. DataBridge completo, APOFYX y Patrimonio');
  limpiarBases();
  await Promise.all([
    servicio('ms-debt', { EVENTOS_INTERVALO_MS: '2000' }),
    servicio('ms-auth'),
    servicio('ms-payments', { APP_DISPATCH_INTERVAL_MS: '2000' }),
  ]);

  titulo('1. Cada uno le dice al de abajo donde avisarle');
  const claveDataBridge = await claveApofyxEnDataBridge();
  const clavePatrimonio = clavePatrimonioEnApofyx();
  const secretoPatrimonio = manage(['suscribir_cliente', '76418902-7', `${URLS.patrimonio}/api/eventos`]).match(/(whsec_\S+)/)?.[1];
  ok(!!secretoPatrimonio, 'APOFYX registra a Patrimonio y le entrega su secreto');
  const env = { DATABRIDGE_URL: URLS.debt, DATABRIDGE_CLAVE: claveDataBridge, DATABRIDGE_REENVIO_INMEDIATO: '1' };
  const secretoDataBridge = manage(['suscribirse_a_databridge', `${URLS.apofyx}/api/v1/eventos`], env)
    .match(/DATABRIDGE_SECRETO_EVENTOS=(\S+)/)?.[1];
  ok(!!secretoDataBridge, 'APOFYX se suscribe a DataBridge y recibe su secreto');
  await Promise.all([
    patrimonio({ EVENTOS_SECRET: secretoPatrimonio }),
    apofyx({ ...env, DATABRIDGE_SECRETO_EVENTOS: secretoDataBridge }),
  ]);

  titulo('2. La cartera baja');
  const token = await loginPatrimonio();
  const emitido = (await http(`${URLS.patrimonio}/api/admin/arriendos/lotes`, { method: 'POST', token,
    body: { corte: '2026-09-18' } })).body;
  const r = (await http(`${URLS.apofyx}/api/v1/carteras`, { method: 'POST', clave: clavePatrimonio, body: emitido.cartera })).body;
  ok(r.aceptadas === 3, `APOFYX acepta ${r.aceptadas} y las pasa a DataBridge`);
  const ids = Object.fromEntries(enDataBridge('SELECT external_id, id FROM debts'));
  const deudaEnPatrimonio = async (codigo) => (await http(`${URLS.patrimonio}/api/admin/arriendos/contratos`, { token }))
    .body.find((c) => c.codigo === codigo)?.deuda;
  ok(await deudaEnPatrimonio('CTR-2026-031') === 410000, 'en Patrimonio, Valentina debe $410.000');

  titulo('3. Valentina paga en DataBridge');
  const valentina = await entrarComo('18905214-6', 'CTR-2026-031');
  const ajena = await http(`${URLS.pay}/api/payments/checkout`, { method: 'POST', token: valentina,
    body: { debtId: ids['CTR-2025-014'], gateway: 'webpay' } });
  ok(ajena.status === 403, `no puede abrir el cobro de la deuda de otro -> ${ajena.status}`);
  const cobro = (await http(`${URLS.pay}/api/payments/checkout`, { method: 'POST', token: valentina,
    body: { debtId: ids['CTR-2026-031'], gateway: 'webpay' } })).body;
  const enlace = new URL(cobro.checkoutUrl);
  const confirmado = await http(`${URLS.pay}/api/payments/public/${enlace.pathname.split('/').pop()}/confirm?sig=${encodeURIComponent(enlace.searchParams.get('sig'))}`, { method: 'POST' });
  ok(confirmado.status === 200, 'la pasarela confirma el pago');

  titulo('4. El aviso sube hasta el contrato de arriendo');
  ok(await hasta(async () => await deudaEnPatrimonio('CTR-2026-031') === 0), 'en Patrimonio, CTR-2026-031 queda en $0');
  ok(enDataBridge(`SELECT status FROM debts WHERE external_id = 'CTR-2026-031'`)[0][0] === 'paid', 'DataBridge: pagada');
  const cuerpos = enDataBridge('SELECT payload FROM outbox').map((f) => f[0]).join(' ');
  ok(!cuerpos.includes('18905214') && !/Valentina/i.test(cuerpos), 'los eventos no llevan datos personales');
  ok(enApofyx(`SELECT status FROM cartera_debt WHERE external_id = 'CTR-2026-031'`)[0][0] === 'paid', 'APOFYX: pagada');
  const avisos = enApofyx(`SELECT status, JSON_UNQUOTE(JSON_EXTRACT(payload, '$.lote_id_externo')) FROM integracion_outboundevent`);
  ok(avisos.length === 2 && avisos.every((a) => a[0] === 'delivered' && a[1] === emitido.lote.id_externo),
    `APOFYX avisa a Patrimonio con el lote de Patrimonio (${avisos[0]?.[1]})`);

  titulo('5. Felipe acepta un plan de 6 cuotas');
  const felipe = await entrarComo('16482337-7', 'CTR-2025-014');
  const plan = await http(`${URLS.debt}/api/debts/${ids['CTR-2025-014']}/repact`, { method: 'POST', token: felipe, body: { months: 6 } });
  ok(plan.status === 200, 'acepta el plan en DataBridge');
  ok(await hasta(() => enApofyx(`SELECT status FROM cartera_debt WHERE external_id = 'CTR-2025-014'`)[0][0] === 'repacted'),
    'APOFYX la pasa a "en convenio de pago"');
  ok(await deudaEnPatrimonio('CTR-2025-014') === 1040000, 'Patrimonio sigue viendo la deuda completa: un plan no es un pago');

  titulo('6. El avance de la campana llega al embudo de APOFYX');
  ok(enApofyx(`SELECT COUNT(*) FROM integracion_inboundevent WHERE type = 'lote.procesado'`)[0][0] !== '0',
    'APOFYX recibe el lote.procesado con que DataBridge confirma la ingesta');
  await http(`${URLS.debt}/internal/campanas/avance`, { method: 'POST', interna: true });
  const foto = await hasta(() => {
    const filas = enApofyx(`SELECT payments, recovered_clp, messages_sent, link_clicks
                              FROM crm_campaignfunnelsnapshot ORDER BY id DESC LIMIT 1`);
    return filas.length && filas[0][0] !== '0' ? filas[0] : null;
  }, 20);
  ok(foto && foto[1] === '410000',
    `el embudo queda con ${foto?.[0]} pago(s) y $${Number(foto?.[1]).toLocaleString('es-CL')} recuperados, ` +
    'lo que APOFYX no podia medir sola');
} catch (e) {
  ok(false, e.message);
} finally {
  terminar();
}
