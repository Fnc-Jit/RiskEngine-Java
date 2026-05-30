#!/usr/bin/env python3
"""
ONNX Model Training Script — Isolation Forest for Market Anomaly Detection.

Trains a simple Isolation Forest on synthetic feature data and exports it
to ONNX format for the Java RiskEngine inference pipeline.

Generates a golden vector file for parity testing.

Usage:
    pip install scikit-learn skl2onnx numpy
    python train_model.py
"""

import json
import numpy as np
from sklearn.ensemble import IsolationForest
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType


def generate_training_data(n_samples=5000, seed=42):
    """Generate synthetic training features mimicking market data."""
    rng = np.random.RandomState(seed)

    # 6 features matching the FeatureExtractor output:
    # [priceZScore, volumeRatio, rollingVolatility, bidAskSpread, timeOfDayEncoding, directionStreak]
    normal_data = rng.randn(n_samples, 6) * np.array([1.0, 0.3, 0.02, 0.05, 0.3, 0.3])
    normal_data[:, 1] += 1.0       # volume ratio centered around 1.0
    normal_data[:, 4] = np.clip(normal_data[:, 4] + 0.5, 0, 1)  # time of day in [0,1]

    # Inject ~5% anomalies
    n_anomalies = int(n_samples * 0.05)
    anomalies = rng.randn(n_anomalies, 6) * np.array([3.0, 1.5, 0.08, 0.15, 0.3, 0.8])
    anomalies[:, 1] += 3.0  # High volume ratio
    anomalies[:, 4] = np.clip(anomalies[:, 4] + 0.5, 0, 1)

    data = np.vstack([normal_data, anomalies]).astype(np.float32)
    rng.shuffle(data)
    return data


def main():
    print("Training Isolation Forest model...")
    data = generate_training_data()
    print(f"Training data shape: {data.shape}")

    model = IsolationForest(
        n_estimators=100,
        contamination=0.05,
        random_state=42,
        max_samples='auto',
    )
    model.fit(data)
    print("Model trained successfully.")

    # Export to ONNX
    initial_type = [("features", FloatTensorType([None, 6]))]
    onnx_model = convert_sklearn(
        model,
        initial_types=initial_type,
        target_opset={"": 17, "ai.onnx.ml": 3},
        options={id(model): {"score_samples": True}},
    )

    output_path = "../model/risk_model.onnx"
    with open(output_path, "wb") as f:
        f.write(onnx_model.SerializeToString())
    print(f"ONNX model saved to: {output_path}")

    # Generate golden vectors for parity testing
    rng = np.random.RandomState(123)
    golden_inputs = rng.randn(20, 6).astype(np.float32)
    golden_inputs[:, 1] += 1.0
    golden_inputs[:, 4] = np.clip(golden_inputs[:, 4] + 0.5, 0, 1)

    raw_scores = model.decision_function(golden_inputs)

    golden_vectors = []
    for i in range(len(golden_inputs)):
        golden_vectors.append({
            "input": golden_inputs[i].tolist(),
            "expectedRawScore": float(raw_scores[i]),
        })

    golden_path = "../model/golden_vectors.json"
    with open(golden_path, "w") as f:
        json.dump(golden_vectors, f, indent=2)
    print(f"Golden vectors saved to: {golden_path}")
    print("Done!")


if __name__ == "__main__":
    main()
