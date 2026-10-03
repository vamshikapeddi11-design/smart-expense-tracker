# ==============================================================================
# Smart Expense Tracker — ML service (FastAPI + scikit-learn)
# Builds the Python environment, trains the categorisation model at build time
# (so the container starts serving predictions immediately), then starts the
# API with uvicorn.
# ==============================================================================

FROM python:3.11-slim

WORKDIR /app

# Install dependencies first for better layer caching.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy the service source (app/ + train_model.py).
COPY . .

# Train the expense-categorisation model now, so it is baked into the image.
RUN python train_model.py

EXPOSE 8000

# Start the FastAPI app.
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
