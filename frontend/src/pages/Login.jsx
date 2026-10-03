// Login page: POST /auth/login -> {token, user}; stores both in localStorage.

import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, setToken, saveUser } from "../api.js";

export default function Login() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await api.login(form);
      setToken(res.token);
      if (res.user) saveUser(res.user);
      navigate("/");
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h1>Welcome back 👋</h1>
      <div className="card">
        {error && <div className="error">{error}</div>}
        <form className="form-grid" onSubmit={submit}>
          <label className="field">
            Email
            <input type="email" value={form.email} onChange={set("email")} required />
          </label>
          <label className="field">
            Password
            <input type="password" value={form.password} onChange={set("password")} required />
          </label>
          <button className="btn btn-block" disabled={busy}>
            {busy ? "Logging in…" : "Login"}
          </button>
        </form>
      </div>
      <p className="muted" style={{ textAlign: "center" }}>
        No account? <Link to="/register">Sign up</Link>
      </p>
    </div>
  );
}
