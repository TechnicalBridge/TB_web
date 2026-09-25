/**
 * Iconos de trazo, del color del texto que los rodea. Dibujados aca y no
 * traidos de una libreria: son pocos y asi el portal no suma otra dependencia.
 */
function Trazo({ size = 18, children, ...resto }) {
  return (
    <svg className="icono" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
         strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...resto}>
      {children}
    </svg>
  );
}

export const IconoCheck = (p) => <Trazo {...p}><path d="M5 12.5l4.2 4.2L19 7" /></Trazo>;

export const IconoFlecha = (p) => <Trazo {...p}><path d="M5 12h14M13 6l6 6-6 6" /></Trazo>;

export const IconoVolver = (p) => <Trazo {...p}><path d="M19 12H5M11 18l-6-6 6-6" /></Trazo>;

export const IconoSol = (p) => (
  <Trazo {...p}>
    <circle cx="12" cy="12" r="4" />
    <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
  </Trazo>
);

export const IconoLuna = (p) => <Trazo {...p}><path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z" /></Trazo>;

export const IconoChat = (p) => (
  <Trazo {...p}><path d="M21 12a8 8 0 0 1-11.6 7.1L4 20.5l1.4-4.6A8 8 0 1 1 21 12z" /></Trazo>
);

export const IconoCerrar = (p) => <Trazo {...p}><path d="M6 6l12 12M18 6L6 18" /></Trazo>;

export const IconoCandado = (p) => (
  <Trazo {...p}><rect x="4" y="11" width="16" height="10" rx="2.5" /><path d="M8 11V8a4 4 0 0 1 8 0v3" /></Trazo>
);

export const IconoCalendario = (p) => (
  <Trazo {...p}><rect x="3" y="5" width="18" height="16" rx="2.5" /><path d="M3 10h18M8 3v4M16 3v4" /></Trazo>
);

export const IconoDocumento = (p) => (
  <Trazo {...p}><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></Trazo>
);

export const IconoSalir = (p) => (
  <Trazo {...p}><path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3M10 17l5-5-5-5M15 12H4" /></Trazo>
);

export const IconoCartera = (p) => (
  <Trazo {...p}><rect x="3" y="6" width="18" height="14" rx="2.5" /><path d="M16 13h2M3 10h18M7 6V4h10v2" /></Trazo>
);

/** El check que se dibuja solo, para cuando un pago queda listo. */
export function CheckAnimado({ size = 72 }) {
  return (
    <svg className="check-animado" width={size} height={size} viewBox="0 0 52 52" aria-hidden="true">
      <circle className="check-circulo" cx="26" cy="26" r="24" fill="none" />
      <path className="check-trazo" fill="none" d="M15 27l7.5 7.5L38 19" />
    </svg>
  );
}
