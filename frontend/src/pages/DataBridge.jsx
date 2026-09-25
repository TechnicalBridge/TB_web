import { useEffect, useState } from "react";
import { resumenDeCartera } from "../api/analitica";
import { enviarCodigo as pedirCodigo, listarDeudas } from "../api/deudas";
import { dinero, ESTADO_DEUDA, fecha, rutLegible } from "../utils/formato";
import BarraEstado from "../components/BarraEstado";
import CargaCsv from "../components/CargaCsv";
import { EstadoCartera, RecuperadoPorDia } from "../components/Graficos";
import { IconoCalendario, IconoCartera, IconoCheck } from "../components/Iconos";

/**
 * El portal de la empresa que gestiona la cartera: la agencia (APOFYX) o el
 * acreedor que trabaja directo.
 *
 * La cartera llega por la API del contrato v1 desde el sistema de la empresa
 * o, para quien no tiene integracion, cargando el CSV del mismo contrato
 * aqui. El portal muestra en que va cada deuda y le hace llegar al deudor su
 * codigo de acceso.
 */
export default function DataBridge() {
  const [resumen, setResumen] = useState(null);
  const [deudas, setDeudas] = useState([]);
  const [filtro, setFiltro] = useState("todas");
  const [avisos, setAvisos] = useState({});
  const [error, setError] = useState("");

  async function refrescar() {
    const [r, d] = await Promise.all([resumenDeCartera(), listarDeudas()]);
    setResumen(r);
    setDeudas(d);
  }

  useEffect(() => {
    refrescar().catch((err) => setError(err.message));
  }, []);

  async function enviarCodigo(deuda) {
    setAvisos((a) => ({ ...a, [deuda.id]: { enviando: true } }));
    try {
      const r = await pedirCodigo(deuda.id);
      setAvisos((a) => ({ ...a, [deuda.id]: { ok: `Enviado a ${r.destino}` } }));
    } catch (err) {
      setAvisos((a) => ({ ...a, [deuda.id]: { error: err.message } }));
    }
  }

  const visibles = deudas.filter((d) => filtro === "todas" || d.estado === filtro);

  return (
    <div>
      <header className="topbar aparece">
        <div>
          <span className="eyebrow">{resumen?.organizacion || "DataBridge"}</span>
          <h1>Cartera morosa</h1>
          <p>Deudores con meses impagos, y en qué va cada uno: pendiente, en convenio o pago conciliado.</p>
        </div>
        <span className="badge badge-ok"><span className="dot" /> Contrato v1</span>
      </header>
      {error ? <div className="error">{error}</div> : null}

      <section className="grid-3 stats bloque">
        <div className="card stat aparece" style={{ "--i": 1 }}>
          <IconoCartera />
          <span>Deudas en gestión</span>
          <b>{resumen?.activas ?? "–"}</b>
          <small>{resumen ? `${resumen.enConvenio} en convenio de pago` : ""}</small>
        </div>
        <div className="card stat aparece" style={{ "--i": 2 }}>
          <IconoCheck />
          <span>Pagos conciliados</span>
          <b>{resumen?.pagadas ?? "–"}</b>
          <small>{resumen ? `${resumen.retiradas} retiradas por el acreedor` : ""}</small>
        </div>
        <div className="card stat aparece" style={{ "--i": 3 }}>
          <IconoCalendario />
          <span>Recuperado</span>
          <b className="totales">
            {(resumen?.porMoneda || []).map((m) => (
              <span key={m.moneda}>{dinero(m.recuperado, m.moneda)}</span>
            ))}
          </b>
          <small>{(resumen?.porMoneda || []).map((m) => `${m.tasaRecuperacion}% en ${m.moneda}`).join(" · ")}</small>
        </div>
      </section>

      <div className="grid-2 bloque aparece" style={{ "--i": 4, alignItems: "stretch" }}>
        <EstadoCartera resumen={resumen} />
        <RecuperadoPorDia resumen={resumen} />
      </div>

      <div className="bloque aparece" style={{ "--i": 5 }}>
        <CargaCsv onCargada={() => refrescar().catch((err) => setError(err.message))} />
      </div>

      <div className="card aparece" style={{ "--i": 6 }}>
        <div className="card-cab">
          <h3>Deudas</h3>
          <div className="filters">
            {["todas", "open", "repacted", "paid", "withdrawn"].map((f) => (
              <button key={f} type="button" className={`chip ${filtro === f ? "on" : ""}`} onClick={() => setFiltro(f)}>
                {f === "todas" ? "Todas" : ESTADO_DEUDA[f]}
              </button>
            ))}
          </div>
        </div>
        {visibles.length === 0 ? (
          <div className="empty">
            {deudas.length === 0
              ? "Todavía no llega cartera. Llega por la API del contrato (POST /api/v1/carteras) o cargando el CSV aquí arriba."
              : "No hay deudas en ese estado."}
          </div>
        ) : (
          <div className="tabla-scroll">
            <table className="table">
              <thead>
                <tr>
                  <th>Deudor</th>
                  <th>Acreedor</th>
                  <th className="num">Saldo</th>
                  <th>Estado</th>
                  <th>Acceso</th>
                </tr>
              </thead>
              <tbody>
                {visibles.map((d) => {
                  const aviso = avisos[d.id];
                  const cobrable = d.estado === "open" || d.estado === "repacted";
                  return (
                    <tr key={d.id}>
                      <td>{d.deudor}<span className="sub">{rutLegible(d.deudorRut)}</span></td>
                      <td>{d.acreedor}<span className="sub">{d.externalId} · {d.concepto}</span></td>
                      <td className="num">
                        {dinero(d.saldo, d.moneda)}
                        <span className="sub">de {dinero(d.montoOriginal, d.moneda)}</span>
                      </td>
                      <td>
                        <BarraEstado deuda={d} compacta />
                        <span className="sub">{fecha(d.actualizada)}</span>
                      </td>
                      <td>
                        {cobrable ? (
                          <button type="button" className="btn btn-soft btn-sm" disabled={aviso?.enviando}
                                  onClick={() => enviarCodigo(d)}>
                            {aviso?.enviando ? <><span className="girando" /> Enviando…</> : "Enviar código"}
                          </button>
                        ) : null}
                        {aviso?.ok ? <span className="sub">{aviso.ok}</span> : null}
                        {aviso?.error ? <span className="sub" style={{ color: "var(--danger)" }}>{aviso.error}</span> : null}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint">
          El código va al correo del deudor y no se muestra aquí: quien lo viera podría entrar en su lugar.
        </p>
      </div>
    </div>
  );
}
