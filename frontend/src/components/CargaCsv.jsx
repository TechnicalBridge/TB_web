import { useEffect, useState } from "react";
import { api, rutLegible } from "../api";

const COLUMNAS = [
  "deuda_id", "accion", "motivo_retiro", "deudor_rut", "deudor_tipo", "deudor_nombre", "deudor_correo",
  "deudor_telefono", "moneda", "concepto", "referencias", "cargo_concepto", "cargo_periodo", "cargo_monto",
  "cargo_vencimiento",
];
const EJEMPLO = [
  "CTR-2025-014;registrar;;16482337-7;persona;Felipe Rojas Muñoz;felipe.rojas@correo.cl;+56987654321;CLP;Arriendo mensual;contrato=CTR-2025-014;Arriendo agosto;2026-08;520000;2026-08-05",
  "CTR-2024-007;registrar;;76991245-2;empresa;Comercial Ñandú SpA;administracion@nandu.cl;;UF;Arriendo local comercial;;Arriendo julio;2026-07;38,5;2026-07-05",
  "CTR-2025-022;retirar;pago_directo;;;;;;;;;;;;",
];

function hoyEnChile() {
  return new Date().toLocaleDateString("sv-SE", { timeZone: "America/Santiago" });
}

/** La plantilla del contrato, con BOM para que Excel respete las tildes. */
function descargarPlantilla() {
  const texto = "\uFEFF" + [COLUMNAS.join(";"), ...EJEMPLO].join("\r\n") + "\r\n";
  const url = URL.createObjectURL(new Blob([texto], { type: "text/csv;charset=utf-8" }));
  const a = document.createElement("a");
  a.href = url;
  a.download = "cartera-v1-plantilla.csv";
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Cargar cartera por archivo: el modo archivo del contrato, para quien no
 * tiene integracion por API. El archivo entra por la misma ingesta que la
 * API, con las mismas validaciones y la misma aceptacion parcial.
 */
export default function CargaCsv({ onCargada }) {
  const [opciones, setOpciones] = useState(null);
  const [acreedor, setAcreedor] = useState("");
  const [campana, setCampana] = useState("");
  const [corte, setCorte] = useState(hoyEnChile());
  const [lote, setLote] = useState(`CSV-${hoyEnChile()}-01`);
  const [archivo, setArchivo] = useState(null);
  const [arrastrando, setArrastrando] = useState(false);
  const [busy, setBusy] = useState(false);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    api("/debts/cartera/opciones")
      .then((data) => {
        setOpciones(data);
        setAcreedor(data.acreedores?.[0]?.rut || "");
      })
      .catch((err) => setError(err.message));
  }, []);

  const elegido = opciones?.acreedores?.find((a) => a.rut === acreedor);

  async function subir(e) {
    e.preventDefault();
    if (!archivo) return;
    setBusy(true);
    setError("");
    setResultado(null);
    const form = new FormData();
    form.append("archivo", archivo);
    form.append("lote_id_externo", lote);
    form.append("fecha_corte", corte);
    form.append("acreedor_rut", acreedor);
    if (campana) form.append("campana_id_externo", campana);
    try {
      const r = await api("/debts/cartera", { method: "POST", body: form });
      setResultado(r);
      onCargada?.();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  const rechazos = (resultado?.resultados || []).filter((r) => r.resultado === "rechazada");

  return (
    <form
      className={`card dropzone ${arrastrando ? "on" : ""}`}
      onSubmit={subir}
      onDragOver={(e) => { e.preventDefault(); setArrastrando(true); }}
      onDragLeave={() => setArrastrando(false)}
      onDrop={(e) => {
        e.preventDefault();
        setArrastrando(false);
        if (e.dataTransfer.files?.[0]) setArchivo(e.dataTransfer.files[0]);
      }}
    >
      <div className="topbar" style={{ marginBottom: 8 }}>
        <h3 style={{ margin: 0 }}>Cargar cartera (CSV)</h3>
        <button type="button" className="link-btn" onClick={descargarPlantilla}>Descargar plantilla</button>
      </div>
      {error ? <div className="error">{error}</div> : null}

      <div className="grid-2">
        <div className="field">
          <label htmlFor="acreedor">Acreedor</label>
          <select id="acreedor" value={acreedor} onChange={(e) => { setAcreedor(e.target.value); setCampana(""); }}>
            {(opciones?.acreedores || []).map((a) => (
              <option key={a.rut} value={a.rut}>{a.nombre} · {rutLegible(a.rut)}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="campana">Campaña</label>
          <select id="campana" value={campana} onChange={(e) => setCampana(e.target.value)}
                  disabled={!elegido?.campanas?.length}>
            <option value="">{elegido?.campanas?.length ? "Sin campaña" : "No aplica"}</option>
            {(elegido?.campanas || []).map((c) => (
              <option key={c.idExterno} value={c.idExterno}>{c.nombre}</option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="lote">Id del lote</label>
          <input id="lote" value={lote} onChange={(e) => setLote(e.target.value)} required />
        </div>
        <div className="field">
          <label htmlFor="corte">Fecha de corte</label>
          <input id="corte" type="date" value={corte} onChange={(e) => setCorte(e.target.value)} required />
        </div>
      </div>

      <p className="hint" style={{ textAlign: "left", margin: "4px 0 12px" }}>
        {archivo
          ? <>Archivo: <b>{archivo.name}</b> ({Math.ceil(archivo.size / 1024)} KB)</>
          : "Arrastra aquí el CSV, o elígelo. Separador ; y una fila por cargo, como en la plantilla."}
      </p>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
        <label className="btn btn-ghost btn-sm" style={{ marginTop: 0 }}>
          Elegir archivo
          <input type="file" accept=".csv,text/csv" hidden onChange={(e) => setArchivo(e.target.files?.[0] || null)} />
        </label>
        <button className="btn btn-primary btn-sm" style={{ marginTop: 0 }} disabled={!archivo || busy || !acreedor}>
          {busy ? "Cargando…" : "Cargar"}
        </button>
      </div>

      {resultado ? (
        <div className="notice" style={{ marginTop: 14 }}>
          {resultado.repetido ? "Ese lote ya se había cargado con el mismo contenido: no se duplicó nada. " : ""}
          {resultado.recibidas} deuda(s) en el archivo: <b>{resultado.aceptadas} aceptadas</b>
          {rechazos.length ? `, ${rechazos.length} rechazadas` : ""}.
          {rechazos.slice(0, 5).map((r) => (
            <div key={r.id_externo}>· {r.id_externo}: {r.errores?.[0]?.mensaje}</div>
          ))}
        </div>
      ) : null}
    </form>
  );
}
