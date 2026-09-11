# Technical Bridge & DataBridge

Ecosistema B2B2C de conciliación, repactación y pago de deudas. Arquitectura del documento *Plan de Trabajo Kanban y Arquitectura de Software*.

```
frontend (5173)
    │  /api/*
    ▼
API Gateway 8082  (Spring Cloud Gateway · CORS · Bucket4j)
    ├─ /api/auth/**  /api/me     → MS-Auth      8081
    ├─ /api/debts/** /analytics  → MS-Debt      8083
    ├─ /api/payments/**          → MS-Payments  8084
    └─ /api/ai/**                → MS-AI        8085  (Python FastAPI)
                                      │
MS-Payments ──pago_exitoso──► RabbitMQ (opcional) ──► MS-Debt
                 └ fallback HTTP /internal/events/pago-exitoso
```

| Pieza | Rol |
| --- | --- |
| **API Gateway** | Entrada única, CORS, rate limiting (10 req/min en `/api/auth/**`) |
| **MS-Auth** | Magic links UUID de un solo uso, SMTP (o log), JWT |
| **MS-Debt** | Saldo, cuotas, repactación 3–24 meses, CSV DataBridge, auditoría, PDF deuda cero |
| **MS-Payments** | Mercado Pago / Khipu / Webpay simulados, webhooks HMAC, evento `pago_exitoso` |
| **MS-AI** | Chatbot NLP (SpaceXAI si hay `XAI_API_KEY`, si no reglas locales) · solo lectura |
| **RabbitMQ** | Bus opcional. Sin broker, MS-Payments avisa a MS-Debt por HTTP |

## Cómo arrancar

```bash
npm install
npm run install:all
npm run dev
```

- Web: http://localhost:5173
- Gateway: http://localhost:8082/health

Requisitos: Node 18+, JDK 17+ (el wrapper busca un JDK reciente), Python 3 para MS-AI.

RabbitMQ (opcional):

```bash
docker compose up -d
# EVENTS_RABBIT=true npm run dev
```

## Cuentas demo (passwordless)

Pide el enlace mágico; en desarrollo aparece en pantalla.

| Portal | Correo |
| --- | --- |
| Technical Bridge | `ana.perez@correo.com` |
| Technical Bridge | `demo@technicalbridge.com` |
| DataBridge | `carlos.soto@databridge.com` |

CSV de ejemplo: `databridge-cartera.ejemplo.csv`.

## Flujo

1. El deudor entra con magic link. JWT en sesión (Zustand).
2. **Mis deudas**: saldo, simular cuotas, pagar.
3. La pasarela confirma con firma HMAC; MS-Payments publica `pago_exitoso`.
4. MS-Debt marca cuota/deuda **PAGADA**. El front hace polling.
5. Con saldo cero se descarga el **Certificado de Deuda Cero** (PDF).
6. DataBridge sube CSV y ve recaudación / cartera activa.

IA opcional: copia `.env.example` a `.env` y pon `XAI_API_KEY` (SpaceXAI / xAI).
