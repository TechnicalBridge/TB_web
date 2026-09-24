import { NavLink, Outlet } from "react-router-dom";
import { rutLegible } from "../utils/formato";
import { useAuth } from "../store/authStore";
import Logo from "./Logo";
import Chatbot from "./Chatbot";

const debtorLinks = [{ to: "/app", label: "Mis pagos", end: true }];
const creditorLinks = [{ to: "/databridge", label: "Cartera", end: true }];

export default function Layout({ variant }) {
  const { user, logout } = useAuth();
  const links = variant === "creditor" ? creditorLinks : debtorLinks;
  const brand = variant === "creditor" ? "DATABRIDGE" : "TECHNICAL BRIDGE";
  const sub = variant === "creditor" ? "Portal de empresas" : "Portal de pago";

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row" style={{ marginBottom: 18 }}>
          <Logo size={44} />
          <div>
            <div className="brand-name" style={{ fontSize: 13 }}>{brand}</div>
            <div className="brand-sub" style={{ letterSpacing: "0.06em" }}>{sub}</div>
          </div>
        </div>
        {links.map((l) => (
          <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => `nav-link${isActive ? " active" : ""}`}>
            {l.label}
          </NavLink>
        ))}
        <div className="sidebar-foot">
          <div className="user-chip">
            {user?.role === "CREDITOR" ? (
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
          <button className="btn btn-ghost" onClick={logout}>Cerrar sesión</button>
        </div>
      </aside>
      <div className="main">
        <Outlet />
      </div>
      {variant === "debtor" ? <Chatbot /> : null}
      <nav className="mobile-bar" style={{ gridTemplateColumns: "1fr 1fr" }}>
        {links.map((l) => (
          <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => (isActive ? "active" : "")}>
            {l.label}
          </NavLink>
        ))}
        <button type="button" onClick={logout} style={{ background: "transparent", border: 0, color: "inherit" }}>
          Salir
        </button>
      </nav>
    </div>
  );
}
