import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Logo from "../components/Logo";
import { api } from "../api";
import { useAuth } from "../store/authStore";

export default function Login({ portal }) {
  const { requestMagicLink, error, setError } = useAuth();
  const [email, setEmail] = useState("");
  const [name, setName] = useState("");
  const [busy, setBusy] = useState(false);
  const [sent, setSent] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const isBridge = portal !== "DATABRIDGE";

  useEffect(() => {
    api("/auth/demo")
      .then((data) => setAccounts(data.accounts || []))
      .catch(() => {});
  }, []);

  async function onSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const data = await requestMagicLink(email, name);
      if (name) sessionStorage.setItem("tb_pending_name", name);
      setSent(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const demos = accounts.filter((a) => (isBridge ? a.role === "DEBTOR" : a.role === "CREDITOR"));

  return (
    <div className="auth-shell">
      <section className="auth-hero">
        <div className="brand-row">
          <Logo size={72} />
          <div>
            <div className="brand-name">{isBridge ? "TECHNICAL BRIDGE" : "DATABRIDGE"}</div>
            <div className="brand-sub">{isBridge ? "Core B2C · deudor" : "Core B2B · acreedor"}</div>
          </div>
        </div>
        <div className="hero-copy">
          <h1>{isBridge ? "Entra sin contraseña." : "Carga cartera y mira la recuperación."}</h1>
          <p>
            Te enviamos un enlace UUID de un solo uso. Al abrirlo, MS-Auth emite un JWT y quedas en sesión.
          </p>
        </div>
        <Link to="/" className="badge badge-muted">Volver a los portales</Link>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <div className="brand-row" style={{ marginBottom: 18 }}>
            <Logo size={48} />
            <div>
              <div className="brand-name" style={{ fontSize: 16 }}>Magic link</div>
              <div className="brand-sub">Rate limited en el gateway</div>
            </div>
          </div>
          {error ? <div className="error">{error}</div> : null}
          {sent ? (
            <div>
              <p>Revisa tu correo. En este entorno de desarrollo el enlace queda aquí:</p>
              <Link className="btn btn-primary" to={sent.magicUrl.replace(/^https?:\/\/[^/]+/, "")}>
                Abrir enlace mágico
              </Link>
              <p className="hint">UUID de un solo uso · {sent.expiresInMinutes} min</p>
            </div>
          ) : (
            <form onSubmit={onSubmit}>
              {!isBridge ? null : (
                <div className="field">
                  <label>Nombre (si es tu primer acceso)</label>
                  <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ana Pérez" />
                </div>
              )}
              <div className="field">
                <label>Correo</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder={isBridge ? "ana.perez@correo.com" : "carlos.soto@databridge.com"}
                  required
                />
              </div>
              <button className="btn btn-primary" disabled={busy}>
                {busy ? "Enviando…" : "Enviar enlace"}
              </button>
            </form>
          )}
          <div className="hint" style={{ textAlign: "left", marginTop: 18 }}>
            Cuentas demo:
            {demos.map((a) => (
              <button
                key={a.email}
                type="button"
                className="chip"
                style={{ display: "block", width: "100%", marginTop: 8, textAlign: "left" }}
                onClick={() => {
                  setEmail(a.email);
                  setName(a.name);
                }}
              >
                {a.name} · {a.email}
              </button>
            ))}
          </div>
        </div>
      </section>
    </div>
  );
}
