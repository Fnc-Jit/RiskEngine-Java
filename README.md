# RiskEngine-Java

**Real-time financial risk scoring pipeline** built with Java 21, Spring Boot, Apache Kafka, ONNX Runtime, and TimescaleDB.

## Overview

RiskEngine-Java ingests high-volume synthetic market events from Kafka, transforms events into feature vectors, runs low-latency ONNX inference in-process, persists scored risk signals to TimescaleDB, and exposes results through Spring Boot REST endpoints and a live WebSocket dashboard.

## Architecture

```
Event Generator (Python) → Kafka → Spring Kafka Consumer → Feature Extractor
    → ONNX Scoring Service → Risk Signal Mapper → TimescaleDB
    → REST API / WebSocket Dashboard
```

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3 |
| Message Broker | Apache Kafka (KRaft) |
| Model Inference | ONNX Runtime 1.18.0 |
| Time-Series DB | TimescaleDB (PostgreSQL 16) |
| Latency Tracking | HdrHistogram |
| Event Generator | Python 3.12 |
| Containerization | Docker Compose |

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 21 (for local development)
- Python 3.12+ (for model training and event generation)

### 1. Train the ONNX Model

```bash
cd scripts
pip install scikit-learn skl2onnx numpy
python train_model.py
```

### 2. Start the Stack

```bash
docker compose up --build
```

This starts:
- **Kafka** (KRaft mode) on port 9094
- **TimescaleDB** on port 5432
- **RiskEngine** on port 8080
- **Event Generator** publishing 100 events/sec

### 3. Verify

```bash
# Health check
curl http://localhost:8080/health

# Latest signals
curl http://localhost:8080/signals/latest?limit=10

# Metrics
curl http://localhost:8080/metrics
```

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| GET | `/health` | App, DB, and ONNX model status |
| GET | `/signals/latest?limit=N` | Most recent N signals (default 100) |
| GET | `/signals?ticker=X&from=T1&to=T2` | Filtered signals by ticker and time range |
| GET | `/signals/{eventId}` | Lookup by event ID |
| GET | `/metrics` | Live metrics (counters, latency percentiles, throughput) |

## Project Structure

```
risk-engine/
├── src/main/java/com/jitraj/riskengine/
│   ├── config/          # Configuration classes
│   ├── consumer/        # Kafka consumer
│   ├── controller/      # REST API controllers
│   ├── dto/             # Data transfer objects
│   ├── metrics/         # Metrics service (HdrHistogram)
│   ├── model/           # Persistence entities & enums
│   ├── repository/      # TimescaleDB persistence
│   ├── scoring/         # ONNX inference service
│   ├── service/         # Business logic services
│   ├── util/            # Utility classes
│   └── benchmark/       # Benchmark harness
├── src/main/resources/
│   ├── application.yml  # Externalized configuration
│   └── db/              # SQL migrations
├── scripts/
│   ├── event_generator.py   # Synthetic event publisher
│   └── train_model.py       # ONNX model training
├── model/               # ONNX model and golden vectors
├── docker-compose.yml   # Full local stack
├── Dockerfile           # Multi-stage Java build
└── build.gradle         # Gradle build configuration
```

## Configuration

All tunable parameters are externalized in `application.yml`. Key settings:

| Property | Default | Description |
|---|---|---|
| `kafka.topic.market-events` | `market-events` | Kafka topic name |
| `feature.window.min-samples` | `10` | Cold-start threshold |
| `scoring.model.path` | `model/risk_model.onnx` | ONNX model file path |
| `risk.thresholds.elevated` | `50` | Elevated risk threshold |
| `risk.thresholds.critical` | `85` | Critical risk threshold |
| `repository.batch-size` | `100` | DB insert batch size |
| `metrics.window-seconds` | `60` | Throughput window |

## Testing

```bash
./gradlew test
```

Includes:
- MarketEvent JSON deserialization tests
- Feature extraction tests (cold start, determinism)
- Risk score normalization and label tests
- Scoring service mock tests
- Rolling window utility tests

## Benchmark Results

> Fill in after running benchmarks with `./gradlew benchmark`

| Throughput | p50 | p95 | p99 | Error Rate | Notes |
|---|---|---|---|---|---|
| 1K eps | - | - | - | - | baseline |
| 5K eps | - | - | - | - | stable |

See [hardware.md](docs/hardware.md) for benchmark hardware specifications.

## License

MIT
