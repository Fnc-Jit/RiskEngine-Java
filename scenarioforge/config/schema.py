"""
Typed dataclasses representing the ScenarioForge configuration schema.
These are the internal Python objects produced by the config loader.
"""
from __future__ import annotations
from dataclasses import dataclass, field
from typing import Dict, List, Optional


@dataclass
class TickerConfig:
    symbol: str
    start_price: float
    base_volatility: float
    avg_volume: int
    avg_spread: float


@dataclass
class RegimeConfig:
    name: str
    duration_sec: float
    vol_multiplier: float = 1.0
    volume_multiplier: float = 1.0
    spread_multiplier: float = 1.0
    anomaly_multiplier: float = 1.0
    directional_bias: float = 0.0


@dataclass
class AnomalyConfig:
    base_rate: float = 0.03
    types: List[str] = field(default_factory=lambda: [
        "price_spike", "price_crash", "volume_spike",
        "spread_blowout", "combined_stress"
    ])
    severity_min: float = 1.5
    severity_max: float = 4.0
    emit_labels: bool = True


@dataclass
class OutputConfig:
    mode: str = "kafka"          # kafka | file | console
    topic: str = "market-events"
    brokers: str = "localhost:9092"
    file_path: Optional[str] = None


@dataclass
class ScenarioConfig:
    scenario_name: str
    seed: int
    target_eps: float
    tickers: Dict[str, TickerConfig]
    regime_schedule: List[RegimeConfig]
    anomaly_config: AnomalyConfig
    output: OutputConfig
    duration_sec: Optional[float] = None
    max_events: Optional[int] = None
