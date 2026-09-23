"""Phase 4A individualized contextual perturbation builder.

This is an inspectable data transformation only.  It does not import or run
HOTCO-CT, modify a Passport, or call any external service.
"""

from __future__ import annotations

from dataclasses import dataclass, field
import json
import math
from typing import Any, Mapping

from route_context import ContextStatus, NormalizedStressor, RouteContext


PERTURBATION_VERSION = "context-perturbation-v1"
SUPPORTED_TOLERANCES = ("rain", "crowding", "darkness", "traffic", "temperature", "wind")
IGNORED_CONTEXT_VARIABLES = ("air_pollution", "parking", "charging_availability")
MODE_ALIASES = {"car": "car_driver", "pt": "pt_bus_tram"}
KNOWN_MODES = (
    "walk", "bike", "bikeshare", "pt_bus_tram", "train", "car_driver",
    "car_passenger", "taxi", "ev", "hybrid", "carsharing", "escooter", "motorcycle",
)
NEED_TARGETS = {
    "rain": {"comfort": 1.0, "safety_accident": 1.0},
    "temperature": {"comfort": 1.0},
    "wind": {"comfort": 1.0},
    "darkness": {"safety_crime": 1.0, "safety_accident": 1.0},
    "traffic": {"speed": 1.0, "reliable": 1.0},
    "crowding": {"privacy": 1.0, "comfort": 1.0, "health_infection": 1.0},
}
VALENCE_TARGETS = {
    "rain": {"walk": -1.0, "bike": -1.0, "bikeshare": -1.0, "escooter": -1.0, "motorcycle": -1.0},
    "temperature": {"walk": -1.0, "bike": -1.0, "bikeshare": -1.0, "escooter": -1.0, "motorcycle": -1.0},
    "darkness": {"walk": -1.0, "bike": -1.0, "bikeshare": -1.0, "escooter": -1.0},
    "traffic": {"car_driver": -1.0, "car_passenger": -1.0, "taxi": -1.0, "ev": -1.0, "hybrid": -1.0, "carsharing": -1.0},
    "crowding": {"pt_bus_tram": -1.0, "train": -1.0},
}


def _finite(value: Any) -> float:
    try:
        result = float(value)
    except (TypeError, ValueError) as exc:
        raise ValueError("tolerance values must be numeric") from exc
    if not math.isfinite(result) or not 0.0 <= result <= 1.0:
        raise ValueError("tolerance values must be within [0, 1]")
    return result


@dataclass(frozen=True)
class ToleranceProfile:
    values: Mapping[str, float] = field(default_factory=dict)

    def __post_init__(self) -> None:
        normalized = {str(name): _finite(value) for name, value in self.values.items()}
        object.__setattr__(self, "values", normalized)

    @classmethod
    def from_mapping(cls, values: Mapping[str, Any]) -> "ToleranceProfile":
        return cls(values)

    def get(self, stressor: str) -> float | None:
        return self.values.get(stressor)

    def to_dict(self) -> dict[str, float]:
        return dict(self.values)


@dataclass(frozen=True)
class EffectiveExposure:
    stressor: str
    context_value: float | None
    tolerance: float | None
    effective_value: float | None
    source_scope: str
    mode: str | None
    status: ContextStatus
    contextual_coverage: float | None = None
    source_normalization_metadata: Mapping[str, Any] = field(default_factory=dict)
    formula: str = "context * (1 - tolerance)"
    warnings: tuple[str, ...] = ()

    def to_dict(self) -> dict[str, Any]:
        return {
            "stressor": self.stressor, "context_value": self.context_value,
            "tolerance": self.tolerance, "effective_value": self.effective_value,
            "source_scope": self.source_scope, "mode": self.mode,
            "status": self.status.value, "contextual_coverage": self.contextual_coverage,
            "source_normalization_metadata": dict(self.source_normalization_metadata),
            "formula": self.formula, "warnings": list(self.warnings),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "EffectiveExposure":
        return cls(
            str(data["stressor"]), data.get("context_value"), data.get("tolerance"), data.get("effective_value"),
            str(data["source_scope"]), data.get("mode"), ContextStatus(data["status"]), data.get("contextual_coverage"),
            data.get("source_normalization_metadata", {}), str(data.get("formula", "context * (1 - tolerance)")), tuple(data.get("warnings", ())),
        )


@dataclass(frozen=True)
class NodePerturbationContribution:
    stressor: str
    target_node: str
    target_family: str
    mode: str | None
    effective_exposure: float
    coefficient: float
    contribution: float
    source_scope: str
    source_normalization_metadata: Mapping[str, Any] = field(default_factory=dict)
    tolerance_source: str | None = None
    warnings: tuple[str, ...] = ()

    def to_dict(self) -> dict[str, Any]:
        return {
            "stressor": self.stressor, "target_node": self.target_node,
            "target_family": self.target_family, "mode": self.mode,
            "effective_exposure": self.effective_exposure, "coefficient": self.coefficient,
            "contribution": self.contribution, "source_scope": self.source_scope,
            "source_normalization_metadata": dict(self.source_normalization_metadata),
            "tolerance_source": self.tolerance_source, "warnings": list(self.warnings),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "NodePerturbationContribution":
        return cls(str(data["stressor"]), str(data["target_node"]), str(data["target_family"]), data.get("mode"), float(data["effective_exposure"]), float(data["coefficient"]), float(data["contribution"]), str(data["source_scope"]), data.get("source_normalization_metadata", {}), data.get("tolerance_source"), tuple(data.get("warnings", ())))


@dataclass(frozen=True)
class ContextPerturbationResult:
    route_id: str
    normalization_version: str
    perturbation_version: str
    effective_exposures: tuple[EffectiveExposure, ...]
    need_perturbations: Mapping[str, float]
    action_perturbations: Mapping[str, float]
    valence_perturbations: Mapping[str, float]
    contributions: tuple[NodePerturbationContribution, ...]
    missing_stressors: tuple[str, ...]
    ignored_context_variables: tuple[str, ...]
    warnings: tuple[str, ...]
    data_coverage: Mapping[str, Any]

    def to_dict(self) -> dict[str, Any]:
        return {
            "route_id": self.route_id, "normalization_version": self.normalization_version,
            "perturbation_version": self.perturbation_version,
            "effective_exposures": [item.to_dict() for item in self.effective_exposures],
            "need_perturbations": dict(self.need_perturbations), "action_perturbations": dict(self.action_perturbations),
            "valence_perturbations": dict(self.valence_perturbations),
            "contributions": [item.to_dict() for item in self.contributions],
            "missing_stressors": list(self.missing_stressors), "ignored_context_variables": list(self.ignored_context_variables),
            "warnings": list(self.warnings), "data_coverage": dict(self.data_coverage),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "ContextPerturbationResult":
        return cls(str(data["route_id"]), str(data["normalization_version"]), str(data["perturbation_version"]), tuple(EffectiveExposure.from_dict(x) for x in data["effective_exposures"]), data["need_perturbations"], data["action_perturbations"], data["valence_perturbations"], tuple(NodePerturbationContribution.from_dict(x) for x in data["contributions"]), tuple(data["missing_stressors"]), tuple(data["ignored_context_variables"]), tuple(data["warnings"]), data["data_coverage"])

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), sort_keys=True, separators=(",", ":"))

    @classmethod
    def from_json(cls, value: str) -> "ContextPerturbationResult":
        return cls.from_dict(json.loads(value))


class ContextPerturbationBuilder:
    def __init__(self, tolerance_profile: ToleranceProfile | Mapping[str, Any]) -> None:
        self.tolerances = tolerance_profile if isinstance(tolerance_profile, ToleranceProfile) else ToleranceProfile.from_mapping(tolerance_profile)

    def build(self, route: RouteContext) -> ContextPerturbationResult:
        route_values = {item.name: item for item in route.normalized_stressors}
        modes = {self._canonical_mode(mode) for mode in route.mode_specific_stressors}
        modes.update(self._canonical_mode(segment.mode) for segment in route.segments)
        modes.update(KNOWN_MODES)
        exposures: list[EffectiveExposure] = []
        contributions: list[NodePerturbationContribution] = []
        warnings: list[str] = []
        missing: list[str] = []
        need: dict[str, float] = {}
        valence: dict[str, float] = {f"valence_{mode}": 0.0 for mode in sorted(modes)}
        actions: dict[str, float] = {mode: 0.0 for mode in sorted(modes)}

        for stressor in SUPPORTED_TOLERANCES:
            context = route_values.get(stressor)
            exposure = self._exposure(stressor, context, self.tolerances.get(stressor), "route", None)
            exposures.append(exposure)
            if exposure.effective_value is None:
                missing.append(stressor); warnings.extend(exposure.warnings)
                continue
            for node, coefficient in NEED_TARGETS[stressor].items():
                amount = coefficient * exposure.effective_value; need[node] = need.get(node, 0.0) + amount
                contributions.append(self._contribution(stressor, node, "need", None, exposure, coefficient, amount, "route"))

        for ignored in IGNORED_CONTEXT_VARIABLES:
            if ignored in route_values or any(item.name == ignored for item in route.normalized_stressors):
                warnings.append(f"{ignored} is ignored: no measured individual tolerance dimension")

        for mode in sorted(modes):
            original_mode = next((name for name in route.mode_specific_stressors if self._canonical_mode(name) == mode), mode)
            mode_values = {item.name: item for item in route.mode_specific_stressors.get(original_mode, ())}
            for stressor, targets in VALENCE_TARGETS.items():
                context = mode_values.get(stressor)
                exposure = self._exposure(stressor, context, self.tolerances.get(stressor), "mode", mode)
                exposures.append(exposure)
                if exposure.effective_value is None:
                    warnings.append(f"mode-specific {stressor} exposure unavailable for {mode}; route exposure not used as fallback")
                    continue
                if stressor == "traffic" and mode == "pt_bus_tram" and context is None:
                    continue
                if mode not in targets:
                    continue
                node = f"valence_{mode}"; coefficient = targets[mode]; amount = coefficient * exposure.effective_value
                valence[node] = valence.get(node, 0.0) + amount
                contributions.append(self._contribution(stressor, node, "valence", mode, exposure, coefficient, amount, "mode"))

        normalization_version = ((route.normalized_stressors[0].normalization_metadata or {}).get("normalization_version", "unknown") if route.normalized_stressors else "unknown")
        return ContextPerturbationResult(route.route_id, normalization_version, PERTURBATION_VERSION, tuple(exposures), need, actions, valence, tuple(contributions), tuple(sorted(set(missing))), tuple(sorted(ignored for ignored in IGNORED_CONTEXT_VARIABLES if ignored in route_values)), tuple(dict.fromkeys(warnings)), {"route": {item.stressor: item.contextual_coverage for item in exposures if item.source_scope == "route"}, "mode": {item.mode: item.contextual_coverage for item in exposures if item.source_scope == "mode"}})

    @staticmethod
    def _canonical_mode(mode: str) -> str:
        return MODE_ALIASES.get(mode, mode)

    @staticmethod
    def _exposure(stressor: str, context: NormalizedStressor | None, tolerance: float | None, scope: str, mode: str | None) -> EffectiveExposure:
        context_value = context.value if context is not None and context.status != ContextStatus.UNKNOWN else None
        metadata = context.normalization_metadata if context is not None else {}
        coverage = (metadata or {}).get("coverage_fraction") if context is not None else None
        warnings: list[str] = []
        if context_value is None: warnings.append(f"{stressor} context is UNKNOWN")
        if tolerance is None: warnings.append(f"{stressor} tolerance is missing")
        effective = context_value * (1.0 - tolerance) if context_value is not None and tolerance is not None else None
        status = ContextStatus.DERIVED if effective is not None else ContextStatus.UNKNOWN
        return EffectiveExposure(stressor, context_value, tolerance, effective, scope, mode, status, coverage, metadata or {}, warnings=tuple(warnings))

    @staticmethod
    def _contribution(stressor: str, node: str, family: str, mode: str | None, exposure: EffectiveExposure, coefficient: float, amount: float, scope: str) -> NodePerturbationContribution:
        return NodePerturbationContribution(stressor, node, family, mode, exposure.effective_value or 0.0, coefficient, amount, scope, exposure.source_normalization_metadata, f"tolerance:{stressor}")
