// Register page: POST /auth/signup -> {token, user}; stores both in localStorage.

import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { api, setToken, saveUser } from "../api.js";

export default function Register() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ name: "", email: "", password: "" });
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    setBusy(true);
    try {
      const res = await api.signup(form);
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
      <h1>Create an account ✨</h1>
      <div className="card">
        {error && <div className="error">{error}</div>}
        <form className="form-grid" onSubmit={submit}>
          <label className="field">
            Name
            <input type="text" value={form.name} onChange={set("name")} required />
          </label>
          <label className="field">
            Email
            <input type="email" value={form.email} onChange={set("email")} required />
          </label>
          <label className="field">
            Password
            <input
              type="password"
              value={form.password}
              onChange={set("password")}
              minLength={6}
              required
            />
          </label>
          <button className="btn btn-block" disabled={busy}>
            {busy ? "Creating…" : "Sign up"}
          </button>
        </form>
      </div>
      <p className="muted" style={{ textAlign: "center" }}>
        Already have an account? <Link to="/login">Login</Link>
      </p>
    </div>
  );
}
