"""
JSON Serializer — converts a generated event dict into the downstream payload format.
Field names match the Java MarketEvent DTO exactly.
"""
from __future__ import annotations
import json
import uuid
from datetime import datetime, timezone
from typing import Any, Dict, Optional


TIME_BUCKETS = ["PRE_MARKET", "OPEN", "MID_MORNING", "LUNCH", "AFTERNOON", "CLOSE", "AFTER_HOURS"]


def build_event(
    *,
    seq: int,
    ticker: str,
    price: float,
    volume: int,
    volatility: float,
    bid_ask_spread: float,
    regime_name: str,
    anomaly_tag: bool,
    anomaly_type: Optional[str],
    scenario_name: str,
    emit_labels: bool,
) -> Dict[str, Any]:
    event: Dict[str, Any] = {
        "eventId": f"sf-{uuid.uuid4().hex[:12]}",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "ticker": ticker,
        "price": price,
        "volume": volume,
        "volatility": round(volatility, 6),
        "bidAskSpread": round(bid_ask_spread, 6),
        "timeBucket": TIME_BUCKETS[seq % len(TIME_BUCKETS)],
        "anomalyTag": anomaly_tag,
        # Extended fields (ignored by Java DTO but useful for analysis)
        "regime": regime_name,
        "scenarioName": scenario_name,
        "sequenceNumber": seq,
    }
    if emit_labels and anomaly_type:
        event["anomalyType"] = anomaly_type
    return event


def to_json(event: Dict[str, Any]) -> str:
    return json.dumps(event, separators=(",", ":"))
