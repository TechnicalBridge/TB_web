import { NavLink, Outlet } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import Logo from "./Logo";

const links = [
  { to: "/app", label: "Inicio", end: true },
  { to: "/app/pagos", label: "Pagos" },
  { to: "/app/simular", label: "Simular" },
  { to: "/app/ia", label: "IA" },
  { to: "/app/pago", label: "Forma de pago" },
];

export default function Layout() {
  const { user, logout } = useAuth();

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand-row" style={{ marginBottom: 18 }}>
          <Logo size={44} />
          <div>
            <div className="brand-name" style={{ fontSize: 14 }}>DIGITAL BOT</div>
            <div className="brand-sub" style={{ letterSpacing: "0.06em" }}>Verificado</div>
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
            <span>{user?.role === "guest" ? "Modo invitado" : user?.email}</span>
            <span>{user?.plan?.name || "Sin plan activo"}</span>
          </div>
          <button className="btn btn-ghost" onClick={logout}>Cerrar sesión</button>
        </div>
      </aside>
      <div className="main">
        <Outlet />
      </div>
      <nav className="mobile-bar">
        {links.map((l) => (
          <NavLink key={l.to} to={l.to} end={l.end} className={({ isActive }) => (isActive ? "active" : "")}>
            {l.label}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
