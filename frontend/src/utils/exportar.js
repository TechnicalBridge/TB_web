/**
 * Descargas hechas en el navegador: archivos que el servidor manda (PDF) y
 * planillas que se arman aca con lo que ya esta en pantalla.
 */

/** Guarda un Blob con ese nombre, como si fuera un enlace de descarga. */
export function descargar(blob, nombre) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nombre;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Una planilla para Excel: separada por punto y coma, que es lo que espera
 * Excel en Chile (la coma es el decimal), y con BOM para que respete las
 * tildes. Los montos van sin puntos de miles, para que Excel los lea como
 * numeros y se puedan sumar.
 */
export function descargarCsv(nombre, columnas, filas) {
  const celda = (valor) => {
    const texto = valor === null || valor === undefined ? "" : String(valor);
    return /[";\n]/.test(texto) ? `"${texto.replace(/"/g, '""')}"` : texto;
  };
  const lineas = [columnas.map(celda).join(";"), ...filas.map((fila) => fila.map(celda).join(";"))];
  descargar(new Blob(["﻿" + lineas.join("\r\n") + "\r\n"], { type: "text/csv;charset=utf-8" }), nombre);
}

/** Un monto como lo lee Excel en Chile: sin miles y con coma decimal. */
export const montoParaExcel = (valor, moneda) =>
  moneda === "UF" ? Number(valor).toFixed(2).replace(".", ",") : String(Math.round(Number(valor)));
