from __future__ import annotations

import unittest

from route_context import CandidateRouteInput, ContextObservation, ContextStatus, Coordinate
from route_context_adapter import (
    RouteContextAdapter,
    normalize_air_pollution,
    normalize_crowding,
    normalize_observations,
    normalize_parking,
    normalize_temperature,
    normalize_traffic,
    normalize_charging,
    normalize_darkness,
    sample_route_geometry,
)


O = Coordinate(52.13, 11.63)
D = Coordinate(52.14, 11.64)


def obs(variable, value, entity="e1"):
    return ContextObservation("orion", variable, value, float(value) if isinstance(value, (int, float)) else None, source_entity_id=entity, status=ContextStatus.OBSERVED)


class RouteContextAdapterTests(unittest.TestCase):
    def test_curved_geometry_preserves_bend_and_route_fidelity(self):
        geometry = [O.to_dict(), {"latitude": 52.13, "longitude": 11.66}, D.to_dict()]
        sampled = sample_route_geometry(geometry)
        self.assertEqual(sampled[1], Coordinate(52.13, 11.66))
        route = RouteContextAdapter().enrich(CandidateRouteInput(
            "curved", O, D, summary={"duration_seconds": 300, "distance_meters": 1200},
            geometry=geometry, transport_modes=("bike",),
            source_metadata={"geometry_provenance": "ROUTE_RECONSTRUCTION"},
        ))
        self.assertEqual(len(route.segments), 2)
        self.assertEqual(route.source_metadata["context_spatial_fidelity"], "MEDIUM")
        self.assertEqual(route.segments[0].end, Coordinate(52.13, 11.66))

    def test_invalid_geometry_downgrades_to_endpoint_approximation(self):
        route = RouteContextAdapter().enrich(CandidateRouteInput("bad", O, D, geometry=[{"bad": 1}]))
        self.assertEqual(len(route.segments), 1)
        self.assertEqual(route.source_metadata["context_spatial_fidelity"], "LOW")
    def test_temperature_comfort_cold_hot_and_clamp(self):
        self.assertEqual(normalize_temperature((obs("temperature", 20),)).value, 0.0)
        self.assertGreater(normalize_temperature((obs("temperature", 0),)).value, 0.0)
        self.assertGreater(normalize_temperature((obs("temperature", 30),)).value, 0.0)
        self.assertEqual(normalize_temperature((obs("temperature", -100),)).value, 1.0)

    def test_missing_temperature_is_unknown(self):
        item = normalize_temperature(())
        self.assertIsNone(item.value); self.assertEqual(item.status, ContextStatus.UNKNOWN)

    def test_traffic_ratio_and_missing_speed(self):
        self.assertAlmostEqual(normalize_traffic((obs("avgSpeed", 25), obs("speedLimit", 50))).value, .5)
        self.assertIsNone(normalize_traffic((obs("speedLimit", 50),)).value)

    def test_parking(self):
        self.assertAlmostEqual(normalize_parking((obs("freeSpots", 25), obs("totalSpots", 100))).value, .75)

    def test_charging_is_availability(self):
        item = normalize_charging((obs("availableCapacity", 3), obs("capacity", 6)))
        self.assertAlmostEqual(item.value, .5); self.assertEqual(item.normalization_metadata["parameters"]["semantic"], "availability_not_stress")

    def test_air_quality_missing_and_partial(self):
        self.assertIsNone(normalize_air_pollution(()).value)
        item = normalize_air_pollution((obs("pm25", 12.5),))
        self.assertAlmostEqual(item.value, .5)
        self.assertNotIn("pm10", item.normalization_metadata["pollutant_values"])

    def test_crowding_requires_explicit_observation(self):
        self.assertIsNone(normalize_crowding((obs("avgSpeed", 1),)).value)

    def test_darkness_uses_route_location_and_solar_elevation(self):
        midday = normalize_darkness((obs("datetime", "2026-06-21T12:00:00+02:00"),), coordinate=O)
        midnight = normalize_darkness((obs("datetime", "2026-06-21T00:00:00+02:00"),), coordinate=O)
        self.assertEqual(midday.value, 0.0)
        self.assertEqual(midnight.value, 1.0)
        self.assertEqual(midnight.normalization_metadata["method"], "solar_elevation_civil_twilight")

    def test_darkness_without_route_coordinate_remains_unknown(self):
        item = normalize_darkness((obs("datetime", "2026-06-21T00:00:00+02:00"),))
        self.assertIsNone(item.value)
        self.assertEqual(item.status, ContextStatus.UNKNOWN)

    def test_intermodal_segment_exposure_and_raw_preservation(self):
        candidate = CandidateRouteInput("r", O, D, summary={"duration_seconds": 1500}, legs=(
            {"segment_id": "w1", "mode": "walk", "start": O.to_dict(), "end": O.to_dict(), "duration_seconds": 360},
            {"segment_id": "t1", "mode": "pt_bus_tram", "start": O.to_dict(), "end": D.to_dict(), "duration_seconds": 900},
            {"segment_id": "w2", "mode": "walk", "start": D.to_dict(), "end": D.to_dict(), "duration_seconds": 240},
        ))
        segment = {"w1": (obs("rain", 8), obs("temperature", 20)), "t1": (obs("rain", 1),), "w2": (obs("rain", 8), obs("temperature", 20))}
        route = RouteContextAdapter().enrich(candidate, segment_observations=segment)
        self.assertEqual([s.mode for s in route.segments], ["walk", "pt_bus_tram", "walk"])
        self.assertEqual(len(route.raw_observations), 5)
        self.assertAlmostEqual(next(x for x in route.mode_specific_stressors["walk"] if x.name == "rain").value, 0.8)

    def test_duration_weighting_excludes_unknown_and_reports_coverage(self):
        candidate = CandidateRouteInput("r", O, D, legs=(
            {"segment_id": "a", "mode": "walk", "start": O.to_dict(), "end": D.to_dict(), "duration_seconds": 10},
            {"segment_id": "b", "mode": "walk", "start": O.to_dict(), "end": D.to_dict(), "duration_seconds": 30},
        ))
        route = RouteContextAdapter().enrich(candidate, segment_observations={"a": (obs("rain", 0),), "b": ()})
        rain = next(x for x in route.normalized_stressors if x.name == "rain")
        self.assertEqual(rain.value, 0.0); self.assertEqual(rain.normalization_metadata["coverage_fraction"], .25)

    def test_zero_is_known_zero_and_no_tolerance_or_hotco_data(self):
        values = normalize_observations((obs("rain", 0),))
        self.assertEqual(next(x for x in values if x.name == "rain").value, 0.0)
        self.assertFalse(any("tolerance" in str(x).lower() for x in values))

    def test_unsegmented_candidate_uses_route_observations(self):
        route = RouteContextAdapter().enrich(CandidateRouteInput("r", O, D, summary={"duration_seconds": 1}), observations=(obs("rain", 0),))
        rain = next(x for x in route.normalized_stressors if x.name == "rain")
        self.assertEqual(rain.value, 0.0)
        self.assertTrue(rain.normalization_metadata["source_observations"])


if __name__ == "__main__":
    unittest.main()
