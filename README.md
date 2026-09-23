# Technical Bridge & DataBridge

[![CI](https://github.com/TechnicalBridge/TB_web/actions/workflows/ci.yml/badge.svg)](https://github.com/TechnicalBridge/TB_web/actions/workflows/ci.yml)

Plataforma de pago y repactación de deudas. El deudor entra con su **RUT y un código**, sin
cuenta ni contraseña, ve qué debe y a quién, y paga o acepta un plan de cuotas. La empresa que
gestiona la cartera la ve al día y se entera de cada pago por eventos firmados.

Es la última pieza de la cadena
[**Patrimonio Inmuebles**](https://github.com/TechnicalBridge/patrimonioinmuebles) →
[**APOFYX**](https://github.com/TechnicalBridge/APOFYX) → **DataBridge**, y se conecta con las
otras dos solo por el contrato de [`docs/integracion/`](docs/integracion/README.md).

Cómo quedó cada tarea del *Plan de Trabajo Kanban*, y por qué algunas se apartan de él:
[`docs/plan-kanban.md`](docs/plan-kanban.md).

```
frontend 5173 ── /api/* ──► Gateway 8082 (CORS · rate limiting)
                              ├─ /api/auth/**  /api/me          → ms-auth      8081
                              ├─ /api/debts/** /api/analytics/** → ms-debt      8083
                              ├─ /api/payments/**                → ms-payments  8084
                              └─ /api/ai/**                      → ms-ai        8085

Contrato v1, de sistema a sistema (con clave de API):
  APOFYX ── POST /api/v1/carteras, /mandatos, /campanas, /suscripciones ──► ms-debt
  ms-debt ── eventos firmados (pago.confirmado, deuda.saldada, ...) ──► APOFYX

Entre servicios (clave interna, no expuesto por el gateway):
  ms-payments ── /internal/events/pago-confirmado ──► ms-debt
  ms-debt ───── /internal/codigos ──► ms-auth
```

| Pieza | Qué hace |
| --- | --- |
| **ms-auth** | Código de acceso (RUT + 6 caracteres, un solo uso, 24 h) y enlace de respaldo por correo, que es también como entra el personal de las empresas. Emite el JWT |
| **ms-debt** | Deudas, cargos y cuotas; simulación y aceptación de planes (3 a 24 meses, sin interés); ingesta de la cartera v1 por API o CSV; eventos de vuelta a quien entregó la cartera; resumen para el dashboard y certificado PDF de deuda pagada |
| **ms-payments** | Cobros con Webpay, Mercado Pago y Khipu simulados. El monto lo decide ms-debt, nunca el navegador. En UF fija los pesos al abrir el cobro |
| **ms-ai** | Asistente de solo lectura (Python/FastAPI). Lee las deudas con la sesión del deudor, nunca con acceso propio a la base, y detecta frustración o desconfianza para ajustar el tono. Usa un LLM si hay `XAI_API_KEY`; si no, reglas |
| **MySQL 8.4** | Una base por servicio, con esquema versionado en Flyway (`db/README.md`) |
| **RabbitMQ** | Opcional (`EVENTS_RABBIT=true`). Lleva el aviso de pago de ms-payments a ms-debt; sin él va por HTTP. En los dos casos el aviso sale de una bandeja con reintentos |

## Cómo arrancar

Requisitos: **Docker Desktop**, **JDK 25**, **Node 18+** y Python 3 para ms-ai.

```bash
# 1. Base de datos y buzón de prueba
docker compose up -d mysql mailpit

# 2. Dependencias
npm install
npm run install:all

# 3. Todo junto: gateway, servicios y frontend
npm run dev
```

| | |
| --- | --- |
| Portal | http://localhost:5173 |
| Buzón de prueba | http://localhost:8025 |
| Gateway | http://localhost:8082/actuator/health |

Para que ms-auth mande los correos al buzón de prueba en vez de solo escribirlos en su log:

```bash
SMTP_HOST=localhost SMTP_PORT=1025 SMTP_AUTH=false SMTP_STARTTLS=false
```

## Recorrido de demostración

1. **La cartera llega por API o por archivo.** APOFYX la entrega con `POST /api/v1/carteras`
   (ver su README); quien no tiene integración arrastra el CSV del contrato al portal. Las dos
   entradas usan la misma ingesta: mismas validaciones, aceptación parcial e idempotencia. Al
   arrancar vacío, ms-debt siembra un ejemplo.
2. **La empresa entra.** En *Soy una empresa*, `camila.reyes@apofyx.cl` pide su enlace; llega
   al buzón de prueba. Ve la cartera que APOFYX entregó, aunque el acreedor sea Patrimonio.
3. **Le envía el código al deudor.** El botón *Enviar código* manda el código al correo del
   deudor. La pantalla no lo muestra: quien lo viera podría entrar en su lugar.
4. **El deudor entra** con su RUT y ese código, simula un plan, lo acepta y paga.
5. **El pago vuelve por la cadena**: ms-payments avisa a ms-debt, ms-debt emite los eventos, y
   APOFYX y Patrimonio los reciben (ver el contrato, sección 8).

**Pagos en UF.** Usan la UF de ese día exacto, del **Banco Central** (decisión I10). Como la publica
con un mes de adelanto, ms-payments la carga al arrancar y cada mañana a las 9:30. Necesita las
credenciales de su API, que se obtienen registrándose en si3.bcentral.cl:

```bash
BCENTRAL_USER=tu-usuario BCENTRAL_PASS=tu-clave
```

Sin ellas, ms-payments avisa en el log y la UF se carga a mano:

```bash
curl -X POST http://localhost:8084/internal/uf \
     -H "X-Internal-Key: tbridge-internal-dev" -H "Content-Type: application/json" \
     -d '{"dia":"2026-09-22","valor":"39876.54"}'
```

## Seguridad, en corto

- El deudor nunca recibe un enlace para entrar: escribe la dirección y su código.
- "No hay código" y "código incorrecto" responden igual, para no revelar qué RUT tienen deuda.
- Cada sesión se identifica por RUT. Un deudor ve y paga solo lo suyo; una empresa ve solo la
  cartera que le corresponde.
- El código de acceso solo se escribe en el log cuando no hay SMTP, es decir, en desarrollo.
- Los eventos van firmados con HMAC-SHA256 y caducan a los 5 minutos.

`databridge-cartera.ejemplo.csv` es del formato anterior y ya no se usa. La plantilla vigente
está en `docs/integracion/ejemplos/`, y el portal la ofrece para descargar.

## Pruebas

```bash
./mvnw test                                   # Java: common, gateway y los tres servicios
cd ms-ai && python -m unittest discover tests # asistente
```

Una prueba de ms-debt convierte la plantilla CSV del contrato y comprueba que da exactamente las
mismas deudas que el ejemplo JSON: los dos formatos son un solo contrato.

Las pruebas de punta a punta levantan los tres sistemas a la vez (Patrimonio, APOFYX y DataBridge)
y recorren la cadena completa: ver [`pruebas/README.md`](pruebas/README.md).
