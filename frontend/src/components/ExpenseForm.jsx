// ---------------------------------------------------------------------------
// ExpenseForm — shared add/edit expense form.
// Used by the Manual tab and by the QR/OCR flows via `initial` prefill.
// Props:
//   initial  — object with any of {amount, category, date, paymentMode,
//              merchant, notes} to pre-populate the fields
//   onSubmit — async (expensePayload) => void
//   submitLabel — text for the submit button
// "Auto" in the category dropdown calls the ML /categorize endpoint with
// the merchant + notes text and fills the predicted category.
// ---------------------------------------------------------------------------

import { useEffect, useState } from "react";
import { CATEGORIES, PAYMENT_MODES, ml } from "../api.js";

const empty = {
  amount: "",
  category: "Auto", // "Auto" triggers ML prediction before submit
  date: new Date().toISOString().slice(0, 10),
  paymentMode: "UPI",
  merchant: "",
  notes: "",
};

export default function ExpenseForm({ initial = {}, onSubmit, submitLabel = "Save expense" }) {
  const [form, setForm] = useState({ ...empty, ...initial });
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [autoHint, setAutoHint] = useState(""); // shows the ML-predicted category

  // Refresh when the parent hands us new prefill data (QR / OCR result).
  useEffect(() => {
    setForm({ ...empty, ...initial });
    setAutoHint("");
  }, [JSON.stringify(initial)]); // eslint-disable-line react-hooks/exhaustive-deps

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setAutoHint("");

    const amount = parseFloat(form.amount);
    if (!amount || amount <= 0) {
      setError("Please enter a valid amount.");
      return;
    }

    let category = form.category;
    // "Auto": ask the ML service to predict from merchant + notes text.
    if (category === "Auto") {
      try {
        const pred = await ml.categorize({
          merchant: form.merchant,
          text: `${form.merchant} ${form.notes}`.trim(),
        });
        category = pred.category || "Other";
        setAutoHint(`ML predicted: ${category} (${Math.round((pred.confidence || 0) * 100)}% confident)`);
      } catch {
        category = "Other"; // fall back gracefully if ML is down
        setAutoHint("ML service unreachable — defaulted to Other.");
      }
    }

    setSaving(true);
    try {
      await onSubmit({
        amount,
        category,
        date: form.date,
        paymentMode: form.paymentMode,
        merchant: form.merchant.trim(),
        notes: form.notes.trim(),
      });
      setForm({ ...empty }); // reset after a successful save
    } catch (err) {
      setError(err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <form className="form-grid" onSubmit={handleSubmit}>
      {error && <div className="error">{error}</div>}
      {autoHint && <div className="notice">{autoHint}</div>}

      <div className="row-2">
        <label className="field">
          Amount (₹)
          <input
            type="number"
            step="0.01"
            min="0"
            inputMode="decimal"
            value={form.amount}
            onChange={set("amount")}
            placeholder="250"
            required
          />
        </label>
        <label className="field">
          Date
          <input type="date" value={form.date} onChange={set("date")} required />
        </label>
      </div>

      <div className="row-2">
        <label className="field">
          Category
          <select value={form.category} onChange={set("category")}>
            <option value="Auto">🤖 Auto (ML)</option>
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
        Merchant
        <input
          type="text"
          value={form.merchant}
          onChange={set("merchant")}
          placeholder="e.g. Swiggy, DMart, Uber"
        />
      </label>

      <label className="field">
        Notes
        <input
          type="text"
          value={form.notes}
          onChange={set("notes")}
          placeholder="Optional note"
        />
      </label>

      <button className="btn btn-block" type="submit" disabled={saving}>
        {saving ? "Saving…" : submitLabel}
      </button>
    </form>
  );
}
