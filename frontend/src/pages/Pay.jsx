import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { obtenerDeuda } from "../api/deudas";
import { abrirCobro, obtenerPago } from "../api/pagos";
import { dinero, ESTADO_CUOTA, estadoDe, fecha } from "../utils/formato";

const PASARELAS = [
  { id: "webpay", label: "Webpay" },
  { id: "mercadopago", label: "Mercado Pago" },
  { id: "khipu", label: "Khipu" },
];

/**
 * Pagar una cuota o el saldo.
 *
 * El monto no lo manda la pantalla: ms-payments se lo pregunta a ms-debt. Si
 * lo mandara el navegador, bastaria editar la peticion para pagar un peso y
 * dar la deuda por saldada.
 */
export default function Pay() {
  const { id } = useParams();
  const [deuda, setDeuda] = useState(null);
  const [pasarela, setPasarela] = useState("webpay");
  const [modo, setModo] = useState("cuota");
  const [pago, setPago] = useState(null);
  const [acreditado, setAcreditado] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const cargar = () => obtenerDeuda(id).then(setDeuda);

  useEffect(() => {
    cargar().catch((err) => setError(err.message));
  }, [id]);

  // Mientras la pasarela no confirma, se consulta el pago.
  useEffect(() => {
    if (!pago || pago.status === "paid") return;
    const t = setInterval(async () => {
      try {
        setPago(await obtenerPago(pago.id));
      } catch {
        /* se reintenta en el proximo ciclo */
      }
    }, 2000);
    return () => clearInterval(t);
  }, [pago?.id, pago?.status]);

  // Confirmado el pago, el aviso tarda unos segundos en llegar a la deuda.
  useEffect(() => {
    if (pago?.status !== "paid" || acreditado) return;
    const saldoAntes = Number(deuda?.saldo);
    const t = setInterval(async () => {
      const nueva = await obtenerDeuda(id).catch(() => null);
      if (nueva && Number(nueva.saldo) < saldoAntes) {
        setDeuda(nueva);
        setAcreditado(true);
      }
    }, 2000);
    return () => clearInterval(t);
  }, [pago?.status, acreditado]);

  if (!deuda) return <div className="card">{error || "Cargando…"}</div>;

  const moneda = deuda.moneda;
  const pendientes = (deuda.cuotas || []).filter((c) => c.estado === "pending");
  const proxima = pendientes[0];
  const hayVarias = pendientes.length > 1;
  const pagaCuota = hayVarias && modo === "cuota";
  const monto = pagaCuota ? Number(proxima?.monto || 0) : Number(deuda.saldo || 0);

  async function pagar() {
    setBusy(true);
    setError("");
    try {
      const data = await abrirCobro(Number(id), pagaCuota ? proxima.id : null, pasarela);
      setPago(data);
      window.open(data.checkoutUrl, "_blank", "noopener,width=480,height=720");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Pagar</h1>
          <p>{deuda.acreedor} · {deuda.concepto}</p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/app">Volver</Link>
      </div>
      {error ? <div className="error">{error}</div> : null}

      {pago?.status === "paid" ? (
        <div className="card success">
          <h2>Pago recibido</h2>
          <p>
            {dinero(pago.amount, pago.currency)}
            {pago.currency === "UF" && pago.amountClp ? ` · ${dinero(pago.amountClp)} al valor de hoy` : ""}
          </p>
          <p className="hint">
            {acreditado
              ? `Tu saldo quedó en ${dinero(deuda.saldo, moneda)}. ${deuda.acreedor} ya fue avisado.`
              : "Estamos acreditándolo en tu deuda…"}
          </p>
          <Link className="btn btn-primary" to="/app">Ver mis pagos</Link>
        </div>
      ) : (
        <div className="grid-2">
          <div className="card">
            {hayVarias ? (
              <div className="toggle">
                <button type="button" className={modo === "cuota" ? "on" : ""} onClick={() => setModo("cuota")}>
                  Próxima cuota
                </button>
                <button type="button" className={modo === "saldo" ? "on" : ""} onClick={() => setModo("saldo")}>
                  Todo el saldo
                </button>
              </div>
            ) : null}
            <p style={{ fontSize: 28, fontFamily: "var(--display)", margin: "8px 0 4px" }}>{dinero(monto, moneda)}</p>
            {pagaCuota && proxima ? (
              <p className="hint" style={{ textAlign: "left", marginTop: 0 }}>
                Cuota {proxima.numero} · vence {fecha(proxima.vencimiento)}
              </p>
            ) : null}
            <div className="methods">
              {PASARELAS.map((p) => (
                <button key={p.id} type="button" className={`method ${pasarela === p.id ? "on" : ""}`}
                        onClick={() => setPasarela(p.id)}>
                  {p.label}
                </button>
              ))}
            </div>
            <button className="btn btn-primary" disabled={busy || monto <= 0 || !!pago} onClick={pagar}>
              {busy ? "Abriendo…" : "Ir a pagar"}
            </button>
            {pago ? <p className="hint">Esperando la confirmación de la pasarela…</p> : null}
          </div>

          <div className="card">
            <h3 style={{ marginTop: 0 }}>{(deuda.cuotas || []).length > 1 ? "Cuotas" : "Detalle"}</h3>
            {(deuda.cuotas || []).length > 1 ? (
              <table className="table">
                <thead>
                  <tr><th>#</th><th>Vence</th><th>Monto</th><th></th></tr>
                </thead>
                <tbody>
                  {deuda.cuotas.map((c) => {
                    const estado = estadoDe(ESTADO_CUOTA, c.estado);
                    return (
                      <tr key={c.id}>
                        <td>{c.numero}</td>
                        <td>{fecha(c.vencimiento)}</td>
                        <td>{dinero(c.monto, moneda)}</td>
                        <td><span className={`badge ${estado.clase}`}>{estado.texto}</span></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            ) : (
              <table className="table">
                <thead>
                  <tr><th>Concepto</th><th>Vence</th><th>Monto</th></tr>
                </thead>
                <tbody>
                  {(deuda.cargos || []).map((c, i) => (
                    <tr key={i}>
                      <td>{c.concepto}</td>
                      <td>{fecha(c.vencimiento)}</td>
                      <td>{dinero(c.monto, moneda)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
