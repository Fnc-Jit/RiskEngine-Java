"""
Volume Simulator — generates per-event volume values.
"""
from __future__ import annotations
import random
from ..config.schema import RegimeConfig, TickerConfig


class VolumeSimulator:
    def __init__(self, ticker: TickerConfig, rng: random.Random) -> None:
        self._ticker = ticker
        self._rng = rng

    def next_volume(self, regime: RegimeConfig, anomaly_active: bool = False) -> int:
        base = self._ticker.avg_volume * regime.volume_multiplier
        if anomaly_active:
            base *= self._rng.uniform(2.5, 6.0)
        # Log-normal noise: mean=base, cv≈0.3
        sigma = 0.3
        mu = math.log(base) - 0.5 * sigma ** 2
        vol = int(math.exp(self._rng.gauss(mu, sigma)))
        return max(vol, 100)


import math  # noqa: E402 — placed after class to keep class readable
