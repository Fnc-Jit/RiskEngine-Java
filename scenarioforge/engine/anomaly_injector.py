"""
Anomaly Injector — decides whether an anomaly occurs and perturbs event fields.
"""
from __future__ import annotations
import random
from dataclasses import dataclass
from typing import Optional
from ..config.schema import AnomalyConfig, RegimeConfig


@dataclass
class AnomalyResult:
    active: bool
    anomaly_type: Optional[str]
    price_multiplier: float = 1.0
    volume_multiplier: float = 1.0
    spread_multiplier: float = 1.0


_NO_ANOMALY = AnomalyResult(active=False, anomaly_type=None)


class AnomalyInjector:
    def __init__(self, config: AnomalyConfig, rng: random.Random) -> None:
        self._cfg = config
        self._rng = rng

    def evaluate(self, regime: RegimeConfig) -> AnomalyResult:
        effective_rate = self._cfg.base_rate * regime.anomaly_multiplier
        if self._rng.random() >= effective_rate:
            return _NO_ANOMALY

        if not self._cfg.types:
            return _NO_ANOMALY

        anomaly_type = self._rng.choice(self._cfg.types)
        severity = self._rng.uniform(self._cfg.severity_min, self._cfg.severity_max)

        if anomaly_type == "price_spike":
            return AnomalyResult(True, anomaly_type,
                                 price_multiplier=1.0 + (severity - 1.0) * 0.15)
        elif anomaly_type == "price_crash":
            return AnomalyResult(True, anomaly_type,
                                 price_multiplier=1.0 - (severity - 1.0) * 0.12)
        elif anomaly_type == "volume_spike":
            return AnomalyResult(True, anomaly_type,
                                 volume_multiplier=severity * 2.0)
        elif anomaly_type == "spread_blowout":
            return AnomalyResult(True, anomaly_type,
                                 spread_multiplier=severity * 1.5)
        elif anomaly_type == "combined_stress":
            return AnomalyResult(True, anomaly_type,
                                 price_multiplier=1.0 - (severity - 1.0) * 0.08,
                                 volume_multiplier=severity * 1.8,
                                 spread_multiplier=severity * 1.3)
        else:
            return AnomalyResult(True, anomaly_type)
