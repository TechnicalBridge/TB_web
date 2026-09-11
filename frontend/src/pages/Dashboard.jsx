import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../context/AuthContext";

export default function Dashboard() {
  const { user } = useAuth();
  const [totals, setTotals] = useState({ count: 0, paid: 0, pending: 0 });
  const [recent, setRecent] = useState([]);

  useEffect(() => {
    api("/payments")
      .then((data) => {
        setTotals(data.totals);
        setRecent(data.payments.slice(0, 4));
      })
      .catch(() => {});
  }, []);

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Hola, {user.name.split(" ")[0]}</h1>
          <p>Panel DIGITAL BOT · sistema automatizado verificado</p>
        </div>
        <span className="badge badge-ok"><span className="dot" /> {user.role === "guest" ? "Invitado" : "Sesión activa"}</span>
      </div>

      <div className="grid-3" style={{ marginBottom: 16 }}>
        <div className="card stat">
          <span>Pagos registrados</span>
          <b>{totals.count}</b>
        </div>
        <div className="card stat">
          <span>Completados</span>
          <b>{totals.paid} USD</b>
        </div>
        <div className="card stat">
          <span>Pendientes</span>
          <b>{totals.pending} USD</b>
        </div>
      </div>

      <div className="grid-2">
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Últimos pagos</h3>
          {recent.length === 0 ? (
            <div className="empty">Aún no hay pagos. Simula un plan y emite un token.</div>
          ) : (
            <table className="table">
              <thead>
                <tr>
                  <th>Referencia</th>
                  <th>Plan</th>
                  <th>Importe</th>
                  <th>Estado</th>
                </tr>
              </thead>
              <tbody>
                {recent.map((p) => (
                  <tr key={p.id}>
                    <td>{p.reference}</td>
                    <td>{p.plan?.name}</td>
                    <td>{p.amount} {p.currency}</td>
                    <td>
                      <span className={`badge ${p.status === "completado" ? "badge-ok" : "badge-wait"}`}>
                        {p.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <div style={{ marginTop: 16 }}>
            <Link className="btn btn-cyan btn-sm" to="/app/pagos">Ver lista de pagos</Link>
          </div>
        </div>
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Siguiente paso</h3>
          <p style={{ color: "var(--muted)" }}>
            1. Simula el plan. 2. La IA verifica y emite un token DBT. 3. Con el token aparece la forma de pago.
          </p>
          <p>
            Plan actual: <b>{user.plan?.name || "ninguno"}</b>
          </p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
            <Link className="btn btn-primary btn-sm" to="/app/simular">Simular plan</Link>
            <Link className="btn btn-cyan btn-sm" to="/app/ia">Abrir IA</Link>
            <Link className="btn btn-ghost btn-sm" to="/app/pago">Forma de pago</Link>
          </div>
        </div>
      </div>
    </div>
  );
}
