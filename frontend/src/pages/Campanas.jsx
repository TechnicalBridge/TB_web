import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { cambiarEstadoCampana, guardarCampana, listarCampanas } from "../api/campanas";
import { opcionesDeCarga } from "../api/deudas";
import { fecha } from "../utils/formato";
import Cargando from "../components/Cargando";
import { IconoAlerta } from "../components/Iconos";

const ESTADO = {
  en_curso: ["En curso", "badge-ok"],
  pausada: ["Pausada", "badge-warn"],
  terminada: ["Terminada", "badge-muted"],
};

const VACIA = { id: "", acreedor: "", nombre: "", inicio: "", fin: "", intentos: "3", cadencia: "1, 4, 11" };

/** "1, 4, 11" -> { dias: [1, 4, 11] }, o el error. Las mismas reglas con que DataBridge la rechaza. */
function leerCadencia(texto) {
  const partes = texto.split(/[,;\s]+/).filter(Boolean);
  if (partes.length === 0) return { error: "Escribe al menos un día." };
  const dias = partes.map(Number);
  if (dias.some((d) => !Number.isInteger(d) || d <= 0)) return { error: "La cadencia son días enteros y mayores que cero." };
  if (dias.some((d, i) => i > 0 && d <= dias[i - 1])) return { error: "Cada día tiene que ser mayor que el anterior." };
  return { dias };
}

/**
 * Lo que la ley atrasa (art. 37 de la Ley 19.496): dos contactos con menos de 2 días
 * entre ellos, o un tercero dentro de la misma semana. No se rechaza: DataBridge lo corre.
 */
function advertencias(dias, intentos) {
  const usados = dias.slice(0, intentos);
  const avisos = [];
  if (usados.some((d, i) => i > 0 && d - usados[i - 1] < 2)) {
    avisos.push("Hay contactos con menos de 2 días entre ellos: la ley pide al menos 2.");
  }
  if (usados.some((d, i) => i > 1 && d - usados[i - 2] < 7)) {
    avisos.push("Hay 3 contactos dentro de una semana: la ley permite 2.");
  }
  if (intentos > dias.length) {
    avisos.push(`La cadencia tiene ${dias.length} ${dias.length === 1 ? "día" : "días"}: se harán ${dias.length} contactos, no ${intentos}.`);
  }
  return avisos;
}

/**
 * Las campañas de la empresa: las crea, las cambia, las pausa y las termina.
 * Entran por el mismo camino que las del contrato, con sus mismas reglas.
 */
export default function Campanas() {
  const [campanas, setCampanas] = useState(null);
  const [acreedores, setAcreedores] = useState([]);
  const [form, setForm] = useState(VACIA);
  const [editando, setEditando] = useState(null);
  const [busy, setBusy] = useState(false);
  const [cambiando, setCambiando] = useState("");
  const [error, setError] = useState("");
  const [hecho, setHecho] = useState("");

  const cargar = () => listarCampanas().then(setCampanas);

  useEffect(() => {
    cargar().catch((err) => {
      setError(err.message);
      setCampanas([]);
    });
    opcionesDeCarga()
      .then((o) => {
        setAcreedores(o.acreedores || []);
        setForm((f) => ({ ...f, acreedor: f.acreedor || o.acreedores?.[0]?.rut || "" }));
      })
      .catch((err) => setError(err.message));
  }, []);

  const cadencia = useMemo(() => leerCadencia(form.cadencia), [form.cadencia]);
  const intentos = Number(form.intentos);
  const intentosValidos = Number.isInteger(intentos) && intentos >= 1 && intentos <= 10;
  const fechasValidas = !form.inicio || !form.fin || form.fin >= form.inicio;
  const avisos = cadencia.dias && intentosValidos ? advertencias(cadencia.dias, intentos) : [];
  const listo = form.nombre.trim() && form.acreedor && cadencia.dias && intentosValidos && fechasValidas;

  const campo = (nombre) => (e) => setForm((f) => ({ ...f, [nombre]: e.target.value }));

  function nueva() {
    setEditando(null);
    setForm({ ...VACIA, acreedor: acreedores[0]?.rut || "" });
  }

  function editar(c) {
    setEditando(c);
    setError("");
    setHecho("");
    setForm({
      id: c.idExterno, acreedor: c.acreedorRut, nombre: c.nombre, inicio: c.inicio || "", fin: c.fin || "",
      intentos: String(c.intentos), cadencia: (c.cadenciaDias || []).join(", "),
    });
    document.getElementById("form-campana")?.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  async function guardar(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setHecho("");
    try {
      const guardada = await guardarCampana({
        id_externo: form.id.trim() || null,
        acreedor_rut: form.acreedor,
        nombre: form.nombre.trim(),
        inicio: form.inicio || null,
        fin: form.fin || null,
        canales: ["correo"],
        intentos,
        cadencia_dias: cadencia.dias,
      });
      setHecho(editando
        ? `Se guardaron los cambios de "${guardada.nombre}".`
        : `Se creó "${guardada.nombre}" (${guardada.idExterno}). Ya la puedes elegir al cargar cartera.`);
      nueva();
      await cargar();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function cambiar(c, estado, pregunta) {
    if (pregunta && !window.confirm(pregunta)) return;
    setCambiando(c.idExterno);
    setError("");
    setHecho("");
    try {
      await cambiarEstadoCampana(c.idExterno, estado);
      if (editando?.idExterno === c.idExterno) nueva();
      await cargar();
    } catch (err) {
      setError(err.message);
    } finally {
      setCambiando("");
    }
  }

  if (campanas === null) return <Cargando tarjetas={2} />;

  return (
    <div>
      <header className="topbar aparece">
        <div>
          <h1>Campañas</h1>
          <p>Tú decides cuándo y cuántas veces se contacta al deudor; DataBridge le escribe dentro de lo que permite la ley.</p>
        </div>
      </header>
      {error ? <div className="error">{error}</div> : null}
      {hecho ? <div className="notice">{hecho}</div> : null}

      <div className="grid-2">
        <div className="card aparece" style={{ "--i": 1 }}>
          <h3>Tus campañas</h3>
          {campanas.length === 0 ? (
            <div className="empty">Todavía no hay campañas. Crea una y elígela al cargar la cartera.</div>
          ) : (
            <div className="campanas">
              {campanas.map((c) => {
                const [estado, clase] = ESTADO[c.estado] || [c.estado, "badge-muted"];
                const ocupada = cambiando === c.idExterno;
                return (
                  <div key={c.idExterno} className={`campana${editando?.idExterno === c.idExterno ? " elegida" : ""}`}>
                    <div className="campana-cabeza">
                      <div className="fila-que">
                        <b>{c.nombre}</b>
                        <span>{c.propia ? "Cartera propia" : c.acreedor} · <span className="mono">{c.idExterno}</span></span>
                      </div>
                      <span className={`badge ${clase}`}>{estado}</span>
                    </div>
                    <div className="totales campana-cifras">
                      <span>{fecha(c.inicio)} – {c.fin ? fecha(c.fin) : "sin fin"}</span>
                      <span>{c.intentos} {c.intentos === 1 ? "contacto" : "contactos"}, días {(c.cadenciaDias || []).join(", ")}</span>
                    </div>
                    <div className="totales campana-cifras">
                      <span><b className="num">{c.deudas}</b> {c.deudas === 1 ? "deuda" : "deudas"}</span>
                      <span><b className="num">{c.contactos}</b> {c.contactos === 1 ? "contacto enviado" : "contactos enviados"}</span>
                      <span><b className="num">{c.saldadas}</b> {c.saldadas === 1 ? "pagada" : "pagadas"}</span>
                    </div>
                    {c.estado !== "terminada" ? (
                      <div className="fila-acciones">
                        <button type="button" className="btn btn-ghost btn-sm" disabled={ocupada} onClick={() => editar(c)}>
                          Cambiar
                        </button>
                        {c._links?.pausar ? (
                          <button type="button" className="btn btn-soft btn-sm" disabled={ocupada}
                                  onClick={() => cambiar(c, "pausada")}>Pausar</button>
                        ) : null}
                        {c._links?.reanudar ? (
                          <button type="button" className="btn btn-soft btn-sm" disabled={ocupada}
                                  onClick={() => cambiar(c, "en_curso")}>Reanudar</button>
                        ) : null}
                        {c._links?.terminar ? (
                          <button type="button" className="btn btn-peligro btn-sm" disabled={ocupada}
                                  onClick={() => cambiar(c, "terminada",
                                    `¿Terminar "${c.nombre}"? No se contacta a nadie más por ella y no se puede reanudar.`)}>
                            Terminar
                          </button>
                        ) : null}
                      </div>
                    ) : null}
                  </div>
                );
              })}
            </div>
          )}
        </div>

        <div className="card aparece" id="form-campana" style={{ "--i": 2 }}>
          <h3>{editando ? `Cambiar "${editando.nombre}"` : "Nueva campaña"}</h3>
          <form onSubmit={guardar}>
            <div className="field">
              <label htmlFor="c-nombre">Nombre</label>
              <input id="c-nombre" value={form.nombre} maxLength={120} onChange={campo("nombre")}
                     placeholder="Arriendos octubre" required />
            </div>
            <div className="field">
              <label htmlFor="c-acreedor">Acreedor</label>
              <select id="c-acreedor" value={form.acreedor} onChange={campo("acreedor")}
                      disabled={Boolean(editando) || acreedores.length === 0}>
                {acreedores.length === 0 ? <option value="">Sin acreedores con mandato vigente</option> : null}
                {acreedores.map((a) => <option key={a.rut} value={a.rut}>{a.nombre}</option>)}
              </select>
            </div>
            <div className="campos-2">
              <div className="field">
                <label htmlFor="c-inicio">Inicio</label>
                <input id="c-inicio" type="date" value={form.inicio} onChange={campo("inicio")} />
              </div>
              <div className="field">
                <label htmlFor="c-fin">Fin</label>
                <input id="c-fin" type="date" value={form.fin} min={form.inicio || undefined} onChange={campo("fin")}
                       required={Boolean(editando?.fin)} />
              </div>
            </div>
            {!fechasValidas ? <div className="error">El fin no puede ser antes del inicio.</div> : null}
            <div className="campos-2">
              <div className="field">
                <label htmlFor="c-intentos">Contactos</label>
                <input id="c-intentos" type="number" min={1} max={10} step={1} value={form.intentos}
                       onChange={campo("intentos")} required />
              </div>
              <div className="field">
                <label htmlFor="c-cadencia">Días de contacto</label>
                <input id="c-cadencia" value={form.cadencia} onChange={campo("cadencia")} inputMode="numeric"
                       placeholder="1, 4, 11, 25, 45" aria-describedby="c-cadencia-ayuda" required />
              </div>
            </div>
            <p className="hint" id="c-cadencia-ayuda" style={{ marginTop: -6, marginBottom: 14 }}>
              Los días se cuentan desde que la deuda entra a DataBridge. Canal: correo.
            </p>
            {!intentosValidos ? <div className="error">Los contactos van de 1 a 10.</div> : null}
            {cadencia.error ? <div className="error">{cadencia.error}</div> : null}
            {avisos.length > 0 ? (
              <div className="aviso bloque" role="status">
                <IconoAlerta />
                <div>
                  <b>La ley va a atrasar algunos contactos</b>
                  {avisos.map((a) => <span key={a} className="sub">{a}</span>)}
                </div>
              </div>
            ) : null}
            {editando ? null : (
              <div className="field">
                <label htmlFor="c-id">Id en tu sistema (opcional)</label>
                <input id="c-id" value={form.id} maxLength={64} onChange={campo("id")} placeholder="Si lo dejas vacío, DataBridge le pone uno" />
              </div>
            )}
            <button className="btn btn-primary btn-block" disabled={busy || !listo}>
              {busy ? <><span className="girando" /> Guardando…</> : editando ? "Guardar cambios" : "Crear campaña"}
            </button>
            {editando ? (
              <button type="button" className="btn btn-ghost btn-block" style={{ marginTop: 8 }} onClick={nueva}>
                Cancelar
              </button>
            ) : null}
          </form>
          <p className="hint">
            Para usarla, elígela al subir la planilla en <Link className="link-btn" to="/databridge/cargar">Cargar cartera</Link>.
            Se deja de contactar a quien paga, repacta, reclama o sale de la cobranza.
          </p>
        </div>
      </div>
    </div>
  );
}
