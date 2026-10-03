"""
Spending insights engine.

Pure function: generate_insights(payload) -> {"insights": [...], "anomalies": [...]}
No network, no state - easy to unit test.
"""

from statistics import median


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _rupees(amount: float) -> str:
    """Format an amount as Indian rupees, e.g. 45000 -> '₹45,000'."""
    return f"\u20b9{amount:,.0f}"


def _pct_change(current: float, previous: float):
    """Return percent change of current vs previous, or None if undefined."""
    if previous == 0:
        return None
    return (current - previous) / previous * 100


def _summarise_by_category(transactions: list) -> dict:
    """Map each category to its total spend in the given transactions."""
    totals: dict = {}
    for txn in transactions:
        category = txn.get("category", "Other")
        totals[category] = totals.get(category, 0) + float(txn.get("amount", 0))
    return totals


# ---------------------------------------------------------------------------
# Insight builders
# ---------------------------------------------------------------------------

def _month_over_month_insights(payload: dict, current_by_cat: dict) -> list:
    """Natural-language sentences about spending change vs last month.

    Uses the optional 'prev_month_by_category' map for per-category
    comparisons; otherwise falls back to the overall monthly totals.
    """
    insights = []
    prev_by_cat = payload.get("prev_month_by_category") or {}

    for category in sorted(current_by_cat):
        current = current_by_cat[category]
        previous = prev_by_cat.get(category)
        if previous is None:
            continue  # no last-month data for this category: skip
        change = _pct_change(current, previous)
        if change is None or abs(change) < 1:
            continue  # tiny/no change: not worth reporting
        direction = "more" if change > 0 else "less"
        insights.append(
            f"You spent {abs(change):.0f}% {direction} on "
            f"{category.lower()} this month vs last month "
            f"({_rupees(previous)} \u2192 {_rupees(current)})."
        )

    # Overall month-to-month comparison, when both totals are provided.
    prev_total = payload.get("prev_month_total")
    curr_total = payload.get("current_month_total")
    if prev_total and curr_total:
        change = _pct_change(curr_total, prev_total)
        if change is not None and abs(change) >= 1:
            direction = "more" if change > 0 else "less"
            insights.append(
                f"Overall spending is {abs(change):.0f}% {direction} than "
                f"last month ({_rupees(prev_total)} \u2192 {_rupees(curr_total)})."
            )
    return insights


def _subscription_delta_insight(payload: dict) -> list:
    """Report how subscription spending changed vs last month."""
    insights = []
    prev = payload.get("prev_subscriptions_total")
    curr = payload.get("current_subscriptions_total")
    if prev is None or curr is None:
        return insights
    change = _pct_change(curr, prev)
    if change is None:
        return insights
    if abs(change) < 1:
        insights.append("Subscription spending is about the same as last month.")
    elif change > 0:
        insights.append(
            f"Subscription spending rose {change:.0f}% vs last month "
            f"({_rupees(prev)} \u2192 {_rupees(curr)}). Check for price hikes "
            f"or new subscriptions you forgot about."
        )
    else:
        insights.append(
            f"Subscription spending fell {abs(change):.0f}% vs last month "
            f"({_rupees(prev)} \u2192 {_rupees(curr)}). Nice saving!"
        )
    return insights


def _budget_insights(payload: dict, current_by_cat: dict) -> list:
    """Warn about breached budgets, or budgets close to their limit."""
    insights = []
    for budget in payload.get("budgets", []):
        category = budget.get("category")
        limit = float(budget.get("monthlyLimit", 0))
        if limit <= 0:
            continue
        spent = current_by_cat.get(category, 0)
        if spent >= limit:
            insights.append(
                f"Budget alert: you breached your {category} budget - "
                f"spent {_rupees(spent)} of {_rupees(limit)}."
            )
        elif spent >= 0.8 * limit:
            insights.append(
                f"Heads up: {category} spending is at {_rupees(spent)} of your "
                f"{_rupees(limit)} budget ({spent / limit * 100:.0f}% used)."
            )
    return insights


# ---------------------------------------------------------------------------
# Anomaly detection
# ---------------------------------------------------------------------------

def _large_amount_anomalies(transactions: list) -> list:
    """Flag any transaction larger than 3x the median of its category.

    Median (not mean) is used because a single huge transaction would skew
    the mean and hide itself.
    """
    anomalies = []
    # Group amounts by category so each transaction is compared to its peers.
    amounts_by_cat: dict = {}
    for txn in transactions:
        category = txn.get("category", "Other")
        amounts_by_cat.setdefault(category, []).append(float(txn.get("amount", 0)))

    for idx, txn in enumerate(transactions):
        category = txn.get("category", "Other")
        amounts = amounts_by_cat[category]
        med = median(amounts)
        amount = float(txn.get("amount", 0))
        if med > 0 and amount > 3 * med:
            ratio = amount / med
            severity = "high" if ratio >= 5 else "medium"
            anomalies.append({
                "transaction_index": idx,
                "reason": (
                    f"Unusually large: {_rupees(amount)} in {category} "
                    f"({ratio:.1f}x your median)"
                ),
                "severity": severity,
            })
    return anomalies


def _duplicate_anomalies(transactions: list) -> list:
    """Flag exact duplicates: same amount + merchant + date appearing twice."""
    anomalies = []
    seen = {}  # (amount, merchant, date) -> first index where it appeared
    for idx, txn in enumerate(transactions):
        key = (
            float(txn.get("amount", 0)),
            str(txn.get("merchant", "")).strip().lower(),
            str(txn.get("date", "")),
        )
        if key in seen:
            anomalies.append({
                "transaction_index": idx,
                "reason": (
                    f"Possible duplicate of transaction #{seen[key]}: "
                    f"{_rupees(key[0])} at '{txn.get('merchant')}' on {txn.get('date')}"
                ),
                "severity": "medium",
            })
        else:
            seen[key] = idx
    return anomalies


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def generate_insights(payload: dict) -> dict:
    """Build insights + anomalies from a payload dict.

    Expected payload keys:
      - transactions: list of {"amount", "category", "date", "merchant"}
      - budgets:      list of {"category", "monthlyLimit"}          (optional)
      - prev_month_total / current_month_total                    (optional)
      - prev_month_by_category: {category: amount}                 (optional)
      - prev_subscriptions_total / current_subscriptions_total    (optional)

    Returns {"insights": [str, ...], "anomalies": [{transaction_index,
    reason, severity}, ...]}.
    """
    transactions = payload.get("transactions", [])
    current_by_cat = _summarise_by_category(transactions)

    insights = []
    insights += _month_over_month_insights(payload, current_by_cat)
    insights += _subscription_delta_insight(payload)
    insights += _budget_insights(payload, current_by_cat)

    if not insights:
        insights.append("No notable spending changes this month. Keep it up!")

    anomalies = _large_amount_anomalies(transactions)
    anomalies += _duplicate_anomalies(transactions)

    return {"insights": insights, "anomalies": anomalies}
