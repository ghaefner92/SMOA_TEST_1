from __future__ import annotations

import json
import unittest

from route_context import (
    CandidateRouteInput,
    ContextObservation,
    ContextStatus,
    Coordinate,
    NormalizedStressor,
    RouteContext,
    RouteContextBundle,
    RouteSegmentContext,
)


ORIGIN = Coordinate(52.1300, 11.6300)
DESTINATION = Coordinate(52.1400, 11.6400)


def stressor(name: str, value: float | None, status: ContextStatus) -> NormalizedStressor:
    return NormalizedStressor(name=name, value=value, status=status, source_variables=(name,))


class RouteContextTests(unittest.TestCase):
    def test_simple_bicycle_route(self) -> None:
        route = RouteContext(
            "bike-1", "2026-09-12T10:00:00Z", ORIGIN, DESTINATION,
            total_distance_meters=2200, total_duration_seconds=600,
            segments=(RouteSegmentContext("s1", "bike", ORIGIN, DESTINATION, distance_meters=2200),),
        )
        self.assertEqual(route.segments[0].mode, "bike")

    def test_intermodal_route_and_segment_exposure(self) -> None:
        segments = (
            RouteSegmentContext("walk-1", "walk", ORIGIN, ORIGIN, normalized_stressors=(stressor("rain", .85, ContextStatus.DERIVED),)),
            RouteSegmentContext("tram-1", "pt_bus_tram", ORIGIN, DESTINATION, normalized_stressors=(stressor("crowding", None, ContextStatus.UNKNOWN),)),
            RouteSegmentContext("walk-2", "walk", DESTINATION, DESTINATION),
        )
        route = RouteContext("multi", "now", ORIGIN, DESTINATION, segments=segments)
        self.assertEqual([s.mode for s in route.segments], ["walk", "pt_bus_tram", "walk"])
        self.assertIsNone(route.segments[1].normalized_stressors[0].value)

    def test_route_and_mode_specific_stressors_coexist(self) -> None:
        route = RouteContext(
            "r", "now", ORIGIN, DESTINATION,
            normalized_stressors=(stressor("rain", .62, ContextStatus.DERIVED),),
            mode_specific_stressors={"walk": (stressor("rain", .85, ContextStatus.DERIVED),)},
        )
        self.assertEqual(route.normalized_stressors[0].value, .62)
        self.assertEqual(route.mode_specific_stressors["walk"][0].value, .85)

    def test_unknown_is_not_zero(self) -> None:
        unknown = stressor("rain", None, ContextStatus.UNKNOWN)
        self.assertIsNone(unknown.to_dict()["value"])
        self.assertNotEqual(unknown.to_dict()["value"], 0.0)
        self.assertEqual(stressor("rain", 0.0, ContextStatus.OBSERVED).value, 0.0)

    def test_invalid_values_are_rejected(self) -> None:
        with self.assertRaises(ValueError): stressor("rain", 1.1, ContextStatus.DERIVED)
        with self.assertRaises(ValueError): RouteContext("r", "now", ORIGIN, DESTINATION, total_distance_meters=-1)
        with self.assertRaises(ValueError): RouteContext("r", "now", ORIGIN, DESTINATION, total_duration_seconds=-1)
        with self.assertRaises(ValueError): Coordinate(91, 0)

    def test_round_trip_preserves_semantics(self) -> None:
        route = RouteContext("r", "now", ORIGIN, DESTINATION, raw_observations=(ContextObservation("weather", "rain", "unknown"),), normalized_stressors=(stressor("rain", None, ContextStatus.UNKNOWN),))
        self.assertEqual(route, RouteContext.from_json(route.to_json()))

    def test_future_mode_name_is_forward_compatible(self) -> None:
        candidate = CandidateRouteInput.from_ranked_route({"route_id": "future", "mode_key": "escooter", "legs": [{"mode": "escooter"}]}, origin=ORIGIN, destination=DESTINATION)
        self.assertEqual(candidate.route_id, "future")
        self.assertEqual(candidate.transport_modes, ("escooter",))
        self.assertEqual(candidate, CandidateRouteInput.from_json(candidate.to_json()))

    def test_bundle_holds_multiple_candidates(self) -> None:
        routes = tuple(RouteContext(str(i), "now", ORIGIN, DESTINATION) for i in (1, 2))
        bundle = RouteContextBundle("search-1", "now", routes)
        self.assertEqual(len(RouteContextBundle.from_json(bundle.to_json()).candidate_routes), 2)

    def test_creation_does_not_run_hotco(self) -> None:
        # The data contract has no engine dependency or simulation side effect.
        route = RouteContext("r", "now", ORIGIN, DESTINATION)
        self.assertNotIn("simulation", json.dumps(route.to_dict()))


if __name__ == "__main__":
    unittest.main()
