import { Link } from "react-router-dom";
import Logo from "../components/Logo";
import TemaToggle from "../components/TemaToggle";
import { IconoCalendario, IconoCandado, IconoCartera, IconoCheck, IconoFlecha } from "../components/Iconos";

const PUNTOS = [
  { Icono: IconoCandado, titulo: "Tu RUT y un código", texto: "Sin cuenta ni enlaces que falsificar." },
  { Icono: IconoCalendario, titulo: "Cuotas sin interés", texto: "Tú eliges el plazo, de 3 a 24 meses." },
  { Icono: IconoCheck, titulo: "Paga a tu ritmo", texto: "Una cuota, varias o todo el saldo." },
  { Icono: IconoCartera, titulo: "Pesos y UF", texto: "Cada pago en UF guarda el valor del día." },
];

export default function Landing() {
  return (
    <div className="auth-shell">
      <TemaToggle flotante />
      <section className="auth-hero">
        <div className="brand-row aparece">
          <Logo size={56} />
          <div>
            <div className="brand-name">Technical Bridge</div>
            <div className="brand-sub">Cobranza de arriendos en mora</div>
          </div>
        </div>
        <div className="hero-copy">
          <h1 className="aparece" style={{ "--i": 1 }}>
            ¿Pagos atrasados? <span className="resalta">Ponte al día</span> sin miedo.
          </h1>
          <p className="aparece" style={{ "--i": 2 }}>
            Si tienes meses de arriendo impagos, aquí ves exactamente qué debes y a quién, y lo pagas
            de una vez o en cuotas. Tu deuda avanza a la vista: pendiente, en convenio, pago conciliado.
          </p>
          <div className="hero-points">
            {PUNTOS.map(({ Icono, titulo, texto }, i) => (
              <div key={titulo} className="point aparece" style={{ "--i": i + 3 }}>
                <Icono size={20} />
                <div>
                  <strong>{titulo}</strong>
                  <span>{texto}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
        <span className="badge badge-ok badge-link aparece" style={{ "--i": 8 }}>
          <span className="dot" /> Hecho en Chile · RUT y UF
        </span>
      </section>
      <section className="auth-panel">
        <div className="auth-card">
          <div className="brand-row" style={{ marginBottom: 22 }}>
            <Logo size={44} />
            <div>
              <div className="brand-name" style={{ fontSize: 18 }}>¿Quién eres?</div>
              <div className="brand-sub">Dos portales</div>
            </div>
          </div>
          <div className="opciones-entrada">
            <Link className="btn btn-primary btn-block" to="/login">
              Tengo un código de acceso
              <IconoFlecha size={16} />
            </Link>
            <Link className="btn btn-ghost btn-block" to="/databridge/login">Soy una empresa</Link>
          </div>
          <p className="hint centro">Nunca te pediremos que entres desde un enlace en un mensaje.</p>
        </div>
      </section>
    </div>
  );
}
