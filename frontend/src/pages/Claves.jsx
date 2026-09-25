import { useEffect, useState } from "react";
import { emitirClave, listarClaves, revocarClave } from "../api/claves";
import { fecha } from "../utils/formato";
import Cargando from "../components/Cargando";
import { IconoCheck, IconoCopiar } from "../components/Iconos";

/**
 * Las claves con que el sistema de la empresa entrega cartera por la API.
 * Se emiten y se revocan aca; cada clave se ve una sola vez, al emitirla.
 */
export default function Claves() {
  const [claves, setClaves] = useState(null);
  const [nombre, setNombre] = useState("");
  const [nueva, setNueva] = useState(null);
  const [copiada, setCopiada] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const cargar = () => listarClaves().then(setClaves);

  useEffect(() => {
    cargar().catch((err) => {
      setError(err.message);
      setClaves([]);
    });
  }, []);

  async function emitir(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setCopiada(false);
    try {
      setNueva(await emitirClave(nombre.trim()));
      setNombre("");
      await cargar();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function revocar(clave) {
    if (!window.confirm(`¿Revocar "${clave.nombre}"? El sistema que la use deja de poder entregar cartera.`)) return;
    setError("");
    try {
      await revocarClave(clave.id);
      await cargar();
    } catch (err) {
      setError(err.message);
    }
  }

  async function copiar() {
    try {
      await navigator.clipboard.writeText(nueva.clave);
      setCopiada(true);
    } catch {
      setError("No se pudo copiar: selecciónala y cópiala a mano.");
    }
  }

  if (claves === null) return <Cargando tarjetas={1} />;

  return (
    <div>
      <header className="topbar aparece">
        <div>
          <h1>Claves de API</h1>
          <p>Con ellas tu sistema entrega cartera por la API del contrato. Cada clave se ve una sola vez.</p>
        </div>
      </header>
      {error ? <div className="error">{error}</div> : null}

      {nueva ? (
        <div className="clave-nueva bloque">
          <b>Guarda esta clave ahora: no se puede volver a mostrar.</b>
          <div className="clave-texto">
            <code>{nueva.clave}</code>
            <button type="button" className="btn btn-ghost btn-sm" onClick={copiar}>
              {copiada ? <IconoCheck size={16} /> : <IconoCopiar size={16} />}
              {copiada ? "Copiada" : "Copiar"}
            </button>
          </div>
          <span className="sub">Si la pierdes, emite otra y revoca esta.</span>
        </div>
      ) : null}

      <div className="grid-2">
        <div className="card aparece" style={{ "--i": 1 }}>
          <h3>Tus claves</h3>
          {claves.length === 0 ? (
            <div className="empty">Todavía no hay claves. Emite una para conectar tu sistema.</div>
          ) : (
            <div className="tabla-scroll">
              <table className="table">
                <thead>
                  <tr><th>Nombre</th><th>Creada</th><th>Último uso</th><th>Estado</th><th /></tr>
                </thead>
                <tbody>
                  {claves.map((c) => (
                    <tr key={c.id}>
                      <td>{c.nombre}<span className="sub mono">{c.prefijo}…</span></td>
                      <td>{fecha(c.creadaEn)}</td>
                      <td>{c.ultimoUso ? fecha(c.ultimoUso) : "Nunca"}</td>
                      <td>
                        {c.activa
                          ? <span className="badge badge-ok">Activa</span>
                          : <span className="badge badge-muted">Revocada</span>}
                      </td>
                      <td>
                        {c.activa ? (
                          <button type="button" className="btn btn-peligro btn-sm" onClick={() => revocar(c)}>Revocar</button>
                        ) : null}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="card aparece" style={{ "--i": 2 }}>
          <h3>Emitir una clave</h3>
          <form onSubmit={emitir}>
            <div className="field">
              <label htmlFor="nombre-clave">Para qué sistema es</label>
              <input id="nombre-clave" value={nombre} maxLength={80} onChange={(e) => setNombre(e.target.value)}
                     placeholder="Servidor de cobranza" required />
            </div>
            <button className="btn btn-primary btn-block" disabled={busy || !nombre.trim()}>
              {busy ? <><span className="girando" /> Emitiendo…</> : "Emitir clave"}
            </button>
          </form>
          <p className="hint">Tu sistema la manda en cada entrega:</p>
          <pre className="codigo">{`Authorization: Bearer tbk_...`}</pre>
        </div>
      </div>
    </div>
  );
}
