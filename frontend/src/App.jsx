import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./context/AuthContext";
import Layout from "./components/Layout";
import Auth from "./pages/Auth";
import Dashboard from "./pages/Dashboard";
import Payments from "./pages/Payments";
import Simulate from "./pages/Simulate";
import Assistant from "./pages/Assistant";
import Checkout from "./pages/Checkout";

function Guard({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return <div className="main">Cargando DIGITAL BOT…</div>;
  if (!user) return <Navigate to="/" replace />;
  return children;
}

function PublicOnly({ children }) {
  const { user, ready } = useAuth();
  if (!ready) return <div className="main">Cargando DIGITAL BOT…</div>;
  if (user) return <Navigate to="/app" replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <PublicOnly>
            <Auth />
          </PublicOnly>
        }
      />
      <Route
        path="/app"
        element={
          <Guard>
            <Layout />
          </Guard>
        }
      >
        <Route index element={<Dashboard />} />
        <Route path="pagos" element={<Payments />} />
        <Route path="simular" element={<Simulate />} />
        <Route path="ia" element={<Assistant />} />
        <Route path="pago" element={<Checkout />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
