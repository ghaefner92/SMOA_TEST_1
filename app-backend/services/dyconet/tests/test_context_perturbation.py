from __future__ import annotations

import unittest

from context_perturbation import ContextPerturbationBuilder, ToleranceProfile
from route_context import ContextStatus, Coordinate, NormalizedStressor, RouteContext, RouteSegmentContext


O = Coordinate(52.13, 11.63)
D = Coordinate(52.14, 11.64)
T = {"rain": .25, "crowding": .4, "darkness": .8, "traffic": .3, "temperature": .5}


def route(values, mode_values=None):
    return RouteContext("r", "now", O, D, normalized_stressors=tuple(NormalizedStressor(k, v, ContextStatus.DERIVED, (k,), {"normalization_version": "context-normalization-v1", "coverage_fraction": 1.0}) for k, v in values.items()), mode_specific_stressors=mode_values or {"bike": tuple(NormalizedStressor(k, v, ContextStatus.DERIVED, (k,)) for k, v in values.items())}, segments=(RouteSegmentContext("s", "bike", O, D, duration_seconds=10),))


class ContextPerturbationTests(unittest.TestCase):
    def test_effective_exposure_formula_zero_one_and_zero_context(self):
        result = ContextPerturbationBuilder({"rain": .75}).build(route({"rain": .8}))
        self.assertEqual(next(x for x in result.effective_exposures if x.stressor == "rain" and x.source_scope == "route").effective_value, .2)
        self.assertEqual(ContextPerturbationBuilder({"rain": 0}).build(route({"rain": .8})).need_perturbations["comfort"], .8)
        self.assertEqual(ContextPerturbationBuilder({"rain": 1}).build(route({"rain": .8})).need_perturbations["comfort"], 0.0)
        self.assertEqual(ContextPerturbationBuilder({"rain": .3}).build(route({"rain": 0})).need_perturbations["comfort"], 0.0)

    def test_unknown_context_and_missing_tolerance(self):
        unknown = RouteContext("r", "now", O, D, normalized_stressors=(NormalizedStressor("rain", None, ContextStatus.UNKNOWN),))
        result = ContextPerturbationBuilder({"rain": .5}).build(unknown)
        self.assertNotIn("comfort", result.need_perturbations)
        missing = ContextPerturbationBuilder({}).build(route({"rain": .8}))
        self.assertTrue(any("tolerance is missing" in warning for warning in missing.warnings))

    def test_need_target_matrix(self):
        result = ContextPerturbationBuilder(T).build(route({"rain": .6, "temperature": .5, "darkness": .4, "traffic": .7, "crowding": .8}))
        self.assertGreater(result.need_perturbations["comfort"], 0)
        self.assertGreater(result.need_perturbations["safety_accident"], 0)
        self.assertGreater(result.need_perturbations["safety_crime"], 0)
        self.assertGreater(result.need_perturbations["speed"], 0)
        self.assertGreater(result.need_perturbations["reliable"], 0)
        self.assertGreater(result.need_perturbations["privacy"], 0)
        self.assertGreater(result.need_perturbations["health_infection"], 0)

    def test_valence_targets_and_no_car_reward(self):
        values = {"rain": .8, "temperature": .5, "darkness": .6, "traffic": .7, "crowding": .4}
        modes = {"bike": tuple(NormalizedStressor(k, v, ContextStatus.DERIVED) for k, v in values.items()), "walk": tuple(NormalizedStressor(k, v, ContextStatus.DERIVED) for k, v in values.items()), "car": (NormalizedStressor("traffic", .7, ContextStatus.DERIVED),), "pt": (NormalizedStressor("crowding", .4, ContextStatus.DERIVED),), "train": (NormalizedStressor("crowding", .4, ContextStatus.DERIVED),)}
        result = ContextPerturbationBuilder(T).build(route(values, modes))
        self.assertLess(result.valence_perturbations["valence_bike"], 0)
        self.assertLess(result.valence_perturbations["valence_walk"], 0)
        self.assertLess(result.valence_perturbations["valence_car_driver"], 0.0)
        self.assertLess(result.valence_perturbations["valence_pt_bus_tram"], 0)
        self.assertLess(result.valence_perturbations["valence_train"], 0)

    def test_traffic_does_not_perturb_bike_and_pt_requires_explicit_traffic(self):
        modes = {"bike": (NormalizedStressor("traffic", .7, ContextStatus.DERIVED),), "pt_bus_tram": (NormalizedStressor("crowding", .4, ContextStatus.DERIVED),)}
        result = ContextPerturbationBuilder(T).build(route({"traffic": .7}, modes))
        self.assertEqual(result.valence_perturbations["valence_bike"], 0.0)

    def test_actions_are_always_zero_and_totals_are_not_clipped(self):
        result = ContextPerturbationBuilder({k: 0 for k in T}).build(route({"rain": 1, "temperature": 1, "crowding": 1}))
        self.assertTrue(all(value == 0.0 for value in result.action_perturbations.values()))
        self.assertEqual(result.need_perturbations["comfort"], 3.0)

    def test_determinism_alias_and_unknown_mode(self):
        modes = {"car": (NormalizedStressor("traffic", .5, ContextStatus.DERIVED),), "spaceship": ()}
        first = ContextPerturbationBuilder(T).build(route({"traffic": .5}, modes))
        second = ContextPerturbationBuilder(T).build(route({"traffic": .5}, modes))
        self.assertEqual(first, second)
        self.assertIn("car_driver", first.action_perturbations)
        self.assertIn("spaceship", first.action_perturbations)

    def test_ignored_variables_and_unknown_mode_specific_no_fallback(self):
        values = {"wind": .8, "air_pollution": .7, "parking": .6, "charging_availability": .5, "rain": .8}
        modes = {"bike": (NormalizedStressor("rain", None, ContextStatus.UNKNOWN),)}
        result = ContextPerturbationBuilder(T).build(route(values, modes))
        self.assertEqual(set(result.ignored_context_variables), {"air_pollution", "parking", "charging_availability"})
        self.assertEqual(result.valence_perturbations["valence_bike"], 0.0)
        self.assertTrue(any("not used as fallback" in warning for warning in result.warnings))

    def test_round_trip_and_auditable_contributions(self):
        result = ContextPerturbationBuilder(T).build(route({"rain": .8}))
        restored = type(result).from_json(result.to_json())
        self.assertEqual(result, restored)
        self.assertTrue(result.contributions[0].source_normalization_metadata is not None)

    def test_invalid_tolerance_and_no_beliefs_dependency(self):
        with self.assertRaises(ValueError): ToleranceProfile({"rain": 2})
        self.assertFalse(any("belief" in str(item).lower() for item in ContextPerturbationBuilder(T).build(route({"rain": .1})).to_dict().values()))


if __name__ == "__main__":
    unittest.main()
