import { NavLink, Outlet } from "react-router-dom";
import { rutLegible } from "../utils/formato";
import { useAuth } from "../store/authStore";
import Logo from "./Logo";
import Chatbot from "./Chatbot";
import TemaToggle from "./TemaToggle";
import { IconoCartera, IconoDocumento, IconoSalir } from "./Iconos";

const debtorLinks = [{ to: "/app", label: "Mis deudas", end: true, Icono: IconoDocumento }];
const creditorLinks = [{ to: "/databridge", label: "Cartera", end: true, Icono: IconoCartera }];

/** Las iniciales para el circulo del usuario: "Camila Reyes" -> "CR". */
function iniciales(nombre) {
  return String(nombre || "")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0].toUpperCase())
    .join("");
}

export default function Layout({ variant }) {
  const { user, logout } = useAuth();
  const esEmpresa = variant === "creditor";
  const links = esEmpresa ? creditorLinks : debtorLinks;

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row">
          <Logo size={42} />
          <div>
            <div className="brand-name">{esEmpresa ? "DataBridge" : "Technical Bridge"}</div>
            <div className="brand-sub">{esEmpresa ? "Portal de empresas" : "Portal de pago"}</div>
          </div>
        </div>
        {links.map(({ to, label, end, Icono }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
            <Icono />
            {label}
          </NavLink>
        ))}
        <div className="sidebar-foot">
          <TemaToggle conTexto />
          <div className="user-chip">
            <span className="avatar">{esEmpresa ? iniciales(user?.nombre) : "TB"}</span>
            <div>
              {esEmpresa ? (
                <>
                  <b>{user?.nombre}</b>
                  <span>{user?.correo}</span>
                </>
              ) : (
                <>
                  <b>RUT {rutLegible(user?.rut)}</b>
                  <span>Sesión con código de acceso</span>
                </>
              )}
            </div>
          </div>
          <button className="btn btn-ghost btn-block" onClick={logout}>
            <IconoSalir />
            Cerrar sesión
          </button>
        </div>
      </aside>
      <main className="main">
        <Outlet />
      </main>
      {esEmpresa ? null : <Chatbot />}
      <nav className="mobile-bar">
        {links.map(({ to, label, end, Icono }) => (
          <NavLink key={to} to={to} end={end} className={({ isActive }) => (isActive ? "active" : "")}>
            <Icono />
            {label}
          </NavLink>
        ))}
        <TemaToggle conTexto />
        <button type="button" onClick={logout}>
          <IconoSalir />
          Salir
        </button>
      </nav>
    </div>
  );
}
