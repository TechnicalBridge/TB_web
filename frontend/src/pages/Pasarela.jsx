import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import { confirmarPagoPublico, pagoPublico } from "../api/pagos";
import { dinero } from "../utils/formato";

const NOMBRES = { webpay: "Webpay", mercadopago: "Mercado Pago", khipu: "Khipu" };

/**
 * La pasarela simulada. En produccion esta pagina es la de Webpay o Khipu;
 * aca confirma el pago con la misma firma del enlace, que es lo que haria el
 * aviso firmado de la pasarela real.
 */
export default function Pasarela() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const sig = params.get("sig") || "";
  const [pago, setPago] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    pagoPublico(id, sig)
      .then(setPago)
      .catch((err) => setError(err.status === 401 ? "Enlace de pago inválido" : err.message));
  }, [id, sig]);

  async function confirmar() {
    setBusy(true);
    setError("");
    try {
      setPago(await confirmarPagoPublico(id, sig));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="auth-panel" style={{ minHeight: "100vh" }}>
      <div className="auth-card">
        <div className="brand-sub">{NOMBRES[pago?.gateway] || "Pasarela"} · simulación</div>
        <h2 style={{ fontFamily: "var(--display)", marginTop: 8 }}>Confirmar pago</h2>
        {error ? <div className="error">{error}</div> : null}
        {pago?.status === "paid" ? (
          <div className="success">
            <h2>Pago aprobado</h2>
            <p className="hint">Puedes cerrar esta ventana: el portal se actualiza solo.</p>
          </div>
        ) : pago ? (
          <>
            <p>Monto</p>
            <p style={{ fontSize: 36, fontFamily: "var(--display)", margin: "8px 0 4px" }}>
              {dinero(pago.amount, pago.currency)}
            </p>
            {pago.currency === "UF" && pago.amountClp ? (
              <p className="hint" style={{ textAlign: "left", marginTop: 0 }}>
                {dinero(pago.amountClp)} al valor de la UF de hoy
              </p>
            ) : null}
            <button className="btn btn-primary" style={{ marginTop: 14 }} disabled={busy} onClick={confirmar}>
              {busy ? "Confirmando…" : "Pagar"}
            </button>
          </>
        ) : !error ? (
          <p className="hint">Cargando…</p>
        ) : null}
      </div>
    </div>
  );
}
