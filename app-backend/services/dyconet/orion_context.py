"""Read-only Orion/NGSI context provider for the route-context contract.

The provider retrieves raw entity observations only.  It does not normalize
values, infer stressors, call the routing service, or invoke HOTCO-CT.
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
import json
import math
import os
from typing import Any, Callable, Mapping, Sequence
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from route_context import ContextObservation, ContextStatus


DEFAULT_BASE_URL = "https://imiq-public.et.uni-magdeburg.de/api/orion"
API_KEY_ENV = "IMIQ_ORION_API_KEY"
BASE_URL_ENV = "IMIQ_ORION_BASE_URL"
PUBLIC_TIMEOUT_SECONDS = 8.0


class OrionProviderError(RuntimeError):
    """Base class for predictable provider failures."""


class OrionConfigurationError(OrionProviderError):
    pass


class OrionTimeoutError(OrionProviderError):
    pass


class OrionNetworkError(OrionProviderError):
    pass


class OrionHttpError(OrionProviderError):
    def __init__(self, status_code: int):
        super().__init__(f"Orion returned HTTP status {status_code}")
        self.status_code = status_code


class OrionJsonError(OrionProviderError):
    pass


class OrionEntityError(OrionProviderError):
    pass


@dataclass(frozen=True)
class OrionEntity:
    entity_id: str
    entity_type: str
    attributes: Mapping[str, Any]
    raw: Mapping[str, Any]

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "OrionEntity":
        entity_id = value.get("id")
        entity_type = value.get("type")
        if not entity_id or not entity_type:
            raise OrionEntityError("Orion entity is missing id or type")
        attributes = {key: item for key, item in value.items() if key not in {"id", "type"}}
        return cls(str(entity_id), str(entity_type), attributes, dict(value))


@dataclass(frozen=True)
class OrionQueryResult:
    entities: tuple[OrionEntity, ...]

    @property
    def empty(self) -> bool:
        return not self.entities


Transport = Callable[[str, str, Mapping[str, str], Mapping[str, str], float], tuple[int, Any]]


def _default_transport(method: str, url: str, headers: Mapping[str, str], params: Mapping[str, str], timeout: float) -> tuple[int, Any]:
    query = urlencode(params)
    request = Request(f"{url}?{query}" if query else url, method=method, headers=dict(headers))
    try:
        with urlopen(request, timeout=timeout) as response:
            return int(response.status), json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        return int(exc.code), None
    except TimeoutError as exc:
        raise OrionTimeoutError("Orion request timed out") from exc
    except URLError as exc:
        raise OrionNetworkError("Orion request failed") from exc
    except json.JSONDecodeError as exc:
        raise OrionJsonError("Orion returned malformed JSON") from exc


ENTITY_TYPES = {
    "weather": "Weather",
    "parking": "Parking",
    "air_quality": "AirQuality",
    "traffic": "Traffic",
    "ev_charging": "EVChargingStation",
    "vehicles": "Vehicle",
    "water_levels": "WaterLevel",
    "buildings": "Building",
    "restaurant": "Restaurant",
    "cafe": "Cafe",
    "supermarket": "Supermarket",
    "kiosk": "Kiosk",
}

EXPECTED_ATTRIBUTES = {
    "Weather": ("temperature", "humidity", "rain", "windSpeed", "windGust", "windSpeed10Min", "uvIndex", "lightIntensity", "datetime"),
    "Parking": ("name", "freeSpots", "totalSpots", "status"),
    "AirQuality": ("pm25", "pm10", "no2", "o3", "co2", "temperature", "humidity"),
    "Traffic": ("avgSpeed", "speedLimit", "geometry", "outline"),
    "EVChargingStation": ("name", "availableCapacity", "capacity"),
    "Vehicle": ("category", "source", "location"),
    "WaterLevel": ("level", "volume"),
    "Building": ("name", "electricity", "heating", "cold-water", "temperature", "humidity"),
    "Restaurant": ("name", "cuisine", "opening_hours", "website", "location"),
    "Cafe": ("name", "cuisine", "opening_hours", "website", "location"),
    "Supermarket": ("name", "opening_hours", "website", "location"),
    "Kiosk": ("name", "opening_hours", "website", "location"),
}


def _metadata_value(metadata: Mapping[str, Any], key: str) -> Any:
    value = metadata.get(key)
    return value.get("value") if isinstance(value, Mapping) and "value" in value else value


def _attribute_value(value: Any) -> tuple[Any, str | None, str | None, dict[str, Any]]:
    """Extract full NGSI attributes while retaining wrappers and nested metadata."""

    if not isinstance(value, Mapping) or "value" not in value:
        return value, None, None, {}
    metadata = value.get("metadata") if isinstance(value.get("metadata"), Mapping) else {}
    unit = value.get("unitCode") or value.get("unit") or _metadata_value(metadata, "unitCode") or _metadata_value(metadata, "unit")
    timestamp = value.get("observedAt") or value.get("datetime") or _metadata_value(metadata, "observedAt") or _metadata_value(metadata, "datetime")
    return value.get("value"), str(unit) if unit is not None else None, str(timestamp) if timestamp is not None else None, {
        "ngsi_type": value.get("type"), "ngsi_metadata": dict(metadata),
    }


def _timestamp_metadata(timestamp: str | None) -> dict[str, Any]:
    if not timestamp:
        return {"timestamp_confidence": "UNKNOWN"}
    try:
        parsed = datetime.fromisoformat(timestamp.replace("Z", "+00:00"))
    except ValueError:
        return {"timestamp_raw": timestamp, "timestamp_confidence": "UNKNOWN"}
    if parsed.tzinfo is None:
        return {"timestamp_raw": timestamp, "parsed_local_datetime": parsed.isoformat(), "timezone_provenance": "INFERRED_EUROPE_BERLIN", "timestamp_confidence": "WEAKLY_INFERRED"}
    return {"timestamp_raw": timestamp, "parsed_datetime": parsed.isoformat(), "timezone_provenance": "EXPLICIT", "timestamp_confidence": "CONFIRMED"}


WALTER_WEATHER_ENTITY = "Sensor:Weather:Walter"


def _source_contract(variable: str, unit: str | None, source_entity_id: str | None = None) -> tuple[str, bool]:
    normalized_unit = (unit or "").strip().lower()
    explicit_units = {
        "temperature": {"c", "°c", "celsius", "degree celsius", "cel"},
        "dewPointTemperature": {"c", "°c", "celsius", "degree celsius", "cel"},
        "feelsLikeTemperature": {"c", "°c", "celsius", "degree celsius", "cel"},
        "heatIndexTemperature": {"c", "°c", "celsius", "degree celsius", "cel"},
        "windChillTemperature": {"c", "°c", "celsius", "degree celsius", "cel"},
        "humidity": {"%", "percent", "percentage"},
        "airPressure": {"hpa", "mbar"},
        "airPressureAbsolute": {"hpa", "mbar"},
        "windSpeed": {"m/s", "mps"},
        "windSpeed10Min": {"m/s", "mps"},
        "windGust": {"m/s", "mps"},
        "windDirection": {"degree", "degrees", "°"},
    }
    if variable in explicit_units:
        if normalized_unit in explicit_units[variable]:
            return "CONFIRMED", True
        if source_entity_id == WALTER_WEATHER_ENTITY and not normalized_unit:
            # Walter's published sensor contract supplies these units out of
            # band even though the NGSI instance omits unit metadata.
            return "CONFIRMED", True
        return "WEAKLY_INFERRED", False
    if variable in {"rain", "rainHourly", "rainDaily", "rainWeekly", "rainMonthly", "rainYearly"}:
        if source_entity_id == WALTER_WEATHER_ENTITY and not normalized_unit:
            return "CONFIRMED", True
        return "UNKNOWN", False
    if variable in {"uvIndex", "lightIntensity"} and source_entity_id == WALTER_WEATHER_ENTITY and not normalized_unit:
        return "STRONGLY_INFERRED", True
    if variable == "datetime":
        return "STRONGLY_INFERRED", True
    return "UNKNOWN", False


def _numeric(value: Any) -> float | None:
    if isinstance(value, bool) or value is None:
        return None
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if math.isfinite(number) else None


def _location_metadata(value: Any) -> dict[str, Any] | None:
    if isinstance(value, str):
        parts = [part.strip() for part in value.split(",")]
        if len(parts) == 2:
            try:
                lat, lon = float(parts[0]), float(parts[1])
                if -90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0:
                    return {
                        "raw_location": value,
                        "latitude": lat,
                        "longitude": lon,
                        "usable": not (lat == 0.0 and lon == 0.0),
                        **({"validity": "INVALID_PLACEHOLDER"} if lat == 0.0 and lon == 0.0 else {}),
                    }
            except (TypeError, ValueError):
                pass
        return {"raw_location": value, "usable": False, "validity": "INVALID_COORDINATES"}
    if not isinstance(value, Mapping):
        return None
    metadata: dict[str, Any] = {"raw_location": dict(value)}
    coordinates = value.get("coordinates")
    if isinstance(coordinates, Sequence) and not isinstance(coordinates, (str, bytes)) and len(coordinates) >= 2:
        try:
            lon, lat = float(coordinates[0]), float(coordinates[1])
            metadata["latitude"] = lat
            metadata["longitude"] = lon
            if lat == 0.0 and lon == 0.0:
                metadata.update({"usable": False, "validity": "INVALID_PLACEHOLDER"})
        except (TypeError, ValueError):
            metadata.update({"usable": False, "validity": "INVALID_COORDINATES"})
    return metadata


def _coordinate_from_query(coords: str | None) -> tuple[float, float] | None:
    if not coords:
        return None
    parts = [part.strip() for part in coords.split(",")]
    if len(parts) != 2:
        return None
    try:
        latitude, longitude = float(parts[0]), float(parts[1])
    except (TypeError, ValueError):
        return None
    return (latitude, longitude) if -90.0 <= latitude <= 90.0 and -180.0 <= longitude <= 180.0 else None


def _distance_meters(first: tuple[float, float], second: tuple[float, float]) -> float:
    latitude_1, longitude_1 = map(math.radians, first)
    latitude_2, longitude_2 = map(math.radians, second)
    d_latitude, d_longitude = latitude_2 - latitude_1, longitude_2 - longitude_1
    a = math.sin(d_latitude / 2.0) ** 2 + math.cos(latitude_1) * math.cos(latitude_2) * math.sin(d_longitude / 2.0) ** 2
    return 6_371_000.0 * 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))


class OrionContextProvider:
    """Small injectable Orion client with no implicit retries or cache."""

    def __init__(self, *, api_key: str | None = None, base_url: str | None = None, timeout: float = PUBLIC_TIMEOUT_SECONDS, transport: Transport | None = None) -> None:
        self.api_key = api_key if api_key is not None else os.environ.get(API_KEY_ENV)
        if timeout <= 0:
            raise ValueError("timeout must be positive")
        self.base_url = (base_url or os.environ.get(BASE_URL_ENV) or DEFAULT_BASE_URL).rstrip("/")
        self.public_mode = self.base_url == DEFAULT_BASE_URL
        self.timeout = timeout
        self._transport = transport or _default_transport

    def _request(self, path: str, params: Mapping[str, str] | None = None) -> Any:
        headers = {"Accept": "application/json"}
        # Legacy private deployments may opt in to the historical header.
        # The public endpoint never receives credentials, even if an ambient
        # legacy environment variable happens to be set.
        if not self.public_mode and self.api_key:
            headers["x-api-key"] = self.api_key
        try:
            status, payload = self._transport("GET", f"{self.base_url}/{path.lstrip('/')}", headers, params or {}, self.timeout)
        except OrionProviderError:
            raise
        except TimeoutError as exc:
            raise OrionTimeoutError("Orion request timed out") from exc
        except Exception as exc:
            raise OrionNetworkError("Orion request failed") from exc
        if status in (401, 403):
            raise OrionHttpError(status)
        if not 200 <= status < 300:
            raise OrionHttpError(status)
        if payload is None:
            raise OrionJsonError("Orion response did not contain JSON")
        return payload

    def get_types(self) -> tuple[str, ...]:
        payload = self._request("types")
        if isinstance(payload, Mapping):
            payload = payload.get("type", payload.get("types", []))
        if not isinstance(payload, list):
            raise OrionJsonError("Orion /types response is not an array")
        return tuple(
            str(item.get("id") or item.get("type") or item)
            if isinstance(item, Mapping)
            else str(item)
            for item in payload
        )

    def query_entities(self, *, entity_type: str | None = None, attrs: Sequence[str] | None = None, limit: int | None = None, q: str | None = None, georel: str | None = None, geometry: str | None = None, coords: str | None = None, key_values: bool = False) -> OrionQueryResult:
        if limit is not None and limit <= 0:
            raise ValueError("limit must be positive")
        params: dict[str, str] = {"options": "keyValues"} if key_values else {}
        if entity_type: params["type"] = entity_type
        if attrs: params["attrs"] = ",".join(attrs)
        if limit is not None: params["limit"] = str(limit)
        if q: params["q"] = q
        if georel: params["georel"] = georel
        if geometry: params["geometry"] = geometry
        if coords: params["coords"] = coords
        payload = self._request("entities", params)
        if isinstance(payload, Mapping): payload = payload.get("entities", payload.get("data", payload))
        if not isinstance(payload, list):
            raise OrionJsonError("Orion /entities response is not an array")
        entities = []
        for item in payload:
            if not isinstance(item, Mapping):
                raise OrionEntityError("Orion entity is not an object")
            entities.append(OrionEntity.from_mapping(item))
        return OrionQueryResult(tuple(entities))

    def observations(self, entity: OrionEntity, *, variables: Sequence[str] | None = None) -> tuple[ContextObservation, ...]:
        names = tuple(variables) if variables is not None else tuple(entity.attributes.keys())
        location = _location_metadata(entity.attributes.get("location"))
        entity_timestamp = entity.attributes.get("datetime") or entity.attributes.get("observedAt")
        if isinstance(entity_timestamp, Mapping):
            entity_timestamp = _attribute_value(entity_timestamp)[2] or _attribute_value(entity_timestamp)[0]
        observations: list[ContextObservation] = []
        for variable in names:
            if variable not in entity.attributes:
                observations.append(ContextObservation("orion", variable, status=ContextStatus.UNKNOWN, source_entity_id=entity.entity_id, spatial_metadata={"entity_type": entity.entity_type, **({"location": location} if location else {})}))
                continue
            raw, unit, timestamp, attribute_metadata = _attribute_value(entity.attributes[variable])
            spatial = {"entity_type": entity.entity_type, **({"location": location} if location else {})}
            if variable == "location": spatial.update(location or {})
            effective_timestamp = timestamp or entity_timestamp
            confidence, eligible = _source_contract(variable, unit, entity.entity_id)
            source_metadata = {**attribute_metadata, **_timestamp_metadata(str(effective_timestamp) if effective_timestamp is not None else None), "source_contract_confidence": confidence, "normalization_eligible": eligible}
            observations.append(ContextObservation("orion", variable, raw, _numeric(raw), unit, effective_timestamp, ContextStatus.OBSERVED, source_entity_id=entity.entity_id, spatial_metadata=spatial, source_metadata=source_metadata))
        return tuple(observations)

    def get_context(self, family: str, *, nearest_only: bool = False, **query: Any) -> tuple[ContextObservation, ...]:
        entity_type = ENTITY_TYPES[family]
        result = self.query_entities(entity_type=entity_type, **query)
        entities = result.entities
        target = _coordinate_from_query(query.get("coords"))
        if nearest_only and target:
            located = []
            for entity in entities:
                location = _location_metadata(entity.attributes.get("location")) or {}
                if location.get("usable") and "latitude" in location and "longitude" in location:
                    located.append((_distance_meters(target, (location["latitude"], location["longitude"])), entity))
            if located:
                entities = (min(located, key=lambda item: item[0])[1],)
        variables = EXPECTED_ATTRIBUTES.get(entity_type)
        return tuple(observation for entity in entities for observation in self.observations(entity, variables=variables))

    def get_weather(self, **query: Any): return self.get_context("weather", **query)
    def get_parking(self, **query: Any): return self.get_context("parking", **query)
    def get_air_quality(self, **query: Any): return self.get_context("air_quality", **query)
    def get_traffic(self, **query: Any): return self.get_context("traffic", **query)
    def get_ev_charging(self, **query: Any): return self.get_context("ev_charging", **query)
    def get_vehicles(self, **query: Any): return self.get_context("vehicles", **query)
    def get_water_levels(self, **query: Any): return self.get_context("water_levels", **query)
    def get_buildings(self, **query: Any): return self.get_context("buildings", **query)
    def get_pois(self, **query: Any): return self.get_context(query.pop("family", "restaurant"), **query)
