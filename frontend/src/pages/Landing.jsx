import { Link } from "react-router-dom";
import Logo from "../components/Logo";

export default function Landing() {
  return (
    <div className="auth-shell">
      <section className="auth-hero">
        <div className="brand-row">
          <Logo size={72} />
          <div>
            <div className="brand-name">TECHNICAL BRIDGE</div>
            <div className="brand-sub">Ecosistema B2B2C</div>
          </div>
        </div>
        <div className="hero-copy">
          <h1>Conciliación, repactación y pago sin fricción.</h1>
          <p>
            Dos plataformas conectadas: el deudor entra sin contraseña; el acreedor carga cartera
            y mira la recuperación en tiempo real.
          </p>
          <div className="hero-points">
            <div className="point">
              <strong>Passwordless</strong>
              <span>Magic link UUID de un solo uso y JWT.</span>
            </div>
            <div className="point">
              <strong>MS-Debt</strong>
              <span>Saldo, cuotas y auditoría inmutable.</span>
            </div>
            <div className="point">
              <strong>Pagos async</strong>
              <span>Webhooks firmados y evento pago_exitoso.</span>
            </div>
            <div className="point">
              <strong>MS-AI</strong>
              <span>Chatbot NLP de solo lectura.</span>
            </div>
          </div>
        </div>
        <div className="badge badge-ok"><span className="dot" /> API Gateway · Rate limiting</div>
      </section>
      <section className="auth-panel">
        <div className="auth-card" style={{ display: "grid", gap: 14 }}>
          <div className="brand-row">
            <Logo size={48} />
            <div>
              <div className="brand-name" style={{ fontSize: 16 }}>Elige portal</div>
              <div className="brand-sub">Misma sesión, distinto rol</div>
            </div>
          </div>
          <Link className="btn btn-primary" to="/login">Technical Bridge · Deudor</Link>
          <Link className="btn btn-ghost" to="/databridge/login" style={{ marginTop: 0 }}>
            DataBridge · Acreedor
          </Link>
          <p className="hint">Acceso por enlace mágico. En desarrollo el enlace aparece en pantalla.</p>
        </div>
      </section>
    </div>
  );
}
