"""
Regime Scheduler — tracks the active market regime by elapsed time.
"""
from __future__ import annotations
from typing import List
from ..config.schema import RegimeConfig


class RegimeScheduler:
    """
    Iterates through a list of RegimeConfig entries in order.
    Each regime is active for its configured durationSec.
    After the last regime, the final regime stays active indefinitely.
    """

    def __init__(self, schedule: List[RegimeConfig]) -> None:
        self._schedule = schedule
        self._index = 0
        self._elapsed_in_regime = 0.0

    @property
    def current(self) -> RegimeConfig:
        return self._schedule[self._index]

    def advance(self, dt_sec: float) -> None:
        """Advance the scheduler by dt_sec seconds."""
        self._elapsed_in_regime += dt_sec
        while (
            self._index < len(self._schedule) - 1
            and self._elapsed_in_regime >= self._schedule[self._index].duration_sec
        ):
            self._elapsed_in_regime -= self._schedule[self._index].duration_sec
            self._index += 1

    def reset(self) -> None:
        self._index = 0
        self._elapsed_in_regime = 0.0
