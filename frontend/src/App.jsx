import { Navigate, Route, Routes } from "react-router-dom";
import { useAuth } from "./store/authStore";
import Layout from "./components/Layout";
import Landing from "./pages/Landing";
import Login from "./pages/Login";
import Magic from "./pages/Magic";
import Debts from "./pages/Debts";
import Repact from "./pages/Repact";
import Pay from "./pages/Pay";
import Pasarela from "./pages/Pasarela";
import DataBridge from "./pages/DataBridge";

function Guard({ children, role }) {
  const { user, ready, homeFor } = useAuth();
  if (!ready) return <div className="main">Cargando Technical Bridge…</div>;
  if (!user) return <Navigate to="/" replace />;
  if (role && user.role !== role) return <Navigate to={homeFor(user)} replace />;
  return children;
}

function PublicOnly({ children }) {
  const { user, ready, homeFor } = useAuth();
  if (!ready) return <div className="main">Cargando Technical Bridge…</div>;
  if (user) return <Navigate to={homeFor(user)} replace />;
  return children;
}

export default function App() {
  return (
    <Routes>
      <Route
        path="/"
        element={
          <PublicOnly>
            <Landing />
          </PublicOnly>
        }
      />
      <Route
        path="/login"
        element={
          <PublicOnly>
            <Login portal="TB" />
          </PublicOnly>
        }
      />
      <Route
        path="/databridge/login"
        element={
          <PublicOnly>
            <Login portal="DATABRIDGE" />
          </PublicOnly>
        }
      />
      <Route path="/magic" element={<Magic />} />
      <Route path="/pasarela/:id" element={<Pasarela />} />
      <Route
        path="/app"
        element={
          <Guard role="DEBTOR">
            <Layout variant="debtor" />
          </Guard>
        }
      >
        <Route index element={<Debts />} />
        <Route path="repactar/:id" element={<Repact />} />
        <Route path="pagar/:id" element={<Pay />} />
      </Route>
      <Route
        path="/databridge"
        element={
          <Guard role="CREDITOR">
            <Layout variant="creditor" />
          </Guard>
        }
      >
        <Route index element={<DataBridge />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
