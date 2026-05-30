# RiskEngine-Java

**Real-time financial risk scoring pipeline** built with Java 21, Spring Boot, Apache Kafka, ONNX Runtime, and TimescaleDB.

## Overview

RiskEngine-Java ingests high-volume synthetic market events from Kafka, transforms events into feature vectors, runs low-latency ONNX inference in-process, persists scored risk signals to TimescaleDB, and exposes results through Spring Boot REST endpoints and a live WebSocket dashboard.

## Architecture

```
ScenarioForge (Python) → Kafka → Spring Kafka Consumer → Feature Extractor
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
| Event Generator | ScenarioForge (Python 3.12) |
| Containerization | Docker Compose |

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 21 (for local development)
- Python 3.12+ (for model training and event generation)

## ScenarioForge — Configurable Event Generator

ScenarioForge is the configurable synthetic market event generator that feeds the pipeline. It models price, volume, spread, regime shifts, and labeled anomaly injection — all driven by config, with no code changes required.

### Run from anywhere

Use `run_scenarioforge.py` — it works from any directory, no `PYTHONPATH` needed:

```bash
# Interactive wizard
python3 risk-engine/run_scenarioforge.py --interactive

# Or if you're already inside risk-engine/
python3 run_scenarioforge.py --interactive
```

#### Interactive wizard (prompts you for every input)

```bash
python3 run_scenarioforge.py --interactive
# short form:
python3 run_scenarioforge.py -i
```

The wizard asks for: starting template, scenario name, seed, target EPS, stop mode (duration or max events), optional custom tickers, anomaly rate/severity/labels, and output mode (kafka/file/console). It prints a summary, asks for confirmation, then runs.

#### Built-in templates

```bash
# Preview events in the terminal (no Kafka needed)
python3 run_scenarioforge.py --template mixed_demo --preview

# Available templates:
# normal_day | volatile_open | flash_crash | low_liquidity | mixed_demo | benchmark_high_throughput
```

#### Custom config file

```bash
python3 run_scenarioforge.py --config scenarioforge/config/scenarios/mixed-demo.json --preview
python3 run_scenarioforge.py --config /path/to/your-config.json
```

#### Runtime overrides

```bash
# Override EPS, duration, seed
python3 run_scenarioforge.py --template mixed_demo --eps 500 --duration 30 --preview

# Write to a file instead of Kafka
python3 run_scenarioforge.py --template mixed_demo --mode file --output events.jsonl

# Publish to Kafka (use port 9094 from the host, 9092 inside Docker)
python3 run_scenarioforge.py --template benchmark_high_throughput --eps 1000 --brokers localhost:9094
```

| Flag | Description |
|---|---|
| `--interactive`, `-i` | Interactive wizard |
| `--template NAME` | Use a built-in template |
| `--config PATH` | Load a JSON scenario config |
| `--preview` | Console preview mode (shorthand for `--mode console`) |
| `--eps N` | Override target events per second |
| `--duration N` | Override run duration in seconds |
| `--seed N` | Override random seed |
| `--mode kafka\|file\|console` | Override output mode |
| `--brokers HOST:PORT` | Override Kafka brokers |
| `--topic NAME` | Override Kafka topic |
| `--output PATH` | Output file when `--mode file` |

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
- **Event Generator** (ScenarioForge `mixed_demo` template) publishing to Kafka

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
│   ├── event_generator.py   # Legacy synthetic event publisher
│   └── train_model.py       # ONNX model training
├── scenarioforge/           # Configurable event generator (ScenarioForge)
│   ├── main.py              # CLI entry point (--interactive, --template, --config)
│   ├── interactive.py       # Interactive wizard
│   ├── templates.py         # Built-in scenario templates
│   ├── config/              # Loader, validator, schema, sample scenarios
│   ├── engine/              # Price/volume/spread simulators, regimes, anomalies
│   └── output/              # Kafka / file / console sinks
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
