"""
ScenarioForge — main entry point.

Usage:
    python -m scenarioforge.main --interactive
    python -m scenarioforge.main --config config/mixed-demo.json
    python -m scenarioforge.main --template flash_crash
    python -m scenarioforge.main --template benchmark_high_throughput --eps 5000 --duration 60
    python -m scenarioforge.main --config config/normal-day.json --preview
    python -m scenarioforge.main --config config/normal-day.json --mode file --output /tmp/events.jsonl
"""
from __future__ import annotations
import argparse
import sys
import time
import webbrowser
from pathlib import Path

from .config.loader import load_file, load_dict
from .config.validator import ConfigValidationError
from .engine.generator import run
from .templates import TEMPLATES


def build_sink(cfg):
    mode = cfg.output.mode
    if mode == "kafka":
        from .output.kafka_sink import KafkaSink
        return KafkaSink(brokers=cfg.output.brokers, topic=cfg.output.topic)
    elif mode == "file":
        from .output.file_sink import FileSink
        return FileSink(path=cfg.output.file_path)
    elif mode == "console":
        from .output.console_sink import ConsoleSink
        return ConsoleSink(max_events=50)
    else:
        raise ValueError(f"Unknown output mode: {mode!r}")


def main(argv=None):
    parser = argparse.ArgumentParser(
        prog="scenarioforge",
        description="ScenarioForge — Configurable Synthetic Market Event Generator",
    )
    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--config", metavar="PATH",
                       help="Path to a JSON scenario config file")
    group.add_argument("--template", metavar="NAME",
                       choices=list(TEMPLATES.keys()),
                       help=f"Built-in template: {', '.join(TEMPLATES.keys())}")
    group.add_argument("--interactive", "-i", action="store_true",
                       help="Interactive wizard: prompts for inputs and runs the scenario")

    parser.add_argument("--eps",      type=float, help="Override targetEps")
    parser.add_argument("--duration", type=float, help="Override durationSec")
    parser.add_argument("--seed",     type=int,   help="Override seed")
    parser.add_argument("--mode",     choices=["kafka", "file", "console"],
                        help="Override output mode")
    parser.add_argument("--brokers",  help="Override Kafka brokers (e.g. localhost:9092)")
    parser.add_argument("--topic",    help="Override Kafka topic")
    parser.add_argument("--output",   help="File path when --mode file")
    parser.add_argument("--preview",  action="store_true",
                        help="Shorthand for --mode console")
    parser.add_argument("--no-dashboard", action="store_true",
                        help="Do not launch the live dashboard server")
    parser.add_argument("--dashboard-port", type=int, default=8050,
                        help="Port for the live dashboard server (default 8050)")
    parser.add_argument("--backend", default="http://localhost:8080",
                        help="RiskEngine Java backend URL (default http://localhost:8080)")

    args = parser.parse_args(argv)

    # Load base config
    try:
        if args.interactive:
            from .interactive import run_wizard
            cfg = load_dict(run_wizard())
        elif args.config:
            cfg = load_file(args.config)
        else:
            cfg = load_dict(TEMPLATES[args.template])
    except (FileNotFoundError, KeyError, ConfigValidationError) as e:
        print(f"[ERROR] {e}", file=sys.stderr)
        sys.exit(1)

    # Apply CLI overrides (mutate output config directly)
    if args.eps:
        cfg.target_eps = args.eps
    if args.duration:
        cfg.duration_sec = args.duration
    if args.seed is not None:
        cfg.seed = args.seed
    if args.preview:
        cfg.output.mode = "console"
    if args.mode:
        cfg.output.mode = args.mode
    if args.brokers:
        cfg.output.brokers = args.brokers
    if args.topic:
        cfg.output.topic = args.topic
    if args.output:
        cfg.output.file_path = args.output

    print(f"[ScenarioForge] Starting scenario: {cfg.scenario_name!r}")
    stop = f"duration={cfg.duration_sec}s" if cfg.duration_sec else f"maxEvents={cfg.max_events}"
    print(f"  seed={cfg.seed}  eps={cfg.target_eps}  {stop}  mode={cfg.output.mode}")
    if cfg.output.mode == "kafka":
        print(f"  brokers={cfg.output.brokers}  topic={cfg.output.topic}")

    # Launch the live dashboard server (serves dashboard + proxies the Java API,
    # avoiding the file:// CORS problem). Skipped for console/preview or --no-dashboard.
    dashboard_url = None
    if not args.no_dashboard and cfg.output.mode != "console":
        try:
            from .dashboard_server import start_dashboard_server
            dashboard_url = start_dashboard_server(port=args.dashboard_port, backend=args.backend)
            print("\n" + "=" * 60)
            print(f"  📊 Dashboard is LIVE at:  {dashboard_url}")
            print(f"  🔌 Proxying backend:      {args.backend}")
            print("=" * 60 + "\n")
            try:
                webbrowser.open(dashboard_url)
            except Exception:
                pass
        except Exception as e:
            print(f"[ScenarioForge] Dashboard server could not start: {e}")

    sink = build_sink(cfg)
    try:
        run(cfg, sink.send)
    finally:
        sink.flush()
        sink.close()

    # Keep the dashboard alive after the test so the user can inspect results.
    if dashboard_url:
        print("\n" + "=" * 60)
        print(f"  ✅ Test complete. Dashboard still live at: {dashboard_url}")
        print("  Press Ctrl+C to stop the dashboard server and exit.")
        print("=" * 60)
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            print("\n[ScenarioForge] Dashboard stopped. Bye.")


if __name__ == "__main__":
    main()
