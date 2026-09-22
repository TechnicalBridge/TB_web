// Lo comun a las pruebas de punta a punta: donde estan los tres sistemas, como
// levantarlos y apagarlos, y como mirar sus bases.
//
// Los tres repositorios tienen que estar uno al lado del otro:
//     Capstone/
//       APOFYX/  PatrimonioInmuebles/  TB_web/
import { spawn, execFileSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const WIN = process.platform === 'win32';

export const TB = path.resolve(import.meta.dirname, '..');
export const CAPSTONE = path.resolve(TB, '..');
export const APOFYX = path.join(CAPSTONE, 'APOFYX');
export const PATRIMONIO = path.join(CAPSTONE, 'PatrimonioInmuebles');

const venv = (dir) => path.join(dir, '.venv', WIN ? 'Scripts/python.exe' : 'bin/python');
export const PY_APOFYX = venv(APOFYX);
export const PY_AI = process.env.MS_AI_PYTHON || venv(path.join(TB, 'ms-ai'));
const MVNW = path.join(TB, WIN ? 'mvnw.cmd' : 'mvnw');

/**
 * El JDK con el que Maven compila.
 *
 * No basta con que haya un java en el PATH: JAVA_HOME suele quedar apuntando al
 * JRE que instala cualquier otro programa, y un JRE no trae javac. Si no
 * compila, Maven se cae en la primera linea y la prueba se queda esperando un
 * servicio que nunca va a arrancar.
 */
function javaHome() {
  const esJdk = (dir) => dir && fs.existsSync(path.join(dir, 'bin', WIN ? 'javac.exe' : 'javac'));
  if (esJdk(process.env.JAVA_HOME)) return process.env.JAVA_HOME;
  const base = 'C:/Program Files/Java';
  if (WIN && fs.existsSync(base)) {
    const jdk = fs.readdirSync(base).filter((d) => d.startsWith('jdk-')).sort().reverse()
      .map((d) => path.join(base, d)).find(esJdk);
    if (jdk) return jdk;
  }
  return null;
}

export const URLS = {
  auth: 'http://127.0.0.1:8081', gateway: 'http://127.0.0.1:8082', debt: 'http://127.0.0.1:8083',
  pay: 'http://127.0.0.1:8084', web: 'http://localhost:5173',
  apofyx: 'http://127.0.0.1:8099', patrimonio: 'http://localhost:3995',
};
export const CLAVE_INTERNA = process.env.INTERNAL_KEY || 'tbridge-internal-dev';
export const RUT = { patrimonio: '76418902-7', apofyx: '77305118-6' };

// ---------------------------------------------------------------------------
//  Resultados
// ---------------------------------------------------------------------------

let fallas = 0;
export function ok(condicion, mensaje) {
  console.log(`${condicion ? 'OK  ' : 'FAIL'} ${mensaje}`);
  if (!condicion) fallas++;
}
export const titulo = (texto) => console.log(`\n--- ${texto}`);
export const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

export async function hasta(fn, segundos = 45) {
  for (let i = 0; i < segundos * 2; i++) {
    const valor = await fn();
    if (valor) return valor;
    await esperar(500);
  }
  return null;
}

// ---------------------------------------------------------------------------
//  Permiso: estas pruebas vacian tablas de las bases de desarrollo
// ---------------------------------------------------------------------------

export function exigirPermiso() {
  if (process.argv.includes('--limpiar-bases') || process.env.PRUEBAS_LIMPIAR === '1') return;
  console.log(`Esta prueba VACIA las tablas de cartera, pagos y eventos de las bases de desarrollo
(tb_debt y tb_payments de DataBridge, y la cartera e integracion de APOFYX) para partir de cero.
Las organizaciones, claves, mandatos, campanas y el personal se conservan.

Si estas de acuerdo, corre de nuevo con --limpiar-bases.`);
  process.exit(2);
}

// ---------------------------------------------------------------------------
//  Procesos
// ---------------------------------------------------------------------------

const procesos = [];

export function arrancar({ cmd, args, cwd, listo, env = {}, capturar, espera = 240000 }) {
  return new Promise((resolve, reject) => {
    const p = spawn(cmd, args, {
      cwd, shell: WIN && cmd.endsWith('.cmd'), detached: !WIN,
      env: { ...process.env, ...env },
    });
    procesos.push(p);

    //  Lo ultimo que dijo el proceso. Si no arranca, esto es lo unico que
    //  explica por que, asi que va en el error en vez de perderse.
    let dicho = '';
    const orden = [cmd, ...args].join(' ');
    const fallar = (motivo) => reject(new Error(
      `${motivo}: ${orden}${dicho.trim() ? `\n\n${dicho.trim().split('\n').slice(-15).join('\n')}` : ''}`));

    const t = setTimeout(() => fallar('no arranco'), espera);
    const mirar = (d) => {
      dicho = (dicho + d).slice(-4000);
      if (capturar) capturar(String(d));
      if (String(d).includes(listo)) { clearTimeout(t); resolve(p); }
    };
    p.stdout.on('data', mirar);
    p.stderr.on('data', mirar);
    //  Un proceso que termina antes de avisar que esta listo no va a avisar
    //  nunca: no tiene sentido esperar los cuatro minutos del plazo.
    p.on('error', (e) => { clearTimeout(t); fallar(`no se pudo ejecutar (${e.code || e.message})`); });
    p.on('exit', (codigo) => { clearTimeout(t); fallar(`termino con codigo ${codigo} sin arrancar`); });
  });
}

const CLASE = { gateway: 'Gateway', 'ms-auth': 'Auth', 'ms-debt': 'Debt', 'ms-payments': 'Payments' };

/** Un servicio de DataBridge con Maven. */
export const servicio = (modulo, env = {}, capturar) => {
  const jdk = javaHome();
  if (!jdk) {
    throw new Error('No encuentro un JDK. Maven necesita compilar, y con un JRE no alcanza.\n'
      + 'Instala el JDK 25 o deja JAVA_HOME apuntando a el.\n'
      + `JAVA_HOME dice ahora: ${process.env.JAVA_HOME || '(nada)'}`);
  }
  return arrancar({
    cmd: MVNW, args: ['-q', '-pl', modulo, 'spring-boot:run'], cwd: TB,
    listo: `Started ${CLASE[modulo]}Application`, env: { JAVA_HOME: jdk, ...env }, capturar,
  });
};

export const asistente = () => arrancar({
  cmd: PY_AI, args: ['-m', 'uvicorn', 'app.main:app', '--port', '8085'], cwd: path.join(TB, 'ms-ai'),
  listo: 'Uvicorn running',
});

export const frontend = () => arrancar({
  cmd: WIN ? 'npx.cmd' : 'npx', args: ['vite', '--port', '5173', '--strictPort'], cwd: path.join(TB, 'frontend'),
  listo: 'ready in',
});

/** Patrimonio sobre una base temporal: la de desarrollo no se toca. */
export function patrimonio(env = {}) {
  const base = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'patrimonio-e2e-')), 'prueba.db');
  return arrancar({
    cmd: process.execPath, args: ['index.js'], cwd: path.join(PATRIMONIO, 'server'), listo: 'API en',
    env: { PORT: '3995', PATRIMONIO_DB: base, ...env },
  });
}

export async function apofyx(env = {}) {
  const p = await arrancar({
    cmd: PY_APOFYX, args: ['manage.py', 'runserver', '8099', '--noreload'], cwd: APOFYX,
    listo: 'development server at', env: { PYTHONUNBUFFERED: '1', ...env },
  });
  // runserver avisa antes de escuchar.
  await hasta(async () => { try { await fetch(URLS.apofyx); return true; } catch { return false; } }, 30);
  return p;
}

export function matar(p) {
  if (!p?.pid) return;
  try {
    if (WIN) execFileSync('taskkill', ['/PID', String(p.pid), '/T', '/F'], { stdio: 'ignore' });
    else process.kill(-p.pid, 'SIGTERM');
  } catch { /* ya no estaba */ }
}

export function terminar() {
  for (const p of procesos) matar(p);
  console.log(fallas ? `\n${fallas} FALLA(S)` : '\nTODO OK');
  process.exit(fallas ? 1 : 0);
}

export function manage(args, env = {}) {
  return execFileSync(PY_APOFYX, ['manage.py', ...args], { cwd: APOFYX, encoding: 'utf8', env: { ...process.env, ...env } });
}

// ---------------------------------------------------------------------------
//  HTTP
// ---------------------------------------------------------------------------

export async function http(url, { token, clave, interna, method = 'GET', body, form, headers = {} } = {}) {
  const r = await fetch(url, {
    method,
    headers: {
      ...(form ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(clave ? { Authorization: `Bearer ${clave}` } : {}),
      ...(interna ? { 'X-Internal-Key': CLAVE_INTERNA } : {}),
      ...headers,
    },
    body: form || (body === undefined ? undefined : typeof body === 'string' ? body : JSON.stringify(body)),
  });
  const tipo = r.headers.get('content-type') || '';
  const cuerpo = tipo.includes('json') ? await r.json() : tipo.includes('pdf') ? await r.arrayBuffer() : await r.text();
  return { status: r.status, body: cuerpo, tipo, headers: r.headers };
}

// ---------------------------------------------------------------------------
//  Bases
// ---------------------------------------------------------------------------

function sql(contenedor, usuario, clave, base, consulta) {
  return execFileSync('docker', ['exec', contenedor, 'mysql', '--default-character-set=utf8mb4',
    `-u${usuario}`, `-p${clave}`, base, '-N', '-e', consulta], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] })
    .trim().split('\n').filter(Boolean).map((l) => l.split('\t'));
}
export const enDataBridge = (q, base = 'tb_debt') => sql('tbridge-db', 'tbridge', 'tbridge_pass', base, q);
export const enApofyx = (q) => sql('apofyx-db', 'apofyx_app', 'apofyx_pass', 'apofyx', q);

/** Deja las carteras en cero en DataBridge y APOFYX. */
export function limpiarBases() {
  enDataBridge(`SET FOREIGN_KEY_CHECKS = 0;
    TRUNCATE outbox; TRUNCATE subscriptions; TRUNCATE debt_events; TRUNCATE installments;
    TRUNCATE repactations; TRUNCATE debt_charges; TRUNCATE debts; TRUNCATE debtors; TRUNCATE batches;
    SET FOREIGN_KEY_CHECKS = 1;`);
  enDataBridge(`SET FOREIGN_KEY_CHECKS = 0;
    TRUNCATE debt_notifications; TRUNCATE payment_events; TRUNCATE payments;
    SET FOREIGN_KEY_CHECKS = 1;`, 'tb_payments');
  enApofyx(`DELETE FROM integracion_outboundevent; DELETE FROM integracion_inboundevent;
    DELETE FROM integracion_subscription; DELETE FROM integracion_forward;
    DELETE FROM cartera_debtcharge; DELETE FROM cartera_debt; DELETE FROM cartera_batch; DELETE FROM cartera_debtor;`);
}

/** Una clave de APOFYX para Patrimonio, emitida por la propia prueba. */
export function clavePatrimonioEnApofyx() {
  const salida = manage(['emitir_clave', RUT.patrimonio, 'Pruebas de punta a punta']);
  const clave = salida.match(/(apx_[A-Za-z0-9_-]+)/)?.[1];
  if (!clave) throw new Error('emitir_clave no devolvio una clave:\n' + salida);
  return clave;
}

/** Una clave de DataBridge para APOFYX. */
export async function claveApofyxEnDataBridge() {
  const r = await http(`${URLS.debt}/internal/claves`, { method: 'POST', interna: true,
    body: { rut: RUT.apofyx, nombre: 'Pruebas de punta a punta' } });
  if (!r.body?.clave) throw new Error('DataBridge no emitio la clave: ' + JSON.stringify(r.body));
  return r.body.clave;
}

/** El id con que APOFYX le presenta su campana de Patrimonio a DataBridge. */
export function campanaDePatrimonio() {
  const [[id]] = enApofyx(`SELECT c.id FROM crm_campaign c JOIN crm_creditor a ON a.id = c.creditor_id
                            WHERE a.tax_id = '${RUT.patrimonio}' AND c.status = 'running' ORDER BY c.id LIMIT 1`);
  return `APX-CMP-${id}`;
}

/** Entra al panel de Patrimonio y le pide la cartera al corte. */
export async function loginPatrimonio() {
  const r = await http(`${URLS.patrimonio}/api/admin/login`, { method: 'POST', body: { password: 'patrimonio' } });
  return r.body.token;
}

export const hoyEnChile = () => new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Santiago' });
