"""Deterministic Phase 3 normalization and route-context enrichment.

This module consumes Phase 1 observations and produces inspectable stressor
values. It does not call Orion, routing, or HOTCO-CT and does not read a
Cognitive Passport.
"""

from __future__ import annotations

from dataclasses import dataclass, replace
from datetime import datetime, timezone
import math
from typing import Any, Iterable, Mapping, Sequence
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

from route_context import (
    ContextObservation,
    ContextStatus,
    Coordinate,
    NormalizedStressor,
    RouteContext,
    RouteSegmentContext,
    CandidateRouteInput,
)
from context_facts import build_context_facts


NORMALIZATION_VERSION = "context-normalization-v1"


@dataclass(frozen=True)
class NormalizationConfig:
    rain_min_mm: float = 0.0
    rain_max_mm: float = 10.0
    comfort_min_c: float = 10.0
    comfort_max_c: float = 25.0
    cold_extreme_c: float = -10.0
    hot_extreme_c: float = 40.0
    wind_low_mps: float = 3.0
    wind_high_mps: float = 12.0
    light_dark_lux: float = 10.0
    light_day_lux: float = 1000.0
    timezone_name: str = "Europe/Berlin"
    air_pollution_thresholds: Mapping[str, float] = None  # type: ignore[assignment]

    def __post_init__(self) -> None:
        if self.rain_max_mm <= self.rain_min_mm:
            raise ValueError("rain bounds must be increasing")
        if not self.cold_extreme_c < self.comfort_min_c < self.comfort_max_c < self.hot_extreme_c:
            raise ValueError("temperature bounds must be ordered")
        if not self.wind_low_mps < self.wind_high_mps:
            raise ValueError("wind bounds must be ordered")
        if not 0.0 <= self.light_dark_lux < self.light_day_lux:
            raise ValueError("light thresholds must be ordered")
        try:
            ZoneInfo(self.timezone_name)
        except ZoneInfoNotFoundError as exc:
            raise ValueError("timezone_name must identify an installed IANA timezone") from exc
        if self.air_pollution_thresholds is None:
            object.__setattr__(self, "air_pollution_thresholds", {
                "pm25": 25.0, "pm10": 50.0, "no2": 100.0, "o3": 120.0,
            })


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, value))


def _numeric(observation: ContextObservation | None) -> float | None:
    if observation is None or observation.numeric_value is None:
        return None
    return observation.numeric_value if math.isfinite(observation.numeric_value) else None


def _eligible(observation: ContextObservation | None) -> bool:
    """Raw public Orion data is not scientific forcing without its contract."""

    if observation is None:
        return False
    metadata = observation.source_metadata or {}
    # Fixtures and explicitly supplied observations predate source metadata;
    # only a provider's explicit ineligible verdict blocks normalization.
    return observation.source != "orion" or bool(metadata.get("normalization_eligible", True))


def _first(observations: Sequence[ContextObservation], variable: str) -> ContextObservation | None:
    return next((item for item in observations if item.variable == variable), None)


def _metadata(method: str, parameters: Mapping[str, Any], observations: Sequence[ContextObservation], **extra: Any) -> dict[str, Any]:
    return {
        "normalization_version": NORMALIZATION_VERSION,
        "method": method,
        "parameters": dict(parameters),
        "source_observations": [
            {"entity_id": item.source_entity_id, "variable": item.variable, "timestamp": item.timestamp}
            for item in observations
        ],
        **extra,
    }


def _unknown(name: str, variables: Sequence[str], observations: Sequence[ContextObservation], method: str = "unavailable") -> NormalizedStressor:
    return NormalizedStressor(name, None, ContextStatus.UNKNOWN, tuple(variables), _metadata(method, {}, observations))


def _derived(name: str, value: float, variables: Sequence[str], observations: Sequence[ContextObservation], method: str, parameters: Mapping[str, Any], **extra: Any) -> NormalizedStressor:
    return NormalizedStressor(name, _clamp(value), ContextStatus.DERIVED, tuple(variables), _metadata(method, parameters, observations, **extra))


def normalize_rain(observations: Sequence[ContextObservation], config: NormalizationConfig = NormalizationConfig()) -> NormalizedStressor:
    item = _first(observations, "rain")
    if item is None:
        return _unknown("rain", ("rain",), observations)
    if not _eligible(item):
        return _unknown("rain", ("rain",), (item,), "source_contract_not_eligible")
    value = _numeric(item)
    if value is not None:
        return _derived("rain", (value - config.rain_min_mm) / (config.rain_max_mm - config.rain_min_mm), ("rain",), (item,), "linear_clamp", {"min_mm_per_hour": config.rain_min_mm, "max_mm_per_hour": config.rain_max_mm, "temporal_semantics": "instantaneous_rate"})
    raw = str(item.raw_value).strip().lower()
    mapping = {"true": 1.0, "yes": 1.0, "rain": 1.0, "raining": 1.0, "false": 0.0, "no": 0.0, "none": 0.0, "dry": 0.0}
    if raw in mapping:
        return _derived("rain", mapping[raw], ("rain",), (item,), "explicit_category_mapping", {"mapping": mapping})
    return _unknown("rain", ("rain",), (item,))


def normalize_temperature(observations: Sequence[ContextObservation], config: NormalizationConfig = NormalizationConfig()) -> NormalizedStressor:
    item = _first(observations, "temperature"); value = _numeric(item)
    if value is None or not _eligible(item): return _unknown("temperature", ("temperature",), observations, "source_contract_not_eligible")
    if config.comfort_min_c <= value <= config.comfort_max_c: stress = 0.0
    elif value < config.comfort_min_c: stress = (config.comfort_min_c - value) / (config.comfort_min_c - config.cold_extreme_c)
    else: stress = (value - config.comfort_max_c) / (config.hot_extreme_c - config.comfort_max_c)
    return _derived("temperature", stress, ("temperature",), (item,), "piecewise_thermal_discomfort", {"comfort_min_c": config.comfort_min_c, "comfort_max_c": config.comfort_max_c, "cold_extreme_c": config.cold_extreme_c, "hot_extreme_c": config.hot_extreme_c})


def normalize_wind(observations: Sequence[ContextObservation], config: NormalizationConfig = NormalizationConfig()) -> NormalizedStressor:
    item = _first(observations, "windSpeed"); value = _numeric(item)
    if value is None or not _eligible(item): return _unknown("wind", ("windSpeed",), observations, "source_contract_not_eligible")
    return _derived("wind", (value - config.wind_low_mps) / (config.wind_high_mps - config.wind_low_mps), ("windSpeed",), (item,), "linear_clamp", {"low_mps": config.wind_low_mps, "high_mps": config.wind_high_mps})


def _solar_elevation_degrees(timestamp: datetime, latitude: float, longitude: float) -> float:
    utc_value = timestamp.astimezone(timezone.utc)
    hour = utc_value.hour + utc_value.minute / 60.0 + utc_value.second / 3600.0
    gamma = 2.0 * math.pi / 365.0 * (utc_value.timetuple().tm_yday - 1 + (hour - 12.0) / 24.0)
    equation_of_time = 229.18 * (0.000075 + 0.001868 * math.cos(gamma) - 0.032077 * math.sin(gamma) - 0.014615 * math.cos(2.0 * gamma) - 0.040849 * math.sin(2.0 * gamma))
    declination = 0.006918 - 0.399912 * math.cos(gamma) + 0.070257 * math.sin(gamma) - 0.006758 * math.cos(2.0 * gamma) + 0.000907 * math.sin(2.0 * gamma) - 0.002697 * math.cos(3.0 * gamma) + 0.00148 * math.sin(3.0 * gamma)
    true_solar_minutes = (hour * 60.0 + equation_of_time + 4.0 * longitude) % 1440.0
    hour_angle = math.radians(true_solar_minutes / 4.0 - 180.0)
    latitude_radians = math.radians(latitude)
    cosine_zenith = max(-1.0, min(1.0, math.sin(latitude_radians) * math.sin(declination) + math.cos(latitude_radians) * math.cos(declination) * math.cos(hour_angle)))
    return 90.0 - math.degrees(math.acos(cosine_zenith))


def normalize_darkness(observations: Sequence[ContextObservation], config: NormalizationConfig = NormalizationConfig(), *, coordinate: Coordinate | None = None) -> NormalizedStressor:
    item = _first(observations, "darkness")
    if item is not None and _numeric(item) is not None:
        return _derived("darkness", _numeric(item) or 0.0, ("darkness",), (item,), "explicit_value", {})
    timestamp = _first(observations, "datetime")
    if timestamp is None or not timestamp.raw_value or coordinate is None:
        return _unknown("darkness", ("datetime", "route_coordinate"), observations, "timestamp_or_route_coordinate_unavailable")
    try:
        parsed = datetime.fromisoformat(str(timestamp.raw_value).replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            parsed = parsed.replace(tzinfo=ZoneInfo(config.timezone_name))
        elevation = _solar_elevation_degrees(parsed, coordinate.latitude, coordinate.longitude)
    except (TypeError, ValueError, ZoneInfoNotFoundError):
        return _unknown("darkness", ("datetime", "route_coordinate"), (timestamp,), "timestamp_or_timezone_unparseable")
    solar_value = 0.0 if elevation >= 0.0 else _clamp(-elevation / 6.0)
    light = _first(observations, "lightIntensity")
    light_value = _numeric(light)
    sources = (timestamp, light) if light is not None else (timestamp,)
    parameters = {"timezone": config.timezone_name, "latitude": coordinate.latitude, "longitude": coordinate.longitude, "solar_elevation_degrees": elevation, "darkness_at_or_below_degrees": -6.0}
    if light_value is None:
        return _derived("darkness", solar_value, ("datetime", "route_coordinate"), sources, "solar_elevation_civil_twilight", parameters)
    sensor_value = _clamp((config.light_day_lux - light_value) / (config.light_day_lux - config.light_dark_lux))
    value = (solar_value + sensor_value) / 2.0
    parameters.update({"method_secondary": "light_intensity_cross_check", "light_intensity_value": light_value, "light_dark_lux": config.light_dark_lux, "light_day_lux": config.light_day_lux, "solar_darkness": solar_value, "sensor_darkness": sensor_value})
    return _derived("darkness", value, ("datetime", "route_coordinate", "lightIntensity"), sources, "solar_elevation_light_cross_check", parameters)


def normalize_traffic(observations: Sequence[ContextObservation]) -> NormalizedStressor:
    speed = _numeric(_first(observations, "avgSpeed")); limit = _numeric(_first(observations, "speedLimit"))
    source = tuple(item for item in (_first(observations, "avgSpeed"), _first(observations, "speedLimit")) if item is not None)
    if speed is None or limit is None or limit <= 0 or not _eligible(_first(observations, "avgSpeed")) or not _eligible(_first(observations, "speedLimit")):
        return _unknown("traffic", ("avgSpeed", "speedLimit"), source, "missing_or_invalid_speed_pair")
    return _derived("traffic", 1.0 - speed / limit, ("avgSpeed", "speedLimit"), source, "inverse_speed_ratio", {"formula": "clamp(1 - avgSpeed / speedLimit, 0, 1)"})


def normalize_parking(observations: Sequence[ContextObservation]) -> NormalizedStressor:
    free = _numeric(_first(observations, "freeSpots")); total = _numeric(_first(observations, "totalSpots"))
    source = tuple(item for item in (_first(observations, "freeSpots"), _first(observations, "totalSpots")) if item is not None)
    if free is None or total is None or total <= 0:
        return _unknown("parking", ("freeSpots", "totalSpots"), source, "missing_or_invalid_capacity")
    return _derived("parking", 1.0 - free / total, ("freeSpots", "totalSpots"), source, "inverse_free_fraction", {"formula": "clamp(1 - freeSpots / totalSpots, 0, 1)"})


def normalize_charging(observations: Sequence[ContextObservation]) -> NormalizedStressor:
    available = _numeric(_first(observations, "availableCapacity")); capacity = _numeric(_first(observations, "capacity"))
    source = tuple(item for item in (_first(observations, "availableCapacity"), _first(observations, "capacity")) if item is not None)
    if available is None or capacity is None or capacity <= 0:
        return _unknown("charging_availability", ("availableCapacity", "capacity"), source, "missing_or_invalid_capacity")
    return _derived("charging_availability", available / capacity, ("availableCapacity", "capacity"), source, "available_capacity_fraction", {"formula": "clamp(availableCapacity / capacity, 0, 1)", "semantic": "availability_not_stress"})


def normalize_air_pollution(observations: Sequence[ContextObservation], config: NormalizationConfig = NormalizationConfig()) -> NormalizedStressor:
    available: list[tuple[str, float, ContextObservation]] = []
    for name, threshold in config.air_pollution_thresholds.items():
        item = _first(observations, name); value = _numeric(item)
        if item is not None and value is not None and threshold > 0: available.append((name, value, item))
    if not available: return _unknown("air_pollution", tuple(config.air_pollution_thresholds), observations, "all_pollutants_unavailable")
    values = {name: _clamp(value / config.air_pollution_thresholds[name]) for name, value, _ in available}
    return _derived("air_pollution", max(values.values()), tuple(values), tuple(item for _, _, item in available), "engineering_threshold_max", {"thresholds": dict(config.air_pollution_thresholds), "aggregation": "max_available_pollutant", "not_a_health_risk_model": True}, pollutant_values=values)


def normalize_crowding(observations: Sequence[ContextObservation]) -> NormalizedStressor:
    item = _first(observations, "crowding")
    if item is None or _numeric(item) is None: return _unknown("crowding", ("crowding",), (item,) if item else (), "explicit_crowding_only")
    return _derived("crowding", _numeric(item) or 0.0, ("crowding",), (item,), "explicit_value", {})


def normalize_observations(observations: Sequence[ContextObservation], config: NormalizationConfig = NormalizationConfig(), *, coordinate: Coordinate | None = None) -> tuple[NormalizedStressor, ...]:
    return (normalize_rain(observations, config), normalize_temperature(observations, config), normalize_wind(observations, config), normalize_darkness(observations, config, coordinate=coordinate), normalize_traffic(observations), normalize_parking(observations), normalize_charging(observations), normalize_air_pollution(observations, config), normalize_crowding(observations))


def _weighted(stressors: Sequence[tuple[float, NormalizedStressor]], name: str) -> tuple[NormalizedStressor, float]:
    known = [(duration, item) for duration, item in stressors if item.name == name and item.value is not None and duration > 0]
    if not known: return NormalizedStressor(name, None, ContextStatus.UNKNOWN, (name,), _metadata("duration_weighted", {}, ())), 0.0
    total = sum(duration for duration, _ in known); value = sum(duration * (item.value or 0.0) for duration, item in known) / total
    sources = tuple(variable for _, item in known for variable in item.source_variables)
    source_observations = [
        reference
        for _, item in known
        for reference in (item.normalization_metadata or {}).get("source_observations", [])
    ]
    result = NormalizedStressor(name, value, ContextStatus.DERIVED, sources, {"normalization_version": NORMALIZATION_VERSION, "method": "duration_weighted_mean", "coverage": total, "known_duration_seconds": total, "source_observations": source_observations})
    return result, total


class RouteContextAdapter:
    def __init__(self, *, config: NormalizationConfig = NormalizationConfig(), search_radius_meters: float = 500.0) -> None:
        if search_radius_meters <= 0: raise ValueError("search radius must be positive")
        self.config = config; self.search_radius_meters = search_radius_meters

    def enrich(self, candidate: CandidateRouteInput, observations: Sequence[ContextObservation] = (), *, segment_observations: Mapping[str, Sequence[ContextObservation]] | None = None, query_metadata: Mapping[str, Any] | None = None) -> RouteContext:
        segment_observations = segment_observations or {}
        segments: list[RouteSegmentContext] = []
        sampled_geometry = sample_route_geometry(candidate.geometry)
        geometry_provenance = str(candidate.source_metadata.get("geometry_provenance", "ORIGIN_DESTINATION_APPROXIMATION"))
        if len(sampled_geometry) >= 2:
            total_duration = _number(candidate.summary.get("duration_seconds", candidate.summary.get("duration"))) or 0.0
            total_distance = _number(candidate.summary.get("distance_meters", candidate.summary.get("distance"))) or 0.0
            mode = candidate.transport_modes[0] if candidate.transport_modes else "unknown"
            legs = tuple({
                "segment_id": f"geometry-{index + 1}", "mode": mode,
                "start": start.to_dict(), "end": end.to_dict(),
                "duration_seconds": total_duration / (len(sampled_geometry) - 1),
                "distance_meters": total_distance / (len(sampled_geometry) - 1),
                "geometry": [start.to_dict(), end.to_dict()],
            } for index, (start, end) in enumerate(zip(sampled_geometry, sampled_geometry[1:])))
        else:
            legs = candidate.legs or ({"segment_id": "route", "mode": (candidate.transport_modes[0] if candidate.transport_modes else "unknown"), "start": candidate.origin.to_dict(), "end": candidate.destination.to_dict(), "duration_seconds": candidate.summary.get("duration_seconds"), "distance_meters": candidate.summary.get("distance_meters")},)
        for index, leg in enumerate(legs):
            segment_id = str(leg.get("segment_id") or leg.get("id") or f"segment-{index + 1}")
            start = _coordinate(leg.get("start"), candidate.origin); end = _coordinate(leg.get("end"), candidate.destination)
            duration = _number(leg.get("duration_seconds", leg.get("duration"))); distance = _number(leg.get("distance_meters", leg.get("distance")))
            raw = tuple(segment_observations.get(segment_id, ()))
            if not candidate.legs and not raw:
                raw = tuple(observations)
            midpoint = Coordinate((start.latitude + end.latitude) / 2.0, (start.longitude + end.longitude) / 2.0)
            segments.append(RouteSegmentContext(segment_id, str(leg.get("mode", "unknown")), start, end, duration, distance, leg.get("geometry"), raw, normalize_observations(raw, self.config, coordinate=midpoint), {"observation_coverage": len(raw) > 0, "spatial_matching": "polyline_segment_midpoint" if len(sampled_geometry) >= 2 else "supplied_segment_observations", "query": (query_metadata or {}).get(segment_id)}))
        all_raw = tuple(observations) or tuple(item for segment in segments for item in segment.raw_observations)
        route_stressors: list[NormalizedStressor] = []
        mode_values: dict[str, list[tuple[float, NormalizedStressor]]] = {}
        for name in ("rain", "temperature", "wind", "darkness", "traffic", "parking", "charging_availability", "air_pollution", "crowding"):
            weighted = [(segment.duration_seconds or 0.0, next(item for item in segment.normalized_stressors if item.name == name)) for segment in segments]
            aggregate, known_duration = _weighted(weighted, name)
            route_stressors.append(NormalizedStressor(aggregate.name, aggregate.value, aggregate.status, aggregate.source_variables, {**(aggregate.normalization_metadata or {}), "coverage_fraction": (known_duration / sum((segment.duration_seconds or 0.0) for segment in segments)) if sum((segment.duration_seconds or 0.0) for segment in segments) else 0.0}))
            for segment in segments: mode_values.setdefault(segment.mode, []).append(((segment.duration_seconds or 0.0), next(item for item in segment.normalized_stressors if item.name == name)))
        mode_stressors = {mode: tuple(_weighted(values, name)[0] for name in ("rain", "temperature", "wind", "darkness", "traffic", "parking", "charging_availability", "air_pollution", "crowding")) for mode, values in mode_values.items()}
        total_distance = _number(candidate.summary.get("distance_meters", candidate.summary.get("distance"))); total_duration = _number(candidate.summary.get("duration_seconds", candidate.summary.get("duration")))
        route_context = RouteContext(candidate.route_id, candidate.requested_at or "", candidate.origin, candidate.destination, total_distance, total_duration, tuple(segments), all_raw, tuple(route_stressors), mode_stressors, {"normalization_version": NORMALIZATION_VERSION, "segment_count": len(segments), "stressor_coverage": {item.name: (item.normalization_metadata or {}).get("coverage_fraction", 0.0) for item in route_stressors}}, {**dict(candidate.source_metadata), "geometry_provenance": geometry_provenance, "context_spatial_fidelity": "MEDIUM" if len(sampled_geometry) >= 2 else "LOW", "geometry_sample_count": len(sampled_geometry), "spatial_matching": "polyline_segment_midpoints" if len(sampled_geometry) >= 2 else "segment_observations_supplied", "search_radius_meters": self.search_radius_meters})
        return replace(route_context, context_facts=tuple(build_context_facts(route_context.to_dict())))


def _number(value: Any) -> float | None:
    if value is None: return None
    try:
        result = float(value); return result if math.isfinite(result) else None
    except (TypeError, ValueError): return None


def _coordinate(value: Any, fallback: Coordinate) -> Coordinate:
    if isinstance(value, Coordinate): return value
    if isinstance(value, Mapping):
        if "latitude" in value: return Coordinate(float(value["latitude"]), float(value["longitude"]))
        if "lat" in value: return Coordinate(float(value["lat"]), float(value["lon"]))
    return fallback


MAX_GEOMETRY_SAMPLE_POINTS = 6


def sample_route_geometry(geometry: Any, *, maximum_points: int = MAX_GEOMETRY_SAMPLE_POINTS) -> tuple[Coordinate, ...]:
    """Deterministically retain route bends without querying every polyline point."""
    if not isinstance(geometry, (list, tuple)) or maximum_points < 2:
        return ()
    points: list[Coordinate] = []
    for raw in geometry:
        try:
            if isinstance(raw, Mapping):
                if "latitude" in raw:
                    points.append(Coordinate(float(raw["latitude"]), float(raw["longitude"])))
                elif "lat" in raw:
                    points.append(Coordinate(float(raw["lat"]), float(raw["lon"])))
                else:
                    return ()
            elif isinstance(raw, (list, tuple)) and len(raw) >= 2:
                points.append(Coordinate(float(raw[0]), float(raw[1])))
        except (TypeError, ValueError):
            return ()
    if len(points) < 2:
        return ()
    if len(points) <= maximum_points:
        return tuple(points)
    indices = [round(index * (len(points) - 1) / (maximum_points - 1)) for index in range(maximum_points)]
    return tuple(points[index] for index in indices)
