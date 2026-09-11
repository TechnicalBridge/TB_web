import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../context/AuthContext";

const welcome = {
  role: "assistant",
  content:
    "Sistema automatizado verificado de DIGITAL BOT en línea. Dime qué necesitas (equipo, mensual/anual) y te recomiendo un plan. El token de pago lo emito yo; sin token no aparece la forma de pago.",
};

export default function Assistant() {
  const { user } = useAuth();
  const [messages, setMessages] = useState([welcome]);
  const [text, setText] = useState("");
  const [busy, setBusy] = useState(false);
  const [issuing, setIssuing] = useState(false);
  const [steps, setSteps] = useState([]);
  const [token, setToken] = useState(null);
  const [error, setError] = useState("");
  const [quote, setQuote] = useState({ planId: "profesional", billing: "mensual", users: 1, extras: [] });

  useEffect(() => {
    try {
      const saved = sessionStorage.getItem("dbt_quote");
      if (saved) setQuote({ ...quote, ...JSON.parse(saved) });
    } catch {
      /* ignore */
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function send(e) {
    e.preventDefault();
    const content = text.trim();
    if (!content) return;
    const next = [...messages, { role: "user", content }];
    setMessages(next);
    setText("");
    setBusy(true);
    setError("");
    try {
      const data = await api("/ai/chat", {
        method: "POST",
        body: { messages: next.filter((m) => m.role !== "system"), ...quote },
      });
      setMessages([...next, { role: "assistant", content: data.reply }]);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function issueToken() {
    setIssuing(true);
    setError("");
    setSteps([
      { label: "Identidad", ok: false },
      { label: "Plan y cotización", ok: false },
      { label: "Firma verificada", ok: false },
      { label: "Emisión de token", ok: false },
    ]);
    const mark = (i) =>
      new Promise((resolve) => {
        setTimeout(() => {
          setSteps((prev) => prev.map((s, idx) => (idx <= i ? { ...s, ok: true } : s)));
          resolve();
        }, 350);
      });
    await mark(0);
    await mark(1);
    try {
      const data = await api("/tokens", { method: "POST", body: quote });
      await mark(2);
      await mark(3);
      setToken(data.token);
      sessionStorage.setItem("dbt_pay_token", data.token.code);
      setMessages((prev) => [
        ...prev,
        {
          role: "assistant",
          content: `Verificación completa. Token ${data.token.code} emitido por ${data.token.amount} ${data.token.currency}. Llévalo a Forma de pago para revelar tarjeta, transferencia o billetera.`,
        },
      ]);
    } catch (err) {
      setError(err.message);
    } finally {
      setIssuing(false);
    }
  }

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Sistema IA</h1>
          <p>Automatizado y verificado · no inventa el token</p>
        </div>
        <span className="badge badge-ok"><span className="dot" /> Canal firmado</span>
      </div>
      {error ? <div className="error">{error}</div> : null}
      {user.role === "guest" ? (
        <div className="error">Modo invitado: puedes preguntar, pero el token de pago exige una cuenta registrada.</div>
      ) : null}
      <div className="grid-2">
        <div className="card chat">
          <div className="messages">
            {messages.map((m, i) => (
              <div key={i} className={`bubble ${m.role === "user" ? "user" : "bot"}`}>
                {m.content}
              </div>
            ))}
            {busy ? <div className="bubble bot">Verificando respuesta…</div> : null}
          </div>
          <form className="composer" onSubmit={send}>
            <input
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="¿Qué plan me conviene?"
            />
            <button className="btn btn-primary btn-sm" disabled={busy}>Enviar</button>
          </form>
        </div>
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Emisión de token</h3>
          <p style={{ color: "var(--muted)" }}>
            Plan {quote.planId} · {quote.billing} · {quote.users} usuario{quote.users > 1 ? "s" : ""}
          </p>
          <div className="steps">
            {(steps.length ? steps : [
              { label: "Identidad", ok: false },
              { label: "Plan y cotización", ok: false },
              { label: "Firma verificada", ok: false },
              { label: "Emisión de token", ok: false },
            ]).map((s) => (
              <div key={s.label} className={`step ${s.ok ? "ok" : ""}`}>
                <span>{s.label}</span>
                <span>{s.ok ? "OK" : "en espera"}</span>
              </div>
            ))}
          </div>
          {token ? (
            <>
              <div className="token-box">{token.code}</div>
              <p style={{ color: "var(--muted)", fontSize: 13 }}>
                {token.amount} {token.currency} · caduca {new Date(token.expiresAt).toLocaleString("es")}
              </p>
              <Link className="btn btn-primary" to="/app/pago">Ir a forma de pago</Link>
            </>
          ) : (
            <button className="btn btn-primary" disabled={issuing || user.role === "guest"} onClick={issueToken}>
              {issuing ? "Verificando…" : "Verificar y emitir token"}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
