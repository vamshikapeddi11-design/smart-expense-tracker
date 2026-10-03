"""
Standalone model training script.

Reads data/training_data.csv, trains the TF-IDF + MultinomialNB pipeline,
prints accuracy on a holdout split, and saves the model to app/model.joblib.

Run from the ml-service directory:
    python train_model.py
"""

from pathlib import Path

import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.metrics import accuracy_score, classification_report

# Reuse the exact same pipeline definition as the service so the saved
# model behaves identically to the one trained on import.
from app.categorizer import train_pipeline, MODEL_PATH, DATA_PATH

# Fixed seed so the holdout accuracy is reproducible between runs.
RANDOM_STATE = 42


def main() -> None:
    # 1. Load the labelled merchant strings.
    df = pd.read_csv(DATA_PATH)
    print(f"Loaded {len(df)} rows from {DATA_PATH}")
    print(f"Categories: {sorted(df['category'].unique())}")

    # 2. Split into train / holdout so we can report honest accuracy.
    #    stratify= keeps every category represented in both splits.
    X_train, X_test, y_train, y_test = train_test_split(
        df["text"], df["category"],
        test_size=0.2,
        random_state=RANDOM_STATE,
        stratify=df["category"],
    )
    print(f"Train: {len(X_train)} rows | Holdout: {len(X_test)} rows")

    # 3. Train the pipeline (TF-IDF vectorizer + Naive Bayes classifier).
    model = train_pipeline()
    # Refit on the train split only, so the holdout stays unseen.
    model.fit(X_train, y_train)

    # 4. Evaluate on the unseen holdout split.
    predictions = model.predict(X_test)
    accuracy = accuracy_score(y_test, predictions)
    print(f"\nHoldout accuracy: {accuracy:.2%}")
    print("\nPer-category report:")
    print(classification_report(y_test, predictions, zero_division=0))

    # 5. Retrain on ALL data for the final artefact (more data = better model),
    #    then save it where the service expects it.
    model.fit(df["text"], df["category"])
    joblib.dump(model, MODEL_PATH)
    print(f"Saved model to {MODEL_PATH}")


if __name__ == "__main__":
    main()
