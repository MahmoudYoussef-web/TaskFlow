import { useEffect, useState } from "react";
import { BrowserRouter, NavLink, Navigate, Route, Routes } from "react-router-dom";
import { AuthProvider, useAuth } from "./auth";
import { Board } from "./Board";
import { Bell, JobsPage, Login, Overview, Register } from "./Pages";
import "./theme.css";

function Shell() {
  const { user, loading, logout } = useAuth();
  const [theme, setTheme] = useState(
    localStorage.getItem("tf_theme") ?? "light"
  );
  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("tf_theme", theme);
  }, [theme]);

  if (loading) return <div className="empty"><p>Loading TaskFlow…</p></div>;

  return (
    <>
      <div className="topbar">
        <b style={{ fontSize: 17 }}>TaskFlow</b>
        {user && (
          <nav>
            <NavLink to="/" end className={({ isActive }) => (isActive ? "active" : "")}>Board</NavLink>
            <NavLink to="/overview" className={({ isActive }) => (isActive ? "active" : "")}>Overview</NavLink>
            {user.role === "ADMIN" && (
              <NavLink to="/jobs" className={({ isActive }) => (isActive ? "active" : "")}>Jobs</NavLink>
            )}
          </nav>
        )}
        <div style={{ marginLeft: "auto" }} className="row">
          <button onClick={() => setTheme(theme === "light" ? "dark" : "light")}>
            {theme === "light" ? "Dark" : "Light"}
          </button>
          {user && (
            <>
              <Bell />
              <span className="avatar" title={user.email}>
                {user.displayName.slice(0, 1).toUpperCase()}
              </span>
              <button onClick={logout}>Log out</button>
            </>
          )}
        </div>
      </div>
      <Routes>
        <Route path="/login" element={user ? <Navigate to="/" /> : <Login />} />
        <Route path="/register" element={user ? <Navigate to="/" /> : <Register />} />
        <Route path="/" element={user ? <Board /> : <Navigate to="/login" />} />
        <Route path="/overview" element={user ? <Overview /> : <Navigate to="/login" />} />
        <Route path="/jobs" element={user ? <JobsPage /> : <Navigate to="/login" />} />
      </Routes>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Shell />
      </AuthProvider>
    </BrowserRouter>
  );
}
