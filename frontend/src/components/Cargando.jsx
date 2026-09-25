/** Mientras llega la pagina: la forma de lo que viene, en vez de un "Cargando…" suelto. */
export default function Cargando({ tarjetas = 2 }) {
  return (
    <div aria-busy="true" aria-label="Cargando">
      <div className="esqueleto" style={{ height: 14, width: 120, marginBottom: 12 }} />
      <div className="esqueleto" style={{ height: 38, width: "min(360px, 70%)", marginBottom: 28 }} />
      <div className="grid-3 bloque">
        {[0, 1, 2].map((i) => <div key={i} className="esqueleto" style={{ height: 96, borderRadius: 20 }} />)}
      </div>
      {Array.from({ length: tarjetas }, (_, i) => (
        <div key={i} className="esqueleto" style={{ height: 190, borderRadius: 20, marginBottom: 18 }} />
      ))}
    </div>
  );
}
