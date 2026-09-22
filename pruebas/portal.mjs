// El portal completo, con las mismas llamadas que hacen sus pantallas, a
// traves del proxy de Vite y del gateway, y con RabbitMQ llevando los pagos:
//   - la empresa entra, ve su cartera y le envia el codigo al deudor;
//   - el deudor entra, repacta en UF y paga (los pesos se fijan al abrir el cobro);
//   - el chatbot, la carga CSV, el certificado y los datos del dashboard.
//
// Parte de lo que deja vuelta.mjs. Levanta RabbitMQ con docker compose.
//
//     node pruebas/vuelta.mjs --limpiar-bases && node pruebas/portal.mjs
import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import {
  URLS, TB, PY_AI, RUT, ok, titulo, hasta, servicio, asistente, frontend, terminar, http,
  enDataBridge, claveApofyxEnDataBridge, hoyEnChile,
} from './lib.mjs';

const WEB = `${URLS.web}/api`;
const RABBIT = { Authorization: 'Basic ' + btoa('guest:guest') };
const COLS = 'deuda_id;accion;motivo_retiro;deudor_rut;deudor_tipo;deudor_nombre;deudor_correo;deudor_telefono;moneda;concepto;referencias;cargo_concepto;cargo_periodo;cargo_monto;cargo_vencimiento';
let logAuth = '';

function dv(cuerpo) {
  let suma = 0, m = 2;
  for (const d of String(cuerpo).split('').reverse()) { suma += Number(d) * m; m = m === 7 ? 2 : m + 1; }
  const r = 11 - (suma % 11);
  return r === 11 ? '0' : r === 10 ? 'K' : String(r);
}
const archivo = (texto, campos) => {
  const f = new FormData();
  f.append('archivo', new Blob([texto], { type: 'text/csv' }), 'cartera.csv');
  for (const [k, v] of Object.entries(campos)) f.append(k, v);
  return f;
};
async function codigoPara(correo, token, deudaId) {
  const desde = logAuth.length;
  await http(`${WEB}/debts/${deudaId}/codigo`, { method: 'POST', token });
  const patron = new RegExp(`${correo.replace(/\./g, '\\.')}: ([2-9A-HJ-NP-Z]{6})`);
  return hasta(() => logAuth.slice(desde).match(patron)?.[1]);
}
async function pagar(token, debtId, installmentId, antesDeConfirmar) {
  const cobro = (await http(`${WEB}/payments/checkout`, { method: 'POST', token, body: { debtId, installmentId, gateway: 'webpay' } })).body;
  if (antesDeConfirmar) await antesDeConfirmar();
  const u = new URL(cobro.checkoutUrl);
  const confirmado = (await http(`${WEB}/payments/public/${u.pathname.split('/').pop()}/confirm?sig=${encodeURIComponent(u.searchParams.get('sig'))}`, { method: 'POST' })).body;
  return { cobro, confirmado, id: u.pathname.split('/').pop() };
}
const cargarUf = (valor) => http(`${URLS.pay}/internal/uf`, { method: 'POST', interna: true, body: { dia: hoyEnChile(), valor } });

try {
  titulo('0. RabbitMQ, DataBridge completo y el frontend');
  execFileSync('docker', ['compose', 'up', '-d', 'rabbitmq'], { cwd: TB, stdio: 'ignore' });
  await hasta(async () => { try { return (await fetch('http://localhost:15672/api/overview', { headers: RABBIT })).ok; } catch { return false; } }, 60);
  const conRabbit = { EVENTS_RABBIT: 'true' };
  const conChatbot = fs.existsSync(PY_AI);
  await Promise.all([
    servicio('gateway'),
    servicio('ms-auth', {}, (d) => { logAuth += d; }),
    servicio('ms-debt', conRabbit),
    servicio('ms-payments', { ...conRabbit, APP_DISPATCH_INTERVAL_MS: '2000' }),
    frontend(),
    ...(conChatbot ? [asistente()] : []),
  ]);
  ok((await http(`${URLS.web}/login`)).body.includes('<div id="root">'), 'el frontend sirve la SPA');

  titulo('1. La empresa entra y le envia el codigo al deudor');
  const pedido = await http(`${WEB}/auth/enlace`, { method: 'POST', body: { correo: 'camila.reyes@apofyx.cl', rut: null } });
  ok(pedido.status === 202 && !JSON.stringify(pedido.body).includes('token'), 'pide su enlace; la respuesta no lo trae');
  const tokenEnlace = await hasta(() => logAuth.match(/camila\.reyes@apofyx\.cl: \S+\/magic\?token=([0-9a-f-]{36})/)?.[1]);
  const camila = (await http(`${WEB}/auth/verify`, { method: 'POST', body: { token: tokenEnlace } })).body.token;
  const yo = (await http(`${WEB}/me`, { token: camila })).body.user;
  ok(yo?.role === 'CREDITOR' && yo?.nombre === 'Camila Reyes', `entra como ${yo?.nombre}`);
  const cartera = (await http(`${WEB}/debts`, { token: camila })).body.debts;
  ok(cartera.length === 3, `ve la cartera que APOFYX entrego aunque el acreedor sea Patrimonio (${cartera.length})`);
  const uf = cartera.find((d) => d.moneda === 'UF');
  const felipeDeuda = cartera.find((d) => d.externalId === 'CTR-2025-014');
  const codigoPyme = await codigoPara('administracion@nandu.cl', camila, uf.id);
  ok(!!codigoPyme, 'el codigo le llega al correo del deudor, no a la pantalla');
  ok((await http(`${WEB}/debts/${uf.id}/codigo`, { method: 'POST' })).status === 401, 'sin sesion nadie pide codigos');

  titulo('2. La pyme repacta en UF y paga la primera cuota');
  const pyme = (await http(`${WEB}/auth/acceso`, { method: 'POST', body: { rut: '76.991.245-2', codigo: codigoPyme } })).body.token;
  const suyas = (await http(`${WEB}/debts`, { token: pyme })).body.debts;
  ok(suyas.length === 1 && suyas[0].moneda === 'UF', 'entra con el RUT con puntos y ve solo lo suyo');
  const plan = (await http(`${WEB}/debts/${uf.id}/simulate?months=6`, { token: pyme })).body.plan;
  ok(Number(plan.total) === 115.5 && Number(plan.monthlyAmount) === 19.25, `6 cuotas de UF ${plan.monthlyAmount}, total UF ${plan.total}`);
  await http(`${WEB}/debts/${uf.id}/repact`, { method: 'POST', token: pyme, body: { months: 6 } });
  const cuota = (await http(`${WEB}/debts/${uf.id}`, { token: pyme })).body.cuotas.find((c) => c.estado === 'pending');
  await cargarUf('40000.00');
  const pagoUf = await pagar(pyme, uf.id, cuota.id, () => cargarUf('41000.00'));
  ok(pagoUf.cobro.amountClp === 770000, `al abrir el cobro ya se sabe cuanto cobrar: $${pagoUf.cobro.amountClp}`);
  ok(pagoUf.confirmado.amountClp === 770000, 'y se registra eso aunque la UF cambie antes de confirmar');
  ok((await http(`${WEB}/payments/${pagoUf.id}`, { token: pyme })).status === 200, 'el deudor ve su propio pago');
  ok((await http(`${WEB}/payments/${pagoUf.id}`, { token: camila })).status === 403, 'APOFYX no ve el pago: el acreedor es otro');
  ok(await hasta(async () => Number((await http(`${WEB}/debts/${uf.id}`, { token: pyme })).body.saldo) === 96.25),
    'el saldo baja a UF 96,25');

  titulo('3. El pago viajo por RabbitMQ');
  const felipeCodigo = await codigoPara('felipe.rojas@correo.cl', camila, felipeDeuda.id);
  const felipe = (await http(`${WEB}/auth/acceso`, { method: 'POST', body: { rut: '16.482.337-7', codigo: felipeCodigo } })).body.token;
  const detalle = (await http(`${WEB}/debts/${felipeDeuda.id}`, { token: felipe })).body;
  const saldoAntes = Number(detalle.saldo);
  await pagar(felipe, felipeDeuda.id, detalle.cuotas.find((c) => c.estado === 'pending').id);
  const saldoDespues = await hasta(async () => {
    const s = Number((await http(`${WEB}/debts/${felipeDeuda.id}`, { token: felipe })).body.saldo);
    return s < saldoAntes ? s : null;
  });
  ok(saldoDespues !== null, `la cuota de Felipe se aplica: $${saldoAntes.toLocaleString('es-CL')} -> $${saldoDespues?.toLocaleString('es-CL')}`);
  // RabbitMQ actualiza sus estadisticas cada pocos segundos.
  const cola = await hasta(async () => {
    const c = await (await fetch('http://localhost:15672/api/queues/%2F/ms-debt.pagos-confirmados', { headers: RABBIT })).json();
    return (c.message_stats?.publish ?? 0) >= 2 ? c : null;
  }, 30) || {};
  ok((cola.message_stats?.publish ?? 0) >= 2 && cola.messages === 0,
    `los dos avisos pasaron por la cola (publicados ${cola.message_stats?.publish}, pendientes ${cola.messages})`);

  titulo('4. El chatbot');
  if (!conChatbot) {
    console.log(`     se salta: no hay entorno de Python en ${PY_AI} (ver pruebas/README.md)`);
  } else {
    const chat = (message) => http(`${WEB}/ai/chat`, { method: 'POST', token: felipe, body: { message, messages: [] } });
    const saldo = (await chat('¿cuánto debo?')).body;
    ok(saldo.reply.includes(`$${saldoDespues.toLocaleString('es-CL')}`), `responde con el saldo real: "${saldo.reply.split('\n')[0]}"`);
    const duda = (await chat('¿esto es una estafa? no reconozco esta deuda')).body;
    ok(duda.sentimiento.etiqueta === 'desconfianza' && duda.reply.includes('nunca te manda un enlace'), 'ante la desconfianza explica como verificar');
    const cesante = (await chat('estoy cesante, no puedo pagar')).body;
    ok(cesante.sentimiento.etiqueta === 'frustracion' && cesante.reply.includes('sin interés'), 'ante la frustracion ofrece cuotas');
  }

  titulo('5. Carga CSV: desde el portal y por la API');
  const opciones = (await http(`${WEB}/debts/cartera/opciones`, { token: camila })).body;
  const patrimonio = opciones.acreedores.find((a) => a.rut === RUT.patrimonio);
  ok(patrimonio?.campanas.length > 0, `el formulario ofrece a ${patrimonio?.nombre} y sus campanas`);
  const corte = hoyEnChile();
  const vence = new Date(Date.now() - 20 * 864e5).toLocaleDateString('sv-SE', { timeZone: 'America/Santiago' });
  const rut = `21345678-${dv(21345678)}`;
  const csv = [COLS,
    `CTR-2026-050;registrar;;${rut};persona;Josefa Muñoz Tapia;josefa.munoz@correo.cl;;CLP;Arriendo mensual;;Arriendo;${corte.slice(0, 7)};450000;${vence}`,
    `CTR-2026-051;registrar;;21345678-${dv(21345678) === '1' ? '2' : '1'};persona;RUT mal escrito;;;CLP;Arriendo;;Arriendo;;300000;${vence}`,
    'CTR-9999-001;retirar;pago_directo;;;;;;;;;;;;'].join('\n');
  const lote = `CSV-${Date.now()}`;
  const campos = { lote_id_externo: lote, fecha_corte: corte, acreedor_rut: patrimonio.rut, campana_id_externo: patrimonio.campanas[0].idExterno };
  const carga = (await http(`${WEB}/debts/cartera`, { method: 'POST', token: camila, form: archivo(csv, campos) })).body;
  ok(carga.recibidas === 3 && carga.aceptadas === 1, `aceptacion parcial: ${carga.aceptadas} de ${carga.recibidas}, cada rechazo con su motivo`);
  ok((await http(`${WEB}/debts/cartera`, { method: 'POST', token: camila, form: archivo(csv, campos) })).body.repetido === true,
    'el mismo archivo dos veces no duplica');
  const viejo = await http(`${WEB}/debts/cartera`, { method: 'POST', token: camila,
    form: archivo('email,nombre,monto\nana@correo.cl,Ana,1000\n', { ...campos, lote_id_externo: `${lote}-X` }) });
  ok(viejo.status === 400 && viejo.body.error.mensaje.includes('Faltan columnas'), 'el formato antiguo se rechaza explicando por que');
  const clave = await claveApofyxEnDataBridge();
  const porApi = await http(`${URLS.debt}/api/v1/carteras`, { method: 'POST', clave, form: archivo(
    [COLS, `CTR-2026-052;registrar;;${rut};persona;Josefa Muñoz Tapia;josefa.munoz@correo.cl;;CLP;Estacionamiento;;Estacionamiento;;60000;${vence}`].join('\n'),
    { lote_id_externo: `${lote}-API`, fecha_corte: corte, acreedor_rut: patrimonio.rut, agencia_rut: RUT.apofyx }) });
  ok(porApi.body.aceptadas === 1, 'POST /api/v1/carteras con archivo y clave de API');

  titulo('6. Certificado y dashboard');
  const pagada = cartera.find((d) => d.estado === 'paid');
  const pdf = await http(`${WEB}/debts/${pagada.id}/certificate`, { token: camila });
  ok(pdf.status === 200 && new TextDecoder().decode(pdf.body.slice(0, 5)) === '%PDF-', `certificado de ${pagada.externalId}: PDF`);
  ok((await http(`${WEB}/debts/${felipeDeuda.id}/certificate`, { token: camila })).status === 409, 'para una deuda en convenio no se emite');
  const resumen = (await http(`${WEB}/analytics/summary`, { token: camila })).body;
  ok(resumen.recuperadoPorDia?.CLP?.length === 30 && Number(resumen.recuperadoPorDia.CLP.at(-1).monto) > 0,
    'el grafico trae los 30 dias con lo recuperado hoy');
  ok(resumen.porAcreedor?.some((a) => a.acreedor === 'Patrimonio Inmuebles'), 'y el desglose por acreedor');
} catch (e) {
  ok(false, e.message);
} finally {
  try { enDataBridge(`DELETE FROM uf_values WHERE day = '${hoyEnChile()}' AND source = 'manual'`, 'tb_payments'); } catch {}
  try { execFileSync('docker', ['compose', 'stop', 'rabbitmq'], { cwd: TB, stdio: 'ignore' }); } catch {}
  terminar();
}
