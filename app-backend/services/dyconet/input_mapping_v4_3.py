"""Strict questionnaire-to-HOTCO-CT v4.3 input mapping.

This module has one non-negotiable policy: every model input must be backed by
an explicit response from the current user. It contains no population means,
completion model, neutral fallback, missing-value substitution, or behavioral
frequency proxy.
"""

from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Any, Dict, List, Mapping, Optional, Sequence

from availability_resolver import resolve_user_declared_availability


INPUT_SCHEMA_VERSION = "hotco_ct_input_2.1"
MODES: tuple[str, ...] = ("car", "bike", "pt", "walk")
NEEDS: tuple[str, ...] = (
    "pro_env",
    "physical",
    "privacy",
    "autonomy",
    "cost",
    "speed",
    "safety_accident",
    "safety_crime",
    "comfort",
    "reliable",
    "health_infection",
)
ENVIRONMENTAL_TOLERANCES: tuple[str, ...] = (
    "rain",
    "crowding",
    "darkness",
    "traffic",
    "temperature",
    "wind",
)
ENVIRONMENTAL_TOLERANCE_SCHEMA_VERSION = "environmental-tolerances-v1"

# Keys used by the Android questionnaire are retained as measurement-source
# labels and mapped deterministically to the canonical model axes.
SURVEY_NEED_TO_CANONICAL: Dict[str, str] = {
    "env": "pro_env",
    "health_activity": "physical",
    "crowding": "privacy",
    "flex": "autonomy",
    "cost": "cost",
    "time": "speed",
    "safety_accident": "safety_accident",
    "safety_crime": "safety_crime",
    "comfort_physical": "comfort",
    "reliable": "reliable",
    "health_infection": "health_infection",
}
CANONICAL_NEED_KEYS = {name: name for name in NEEDS}
ACCEPTED_NEED_KEYS = {**SURVEY_NEED_TO_CANONICAL, **CANONICAL_NEED_KEYS}


class InputValidationError(ValueError):
    """Raised when an input cannot be traced to a complete user response."""

    def __init__(self, issues: Sequence[str]):
        self.issues = list(issues)
        super().__init__("; ".join(self.issues))


@dataclass(frozen=True)
class ParticipantInput:
    agent_id: str
    raw_needs: Dict[str, int]
    raw_beliefs: Dict[str, Dict[str, int]]
    raw_valences: Dict[str, int]
    raw_availability: Dict[str, bool]
    availability_provenance: Dict[str, Any]
    raw_ranking: Optional[List[str]]
    needs: List[float]
    beliefs: List[List[float]]
    valences: List[float]
    availability: List[float]
    ranking: Optional[List[str]]
    environmental_tolerances: Dict[str, Optional[float]]


def _object(value: Any, path: str, issues: List[str]) -> Mapping[str, Any]:
    if not isinstance(value, Mapping):
        issues.append(f"{path} must be a JSON object")
        return {}
    return value


def _integer_rating(
    value: Any,
    *,
    path: str,
    minimum: int,
    maximum: int,
    issues: List[str],
) -> Optional[int]:
    if isinstance(value, bool):
        issues.append(f"{path} must be an integer from {minimum} to {maximum}")
        return None
    try:
        numeric = float(value)
    except (TypeError, ValueError):
        issues.append(f"{path} must be an integer from {minimum} to {maximum}")
        return None
    if not math.isfinite(numeric) or not numeric.is_integer():
        issues.append(f"{path} must be an integer from {minimum} to {maximum}")
        return None
    result = int(numeric)
    if result < minimum or result > maximum:
        issues.append(f"{path} must be between {minimum} and {maximum}")
        return None
    return result


def _environmental_tolerances(
    value: Any,
    *,
    present: bool,
    issues: List[str],
) -> Dict[str, Optional[float]]:
    """Validate measured tolerances without estimating absent dimensions."""

    if not present:
        return {name: None for name in ENVIRONMENTAL_TOLERANCES}
    source = _object(value, "responses.environmental_tolerances", issues)
    unknown = sorted(str(key) for key in source if key not in ENVIRONMENTAL_TOLERANCES)
    if unknown:
        issues.append(
            "responses.environmental_tolerances contains unknown dimensions: "
            + ", ".join(unknown)
        )
    result: Dict[str, Optional[float]] = {}
    for name in ENVIRONMENTAL_TOLERANCES:
        raw = source.get(name)
        if name not in source or raw is None:
            result[name] = None
            continue
        if isinstance(raw, bool):
            issues.append(
                f"responses.environmental_tolerances.{name} must be a number within [0, 1] or null"
            )
            result[name] = None
            continue
        try:
            numeric = float(raw)
        except (TypeError, ValueError):
            issues.append(
                f"responses.environmental_tolerances.{name} must be a number within [0, 1] or null"
            )
            result[name] = None
            continue
        if not math.isfinite(numeric) or not 0.0 <= numeric <= 1.0:
            issues.append(
                f"responses.environmental_tolerances.{name} must be within [0, 1]"
            )
            result[name] = None
            continue
        result[name] = numeric
    return result


def _canonical_need_responses(
    source: Mapping[str, Any],
    *,
    path: str,
    minimum: int,
    maximum: int,
    issues: List[str],
) -> tuple[Dict[str, int], Dict[str, str]]:
    canonical: Dict[str, int] = {}
    source_keys: Dict[str, str] = {}
    for raw_key, raw_value in source.items():
        model_key = ACCEPTED_NEED_KEYS.get(str(raw_key))
        if model_key is None:
            issues.append(f"{path}.{raw_key} is not a recognized HOTCO-CT need")
            continue
        if model_key in canonical:
            issues.append(
                f"{path} supplies both '{source_keys[model_key]}' and '{raw_key}' "
                f"for the same need '{model_key}'"
            )
            continue
        rating = _integer_rating(
            raw_value,
            path=f"{path}.{raw_key}",
            minimum=minimum,
            maximum=maximum,
            issues=issues,
        )
        if rating is not None:
            canonical[model_key] = rating
            source_keys[model_key] = str(raw_key)

    missing = [need for need in NEEDS if need not in canonical]
    if missing:
        issues.append(f"{path} is missing explicit responses for: {', '.join(missing)}")
    return canonical, source_keys


def _mode_object(
    source: Mapping[str, Any],
    *,
    path: str,
    issues: List[str],
) -> Dict[str, Any]:
    unknown = sorted(str(key) for key in source if str(key) not in MODES)
    if unknown:
        issues.append(f"{path} contains unknown modes: {', '.join(unknown)}")
    missing = [mode for mode in MODES if mode not in source]
    if missing:
        issues.append(f"{path} is missing explicit responses for: {', '.join(missing)}")
    return {mode: source[mode] for mode in MODES if mode in source}


def parse_participant_input(payload: Any) -> ParticipantInput:
    """Validate and transform one raw questionnaire payload.

    Transformations reproduce the manuscript's measurement bridge:
      needs:   (rating - 1) / 6
      beliefs: (rating - 4) / 3
      valence: rating / 3, where the raw scale is -3..+3
      availability: explicit user declaration -> binary HOTCO action gate

    Mode-use frequency is deliberately absent from schema 2.1 and cannot enter
    the HOTCO-CT state equation, parameterization, readout, or availability.
    Any missing or invalid required response rejects the entire request.
    """

    issues: List[str] = []
    root = _object(payload, "$", issues)
    allowed_root_keys = {"schema_version", "agent_id", "responses"}
    unknown_root_keys = sorted(str(key) for key in root if key not in allowed_root_keys)
    if unknown_root_keys:
        issues.append(f"$ contains unknown fields: {', '.join(unknown_root_keys)}")

    schema_version = root.get("schema_version")
    if schema_version != INPUT_SCHEMA_VERSION:
        issues.append(
            f"schema_version must be '{INPUT_SCHEMA_VERSION}' (received {schema_version!r})"
        )

    agent_id_value = root.get("agent_id")
    if not isinstance(agent_id_value, (str, int)) or not str(agent_id_value).strip():
        issues.append("agent_id must be a non-empty string or integer")
        agent_id = "invalid"
    else:
        agent_id = str(agent_id_value).strip()

    responses = _object(root.get("responses"), "responses", issues)
    allowed_response_keys = {
        "needs",
        "beliefs",
        "valences",
        "availability",
        "top_needs_ranking",
        "environmental_tolerances",
    }
    unknown_response_keys = sorted(
        str(key) for key in responses if key not in allowed_response_keys
    )
    if unknown_response_keys:
        issues.append(
            "responses contains unknown fields: " + ", ".join(unknown_response_keys)
        )

    needs_source = _object(responses.get("needs"), "responses.needs", issues)
    raw_needs, _ = _canonical_need_responses(
        needs_source,
        path="responses.needs",
        minimum=1,
        maximum=7,
        issues=issues,
    )

    beliefs_source = _object(responses.get("beliefs"), "responses.beliefs", issues)
    belief_modes = _mode_object(
        beliefs_source,
        path="responses.beliefs",
        issues=issues,
    )
    raw_beliefs: Dict[str, Dict[str, int]] = {}
    for mode in MODES:
        mode_source = _object(
            belief_modes.get(mode),
            f"responses.beliefs.{mode}",
            issues,
        )
        mode_values, _ = _canonical_need_responses(
            mode_source,
            path=f"responses.beliefs.{mode}",
            minimum=1,
            maximum=7,
            issues=issues,
        )
        raw_beliefs[mode] = mode_values

    valence_source = _object(responses.get("valences"), "responses.valences", issues)
    valence_modes = _mode_object(
        valence_source,
        path="responses.valences",
        issues=issues,
    )
    raw_valences: Dict[str, int] = {}
    for mode in MODES:
        if mode in valence_modes:
            rating = _integer_rating(
                valence_modes[mode],
                path=f"responses.valences.{mode}",
                minimum=-3,
                maximum=3,
                issues=issues,
            )
            if rating is not None:
                raw_valences[mode] = rating

    availability_source = _object(
        responses.get("availability"),
        "responses.availability",
        issues,
    )
    availability_modes = _mode_object(
        availability_source,
        path="responses.availability",
        issues=issues,
    )
    raw_availability: Dict[str, bool] = {}
    for mode in MODES:
        if mode not in availability_modes:
            continue
        value = availability_modes[mode]
        if not isinstance(value, bool):
            issues.append(f"responses.availability.{mode} must be true or false")
        else:
            raw_availability[mode] = value
    if raw_availability and not any(raw_availability.values()):
        issues.append("responses.availability must mark at least one mode as available")

    raw_ranking: Optional[List[str]] = None
    canonical_ranking: Optional[List[str]] = None
    if "top_needs_ranking" in responses:
        ranking_value = responses.get("top_needs_ranking")
        if not isinstance(ranking_value, list):
            issues.append("responses.top_needs_ranking must be an array of three need keys")
        else:
            raw_ranking = [str(item) for item in ranking_value]
            canonical_ranking = []
            for index, raw_key in enumerate(raw_ranking):
                mapped = ACCEPTED_NEED_KEYS.get(raw_key)
                if mapped is None:
                    issues.append(
                        f"responses.top_needs_ranking[{index}] is not a recognized need: {raw_key}"
                    )
                else:
                    canonical_ranking.append(mapped)
            if len(raw_ranking) != 3:
                issues.append("responses.top_needs_ranking must contain exactly three needs")
            if canonical_ranking and len(set(canonical_ranking)) != len(canonical_ranking):
                issues.append("responses.top_needs_ranking must not contain duplicates")

    environmental_tolerances = _environmental_tolerances(
        responses.get("environmental_tolerances"),
        present="environmental_tolerances" in responses,
        issues=issues,
    )

    if issues:
        raise InputValidationError(issues)

    resolved_availability = resolve_user_declared_availability(raw_availability, MODES)
    needs = [(raw_needs[need] - 1.0) / 6.0 for need in NEEDS]
    beliefs = [
        [(raw_beliefs[mode][need] - 4.0) / 3.0 for need in NEEDS]
        for mode in MODES
    ]
    valences = [raw_valences[mode] / 3.0 for mode in MODES]

    return ParticipantInput(
        agent_id=agent_id,
        raw_needs=raw_needs,
        raw_beliefs=raw_beliefs,
        raw_valences=raw_valences,
        raw_availability=resolved_availability.by_mode,
        availability_provenance=resolved_availability.provenance,
        raw_ranking=raw_ranking,
        needs=needs,
        beliefs=beliefs,
        valences=valences,
        availability=resolved_availability.vector,
        ranking=canonical_ranking,
        environmental_tolerances=environmental_tolerances,
    )
