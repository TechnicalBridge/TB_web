# Estado respecto del Plan de Trabajo Kanban

Este documento contrasta lo construido con el *Plan de Trabajo Kanban y Arquitectura de Software*
(`Proyecto_Capstone_Kanban_Arquitectura (1).pdf`, en la raíz del repositorio). Cada tarea del
backlog dice dónde quedó; cada decisión que se aparta del plan dice por qué.

Estado al 22-09-2026.

## Resumen

Las cinco épicas están implementadas y probadas. Hay seis desvíos respecto del plan, todos
deliberados; se explican más abajo. Lo que queda pendiente está al final.

| Épica | Estado |
| --- | --- |
| 1. Core y autenticación | Completa |
| 2. Dominio de deuda | Completa |
| 3. Flujo de pagos | Completa, con pasarelas simuladas |
| 4. Inteligencia artificial | Completa, con análisis de sentimiento por reglas |
| 5. Analítica B2B | Completa |

## Épica 1 — Core y autenticación

| Tarea del plan | Estado | Dónde |
| --- | --- | --- |
| Configurar API Gateway con enrutamiento | Hecha | `gateway/` (Spring Cloud Gateway) |
| MS-Auth: generación de UUID y envío SMTP | Hecha | `ms-auth`, `AuthService.pedirEnlace`, `MailService` |
| Rate limiting (Bucket4j) | Hecha | `gateway`, 10 peticiones por minuto en `/api/auth/**` |
| Validar token y emitir JWT | Hecha | `common/jwt`, `AuthService` |
| Inicializar React (Zustand, Axios, Tailwind) | Hecha | `frontend/` |
| Maquetar login passwordless | Hecha | `frontend/src/pages/Login.jsx` |
| Captura del UUID desde la URL y sesión JWT | Hecha | `frontend/src/pages/Magic.jsx` |

**Además:** el código de acceso (RUT + 6 caracteres) es la entrada principal del deudor. Ver el
desvío D2.

## Épica 2 — Dominio de deuda

| Tarea del plan | Estado | Dónde |
| --- | --- | --- |
| Modelar el esquema relacional | Hecha | `ms-*/src/main/resources/db/migration/V1__esquema_inicial.sql` (Flyway) |
| CRUD de deudas y tabla de cuotas | Hecha | `ms-debt`, `DebtService` |
| Lógica de repactación (fechas y montos) | Hecha | `RepactationService`: 3 a 24 cuotas, sin interés, redondeo a la unidad de cada moneda |
| API de ingesta masiva CSV | Hecha | `POST /api/v1/carteras` (archivo o JSON) y `POST /api/debts/cartera` desde el portal |
| Technical Bridge: vista "Mis deudas" | Hecha | `frontend/src/pages/Debts.jsx` |
| Simulador de repactación (slider de meses) | Hecha | `frontend/src/pages/Repact.jsx` |
| DataBridge: subida de CSV con drag & drop | Hecha | `frontend/src/components/CargaCsv.jsx` |

**Además:** la ingesta acepta en forma parcial (una deuda mal formada se rechaza sola, con su
motivo), es idempotente (el mismo lote dos veces no duplica) y valida el dígito verificador del RUT.
Ver el desvío D3.

## Épica 3 — Flujo de pagos

| Tarea del plan | Estado | Dónde |
| --- | --- | --- |
| MS-Payments: conexión con Mercado Pago / Khipu | Simulada | `ms-payments`; la pasarela es una página propia (`Pasarela.jsx`). Ver D5 |
| Endpoints para webhooks | Hecha | `POST /api/payments/webhooks/{pasarela}`, firma HMAC verificada |
| RabbitMQ y publicación del pago | Hecha | `EventPublisher`, cola `ms-debt.pagos-confirmados` |
| MS-Debt: listener que marca la deuda pagada | Hecha | `RabbitPaymentListener` → `DebtService.onPagoConfirmado` |
| Botón "Pagar" que abre la pasarela | Hecha | `frontend/src/pages/Pay.jsx` |
| Estado pendiente con polling | Hecha | `Pay.jsx` consulta el pago y luego el saldo |

**Además:** el monto lo decide ms-debt, nunca el navegador; el aviso de pago sale de una bandeja
con reintentos, así que una caída de ms-debt no pierde pagos; y en UF los pesos se fijan al abrir
el cobro con el valor del Banco Central. Ver el desvío D4.

## Épica 4 — Inteligencia artificial

| Tarea del plan | Estado | Dónde |
| --- | --- | --- |
| Entorno Python / FastAPI | Hecha | `ms-ai/` |
| Endpoint de consulta NLP (chatbot) | Hecha | `POST /api/ai/chat` |
| Lectura segura (solo lectura) del saldo | Hecha | ms-ai consulta ms-debt con la sesión del deudor; no tiene acceso propio a ninguna base |
| Widget de chatbot flotante | Hecha | `frontend/src/components/Chatbot.jsx` |
| Historial de mensajes en el estado de React | Hecha | `Chatbot.jsx` |
| Análisis de sentimiento (sección 3 del plan) | Hecha | `ms-ai/app/nlp.py`. Ver D6 |

## Épica 5 — Analítica B2B

| Tarea del plan | Estado | Dónde |
| --- | --- | --- |
| Endpoints de agregación (recaudado, deudas activas) | Hecha | `GET /api/analytics/summary` |
| PDF "Certificado de deuda cero" | Hecha | `CertificateService`; solo para deudas pagadas por completo |
| Dashboard con Recharts | Hecha | `frontend/src/components/Graficos.jsx`: estado de la cartera y recuperado por día |
| Descarga del certificado desde Technical Bridge | Hecha | `Debts.jsx` |

## Desvíos respecto del plan

**D1. MySQL 8.4 en vez de SQL Server u Oracle.** El proyecto convive con APOFYX, que ya usaba MySQL
8.4. Un solo motor evita mantener dos, y MySQL 8.4 cubre lo que el plan le pide al motor:
transacciones, integridad referencial y restricciones `CHECK`. Cada servicio tiene su propia base,
con el esquema versionado en Flyway y validado al arrancar (`ddl-auto: validate`).

**D2. Código de acceso como entrada principal; el enlace mágico, de respaldo.** El plan pide
autenticación sin contraseña con enlace por correo. Se mantiene, pero el deudor entra primero con su
RUT y un código de seis caracteres. Un enlace en un mensaje de cobranza es exactamente lo que imita
el phishing, y es la razón por la que muchos deudores no pagan lo que sí quieren pagar (documento
de APOFYX, secciones 10 y 13.3). Con el código, el deudor escribe la dirección él mismo. El enlace
sigue siendo el respaldo y la forma de entrar del personal de las empresas.

**D3. El CSV sigue un contrato versionado.** El CSV del plan traía correo, nombre y monto. El
formato actual (Cartera v1, `docs/integracion/`) trae RUT, ids del acreedor y cargos por periodo,
y es el mismo por API y por archivo. Sin RUT no se puede identificar a una persona en Chile, y sin
id del acreedor no hay forma de reenviar una cartera sin duplicarla ni de avisarle un pago.

**D4. RabbitMQ opcional, con respaldo por HTTP.** El plan usa RabbitMQ para que MS-Payments avise a
MS-Debt. Está implementado y probado (`EVENTS_RABBIT=true`). Sin broker, el mismo aviso va por
HTTP. En los dos casos sale de una bandeja con reintentos: el pago y su aviso se guardan en la
misma transacción, así que no hay forma de cobrar sin avisar.

**D5. Pasarelas simuladas.** Mercado Pago, Khipu y Webpay están simulados con la misma forma que
tendrían los reales: enlace de pago firmado, confirmación y webhook con HMAC. Conectarlos a sus
sandbox es cambiar la página de la pasarela y el verificador de firmas; el resto del flujo no cambia.

**D6. Análisis de sentimiento por reglas, no con un modelo.** Detecta frustración y desconfianza con
un léxico en español de Chile, y el asistente ajusta su respuesta: a quien desconfía le explica cómo
verificar que el mensaje es legítimo; a quien no puede pagar le ofrece las cuotas. Si hay una clave
de LLM configurada, el asistente la usa para responder, informado por el mismo análisis.

## Más allá del plan

El plan describe dos plataformas. El proyecto las conecta con dos sistemas más, en una cadena de
tres empresas chilenas:

```
Patrimonio Inmuebles  ──cartera──►  APOFYX  ──cartera──►  DataBridge
(inmobiliaria)        ◄──eventos──  (cobranza) ◄──eventos──  (pagos)
```

Cada sistema funciona solo y se conecta con los demás únicamente por el contrato versionado de
`docs/integracion/`. La cadena está probada de punta a punta en `pruebas/`.

## Pendiente

- Conectar las pasarelas a sus sandbox reales (D5).
- Medir la entrega de los mensajes (`entregados`, `respuestas`, `bajas` del evento
  `campana.avance`): depende del proveedor de mensajería, que todavía no existe.
- El flujo de disputa en el portal, y con él el evento `deuda.disputada`.
- Enviar el código de acceso automáticamente al llegar la cartera; hoy lo envía el personal desde el
  portal.
- Imágenes de Docker para los servicios; hoy Docker levanta solo las bases, RabbitMQ y el buzón de
  prueba.
