"""
Generator — the main simulation loop.
Ties together all simulators and drives event production at the configured EPS.
"""
from __future__ import annotations
import random
import time
from typing import Callable, Dict, Iterator

from ..config.schema import ScenarioConfig
from .anomaly_injector import AnomalyInjector
from .price_simulator import PriceSimulator
from .regime_scheduler import RegimeScheduler
from .serializer import build_event
from .spread_simulator import SpreadSimulator
from .volume_simulator import VolumeSimulator


def run(cfg: ScenarioConfig, sink: Callable[[Dict], None]) -> None:
    """
    Run the generator, calling sink(event_dict) for each produced event.

    Args:
        cfg:  Validated ScenarioConfig.
        sink: Callable that receives each event dict (Kafka, file, console).
    """
    rng = random.Random(cfg.seed)

    # Per-ticker simulators
    price_sims = {sym: PriceSimulator(t, random.Random(rng.randint(0, 2**31)))
                  for sym, t in cfg.tickers.items()}
    vol_sims   = {sym: VolumeSimulator(t, random.Random(rng.randint(0, 2**31)))
                  for sym, t in cfg.tickers.items()}
    spread_sims = {sym: SpreadSimulator(t, random.Random(rng.randint(0, 2**31)))
                   for sym, t in cfg.tickers.items()}

    scheduler = RegimeScheduler(cfg.regime_schedule)
    injector  = AnomalyInjector(cfg.anomaly_config, random.Random(rng.randint(0, 2**31)))

    tickers = list(cfg.tickers.keys())
    ticker_rng = random.Random(rng.randint(0, 2**31))

    interval = 1.0 / cfg.target_eps
    dt = interval  # time step per event in seconds

    max_events = cfg.max_events
    duration_sec = cfg.duration_sec

    seq = 0
    start_wall = time.monotonic()
    batch_start = start_wall

    while True:
        # Stop conditions
        if max_events is not None and seq >= max_events:
            break
        elapsed = time.monotonic() - start_wall
        if duration_sec is not None and elapsed >= duration_sec:
            break

        # Advance regime
        scheduler.advance(dt)
        regime = scheduler.current

        # Pick ticker
        ticker = ticker_rng.choice(tickers)

        # Evaluate anomaly
        anomaly = injector.evaluate(regime)

        # Simulate fields
        price  = price_sims[ticker].next_price(regime, dt)
        if anomaly.active:
            price = round(price * anomaly.price_multiplier, 4)
            price = max(price, 0.01)

        volume = vol_sims[ticker].next_volume(regime, anomaly.active)
        if anomaly.active and anomaly.volume_multiplier != 1.0:
            volume = max(int(volume * anomaly.volume_multiplier), 100)

        spread = spread_sims[ticker].next_spread(regime, anomaly.active)
        if anomaly.active and anomaly.spread_multiplier != 1.0:
            spread = round(spread * anomaly.spread_multiplier, 6)

        # Current volatility estimate (base * regime multiplier)
        volatility = cfg.tickers[ticker].base_volatility * regime.vol_multiplier

        event = build_event(
            seq=seq,
            ticker=ticker,
            price=price,
            volume=volume,
            volatility=volatility,
            bid_ask_spread=spread,
            regime_name=regime.name,
            anomaly_tag=anomaly.active,
            anomaly_type=anomaly.anomaly_type if cfg.anomaly_config.emit_labels else None,
            scenario_name=cfg.scenario_name,
            emit_labels=cfg.anomaly_config.emit_labels,
        )

        sink(event)
        seq += 1

        # Rate control — sleep to maintain target EPS
        expected_wall = start_wall + seq * interval
        sleep_time = expected_wall - time.monotonic()
        if sleep_time > 0:
            time.sleep(sleep_time)

    elapsed_total = time.monotonic() - start_wall
    actual_eps = seq / elapsed_total if elapsed_total > 0 else 0
    print(f"[ScenarioForge] Done — {seq} events in {elapsed_total:.1f}s "
          f"({actual_eps:.1f} eps actual)")
