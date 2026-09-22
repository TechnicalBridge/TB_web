import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../store/authStore";

export default function Magic() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const navigate = useNavigate();
  const { entrarConEnlace, homeFor } = useAuth();
  const [mensaje, setMensaje] = useState("Validando el enlace…");
  const [fallo, setFallo] = useState(false);
  // En desarrollo React monta dos veces; el enlace es de un solo uso y el
  // segundo intento lo encontraria gastado.
  const usado = useRef(false);

  useEffect(() => {
    if (usado.current) return;
    usado.current = true;
    if (!token) {
      setMensaje("Al enlace le falta el token.");
      setFallo(true);
      return;
    }
    entrarConEnlace(token)
      .then((user) => navigate(homeFor(user), { replace: true }))
      .catch((err) => {
        setMensaje(err.message);
        setFallo(true);
      });
  }, [token]);

  return (
    <div className="auth-panel" style={{ minHeight: "100vh" }}>
      <div className="auth-card">
        <h2 style={{ fontFamily: "var(--display)", marginTop: 0 }}>Enlace de acceso</h2>
        <p className="hint" style={{ textAlign: "left" }}>{mensaje}</p>
        {fallo ? <Link className="btn btn-primary" to="/login">Volver a entrar</Link> : null}
      </div>
    </div>
  );
}
