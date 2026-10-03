// ---------------------------------------------------------------------------
// Budgets page:
//  - List of monthly budgets rendered with <BudgetBar/> progress bars.
//  - Alerts section from GET /budgets/alerts (NEAR / EXCEEDED).
//  - Add / edit budget form (category + monthly limit).
// ---------------------------------------------------------------------------

import { useEffect, useState } from "react";
import { api, CATEGORIES } from "../api.js";
import BudgetBar from "../components/BudgetBar.jsx";

export default function Budgets() {
  const [budgets, setBudgets] = useState([]);
  const [alerts, setAlerts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(null); // budget being edited (null = add mode)
  const [form, setForm] = useState({ category: "Food", limit: "" });

  const load = async () => {
  setLoading(true);
  setError("");

  try {
    const [b, a] = await Promise.all([
      api.listBudgets(),
      api.budgetAlerts(),
    ]);

    const budgetList = Array.isArray(b) ? b : b.content ?? [];
    const alertList = Array.isArray(a) ? a : a.alerts ?? [];

    // Merge calculated spending information into each budget
    const mergedBudgets = budgetList.map((budget) => {
      const alert = alertList.find(
        (a) => a.category === budget.category
      );

      return {
        ...budget,
        spent: alert?.spent ?? 0,
        percentUsed: alert?.percentUsed ?? 0,
        status: alert?.status ?? "OK",
      };
    });

    setBudgets(mergedBudgets);
    setAlerts(alertList);
  } catch (err) {
    setError(err.message);
  } finally {
    setLoading(false);
  }
};

  useEffect(() => {
    load();
  }, []);

  const startEdit = (b) => {
  setEditing(b);
  setForm({
    category: b.category,
    limit: b.monthlyLimit ?? b.limit ?? ""
  });
};

  const cancelEdit = () => {
    setEditing(null);
    setForm({ category: "Food", limit: "" });
  };

  const submit = async (e) => {
    e.preventDefault();
    setError("");
    const limit = parseFloat(form.limit);
    if (!limit || limit <= 0) {
      setError("Please enter a valid monthly limit.");
      return;
    }
    try {
      if (editing) {
  await api.updateBudget(editing.id, {
    category: form.category,
    monthlyLimit: limit
  });
} else {
  await api.createBudget({
    category: form.category,
    monthlyLimit: limit
  });
}
      cancelEdit();
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  const remove = async (id) => {
    if (!window.confirm("Delete this budget?")) return;
    try {
      await api.deleteBudget(id);
      await load();
    } catch (err) {
      setError(err.message);
    }
  };

  if (loading) return <div className="spinner">Loading budgets…</div>;

  return (
    <div>
      <h1>Budgets</h1>
      {error && <div className="error">{error}</div>}

      {/* ---- alerts ---- */}
      {alerts.length > 0 && (
        <div className="card">
          <h2>⚠️ Alerts</h2>
          {alerts.map((a, i) => (
            <div className={`alert${a.status === "EXCEEDED" ? " exceeded" : ""}`} key={i}>
              <strong>{a.category}</strong> — {a.message || a.status}
            </div>
          ))}
        </div>
      )}

      {/* ---- budget list ---- */}
      <div className="card">
        <h2>Monthly budgets</h2>
        {budgets.length === 0 && <p className="muted">No budgets yet — add one below.</p>}
        {budgets.map((b) => (
          <div key={b.id}>
            <BudgetBar budget={b} />
            <div className="txn-actions" style={{ marginBottom: "0.5rem" }}>
              <button className="btn btn-secondary btn-sm" onClick={() => startEdit(b)}>
                Edit
              </button>
              <button className="btn btn-danger btn-sm" onClick={() => remove(b.id)}>
                Delete
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* ---- add / edit form ---- */}
      <div className="card">
        <h2>{editing ? "Edit budget" : "Add budget"}</h2>
        <form className="form-grid" onSubmit={submit}>
          <div className="row-2">
            <label className="field">
              Category
              <select value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })}>
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Monthly limit (₹)
              <input
                type="number"
                min="0"
                step="0.01"
                value={form.limit}
                onChange={(e) => setForm({ ...form, limit: e.target.value })}
                required
              />
            </label>
          </div>
          <div className="row-2">
            <button className="btn" type="submit">
              {editing ? "Update" : "Add"}
            </button>
            {editing && (
              <button type="button" className="btn btn-secondary" onClick={cancelEdit}>
                Cancel
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  );
}
