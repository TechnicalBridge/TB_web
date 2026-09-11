# DIGITAL BOT

App web de planes y pagos con un **sistema automatizado verificado**. Sin un token `DBT-XXXX-XXXX-XXXX` la forma de pago no se muestra.

Paleta: azul oscuro, celeste, verde y blanco.

Arquitectura:

```
frontend (5173) → API Gateway (8082) → Spring Boot (8081)
```

- **Web** (Vite/React): http://localhost:5173
- **API Gateway** (Spring Cloud Gateway): http://localhost:8082
- **Backend** (Spring Boot + JPA/H2): http://localhost:8081

El frontend llama a `/api/*`. Vite reenvía esas peticiones al gateway, y el gateway las enruta al backend.

## Requisitos

- Node.js 18+
- Java 25 LTS (si `JAVA_HOME` apunta a otra versión, los scripts buscan un JDK 25 en el sistema)
- Maven Wrapper incluido (`mvnw` / `mvnw.cmd`); no hace falta instalar Maven

## Cómo arrancar

```bash
npm install
npm run install:all
npm run dev
```

La primera vez Maven descarga dependencias (puede tardar unos minutos).

## Cuentas

| Acceso | Datos |
| --- | --- |
| Demo | `demo@digitalbot.com` / `demo1234` |
| Registro | nombre, correo y contraseña (mín. 6) |
| Invitado | simula e pregunta a la IA; no emite token ni paga |

## Flujo

1. Inicia sesión, regístrate o entra como invitado.
2. **Simular plan** (mensual/anual, usuarios y extras).
3. **Sistema IA** verifica y emite el token.
4. En **Forma de pago** el token revela tarjeta, transferencia o billetera (simulación).
5. El cobro queda en **Lista de pagos**.

## IA (SpaceXAI / xAI)

Opcional. Copia `backend/.env.example` a `backend/.env` y pon `XAI_API_KEY`. Si no hay clave, el asistente usa respuestas locales verificadas y el token lo sigue emitiendo el backend.

## Endpoints

| Método | Ruta | Auth |
| --- | --- | --- |
| GET | `/health` (solo gateway) | no |
| GET | `/api/health` | no |
| GET | `/api/plans` | no |
| POST | `/api/auth/register` `/login` `/guest` | no |
| GET | `/api/me` | sí |
| POST | `/api/simulate` | sí |
| GET/POST | `/api/payments` | sí |
| POST | `/api/ai/chat` | sí |
| POST | `/api/tokens` `/api/tokens/verify` | sí |
