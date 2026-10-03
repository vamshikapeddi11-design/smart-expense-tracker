# Smart Expense Tracker — ML Microservice

A small FastAPI microservice that powers the ML features of the **Smart Expense Tracker** mini-project:

1. **Transaction categorization** — TF-IDF + Multinomial Naive Bayes (scikit-learn)
2. **Spending insights** — month-over-month trends, budget alerts, anomaly detection
3. **Forecasting** — next-month spend per category (3-month moving average)

The Spring Boot backend calls this service at the `ml.service.url` property (default `http://localhost:8000`).

## Setup

```bash
cd ml-service
pip install -r requirements.txt   # use a venv if your Python is externally managed
python train_model.py             # trains and saves app/model.joblib
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

The service trains on import if `app/model.joblib` is missing, so `train_model.py` is optional — but running it prints holdout accuracy for your project report.

## API

### GET /health

```bash
curl http://localhost:8000/health
# {"status":"ok","model_version":"1.0.0"}
```

### POST /categorize

```bash
curl -X POST http://localhost:8000/categorize \
  -H "Content-Type: application/json" \
  -d '{"text": "SWIGGY BANGALORE"}'
# {"category":"Food","confidence":0.95,"source":"model"}
```

Pipeline: normalize text → TF-IDF vectorize → MultinomialNB `predict_proba`.
If the best class confidence is below **0.55**, the request falls through to `llm_fallback()`, which returns `{"category":"Other","confidence":0.4,"source":"llm_fallback"}`. Empty input returns `{"category":"Other","confidence":1.0,"source":"default"}`.

**LLM fallback design:** the stub has a TODO comment showing exactly where an OpenAI/Anthropic call would go. The API key is read from the `LLM_API_KEY` environment variable — never hardcoded. For this mini-project no key is configured, so the stub returns a safe default.

### POST /insights

```bash
curl -X POST http://localhost:8000/insights \
  -H "Content-Type: application/json" \
  -d '{
    "transactions": [
      {"amount": 250,   "category": "Food", "date": "2026-09-05", "merchant": "Swiggy"},
      {"amount": 300,   "category": "Food", "date": "2026-09-06", "merchant": "Swiggy"},
      {"amount": 45000, "category": "Rent", "date": "2026-09-01", "merchant": "Landlord"},
      {"amount": 250,   "category": "Food", "date": "2026-09-05", "merchant": "Swiggy"}
    ],
    "budgets": [{"category": "Food", "monthlyLimit": 500}],
    "prev_month_total": 4200,
    "current_month_total": 5460,
    "prev_month_by_category": {"Food": 4000, "Rent": 45000},
    "prev_subscriptions_total": 1200,
    "current_subscriptions_total": 1500
  }'
```

Sample response:

```json
{
  "insights": [
    "You spent 80% less on food this month vs last month (₹4,000 → ₹800).",
    "Overall spending is 30% more than last month (₹4,200 → ₹5,460).",
    "Subscription spending rose 25% vs last month (₹1,200 → ₹1,500). Check for price hikes or new subscriptions you forgot about.",
    "Budget alert: you breached your Food budget - spent ₹800 of ₹500."
  ],
  "anomalies": [
    {"transaction_index": 3, "reason": "Possible duplicate of transaction #0: ₹250 at 'Swiggy' on 2026-09-05", "severity": "medium"}
  ]
}
```

What it computes:
- **Month-over-month % change** per category (needs `prev_month_by_category`) plus an overall total comparison, phrased as natural-language sentences.
- **Subscription delta** from `prev_subscriptions_total` / `current_subscriptions_total`.
- **Budget-breach warnings** (and a heads-up at 80% of the limit).
- **Anomalies**: amount > 3× the category median, or an exact duplicate (same amount + merchant + date).

### POST /forecast

```bash
curl -X POST http://localhost:8000/forecast \
  -H "Content-Type: application/json" \
  -d '{"monthly_totals": {"Food": [3200, 3500, 3900], "Transport": [800, 850, 900]}}'
# {"forecast":{"Food":3533.33,"Transport":850.0},"method":"moving_average_3m"}
```

Takes the last up-to-3 months per category (oldest first) and returns their mean, rounded to 2 decimals. With only 1–2 data points it averages what's available.

## How the categorizer works (for the project report)

1. **Text normalization** — the raw description (e.g. `"SWIGGY BANGALORE"`) is lowercased, stripped of punctuation, and whitespace-collapsed.
2. **TF-IDF vectorization** — each description becomes a numeric vector where each word/bigram (e.g. `"electricity bill"`) gets a weight: high if the word is frequent in this transaction but rare across all training rows. Rare, distinctive words like `"zomato"` therefore matter more than common ones. (We keep raw tf-idf weights — no row normalization — because normalized rows flatten Naive Bayes' confidence scores on short texts.)
3. **Multinomial Naive Bayes** — a probabilistic classifier that learns, for each category, how likely each word is to appear. For a new description it multiplies these likelihoods (with the "naive" assumption that words are independent) and picks the category with the highest posterior probability via `predict_proba`.
4. **Confidence gating** — if the top probability is below 0.55, the model admits uncertainty and the request is routed to the LLM fallback instead of returning a shaky guess.

Why this combo: merchant descriptions are short and vocabulary-driven ("uber", "netflix", "apollo"), which is exactly where Naive Bayes shines — it's fast, needs little data (~120 rows here), and its probabilities are well-calibrated for thresholding.

## Project layout

```
ml-service/
├── app/
│   ├── __init__.py
│   ├── main.py          # FastAPI app: /health, /categorize, /insights, /forecast
│   ├── categorizer.py   # TF-IDF + NB model, predict(), llm_fallback() stub
│   ├── insights.py      # pure generate_insights(payload)
│   ├── forecast.py      # pure forecast(payload)
│   └── model.joblib     # trained model (created by train_model.py)
├── data/
│   └── training_data.csv  # ~125 labelled Indian merchant strings
├── train_model.py       # standalone training + holdout evaluation
├── requirements.txt
└── README.md
```
