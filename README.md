# Smart Expense Tracker

An AI-powered personal finance web application that helps users track expenses, manage budgets, and get intelligent spending insights. The system combines a Spring Boot backend, a Python FastAPI machine learning service, and a React frontend, with support for QR-based UPI payment capture, mock bank-statement syncing, and automatic expense categorization.

## Key Features

- **Expense management** — full CRUD for expenses with categories, payment modes, merchants, and notes.
- **AI categorization** — the ML service suggests a category from free text (merchant name, notes) with a confidence score.
- **Budget alerts** — per-category monthly budgets with OK / NEAR / EXCEEDED status computed from actual spend.
- **QR scanning** — scan a UPI QR (camera or image upload) to prefill payee, amount, and note.
- **Bank integration (mock)** — a `BankProvider` interface with a `MockBankProvider` implementation reads a local mock statement, deduplicates, and imports transactions; the design allows a real Account Aggregator provider to be swapped in later.
- **Spending insights & forecasting** — anomaly detection and next-month spend forecasts from the ML service.
- **Recurring expenses** — recurring entries are generated automatically by a scheduled job.
- **Correction learning** — correcting an expense's category feeds the merchant-to-category learning table so future categorizations improve.

## Project Structure

```
smart-expense-tracker/
├── README.md                  # This file
├── ARCHITECTURE.md            # Component design, diagrams, request flows
├── DATABASE.md                # ER diagram and table reference
├── API.md                     # REST endpoint reference with JSON samples
└── sql/
    ├── schema.sql             # PostgreSQL DDL (all tables + category seed)
    └── seed.sql               # Demo user and sample data (demo@example.com / demo1234)
```

> Note: the `sql/` directory and other modules' source files (backend, ML service, frontend) are produced by the respective modules of this project. The documentation files here describe the agreed shared contract.

## Technology Stack

| Layer     | Technology                                  | Port |
|-----------|---------------------------------------------|------|
| Frontend  | React + Vite                                | 5173 |
| Backend   | Java 17, Spring Boot 3.x                    | 8080 |
| ML service| Python 3.10+, FastAPI, Uvicorn              | 8000 |
| Database  | PostgreSQL 15+                              | 5432 |

Base package (backend): `com.expensetracker`. API prefix: `/api`.

## Prerequisites

- **Java 17** and **Maven 3.8+**
- **Python 3.10+**
- **Node.js 18+** and npm
- **PostgreSQL 15+** (or Docker)
- Tesseract OCR binary (for the QR image OCR fallback) — `apt install tesseract-ocr` on Debian/Ubuntu

## Setup

### 1. Database

Create the database and load the schema:

```bash
createdb expensetracker
psql -d expensetracker -f sql/schema.sql
```

Or with Docker:

```bash
docker run --name expense-pg -e POSTGRES_DB=expensetracker \
  -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 -d postgres:15
psql -h localhost -U postgres -d expensetracker -f sql/schema.sql
```

### 2. ML service (Python FastAPI, port 8000)

```bash
cd ml-service
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --port 8000
```

The ML service exposes:
- `POST /categorize` — `{ "text": "..." }` -> `{ "category", "confidence", "source" }`
- `POST /insights` — `{ "transactions", "budgets" }` -> `{ "insights": [], "anomalies": [] }`
- `POST /forecast` — `{ "monthly_totals": [] }` -> `{ "forecast": ... }`

### 3. Backend (Spring Boot, port 8080)

```bash
cd backend
export JWT_SECRET=<a-long-random-secret>
# optionally override the ML URL (default http://localhost:8000)
# export ML_SERVICE_URL=http://localhost:8000
mvn spring-boot:run
```

### 4. Frontend (React + Vite, port 5173)

```bash
cd frontend
npm install
# .env (or use the defaults):
# VITE_API_URL=http://localhost:8080
# VITE_ML_URL=http://localhost:8000
npm run dev
```

Then open http://localhost:5173 in a browser.

## Configuration Notes

| Setting | Where | Notes |
|---------|-------|-------|
| JWT secret | `JWT_SECRET` env var (backend) | Required. Use a long random string; never commit it. |
| ML service URL | `ml.service.url` (Spring property; env `ML_SERVICE_URL`) | Defaults to `http://localhost:8000`. |
| Frontend API URL | `VITE_API_URL` (frontend `.env`) | Default `http://localhost:8080`. |
| Frontend ML URL | `VITE_ML_URL` (frontend `.env`) | Default `http://localhost:8000`; used only for direct ML preview calls if enabled. |
| DB credentials | Spring datasource properties / env | Database name is `expensetracker`. |

No real credentials are stored anywhere in this repository. Bank integration stores only masked account numbers — never credentials, PINs, or OTPs.


## How the Demo Works (End to End)

1. **Start all services** — Postgres, ML service (`:8000`), backend (`:8080`), frontend (`:5173`).
2. **Log in** with the demo account above.
3. **Add an expense manually** — the frontend calls `POST /api/expenses`; if no category is chosen, it first calls the ML `POST /categorize` to suggest one.
4. **Scan a QR** — use the Scan page: the camera (html5-qrcode) or an uploaded image (jsQR in the frontend, or `POST /api/qr/scan-image` with Tesseract OCR fallback in the backend) parses the UPI payload; `POST /api/qr/parse` extracts payee VPA, name, amount, and note; the expense form opens prefilled and you press Save.
5. **Sync mock bank data** — grant consent on the Bank page (`POST /api/bank/consent`), then `POST /api/bank/sync`. The backend pulls transactions from `MockBankProvider` (backed by `mock-statement.json`), skips duplicates by `bank_txn_ref`, auto-categorizes each via the ML service, and saves them with `source=BANK`.
6. **Correct a category** — if an auto-category is wrong, use `POST /api/expenses/{id}/correct`; the merchant-to-category mapping (`merchant_map`) is updated so the same merchant is categorized correctly next time.
7. **Check budgets and insights** — the Budgets page calls `GET /api/budgets/alerts` for OK/NEAR/EXCEEDED status; the Insights page calls the ML `/insights` and `/forecast` endpoints for anomaly detection and next-month spend prediction.
8. **Recurring expenses** — entries created under `/api/recurring` are materialized automatically by the backend `@Scheduled` job each day.
