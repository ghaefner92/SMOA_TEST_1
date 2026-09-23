from __future__ import annotations

import os
import unittest
from unittest.mock import Mock

from orion_context import (
    OrionContextProvider,
    OrionHttpError,
    OrionJsonError,
    OrionTimeoutError,
)
from route_context import ContextStatus


def transport(payload, status=200):
    return lambda method, url, headers, params, timeout: (status, payload)


class OrionContextTests(unittest.TestCase):
    def setUp(self):
        self.old_key = os.environ.get("IMIQ_ORION_API_KEY")
        os.environ["IMIQ_ORION_API_KEY"] = "test-only-key"

    def tearDown(self):
        if self.old_key is None: os.environ.pop("IMIQ_ORION_API_KEY", None)
        else: os.environ["IMIQ_ORION_API_KEY"] = self.old_key

    def test_legacy_key_is_ignored_by_public_runtime(self):
        provider = OrionContextProvider(transport=transport([]))
        self.assertEqual(provider.api_key, "test-only-key")
        self.assertTrue(provider.public_mode)

    def test_base_url_can_be_overridden_and_types_are_typed(self):
        provider = OrionContextProvider(base_url="https://example.invalid/orion", transport=transport([{"id": "Weather"}]))
        self.assertEqual(provider.get_types(), ("Weather",))

    def test_orion_type_descriptors_use_the_real_type_field(self):
        provider = OrionContextProvider(transport=transport([{"type": "Weather", "attrs": {"rain": {"types": ["Number"]}}, "count": 1}]))
        self.assertEqual(provider.get_types(), ("Weather",))

    def test_public_requests_send_no_auth_headers(self):
        seen = {}
        def fake(method, url, headers, params, timeout):
            seen.update(headers); return 200, []
        OrionContextProvider(transport=fake).query_entities()
        self.assertNotIn("x-api-key", seen)
        self.assertNotIn("Authorization", seen)

    def test_missing_key_is_valid_public_configuration(self):
        os.environ.pop("IMIQ_ORION_API_KEY")
        self.assertTrue(OrionContextProvider(transport=transport([])).query_entities().empty)

    def test_weather_numeric_string_preserves_raw_value(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather", "temperature": "18.4"}]))
        item = next(item for item in provider.get_weather() if item.variable == "temperature")
        self.assertEqual(item.raw_value, "18.4"); self.assertEqual(item.numeric_value, 18.4)

    def test_zero_is_preserved(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather", "rain": "0"}]))
        item = next(item for item in provider.get_weather() if item.variable == "rain")
        self.assertEqual(item.numeric_value, 0.0); self.assertEqual(item.status, ContextStatus.OBSERVED)

    def test_missing_weather_attribute_is_unknown(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather"}]))
        item = next(item for item in provider.get_weather() if item.variable == "rain")
        self.assertIsNone(item.numeric_value); self.assertEqual(item.status, ContextStatus.UNKNOWN)

    def test_missing_traffic_speed_is_unknown(self):
        provider = OrionContextProvider(transport=transport([{"id": "t1", "type": "Traffic"}]))
        item = next(item for item in provider.get_traffic() if item.variable == "avgSpeed")
        self.assertIsNone(item.numeric_value); self.assertNotEqual(item.numeric_value, 0.0)

    def test_capacity_fields_parse(self):
        provider = OrionContextProvider(transport=transport([{"id": "p1", "type": "Parking", "freeSpots": "4", "totalSpots": 10}, {"id": "e1", "type": "EVChargingStation", "capacity": "3"}]))
        parking = provider.get_parking(); charging = provider.get_ev_charging()
        self.assertEqual(next(x for x in parking if x.variable == "freeSpots").numeric_value, 4.0)
        self.assertEqual(next(x for x in charging if x.variable == "capacity" and x.numeric_value is not None).numeric_value, 3.0)

    def test_partial_air_quality_does_not_crash(self):
        observations = OrionContextProvider(transport=transport([{"id": "a1", "type": "AirQuality", "pm25": 2}])).get_air_quality()
        self.assertIsNone(next(x for x in observations if x.variable == "pm10").numeric_value)

    def test_ngsi_attribute_wrapper_preserves_unit_and_timestamp(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather", "temperature": {"value": "18.4", "unit": "CEL", "observedAt": "2026-09-12T10:00:00Z"}}]))
        item = next(x for x in provider.get_weather() if x.variable == "temperature")
        self.assertEqual((item.unit, item.timestamp), ("CEL", "2026-09-12T10:00:00Z"))
        self.assertEqual(item.source_metadata["source_contract_confidence"], "CONFIRMED")

    def test_nested_ngsi_metadata_is_preserved(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather", "temperature": {"type": "Number", "value": "18.4", "metadata": {"unitCode": {"type": "Text", "value": "CEL"}, "observedAt": {"type": "DateTime", "value": "2026-09-12T10:00:00Z"}}}}]))
        item = next(x for x in provider.get_weather() if x.variable == "temperature")
        self.assertEqual((item.unit, item.timestamp), ("CEL", "2026-09-12T10:00:00Z"))
        self.assertEqual(item.source_metadata["ngsi_type"], "Number")
        self.assertIn("unitCode", item.source_metadata["ngsi_metadata"])

    def test_naive_weather_datetime_is_preserved_with_inferred_timezone_provenance(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather", "datetime": "2026-09-14 10:22:27", "temperature": {"value": "16.5", "unitCode": "CEL"}}]))
        item = next(x for x in provider.get_weather() if x.variable == "temperature")
        self.assertEqual(item.timestamp, "2026-09-14 10:22:27")
        self.assertEqual(item.source_metadata["timezone_provenance"], "INFERRED_EUROPE_BERLIN")

    def test_malformed_entity_is_explicit(self):
        from orion_context import OrionEntityError
        with self.assertRaises(OrionEntityError): OrionContextProvider(transport=transport([{"type": "Weather"}])).query_entities()

    def test_invalid_vehicle_location_is_flagged(self):
        observations = OrionContextProvider(transport=transport([{"id": "v1", "type": "Vehicle", "location": {"type": "Point", "coordinates": [0, 0]}}])).get_vehicles()
        location = next(x for x in observations if x.variable == "location")
        self.assertEqual(location.spatial_metadata["validity"], "INVALID_PLACEHOLDER")

    def test_string_location_is_parsed_and_preserved_for_all_entity_observations(self):
        provider = OrionContextProvider(transport=transport([{"id": "w1", "type": "Weather", "location": "52.1412, 11.6545", "datetime": "2026-09-14 10:22:27", "temperature": "16.5"}]))
        temperature = next(item for item in provider.get_weather() if item.variable == "temperature")
        location = temperature.spatial_metadata["location"]
        self.assertEqual((location["latitude"], location["longitude"]), (52.1412, 11.6545))
        self.assertEqual(temperature.timestamp, "2026-09-14 10:22:27")

    def test_nearest_only_selects_closest_located_weather_station(self):
        provider = OrionContextProvider(transport=transport([
            {"id": "far", "type": "Weather", "location": "52.20, 11.70", "temperature": "5"},
            {"id": "near", "type": "Weather", "location": "52.14, 11.64", "temperature": "15"},
        ]))
        temperatures = [item for item in provider.get_context("weather", coords="52.141,11.641", nearest_only=True) if item.variable == "temperature"]
        self.assertEqual(len(temperatures), 1)
        self.assertEqual((temperatures[0].source_entity_id, temperatures[0].numeric_value), ("near", 15.0))

    def test_geo_query_parameters_are_generated(self):
        seen = {}
        def fake(method, url, headers, params, timeout):
            seen.update(params); return 200, []
        OrionContextProvider(transport=fake).query_entities(entity_type="Traffic", attrs=("avgSpeed",), limit=5, q="x", georel="near;maxDistance:500", geometry="point", coords="52.1395,11.6476")
        self.assertEqual(seen, {"type": "Traffic", "attrs": "avgSpeed", "limit": "5", "q": "x", "georel": "near;maxDistance:500", "geometry": "point", "coords": "52.1395,11.6476"})

    def test_keyvalues_remains_explicit_compatibility_mode(self):
        seen = {}
        OrionContextProvider(transport=lambda method, url, headers, params, timeout: (seen.update(params) or (200, []))).query_entities(key_values=True)
        self.assertEqual(seen, {"options": "keyValues"})

    def test_failures_are_predictable(self):
        with self.assertRaises(OrionTimeoutError):
            OrionContextProvider(transport=lambda *args: (_ for _ in ()).throw(TimeoutError())) .query_entities()
        with self.assertRaises(OrionHttpError) as error: OrionContextProvider(transport=transport([], 500)).query_entities()
        self.assertEqual(error.exception.status_code, 500)
        with self.assertRaises(OrionJsonError): OrionContextProvider(transport=transport({"bad": True})).query_entities()

    def test_auth_error_does_not_leak_key(self):
        with self.assertRaises(OrionHttpError) as error: OrionContextProvider(transport=transport([], 401)).query_entities()
        self.assertNotIn("test-only-key", str(error.exception))
        with self.assertRaises(OrionHttpError): OrionContextProvider(transport=transport([], 403)).query_entities()

    def test_empty_result_is_valid(self):
        self.assertTrue(OrionContextProvider(transport=transport([])).query_entities().empty)

    def test_no_normalization_or_simulation_or_routing(self):
        provider = OrionContextProvider(transport=transport([{"id": "w", "type": "Weather", "rain": 0.62}]))
        item = next(x for x in provider.get_weather() if x.variable == "rain")
        self.assertEqual(item.numeric_value, 0.62)
        self.assertFalse(hasattr(item, "value"))


if __name__ == "__main__":
    unittest.main()
