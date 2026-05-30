#!/usr/bin/env python3
"""
Launcher for ScenarioForge — run from anywhere inside the repo.
Automatically adds the risk-engine directory to sys.path.

Usage:
    python3 run_scenarioforge.py --interactive
    python3 run_scenarioforge.py --template mixed_demo --preview
    python3 run_scenarioforge.py --template flash_crash --eps 300 --duration 60
    python3 run_scenarioforge.py --config scenarioforge/config/scenarios/mixed-demo.json
"""
import sys
import os

# Ensure the risk-engine directory is on the path regardless of where you run from
HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

from scenarioforge.main import main
main()
