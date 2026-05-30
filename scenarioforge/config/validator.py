"""
Config Validator — fails fast with clear messages on invalid scenario configs.
"""
from __future__ import annotations
from .schema import ScenarioConfig


class ConfigValidationError(ValueError):
    pass


def validate(cfg: ScenarioConfig) -> None:
    errors = []

    if cfg.target_eps <= 0:
        errors.append("targetEps must be positive")

    if cfg.duration_sec is None and cfg.max_events is None:
        errors.append("Either durationSec or maxEvents must be specified")

    if cfg.duration_sec is not None and cfg.duration_sec <= 0:
        errors.append("durationSec must be positive")

    if cfg.max_events is not None and cfg.max_events <= 0:
        errors.append("maxEvents must be positive")

    if not cfg.tickers:
        errors.append("tickers must be non-empty")

    for sym, t in cfg.tickers.items():
        if t.start_price <= 0:
            errors.append(f"tickers.{sym}.startPrice must be positive")
        if t.base_volatility <= 0:
            errors.append(f"tickers.{sym}.baseVolatility must be positive")
        if t.avg_volume <= 0:
            errors.append(f"tickers.{sym}.avgVolume must be positive")
        if t.avg_spread <= 0:
            errors.append(f"tickers.{sym}.avgSpread must be positive")

    if not cfg.regime_schedule:
        errors.append("regimeSchedule must be non-empty")

    for i, r in enumerate(cfg.regime_schedule):
        if r.duration_sec <= 0:
            errors.append(f"regimeSchedule[{i}].durationSec must be positive")
        if r.vol_multiplier <= 0:
            errors.append(f"regimeSchedule[{i}].volMultiplier must be positive")
        if not (-1.0 <= r.directional_bias <= 1.0):
            errors.append(f"regimeSchedule[{i}].directionalBias must be in [-1, 1]")

    ac = cfg.anomaly_config
    if not (0.0 <= ac.base_rate <= 1.0):
        errors.append("anomalyConfig.baseRate must be in [0, 1]")
    if ac.severity_min <= 0:
        errors.append("anomalyConfig.severityMin must be positive")
    if ac.severity_max < ac.severity_min:
        errors.append("anomalyConfig.severityMax must be >= severityMin")

    out = cfg.output
    if out.mode not in ("kafka", "file", "console"):
        errors.append(f"output.mode must be kafka|file|console, got: {out.mode!r}")
    if out.mode == "kafka" and not out.brokers:
        errors.append("output.brokers must be set when mode is kafka")
    if out.mode == "file" and not out.file_path:
        errors.append("output.filePath must be set when mode is file")

    if errors:
        raise ConfigValidationError(
            "Scenario config validation failed:\n" +
            "\n".join(f"  - {e}" for e in errors)
        )
