import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../store/authStore";
import Logo from "./Logo";
import Chatbot from "./Chatbot";

const debtorLinks = [{ to: "/app", label: "Mis deudas", end: true }];
const creditorLinks = [{ to: "/databridge", label: "Dashboard", end: true }];

export default function Layout({ variant }) {
  const { user, logout } = useAuth();
  const links = variant === "creditor" ? creditorLinks : debtorLinks;
  const brand = variant === "creditor" ? "DATABRIDGE" : "TECHNICAL BRIDGE";
  const sub = variant === "creditor" ? "Core B2B" : "Core B2C";

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
            <b>{user?.name}</b>
            <span>{user?.email}</span>
            <span>{user?.role === "CREDITOR" ? "Acreedor" : "Deudor"}</span>
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
