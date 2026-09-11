import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api";

export default function Simulate() {
  const navigate = useNavigate();
  const [plans, setPlans] = useState([]);
  const [extrasCatalog, setExtrasCatalog] = useState([]);
  const [planId, setPlanId] = useState("profesional");
  const [billing, setBilling] = useState("mensual");
  const [users, setUsers] = useState(1);
  const [extras, setExtras] = useState([]);
  const [quote, setQuote] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api("/plans").then((data) => {
      setPlans(data.plans);
      setExtrasCatalog(data.extras);
    });
  }, []);

  useEffect(() => {
    if (!planId) return;
    api("/simulate", { method: "POST", body: { planId, billing, users, extras } })
      .then(setQuote)
      .catch((err) => setError(err.message));
  }, [planId, billing, users, extras]);

  function toggleExtra(id) {
    setExtras((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }

  function goVerify() {
    const payload = { planId, billing, users, extras };
    sessionStorage.setItem("dbt_quote", JSON.stringify(payload));
    navigate("/app/ia");
  }

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Simular plan</h1>
          <p>Ajusta periodo, usuarios y extras. El importe se bloquea al emitir el token.</p>
        </div>
        <div className="toggle">
          <button className={billing === "mensual" ? "on" : ""} onClick={() => setBilling("mensual")}>Mensual</button>
          <button className={billing === "anual" ? "on" : ""} onClick={() => setBilling("anual")}>Anual −20%</button>
        </div>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="grid-3" style={{ marginBottom: 16 }}>
        {plans.map((plan) => (
          <button
            key={plan.id}
            className={`card plan-card ${plan.popular ? "popular" : ""}`}
            onClick={() => setPlanId(plan.id)}
            style={{
              textAlign: "left",
              cursor: "pointer",
              color: "inherit",
              outline: planId === plan.id ? "2px solid var(--cyan)" : "none",
            }}
          >
            {plan.popular ? <span className="badge badge-ok">Recomendado</span> : null}
            <h3>{plan.name}</h3>
            <div style={{ color: "var(--muted)" }}>{plan.tagline}</div>
            <div className="price">
              {billing === "anual" ? plan.yearly : plan.monthly} <small>USD / {billing === "anual" ? "año" : "mes"}</small>
            </div>
            <ul className="features">
              {plan.features.map((f) => (
                <li key={f}>{f}</li>
              ))}
            </ul>
          </button>
        ))}
      </div>
      <div className="grid-2">
        <div className="card">
          <div className="field">
            <label>Usuarios en el equipo: {users}</label>
            <input type="range" min="1" max="20" value={users} onChange={(e) => setUsers(Number(e.target.value))} />
          </div>
          {extrasCatalog.map((extra) => (
            <label key={extra.id} style={{ display: "flex", gap: 10, marginBottom: 10, color: "var(--muted)" }}>
              <input
                type="checkbox"
                checked={extras.includes(extra.id)}
                onChange={() => toggleExtra(extra.id)}
              />
              {extra.label} · {extra.monthly} USD/mes
            </label>
          ))}
        </div>
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Cotización</h3>
          {quote ? (
            <>
              <div className="price">{quote.amount} <small>{quote.currency} / {quote.periodLabel}</small></div>
              <p>Plan {quote.plan.name} · {quote.users} usuario{quote.users > 1 ? "s" : ""}</p>
              {quote.extras.map((e) => (
                <div key={e.id} style={{ color: "var(--muted)" }}>{e.label}: {e.amount} USD</div>
              ))}
              <button className="btn btn-primary" style={{ marginTop: 16 }} onClick={goVerify}>
                Verificar con IA y emitir token
              </button>
            </>
          ) : (
            <p className="empty">Calculando…</p>
          )}
        </div>
      </div>
    </div>
  );
}
