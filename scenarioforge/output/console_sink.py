"""
Console Sink — prints events to stdout for preview/debugging.
"""
from __future__ import annotations
import json
from typing import Dict


class ConsoleSink:
    def __init__(self, max_events: int = 20) -> None:
        self._max = max_events
        self._count = 0

    def send(self, event: Dict) -> None:
        if self._count < self._max:
            print(json.dumps(event, indent=2))
        elif self._count == self._max:
            print(f"[ConsoleSink] Preview limit ({self._max}) reached — suppressing further output")
        self._count += 1

    def flush(self) -> None:
        pass

    def close(self) -> None:
        print(f"[ConsoleSink] Total events generated: {self._count}")
