import { create } from "zustand";

const CLAVE = "tb-tema";
const COLOR_DE_BARRA = { light: "#FBF6EC", dark: "#141316" };

/**
 * El tema: crema y verde bosque, o negro carbon con morado.
 *
 * Lo decide primero el script de index.html, antes de pintar, para que no
 * haya destello al cargar. Mientras la persona no elija, sigue al sistema; al
 * elegir, su eleccion se recuerda en este navegador.
 */
function guardado() {
  try {
    const valor = localStorage.getItem(CLAVE);
    return valor === "light" || valor === "dark" ? valor : null;
  } catch {
    return null;
  }
}

function delSistema() {
  return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}

function aplicar(tema) {
  document.documentElement.setAttribute("data-theme", tema);
  document.querySelector('meta[name="theme-color"]')?.setAttribute("content", COLOR_DE_BARRA[tema]);
}

export const useTema = create((set, get) => ({
  tema: guardado() || delSistema(),
  alternar: () => {
    const tema = get().tema === "dark" ? "light" : "dark";
    try {
      localStorage.setItem(CLAVE, tema);
    } catch {
      /* sin almacenamiento, el tema dura lo que la pestana */
    }
    //  Mientras dura el cambio, todo cambia de color a la vez (index.css).
    const raiz = document.documentElement;
    raiz.classList.add("tema-cambiando");
    setTimeout(() => raiz.classList.remove("tema-cambiando"), 450);
    aplicar(tema);
    set({ tema });
  },
}));

aplicar(useTema.getState().tema);

//  Si la persona no eligio, el portal cambia con el sistema.
window.matchMedia?.("(prefers-color-scheme: dark)").addEventListener?.("change", (e) => {
  if (guardado()) return;
  const tema = e.matches ? "dark" : "light";
  aplicar(tema);
  useTema.setState({ tema });
});
