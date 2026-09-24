import { useState } from "react";
import {
  Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis,
} from "recharts";
import { dinero } from "../utils/formato";

const COLORES = { activas: "#ffd48a", enConvenio: "#3ec6e0", pagadas: "#3dcf70", retiradas: "#5c7185" };
const TOOLTIP = { background: "#0a1628", border: "1px solid rgba(126,223,240,0.2)", borderRadius: 12 };

/** El estado de la cartera: cuenta deudas, asi que no mezcla monedas. */
export function EstadoCartera({ resumen }) {
  if (!resumen) return null;
  const datos = [
    { clave: "activas", nombre: "Pendientes", valor: resumen.activas - resumen.enConvenio },
    { clave: "enConvenio", nombre: "En convenio", valor: resumen.enConvenio },
    { clave: "pagadas", nombre: "Pagadas", valor: resumen.pagadas },
    { clave: "retiradas", nombre: "Retiradas", valor: resumen.retiradas },
  ].filter((d) => d.valor > 0);

  return (
    <div className="card">
      <h3 style={{ marginTop: 0 }}>Estado de la cartera</h3>
      {datos.length === 0 ? (
        <div className="empty">Sin deudas todavía.</div>
      ) : (
        <div style={{ height: 240 }}>
          <ResponsiveContainer width="100%" height="100%">
            <PieChart>
              <Pie data={datos} dataKey="valor" nameKey="nombre" innerRadius={55} outerRadius={85} paddingAngle={3}>
                {datos.map((d) => <Cell key={d.clave} fill={COLORES[d.clave]} stroke="none" />)}
              </Pie>
              <Tooltip contentStyle={TOOLTIP} formatter={(v, n) => [`${v} deuda(s)`, n]} />
              <Legend iconType="circle" wrapperStyle={{ fontSize: 12, color: "#9bb0c4" }} />
            </PieChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}

/**
 * Lo que entro cada dia. Una serie por moneda, con selector: pesos y UF en
 * el mismo eje darian barras que no se pueden comparar.
 */
export function RecuperadoPorDia({ resumen }) {
  const series = resumen?.recuperadoPorDia || {};
  const monedas = Object.keys(series);
  const [elegida, setElegida] = useState(null);
  const moneda = monedas.includes(elegida) ? elegida : monedas[0];
  const datos = (series[moneda] || []).map((p) => ({ dia: p.dia.slice(5), monto: Number(p.monto) }));

  return (
    <div className="card">
      <div className="topbar" style={{ marginBottom: 8 }}>
        <h3 style={{ margin: 0 }}>Recuperado por día</h3>
        {monedas.length > 1 ? (
          <div className="filters" style={{ marginBottom: 0 }}>
            {monedas.map((m) => (
              <button key={m} type="button" className={`chip ${m === moneda ? "on" : ""}`} onClick={() => setElegida(m)}>
                {m}
              </button>
            ))}
          </div>
        ) : null}
      </div>
      {monedas.length === 0 ? (
        <div className="empty">Sin pagos en los últimos 30 días.</div>
      ) : (
        <div style={{ height: 240 }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={datos}>
              <CartesianGrid stroke="rgba(126,223,240,0.12)" vertical={false} />
              <XAxis dataKey="dia" stroke="#9bb0c4" tick={{ fontSize: 10 }} interval={4} />
              <YAxis stroke="#9bb0c4" tick={{ fontSize: 10 }} width={70}
                     tickFormatter={(v) => (moneda === "UF" ? v : `${Math.round(v / 1000)}k`)} />
              <Tooltip contentStyle={TOOLTIP} formatter={(v) => [dinero(v, moneda), "Recuperado"]} />
              <Bar dataKey="monto" fill="#3dcf70" radius={[6, 6, 0, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}
