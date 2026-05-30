"""
Interactive wizard for ScenarioForge.

Prompts the user for scenario parameters one at a time, with sensible defaults,
builds a config dict, validates it, and returns it ready to run.

Run with:  python -m scenarioforge.main --interactive
"""
from __future__ import annotations
from typing import Any, Dict, List

from .templates import TEMPLATES


# ─── Prompt helpers ─────────────────────────────────────────────────────────
def _ask(prompt: str, default: str) -> str:
    raw = input(f"{prompt} [{default}]: ").strip()
    return raw if raw else default


def _ask_int(prompt: str, default: int) -> int:
    while True:
        raw = _ask(prompt, str(default))
        try:
            return int(raw)
        except ValueError:
            print(f"  ! Please enter a whole number.")


def _ask_float(prompt: str, default: float) -> float:
    while True:
        raw = _ask(prompt, str(default))
        try:
            return float(raw)
        except ValueError:
            print(f"  ! Please enter a number.")


def _ask_choice(prompt: str, choices: List[str], default: str) -> str:
    choices_str = "/".join(choices)
    while True:
        raw = _ask(f"{prompt} ({choices_str})", default)
        if raw in choices:
            return raw
        print(f"  ! Please choose one of: {choices_str}")


def _ask_yes_no(prompt: str, default: bool) -> bool:
    d = "y" if default else "n"
    while True:
        raw = _ask(f"{prompt} (y/n)", d).lower()
        if raw in ("y", "yes"):
            return True
        if raw in ("n", "no"):
            return False
        print("  ! Please answer y or n.")


# ─── Wizard ─────────────────────────────────────────────────────────────────
def run_wizard() -> Dict[str, Any]:
    print("=" * 64)
    print("  ScenarioForge — Interactive Scenario Builder")
    print("=" * 64)
    print("Press Enter to accept the [default] shown in brackets.\n")

    # 1. Start from a template or from scratch
    print("Available templates:")
    template_names = list(TEMPLATES.keys())
    for i, name in enumerate(template_names, 1):
        print(f"  {i}. {name}")
    print("  0. start from scratch (single regime)")

    base: Dict[str, Any]
    choice = _ask("Start from which template number (or 0 for scratch)", "5")
    try:
        idx = int(choice)
    except ValueError:
        idx = 5

    if 1 <= idx <= len(template_names):
        import copy
        base = copy.deepcopy(TEMPLATES[template_names[idx - 1]])
        print(f"\nStarting from template: {template_names[idx - 1]}\n")
    else:
        base = _blank_scenario()
        print("\nStarting from scratch.\n")

    # 2. Core scenario params
    print("--- Core parameters ---")
    base["scenarioName"] = _ask("Scenario name", base.get("scenarioName", "my_scenario"))
    base["seed"]         = _ask_int("Random seed", base.get("seed", 42))
    base["targetEps"]    = _ask_float("Target events per second", base.get("targetEps", 200))

    stop_mode = _ask_choice("Stop after duration or max events?",
                            ["duration", "events"], "duration")
    if stop_mode == "duration":
        base["durationSec"] = _ask_float("Duration in seconds", base.get("durationSec") or 120)
        base["maxEvents"] = None
    else:
        base["maxEvents"] = _ask_int("Max events", base.get("maxEvents") or 1000)
        base["durationSec"] = None

    # 3. Tickers
    print("\n--- Market universe ---")
    if _ask_yes_no("Customize ticker universe?", False):
        base["tickers"] = _build_tickers(base.get("tickers", {}))
    else:
        print(f"Using {len(base['tickers'])} tickers: {', '.join(base['tickers'].keys())}")

    # 4. Anomalies
    print("\n--- Anomaly injection ---")
    ac = base.get("anomalyConfig", {})
    ac["baseRate"]    = _ask_float("Anomaly base rate (0-1)", ac.get("baseRate", 0.05))
    ac["severityMin"] = _ask_float("Anomaly severity min", ac.get("severityMin", 1.5))
    ac["severityMax"] = _ask_float("Anomaly severity max", ac.get("severityMax", 4.0))
    ac["emitLabels"]  = _ask_yes_no("Emit anomaly labels in output?", ac.get("emitLabels", True))
    base["anomalyConfig"] = ac

    # 5. Output
    print("\n--- Output ---")
    out = base.get("output", {})
    mode = _ask_choice("Output mode", ["kafka", "file", "console"], out.get("mode", "kafka"))
    out["mode"] = mode
    if mode == "kafka":
        out["brokers"] = _ask("Kafka brokers", out.get("brokers", "localhost:9094"))
        out["topic"]   = _ask("Kafka topic", out.get("topic", "market-events"))
    elif mode == "file":
        out["filePath"] = _ask("Output file path", out.get("filePath") or "events.jsonl")
    base["output"] = out

    # 6. Summary
    print("\n" + "=" * 64)
    print("  Scenario summary")
    print("=" * 64)
    print(f"  Name:        {base['scenarioName']}")
    print(f"  Seed:        {base['seed']}")
    print(f"  Target EPS:  {base['targetEps']}")
    if base.get("durationSec"):
        print(f"  Duration:    {base['durationSec']}s")
    else:
        print(f"  Max events:  {base['maxEvents']}")
    print(f"  Tickers:     {', '.join(base['tickers'].keys())}")
    print(f"  Regimes:     {' -> '.join(r['name'] for r in base['regimeSchedule'])}")
    print(f"  Anomaly rate:{base['anomalyConfig']['baseRate']}")
    print(f"  Output:      {base['output']['mode']}", end="")
    if base["output"]["mode"] == "kafka":
        print(f" ({base['output']['brokers']} / {base['output']['topic']})")
    elif base["output"]["mode"] == "file":
        print(f" ({base['output']['filePath']})")
    else:
        print()
    print("=" * 64)

    if not _ask_yes_no("\nRun this scenario now?", True):
        print("Aborted by user.")
        raise SystemExit(0)

    return base


def _build_tickers(existing: Dict[str, Any]) -> Dict[str, Any]:
    print("Enter tickers one at a time. Leave the symbol blank to finish.")
    tickers: Dict[str, Any] = {}
    while True:
        sym = input("  Ticker symbol (blank to stop): ").strip().upper()
        if not sym:
            break
        default = existing.get(sym, {"startPrice": 100.0, "baseVolatility": 0.015,
                                     "avgVolume": 100000, "avgSpread": 0.04})
        tickers[sym] = {
            "startPrice":     _ask_float(f"    {sym} start price", default["startPrice"]),
            "baseVolatility": _ask_float(f"    {sym} base volatility", default["baseVolatility"]),
            "avgVolume":      _ask_int(f"    {sym} avg volume", default["avgVolume"]),
            "avgSpread":      _ask_float(f"    {sym} avg spread", default["avgSpread"]),
        }
    if not tickers:
        print("  No tickers entered — falling back to AAPL default.")
        tickers["AAPL"] = {"startPrice": 210.0, "baseVolatility": 0.012,
                           "avgVolume": 120000, "avgSpread": 0.03}
    return tickers


def _blank_scenario() -> Dict[str, Any]:
    return {
        "scenarioName": "my_scenario",
        "seed": 42,
        "targetEps": 200,
        "durationSec": 120,
        "tickers": {
            "AAPL": {"startPrice": 210.0, "baseVolatility": 0.012, "avgVolume": 120000, "avgSpread": 0.03},
        },
        "regimeSchedule": [
            {"name": "normal", "durationSec": 120, "volMultiplier": 1.0,
             "volumeMultiplier": 1.0, "spreadMultiplier": 1.0,
             "anomalyMultiplier": 1.0, "directionalBias": 0.0},
        ],
        "anomalyConfig": {
            "baseRate": 0.05,
            "types": ["price_spike", "price_crash", "volume_spike", "spread_blowout", "combined_stress"],
            "severityMin": 1.5, "severityMax": 4.0, "emitLabels": True,
        },
        "output": {"mode": "kafka", "topic": "market-events", "brokers": "localhost:9092"},
    }
