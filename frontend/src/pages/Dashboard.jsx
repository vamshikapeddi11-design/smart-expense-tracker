// ---------------------------------------------------------------------------
// Dashboard — the home screen.
// - Summary cards: this month's spend, % change vs last month, top category,
//   open budget-alert count.
// - Recharts PieChart: category-wise spend this month.
// - Recharts BarChart: total spend for the last 6 months
//   (one /expenses/summary call per month).
// - Recent transactions list.
// - "Ask ML insights" button -> ML /insights -> natural-language strings.
// ---------------------------------------------------------------------------

import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import {
  PieChart,
  Pie,
  Cell,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  ResponsiveContainer,
} from "recharts";
import { api, ml } from "../api.js";

const PIE_COLORS = [
  "#1d4ed8",
  "#16a34a",
  "#d97706",
  "#dc2626",
  "#7c3aed",
  "#0891b2",
  "#db2777",
  "#65a30d",
  "#ea580c",
  "#475569",
  "#0d9488",
];

// Roll back n months from the current date: [{year, month}]
function pastMonths(n) {
  const now = new Date();
  const out = [];
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    out.push({ year: d.getFullYear(), month: d.getMonth() + 1, label: d.toLocaleString("en", { month: "short" }) });
  }
  return out;
}

// The backend summary is flexible; normalize whatever shape it returns
// into { total, byCategory: [{name, value}] }.
function normalizeSummary(data) {
  if (!data) return { total: 0, byCategory: [] };
  const total = Number(data.total ?? data.totalSpent ?? data.totalAmount ?? 0);
  const raw = data.byCategory ?? data.categoryWise ?? data.categories ?? [];
  const byCategory = Array.isArray(raw)
    ? raw.map((c) => ({
        name: c.category ?? c.name ?? "Other",
        value: Number(c.amount ?? c.total ?? c.value ?? 0),
      }))
    : Object.entries(raw).map(([name, value]) => ({ name, value: Number(value) }));
  return { total, byCategory };
}

export default function Dashboard() {
  const [summary, setSummary] = useState(null); // current month
  const [prevTotal, setPrevTotal] = useState(0);
  const [history, setHistory] = useState([]); // last 6 months totals
  const [alerts, setAlerts] = useState([]);
  const [recent, setRecent] = useState([]);
  const [insights, setInsights] = useState([]);
  const [insightsBusy, setInsightsBusy] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const now = new Date();
        const year = now.getFullYear();
        const month = now.getMonth() + 1;

        // Current month summary + previous month summary in parallel.
        const prev = new Date(year, month - 2, 1);
        const [cur, prv, alertData, txns] = await Promise.all([
          api.summary({ period: "month", year, month }),
          api.summary({ period: "month", year: prev.getFullYear(), month: prev.getMonth() + 1 }),
          api.budgetAlerts().catch(() => []),
          api.listExpenses({ limit: 5, sort: "date,desc" }).catch(() => []),
        ]);
        if (cancelled) return;

        const curNorm = normalizeSummary(cur);
        setSummary(curNorm);
        setPrevTotal(normalizeSummary(prv).total);
        setAlerts(Array.isArray(alertData) ? alertData : alertData.alerts ?? []);
        setRecent(Array.isArray(txns) ? txns : txns.content ?? txns.items ?? []);

        // One summary call per month for the last 6 months bar chart.
        const months = pastMonths(6);
        const totals = await Promise.all(
          months.map((m) =>
            api
              .summary({ period: "month", year: m.year, month: m.month })
              .then((s) => ({ label: m.label, total: normalizeSummary(s).total }))
              .catch(() => ({ label: m.label, total: 0 }))
          )
        );
        if (!cancelled) setHistory(totals);
      } catch (err) {
        if (!cancelled) setError(err.message);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const changePct = useMemo(() => {
    if (!summary || prevTotal <= 0) return null;
    return ((summary.total - prevTotal) / prevTotal) * 100;
  }, [summary, prevTotal]);

  const topCategory = useMemo(() => {
    if (!summary?.byCategory?.length) return "—";
    return summary.byCategory.reduce((a, b) => (b.value > a.value ? b : a)).name;
  }, [summary]);

  const askInsights = async () => {
    setInsightsBusy(true);
    try {
      // Build the payload the ML /insights endpoint expects:
      // { transactions: [{amount, category, date, merchant}],
      //   budgets: [{category, monthlyLimit}],
      //   prev_month_total, current_month_total }.
      const now = new Date();
      const monthParam = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
      const [txns, budgets] = await Promise.all([
        api.listExpenses({ month: monthParam }).catch(() => []),
        api.listBudgets().catch(() => []),
      ]);
      const txnArr = Array.isArray(txns) ? txns : txns.content ?? txns.items ?? [];
      const budgetArr = Array.isArray(budgets) ? budgets : budgets.content ?? budgets.items ?? [];
      const res = await ml.insights({
        transactions: txnArr.map((t) => ({
          amount: Number(t.amount ?? 0),
          category: t.category ?? "Other",
          date: String(t.date ?? "").slice(0, 10),
          merchant: t.merchant ?? "",
        })),
        budgets: budgetArr.map((b) => ({
          category: b.category,
          monthlyLimit: Number(b.monthlyLimit ?? b.monthly_limit ?? 0),
        })),
        prev_month_total: prevTotal,
        current_month_total: summary?.total ?? 0,
      });
      // Render the natural-language insights plus any anomaly flags.
      const lines = [...(res.insights ?? [])];
      for (const a of res.anomalies ?? []) lines.push(`\u26a0\ufe0f ${a.reason}`);
      setInsights(lines);
    } catch (err) {
      setInsights([`Could not reach the ML service: ${err.message}`]);
    } finally {
      setInsightsBusy(false);
    }
  };

  if (!summary && !error) return <div className="spinner">Loading…</div>;

  return (
    <div>
      <h1>Dashboard</h1>
      {error && <div className="error">{error}</div>}

      {/* ---- summary stat cards ---- */}
      <div className="stats-grid">
        <div className="stat">
          <div className="label">Spent this month</div>
          <div className="value">₹{(summary?.total ?? 0).toFixed(0)}</div>
          {changePct !== null && (
            <div className={`delta ${changePct >= 0 ? "up" : "down"}`}>
              {changePct >= 0 ? "▲" : "▼"} {Math.abs(changePct).toFixed(1)}% vs last month
            </div>
          )}
        </div>
        <div className="stat">
          <div className="label">Top category</div>
          <div className="value" style={{ fontSize: "1.1rem" }}>
            {topCategory}
          </div>
          <div className="delta">this month</div>
        </div>
        <div className="stat">
          <div className="label">Budget alerts</div>
          <div className="value">{alerts.length}</div>
          <div className="delta">
            <Link to="/budgets">View budgets →</Link>
          </div>
        </div>
        <div className="stat">
          <div className="label">Quick actions</div>
          <div className="delta">
            <Link to="/add">➕ Add expense</Link>
            <br />
            <Link to="/bank">🏦 Link bank</Link>
          </div>
        </div>
      </div>

      {/* ---- category pie chart ---- */}
      <div className="card">
        <h2>Where your money went</h2>
        {summary?.byCategory?.length ? (
          <ResponsiveContainer width="100%" height={240}>
            <PieChart>
              <Pie data={summary.byCategory} dataKey="value" nameKey="name" outerRadius={90} label>
                {summary.byCategory.map((entry, i) => (
                  <Cell key={entry.name} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                ))}
              </Pie>
              <Tooltip formatter={(v) => `₹${Number(v).toFixed(0)}`} />
            </PieChart>
          </ResponsiveContainer>
        ) : (
          <p className="muted">No spending recorded this month yet.</p>
        )}
      </div>

      {/* ---- last 6 months bar chart ---- */}
      <div className="card">
        <h2>Last 6 months</h2>
        <ResponsiveContainer width="100%" height={220}>
          <BarChart data={history}>
            <XAxis dataKey="label" fontSize={12} />
            <YAxis fontSize={12} />
            <Tooltip formatter={(v) => `₹${Number(v).toFixed(0)}`} />
            <Bar dataKey="total" fill="#1d4ed8" radius={[6, 6, 0, 0]} />
          </BarChart>
        </ResponsiveContainer>
      </div>

      {/* ---- ML insights ---- */}
      <div className="card">
        <h2>Smart insights</h2>
        <button className="btn btn-secondary btn-block" onClick={askInsights} disabled={insightsBusy}>
          {insightsBusy ? "Thinking…" : "🤖 Ask ML insights"}
        </button>
        <div style={{ marginTop: "0.75rem" }}>
          {insights.map((text, i) => (
            <div className="insight" key={i}>
              {text}
            </div>
          ))}
          {!insights.length && !insightsBusy && (
            <p className="muted">Get AI-generated observations about your spending.</p>
          )}
        </div>
      </div>

      {/* ---- recent transactions ---- */}
      <div className="card">
        <div className="list-head">
          <h2 style={{ margin: 0 }}>Recent activity</h2>
          <Link to="/transactions">See all →</Link>
        </div>
        {recent.length === 0 && <p className="muted">No transactions yet.</p>}
        {recent.slice(0, 5).map((t) => (
          <div className="txn" key={t.id}>
            <div className="txn-icon">🧾</div>
            <div className="txn-main">
              <div className="txn-title">{t.merchant || t.category || "Expense"}</div>
              <div className="txn-sub">
                {t.category} · {t.date}
              </div>
            </div>
            <div className="txn-amount">₹{Number(t.amount).toFixed(0)}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
