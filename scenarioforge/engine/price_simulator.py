"""
Price Simulator — maintains per-ticker price state and generates next price.

Uses a simple geometric Brownian motion step:
    price_t+1 = price_t * exp(bias*dt + vol*sqrt(dt)*Z)
where Z ~ N(0,1) and dt is the inter-event time in seconds.
"""
from __future__ import annotations
import math
import random
from ..config.schema import RegimeConfig, TickerConfig


class PriceSimulator:
    def __init__(self, ticker: TickerConfig, rng: random.Random) -> None:
        self._ticker = ticker
        self._rng = rng
        self._price = ticker.start_price

    @property
    def current_price(self) -> float:
        return self._price

    def next_price(self, regime: RegimeConfig, dt: float) -> float:
        vol = self._ticker.base_volatility * regime.vol_multiplier
        bias = regime.directional_bias * dt
        z = self._rng.gauss(0.0, 1.0)
        log_return = bias + vol * math.sqrt(max(dt, 1e-6)) * z
        self._price = max(self._price * math.exp(log_return), 0.01)
        return round(self._price, 4)
