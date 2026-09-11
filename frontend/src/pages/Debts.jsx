import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, clp, downloadCertificate } from "../api";
import { useAuth } from "../store/authStore";

export default function Debts() {
  const { user } = useAuth();
  const [debts, setDebts] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    api("/debts")
      .then((data) => setDebts(data.debts || []))
      .catch((err) => setError(err.message));
  }, []);

  const remaining = debts.reduce((s, d) => s + Number(d.remainingAmount || 0), 0);
  const original = debts.reduce((s, d) => s + Number(d.originalAmount || 0), 0);

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>Mis deudas</h1>
          <p>Hola, {user?.name?.split(" ")[0]} · Technical Bridge</p>
        </div>
        <span className="badge badge-ok"><span className="dot" /> Sesión JWT</span>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="grid-3" style={{ marginBottom: 16 }}>
        <div className="card stat">
          <span>Saldo pendiente</span>
          <b>{clp(remaining)}</b>
        </div>
        <div className="card stat">
          <span>Original</span>
          <b>{clp(original)}</b>
        </div>
        <div className="card stat">
          <span>Obligaciones</span>
          <b>{debts.length}</b>
        </div>
      </div>
      <div className="card">
        {debts.length === 0 ? (
          <div className="empty">No hay deudas a tu nombre. DataBridge puede cargarlas por CSV.</div>
        ) : (
          <table className="table">
            <thead>
              <tr>
                <th>Acreedor</th>
                <th>Descripción</th>
                <th>Saldo</th>
                <th>Estado</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {debts.map((d) => (
                <tr key={d.id}>
                  <td>{d.creditorName}</td>
                  <td>{d.description}</td>
                  <td>{clp(d.remainingAmount)}</td>
                  <td>
                    <span className={`badge ${d.status === "PAGADA" ? "badge-ok" : "badge-wait"}`}>{d.status}</span>
                  </td>
                  <td style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    {d.status === "PAGADA" ? (
                      <button className="btn btn-cyan btn-sm" type="button" onClick={() => downloadCertificate(d.id)}>
                        PDF
                      </button>
                    ) : (
                      <>
                        <Link className="btn btn-cyan btn-sm" to={`/app/repactar/${d.id}`}>Repactar</Link>
                        <Link className="btn btn-sm btn-green" to={`/app/pagar/${d.id}`}>Pagar</Link>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
