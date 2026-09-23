from __future__ import annotations

import json
from pathlib import Path
import unittest

import numpy as np

from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import InputValidationError, parse_participant_input
from passport_xai import build_cognitive_passport
from xai_diagnostics import build_xai_diagnostics

try:
    from main import app
except ModuleNotFoundError:  # Local math-only checks can run before Flask is installed.
    app = None


SERVICE_DIR = Path(__file__).resolve().parents[1]


def example_payload():
    return json.loads((SERVICE_DIR / "example_request.json").read_text(encoding="utf-8"))


class InputContractTests(unittest.TestCase):
    def test_complete_questionnaire_has_exact_observed_counts(self):
        participant = parse_participant_input(example_payload())
        self.assertEqual(len(participant.needs), 11)
        self.assertEqual(len(participant.beliefs), 4)
        self.assertTrue(all(len(row) == 11 for row in participant.beliefs))
        self.assertEqual(len(participant.valences), 4)
        self.assertEqual(len(participant.availability), 4)

    def test_schema_21_rejects_frequency_field(self):
        payload = example_payload()
        payload["responses"]["frequencies"] = {
            "car": 3,
            "bike": 4,
            "pt": 2,
            "walk": 4,
        }
        with self.assertRaises(InputValidationError) as context:
            parse_participant_input(payload)
        self.assertIn("unknown fields: frequencies", str(context.exception))

    def test_availability_is_explicit_user_declared_and_auditable(self):
        payload = example_payload()
        payload["responses"]["availability"]["car"] = False
        participant = parse_participant_input(payload)
        self.assertEqual(participant.availability, [0.0, 1.0, 1.0, 1.0])
        provenance = participant.availability_provenance
        self.assertEqual(provenance["policy"], "explicit_user_declared_access_only")
        self.assertFalse(provenance["external_provider_data_used"])
        self.assertFalse(provenance["frequency_or_preference_inference_used"])
        self.assertEqual(
            provenance["by_mode"]["car"]["source"], "user_declared_access"
        )
        self.assertEqual(provenance["by_mode"]["car"]["hotco_action_gate"], 0)

    def test_missing_belief_is_rejected_without_substitution(self):
        payload = example_payload()
        del payload["responses"]["beliefs"]["bike"]["env"]
        with self.assertRaises(InputValidationError) as context:
            parse_participant_input(payload)
        self.assertIn("pro_env", str(context.exception))

    def test_unversioned_metadata_channel_is_rejected(self):
        payload = example_payload()
        payload["profile"] = {"synthetic_segment": 0.75}
        with self.assertRaises(InputValidationError) as context:
            parse_participant_input(payload)
        self.assertIn("unknown fields: profile", str(context.exception))

    def test_api_returns_422_and_explicit_no_imputation_flag(self):
        if app is None:
            self.skipTest("Flask is not installed in this math-only environment")
        payload = example_payload()
        del payload["responses"]["valences"]["walk"]
        with app.test_client() as client:
            response = client.post("/api/dyconet", json=payload)
        self.assertEqual(response.status_code, 422)
        self.assertFalse(response.get_json()["imputation_performed"])


class FormalDynamicsTests(unittest.TestCase):
    def setUp(self):
        self.engine = HOTCOCTv43()
        self.participant = parse_participant_input(example_payload())

    def test_actions_start_exactly_at_zero_and_topology_is_symmetric(self):
        state, beliefs, availability = self.engine.arrays_from_input(self.participant)
        actions = state[:, self.engine.n_needs : self.engine.n_needs + self.engine.n_modes]
        self.assertTrue(np.array_equal(actions, np.zeros_like(actions)))
        topology = self.engine.build_topology(beliefs)
        self.assertTrue(np.allclose(topology, np.transpose(topology, (0, 2, 1)), atol=0.0, rtol=0.0))

    def test_net_first_cancellation_occurs_before_rectification(self):
        state = np.zeros((1, self.engine.n_nodes), dtype=np.float64)
        state[0, 0] = 1.0
        state[0, 1] = 1.0
        topology = np.zeros((1, self.engine.n_nodes, self.engine.n_nodes), dtype=np.float64)
        action = self.engine.n_needs
        topology[0, action, 0] = 1.0
        topology[0, action, 1] = -1.0
        availability = np.ones((1, self.engine.n_modes), dtype=np.float64)
        derivative = self.engine.vector_field(state, topology, availability)
        self.assertEqual(float(derivative[0, action]), 0.0)

    def test_negative_valence_enters_the_action_inhibitory_channel(self):
        state = np.zeros((1, self.engine.n_nodes), dtype=np.float64)
        action = self.engine.n_needs
        valence = self.engine.n_needs + self.engine.n_modes
        state[0, action] = 0.5
        state[0, valence] = -1.0
        availability = np.ones((1, self.engine.n_modes), dtype=np.float64)
        topology = np.zeros((1, self.engine.n_nodes, self.engine.n_nodes), dtype=np.float64)
        topology[0, action, valence] = 0.5
        inhibited = self.engine.vector_field(state, topology, availability)[0, action]
        topology.fill(0.0)
        decay_only = self.engine.vector_field(state, topology, availability)[0, action]
        self.assertLess(float(inhibited), float(decay_only))

    def test_unavailable_action_is_exactly_zero_for_the_full_trajectory(self):
        payload = example_payload()
        payload["responses"]["availability"]["car"] = False
        participant = parse_participant_input(payload)
        result = self.engine.simulate(participant)
        car = result.trajectory[0, :, self.engine.n_needs]
        self.assertTrue(np.array_equal(car, np.zeros_like(car)))

    def test_reference_trajectory_respects_every_declared_bound(self):
        result = self.engine.simulate(self.participant)
        trajectory = result.trajectory[0]
        needs = trajectory[:, : self.engine.n_needs]
        actions = trajectory[:, self.engine.n_needs : self.engine.n_needs + self.engine.n_modes]
        valences = trajectory[:, self.engine.n_needs + self.engine.n_modes :]
        self.assertGreaterEqual(float(needs.min()), 0.0)
        self.assertLessEqual(float(needs.max()), 1.0)
        self.assertGreaterEqual(float(actions.min()), 0.0)
        self.assertLessEqual(float(actions.max()), 1.0)
        self.assertGreaterEqual(float(valences.min()), -1.0)
        self.assertLessEqual(float(valences.max()), 1.0)
        self.assertEqual(trajectory.shape[0], 2001)

    def test_consistent_action_permutation_is_equivariant(self):
        original = self.engine.simulate(self.participant).trajectory[0]
        payload = example_payload()
        for block in ("beliefs", "valences", "availability"):
            values = payload["responses"][block]
            values["car"], values["bike"] = values["bike"], values["car"]
        permuted_participant = parse_participant_input(payload)
        permuted = self.engine.simulate(permuted_participant).trajectory[0]

        expected = original.copy()
        action_start = self.engine.n_needs
        valence_start = self.engine.n_needs + self.engine.n_modes
        expected[:, [action_start, action_start + 1]] = expected[:, [action_start + 1, action_start]]
        expected[:, [valence_start, valence_start + 1]] = expected[:, [valence_start + 1, valence_start]]
        self.assertTrue(np.allclose(permuted, expected, atol=1e-10, rtol=1e-10))


class XaiDiagnosticsTests(unittest.TestCase):
    def setUp(self):
        self.engine = HOTCOCTv43()
        self.participant = parse_participant_input(example_payload())
        self.result = self.engine.simulate(self.participant)
        self.xai = build_xai_diagnostics(self.participant, self.result, self.engine)

    def test_xai_metrics_are_bounded_and_model_grounded(self):
        competition = self.xai["competition"]
        self.assertIn(competition["winner"], ("car", "bike", "pt", "walk"))
        self.assertGreaterEqual(competition["co_dominance_fraction"], 0.0)
        self.assertLessEqual(competition["co_dominance_fraction"], 1.0)
        self.assertGreaterEqual(competition["winner_lead_fraction"], 0.0)
        self.assertLessEqual(competition["winner_lead_fraction"], 1.0)
        self.assertNotIn("_start_index", competition)
        self.assertEqual(self.xai["scope"], "simulation_process_explanation")

    def test_winner_has_support_friction_and_driver_diagnostics(self):
        winner = self.xai["competition"]["winner"]
        support = self.xai["support_dynamics"]["by_mode"][winner]
        mixed = self.xai["mixed_cognitive_support"]["by_mode"][winner]
        self.assertTrue(support["available"])
        self.assertIn("terminal", support["cognitive_signed_input"])
        self.assertIn("terminal", support["affective_signed_input"])
        self.assertGreaterEqual(support["cognitive_affective_friction"]["terminal"], 0.0)
        self.assertLessEqual(support["cognitive_affective_friction"]["terminal"], 1.0)
        self.assertGreaterEqual(mixed["mixed_support_index"]["terminal"], 0.0)
        self.assertLessEqual(mixed["mixed_support_index"]["terminal"], 1.0)
        self.assertLessEqual(len(mixed["top_supporters_terminal"]), 3)
        self.assertLessEqual(len(mixed["top_inhibitors_terminal"]), 3)

    def test_unavailable_mode_is_not_given_explanatory_support_metrics(self):
        payload = example_payload()
        payload["responses"]["availability"]["car"] = False
        participant = parse_participant_input(payload)
        result = self.engine.simulate(participant)
        xai = build_xai_diagnostics(participant, result, self.engine)
        self.assertEqual(xai["support_dynamics"]["by_mode"]["car"], {"available": False})
        self.assertEqual(xai["mixed_cognitive_support"]["by_mode"]["car"], {"available": False})
        self.assertNotEqual(xai["competition"]["winner"], "car")

    def test_xai_explicitly_carries_interpretation_guardrails(self):
        guardrails = " ".join(self.xai["interpretation_guardrails"])
        self.assertIn("subjective experience", guardrails)
        self.assertIn("not causal percentage", guardrails)
        self.assertIn("not human reaction time", guardrails)


class PassportTests(unittest.TestCase):
    def test_passport_v20_retains_android_contract_and_adds_versioned_xai(self):
        participant = parse_participant_input(example_payload())
        engine = HOTCOCTv43()
        output = build_cognitive_passport(
            participant, engine.simulate(participant), engine
        )["cognitive_passport"]
        self.assertEqual(output["schema_version"], "2.0")
        provenance = output["input_provenance"]
        self.assertEqual(provenance["source_schema"], "hotco_ct_input_2.1")
        self.assertFalse(provenance["imputation_used"])
        self.assertEqual(provenance["observed_counts"]["beliefs"], 44)
        availability_provenance = provenance["availability_resolution"]
        self.assertEqual(
            availability_provenance["policy"],
            "explicit_user_declared_access_only",
        )
        self.assertFalse(availability_provenance["external_provider_data_used"])
        self.assertNotIn("questionnaire_references", output)

        deliberation = output["deliberation"]
        self.assertEqual(deliberation["final_choice"], deliberation["terminal_tendency"].upper())
        self.assertEqual(deliberation["probabilities"], deliberation["comparative_readout"])
        self.assertAlmostEqual(sum(deliberation["comparative_readout"].values()), 1.0, places=9)
        self.assertNotIn("reaction_time_seconds", deliberation)
        self.assertIn("xai_diagnostics", output)
        self.assertEqual(output["xai_diagnostics"]["schema_version"], "1.0")
        self.assertEqual(
            output["process_diagnostics"]["action_process"]["winner_switch_count"],
            output["xai_diagnostics"]["leadership"]["winner_switch_count"],
        )


if __name__ == "__main__":
    unittest.main()
