# Pruebas de punta a punta

Las pruebas de cada repositorio corren solas y en CI. Estas son otra cosa: levantan **los tres
sistemas a la vez** —Patrimonio, APOFYX y DataBridge— y recorren la cadena completa por HTTP, como
lo haría un usuario.

| Prueba | Qué comprueba |
| --- | --- |
| `ida.mjs` | Patrimonio entrega su cartera a APOFYX, APOFYX se la pasa a DataBridge con los montos intactos, y un pago en la oficina de Patrimonio llega como retiro a DataBridge |
| `sin-databridge.mjs` | Con DataBridge apagado, Patrimonio y APOFYX siguen funcionando; cuando DataBridge vuelve, lo pendiente se entrega solo |
| `vuelta.mjs` | Un deudor paga en DataBridge con su código de acceso y el aviso sube, firmado, hasta dejar su contrato de arriendo en $0 |
| `portal.mjs` | El portal por el proxy de Vite y el gateway, con RabbitMQ: empresa y deudor, repactación y pago en UF, chatbot, carga CSV, certificado y dashboard |

```bash
node pruebas/todas.mjs --limpiar-bases
```

`todas.mjs` las corre en orden. `portal.mjs` parte del estado que deja `vuelta.mjs`.

## Antes de correrlas

- Los tres repositorios, uno al lado del otro: `Capstone/APOFYX`, `Capstone/PatrimonioInmuebles`,
  `Capstone/TB_web`.
- Las bases arriba: `docker compose up -d` en APOFYX y `docker compose up -d mysql` en TB_web.
  `portal.mjs` levanta RabbitMQ por su cuenta.
- `JAVA_HOME` apuntando a un JDK 25.
- El entorno de APOFYX en `APOFYX/.venv`, con sus dependencias.
- Para el chatbot, un entorno en `ms-ai/.venv` con `pip install -r ms-ai/requirements.txt`, o su
  ruta en `MS_AI_PYTHON`. Sin él, esa parte se salta.
- Los puertos libres: 3995, 5173, 8081 a 8085 y 8099.

## Por qué piden `--limpiar-bases`

Para partir de cero vacían las tablas de cartera, pagos y eventos de las bases de **desarrollo**: en
DataBridge (`tb_debt`, `tb_payments`) y en APOFYX (cartera e integración). Conservan las
organizaciones, las claves, los mandatos, las campañas y el personal. Sin la opción, se niegan a
correr y explican qué harían.

Patrimonio no se toca: cada prueba lo levanta sobre una base temporal.
