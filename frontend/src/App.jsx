// ---------------------------------------------------------------------------
// App shell: route table, protected-route guard, and a mobile bottom nav bar.
// Public: /login, /register. Everything else requires a JWT in localStorage.
// ---------------------------------------------------------------------------

import { Routes, Route, NavLink, Navigate, useNavigate } from "react-router-dom";
import { getToken, clearToken, getUser } from "./api.js";
import Login from "./pages/Login.jsx";
import Register from "./pages/Register.jsx";
import Dashboard from "./pages/Dashboard.jsx";
import AddExpense from "./pages/AddExpense.jsx";
import Transactions from "./pages/Transactions.jsx";
import Budgets from "./pages/Budgets.jsx";
import Recurring from "./pages/Recurring.jsx";
import Bank from "./pages/Bank.jsx";
import Forecast from "./pages/Forecast.jsx";

// Redirect to /login when no token is stored.
function Protected({ children }) {
  return getToken() ? children : <Navigate to="/login" replace />;
}

// Redirect away from auth pages when already logged in.
function GuestOnly({ children }) {
  return getToken() ? <Navigate to="/" replace /> : children;
}

const NAV = [
  { to: "/", label: "Home", icon: "🏠", end: true },
  { to: "/add", label: "Add", icon: "➕" },
  { to: "/transactions", label: "Activity", icon: "🧾" },
  { to: "/budgets", label: "Budgets", icon: "💰" },
  { to: "/insights", label: "Forecast", icon: "📈" },
];

function BottomNav() {
  return (
    <nav className="bottom-nav" aria-label="Primary">
      {NAV.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.end}
          className={({ isActive }) => "nav-item" + (isActive ? " active" : "")}
        >
          <span className="nav-icon" aria-hidden="true">
            {item.icon}
          </span>
          <span className="nav-label">{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}

function Header() {
  const navigate = useNavigate();
  const user = getUser();

  const logout = () => {
    clearToken();
    navigate("/login");
  };

  return (
    <header className="app-header">
      <span className="brand">💸 Smart Expense Tracker</span>
      <span className="header-right">
        {user?.name && <span className="user-chip">{user.name}</span>}
        <button className="btn btn-ghost" onClick={logout}>
          Logout
        </button>
      </span>
    </header>
  );
}

export default function App() {
  const authed = !!getToken();

  return (
    <div className="app">
      {authed && <Header />}
      <main className="page">
        <Routes>
          <Route
            path="/login"
            element={
              <GuestOnly>
                <Login />
              </GuestOnly>
            }
          />
          <Route
            path="/register"
            element={
              <GuestOnly>
                <Register />
              </GuestOnly>
            }
          />
          <Route
            path="/"
            element={
              <Protected>
                <Dashboard />
              </Protected>
            }
          />
          <Route
            path="/add"
            element={
              <Protected>
                <AddExpense />
              </Protected>
            }
          />
          <Route
            path="/transactions"
            element={
              <Protected>
                <Transactions />
              </Protected>
            }
          />
          <Route
            path="/budgets"
            element={
              <Protected>
                <Budgets />
              </Protected>
            }
          />
          <Route
            path="/recurring"
            element={
              <Protected>
                <Recurring />
              </Protected>
            }
          />
          <Route
            path="/bank"
            element={
              <Protected>
                <Bank />
              </Protected>
            }
          />
          <Route
            path="/insights"
            element={
              <Protected>
                <Forecast />
              </Protected>
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      {authed && <BottomNav />}
    </div>
  );
}
