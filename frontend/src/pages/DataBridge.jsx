import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, clp } from "../api";
import { useAuth } from "../store/authStore";

export default function DataBridge() {
  const { user } = useAuth();
  const [summary, setSummary] = useState(null);
  const [debts, setDebts] = useState([]);
  const [drag, setDrag] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  async function refresh() {
    const [s, d] = await Promise.all([api("/analytics/summary"), api("/debts")]);
    setSummary(s);
    setDebts(d.debts || []);
  }

  useEffect(() => {
    refresh().catch((err) => setError(err.message));
  }, []);

  async function upload(file) {
    if (!file) return;
    setError("");
    const form = new FormData();
    form.append("file", file);
    try {
      const res = await api("/debts/ingest", { method: "POST", body: form });
      setResult(res);
      await refresh();
    } catch (err) {
      setError(err.message);
    }
  }

  const chart = (summary?.porAcreedor || []).map((r) => ({
    name: r.name,
    activo: Number(r.activo || 0),
    recaudado: Number(r.recaudado || 0),
  }));

  return (
    <div>
      <div className="topbar">
        <div>
          <h1>DataBridge</h1>
          <p>{user?.name} · monitoreo de cartera y conciliación</p>
        </div>
        <span className="badge badge-ok"><span className="dot" /> Core B2B</span>
      </div>
      {error ? <div className="error">{error}</div> : null}
      <div className="grid-3" style={{ marginBottom: 16 }}>
        <div className="card stat">
          <span>Cartera activa</span>
          <b>{clp(summary?.totalCartera)}</b>
        </div>
        <div className="card stat">
          <span>Recaudado</span>
          <b>{clp(summary?.totalRecaudado)}</b>
        </div>
        <div className="card stat">
          <span>Recuperación</span>
          <b>{summary?.tasaRecuperacion ?? 0}%</b>
        </div>
      </div>
      <div className="grid-2" style={{ marginBottom: 16 }}>
        <div className="card">
          <h3 style={{ marginTop: 0 }}>Por acreedor</h3>
          <div style={{ height: 260 }}>
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={chart}>
                <CartesianGrid stroke="rgba(126,223,240,0.12)" vertical={false} />
                <XAxis dataKey="name" stroke="#9bb0c4" tick={{ fontSize: 11 }} />
                <YAxis stroke="#9bb0c4" tick={{ fontSize: 11 }} />
                <Tooltip />
                <Bar dataKey="activo" fill="#3ec6e0" radius={[6, 6, 0, 0]} />
                <Bar dataKey="recaudado" fill="#3dcf70" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
        <div
          className={`card dropzone ${drag ? "on" : ""}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDrag(false);
            upload(e.dataTransfer.files?.[0]);
          }}
        >
          <h3 style={{ marginTop: 0 }}>Carga masiva CSV</h3>
          <p className="hint" style={{ textAlign: "left" }}>
            Columnas: email,nombre,acreedor,monto,fecha_vencimiento,descripcion
          </p>
          <label className="btn btn-cyan btn-sm" style={{ display: "inline-block", marginTop: 12 }}>
            Elegir archivo
            <input
              type="file"
              accept=".csv"
              hidden
              onChange={(e) => upload(e.target.files?.[0])}
            />
          </label>
          {result ? (
            <p className="hint" style={{ textAlign: "left" }}>
              Importadas: {result.imported}. Errores: {(result.errors || []).length}
            </p>
          ) : (
            <p className="hint">Arrastra el CSV aquí.</p>
          )}
        </div>
      </div>
      <div className="card">
        <h3 style={{ marginTop: 0 }}>Cartera</h3>
        <table className="table">
          <thead>
            <tr>
              <th>Deudor</th>
              <th>Acreedor</th>
              <th>Saldo</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {debts.map((d) => (
              <tr key={d.id}>
                <td>{d.debtorName}<div className="hint" style={{ margin: 0, textAlign: "left" }}>{d.debtorEmail}</div></td>
                <td>{d.creditorName}</td>
                <td>{clp(d.remainingAmount)}</td>
                <td><span className={`badge ${d.status === "PAGADA" ? "badge-ok" : "badge-wait"}`}>{d.status}</span></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
