import { Link } from "react-router-dom";
import CargaCsv, { COLUMNAS } from "../components/CargaCsv";

/** Cargar cartera por archivo, con la guia del formato al lado. */
export default function Cargar() {
  return (
    <div>
      <header className="topbar aparece">
        <div>
          <h1>Cargar cartera</h1>
          <p>Para entregar deudas sin integración: el mismo contrato que la API, en un archivo.</p>
        </div>
      </header>

      <div className="grid-2">
        <div className="aparece" style={{ "--i": 1 }}>
          <CargaCsv />
        </div>

        <div>
          <div className="card aparece" style={{ "--i": 2, marginBottom: 16 }}>
            <h3>El formato</h3>
            <p style={{ margin: 0 }}>
              Separado por punto y coma, con una fila por cargo: una deuda de tres meses son tres filas con el
              mismo <code>deuda_id</code>. Las fechas van como 2026-09-05 y los montos en UF con coma decimal.
            </p>
            <div className="columnas-csv">
              {COLUMNAS.map((c) => <code key={c}>{c}</code>)}
            </div>
            <p className="hint">
              Para sacar una deuda de la cobranza (el deudor pagó en tu oficina), manda una fila con acción
              <code> retirar</code> y el motivo.
            </p>
          </div>

          <div className="card aparece" style={{ "--i": 3 }}>
            <h3>¿Tu sistema puede enviarla solo?</h3>
            <p style={{ margin: 0 }}>
              Con una clave de <Link className="link-btn" to="/databridge/claves">Claves de API</Link>, tu sistema
              entrega la cartera sin que nadie suba archivos:
            </p>
            <pre className="codigo">{`POST /api/v1/carteras
Authorization: Bearer tbk_...`}</pre>
          </div>
        </div>
      </div>
    </div>
  );
}
