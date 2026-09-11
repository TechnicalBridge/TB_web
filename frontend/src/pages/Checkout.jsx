import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../context/AuthContext";

export default function Checkout() {
  const { user, setUser } = useAuth();
  const [code, setCode] = useState("");
  const [verified, setVerified] = useState(null);
  const [method, setMethod] = useState("tarjeta");
  const [cardNumber, setCardNumber] = useState("");
  const [holder, setHolder] = useState(user?.name || "");
  const [expiry, setExpiry] = useState("");
  const [cvv, setCvv] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(null);

  useEffect(() => {
    const saved = sessionStorage.getItem("dbt_pay_token");
    if (saved) setCode(saved);
  }, []);

  async function verify(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setDone(null);
    try {
      const data = await api("/tokens/verify", { method: "POST", body: { code } });
      setVerified(data);
    } catch (err) {
      setVerified(null);
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function pay(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const data = await api("/payments", {
        method: "POST",
        body: {
          code,
          method,
          cardNumber,
          holder,
          expiry,
          cvv,
        },
      });
      setDone(data.payment);
      setUser(data.user);
      sessionStorage.removeItem("dbt_pay_token");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Forma de pago</h1>
          <p>Oculta hasta que el token DBT sea verificado.</p>
        </div>
        <span className="badge badge-wait"><span className="dot" /> Simulación segura</span>
      </div>
      {error ? <div className="error">{error}</div> : null}

      {done ? (
        <div className="card success">
          <span className="badge badge-ok"><span className="dot" /> Pago verificado</span>
          <h2>Cobro simulado completado</h2>
          <p>{done.amount} {done.currency} · {done.plan?.name} · {done.reference}</p>
          <Link className="btn btn-primary btn-sm" to="/app/pagos">Ver en lista de pagos</Link>
        </div>
      ) : (
        <div className="grid-2">
          <div className="card">
            <h3 style={{ marginTop: 0 }}>1. Token de verificación</h3>
            <form onSubmit={verify}>
              <div className="field">
                <label>Token DBT</label>
                <input
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  placeholder="DBT-XXXX-XXXX-XXXX"
                  required
                />
              </div>
              <button className="btn btn-primary" disabled={busy}>
                {busy && !verified ? "Comprobando…" : "Verificar token"}
              </button>
            </form>
            <p className="hint">Emite el token en Sistema IA. El invitado no puede pagar.</p>
          </div>
          <div className="card">
            <h3 style={{ marginTop: 0 }}>2. Formas de pago</h3>
            {!verified ? (
              <div className="empty">Introduce un token válido para revelar tarjeta, transferencia o billetera.</div>
            ) : (
              <form onSubmit={pay}>
                <p>
                  {verified.token.plan.name} · <b>{verified.token.amount} {verified.token.currency}</b>
                </p>
                <div className="methods">
                  {verified.methods.map((m) => (
                    <button
                      type="button"
                      key={m.id}
                      className={`method ${method === m.id ? "on" : ""}`}
                      onClick={() => setMethod(m.id)}
                    >
                      <b>{m.name}</b>
                      <div style={{ color: "var(--muted)", fontSize: 12, marginTop: 4 }}>{m.detail}</div>
                    </button>
                  ))}
                </div>
                {method === "tarjeta" ? (
                  <>
                    <div className="field">
                      <label>Número de tarjeta (16 dígitos de prueba)</label>
                      <input value={cardNumber} onChange={(e) => setCardNumber(e.target.value)} placeholder="4242424242424242" required />
                    </div>
                    <div className="field">
                      <label>Titular</label>
                      <input value={holder} onChange={(e) => setHolder(e.target.value)} required />
                    </div>
                    <div className="grid-2" style={{ gap: 10 }}>
                      <div className="field">
                        <label>Caducidad MM/AA</label>
                        <input value={expiry} onChange={(e) => setExpiry(e.target.value)} placeholder="12/28" required />
                      </div>
                      <div className="field">
                        <label>CVV</label>
                        <input value={cvv} onChange={(e) => setCvv(e.target.value)} placeholder="123" required />
                      </div>
                    </div>
                  </>
                ) : method === "transferencia" ? (
                  <div className="point">
                    <strong>Banco DIGITAL BOT</strong>
                    <span>IBAN simulado ES12 2100 0000 0000 0000 · concepto {verified.token.code}</span>
                  </div>
                ) : (
                  <div className="point">
                    <strong>Billetera DIGITAL BOT</strong>
                    <span>Se debitará el saldo simulado al confirmar.</span>
                  </div>
                )}
                <button className="btn btn-primary" style={{ marginTop: 16 }} disabled={busy || user.role === "guest"}>
                  {user.role === "guest" ? "Regístrate para pagar" : `Pagar ${verified.token.amount} USD`}
                </button>
              </form>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
