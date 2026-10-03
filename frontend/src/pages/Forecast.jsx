// ---------------------------------------------------------------------------
// Forecast page: builds monthly totals from /expenses/summary (last 12
// months), sends them to the ML /forecast endpoint, and shows next-month
// per-category forecast cards.
// ---------------------------------------------------------------------------

import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, ml } from "../api.js";

// Same normalization as the dashboard.
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

function pastMonths(n) {
  const now = new Date();
  const out = [];
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    out.push({ year: d.getFullYear(), month: d.getMonth() + 1 });
  }
  return out;
}

export default function Forecast() {
  const [forecasts, setForecasts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        // Build 12 months of history from the backend summary endpoint.
        const months = pastMonths(12);
        const history = await Promise.all(
          months.map(async (m) => {
            try {
              const s = await api.summary({ period: "month", year: m.year, month: m.month });
              const n = normalizeSummary(s);
              return { year: m.year, month: m.month, total: n.total, byCategory: n.byCategory };
            } catch {
              return { year: m.year, month: m.month, total: 0, byCategory: [] };
            }
          })
        );

        // Build per-category monthly series, oldest month first, in the shape
        // the ML /forecast endpoint expects: {"monthly_totals": {"Food": [..], ...}}.
        const monthlyTotals = {};
        history.forEach((h) =>
          h.byCategory.forEach((c) => {
            (monthlyTotals[c.name] = monthlyTotals[c.name] ?? []).push(c.value);
          })
        );
        // Drop categories with no history so the moving average stays meaningful.
        for (const k of Object.keys(monthlyTotals)) {
          if (!monthlyTotals[k].some((v) => v > 0)) delete monthlyTotals[k];
        }

        const res = await ml.forecast({ monthly_totals: monthlyTotals });
        // Contract response: { forecast: {"Food": 3533.33, ...}, method }.
        const arr = Object.entries(res.forecast ?? {}).map(([category, amount]) => ({
          category,
          amount: Number(amount ?? 0),
        }));
        arr.sort((a, b) => b.amount - a.amount);
        if (!cancelled) setForecasts(arr);
      } catch (err) {
        if (!cancelled) setError(err.message);
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const total = forecasts.reduce((sum, f) => sum + Number(f.amount ?? f.predicted ?? 0), 0);

  if (loading) return <div className="spinner">Forecasting next month…</div>;

  return (
    <div>
      <h1>📈 Forecast</h1>
      {error && (
        <div className="error">
          {error} — make sure the ML service is running.{" "}
          <Link to="/">Back to dashboard</Link>
        </div>
      )}

      {!error && (
        <>
          <div className="card">
            <h2>Next month estimate</h2>
            <div className="stat">
              <div className="label">Predicted total spend</div>
              <div className="value">₹{total.toFixed(0)}</div>
            </div>
          </div>

          <h2 style={{ fontSize: "1.05rem" }}>Per-category forecast</h2>
          <div className="forecast-grid">
            {forecasts.map((f, i) => (
              <div className="forecast-card" key={i}>
                <div className="cat">{f.category ?? f.name}</div>
                <div className="amt">₹{Number(f.amount ?? f.predicted ?? 0).toFixed(0)}</div>
                {f.confidence !== undefined && (
                  <div className="muted">{Math.round(f.confidence * 100)}% confident</div>
                )}
              </div>
            ))}
          </div>

          {forecasts.length === 0 && (
            <p className="muted">No forecast data returned by the ML service.</p>
          )}
        </>
      )}
    </div>
  );
}
