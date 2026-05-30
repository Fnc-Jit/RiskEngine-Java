"""
Kafka Sink — publishes events to a Kafka topic using kafka-python.
"""
from __future__ import annotations
import json
import time
from typing import Dict

from kafka import KafkaProducer
from kafka.errors import KafkaError


class KafkaSink:
    def __init__(self, brokers: str, topic: str, retries: int = 5) -> None:
        self._topic = topic
        self._retries = retries
        self._producer = self._connect(brokers, retries)

    def _connect(self, brokers: str, retries: int) -> KafkaProducer:
        last_err = None
        for attempt in range(1, retries + 1):
            try:
                producer = KafkaProducer(
                    bootstrap_servers=brokers,
                    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                    key_serializer=lambda k: k.encode("utf-8") if k else None,
                    acks=1,
                    retries=3,
                    linger_ms=5,
                    batch_size=32768,
                )
                print(f"[KafkaSink] Connected to {brokers} (attempt {attempt})")
                return producer
            except KafkaError as e:
                last_err = e
                print(f"[KafkaSink] Connection attempt {attempt}/{retries} failed: {e}")
                time.sleep(2 ** attempt)
        raise RuntimeError(f"Could not connect to Kafka after {retries} attempts: {last_err}")

    def send(self, event: Dict) -> None:
        self._producer.send(
            self._topic,
            key=event.get("ticker"),
            value=event,
        )

    def flush(self) -> None:
        self._producer.flush()

    def close(self) -> None:
        self._producer.flush()
        self._producer.close()
        print("[KafkaSink] Producer closed.")
