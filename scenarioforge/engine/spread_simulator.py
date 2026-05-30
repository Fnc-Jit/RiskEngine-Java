"""
Spread Simulator — generates synthetic bid-ask spreads.
Wider spreads under high volatility, low liquidity, or anomaly conditions.
"""
from __future__ import annotations
import random
from ..config.schema import RegimeConfig, TickerConfig


class SpreadSimulator:
    def __init__(self, ticker: TickerConfig, rng: random.Random) -> None:
        self._ticker = ticker
        self._rng = rng

    def next_spread(self, regime: RegimeConfig, anomaly_active: bool = False) -> float:
        base = self._ticker.avg_spread * regime.spread_multiplier
        if anomaly_active:
            base *= self._rng.uniform(1.5, 3.0)
        noise = self._rng.gauss(1.0, 0.15)
        spread = base * max(noise, 0.1)
        return round(max(spread, 0.001), 6)
