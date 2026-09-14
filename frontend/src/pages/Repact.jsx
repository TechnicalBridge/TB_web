import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { api, clp } from "../api";

export default function Repact() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [debt, setDebt] = useState(null);
  const [months, setMonths] = useState(12);
  const [plan, setPlan] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    api(`/debts/${id}`).then(setDebt).catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => {
    if (!id) return;
    api(`/debts/${id}/simulate?months=${months}`)
      .then((data) => setPlan(data.plan))
      .catch((err) => setError(err.message));
  }, [id, months]);

  async function confirm() {
    setBusy(true);
    setError("");
    try {
      await api(`/debts/${id}/repact`, { method: "POST", body: { months } });
      navigate("/app");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  if (!debt) return <div className="card">{error || "Cargando…"}</div>;

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Simulador de repactación</h1>
          <p>{debt.creditorName} · saldo {clp(debt.remainingAmount)}</p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/app">Volver</Link>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="grid-2">
        <div className="card">
          <label className="field">
            <span>Plazo: <b>{months} meses</b></span>
            <input
              type="range"
              min={3}
              max={24}
              value={months}
              onChange={(e) => setMonths(Number(e.target.value))}
            />
          </label>
          <div className="grid-3" style={{ marginTop: 12 }}>
            <div className="stat">
              <span>Cuota</span>
              <b>{clp(plan?.monthlyAmount)}</b>
            </div>
            <div className="stat">
              <span>Última</span>
              <b>{clp(plan?.lastAmount)}</b>
            </div>
            <div className="stat">
              <span>Total</span>
              <b>{clp(plan?.total)}</b>
            </div>
          </div>
          <button className="btn btn-primary" style={{ marginTop: 18 }} disabled={busy} onClick={confirm}>
            {busy ? "Aplicando…" : "Confirmar plan"}
          </button>
        </div>
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Tabla de cuotas</h3>
          <table className="table">
            <thead>
              <tr>
                <th>#</th>
                <th>Vence</th>
                <th>Monto</th>
              </tr>
            </thead>
            <tbody>
              {(plan?.cuotas || []).map((c) => (
                <tr key={c.number}>
                  <td>{c.number}</td>
                  <td>{c.dueDate}</td>
                  <td>{clp(c.amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
