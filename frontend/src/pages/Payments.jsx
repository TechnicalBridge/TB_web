import { useEffect, useMemo, useState } from "react";
import { api } from "../api";

const labels = {
  tarjeta: "Tarjeta",
  transferencia: "Transferencia",
  billetera: "Billetera",
};

export default function Payments() {
  const [payments, setPayments] = useState([]);
  const [totals, setTotals] = useState({ count: 0, paid: 0, pending: 0 });
  const [filter, setFilter] = useState("todos");
  const [error, setError] = useState("");

  useEffect(() => {
    api("/payments")
      .then((data) => {
        setPayments(data.payments);
        setTotals(data.totals);
      })
      .catch((err) => setError(err.message));
  }, []);

  const visible = useMemo(() => {
    if (filter === "todos") return payments;
    return payments.filter((p) => p.status === filter);
  }, [payments, filter]);

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Lista de pagos</h1>
          <p>{totals.count} movimientos · {totals.paid} USD cobrados</p>
        </div>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="filters">
        {["todos", "completado", "pendiente"].map((f) => (
          <button key={f} className={`chip ${filter === f ? "on" : ""}`} onClick={() => setFilter(f)}>
            {f}
          </button>
        ))}
      </div>
      <div className="card">
        {visible.length === 0 ? (
          <div className="empty">No hay pagos en este filtro.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Referencia</th>
                <th>Plan</th>
                <th>Método</th>
                <th>Importe</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((p) => (
                <tr key={p.id}>
                  <td>{new Date(p.createdAt).toLocaleDateString("es")}</td>
                  <td>{p.reference}</td>
                  <td>{p.plan?.name || p.planId}</td>
                  <td>{labels[p.method] || p.method}{p.last4 ? ` · ${p.last4}` : ""}</td>
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
      </div>
    </div>
  );
}
