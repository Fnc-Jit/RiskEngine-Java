"""
File Sink — writes events as newline-delimited JSON to a file.
"""
from __future__ import annotations
import json
from pathlib import Path
from typing import Dict


class FileSink:
    def __init__(self, path: str) -> None:
        self._path = Path(path)
        self._path.parent.mkdir(parents=True, exist_ok=True)
        self._f = self._path.open("w", encoding="utf-8")
        print(f"[FileSink] Writing to {self._path}")

    def send(self, event: Dict) -> None:
        self._f.write(json.dumps(event) + "\n")

    def flush(self) -> None:
        self._f.flush()

    def close(self) -> None:
        self._f.close()
        print(f"[FileSink] Closed {self._path}")
