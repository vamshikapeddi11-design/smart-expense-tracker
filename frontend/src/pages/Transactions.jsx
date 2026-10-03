// ---------------------------------------------------------------------------
// Transactions page: filterable expense list with edit / delete.
// Each row has a "Correct category" dropdown -> POST /expenses/{id}/correct.
// Backend note: those corrections train the ML merchant_map, so future
// auto-categorization keeps improving from user feedback.
// ---------------------------------------------------------------------------

import { useEffect, useMemo, useState } from "react";
import { api, CATEGORIES } from "../api.js";
import ExpenseForm from "../components/ExpenseForm.jsx";

export default function Transactions() {
  const [txns, setTxns] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [editing, setEditing] = useState(null);
  const [correcting, setCorrecting] = useState(null); // row id -> pending category

  // Filters
  const now = new Date();
  const [month, setMonth] = useState(`${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`);
  const [category, setCategory] = useState("All");
  const [search, setSearch] = useState("");

  const load = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await api.listExpenses();
      setTxns(Array.isArray(data) ? data : data.content ?? data.items ?? []);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const filtered = useMemo(() => {
    return txns.filter((t) => {
      if (month && !String(t.date).startsWith(month)) return false;
      if (category !== "All" && t.category !== category) return false;
      if (search) {
        const hay = `${t.merchant ?? ""} ${t.notes ?? ""} ${t.category ?? ""}`.toLowerCase();
        if (!hay.includes(search.toLowerCase())) return false;
      }
      return true;
    });
  }, [txns, month, category, search]);

  const total = useMemo(
    () => filtered.reduce((sum, t) => sum + Number(t.amount || 0), 0),
    [filtered]
  );

  const remove = async (id) => {
    if (!window.confirm("Delete this expense?")) return;
    try {
      await api.deleteExpense(id);
      setTxns((list) => list.filter((t) => t.id !== id));
    } catch (err) {
      setError(err.message);
    }
  };

  const saveEdit = async (payload) => {
    await api.updateExpense(editing.id, payload);
    setEditing(null);
    await load();
  };

  // Send a manual category correction so the backend can learn from it.
  const correct = async (txn, newCategory) => {
    setCorrecting(txn.id);
    try {
      await api.correctCategory(txn.id, newCategory);
      setTxns((list) => list.map((t) => (t.id === txn.id ? { ...t, category: newCategory } : t)));
    } catch (err) {
      setError(err.message);
    } finally {
      setCorrecting(null);
    }
  };

  if (loading) return <div className="spinner">Loading transactions…</div>;

  return (
    <div>
      <h1>Transactions</h1>
      {error && <div className="error">{error}</div>}

      {/* ---- filters ---- */}
      <div className="filter-bar">
        <input type="month" value={month} onChange={(e) => setMonth(e.target.value)} />
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          <option value="All">All categories</option>
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>
              {c}
            </option>
          ))}
        </select>
        <input
          type="search"
          placeholder="Search merchant / notes…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          style={{ gridColumn: "1 / -1" }}
        />
      </div>

      <div className="card">
        <div className="list-head">
          <span className="muted">
            {filtered.length} transaction{filtered.length === 1 ? "" : "s"}
          </span>
          <strong>Total ₹{total.toFixed(0)}</strong>
        </div>

        {filtered.length === 0 && <p className="muted">Nothing matches these filters.</p>}

        {filtered.map((t) => (
          <div className="txn" key={t.id} style={{ alignItems: "flex-start" }}>
            <div className="txn-icon">🧾</div>
            <div className="txn-main">
              <div className="txn-title">{t.merchant || t.category || "Expense"}</div>
              <div className="txn-sub">
                <span className="chip">{t.category}</span> · {t.date} · {t.paymentMode}
                {t.notes && <div>{t.notes}</div>}
              </div>
              <div className="txn-actions" style={{ marginTop: "0.4rem" }}>
                <button className="btn btn-secondary btn-sm" onClick={() => setEditing(t)}>
                  Edit
                </button>
                <button className="btn btn-danger btn-sm" onClick={() => remove(t.id)}>
                  Delete
                </button>
                {/* Correct-category feedback loop for the ML merchant map */}
                <select
                  className="btn-sm"
                  style={{ width: "auto" }}
                  value={correcting === t.id ? "__sending" : ""}
                  disabled={correcting === t.id}
                  onChange={(e) => e.target.value && correct(t, e.target.value)}
                >
                  <option value="">{correcting === t.id ? "Sending…" : "Correct category"}</option>
                  {CATEGORIES.filter((c) => c !== t.category).map((c) => (
                    <option key={c} value={c}>
                      → {c}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="txn-amount">₹{Number(t.amount).toFixed(0)}</div>
          </div>
        ))}
      </div>

      {/* ---- edit drawer ---- */}
      {editing && (
        <div className="card">
          <div className="list-head">
            <h2 style={{ margin: 0 }}>Edit expense</h2>
            <button className="btn btn-ghost" onClick={() => setEditing(null)}>
              ✕ Close
            </button>
          </div>
          <ExpenseForm initial={editing} onSubmit={saveEdit} submitLabel="Update expense" />
        </div>
      )}
    </div>
  );
}
