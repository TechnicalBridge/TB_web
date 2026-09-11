import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { api, clp } from "../api";

const GATEWAYS = [
  { id: "MERCADOPAGO", label: "Mercado Pago" },
  { id: "KHIPU", label: "Khipu" },
  { id: "WEBPAY", label: "Webpay" },
];

export default function Pay() {
  const { id } = useParams();
  const [debt, setDebt] = useState(null);
  const [gateway, setGateway] = useState("MERCADOPAGO");
  const [mode, setMode] = useState("cuota");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [payment, setPayment] = useState(null);

  async function load() {
    const data = await api(`/debts/${id}`);
    setDebt(data);
  }

  useEffect(() => {
    load().catch((err) => setError(err.message));
  }, [id]);

  useEffect(() => {
    if (!payment || payment.status === "PAGADO") return;
    const t = setInterval(async () => {
      try {
        const p = await api(`/payments/${payment.id}`);
        setPayment(p);
        if (p.status === "PAGADO") load();
      } catch {
        /* ignore */
      }
    }, 2000);
    return () => clearInterval(t);
  }, [payment?.id, payment?.status]);

  const next = (debt?.cuotas || []).find((c) => c.status === "PENDIENTE");
  const amount = mode === "saldo" ? Number(debt?.remainingAmount || 0) : Number(next?.amount || debt?.remainingAmount || 0);

  async function startPay() {
    setBusy(true);
    setError("");
    try {
      const data = await api("/payments/checkout", {
        method: "POST",
        body: {
          debtId: id,
          installmentId: mode === "cuota" ? next?.id : null,
          amount,
          gateway,
        },
      });
      setPayment(data);
      window.open(data.checkoutUrl, "_blank", "noopener,width=480,height=720");
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
          <h1>Pagar</h1>
          <p>{debt.creditorName} · {debt.description}</p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/app">Volver</Link>
      </div>
      {error ? <div className="error">{error}</div> : null}
      {payment?.status === "PAGADO" ? (
        <div className="card success">
          <h2>Pago acreditado</h2>
          <p>MS-Payments publicó <code>pago_exitoso</code> y MS-Debt actualizó el saldo.</p>
          <Link className="btn btn-primary" to="/app">Ver mis deudas</Link>
        </div>
      ) : (
        <div className="grid-2">
          <div className="card">
            <div className="toggle">
              <button type="button" className={mode === "cuota" ? "on" : ""} onClick={() => setMode("cuota")}>
                Próxima cuota
              </button>
              <button type="button" className={mode === "saldo" ? "on" : ""} onClick={() => setMode("saldo")}>
                Saldo total
              </button>
            </div>
            <p style={{ fontSize: 28, fontFamily: "var(--display)", margin: "8px 0 16px" }}>{clp(amount)}</p>
            <div className="methods">
              {GATEWAYS.map((g) => (
                <button
                  key={g.id}
                  type="button"
                  className={`method ${gateway === g.id ? "on" : ""}`}
                  onClick={() => setGateway(g.id)}
                >
                  {g.label}
                </button>
              ))}
            </div>
            <button className="btn btn-primary" disabled={busy || amount <= 0} onClick={startPay}>
              {busy ? "Abriendo…" : "Pagar"}
            </button>
            {payment?.status === "PENDIENTE" ? (
              <p className="hint">Esperando confirmación de la pasarela (polling)…</p>
            ) : null}
          </div>
          <div className="card">
            <h3 style={{ marginTop: 0 }}>Cuotas</h3>
            <table className="table">
              <thead>
                <tr>
                  <th>#</th>
                  <th>Vence</th>
                  <th>Monto</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {(debt.cuotas || []).map((c) => (
                  <tr key={c.id}>
                    <td>{c.number}</td>
                    <td>{c.dueDate}</td>
                    <td>{clp(c.amount)}</td>
                    <td>
                      <span className={`badge ${c.status === "PAGADA" ? "badge-ok" : "badge-wait"}`}>{c.status}</span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}
