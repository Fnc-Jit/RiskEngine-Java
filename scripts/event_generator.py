#!/usr/bin/env python3
"""
Synthetic Market Event Generator for RiskEngine.

Generates deterministic market events and publishes them to a Kafka topic.
Supports configurable rate, seed, and ticker universe.

Usage:
    python event_generator.py --rate 100 --seed 42 --bootstrap-servers localhost:9094
"""

import argparse
import json
import os
import random
import time
import uuid
from datetime import datetime, timezone

from kafka import KafkaProducer

# Default ticker universe
TICKERS = ["AAPL", "GOOG", "MSFT", "AMZN", "TSLA", "NVDA", "META", "JPM", "BAC", "GS"]

# Time buckets for market hours simulation
TIME_BUCKETS = ["PRE_MARKET", "OPEN", "MID_MORNING", "LUNCH", "AFTERNOON", "CLOSE", "AFTER_HOURS"]

# Base prices per ticker (approximate)
BASE_PRICES = {
    "AAPL": 210.0, "GOOG": 175.0, "MSFT": 420.0, "AMZN": 185.0, "TSLA": 260.0,
    "NVDA": 130.0, "META": 500.0, "JPM": 200.0, "BAC": 40.0, "GS": 450.0,
}


def generate_event(rng: random.Random, seq: int) -> dict:
    """Generate a single synthetic market event."""
    ticker = rng.choice(TICKERS)
    base_price = BASE_PRICES.get(ticker, 100.0)

    # Normal price variation (±5%)
    price = base_price * (1.0 + rng.gauss(0, 0.02))

    # Volume: base volume with some randomness
    volume = int(rng.gauss(150000, 50000))
    volume = max(100, volume)

    # Volatility: typically 0.01 to 0.05
    volatility = abs(rng.gauss(0.02, 0.01))

    # Bid-ask spread: typically 0.01 to 0.20
    bid_ask_spread = abs(rng.gauss(0.05, 0.03))

    # Time bucket based on sequence to simulate market hours
    time_bucket = TIME_BUCKETS[seq % len(TIME_BUCKETS)]

    # Anomaly injection: ~5% of events
    is_anomaly = rng.random() < 0.05
    if is_anomaly:
        # Inject anomalous features
        price *= rng.choice([0.85, 1.15, 0.90, 1.10])  # ±10-15% spike
        volume = int(volume * rng.uniform(3.0, 8.0))     # Volume surge
        volatility *= rng.uniform(2.0, 5.0)              # Volatility spike

    event = {
        "eventId": f"evt-{uuid.uuid4().hex[:12]}",
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "ticker": ticker,
        "price": round(price, 2),
        "volume": volume,
        "volatility": round(volatility, 6),
        "bidAskSpread": round(bid_ask_spread, 4),
        "timeBucket": time_bucket,
        "anomalyTag": is_anomaly,
    }
    return event


def main():
    parser = argparse.ArgumentParser(description="Synthetic Market Event Generator")
    parser.add_argument("--rate", type=int, default=int(os.getenv("EVENTS_PER_SECOND", "100")),
                        help="Events per second")
    parser.add_argument("--seed", type=int, default=int(os.getenv("SEED", "42")),
                        help="Random seed for deterministic generation")
    parser.add_argument("--bootstrap-servers", type=str,
                        default=os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9094"),
                        help="Kafka bootstrap servers")
    parser.add_argument("--topic", type=str,
                        default=os.getenv("KAFKA_TOPIC", "market-events"),
                        help="Kafka topic")
    parser.add_argument("--count", type=int, default=0,
                        help="Total events to generate (0 = infinite)")
    args = parser.parse_args()

    rng = random.Random(args.seed)
    print(f"Starting event generator — rate={args.rate}/s, seed={args.seed}, topic={args.topic}")
    print(f"Connecting to Kafka at {args.bootstrap_servers}...")

    producer = KafkaProducer(
        bootstrap_servers=args.bootstrap_servers,
        value_serializer=lambda v: json.dumps(v).encode("utf-8"),
        key_serializer=lambda k: k.encode("utf-8") if k else None,
    )

    interval = 1.0 / args.rate if args.rate > 0 else 0.01
    seq = 0
    total_sent = 0

    try:
        while True:
            batch_start = time.time()
            for _ in range(min(args.rate, 100)):  # Send in small batches
                event = generate_event(rng, seq)
                producer.send(args.topic, key=event["ticker"], value=event)
                seq += 1
                total_sent += 1

                if args.count > 0 and total_sent >= args.count:
                    producer.flush()
                    print(f"Completed: {total_sent} events sent.")
                    return

            producer.flush()
            elapsed = time.time() - batch_start
            sleep_time = max(0, (min(args.rate, 100) * interval) - elapsed)
            if sleep_time > 0:
                time.sleep(sleep_time)

            if total_sent % 1000 == 0:
                print(f"Sent {total_sent} events...")

    except KeyboardInterrupt:
        print(f"\nStopped. Total events sent: {total_sent}")
    finally:
        producer.close()


if __name__ == "__main__":
    main()
