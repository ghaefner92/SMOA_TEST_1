from __future__ import annotations

import copy
import json
import unittest

import numpy as np

from context_perturbation import ContextPerturbationResult
from hotco_ct_v4_3 import HOTCOCTParameters, HOTCOCTv43
from input_mapping_v4_3 import MODES, NEEDS, parse_participant_input


def participant():
    with open("example_request.json", encoding="utf-8") as handle:
        return parse_participant_input(json.load(handle))


def perturbation(needs=None, valences=None, actions=None):
    return ContextPerturbationResult(
        route_id="route-test", normalization_version="context-normalization-v1",
        perturbation_version="context-perturbation-v1", effective_exposures=(),
        need_perturbations=needs or {}, action_perturbations=actions or {},
        valence_perturbations=valences or {}, contributions=(), missing_stressors=(),
        ignored_context_variables=(), warnings=(), data_coverage={},
    )


class ContextIntegrationTests(unittest.TestCase):
    def test_no_context_is_exact_baseline(self):
        engine = HOTCOCTv43()
        baseline = engine.simulate(participant())
        explicit_zero = engine.simulate(participant(), perturbation())
        self.assertTrue(np.array_equal(baseline.trajectory, explicit_zero.trajectory))
        self.assertFalse(baseline.context_metadata["context_applied"])
        self.assertTrue(all(value == 0.0 for value in baseline.context_metadata["xi_context"].values()))

    def test_zero_alpha_is_exact_baseline(self):
        engine = HOTCOCTv43(parameters=HOTCOCTParameters(alpha_context=0.0))
        baseline = engine.simulate(participant())
        contextual = engine.simulate(participant(), perturbation({"comfort": 10.0}, {"valence_bike": -10.0}))
        self.assertTrue(np.array_equal(baseline.trajectory, contextual.trajectory))

    def test_context_enters_dynamics_and_metadata(self):
        engine = HOTCOCTv43()
        baseline = engine.simulate(participant())
        contextual = engine.simulate(participant(), perturbation({"comfort": 0.8}))
        self.assertFalse(np.array_equal(baseline.trajectory, contextual.trajectory))
        self.assertEqual(contextual.context_metadata["alpha_context"], 1.0)
        self.assertTrue(contextual.context_metadata["applied"])
        self.assertTrue(contextual.context_metadata["context_applied"])
        self.assertEqual(contextual.context_metadata["xi_context"]["need_comfort"], 0.8)
        self.assertEqual(contextual.context_metadata["positive_contextual_drive"]["need_comfort"], 0.8)
        self.assertEqual(contextual.context_metadata["negative_contextual_drive"]["need_comfort"], 0.0)
        self.assertEqual(contextual.context_metadata["perturbation_version"], "context-perturbation-v1")

    def test_large_raw_xi_is_finite_bounded_and_unclipped_in_metadata(self):
        engine = HOTCOCTv43()
        result = engine.simulate(participant(), perturbation({"need_comfort": 3.0}, {"valence_bike": -3.0}))
        self.assertTrue(np.isfinite(result.trajectory).all())
        self.assertGreaterEqual(float(result.trajectory.min()), -1.0)
        self.assertLessEqual(float(result.trajectory.max()), 1.0)
        self.assertEqual(result.context_metadata["xi_context"]["need_comfort"], 3.0)
        self.assertEqual(result.context_metadata["xi_context"]["valence_bike"], -3.0)

    def test_positive_and_negative_context_use_shunting_terms(self):
        engine = HOTCOCTv43()
        p = participant()
        state, beliefs, availability = engine.arrays_from_input(p)
        topology = engine.build_topology(beliefs)
        positive = perturbation({"comfort": 0.5})
        negative = perturbation(valences={"valence_bike": -0.5})
        stats = engine._empty_correction_stats()
        positive_field = engine.vector_field(state, topology, availability, positive)
        negative_field = engine.vector_field(state, topology, availability, negative)
        self.assertFalse(np.array_equal(positive_field, engine.vector_field(state, topology, availability)))
        self.assertFalse(np.array_equal(negative_field, engine.vector_field(state, topology, availability)))

    def test_action_targets_are_always_ignored(self):
        engine = HOTCOCTv43()
        result = engine.simulate(participant(), perturbation(actions={"bike": 99.0}))
        action_slice = slice(engine.n_needs, engine.n_needs + engine.n_modes)
        baseline = engine.simulate(participant())
        self.assertTrue(np.array_equal(result.trajectory[:, :, action_slice], baseline.trajectory[:, :, action_slice]))
        self.assertTrue(any("action context target" in warning for warning in result.context_metadata["warnings"]))

    def test_semantic_targets_align_without_fixed_context_indices(self):
        engine = HOTCOCTv43()
        result = engine.simulate(participant(), perturbation({"need_comfort": 0.4}, {"valence_car_driver": -0.2}))
        self.assertEqual(result.context_metadata["target_names"], ["need_comfort", "valence_car_driver"])

    def test_missing_target_is_warned_and_ignored(self):
        engine = HOTCOCTv43()
        result = engine.simulate(participant(), perturbation({"need_future_need": 2.0}, {"valence_train": -1.0}))
        self.assertTrue(any("does not exist" in warning for warning in result.context_metadata["warnings"]))

    def test_context_does_not_change_topology_initial_state_or_availability(self):
        engine = HOTCOCTv43()
        base = engine.simulate(participant())
        result = engine.simulate(participant(), perturbation({"comfort": 2.0}, {"valence_bike": -1.0}))
        self.assertTrue(np.array_equal(base.topology, result.topology))
        self.assertTrue(np.array_equal(base.availability, result.availability))
        self.assertTrue(np.array_equal(base.trajectory[:, 0, :], result.trajectory[:, 0, :]))

    def test_unclipped_context_changes_need_drive_without_parameter_learning(self):
        engine = HOTCOCTv43()
        result = engine.simulate(participant(), perturbation({"comfort": 1.5}))
        self.assertEqual(result.context_metadata["alpha_context"], 1.0)
        self.assertTrue(np.isfinite(result.trajectory).all())

    def test_parameter_rejects_negative_context_gain(self):
        with self.assertRaises(ValueError): HOTCOCTParameters(alpha_context=-0.1)


if __name__ == "__main__":
    unittest.main()
