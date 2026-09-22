import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, dinero, downloadCertificate, ESTADO_DEUDA, rutLegible } from "../api";
import { useAuth } from "../store/authStore";

/** Suma por moneda: pesos y UF no se pueden sumar entre si. */
function porMoneda(deudas, campo) {
  const totales = {};
  for (const d of deudas) totales[d.moneda] = (totales[d.moneda] || 0) + Number(d[campo] || 0);
  return Object.entries(totales);
}

export default function Debts() {
  const { user } = useAuth();
  const [deudas, setDeudas] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api("/debts")
      .then((data) => setDeudas(data.debts || []))
      .catch((err) => {
        setError(err.status === 404 ? "" : err.message);
        setDeudas([]);
      });
  }, []);

  if (deudas === null) return <div className="card">Cargando…</div>;

  const vigentes = deudas.filter((d) => d.estado === "open" || d.estado === "repacted");
  const nombre = deudas[0]?.deudor?.split(" ")[0];

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Mis pagos pendientes</h1>
          <p>{nombre ? `Hola, ${nombre}` : "Hola"} · RUT {rutLegible(user?.rut)}</p>
        </div>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="grid-3" style={{ marginBottom: 16 }}>
        <div className="card stat">
          <span>Por pagar</span>
          <b className="totales">
            {porMoneda(vigentes, "saldo").map(([moneda, total]) => (
              <span key={moneda}>{dinero(total, moneda)}</span>
            ))}
            {vigentes.length === 0 ? dinero(0) : null}
          </b>
        </div>
        <div className="card stat">
          <span>Pendientes</span>
          <b>{vigentes.length}</b>
        </div>
        <div className="card stat">
          <span>Pagadas</span>
          <b>{deudas.filter((d) => d.estado === "paid").length}</b>
        </div>
      </div>

      <div className="card">
        {deudas.length === 0 ? (
          <div className="empty">No hay pagos pendientes a tu nombre.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Empresa</th>
                <th>Concepto</th>
                <th>Saldo</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {deudas.map((d) => {
                const estado = ESTADO_DEUDA[d.estado] || { texto: d.estado, clase: "badge-muted" };
                return (
                  <tr key={d.id}>
                    <td>{d.acreedor}<span className="sub">{d.externalId}</span></td>
                    <td>{d.concepto}</td>
                    <td>{dinero(d.saldo, d.moneda)}</td>
                    <td><span className={`badge ${estado.clase}`}>{estado.texto}</span></td>
                    <td style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      {d.estado === "paid" ? (
                        <button className="btn btn-cyan btn-sm" type="button"
                                onClick={() => downloadCertificate(d.id)}>
                          Certificado
                        </button>
                      ) : null}
                      {d.estado === "open" ? (
                        <Link className="btn btn-cyan btn-sm" to={`/app/repactar/${d.id}`}>Ver planes</Link>
                      ) : null}
                      {d.estado === "open" || d.estado === "repacted" ? (
                        <Link className="btn btn-sm btn-green" to={`/app/pagar/${d.id}`}>Pagar</Link>
                      ) : null}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
