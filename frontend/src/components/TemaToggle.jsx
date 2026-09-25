import { useTema } from "../store/temaStore";
import { IconoLuna, IconoSol } from "./Iconos";

/** Cambia entre el tema claro (crema) y el oscuro (carbon). */
export default function TemaToggle({ flotante = false, conTexto = false }) {
  const { tema, alternar } = useTema();
  const oscuro = tema === "dark";
  const etiqueta = oscuro ? "Usar tema claro" : "Usar tema oscuro";

  return (
    <button type="button" onClick={alternar} title={etiqueta} aria-label={etiqueta}
            className={`tema-toggle${flotante ? " tema-flotante" : ""}${conTexto ? " con-texto" : ""}`}>
      <span className="tema-icono" key={tema}>{oscuro ? <IconoSol /> : <IconoLuna />}</span>
      {conTexto ? <span>{oscuro ? "Tema claro" : "Tema oscuro"}</span> : null}
    </button>
  );
}
