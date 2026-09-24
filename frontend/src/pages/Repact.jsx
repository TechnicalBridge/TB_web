import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { obtenerDeuda, repactar, simularPlan } from "../api/deudas";
import { dinero, fecha } from "../utils/formato";

/**
 * Simular un plan de cuotas y aceptarlo.
 *
 * Simular no compromete nada: el deudor mueve el plazo y ve la cuota. Recien
 * al confirmar se crea el plan, y la empresa se entera por un evento.
 */
export default function Repact() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [deuda, setDeuda] = useState(null);
  const [meses, setMeses] = useState(6);
  const [plan, setPlan] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    obtenerDeuda(id).then(setDeuda).catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => {
    simularPlan(id, meses)
      .then((nuevo) => {
        setPlan(nuevo);
        setError("");
      })
      .catch((err) => setError(err.message));
  }, [id, meses]);

  async function confirmar() {
    setBusy(true);
    setError("");
    try {
      await repactar(id, meses);
      navigate(`/app/pagar/${id}`);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (!deuda) return <div className="card">{error || "Cargando…"}</div>;
  const moneda = deuda.moneda;

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Pagar en cuotas</h1>
          <p>{deuda.acreedor} · {deuda.concepto} · saldo {dinero(deuda.saldo, moneda)}</p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/app">Volver</Link>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="grid-2">
        <div className="card">
          <label className="field">
            <span>Plazo: <b>{meses} meses</b></span>
            <input type="range" min={3} max={24} value={meses}
                   onChange={(e) => setMeses(Number(e.target.value))} />
          </label>
          <div className="grid-3" style={{ marginTop: 12 }}>
            <div className="stat">
              <span>Cuota</span>
              <b>{dinero(plan?.monthlyAmount, moneda)}</b>
            </div>
            <div className="stat">
              <span>Última</span>
              <b>{dinero(plan?.lastAmount, moneda)}</b>
            </div>
            <div className="stat">
              <span>Total</span>
              <b>{dinero(plan?.total, moneda)}</b>
            </div>
          </div>
          <p className="hint" style={{ textAlign: "left" }}>
            Sin intereses: el total es lo que debes hoy. La última cuota absorbe el redondeo.
            {moneda === "UF" ? " En UF, cada cuota se paga al valor de la UF del día en que pagas." : ""}
          </p>
          <button className="btn btn-primary" style={{ marginTop: 8 }} disabled={busy || !plan} onClick={confirmar}>
            {busy ? "Aceptando…" : `Aceptar ${meses} cuotas`}
          </button>
        </div>
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Calendario</h3>
          <table className="table">
            <thead>
              <tr><th>#</th><th>Vence</th><th>Monto</th></tr>
            </thead>
            <tbody>
              {(plan?.cuotas || []).map((c) => (
                <tr key={c.number}>
                  <td>{c.number}</td>
                  <td>{fecha(c.dueDate)}</td>
                  <td>{dinero(c.amount, moneda)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
