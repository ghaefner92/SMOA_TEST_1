"""Serializable route-context domain models.

This module deliberately stops at the data contract.  It does not normalize
observations, call context providers, invoke HOTCO-CT, or alter routing.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
import json
import math
from typing import Any, Mapping, Sequence


class ContextStatus(str, Enum):
    OBSERVED = "OBSERVED"
    DERIVED = "DERIVED"
    UNKNOWN = "UNKNOWN"


CANONICAL_STRESSORS = frozenset(
    {
        "rain",
        "temperature",
        "traffic",
        "crowding",
        "darkness",
        "air_pollution",
        "parking",
        "charging_availability",
        "wind",
    }
)


def _json_value(value: Any) -> Any:
    """Return a JSON-compatible value, rejecting opaque Python objects."""

    if value is None or isinstance(value, (str, bool, int, float)):
        if isinstance(value, float) and not math.isfinite(value):
            raise ValueError("context values must contain finite numbers")
        return value
    if isinstance(value, Mapping):
        return {str(key): _json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_value(item) for item in value]
    raise TypeError(f"value of type {type(value).__name__} is not JSON-compatible")


def _status(value: ContextStatus | str) -> ContextStatus:
    return value if isinstance(value, ContextStatus) else ContextStatus(value)


@dataclass(frozen=True)
class Coordinate:
    latitude: float
    longitude: float

    def __post_init__(self) -> None:
        if not -90.0 <= self.latitude <= 90.0:
            raise ValueError("latitude must be within [-90, 90]")
        if not -180.0 <= self.longitude <= 180.0:
            raise ValueError("longitude must be within [-180, 180]")

    def to_dict(self) -> dict[str, float]:
        return {"latitude": self.latitude, "longitude": self.longitude}

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "Coordinate":
        return cls(float(data["latitude"]), float(data["longitude"]))


@dataclass(frozen=True)
class ContextObservation:
    source: str
    variable: str
    raw_value: Any = None
    numeric_value: float | None = None
    unit: str | None = None
    timestamp: str | None = None
    status: ContextStatus = ContextStatus.UNKNOWN
    quality: float | None = None
    confidence: float | None = None
    source_entity_id: str | None = None
    spatial_metadata: Mapping[str, Any] | None = None
    source_metadata: Mapping[str, Any] | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "status", _status(self.status))
        if not self.source or not self.variable:
            raise ValueError("observation source and variable cannot be empty")
        if self.numeric_value is not None and not math.isfinite(self.numeric_value):
            raise ValueError("numeric_value must be finite")
        for name, value in (("quality", self.quality), ("confidence", self.confidence)):
            if value is not None and not 0.0 <= value <= 1.0:
                raise ValueError(f"{name} must be within [0, 1]")
        _json_value(self.raw_value)
        if self.spatial_metadata is not None:
            _json_value(self.spatial_metadata)
        if self.source_metadata is not None:
            _json_value(self.source_metadata)

    def to_dict(self) -> dict[str, Any]:
        return {
            "source": self.source,
            "variable": self.variable,
            "raw_value": _json_value(self.raw_value),
            "numeric_value": self.numeric_value,
            "unit": self.unit,
            "timestamp": self.timestamp,
            "status": self.status.value,
            "quality": self.quality,
            "confidence": self.confidence,
            "source_entity_id": self.source_entity_id,
            "spatial_metadata": _json_value(self.spatial_metadata),
            "source_metadata": _json_value(self.source_metadata),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "ContextObservation":
        return cls(
            source=str(data["source"]), variable=str(data["variable"]),
            raw_value=data.get("raw_value"),
            numeric_value=None if data.get("numeric_value") is None else float(data["numeric_value"]),
            unit=data.get("unit"), timestamp=data.get("timestamp"),
            status=_status(data.get("status", ContextStatus.UNKNOWN.value)),
            quality=data.get("quality"), confidence=data.get("confidence"),
            source_entity_id=data.get("source_entity_id"),
            spatial_metadata=data.get("spatial_metadata"),
            source_metadata=data.get("source_metadata"),
        )


@dataclass(frozen=True)
class NormalizedStressor:
    name: str
    value: float | None
    status: ContextStatus
    source_variables: tuple[str, ...] = ()
    normalization_metadata: Mapping[str, Any] | None = None
    confidence: float | None = None
    quality: float | None = None
    timestamp: str | None = None

    def __post_init__(self) -> None:
        object.__setattr__(self, "status", _status(self.status))
        if not self.name:
            raise ValueError("stressor name cannot be empty")
        if self.value is not None and not 0.0 <= self.value <= 1.0:
            raise ValueError("normalized stressor value must be within [0, 1]")
        if self.status == ContextStatus.UNKNOWN and self.value is not None:
            raise ValueError("UNKNOWN stressors must have value=None")
        if self.status in (ContextStatus.OBSERVED, ContextStatus.DERIVED) and self.value is None:
            raise ValueError("observed/derived stressors require a numeric value")
        for name, value in (("confidence", self.confidence), ("quality", self.quality)):
            if value is not None and not 0.0 <= value <= 1.0:
                raise ValueError(f"{name} must be within [0, 1]")
        if self.normalization_metadata is not None:
            _json_value(self.normalization_metadata)

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name, "value": self.value, "status": self.status.value,
            "source_variables": list(self.source_variables),
            "normalization_metadata": _json_value(self.normalization_metadata),
            "confidence": self.confidence, "quality": self.quality,
            "timestamp": self.timestamp,
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "NormalizedStressor":
        return cls(
            name=str(data["name"]), value=data.get("value"),
            status=_status(data["status"]),
            source_variables=tuple(data.get("source_variables", ())),
            normalization_metadata=data.get("normalization_metadata"),
            confidence=data.get("confidence"), quality=data.get("quality"),
            timestamp=data.get("timestamp"),
        )


def _nonnegative(value: float | None, name: str) -> None:
    if value is not None and (not math.isfinite(value) or value < 0.0):
        raise ValueError(f"{name} cannot be negative and must be finite")


@dataclass(frozen=True)
class RouteSegmentContext:
    segment_id: str
    mode: str
    start: Coordinate
    end: Coordinate
    duration_seconds: float | None = None
    distance_meters: float | None = None
    geometry: Any = None
    raw_observations: tuple[ContextObservation, ...] = ()
    normalized_stressors: tuple[NormalizedStressor, ...] = ()
    contextual_data_completeness: Mapping[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.segment_id or not self.mode:
            raise ValueError("segment_id and mode cannot be empty")
        _nonnegative(self.duration_seconds, "duration")
        _nonnegative(self.distance_meters, "distance")
        _json_value(self.geometry)
        _json_value(self.contextual_data_completeness)

    def to_dict(self) -> dict[str, Any]:
        return {
            "segment_id": self.segment_id, "mode": self.mode,
            "start": self.start.to_dict(), "end": self.end.to_dict(),
            "duration_seconds": self.duration_seconds, "distance_meters": self.distance_meters,
            "geometry": _json_value(self.geometry),
            "raw_observations": [item.to_dict() for item in self.raw_observations],
            "normalized_stressors": [item.to_dict() for item in self.normalized_stressors],
            "contextual_data_completeness": _json_value(self.contextual_data_completeness),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "RouteSegmentContext":
        return cls(
            segment_id=str(data["segment_id"]), mode=str(data["mode"]),
            start=Coordinate.from_dict(data["start"]), end=Coordinate.from_dict(data["end"]),
            duration_seconds=data.get("duration_seconds"), distance_meters=data.get("distance_meters"),
            geometry=data.get("geometry"),
            raw_observations=tuple(ContextObservation.from_dict(item) for item in data.get("raw_observations", ())),
            normalized_stressors=tuple(NormalizedStressor.from_dict(item) for item in data.get("normalized_stressors", ())),
            contextual_data_completeness=data.get("contextual_data_completeness", {}),
        )


@dataclass(frozen=True)
class RouteContext:
    route_id: str
    requested_at: str
    origin: Coordinate
    destination: Coordinate
    total_distance_meters: float | None = None
    total_duration_seconds: float | None = None
    segments: tuple[RouteSegmentContext, ...] = ()
    raw_observations: tuple[ContextObservation, ...] = ()
    normalized_stressors: tuple[NormalizedStressor, ...] = ()
    mode_specific_stressors: Mapping[str, tuple[NormalizedStressor, ...]] = field(default_factory=dict)
    contextual_data_completeness: Mapping[str, Any] = field(default_factory=dict)
    source_metadata: Mapping[str, Any] = field(default_factory=dict)
    context_facts: tuple[Mapping[str, Any], ...] = ()

    def __post_init__(self) -> None:
        if not self.route_id:
            raise ValueError("route_id cannot be empty")
        _nonnegative(self.total_distance_meters, "distance")
        _nonnegative(self.total_duration_seconds, "duration")
        for mode, stressors in self.mode_specific_stressors.items():
            if not mode:
                raise ValueError("mode names cannot be empty")
            if not isinstance(stressors, tuple):
                raise TypeError("mode-specific stressors must be tuples")
        _json_value(self.contextual_data_completeness)
        _json_value(self.source_metadata)

    def to_dict(self) -> dict[str, Any]:
        return {
            "route_id": self.route_id, "requested_at": self.requested_at,
            "origin": self.origin.to_dict(), "destination": self.destination.to_dict(),
            "total_distance_meters": self.total_distance_meters,
            "total_duration_seconds": self.total_duration_seconds,
            "segments": [item.to_dict() for item in self.segments],
            "raw_observations": [item.to_dict() for item in self.raw_observations],
            "normalized_stressors": [item.to_dict() for item in self.normalized_stressors],
            "context_facts": [_json_value(item) for item in self.context_facts],
            "mode_specific_stressors": {
                mode: [item.to_dict() for item in stressors]
                for mode, stressors in self.mode_specific_stressors.items()
            },
            "contextual_data_completeness": _json_value(self.contextual_data_completeness),
            "source_metadata": _json_value(self.source_metadata),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "RouteContext":
        return cls(
            route_id=str(data["route_id"]), requested_at=str(data["requested_at"]),
            origin=Coordinate.from_dict(data["origin"]), destination=Coordinate.from_dict(data["destination"]),
            total_distance_meters=data.get("total_distance_meters"), total_duration_seconds=data.get("total_duration_seconds"),
            segments=tuple(RouteSegmentContext.from_dict(item) for item in data.get("segments", ())),
            raw_observations=tuple(ContextObservation.from_dict(item) for item in data.get("raw_observations", ())),
            normalized_stressors=tuple(NormalizedStressor.from_dict(item) for item in data.get("normalized_stressors", ())),
            mode_specific_stressors={mode: tuple(NormalizedStressor.from_dict(item) for item in values) for mode, values in data.get("mode_specific_stressors", {}).items()},
            contextual_data_completeness=data.get("contextual_data_completeness", {}),
            source_metadata=data.get("source_metadata", {}),
            context_facts=tuple(data.get("context_facts", ())),
        )

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), sort_keys=True, separators=(",", ":"))

    @classmethod
    def from_json(cls, value: str) -> "RouteContext":
        return cls.from_dict(json.loads(value))


@dataclass(frozen=True)
class CandidateRouteInput:
    """Internal adapter DTO; independent from the external routing client."""

    route_id: str
    origin: Coordinate
    destination: Coordinate
    summary: Mapping[str, Any] = field(default_factory=dict)
    legs: tuple[Mapping[str, Any], ...] = ()
    geometry: Any = None
    requested_at: str | None = None
    source_metadata: Mapping[str, Any] = field(default_factory=dict)
    transport_modes: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if not self.route_id:
            raise ValueError("route_id cannot be empty")
        _json_value(self.summary); _json_value(self.legs); _json_value(self.geometry); _json_value(self.source_metadata)

    def to_dict(self) -> dict[str, Any]:
        return {
            "route_id": self.route_id,
            "origin": self.origin.to_dict(),
            "destination": self.destination.to_dict(),
            "summary": _json_value(self.summary),
            "legs": _json_value(self.legs),
            "geometry": _json_value(self.geometry),
            "requested_at": self.requested_at,
            "source_metadata": _json_value(self.source_metadata),
            "transport_modes": list(self.transport_modes),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "CandidateRouteInput":
        return cls(
            route_id=str(data["route_id"]),
            origin=Coordinate.from_dict(data["origin"]),
            destination=Coordinate.from_dict(data["destination"]),
            summary=data.get("summary", {}), legs=tuple(data.get("legs", ())),
            geometry=data.get("geometry"), requested_at=data.get("requested_at"),
            source_metadata=data.get("source_metadata", {}),
            transport_modes=tuple(data.get("transport_modes", ())),
        )

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), sort_keys=True, separators=(",", ":"))

    @classmethod
    def from_json(cls, value: str) -> "CandidateRouteInput":
        return cls.from_dict(json.loads(value))

    @classmethod
    def from_ranked_route(cls, route: Any, *, origin: Coordinate, destination: Coordinate, requested_at: str | None = None) -> "CandidateRouteInput":
        def get(name: str, default: Any = None) -> Any:
            return route.get(name, default) if isinstance(route, Mapping) else getattr(route, name, default)
        summary = get("summary") or {}
        legs = tuple(get("legs") or ())
        route_id = get("route_id") or get("id") or (f"rank-{get('rank')}" if get("rank") is not None else "")
        modes = tuple(
            str(leg.get("mode")) for leg in legs
            if isinstance(leg, Mapping) and leg.get("mode") is not None
        )
        return cls(route_id=str(route_id), origin=origin, destination=destination, summary=summary, legs=legs, geometry=get("geometry"), requested_at=requested_at, transport_modes=modes)


@dataclass(frozen=True)
class RouteContextBundle:
    search_id: str
    timestamp: str
    candidate_routes: tuple[RouteContext, ...] = ()
    contextual_data_version: str = "1.0"
    source_status: str = "UNKNOWN"
    warnings: tuple[str, ...] = ()

    def __post_init__(self) -> None:
        if not self.search_id:
            raise ValueError("search_id cannot be empty")

    def to_dict(self) -> dict[str, Any]:
        return {
            "search_id": self.search_id, "timestamp": self.timestamp,
            "candidate_routes": [route.to_dict() for route in self.candidate_routes],
            "contextual_data_version": self.contextual_data_version,
            "source_status": self.source_status, "warnings": list(self.warnings),
        }

    @classmethod
    def from_dict(cls, data: Mapping[str, Any]) -> "RouteContextBundle":
        return cls(
            search_id=str(data["search_id"]), timestamp=str(data["timestamp"]),
            candidate_routes=tuple(RouteContext.from_dict(item) for item in data.get("candidate_routes", ())),
            contextual_data_version=str(data.get("contextual_data_version", "1.0")),
            source_status=str(data.get("source_status", "UNKNOWN")), warnings=tuple(data.get("warnings", ())),
        )

    def to_json(self) -> str:
        return json.dumps(self.to_dict(), sort_keys=True, separators=(",", ":"))

    @classmethod
    def from_json(cls, value: str) -> "RouteContextBundle":
        return cls.from_dict(json.loads(value))
