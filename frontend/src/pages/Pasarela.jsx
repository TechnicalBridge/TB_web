import { useEffect, useState } from "react";
import { useParams, useSearchParams } from "react-router-dom";
import axios from "axios";
import { clp } from "../api";

export default function Pasarela() {
  const { id } = useParams();
  const [params] = useSearchParams();
  const sig = params.get("sig") || "";
  const [payment, setPayment] = useState(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    axios
      .get(`/api/payments/public/${id}`, { params: { sig } })
      .then((res) => setPayment(res.data))
      .catch((err) => setError(err.response?.data?.error || "Checkout inválido"));
  }, [id, sig]);

  async function confirm() {
    setBusy(true);
    setError("");
    try {
      const res = await axios.post(`/api/payments/public/${id}/confirm`, null, { params: { sig } });
      setPayment(res.data);
    } catch (err) {
      setError(err.response?.data?.error || err.message);
    } finally {
      setBusy(false);
    }
  }

  const names = { MERCADOPAGO: "Mercado Pago", KHIPU: "Khipu", WEBPAY: "Webpay" };

  return (
    <div className="auth-panel" style={{ minHeight: "100vh" }}>
      <div className="auth-card">
        <div className="brand-sub">{names[payment?.gateway] || "Pasarela"}</div>
        <h2 style={{ fontFamily: "var(--display)", marginTop: 8 }}>Checkout simulado</h2>
        {error ? <div className="error">{error}</div> : null}
        {payment?.status === "PAGADO" ? (
          <div className="success">
            <h2>Pago exitoso</h2>
            <p>Webhook firmado · evento publicado a MS-Debt.</p>
            <p className="hint">Puedes cerrar esta ventana. Technical Bridge se actualiza solo.</p>
          </div>
        ) : payment ? (
          <>
            <p>Importe a pagar</p>
            <p style={{ fontSize: 36, fontFamily: "var(--display)", margin: "8px 0 18px" }}>{clp(payment.amount)}</p>
            <button className="btn btn-primary" disabled={busy} onClick={confirm}>
              {busy ? "Confirmando…" : "Pagar ahora"}
            </button>
            <p className="hint">Simulación de Checkout Pro / Khipu / Webpay. Firma HMAC-SHA256.</p>
          </>
        ) : (
          <p className="hint">Cargando preferencia…</p>
        )}
      </div>
    </div>
  );
}
