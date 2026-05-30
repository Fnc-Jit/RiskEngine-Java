"""
Built-in scenario templates.
Each template is a plain dict matching the JSON config schema.
Users can copy and edit these as starting points.
"""
from __future__ import annotations
from typing import Any, Dict

_TICKERS_FULL = {
    "AAPL": {"startPrice": 210.0, "baseVolatility": 0.012, "avgVolume": 120000, "avgSpread": 0.03},
    "MSFT": {"startPrice": 420.0, "baseVolatility": 0.011, "avgVolume": 90000,  "avgSpread": 0.03},
    "GOOG": {"startPrice": 175.0, "baseVolatility": 0.013, "avgVolume": 70000,  "avgSpread": 0.04},
    "TSLA": {"startPrice": 260.0, "baseVolatility": 0.025, "avgVolume": 200000, "avgSpread": 0.06},
    "NVDA": {"startPrice": 130.0, "baseVolatility": 0.022, "avgVolume": 180000, "avgSpread": 0.05},
    "AMZN": {"startPrice": 185.0, "baseVolatility": 0.014, "avgVolume": 80000,  "avgSpread": 0.04},
    "JPM":  {"startPrice": 200.0, "baseVolatility": 0.010, "avgVolume": 60000,  "avgSpread": 0.04},
    "GS":   {"startPrice": 463.0, "baseVolatility": 0.014, "avgVolume": 40000,  "avgSpread": 0.05},
}

_TICKERS_SMALL = {k: v for k, v in list(_TICKERS_FULL.items())[:4]}

_ANOMALY_STANDARD = {
    "baseRate": 0.03,
    "types": ["price_spike", "price_crash", "volume_spike", "spread_blowout", "combined_stress"],
    "severityMin": 1.5,
    "severityMax": 4.0,
    "emitLabels": True,
}

_ANOMALY_HIGH = {**_ANOMALY_STANDARD, "baseRate": 0.10, "severityMin": 2.0, "severityMax": 6.0}
_ANOMALY_LOW  = {**_ANOMALY_STANDARD, "baseRate": 0.01}

_OUTPUT_KAFKA = {"mode": "kafka", "topic": "market-events", "brokers": "localhost:9092"}


TEMPLATES: Dict[str, Dict[str, Any]] = {

    "normal_day": {
        "scenarioName": "normal_day",
        "seed": 42,
        "targetEps": 100,
        "durationSec": 120,
        "tickers": _TICKERS_SMALL,
        "regimeSchedule": [
            {"name": "normal", "durationSec": 120,
             "volMultiplier": 1.0, "volumeMultiplier": 1.0,
             "spreadMultiplier": 1.0, "anomalyMultiplier": 1.0, "directionalBias": 0.0},
        ],
        "anomalyConfig": _ANOMALY_LOW,
        "output": _OUTPUT_KAFKA,
    },

    "volatile_open": {
        "scenarioName": "volatile_open",
        "seed": 42,
        "targetEps": 200,
        "durationSec": 120,
        "tickers": _TICKERS_SMALL,
        "regimeSchedule": [
            {"name": "volatile", "durationSec": 60,
             "volMultiplier": 2.5, "volumeMultiplier": 2.0,
             "spreadMultiplier": 1.8, "anomalyMultiplier": 2.0, "directionalBias": 0.0},
            {"name": "normal", "durationSec": 60,
             "volMultiplier": 1.0, "volumeMultiplier": 1.0,
             "spreadMultiplier": 1.0, "anomalyMultiplier": 1.0, "directionalBias": 0.0},
        ],
        "anomalyConfig": _ANOMALY_STANDARD,
        "output": _OUTPUT_KAFKA,
    },

    "flash_crash": {
        "scenarioName": "flash_crash",
        "seed": 42,
        "targetEps": 300,
        "durationSec": 120,
        "tickers": _TICKERS_FULL,
        "regimeSchedule": [
            {"name": "normal",   "durationSec": 40,
             "volMultiplier": 1.0, "volumeMultiplier": 1.0,
             "spreadMultiplier": 1.0, "anomalyMultiplier": 1.0, "directionalBias": 0.0},
            {"name": "volatile", "durationSec": 20,
             "volMultiplier": 2.5, "volumeMultiplier": 2.0,
             "spreadMultiplier": 1.8, "anomalyMultiplier": 2.0, "directionalBias": -0.3},
            {"name": "crash",    "durationSec": 15,
             "volMultiplier": 5.0, "volumeMultiplier": 4.0,
             "spreadMultiplier": 3.0, "anomalyMultiplier": 4.0, "directionalBias": -0.9},
            {"name": "recovery", "durationSec": 45,
             "volMultiplier": 1.8, "volumeMultiplier": 1.5,
             "spreadMultiplier": 1.3, "anomalyMultiplier": 1.2, "directionalBias": 0.5},
        ],
        "anomalyConfig": _ANOMALY_HIGH,
        "output": _OUTPUT_KAFKA,
    },

    "low_liquidity": {
        "scenarioName": "low_liquidity",
        "seed": 42,
        "targetEps": 50,
        "durationSec": 120,
        "tickers": _TICKERS_SMALL,
        "regimeSchedule": [
            {"name": "low_liquidity", "durationSec": 120,
             "volMultiplier": 1.3, "volumeMultiplier": 0.3,
             "spreadMultiplier": 3.0, "anomalyMultiplier": 1.5, "directionalBias": 0.0},
        ],
        "anomalyConfig": _ANOMALY_STANDARD,
        "output": _OUTPUT_KAFKA,
    },

    "mixed_demo": {
        "scenarioName": "mixed_demo",
        "seed": 42,
        "targetEps": 200,
        "durationSec": 120,
        "tickers": _TICKERS_FULL,
        "regimeSchedule": [
            {"name": "normal",   "durationSec": 50,
             "volMultiplier": 1.0, "volumeMultiplier": 1.0,
             "spreadMultiplier": 1.0, "anomalyMultiplier": 1.0, "directionalBias": 0.0},
            {"name": "volatile", "durationSec": 30,
             "volMultiplier": 2.2, "volumeMultiplier": 1.8,
             "spreadMultiplier": 1.6, "anomalyMultiplier": 1.7, "directionalBias": 0.0},
            {"name": "crash",    "durationSec": 10,
             "volMultiplier": 4.0, "volumeMultiplier": 3.2,
             "spreadMultiplier": 2.5, "anomalyMultiplier": 3.0, "directionalBias": -0.8},
            {"name": "recovery", "durationSec": 30,
             "volMultiplier": 1.5, "volumeMultiplier": 1.3,
             "spreadMultiplier": 1.2, "anomalyMultiplier": 1.1, "directionalBias": 0.4},
        ],
        "anomalyConfig": {
            "baseRate": 0.05,
            "types": ["price_spike", "price_crash", "volume_spike", "spread_blowout", "combined_stress"],
            "severityMin": 1.5,
            "severityMax": 4.0,
            "emitLabels": True,
        },
        "output": _OUTPUT_KAFKA,
    },

    "benchmark_high_throughput": {
        "scenarioName": "benchmark_high_throughput",
        "seed": 42,
        "targetEps": 1000,
        "durationSec": 300,
        "tickers": _TICKERS_FULL,
        "regimeSchedule": [
            {"name": "normal", "durationSec": 300,
             "volMultiplier": 1.0, "volumeMultiplier": 1.0,
             "spreadMultiplier": 1.0, "anomalyMultiplier": 1.0, "directionalBias": 0.0},
        ],
        "anomalyConfig": _ANOMALY_STANDARD,
        "output": _OUTPUT_KAFKA,
    },
}
