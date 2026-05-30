"""
Config Loader — reads a JSON scenario file and returns a validated ScenarioConfig.
"""
from __future__ import annotations
import json
from pathlib import Path
from typing import Any, Dict

from .schema import (
    AnomalyConfig, OutputConfig, RegimeConfig,
    ScenarioConfig, TickerConfig,
)
from .validator import validate


def load_file(path: str | Path) -> ScenarioConfig:
    """Load and validate a scenario config from a JSON file."""
    p = Path(path)
    if not p.exists():
        raise FileNotFoundError(f"Scenario config not found: {p}")
    with p.open() as f:
        raw = json.load(f)
    return _parse(raw)


def load_dict(raw: Dict[str, Any]) -> ScenarioConfig:
    """Parse and validate a scenario config from a plain dict."""
    return _parse(raw)


def _parse(raw: Dict[str, Any]) -> ScenarioConfig:
    tickers = {
        sym: TickerConfig(
            symbol=sym,
            start_price=float(cfg["startPrice"]),
            base_volatility=float(cfg["baseVolatility"]),
            avg_volume=int(cfg["avgVolume"]),
            avg_spread=float(cfg["avgSpread"]),
        )
        for sym, cfg in raw["tickers"].items()
    }

    regimes = [
        RegimeConfig(
            name=r["name"],
            duration_sec=float(r["durationSec"]),
            vol_multiplier=float(r.get("volMultiplier", 1.0)),
            volume_multiplier=float(r.get("volumeMultiplier", 1.0)),
            spread_multiplier=float(r.get("spreadMultiplier", 1.0)),
            anomaly_multiplier=float(r.get("anomalyMultiplier", 1.0)),
            directional_bias=float(r.get("directionalBias", 0.0)),
        )
        for r in raw["regimeSchedule"]
    ]

    ac = raw.get("anomalyConfig", {})
    anomaly = AnomalyConfig(
        base_rate=float(ac.get("baseRate", 0.03)),
        types=list(ac.get("types", ["price_spike", "price_crash",
                                     "volume_spike", "spread_blowout",
                                     "combined_stress"])),
        severity_min=float(ac.get("severityMin", 1.5)),
        severity_max=float(ac.get("severityMax", 4.0)),
        emit_labels=bool(ac.get("emitLabels", True)),
    )

    oc = raw.get("output", {})
    output = OutputConfig(
        mode=oc.get("mode", "kafka"),
        topic=oc.get("topic", "market-events"),
        brokers=oc.get("brokers", "localhost:9092"),
        file_path=oc.get("filePath"),
    )

    cfg = ScenarioConfig(
        scenario_name=raw["scenarioName"],
        seed=int(raw["seed"]),
        target_eps=float(raw["targetEps"]),
        tickers=tickers,
        regime_schedule=regimes,
        anomaly_config=anomaly,
        output=output,
        duration_sec=float(raw["durationSec"]) if raw.get("durationSec") is not None else None,
        max_events=int(raw["maxEvents"]) if raw.get("maxEvents") is not None else None,
    )

    validate(cfg)
    return cfg
