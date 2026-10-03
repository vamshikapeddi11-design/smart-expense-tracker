# Smart Expense Tracker — Frontend

React + Vite frontend for the **Smart Expense Tracker** college mini-project.
Works with the Spring Boot backend (`http://localhost:8080`) and the ML
microservice (`http://localhost:8000`).

## Setup

```bash
cd smart-expense-tracker/frontend
npm install
cp .env.example .env   # or create .env manually (see below)
npm run dev            # runs on http://localhost:5173
```

## Environment variables

| Variable        | Default                | Meaning                          |
|-----------------|------------------------|----------------------------------|
| `VITE_API_URL`  | `http://localhost:8080`| Spring Boot backend base URL     |
| `VITE_ML_URL`   | `http://localhost:8000`| ML service (FastAPI) base URL    |

`src/api.js` reads them via `import.meta.env` and falls back to the defaults,
so the app runs out of the box if both services are on those ports.

## Feature tour (screens)

- **Login / Register** — `POST /api/auth/signup|login`. The returned JWT is
  stored in `localStorage` and sent as `Authorization: Bearer <token>` on every
  request.
- **Dashboard** — summary cards (this month's spend, % vs last month, top
  category, budget-alert count), a Recharts pie chart of category-wise spend, a
  bar chart of the last 6 months (one `/expenses/summary` call per month), recent
  transactions, and an **"Ask ML insights"** button that calls `VITE_ML_URL/insights`
  and renders natural-language insight strings.
- **Add expense** — three tabs:
  - *Manual*: amount, category (with **🤖 Auto** — calls ML `/categorize` on the
    merchant + notes text), date, payment mode, merchant, notes.
  - *Scan QR*: live camera scan (`html5-qrcode`). UPI payloads
    (`upi://pay?pa=…&pn=…&am=…&tn=…&cu=…`) are parsed client-side into payee VPA,
    payee name, amount and note; non-UPI QR text goes into notes.
  - *Upload*: image file → drawn to canvas → `jsQR` decode; if no QR is found,
    `tesseract.js` OCR runs and we regex out the total (₹/Rs/INR patterns),
    date (dd-mm-yyyy) and merchant (first text line).
  - In every case the user reviews/edits the pre-filled form before saving —
    nothing is posted to `POST /expenses` automatically.
- **Transactions** — filter by month / category / free-text search, edit, delete,
  and a per-row **"Correct category"** dropdown → `POST /expenses/{id}/correct`.
  These corrections feed the backend's `merchant_map` so auto-categorization
  keeps improving.
- **Budgets** — monthly budgets with progress bars (green / amber NEAR / red
  EXCEEDED), an alerts section from `GET /budgets/alerts`, and add/edit forms.
- **Recurring** — list, add, edit and delete recurring expenses (frequency,
  next due date, active flag).
- **Bank** — mock account-aggregator flow: "Link mock bank" → `POST /bank/consent`
  (shows consent status), linked accounts from `GET /bank/accounts`, "Sync now"
  → `POST /bank/sync` (shows `{newTransactions, duplicatesSkipped}`), and a
  statement file upload → `POST /bank/import`.
- **Forecast** — builds 12 months of totals from `/expenses/summary`, calls ML
  `/forecast`, and renders next-month per-category forecast cards.

The mobile bottom nav (Home / Add / Activity / Budgets / Forecast) appears once
logged in; Recurring and Bank are reachable via links/URLs (mobile-first design).

## Notes

- **Camera permission**: browsers only grant camera access on **HTTPS or
  localhost**. `npm run dev` serves on `http://localhost:5173`, which works —
  but opening the dev server by LAN IP (`http://192.168.x.x:5173`) will block
  the camera. For testing on a physical phone, use a tunnel (e.g. ngrok) with
  HTTPS or run behind HTTPS.
- `tesseract.js` downloads the English OCR language pack (~ a few MB) on first
  use, so the first receipt upload needs internet access.
- Recharts charts need a sized container — `ResponsiveContainer` handles this,
  but the chart cards need the app to be visible (not `display:none`).
