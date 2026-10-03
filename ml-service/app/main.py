"""
Smart Expense Tracker - ML microservice.

Run with:
    uvicorn app.main:app --host 0.0.0.0 --port 8000

Called by the Spring Boot backend via the `ml.service.url` property
(default http://localhost:8000).
"""

from typing import Dict, List, Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from app.categorizer import MODEL_VERSION, predict
from app.forecast import forecast as run_forecast
from app.insights import generate_insights

# ---------------------------------------------------------------------------
# App
# ---------------------------------------------------------------------------

app = FastAPI(
    title="Smart Expense Tracker - ML Service",
    description="Transaction categorization, spending insights and forecasting.",
    version=MODEL_VERSION,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Request / response models
# ---------------------------------------------------------------------------

class CategorizeRequest(BaseModel):
    text: str = Field(
        ...,
        examples=["SWIGGY BANGALORE"],
        description="Raw transaction description / merchant string.",
    )


class CategorizeResponse(BaseModel):
    category: str = Field(examples=["Food"])
    confidence: float = Field(examples=[0.92])
    source: str = Field(
        examples=["model"],
        description="One of: model, llm_fallback, default.",
    )


class Transaction(BaseModel):
    amount: float = Field(examples=[250])
    category: str = Field(examples=["Food"])
    date: str = Field(examples=["2026-09-05"])
    merchant: str = Field(examples=["Swiggy"])


class Budget(BaseModel):
    category: str = Field(examples=["Food"])
    monthlyLimit: float = Field(examples=[5000])


class InsightsRequest(BaseModel):
    transactions: List[Transaction] = Field(
        default_factory=list,
        examples=[[
            {"amount": 250, "category": "Food", "date": "2026-09-05", "merchant": "Swiggy"},
            {"amount": 45000, "category": "Rent", "date": "2026-09-01", "merchant": "Landlord"},
        ]],
    )
    budgets: List[Budget] = Field(
        default_factory=list,
        examples=[[{"category": "Food", "monthlyLimit": 5000}]],
    )
    prev_month_total: Optional[float] = Field(default=None, examples=[4200])
    current_month_total: Optional[float] = Field(default=None, examples=[5460])
    prev_month_by_category: Optional[Dict[str, float]] = Field(
        default=None,
        examples=[{"Food": 4000, "Transport": 900}],
        description="Last month's spend per category, for per-category MoM comparison.",
    )
    prev_subscriptions_total: Optional[float] = Field(default=None, examples=[1200])
    current_subscriptions_total: Optional[float] = Field(default=None, examples=[1500])


class Anomaly(BaseModel):
    transaction_index: int = Field(examples=[3])
    reason: str = Field(
        examples=["Unusually large: \u20b945,000 in Rent (5.2x your median)"]
    )
    severity: str = Field(examples=["high"])


class InsightsResponse(BaseModel):
    insights: List[str] = Field(
        examples=[["You spent 30% more on food this month vs last month."]]
    )
    anomalies: List[Anomaly] = Field(default_factory=list)


class ForecastRequest(BaseModel):
    monthly_totals: Dict[str, List[float]] = Field(
        ...,
        examples=[{"Food": [3200, 3500, 3900], "Transport": [800, 850, 900]}],
        description="Past monthly totals per category, oldest month first.",
    )


class ForecastResponse(BaseModel):
    forecast: Dict[str, float] = Field(examples=[{"Food": 4200.0, "Transport": 950.0}])
    method: str = Field(examples=["moving_average_3m"])


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    """Liveness check used by the backend before calling the ML service."""
    return {"status": "ok", "model_version": MODEL_VERSION}


@app.post("/categorize", response_model=CategorizeResponse)
def categorize(body: CategorizeRequest):
    """Classify a transaction description into one of the 11 categories."""
    return predict(body.text)


@app.post("/insights", response_model=InsightsResponse)
def insights(body: InsightsRequest):
    """Generate natural-language insights and anomaly flags."""
    # model_dump() converts the Pydantic models into plain dicts/lists,
    # which is what the pure generate_insights() function expects.
    return generate_insights(body.model_dump())


@app.post("/forecast", response_model=ForecastResponse)
def forecast(body: ForecastRequest):
    """Forecast next month's spend per category (3-month moving average)."""
    return run_forecast(body.model_dump())
