// ---------------------------------------------------------------------------
// Bank page: mock account-aggregator consent flow.
//  1. "Link mock bank"  -> POST /bank/consent  (shows consent status)
//  2. Linked accounts    -> GET /bank/accounts
//  3. "Sync now"         -> POST /bank/sync    (shows {newTransactions,
//                              duplicatesSkipped})
//  4. Statement upload   -> POST /bank/import  (multipart file)
// ---------------------------------------------------------------------------

import { useEffect, useState } from "react";
import { api } from "../api.js";

export default function Bank() {
  const [consent, setConsent] = useState(null);
  const [accounts, setAccounts] = useState([]);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [busy, setBusy] = useState(false);

  const loadAccounts = async () => {
    try {
      const data = await api.bankAccounts();
      setAccounts(Array.isArray(data) ? data : data.accounts ?? []);
    } catch (err) {
      // No consent yet — that's fine, show nothing.
      setAccounts([]);
    }
  };

  useEffect(() => {
    loadAccounts();
  }, []);

  const linkBank = async () => {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const res = await api.bankConsent();
      setConsent(res);
      setNotice("Mock bank linked successfully ✔");
      await loadAccounts();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const syncNow = async () => {
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const res = await api.bankSync();
      setNotice(
        `Sync complete: ${res.newTransactions ?? 0} new transaction(s), ` +
          `${res.duplicatesSkipped ?? 0} duplicate(s) skipped.`
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const importStatement = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError("");
    setNotice("");
    setBusy(true);
    try {
      const res = await api.bankImport(file);
      setNotice(
        `Statement imported: ${res.imported ?? res.newTransactions ?? 0} transaction(s) added.`
      );
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
      e.target.value = "";
    }
  };

  return (
    <div>
      <h1>Bank sync</h1>
      {error && <div className="error">{error}</div>}
      {notice && <div className="notice">{notice}</div>}

      <div className="card">
        <h2>Mock bank consent</h2>
        <p className="muted">
          This demo uses a simulated bank. Clicking below calls POST /bank/consent to grant
          a mock consent, exactly like a real account-aggregator flow would.
        </p>
        {consent && (
          <div className="notice">
            Status: {consent.status ?? "GRANTED"}
            {consent.consentId && <> · consent id: {consent.consentId}</>}
          </div>
        )}
        <button className="btn btn-block" onClick={linkBank} disabled={busy}>
          {busy ? "Linking…" : "🏦 Link mock bank"}
        </button>
      </div>

      <div className="card">
        <div className="list-head">
          <h2 style={{ margin: 0 }}>Linked accounts</h2>
          <button className="btn btn-secondary btn-sm" onClick={syncNow} disabled={busy}>
            {busy ? "Syncing…" : "🔄 Sync now"}
          </button>
        </div>
        {accounts.length === 0 && (
          <p className="muted">No accounts linked yet — link the mock bank above.</p>
        )}
        {accounts.map((a, i) => (
          <div className="txn" key={a.id ?? i}>
            <div className="txn-icon">🏛️</div>
            <div className="txn-main">
              <div className="txn-title">{a.bankName || a.name || "Bank account"}</div>
              <div className="txn-sub">
                {a.accountNumber ? `•••• ${String(a.accountNumber).slice(-4)}` : a.type ?? ""}
              </div>
            </div>
            {a.balance !== undefined && (
              <div className="txn-amount">₹{Number(a.balance).toFixed(0)}</div>
            )}
          </div>
        ))}
      </div>

      <div className="card">
        <h2>Import statement</h2>
        <p className="hint">Upload a CSV / PDF bank statement (POST /bank/import).</p>
        <input type="file" onChange={importStatement} disabled={busy} />
      </div>
    </div>
  );
}
