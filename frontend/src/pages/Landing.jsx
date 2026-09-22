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
          <h1>Pagar una deuda sin tener que confiar a ciegas.</h1>
          <p>
            El deudor entra con su RUT y un código, sin cuenta ni enlaces, ve exactamente qué debe
            y a quién, y paga o repacta. La empresa ve su cartera al día y se entera de cada pago.
          </p>
          <div className="hero-points">
            <div className="point">
              <strong>Código de acceso</strong>
              <span>RUT + código de un solo uso. Sin enlaces que falsificar.</span>
            </div>
            <div className="point">
              <strong>Cuotas sin interés</strong>
              <span>El deudor simula el plan y lo acepta él mismo.</span>
            </div>
            <div className="point">
              <strong>Pesos y UF</strong>
              <span>Cada pago en UF guarda el valor del día.</span>
            </div>
            <div className="point">
              <strong>Contrato v1</strong>
              <span>La cartera llega por API y el pago vuelve firmado.</span>
            </div>
          </div>
        </div>
        <div className="badge badge-ok"><span className="dot" /> Hecho en Chile · RUT y UF</div>
      </section>
      <section className="auth-panel">
        <div className="auth-card" style={{ display: "grid", gap: 14 }}>
          <div className="brand-row">
            <Logo size={48} />
            <div>
              <div className="brand-name" style={{ fontSize: 16 }}>¿Quién eres?</div>
              <div className="brand-sub">Dos portales</div>
            </div>
          </div>
          <Link className="btn btn-primary" to="/login">Tengo un código de acceso</Link>
          <Link className="btn btn-ghost" to="/databridge/login" style={{ marginTop: 0 }}>
            Soy una empresa
          </Link>
          <p className="hint">Nunca te pediremos que entres desde un enlace en un mensaje.</p>
        </div>
      </section>
    </div>
  );
}
