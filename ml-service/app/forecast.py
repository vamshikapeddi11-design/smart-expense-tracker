"""
Spending forecaster.

Pure function: forecast(payload) -> {"forecast": {...}, "method": ...}

Predicts next month's spend per category with a 3-month moving average.
"""


def forecast(payload: dict) -> dict:
    """Forecast next month's spend per category.

    Expected payload: {"monthly_totals": {"Food": [3200, 3500, 3900], ...}}
    where each list holds past monthly totals, oldest first.

    Method: mean of the last 3 months. If fewer than 3 data points exist,
    average whatever is available (1 or 2 points). Results are rounded to
    2 decimals.

    Note (possible extension): a linear regression on the month index
    (e.g. sklearn.linear_model.LinearRegression) would capture trends -
    predicting a rising Food spend of 4200 instead of the flat average -
    but it needs more history to be stable, so the moving average is the
    safer default for a mini-project.
    """
    monthly_totals = payload.get("monthly_totals", {})
    result = {}

    for category, values in monthly_totals.items():
        # Skip empty histories; nothing to average.
        if not values:
            continue
        # Take the most recent 3 months (or fewer if history is short).
        window = values[-3:]
        average = sum(window) / len(window)
        result[category] = round(float(average), 2)

    return {"forecast": result, "method": "moving_average_3m"}
