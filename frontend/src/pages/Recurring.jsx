// ---------------------------------------------------------------------------
// Recurring page: list + add/edit recurring expenses
// (subscriptions, rent, etc.). CRUD on /api/recurring.
// ---------------------------------------------------------------------------

import { useEffect, useState } from "react";
import { api, CATEGORIES, PAYMENT_MODES } from "../api.js";

const FREQUENCIES = ["daily", "weekly", "monthly", "yearly"];

const emptyForm = {
  merchant: "",
  amount: "",
  category: "Subscriptions",
  paymentMode: "UPI",
  frequency: "monthly",
  nextDueDate: new Date().toISOString().slice(0, 10),
  active: true,
};

export default function Recurring() {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(emptyForm);

  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await api.listRecurring();
      setItems(Array.isArray(data) ? data : data.content ?? []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const set = (key) => (e) => {
    const v = e.target.type === "checkbox" ? e.target.checked : e.target.value;
    setForm((f) => ({ ...f, [key]: v }));
  };

  const startEdit = (item) => {
    setEditing(item);
    setForm({ ...emptyForm, ...item, amount: item.amount });
  };

  const cancel = () => {
    setEditing(null);
    setForm(emptyForm);
  };

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    const amount = parseFloat(form.amount);
    if (!amount || amount <= 0) {
      setError("Please enter a valid amount.");
      return;
    }
    try {
      const payload = { ...form, amount };
      if (editing) await api.updateRecurring(editing.id, payload);
      else await api.createRecurring(payload);
      cancel();
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  const remove = async (id) => {
    if (!window.confirm("Delete this recurring expense?")) return;
    try {
      await api.deleteRecurring(id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) return <div className="spinner">Loading…</div>;

  return (
    <div>
      <h1>Recurring</h1>
      {error && <div className="error">{error}</div>}

      <div className="card">
        <h2>Upcoming / active</h2>
        {items.length === 0 && <p className="muted">No recurring expenses set up.</p>}
        {items.map((r) => (
          <div className="txn" key={r.id} style={{ alignItems: "flex-start" }}>
            <div className="txn-icon">🔁</div>
            <div className="txn-main">
              <div className="txn-title">{r.merchant || r.category}</div>
              <div className="txn-sub">
                <span className="chip">{r.frequency}</span> · next due {r.nextDueDate} ·{" "}
                {r.active ? "active" : "paused"}
              </div>
              <div className="txn-actions" style={{ marginTop: "0.4rem" }}>
                <button className="btn btn-secondary btn-sm" onClick={() => startEdit(r)}>
                  Edit
                </button>
                <button className="btn btn-danger btn-sm" onClick={() => remove(r.id)}>
                  Delete
                </button>
              </div>
            </div>
            <div className="txn-amount">₹{Number(r.amount).toFixed(0)}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <h2>{editing ? "Edit recurring" : "Add recurring expense"}</h2>
        <form className="form-grid" onSubmit={submit}>
          <label className="field">
            Merchant / description
            <input type="text" value={form.merchant} onChange={set("merchant")} required />
          </label>
          <div className="row-2">
            <label className="field">
              Amount (₹)
              <input type="number" step="0.01" min="0" value={form.amount} onChange={set("amount")} required />
            </label>
            <label className="field">
              Frequency
              <select value={form.frequency} onChange={set("frequency")}>
                {FREQUENCIES.map((f) => (
                  <option key={f} value={f}>
                    {f}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className="row-2">
            <label className="field">
              Category
              <select value={form.category} onChange={set("category")}>
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Payment mode
              <select value={form.paymentMode} onChange={set("paymentMode")}>
                {PAYMENT_MODES.map((m) => (
                  <option key={m} value={m}>
                    {m}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <label className="field">
            Next due date
            <input type="date" value={form.nextDueDate} onChange={set("nextDueDate")} required />
          </label>
          <label className="field" style={{ flexDirection: "row", alignItems: "center" }}>
            <input type="checkbox" checked={form.active} onChange={set("active")} style={{ width: "auto" }} />
            Active
          </label>
          <div className="row-2">
            <button className="btn" type="submit">
              {editing ? "Update" : "Add"}
            </button>
            {editing && (
              <button type="button" className="btn btn-secondary" onClick={cancel}>
                Cancel
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
