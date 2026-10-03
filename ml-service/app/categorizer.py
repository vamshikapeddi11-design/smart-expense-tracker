"""
Transaction categorizer.

Loads a pre-trained TF-IDF + Multinomial Naive Bayes pipeline from
``app/model.joblib`` if it exists; otherwise it trains a fresh pipeline
on ``data/training_data.csv`` when this module is imported.

Public API:
    predict(text)      -> {"category": str, "confidence": float, "source": str}
    llm_fallback(text) -> {"category": str, "confidence": float, "source": str}
"""

import os
import re
from pathlib import Path

import joblib
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB
from sklearn.pipeline import Pipeline

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------

# The 11 expense categories used across the whole project.
CATEGORIES = [
    "Food", "Transport", "Shopping", "Utilities", "Rent",
    "Subscriptions", "Health", "Entertainment", "Education",
    "Travel", "Other",
]

# Model version surfaced by GET /health.
MODEL_VERSION = "1.0.0"

# Below this confidence we do not trust the ML prediction and fall back.
CONFIDENCE_THRESHOLD = 0.55

# Files live next to this module: app/model.joblib and ../data/training_data.csv
BASE_DIR = Path(__file__).resolve().parent
MODEL_PATH = BASE_DIR / "model.joblib"
DATA_PATH = BASE_DIR.parent / "data" / "training_data.csv"


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def normalize_text(text: str) -> str:
    """Clean a raw transaction description for the model.

    Steps: lowercase, strip punctuation/special chars, collapse whitespace.
    Keeping the exact output format matters because the TF-IDF vectorizer
    was fitted on text normalized the same way.
    """
    if not text:
        return ""
    text = text.lower()
    # Keep only letters, digits and spaces (drop punctuation like "-" or "/").
    text = re.sub(r"[^a-z0-9\s]", " ", text)
    # Collapse runs of whitespace into a single space.
    text = re.sub(r"\s+", " ", text).strip()
    return text


def train_pipeline() -> Pipeline:
    """Train a TF-IDF + MultinomialNB pipeline on data/training_data.csv."""
    df = pd.read_csv(DATA_PATH)
    # NB works well on short, sparse merchant strings, so word unigrams +
    # bigrams (e.g. "electricity bill") capture the useful phrases.
    # norm=None: keep raw tf-idf weights instead of l2-normalizing each row.
    # l2-normalized rows shrink every feature below 1, which flattens the
    # Naive Bayes posteriors (nothing ever crossed our 0.55 gate); raw
    # tf-idf weights fit NB's multinomial event model much better.
    pipeline = Pipeline([
        ("tfidf", TfidfVectorizer(
            preprocessor=normalize_text,
            ngram_range=(1, 2),   # unigrams + bigrams
            min_df=1,
            norm=None,
        )),
        ("clf", MultinomialNB()),
    ])
    pipeline.fit(df["text"], df["category"])
    return pipeline


def load_or_train_model() -> Pipeline:
    """Load model.joblib if present, else train and persist it."""
    if MODEL_PATH.exists():
        return joblib.load(MODEL_PATH)
    model = train_pipeline()
    # Persist so later imports / restarts reuse the same model.
    joblib.dump(model, MODEL_PATH)
    return model


# ---------------------------------------------------------------------------
# Model singleton (loaded once at import time)
# ---------------------------------------------------------------------------
_model = load_or_train_model()


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def predict(text: str) -> dict:
    """Categorize a transaction description.

    Returns {"category", "confidence", "source"} where source is one of:
      - "default"      : empty/blank input -> always "Other"
      - "model"        : ML prediction with confidence >= threshold
      - "llm_fallback" : ML confidence below threshold -> LLM stub used
    """
    # Empty input short-circuits: nothing to classify.
    if not text or not text.strip():
        return {"category": "Other", "confidence": 1.0, "source": "default"}

    cleaned = normalize_text(text)
    if not cleaned:  # text was only punctuation, e.g. "---"
        return {"category": "Other", "confidence": 1.0, "source": "default"}

    # predict_proba gives a probability per class; the max is our confidence.
    probabilities = _model.predict_proba([text])[0]
    best_idx = int(probabilities.argmax())
    category = str(_model.classes_[best_idx])
    confidence = round(float(probabilities[best_idx]), 2)

    # Low-confidence predictions are handed to the LLM fallback instead
    # of returning a guess the model itself is unsure about.
    if confidence < CONFIDENCE_THRESHOLD:
        return llm_fallback(text)

    return {"category": category, "confidence": confidence, "source": "model"}


def llm_fallback(text: str) -> dict:
    """Stub for an LLM-based categorization fallback.

    Design: when the Naive Bayes model is not confident (< 0.55), we would
    ask a large language model (OpenAI / Anthropic) to classify the text.
    The API key is read from the LLM_API_KEY environment variable and is
    never hardcoded in the source.

    TODO (real integration):
        1. Set the environment variable:  export LLM_API_KEY="sk-..."
        2. Uncomment the client call below and send a prompt such as:
               "Classify this expense into one of: Food, Transport, ...
                Text: '<text>'. Reply with JSON {category, confidence}."
        3. Parse the JSON reply and return it with source="llm_fallback".
        4. Wrap the call in try/except and fall back to {"Other", 0.4}
           if the API is unreachable or the key is missing.

    # Example (DO NOT hardcode keys - use env vars only):
    #   import os
    #   from openai import OpenAI
    #   api_key = os.getenv("LLM_API_KEY")
    #   if not api_key:
    #       return {"category": "Other", "confidence": 0.4, "source": "llm_fallback"}
    #   client = OpenAI(api_key=api_key)
    #   response = client.chat.completions.create(
    #       model="gpt-4o-mini",
    #       messages=[{"role": "user", "content": prompt}],
    #       response_format={"type": "json_object"},
    #   )
    #   ... parse response.choices[0].message.content ...

    For now (no key configured in this mini-project) we return a safe
    default so the pipeline never crashes.
    """
    _ = text  # currently unused; will feed the LLM prompt once wired up
    return {"category": "Other", "confidence": 0.4, "source": "llm_fallback"}
