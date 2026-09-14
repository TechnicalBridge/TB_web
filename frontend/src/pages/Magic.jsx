import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useAuth } from "../store/authStore";

export default function Magic() {
  const [params] = useSearchParams();
  const token = params.get("token") || params.get("uuid") || "";
  const navigate = useNavigate();
  const { verifyMagic, homeFor, setError } = useAuth();
  const [message, setMessage] = useState("Validando enlace de un solo uso…");

  useEffect(() => {
    if (!token) {
      setMessage("Falta el token UUID en la URL.");
      return;
    }
    verifyMagic(token, sessionStorage.getItem("tb_pending_name") || undefined)
      .then((user) => {
        sessionStorage.removeItem("tb_pending_name");
        navigate(homeFor(user), { replace: true });
      })
      .catch((err) => {
        setError(err.message);
        setMessage(err.message);
      });
  }, [token]);

  return (
    <div className="auth-panel" style={{ minHeight: "100vh" }}>
      <div className="auth-card">
        <h2 style={{ fontFamily: "var(--display)", marginTop: 0 }}>Acceso passwordless</h2>
        <p className="hint" style={{ textAlign: "left" }}>{message}</p>
      </div>
    </div>
  );
}
