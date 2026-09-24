# Pruebas de rendimiento

Tres escenarios, con **k6** corriendo en un contenedor. No hay que instalar nada, y como entra a
la red de Docker Compose mide el camino completo: nginx → gateway → servicio → MySQL.

| Escenario | Qué responde |
| --- | --- |
| [`capacidad.js`](capacidad.js) | Cómo lo siente una persona: cien personas a la vez, leyendo su deuda |
| [`estres.js`](estres.js) | Cuánto aguanta el sistema, y dónde empieza a doler |
| [`limite.js`](limite.js) | Que el límite de peticiones corte donde dice, y que se recargue solo |

## Antes de correrlas

La pila completa arriba, **con el límite de peticiones subido**:

```powershell
$env:RATE_GLOBAL_CAPACITY = "10000000"
docker compose --profile app up -d --wait
```

### Por qué se sube el límite

Todo el tráfico de k6 sale de **una sola IP**, y el limitador del gateway cuenta por IP. Con el
cupo de fábrica —120 por minuto— las pruebas medirían el limitador y no el sistema: dos
peticiones por segundo y el resto `429`.

> **La trampa.** La variable vale solo en esa terminal, y **cualquier** `docker compose up`
> que se haga sin ella —aunque sea para reconstruir otro servicio— recrea el gateway con el cupo
> de fábrica. Le pasó a quien escribió estas pruebas: el resultado salió con 99 % de errores en
> medio milisegundo, que parece un sistema roto y era solo el limitador haciendo su trabajo.
>
> Por eso `capacidad.js` y `estres.js` **se detienen solos** si el limitador responde aunque sea
> una vez (`limitador_intervino`), en vez de entregar un número falso.

Al terminar, el cupo vuelve a lo normal con:

```powershell
Remove-Item Env:\RATE_GLOBAL_CAPACITY
docker compose --profile app up -d gateway
```

---

## 1. Capacidad: cómo lo siente una persona

```powershell
docker compose --profile carga run --rm k6 run /carga/capacidad.js
```

Sube por escalones —5, 20, 50 y 100 usuarios simultáneos, medio minuto sostenido en cada uno—
sobre las cuatro lecturas que de verdad se usan. Cada usuario pide cuatro páginas y se queda un
segundo mirando, como haría una persona.

| Ruta | Qué cuesta |
| --- | --- |
| `GET /api/debts` | La lista del deudor: join contra la vista de saldo |
| `GET /api/debts/{id}` | El detalle de una deuda con sus cargos |
| `GET /api/debts/{id}/simulate` | El simulador de cuotas, entre 3 y 24 |
| `GET /api/analytics/summary` | El resumen de la empresa: la lectura más pesada |

**Solo lecturas, a propósito.** Un minuto de carga sostenida sobre *repactar* o *pagar* dejaría
miles de convenios basura en la base, y la prueba dejaría de poder repetirse.

La sesión del deudor se arma en el `setup()`: se pide un código por la red interna y se canjea.
La de la empresa se pide por correo y se lee del buzón de prueba, que es el mismo camino que
recorre una persona; si no se consigue, el escenario sigue sin medir el resumen y lo avisa.

### Resultado

> Medido el 23-09-2026 sobre una pila recién levantada con volúmenes vacíos · Intel Core
> i5-14400F, con 8 CPU y 10 GB asignados a Docker Desktop en Windows · límite subido a
> 10.000.000 · los números son de los **tramos sostenidos** de cada escalón, no de las rampas.

| Usuarios | Peticiones/s | p50 | p95 | p99 | Errores |
| --- | --- | --- | --- | --- | --- |
| 5 | 19,4 | 5,0 ms | 7,6 ms | 9,6 ms | 0 % |
| 20 | 78,5 | 4,1 ms | 6,2 ms | 7,6 ms | 0 % |
| 50 | 196,4 | 4,3 ms | 6,9 ms | 9,1 ms | 0 % |
| 100 | 392,5 | 4,1 ms | 7,0 ms | 9,6 ms | 0 % |

En el escalón de 100 usuarios, por ruta:

| Ruta | p95 |
| --- | --- |
| `GET /api/debts` | 5,8 ms |
| `GET /api/debts/{id}` | 6,9 ms |
| `GET /api/debts/{id}/simulate` | 5,2 ms |
| `GET /api/analytics/summary` | 8,2 ms |

**32.500 peticiones, todas `200`**, y el limitador no intervino ni una vez.

**Lectura de los números.** El rendimiento crece en línea recta con los usuarios —unas cuatro
peticiones por segundo cada uno— y el p95 no se mueve de los 6 a 8 ms. Dicho de otro modo: **a
cien personas a la vez, el sistema no se entera**. El primer escalón sale apenas más lento que
los siguientes porque ahí la JVM todavía se está calentando. Esta prueba no alcanza a mostrar
dónde se quiebra el sistema, y por eso existe la siguiente.

---

## 2. Estrés: dónde empieza a doler

```powershell
docker compose --profile carga run --rm k6 run /carga/estres.js
```

La prueba de capacidad no alcanza a mostrar el techo: con las pausas de una persona, cien
usuarios son apenas cuatrocientas peticiones por segundo, y ahí la latencia no se mueve.

Este escenario no simula a nadie. **Impone un ritmo** de peticiones por segundo, sin pausas, y lo
sube por escalones —200, 500, 1.000, 1.500 y 2.000— hasta que algo cede: la latencia se dispara,
aparecen errores, o k6 ya no alcanza a sostener el ritmo pedido porque las respuestas no vuelven a
tiempo (las **iteraciones perdidas**). Una sola ruta, la más usada —la lista de deudas—, para
que el número sea del sistema y no de una mezcla.

Tiene un freno de mano: si el p95 pasa de dos segundos, se detiene, porque el techo ya se
encontró y seguir solo recalentaría la máquina.

### Resultado

> Medido el 23-09-2026 · la misma máquina y la misma pila · límite subido a 10.000.000

| Ritmo pedido | Logrado | p50 | p95 | p99 | Errores | Perdidas |
| --- | --- | --- | --- | --- | --- | --- |
| 200/s | 200/s | 3,8 ms | 5,8 ms | 7,3 ms | 0 % | 0 |
| 500/s | 500/s | 3,1 ms | 5,4 ms | 7,8 ms | 0 % | 0 |
| 1.000/s | 999/s | 3,6 ms | 10,5 ms | 34,9 ms | 0 % | 34 |
| 1.500/s | 1.490/s | 5,7 ms | **77,2 ms** | 171,2 ms | 0 % | 308 |
| 2.000/s | **1.806/s** | 158,1 ms | **405,6 ms** | 592,8 ms | 0 % | 6.195 |

**211.577 peticiones, todas respondidas con `200`.** Holgado hasta mil por segundo; hacia las
**1.500 empieza a doler** —el p95 se multiplica por siete—; y el **techo está en unas 1.800 por
segundo**, donde ya no alcanza a sostener lo que se le pide. Y aun saturado **no falla**: se pone
lento, que es como tiene que degradar un sistema.

### Lo que encontró: nginx se quedaba sin puertos

La primera vez que corrió, esta prueba dio un resultado muy distinto: **`502` desde las 500
peticiones por segundo**, con la latencia bajísima. Un sistema saturado se pone lento *antes* de
fallar; errores rápidos significan que algo rechaza de inmediato.

Era nginx. Abría una conexión TCP **nueva** hacia el gateway por cada petición y la cerraba al
terminar, y cada conexión cerrada deja su puerto ocupado sesenta segundos. Pasadas las quinientas
por segundo se acababan los puertos, y nginx respondía `connect() failed (99: Address not
available)`. **El gateway y los servicios no registraron un solo error**: estaban bien.

El arreglo está en [`frontend/nginx.conf`](../../frontend/nginx.conf): un bloque `upstream` con
`keepalive`, para que las conexiones se reutilicen. Con eso los `502` desaparecieron y el techo
pasó de ~500 a ~1.800 peticiones por segundo.

---

## 3. Los frenos contra la fuerza bruta

Con los valores de **fábrica**, sin la variable:

```powershell
docker compose --profile carga run --rm k6 run /carga/limite.js
```

Son dos capas, y cada una responde `429` con su propio mensaje:

| Capa | Qué cuenta | Cuánto |
| --- | --- | --- |
| **Gateway** | Peticiones por IP a `/api/auth/**`, sin mirar si el código era correcto | 10 por minuto |
| **ms-auth** | **Intentos fallidos** por IP | 10 en diez minutos, y bloquea |

La que de verdad importa contra la fuerza bruta es la segunda: el cupo del gateway se recarga
cada seis segundos, así que solo, dejaría pasar un intento cada seis segundos para siempre.

La prueba manda códigos equivocados y comprueba las dos: que el gateway corte en la petición
11, que se recargue solo —un limitador que bloquea para siempre no es una protección sino una
caída—, y que la petición que logra pasar la frene `ms-auth`, porque ese origen ya falló diez
veces.

### Resultado

> Medido el 23-09-2026 · cupos de fábrica

| Comprobación | Resultado |
| --- | --- |
| El gateway corta en la petición 11 | ✓ |
| El cupo del gateway se recarga solo | ✓ |
| `ms-auth` frena por su cuenta al origen que ya falló diez veces | ✓ |

### Lo que encontró: todos eran la misma IP

La primera versión de esta prueba falló de una forma que destapó un error grave. `ms-auth`
anotaba **todos** los intentos con la IP del **gateway**: Spring Cloud Gateway borra la cabecera
`X-Forwarded-For` antes de reenviar cuando nadie le dijo en qué proxies confiar, y es una defensa
razonable, pero dejaba a `ms-auth` sin saber quién era el cliente. Como su freno cuenta por IP,
**diez códigos equivocados de cualquier persona bloqueaban el ingreso con código de todos los
deudores durante diez minutos.** Una denegación de servicio al alcance de cualquiera.

Y al rastrearlo apareció un segundo problema: el gateway le creía el primer valor de esa
cabecera a cualquiera, y nginx agregaba la IP real al final de lo que mandara el cliente. Un
atacante que enviara un valor distinto en cada intento conseguía un cupo nuevo cada vez.

Los dos se cerraron juntos: nginx **sobrescribe** la cabecera con la IP que vio, el gateway solo
le cree a los proxies de su lista (`TRUSTED_PROXIES`) y se la pasa a los servicios, y el
limitador aplica la misma regla. Está comprobado con tráfico real: mandando doce IP inventadas
el sistema igual frena en la décima, y los fallos de un cliente ya no bloquean a otro. Las
reglas tienen además sus pruebas unitarias en `gateway/src/test`.

---

## Lo que estas pruebas no dicen

- Se corren sobre **Docker Desktop en una máquina de escritorio**, con las tres bases, el bus de
  mensajes y los cinco servicios compitiendo por los mismos núcleos. Los números sirven para
  comparar entre escalones y para detectar una regresión, **no para prometer capacidad en
  producción**.
- No miden escritura. Repactar y pagar cambian estado, y medirlos con carga sostenida exigiría
  poder deshacer lo escrito.
- No miden la base con volumen real. La cartera de prueba tiene unas pocas deudas: una tabla con
  cien mil se comportaría distinto, y eso es una prueba aparte que todavía no existe.
