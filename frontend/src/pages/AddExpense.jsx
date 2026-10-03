// ---------------------------------------------------------------------------
// AddExpense — three ways to add an expense:
//   [Manual]  plain form (category "Auto" -> ML /categorize)
//   [Scan QR] live camera scan via <QRScanner/>
//   [Upload]  image file -> jsQR decode, else tesseract.js OCR fallback
//
// Whatever path is used, the extracted data only PRE-FILLS the form —
// the user always reviews/edits and hits "Save" before POST /expenses.
// ---------------------------------------------------------------------------

import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import jsQR from "jsqr";
import { createWorker } from "tesseract.js";
import { api } from "../api.js";
import ExpenseForm from "../components/ExpenseForm.jsx";
import QRScanner from "../components/QRScanner.jsx";

/**
 * Parse a UPI deep-link payload like:
 *   upi://pay?pa=shop@upi&pn=ShopName&am=250&tn=Thanks&cu=INR
 * Returns a partial expense object (payeeVpa, payeeName, amount, note...).
 */
function parseUpiPayload(payload) {
  const query = payload.split("?")[1] || "";
  const params = new URLSearchParams(query);
  return {
    payeeVpa: params.get("pa") || "", // payee VPA address
    payeeName: params.get("pn") || "", // payee name
    amount: params.get("am") || "", // transaction amount
    note: params.get("tn") || "", // transaction note
    currency: params.get("cu") || "INR", // currency code
    ref: params.get("tr") || "", // optional transaction ref
  };
}

/** Pull an amount out of free OCR text: matches ₹ / Rs / INR / TOTAL patterns. */
function extractAmount(text) {
  const patterns = [
    /(?:total|grand\s*total|amount\s*payable|net\s*amount)[^\d₹]*[₹]?\s*([\d,]+\.\d{2})/i,
    /[₹]\s*([\d,]+\.?\d*)/,
    /(?:rs\.?|inr)\s*([\d,]+\.?\d*)/i,
  ];
  for (const re of patterns) {
    const m = text.match(re);
    if (m) return m[1].replace(/,/g, "");
  }
  return "";
}

/** Pull a dd-mm-yyyy (or dd/mm/yy) date out of OCR text -> yyyy-mm-dd for the form. */
function extractDate(text) {
  const m = text.match(/(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})/);
  if (!m) return "";
  let [, d, mo, y] = m;
  if (y.length === 2) y = "20" + y;
  return `${y}-${mo.padStart(2, "0")}-${d.padStart(2, "0")}`;
}

const TABS = ["Manual", "Scan QR", "Upload"];

export default function AddExpense() {
  const navigate = useNavigate();
  const [tab, setTab] = useState("Manual");
  const [prefill, setPrefill] = useState({});
  const [scanInfo, setScanInfo] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const fileRef = useRef(null);

  // Decode the raw QR payload and turn it into form prefill values.
  const handlePayload = (text) => {
    setError("");
    if (text.trim().toLowerCase().startsWith("upi://")) {
      // --- UPI QR: map its fields onto our form fields -------------------
      const upi = parseUpiPayload(text);
      setScanInfo(
        `UPI QR detected${upi.payeeName ? ` — ${upi.payeeName}` : ""}. Review and save.`
      );
      setPrefill({
        amount: upi.amount,
        merchant: upi.payeeName || upi.payeeVpa,
        notes: [upi.note, upi.payeeVpa ? `VPA: ${upi.payeeVpa}` : ""]
          .filter(Boolean)
          .join(" · "),
        paymentMode: "UPI",
        category: "Auto",
      });
      // Optional: also ask the backend's /qr/parse endpoint for its view.
      api.parseQr(text).catch(() => {});
    } else {
      // --- Non-UPI QR: keep the raw text in notes for the user to edit --
      setScanInfo("QR scanned (not a UPI payment code) — its text was added to notes.");
      setPrefill({ notes: text, category: "Other" });
    }
    setTab("Manual"); // switch to the form so the user can confirm
  };

  /**
   * Upload tab flow:
   *  1. Draw the image to a canvas.
   *  2. Run jsQR on the pixels — fast and accurate for QR codes.
   *  3. If no QR found, fall back to tesseract.js OCR and regex out the
   *     amount / date / merchant from the receipt text.
   */
  const handleImageUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setError("");
    setBusy(true);
    setScanInfo("Reading image…");

    try {
      // --- Step 1: load image into a canvas -------------------------------
      const url = URL.createObjectURL(file);
      const img = await new Promise((resolve, reject) => {
        const im = new Image();
        im.onload = () => resolve(im);
        im.onerror = reject;
        im.src = url;
      });
      const canvas = document.createElement("canvas");
      // Cap large images to keep decoding fast.
      const scale = Math.min(1, 1200 / Math.max(img.width, img.height));
      canvas.width = Math.round(img.width * scale);
      canvas.height = Math.round(img.height * scale);
      const ctx = canvas.getContext("2d");
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(url);

      // --- Step 2: try jsQR on the raw pixels ----------------------------
      const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const qr = jsQR(imageData.data, imageData.width, imageData.height);
      if (qr?.data) {
        setScanInfo("QR code found in the image ✔");
        handlePayload(qr.data);
        setBusy(false);
        return;
      }

      // --- Step 3: OCR fallback with tesseract.js -------------------------
      setScanInfo("No QR found — running OCR on the receipt…");
      const worker = await createWorker("eng");
      const {
        data: { text },
      } = await worker.recognize(canvas);
      await worker.terminate();

      const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
      const merchant = lines[0] || "";
      const amount = extractAmount(text);
      const date = extractDate(text);

      if (!merchant && !amount) {
        setError("Could not extract anything useful from the image. Please enter the details manually.");
        setScanInfo("");
      } else {
        setScanInfo("Receipt text extracted — review and save.");
        setPrefill({
          amount,
          merchant,
          date: date || new Date().toISOString().slice(0, 10),
          notes: `OCR from receipt${amount ? ` (total ₹${amount})` : ""}`,
          category: "Auto",
        });
        setTab("Manual");
      }
    } catch (err) {
      setError(`Image processing failed: ${err.message}`);
      setScanInfo("");
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = ""; // allow re-uploading the same file
    }
  };

  const saveExpense = async (payload) => {
    await api.createExpense(payload);
    navigate("/", { state: { flash: "Expense saved ✔" } });
  };

  return (
    <div>
      <h1>Add expense</h1>

      {/* ---- tab bar ---- */}
      <div className="tabs" role="tablist">
        {TABS.map((t) => (
          <button
            key={t}
            role="tab"
            className={`tab${tab === t ? " active" : ""}`}
            onClick={() => setTab(t)}
          >
            {t}
          </button>
        ))}
      </div>

      {error && <div className="error">{error}</div>}

      {/* ---- Manual: plain form ---- */}
      {tab === "Manual" && (
        <div className="card">
          {scanInfo && <div className="notice">{scanInfo}</div>}
          <ExpenseForm initial={prefill} onSubmit={saveExpense} />
        </div>
      )}

      {/* ---- Scan QR: live camera ---- */}
      {tab === "Scan QR" && (
        <div className="card">
          <h2>Scan a UPI QR code</h2>
          <QRScanner onDecode={handlePayload} onError={setError} />
          <p className="hint" style={{ marginTop: "0.75rem" }}>
            Camera needs permission, and only works over HTTPS or localhost. Scanned data is
            pre-filled into the form for you to review — nothing is saved automatically.
          </p>
        </div>
      )}

      {/* ---- Upload: image file -> jsQR -> OCR ---- */}
      {tab === "Upload" && (
        <div className="card">
          <h2>Upload QR / receipt image</h2>
          <p className="hint">
            Upload a photo of a UPI QR code or a paper bill. We first try to decode a QR
            code (jsQR); if there isn't one, we OCR the receipt (tesseract.js) and extract
            the total, date and merchant.
          </p>
          {scanInfo && <div className="notice">{scanInfo}</div>}
          <input
            ref={fileRef}
            type="file"
            accept="image/*"
            onChange={handleImageUpload}
            disabled={busy}
          />
          {busy && <p className="muted">Processing image… this can take a few seconds.</p>}
        </div>
      )}
    </div>
  );
}
