export default function BudgetBar({ budget }) {
  const limit = Number(budget.monthlyLimit ?? budget.limit) || 0;
const spent = Number(budget.spent ?? budget.used ?? 0);

const pct = limit > 0 ? (spent / limit) * 100 : 0;

const tone =
  spent >= limit ? "exceeded" :
  pct >= 80 ? "near" :
  "ok";

const label =
  spent >= limit ? "EXCEEDED" :
  pct >= 80 ? "NEAR LIMIT" :
  "ON TRACK";

  return (
    <div className="budget-row">
      <div className="budget-top">
        <span>
          <strong>{budget.category}</strong>{" "}
          <span className="chip">{label}</span>
        </span>

        <span className="muted">
          ₹{spent.toFixed(0)} / ₹{limit.toFixed(0)}
        </span>
      </div>

      <div
        className="budget-bar"
        role="progressbar"
        aria-valuenow={Math.round(pct)}
      >
        <div
  className={`budget-fill ${tone}`}
  style={{ width: `${Math.min(100, pct)}%` }}
/>
      </div>
    </div>
  );
}