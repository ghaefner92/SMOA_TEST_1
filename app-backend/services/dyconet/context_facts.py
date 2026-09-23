"""Canonical, presentation-safe current-condition facts for route context.

Facts are derived from serialized deterministic route context only.  They do
not normalize Orion observations or infer scientific eligibility; those remain
the responsibility of the context normalizer.
"""

from __future__ import annotations

from datetime import datetime, timedelta
from typing import Any, Mapping, Sequence


CURRENT_WINDOW = timedelta(minutes=30)
RECENT_WINDOW = timedelta(hours=6)
CELSIUS_UNITS = frozenset({"c", "°c", "celsius", "degree celsius", "cel"})


def _parse_time(value: Any) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        return None
    try:
        return datetime.fromisoformat(value.strip().replace("Z", "+00:00"))
    except ValueError:
        return None


def freshness(observed_at: Any, requested_at: Any) -> str:
    """Presentation-only freshness against the request's deterministic as-of time."""

    observed, requested = _parse_time(observed_at), _parse_time(requested_at)
    if observed is None or requested is None or (observed.tzinfo is None) != (requested.tzinfo is None):
        return "UNKNOWN"
    age = requested - observed
    if age < timedelta(0):
        return "UNKNOWN"
    if age <= CURRENT_WINDOW:
        return "CURRENT"
    if age <= RECENT_WINDOW:
        return "RECENT"
    return "STALE"


def _observation(observations: Sequence[Mapping[str, Any]], variable: str) -> Mapping[str, Any] | None:
    return next((item for item in observations if item.get("variable") == variable), None)


def _stressor(stressors: Sequence[Mapping[str, Any]], name: str) -> Mapping[str, Any] | None:
    return next((item for item in stressors if item.get("name") == name), None)


def build_context_facts(route_context: Mapping[str, Any]) -> list[dict[str, Any]]:
    """Build the single source of truth consumed by Android and narration."""

    observations = [item for item in route_context.get("raw_observations", ()) if isinstance(item, Mapping)]
    stressors = [item for item in route_context.get("normalized_stressors", ()) if isinstance(item, Mapping)]
    requested_at = route_context.get("requested_at")
    temperature = _observation(observations, "temperature")
    temperature_unit = str((temperature or {}).get("unit") or "").strip().lower()
    temperature_value = (temperature or {}).get("numeric_value")
    temperature_meta = (temperature or {}).get("source_metadata") or {}
    temperature_confidence = str(temperature_meta.get("source_contract_confidence", "UNKNOWN"))
    temperature_ok = temperature_value is not None and (temperature_unit in CELSIUS_UNITS or temperature_confidence == "CONFIRMED")
    # Keep an observed numeric value visible even when Orion omitted its unit.
    # It is deliberately not labelled Celsius and remains ineligible for
    # scientific normalization/model forcing until the source contract is
    # confirmed.
    if temperature_value is not None and not temperature_ok:
        temperature_display = f"{float(temperature_value):g} (unit unconfirmed)"
        temperature_status = "UNKNOWN"
    else:
        temperature_display = f"{float(temperature_value):g} °C" if temperature_ok else "No data"
        temperature_status = "AVAILABLE" if temperature_ok else "NO_DATA"
    facts = [{
        "variable": "temperature", "label": "Temperature",
        "display_value": temperature_display,
        "status": temperature_status,
        "freshness": freshness((temperature or {}).get("timestamp"), requested_at),
        "source_confidence": temperature_confidence if temperature_ok else "UNKNOWN",
        "observed_at": (temperature or {}).get("timestamp"),
        "displayable": True, "eligible_for_model": bool(temperature_ok and temperature_meta.get("normalization_eligible", False)),
    }]
    darkness = _stressor(stressors, "darkness")
    darkness_value = (darkness or {}).get("value")
    darkness_meta = (darkness or {}).get("normalization_metadata") or {}
    darkness_sources = darkness_meta.get("source_observations") or []
    darkness_timestamp = next((item.get("timestamp") for item in darkness_sources if isinstance(item, Mapping) and item.get("timestamp")), None)
    if darkness_value is not None:
        facts.append({
            "variable": "daylight", "label": "Light conditions",
            "display_value": "Daylight" if float(darkness_value) < 0.5 else "Low-light conditions",
            "status": "AVAILABLE", "freshness": freshness(darkness_timestamp, requested_at),
            "source_confidence": "STRONGLY_INFERRED", "observed_at": darkness_timestamp,
            "displayable": True, "eligible_for_model": True,
        })
    else:
        facts.append({"variable": "daylight", "label": "Light conditions", "display_value": "No data", "status": "NO_DATA", "freshness": "UNKNOWN", "source_confidence": "UNKNOWN", "displayable": True, "eligible_for_model": False})
    facts.append({"variable": "crowding", "label": "Crowding", "display_value": "No data", "status": "UNKNOWN", "freshness": "UNKNOWN", "source_confidence": "UNKNOWN", "displayable": True, "eligible_for_model": False})
    for variable, label, suffix in (("windSpeed", "Wind", "m/s"), ("rain", "Rain rate", "mm/h")):
        observation = _observation(observations, variable)
        value = (observation or {}).get("numeric_value")
        metadata = (observation or {}).get("source_metadata") or {}
        if value is not None and metadata.get("source_contract_confidence") == "CONFIRMED":
            facts.append({"variable": "wind" if variable == "windSpeed" else "rain", "label": label, "display_value": f"{float(value):g} {suffix}", "status": "AVAILABLE", "freshness": freshness((observation or {}).get("timestamp"), requested_at), "source_confidence": "CONFIRMED", "observed_at": (observation or {}).get("timestamp"), "displayable": True, "eligible_for_model": bool(metadata.get("normalization_eligible", False))})
        else:
            facts.append({"variable": "wind" if variable == "windSpeed" else "rain", "label": label, "display_value": "No data", "status": "NO_DATA", "freshness": "UNKNOWN", "source_confidence": "UNKNOWN", "displayable": True, "eligible_for_model": False})
    return facts
