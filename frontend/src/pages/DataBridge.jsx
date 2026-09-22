import { useEffect, useState } from "react";
import { api, dinero, ESTADO_DEUDA, fecha, rutLegible } from "../api";
import CargaCsv from "../components/CargaCsv";
import { EstadoCartera, RecuperadoPorDia } from "../components/Graficos";
import { useAuth } from "../store/authStore";

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
  const { user } = useAuth();
  const [resumen, setResumen] = useState(null);
  const [deudas, setDeudas] = useState([]);
  const [filtro, setFiltro] = useState("todas");
  const [avisos, setAvisos] = useState({});
  const [error, setError] = useState("");

  async function refrescar() {
    const [r, d] = await Promise.all([api("/analytics/summary"), api("/debts")]);
    setResumen(r);
    setDeudas(d.debts || []);
  }

  useEffect(() => {
    refrescar().catch((err) => setError(err.message));
  }, []);

  async function enviarCodigo(deuda) {
    setAvisos((a) => ({ ...a, [deuda.id]: { enviando: true } }));
    try {
      const r = await api(`/debts/${deuda.id}/codigo`, { method: "POST" });
      setAvisos((a) => ({ ...a, [deuda.id]: { ok: `Enviado a ${r.destino}` } }));
    } catch (err) {
      setAvisos((a) => ({ ...a, [deuda.id]: { error: err.message } }));
    }
  }

  const visibles = deudas.filter((d) => filtro === "todas" || d.estado === filtro);

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Cartera</h1>
          <p>{resumen?.organizacion || user?.nombre} · {user?.nombre}</p>
        </div>
        <span className="badge badge-ok"><span className="dot" /> Contrato v1</span>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="grid-3" style={{ marginBottom: 16 }}>
        <div className="card stat">
          <span>Deudas en gestión</span>
          <b>{resumen?.activas ?? "–"}</b>
          <small className="hint" style={{ margin: 0, textAlign: "left" }}>
            {resumen ? `${resumen.enConvenio} en convenio de pago` : ""}
          </small>
        </div>
        <div className="card stat">
          <span>Pagadas</span>
          <b>{resumen?.pagadas ?? "–"}</b>
          <small className="hint" style={{ margin: 0, textAlign: "left" }}>
            {resumen ? `${resumen.retiradas} retiradas por el acreedor` : ""}
          </small>
        </div>
        <div className="card stat">
          <span>Recuperado</span>
          <b className="totales">
            {(resumen?.porMoneda || []).map((m) => (
              <span key={m.moneda}>{dinero(m.recuperado, m.moneda)}</span>
            ))}
          </b>
          <small className="hint" style={{ margin: 0, textAlign: "left" }}>
            {(resumen?.porMoneda || []).map((m) => `${m.tasaRecuperacion}% en ${m.moneda}`).join(" · ")}
          </small>
        </div>
      </div>

      <div className="grid-2" style={{ marginBottom: 16 }}>
        <EstadoCartera resumen={resumen} />
        <RecuperadoPorDia resumen={resumen} />
      </div>

      <div style={{ marginBottom: 16 }}>
        <CargaCsv onCargada={() => refrescar().catch((err) => setError(err.message))} />
      </div>

      <div className="card">
        <div className="topbar" style={{ marginBottom: 8 }}>
          <h3 style={{ margin: 0 }}>Deudas</h3>
          <div className="filters" style={{ marginBottom: 0 }}>
            {["todas", "open", "repacted", "paid", "withdrawn"].map((f) => (
              <button key={f} type="button" className={`chip ${filtro === f ? "on" : ""}`} onClick={() => setFiltro(f)}>
                {f === "todas" ? "Todas" : ESTADO_DEUDA[f].texto}
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
          <table className="table">
            <thead>
              <tr>
                <th>Deudor</th>
                <th>Acreedor</th>
                <th>Saldo</th>
                <th>Estado</th>
                <th>Acceso</th>
              </tr>
            </thead>
            <tbody>
              {visibles.map((d) => {
                const estado = ESTADO_DEUDA[d.estado] || { texto: d.estado, clase: "badge-muted" };
                const aviso = avisos[d.id];
                const cobrable = d.estado === "open" || d.estado === "repacted";
                return (
                  <tr key={d.id}>
                    <td>{d.deudor}<span className="sub">{rutLegible(d.deudorRut)}</span></td>
                    <td>{d.acreedor}<span className="sub">{d.externalId} · {d.concepto}</span></td>
                    <td>
                      {dinero(d.saldo, d.moneda)}
                      <span className="sub">de {dinero(d.montoOriginal, d.moneda)}</span>
                    </td>
                    <td>
                      <span className={`badge ${estado.clase}`}>{estado.texto}</span>
                      <span className="sub">{fecha(d.actualizada)}</span>
                    </td>
                    <td>
                      {cobrable ? (
                        <button type="button" className="btn btn-cyan btn-sm" disabled={aviso?.enviando}
                                onClick={() => enviarCodigo(d)}>
                          {aviso?.enviando ? "Enviando…" : "Enviar código"}
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
        )}
        <p className="hint" style={{ textAlign: "left" }}>
          El código va al correo del deudor y no se muestra aquí: quien lo viera podría entrar en su lugar.
        </p>
      </div>
    </div>
  );
}
