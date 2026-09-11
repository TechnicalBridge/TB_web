import { useState } from "react";
import { useAuth } from "../context/AuthContext";
import Logo from "../components/Logo";

export default function Auth() {
  const { login, register, guest, error, setError } = useAuth();
  const [tab, setTab] = useState("login");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      if (tab === "login") await login(email, password);
      else await register(name, email, password);
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function onGuest() {
    setBusy(true);
    setError("");
    try {
      await guest();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-shell">
      <section className="auth-hero">
        <div className="brand-row">
          <Logo size={72} />
          <div>
            <div className="brand-name">DIGITAL BOT</div>
            <div className="brand-sub">Sistema automatizado verificado</div>
          </div>
        </div>
        <div className="hero-copy">
          <h1>Planes, pagos y un token que abre el cobro.</h1>
          <p>
            Entra, simula tu plan y deja que el sistema verificado emita un token.
            Solo con ese token aparece la forma de pago.
          </p>
          <div className="hero-points">
            <div className="point">
              <strong>Lista de pagos</strong>
              <span>Historial claro, estados y referencias.</span>
            </div>
            <div className="point">
              <strong>Simulador</strong>
              <span>Mensual o anual, extras y usuarios.</span>
            </div>
            <div className="point">
              <strong>IA verificada</strong>
              <span>Asistente que no inventa el token.</span>
            </div>
            <div className="point">
              <strong>Token DBT</strong>
              <span>Sin token, no hay formulario de cobro.</span>
            </div>
          </div>
        </div>
        <div className="badge badge-ok"><span className="dot" /> Canal verificado</div>
      </section>

      <section className="auth-panel">
        <div className="auth-card">
          <div className="brand-row" style={{ marginBottom: 18 }}>
            <Logo size={48} />
            <div>
              <div className="brand-name" style={{ fontSize: 16 }}>DIGITAL BOT</div>
              <div className="brand-sub">Acceso</div>
            </div>
          </div>
          <div className="tabs">
            <button className={tab === "login" ? "active" : ""} type="button" onClick={() => setTab("login")}>
              Iniciar sesión
            </button>
            <button className={tab === "register" ? "active" : ""} type="button" onClick={() => setTab("register")}>
              Registro
            </button>
          </div>
          {error ? <div className="error">{error}</div> : null}
          <form onSubmit={onSubmit}>
            {tab === "register" ? (
              <div className="field">
                <label>Nombre</label>
                <input value={name} onChange={(e) => setName(e.target.value)} placeholder="Ana Pérez" required />
              </div>
            ) : null}
            <div className="field">
              <label>Correo</label>
              <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="demo@digitalbot.com" required />
            </div>
            <div className="field">
              <label>Contraseña</label>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="mínimo 6 caracteres" required />
            </div>
            <button className="btn btn-primary" disabled={busy}>
              {busy ? "Entrando…" : tab === "login" ? "Entrar" : "Crear cuenta"}
            </button>
          </form>
          <button className="btn btn-ghost" type="button" disabled={busy} onClick={onGuest}>
            Entrar como invitado
          </button>
          <p className="hint">Demo: demo@digitalbot.com / demo1234. El invitado simula, no cobra.</p>
        </div>
      </section>
    </div>
  );
}
