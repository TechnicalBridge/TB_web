# Technical Bridge — DataBridge

[![CI](https://github.com/TechnicalBridge/TB_web/actions/workflows/ci.yml/badge.svg)](https://github.com/TechnicalBridge/TB_web/actions/workflows/ci.yml)

Plataforma de pago y repactación de deudas. Proyecto de Capstone; las empresas, las personas y
los RUT son ficticios.

**Todo el sistema se levanta con una orden** y queda en http://localhost:8080 — no hace falta
tener instalado JDK, Node ni Python, solo Docker:

```powershell
docker compose --profile app up -d --wait
```

| | |
| --- | --- |
| [1. Descripción](#1-descripción) | [2. Tecnologías](#2-tecnologías-utilizadas) · [3. Cómo ejecutarlo](#3-cómo-ejecutar-el-proyecto-localmente) · [4. Equipo](#4-integrantes-del-equipo) |
| [5. Metodología](#5-metodología-de-trabajo) | [6. Arquitectura](#6-arquitectura-de-la-solución) · [7. Modelo de datos](#7-modelo-de-datos) · [8. Diagramas UML](#8-diagramas-uml) |
| [9. Requisitos no funcionales](#9-requisitos-no-funcionales) | [10. Docker](#10-docker) · [11. Pruebas](#11-pruebas) · [12. Innovación](#12-innovación) |

---

## 1. Descripción

### Qué hace

DataBridge es donde **el deudor paga**. Entra con su RUT y un código de seis caracteres que le
llegó por correo o WhatsApp —sin cuenta, sin contraseña—, ve exactamente qué debe y a quién,
y puede pagarlo de una vez o repactarlo en 3 a 24 cuotas sin interés. Si tiene dudas, le
pregunta a un asistente que lee su deuda con su propia sesión. Cuando termina de pagar, descarga
su certificado de deuda cero.

Del otro lado, la empresa que gestiona la cartera la ve al día, carga deudas nuevas por API o
arrastrando un CSV, le envía el código al deudor y mira en un panel cuánto se ha recuperado.

### A quién va dirigido

| Quién | Qué hace acá |
| --- | --- |
| **La persona que debe** | Entra, mira, repacta y paga. Es quien usa el portal de verdad |
| **La agencia de cobranza** (APOFYX) | Entrega la cartera, envía los códigos y sigue la recuperación |
| **El acreedor** (Patrimonio Inmuebles) | No entra nunca: recibe el aviso de cada pago en su propio sistema |

### Qué problema resuelve

Cobrar deudas chicas cuesta más que la deuda. Una llamada a alguien que debe $40.000 se come el
margen, así que a esa persona nadie la llama: le mandan cartas, la mandan a DICOM y la deuda
envejece hasta que se castiga.

Y del otro lado está el problema espejo, que es el que casi nadie mira: **el deudor que sí quiere
pagar no puede**. Tiene que llamar en horario de oficina, esperar, dar sus datos y que alguien le
diga cuánto debe. DataBridge saca a la persona del medio en los dos sentidos: el deudor paga solo
a las 11 de la noche si quiere, y el acreedor se entera sin que nadie escriba un correo.

---

## 2. Tecnologías utilizadas

| Capa | Tecnología | Por qué |
| --- | --- | --- |
| **Lenguajes** | Java 25, JavaScript (ES2022), Python 3.13 | |
| **Backend** | Spring Boot 3.5 · Spring Cloud Gateway · Spring Security · Spring Data JPA | Cuatro microservicios independientes |
| **Asistente** | FastAPI + Uvicorn | El único servicio que no es de Spring: la librería de lenguaje natural vive en Python |
| **Frontend** | React 18 · React Router 7 · Vite · Zustand · Tailwind 4 · Recharts | |
| **Base de datos** | **MySQL 8.4**, una por servicio | El mismo motor que usa APOFYX, para no tener dos en el proyecto |
| **Migraciones** | Flyway, con `ddl-auto: validate` | El esquema se versiona; Hibernate no lo cambia a espaldas de nadie |
| **Mensajería** | RabbitMQ 3.13 | Lleva el aviso de pago entre servicios |
| **Autenticación** | JWT (JJWT) · códigos de un solo uso | Sin contraseñas |
| **Contenedores** | Docker · Docker Compose | Nueve contenedores, una orden |
| **Integración continua** | GitHub Actions | Corre las pruebas en cada push |
| **Servidor web** | nginx (sin privilegios) | Sirve el portal compilado y hace de proxy al gateway |
| **Correo** | Mailpit | Buzón de prueba: recibe los códigos sin mandárselos a nadie |

**Nube:** ninguna. El sistema corre entero en contenedores, así que puede desplegarse en
cualquier proveedor que acepte Docker, pero **hoy no depende de ningún servicio administrado**.
La única dependencia externa es la API del Banco Central para el valor de la UF, y es opcional.

---

## 3. Cómo ejecutar el proyecto localmente

### La forma corta: todo en Docker

Lo único que hace falta es **Docker Desktop** corriendo.

```powershell
git clone https://github.com/TechnicalBridge/TB_web.git
cd TB_web
docker compose --profile app up -d --wait
```

`--wait` devuelve el control recién cuando los nueve contenedores están **sanos**, no cuando
arrancaron. La primera vez demora unos minutos porque compila; después son segundos.

| | |
| --- | --- |
| **Portal** | http://localhost:8080 |
| Buzón de prueba (los códigos llegan acá) | http://localhost:8025 |
| RabbitMQ | http://localhost:15672 · `guest` / `guest` |
| Base de datos | `127.0.0.1:3308` · `tbridge` / `tbridge_pass` |

Para apagar: `docker compose --profile app down`. Con `-v` borra además los datos.

### Para entrar como deudor

El sistema arranca con una cartera de ejemplo. Para conseguir un código:

```powershell
$cuerpo = @{ rut = "16482337-7"; canales = @("correo"); correo = "felipe.rojas@correo.cl"
             acreedor = "Patrimonio Inmuebles"; paraQue = "CTR-2025-014" } | ConvertTo-Json
docker compose exec ms-auth curl -s -X POST http://127.0.0.1:8081/internal/codigos `
  -H "X-Internal-Key: tbridge-internal-dev" -H "Content-Type: application/json" -d $cuerpo
```

El código sirve **una sola vez** y dura 24 horas. También llega al buzón de prueba.

> Se pide desde dentro del contenedor a propósito: los endpoints internos **no** están
> publicados hacia afuera. Ver [§9](#9-requisitos-no-funcionales).

### Para programar

Con las imágenes no se programa: recompilar en cada cambio sería insoportable. Se levanta la
infraestructura en Docker y los servicios en la máquina.

Hace falta **Docker Desktop**, un **JDK 25** (no un JRE: Maven compila), **Node 22+** y
**Python 3.13+**.

```powershell
docker compose up -d          # solo MySQL, RabbitMQ y el buzón
npm install
npm run install:all
npm run dev                   # gateway, los cuatro servicios y el portal
```

El portal queda en http://localhost:5173, servido por Vite con recarga automática.

---

## 4. Integrantes del equipo

> **⚠ POR COMPLETAR ANTES DE ENTREGAR.** El equipo tiene que llenar la columna de rol.

| Integrante | Rol |
| --- | --- |
| Pedro Campos | |
| Martín Gutiérrez | |
| Flavio Henríquez | |
| Esteban Maino | |

---

## 5. Metodología de trabajo

**Kanban**, con prácticas de **DevOps** para la entrega.

El trabajo se organizó en un tablero Kanban dividido en cinco épicas —autenticación, gestión de
deudas, pagos, asistente, y reportes— con un backlog de tareas que se fueron tomando de a una.
El seguimiento tarea por tarea, con dónde quedó cada una y **en qué nos apartamos del plan
original y por qué**, está en [`docs/plan-kanban.md`](docs/plan-kanban.md).

De DevOps se tomaron tres cosas, no por moda sino porque resolvían un problema concreto:

| Práctica | Qué resuelve |
| --- | --- |
| **Integración continua** (GitHub Actions) | Las pruebas corren en cada push, en un equipo limpio. Lo que funciona "en mi máquina" no cuenta |
| **Infraestructura como código** (Docker Compose) | Levantar el sistema es una orden y no una página de instrucciones que alguien sigue mal |
| **Esquema versionado** (Flyway) | Cambiar la base es un archivo con número, revisable, no un `ALTER TABLE` que alguien corrió una vez |

---

## 6. Arquitectura de la solución

Cinco servicios detrás de una sola puerta. El navegador solo habla con el portal; el portal manda
todo lo que empiece con `/api` al **gateway**, y el gateway decide a qué servicio le toca. Ningún
servicio queda expuesto hacia afuera.

```mermaid
flowchart TD
    N["Navegador"] --> P["Portal · React servido por nginx<br/>puerto 8080"]
    P -->|"/api/*"| G["Gateway · Spring Cloud Gateway<br/>CORS · límite de peticiones · enrutamiento"]

    G -->|"/api/auth/** · /api/me"| A["ms-auth<br/>códigos de acceso y JWT"]
    G -->|"/api/debts/** · /api/analytics/**"| D["ms-debt<br/>deudas, cuotas y cartera"]
    G -->|"/api/payments/**"| Y["ms-payments<br/>cobros y UF"]
    G -->|"/api/ai/**"| I["ms-ai<br/>asistente, solo lectura"]

    A --- BA[("tb_auth")]
    D --- BD[("tb_debt")]
    Y --- BY[("tb_payments")]

    Y -.->|"pago.confirmado"| R{{"RabbitMQ"}}
    R -.-> D
    I -->|"con la sesión del deudor"| D

    AP["APOFYX"] ==>|"Cartera v1 · clave de API"| D
    D ==>|"eventos firmados con HMAC"| AP
```

### Los componentes

| Pieza | Qué hace |
| --- | --- |
| **portal** | React con Zustand y Tailwind. En producción se compila y lo sirve nginx, que además hace de proxy hacia el gateway: el navegador ve un solo origen y no hay CORS que resolver |
| **gateway** | La única puerta. CORS, límite de peticiones con Bucket4j y enrutamiento. Lo que no pasa por aquí, no entra |
| **ms-auth** | Código de acceso (RUT + 6 caracteres, un solo uso, 24 h) y enlace de respaldo por correo, que es también como entra el personal de las empresas. Emite el JWT |
| **ms-debt** | Deudas, cargos y cuotas; simulación y aceptación de planes (3 a 24 meses, sin interés); ingesta de la cartera v1 por API o CSV; eventos de vuelta a quien entregó la cartera; resumen para el dashboard y certificado PDF de deuda pagada |
| **ms-payments** | Cobros con Webpay, Mercado Pago y Khipu simulados. El monto lo decide ms-debt, nunca el navegador. En UF fija los pesos al abrir el cobro |
| **ms-ai** | Asistente de solo lectura (Python/FastAPI). Lee las deudas con la sesión del deudor, nunca con acceso propio a la base, y detecta frustración o desconfianza para ajustar el tono. Usa un LLM si hay `XAI_API_KEY`; si no, reglas |
| **MySQL 8.4** | Una base por servicio, con esquema versionado en Flyway ([`db/README.md`](db/README.md)) |
| **RabbitMQ** | Lleva el aviso de pago de ms-payments a ms-debt. Si está apagado, el mismo aviso va por HTTP; en los dos casos sale de una bandeja con reintentos, así que no se pierde |

### Comunicación entre servicios

Tres formas, y cada una está donde está por una razón:

| Entre quiénes | Cómo | Por qué así |
| --- | --- | --- |
| Navegador → servicios | HTTP por el gateway, con JWT | Una sola puerta que revisar |
| ms-payments → ms-debt | **RabbitMQ**, cola `ms-debt.pagos-confirmados` | El pago ya ocurrió: si ms-debt está caído, el aviso espera en la cola en vez de perderse |
| ms-debt → ms-auth, ms-payments → ms-debt | HTTP interno con `X-Internal-Key`, fuera del gateway | Son llamadas entre servicios, no de usuarios. El gateway no las expone |
| APOFYX ↔ DataBridge | HTTP con clave de API, y eventos firmados con HMAC-SHA256 | Son empresas distintas: ninguna entra en la base de la otra |

```
Contrato v1, de sistema a sistema (con clave de API):
  APOFYX ── POST /api/v1/carteras, /mandatos, /campanas, /suscripciones ──► ms-debt
  ms-debt ── eventos firmados (pago.confirmado, deuda.saldada, ...) ──────► APOFYX

Entre servicios (clave interna, no expuesto por el gateway):
  ms-payments ── /internal/events/pago-confirmado ──► ms-debt
  ms-debt ───── /internal/codigos ──────────────────► ms-auth
```

El contrato completo, con sus ejemplos y su esquema JSON, está en
[`docs/integracion/`](docs/integracion/README.md).

---

## 7. Modelo de datos

**Una base por servicio**, las tres en el mismo MySQL 8.4. No comparten tablas: si ms-payments
necesita saber cuánto se debe, se lo **pregunta** a ms-debt, no lo lee de su base. Eso permite
que cada servicio cambie su esquema sin romper a los demás, e impide que un error en pagos
corrompa la cartera.

El esquema lo versiona **Flyway**: cada servicio trae su `V1__esquema_inicial.sql` y arranca con
`ddl-auto: validate`, que compara las entidades con las tablas y se niega a partir si no calzan.

### `tb_debt` — la cartera

```mermaid
erDiagram
    organizations ||--o{ api_keys    : "autentica con"
    organizations ||--o{ mandates    : "otorga o recibe"
    organizations ||--o{ campaigns   : "gestiona"
    organizations ||--o{ batches     : "envía o recibe"
    organizations ||--o{ debts       : "es acreedora de"
    organizations ||--o{ subscriptions : "se suscribe a"
    campaigns     ||--o{ batches     : "agrupa"
    mandates      ||--o{ debts       : "ampara"
    batches       ||--o{ debts       : "trae"
    debtors       ||--o{ debts       : "debe"
    debts         ||--o{ debt_charges : "se compone de"
    debts         ||--o{ repactations : "se repacta en"
    debts         ||--o{ debt_events  : "registra"
    repactations  ||--o{ installments : "se paga en"
```

Trece tablas y dos vistas (`v_debt_balance`, `v_creditor_portfolio`). Lo que conviene mirar:

- **`organizations`** guarda tanto a la inmobiliaria como a la agencia de cobranza, con un `kind`
  que dice cuál es cuál. Un `mandates` las une: *esta agencia cobra por cuenta de este acreedor,
  desde esta fecha*. Sin mandato vigente, una cartera se rechaza.
- **El saldo no se guarda, se calcula.** `debts` tiene sus `debt_charges` y los pagos se anotan
  aparte; el saldo sale de la vista. Así no hay dos números que puedan discrepar.
- **`outbox`** es la bandeja de salida de los eventos, escrita en la misma transacción que el
  hecho que los provoca. Si el sistema se cae entre "cobré" y "avisé", el aviso sigue ahí.

### `tb_auth` — quién entra

```mermaid
erDiagram
    access_codes {
        string debtor_rut
        string code_hash "SHA-256(rut:codigo)"
        datetime expires_at
        int attempts "tope 5"
    }
    magic_links {
        string email
        string token_hash
        datetime expires_at "15 minutos"
    }
    staff_users {
        string email
        string org_rut
        string role "operator o admin"
    }
    access_log {
        string method "code o magic_link"
        string outcome
        string ip_hash "la IP tampoco se guarda"
    }
```

Cuatro tablas sin relaciones entre sí, y es a propósito: **no hay tabla de usuarios deudores**.
El deudor no tiene cuenta. Se guarda el hash del código, nunca el código; y en `access_log` el
hash de la IP, nunca la IP.

### `tb_payments` — la plata

```mermaid
erDiagram
    payments ||--o{ payment_events     : "deja rastro en"
    payments ||--|| debt_notifications : "avisa con"
    payments {
        string gateway "webpay, mercadopago o khipu"
        string gateway_txn_id "UNIQUE junto a gateway"
        decimal amount
        string currency "CLP o UF"
        decimal uf_value "la UF del dia, si paga en UF"
    }
    uf_values {
        date day "clave primaria"
        decimal value
    }
```

`payments` es **append-only**: un pago no se edita, se le agregan eventos. `UNIQUE (gateway,
gateway_txn_id)` impide cobrar dos veces la misma transacción aunque la pasarela repita el aviso.

---

## 8. Diagramas UML

### Casos de uso

```mermaid
flowchart LR
    DEU(("Deudor"))
    EMP(("Personal de<br/>la agencia"))
    SIS(("APOFYX<br/>otro sistema"))

    DEU --> U1["Entrar con RUT y código"]
    DEU --> U2["Ver qué debe y a quién"]
    DEU --> U3["Simular un plan de cuotas"]
    DEU --> U4["Aceptar el plan"]
    DEU --> U5["Pagar"]
    DEU --> U6["Preguntarle al asistente"]
    DEU --> U7["Descargar el certificado"]

    EMP --> U8["Ver la cartera al día"]
    EMP --> U9["Cargar cartera por CSV"]
    EMP --> U10["Enviarle el código al deudor"]
    EMP --> U11["Ver el dashboard"]

    SIS --> U12["Entregar cartera por API"]
    SIS --> U13["Registrar mandato y campaña"]
    SIS --> U14["Suscribirse a los eventos"]
```

El certificado (`U7`) solo se emite si la deuda está **pagada**: una deuda retirada por el
acreedor también queda en saldo cero, y certificar eso sería decir algo falso.

### Secuencia: el pago, que es la funcionalidad principal

```mermaid
sequenceDiagram
    actor D as Deudor
    participant P as Portal
    participant G as Gateway
    participant A as ms-auth
    participant M as ms-debt
    participant Y as ms-payments
    participant R as RabbitMQ
    participant X as APOFYX

    D->>P: RUT + código de 6 caracteres
    P->>G: POST /api/auth/acceso
    G->>A: (límite de peticiones por IP)
    A->>A: compara SHA-256(rut:código), marca usado
    A-->>D: JWT con el RUT

    D->>P: "Pagar"
    P->>G: POST /api/payments/checkout
    G->>Y: con el JWT
    Y->>M: GET /internal/debts/{id}
    M-->>Y: monto y RUT del dueño
    Note over Y: El monto lo decide ms-debt.<br/>Si es UF, fija los pesos ahora,<br/>con la UF del día en Chile.
    Y-->>D: pasarela

    D->>Y: paga
    Y->>Y: registra el pago (append-only)
    Y->>R: pago.confirmado
    R->>M: la cola entrega
    M->>M: abona, y si queda en cero: deuda.saldada
    M->>X: evento firmado con HMAC
    X-->>D: su arriendo queda en $0 en Patrimonio
```

Lo que este diagrama muestra y conviene notar: **el navegador nunca dice cuánto hay que pagar**.
Lo pregunta ms-payments a ms-debt. Y el aviso de vuelta no viaja por el navegador tampoco.

### Componentes

El diagrama de componentes es el de [§6](#6-arquitectura-de-la-solución): cada servicio es un
componente con su interfaz HTTP, su base propia y sus dependencias dibujadas.

---

## 9. Requisitos no funcionales

| Requisito | Cómo se cumple | Dónde está |
| --- | --- | --- |
| **Seguridad** · autenticación | El deudor entra con RUT + 6 caracteres, sin cuenta ni contraseña. Un solo uso, 24 h, 5 intentos. El alfabeto excluye `0 O 1 I L` porque el código se dicta por teléfono | `ms-auth/AuthService` |
| **Seguridad** · sesión | El JWT dura **15 minutos** y vive en la memoria de la pestaña, no en `localStorage`. Lo que mantiene a alguien adentro es una llave de renovación de 256 bits en una cookie `HttpOnly`, `SameSite=Strict` y limitada a `/api/auth`, que ningún script de la página puede leer. Cada uso la cambia por otra; cerrar sesión la revoca **en el servidor**; y si una llave ya usada vuelve a aparecer —alguien la copió— se revoca toda la sesión | `ms-auth/SessionService`, `V2__sesiones.sql` |
| **Seguridad** · origen | Cada freno cuenta por IP, así que la IP no se puede inventar: nginx sobrescribe `X-Forwarded-For` con la que vio, y el gateway solo le cree a sus proxies de confianza (`TRUSTED_PROXIES`). CORS acepta solo los orígenes del portal (`CORS_ORIGINS`), no `*` | `frontend/nginx.conf`, `gateway/ClienteReal` |
| **Seguridad** · secretos | Solo se guardan hashes: `SHA-256(rut:código)`, del token del enlace, de la clave de API y de la IP. Nada reversible | `V1__esquema_inicial.sql` |
| **Seguridad** · enumeración | "No hay código" y "código incorrecto" responden **lo mismo**, para que nadie pueda averiguar qué RUT tienen deuda | `AuthService.entrarConCodigo` |
| **Seguridad** · autorización | Cada sesión se identifica por RUT. Un deudor ve y paga solo lo suyo; una agencia ve solo la cartera que le corresponde por mandato | `DebtService.acreedorDe` |
| **Seguridad** · integridad | Los eventos van firmados con HMAC-SHA256, caducan a los 5 minutos y se descartan si llegan repetidos | `docs/integracion/README.md` §8 |
| **Seguridad** · superficie | De la aplicación, **solo el portal publica un puerto**. Comprobado: `curl` a 8081, 8083, 8084 y 8085 desde la máquina no obtiene respuesta. MySQL, RabbitMQ y el buzón sí publican, **a propósito**, para revisarlos en desarrollo; en un despliegue real esas tres líneas `ports:` se borran | `docker-compose.yml` |
| **Seguridad** · contenedores | Ninguna imagen corre como root: los servicios usan el usuario `10001` y el portal la nginx sin privilegios, que escucha en el 8080 porque un proceso sin privilegios no puede tomar el 80. Las imágenes de Java llevan solo el JRE, sin compilador ni código fuente | `Dockerfile` |
| **Rendimiento** · medido | Con 100 personas a la vez, **p95 de 7 ms y cero errores** en 32.500 peticiones. En estrés, holgado hasta 1.000 peticiones por segundo, empieza a doler hacia las 1.500 y **toca techo en unas 1.800**, donde se pone lento pero sigue sin fallar. Detalle y máquina en [`pruebas/carga/`](pruebas/carga/README.md) | `pruebas/carga/` |
| **Rendimiento** · límite de peticiones | Dos capas: el gateway deja 10 por minuto en `/api/auth/**` y 120 globales por IP, y `ms-auth` bloquea diez minutos al origen que falla diez códigos. **Medido:** el gateway corta en la petición 11, se recarga solo, y lo que pasa lo frena `ms-auth` | `gateway/RateLimitFilter`, `AuthService` |
| **Rendimiento** · consultas | Trece índices en `tb_debt` para los caminos que se usan: `ix_debt_creditor_status` (la cartera de un acreedor), `ix_debt_debtor` (lo que debe una persona), `ix_batch_creditor`. Los `UNIQUE` hacen doble trabajo: `uq_payment_gateway` evita cobrar dos veces la misma transacción y además es el índice con que se busca | `V1__esquema_inicial.sql` |
| **Rendimiento** · memoria | La JVM lee el límite del contenedor (`MaxRAMPercentage=75`), no el de la máquina, y cada servicio tiene su tope declarado | `Dockerfile` |
| **Escalabilidad** | Ningún servicio guarda sesión: la identidad viaja en el JWT, así que `docker compose up --scale gateway=3` funciona sin más. Cada servicio tiene su base y el trabajo pesado va por colas. **Límite conocido:** ms-debt y ms-payments tienen tareas programadas (`EventDispatcher`, `CampanaAvanceService`, `NotificationDispatcher`, `UfLoader`) que correrían en cada copia y harían el trabajo dos veces; replicarlos exige coordinarlas primero. Replicables hoy: gateway, ms-ai y portal | |
| **Disponibilidad** | Los nueve contenedores declaran `healthcheck` y ninguno arranca antes que aquel del que depende. `restart: unless-stopped` los repone si se caen | `docker-compose.yml` |
| **Disponibilidad** · entrega | Todo lo que sale hacia otro sistema pasa por una bandeja con reintentos (1 min, 5 min, 30 min, 2 h, 6 h, 24 h). Si DataBridge está caído, APOFYX sigue recibiendo carteras y lo pendiente se entrega solo cuando vuelve — y hay una prueba que lo demuestra | `outbox`, `pruebas/sin-databridge.mjs` |
| **Portabilidad** | Una orden levanta el sistema entero en cualquier máquina con Docker, sin instalar JDK, Node ni Python | `docker-compose.yml` |
| **Mantenibilidad** | `ddl-auto: validate` se niega a arrancar si las entidades y las tablas no calzan; Flyway versiona cada cambio de esquema | `application.yml` |

> **Límites conocidos.** El gateway guarda un contador por cada IP que ve y no lo olvida nunca:
> con clientes reales eso es poco, pero en un despliegue largo convendría que expiraran. Y las
> pruebas de carga miden lectura sobre una cartera pequeña, en una sola máquina: no dicen cómo se
> comporta la escritura ni una tabla con cien mil deudas.

---

## 10. Docker

### Qué se construye

| Imagen | Con qué | Tamaño |
| --- | --- | --- |
| `tbridge/gateway`, `tbridge/ms-auth`, `tbridge/ms-debt`, `tbridge/ms-payments` | [`Dockerfile`](Dockerfile), un solo archivo para los cuatro con `--build-arg MODULO=` | 563 – 616 MB |
| `tbridge/ms-ai` | [`ms-ai/Dockerfile`](ms-ai/Dockerfile) | 280 MB |
| `tbridge/portal` | [`frontend/Dockerfile`](frontend/Dockerfile), compila con Node y sirve con nginx | 83 MB |

Los cuatro servicios de Java salen de **un solo Dockerfile**: se construyen igual y solo cambian
en qué módulo empaquetan. Son **dos etapas** —la primera compila con Maven y el JDK, la segunda
se queda solo con el JRE y el `.jar`—, así que ni el código fuente ni el compilador viajan a la
imagen final. Y como la etapa que compila no depende del módulo, Docker la reutiliza: el proyecto
se compila **una vez** para los cuatro.

El [`docker-compose.yml`](docker-compose.yml) orquesta los nueve contenedores con tres perfiles:
sin perfil levanta solo la infraestructura, y `--profile app` levanta el sistema entero.

### Variables de entorno

Todas tienen un valor por omisión de desarrollo, así que el sistema levanta sin configurar nada.
Para cambiarlas, un archivo `.env` al lado del `docker-compose.yml`.

| Variable | Por omisión | Para qué |
| --- | --- | --- |
| `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD` | `tbridge` / `tbridge_pass` / `rootpass` | La base |
| `JWT_SECRET` | `tbridge-dev-secret-change-me-32chars` | Firma los JWT. **El mismo en ms-auth, ms-debt y ms-payments**, o unos no podrán verificar lo que firmó otro. Al menos 32 bytes: con menos, los servicios no arrancan |
| `INTERNAL_KEY` | `tbridge-internal-dev` | Autentica las llamadas entre servicios |
| `JWT_TTL_MINUTES` | `15` | Cuánto dura el JWT. Corto a propósito: no se puede revocar |
| `REFRESH_TTL_HOURS` | `168` | Cuánto dura una sesión desde que se entra. Renovar no la alarga |
| `COOKIE_SECURE` | `false` | **Encender detrás de HTTPS.** Apagado porque todo esto se sirve por HTTP, y una cookie `Secure` sobre HTTP el navegador la descarta sin avisar |
| `CORS_ORIGINS` | los del portal, en `PORTAL_PORT` | Qué orígenes pueden hacer peticiones con credenciales al gateway |
| `TRUSTED_PROXIES` | local y redes de Docker | En qué proxies confía el gateway para saber la IP del cliente. Si Docker creara la red fuera de `172.16–31`, hay que ajustarlo |
| `WEBHOOK_SECRET` | `tbridge-webhook-dev` | Verifica los avisos de las pasarelas |
| `PORTAL_PORT` | `8080` | Dónde queda el portal |
| `PUBLIC_URL` | `http://localhost:8080` | La dirección que se escribe en los correos |
| `BCENTRAL_USER`, `BCENTRAL_PASS` | vacías | La UF del Banco Central. Sin ellas, se carga a mano |
| `XAI_API_KEY` | vacía | El LLM del asistente. Sin ella, responde con reglas |
| `RATE_AUTH_CAPACITY`, `RATE_GLOBAL_CAPACITY` | `10` / `120` | Peticiones por minuto |

**Los valores por omisión son de desarrollo y están escritos en un archivo público: no sirven
para nada que no sea una demostración.**

---

## 11. Pruebas

```powershell
.\mvnw.cmd test                                  # Java: common, gateway y los tres servicios
cd ms-ai ; python -m unittest discover tests     # el asistente
```

| Tipo | Qué cubre | Dónde |
| --- | --- | --- |
| **Unitarias** | Once clases en Java —57 pruebas— y un archivo en Python, **sin base de datos**, para que corran en segundos: repactación (3 a 24 cuotas, redondeo por moneda), el JWT, el código de acceso, la firma de los webhooks, la respuesta del Banco Central y el analizador de sentimiento. Una de ellas convierte la plantilla CSV del contrato y comprueba que da exactamente las mismas deudas que el ejemplo JSON: los dos formatos son un solo contrato | `*/src/test/java`, `ms-ai/tests` |
| **De integración** | Las de punta a punta: levantan **los tres sistemas y sus bases a la vez** y recorren la cadena por HTTP, como lo haría una persona. Cuatro recorridos: la ida, la ida con DataBridge apagado, la vuelta del pago y el portal completo. **71 comprobaciones** | [`pruebas/`](pruebas/README.md) |
| **De seguridad** | En cada push, **CodeQL** (análisis estático de Java, JavaScript y Python), una auditoría de dependencias que rompe el build ante una vulnerabilidad alta, y Dependabot. Pruebas unitarias de las reglas de sesión —rotación, robo, revocación— y de a qué IP se le cree. Y con tráfico real: los dos frenos contra fuerza bruta, que una IP inventada no da un cupo nuevo, y que los puertos de los servicios no responden desde afuera | `.github/workflows/`, `pruebas/carga/limite.js` |
| **De rendimiento** | Con **k6**: cómo lo siente una persona (100 usuarios, p95 de 7 ms) y dónde está el techo (unas 1.800 peticiones por segundo). La prueba de estrés encontró que nginx se quedaba sin puertos a las 500 por segundo; ya está arreglado | [`pruebas/carga/`](pruebas/carga/README.md) |

Las unitarias corren solas en **GitHub Actions** con cada push. Las de punta a punta necesitan
los tres repositorios en la misma máquina, así que se corren a mano.

---

## 12. Innovación

**¿Qué problema resuelve?** Está en [§1](#qué-problema-resuelve): cobrar deudas chicas cuesta más
que la deuda, y al mismo tiempo el deudor que quiere pagar se topa con un horario de oficina.

**¿Qué hace diferente a la solución?** Tres cosas:

1. **Se entra sin cuenta.** El deudor no crea un usuario ni inventa una contraseña: escribe su
   RUT y el código de seis caracteres que le llegó. Una cuenta más es una razón más para no
   pagar, y una base de contraseñas más que se puede filtrar.
2. **Tres empresas, un contrato.** La inmobiliaria, la agencia de cobranza y la plataforma de
   pago son sistemas independientes que se hablan por un contrato versionado, no por una base
   compartida. Sumar un cliente nuevo es escribir su adaptador; nadie toca el código de los
   otros. Y si uno se cae, los demás siguen funcionando — probado, no supuesto.
3. **La deuda vuelve.** Lo difícil de la cobranza tercerizada no es cobrar: es que el acreedor se
   entere. Acá el pago viaja de vuelta, firmado, hasta dejar el contrato de arriendo en $0 sin
   que nadie escriba un correo. Eso es lo que evita que le sigan cobrando a alguien que ya pagó.

**¿Qué valor agrega?** Al acreedor, cobranza de tickets bajos que antes no era rentable, y
certeza de que su cartera está al día. Al deudor, poder pagar a las 11 de la noche, ver el
detalle de lo que debe y repactar sin interés. A la agencia, una cartera que se actualiza sola.

---

## Recorrido de demostración

1. **La cartera llega por API o por archivo.** APOFYX la entrega con `POST /api/v1/carteras`;
   quien no tiene integración arrastra el CSV del contrato al portal. Las dos entradas usan la
   misma ingesta: mismas validaciones, aceptación parcial e idempotencia.
2. **La empresa entra.** En *Soy una empresa*, `camila.reyes@apofyx.cl` pide su enlace; llega al
   buzón de prueba. Ve la cartera que APOFYX entregó, aunque el acreedor sea Patrimonio.
3. **Le envía el código al deudor.** El botón *Enviar código* lo manda al correo del deudor. La
   pantalla no lo muestra: quien lo viera podría entrar en su lugar.
4. **El deudor entra** con su RUT y ese código, simula un plan, lo acepta y paga.
5. **El pago vuelve por la cadena**: ms-payments avisa a ms-debt, ms-debt emite los eventos, y
   APOFYX y Patrimonio los reciben.

**Pagos en UF.** Usan la UF de ese día exacto, del **Banco Central**. Como la publica con un mes
de adelanto, ms-payments la carga al arrancar y cada mañana a las 9:30. Sin credenciales, se
carga a mano:

```powershell
$cuerpo = @{ dia = "2026-09-23"; valor = "39876.54" } | ConvertTo-Json
Invoke-RestMethod -Method Post -Uri "http://localhost:8084/internal/uf" `
  -Headers @{ "X-Internal-Key" = "tbridge-internal-dev" } `
  -ContentType "application/json" -Body $cuerpo
```

---

Este repositorio es una de tres piezas:
[**Patrimonio Inmuebles**](https://github.com/TechnicalBridge/patrimonioinmuebles) →
[**APOFYX**](https://github.com/TechnicalBridge/APOFYX) → **DataBridge**.
