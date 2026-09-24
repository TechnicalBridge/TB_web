// =============================================================================
//  Donde empieza a doler.
// =============================================================================
//  capacidad.js simula personas: cada una lee su deuda y se queda un segundo
//  mirando. Eso dice como lo siente alguien, pero no cuanto aguanta el sistema:
//  con esas pausas, cien personas son apenas unas cuatrocientas peticiones por
//  segundo, y ahi la latencia no se mueve.
//
//  Este escenario no simula a nadie. Impone un ritmo de peticiones por segundo
//  —sin pausas— y lo sube por escalones hasta que algo cede: la latencia se
//  dispara, aparecen errores, o k6 ya no alcanza a sostener el ritmo pedido
//  (las "iteraciones perdidas"). Ese escalon es el techo.
//
//  Una sola ruta, la mas usada —la lista de deudas del deudor—, para que el
//  numero sea del sistema y no de una mezcla de rutas.
//
//      $env:RATE_GLOBAL_CAPACITY = "100000"
//      docker compose --profile app up -d gateway
//      docker compose --profile carga run --rm k6 run /carga/estres.js
// =============================================================================
import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter } from 'k6/metrics';

//  Si el limitador del gateway responde aunque sea una vez, la prueba no esta
//  midiendo el sistema sino el limitador, y cualquier numero que entregue es
//  falso. Pasa facil: basta un "docker compose up" de cualquier servicio sin
//  la variable subida para que Compose recree el gateway con el cupo de
//  fabrica. Asi que la prueba se detiene sola en vez de mentir.
const limitador = new Counter('limitador_intervino');

const PORTAL = __ENV.PORTAL_URI || 'http://portal:8080';
const AUTH = __ENV.AUTH_URI || 'http://ms-auth:8081';
const CLAVE_INTERNA = __ENV.INTERNAL_KEY || 'tbridge-internal-dev';
const RUT = __ENV.RUT_DEUDOR || '16482337-7';

export const options = {
  scenarios: {
    estres: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 400,
      stages: [
        { duration: '15s', target: 200 },  { duration: '30s', target: 200 },
        { duration: '15s', target: 500 },  { duration: '30s', target: 500 },
        { duration: '15s', target: 1000 }, { duration: '30s', target: 1000 },
        { duration: '15s', target: 1500 }, { duration: '30s', target: 1500 },
        { duration: '15s', target: 2000 }, { duration: '30s', target: 2000 },
      ],
    },
  },
  //  Un freno de mano, no una meta: si el p95 pasa de dos segundos ya se
  //  encontro el techo, y seguir solo recalentaria la maquina.
  thresholds: {
    http_req_duration: [{ threshold: 'p(95)<2000', abortOnFail: true, delayAbortEval: '20s' }],
    limitador_intervino: [{ threshold: 'count<1', abortOnFail: true }],
  },
};

export function setup() {
  const emision = http.post(`${AUTH}/internal/codigos`, JSON.stringify({
    rut: RUT, canales: ['correo'], correo: 'estres@prueba.local', acreedor: 'Prueba de estres', paraQue: 'medicion',
  }), { headers: { 'Content-Type': 'application/json', 'X-Internal-Key': CLAVE_INTERNA } });
  if (emision.status !== 200) fail(`no se pudo emitir el codigo: ${emision.status}`);

  const entrada = http.post(`${PORTAL}/api/auth/acceso`,
    JSON.stringify({ rut: RUT, codigo: emision.json('codigo') }),
    { headers: { 'Content-Type': 'application/json' } });
  if (entrada.status !== 200) fail(`el deudor no pudo entrar: ${entrada.status}`);
  return { token: entrada.json('token') };
}

export default function (datos) {
  const r = http.get(`${PORTAL}/api/debts`, {
    headers: { Authorization: `Bearer ${datos.token}` }, tags: { ruta: 'lista' },
  });
  if (r.status === 429) limitador.add(1);
  check(r, { 'responde 200': (x) => x.status === 200 });
}
