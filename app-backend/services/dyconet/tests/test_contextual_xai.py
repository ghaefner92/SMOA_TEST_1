from __future__ import annotations

import json
import unittest
from unittest.mock import patch

from contextual_xai import XAI_SCHEMA_VERSION, build_contextual_xai
from context_facts import build_context_facts
from main import app


def deliberation(*, changed: bool = True) -> dict:
    winner = "pt" if changed else "bike"
    return {
        "schema_version": "contextual-deliberation-response-v1",
        "search_id": "s1",
        "model_version": "hotco_ct_v4.3-online.1",
        "normalization_version": "context-normalization-v1",
        "perturbation_version": "context-perturbation-v1",
        "baseline": {
            "winner": "bike",
            "probabilities": {"bike": .7, "pt": .3},
            "final_action_activations": {"bike": .4, "pt": .2},
            "process_diagnostics": {"competition": {"final_margin": .2, "terminal_entropy": .6}},
        },
        "candidate_results": [{
            "route_id": "r1",
            "hotco": {
                "winner": winner,
                "probabilities": {"bike": .3, "pt": .7},
                "final_action_activations": {"bike": .2, "pt": .5},
                "process_diagnostics": {
                    "competition": {"final_margin": .3, "terminal_entropy": .5},
                    "mixed_cognitive_support": {"by_mode": {winner: {"top_supporters_terminal": [{"need": "comfort", "signed_input": .4}], "top_inhibitors_terminal": [{"need": "cost", "signed_input": -.1}]}}},
                },
            },
            "route_context": {"contextual_data_completeness": {"spatial_matching": "segment_midpoint_near_radius"}},
            "context_perturbation": {
                "perturbation_version": "context-perturbation-v1",
                "missing_stressors": ["traffic"],
                "ignored_context_variables": ["wind"],
                "warnings": ["traffic unknown"],
                "data_coverage": {"route": {"rain": 1.0}},
                "effective_exposures": [{"stressor": "rain", "source_scope": "route", "mode": None, "context_value": .8, "tolerance": .75, "effective_value": .2}],
                "contributions": [{"stressor": "rain", "target_node": "need_comfort", "target_family": "need", "mode": None, "effective_exposure": .2, "coefficient": 1.0, "contribution": .2, "source_scope": "route", "source_normalization_metadata": {"method": "engineering"}, "tolerance_source": "passport"}],
            },
            "warnings": [],
        }],
    }


class ContextualXAITests(unittest.TestCase):
    def test_near_tie_is_propagated_from_existing_competition_diagnostic(self):
        payload = deliberation()
        payload["candidate_results"][0]["hotco"]["process_diagnostics"]["competition"].update({"co_dominance_fraction": 0.5, "persistent_rivalry_fraction": 0.25, "co_dominance_threshold": 0.01})
        result = build_contextual_xai(payload, "r1")
        self.assertEqual(result["ambiguity_state"], "NEAR_TIE")
        self.assertEqual(result["minimized_evidence"]["ambiguity_state"], "NEAR_TIE")
        self.assertTrue(any("similar support" in line for line in result["summary"]))
    def test_context_facts_use_normalized_stressors_without_route_stressors_alias(self):
        route_context = {
            "requested_at": "2026-09-12T10:15:00+00:00",
            "raw_observations": [{"variable": "temperature", "numeric_value": 18.0, "unit": "CEL", "timestamp": "2026-09-12T10:00:00+00:00", "source_metadata": {"source_contract_confidence": "CONFIRMED", "normalization_eligible": True}}],
            "normalized_stressors": [{"name": "darkness", "value": 0.0, "normalization_metadata": {"source_observations": [{"timestamp": "2026-09-12T10:00:00+00:00"}]}}],
        }
        facts = build_context_facts(route_context)
        self.assertEqual([(item["variable"], item["display_value"], item["status"]) for item in facts], [("temperature", "18 °C", "AVAILABLE"), ("daylight", "Daylight", "AVAILABLE"), ("crowding", "No data", "UNKNOWN"), ("wind", "No data", "NO_DATA"), ("rain", "No data", "NO_DATA")])
        self.assertEqual(facts[0]["freshness"], "CURRENT")
        payload = deliberation()
        payload["candidate_results"][0]["route_context"] = {**route_context, "context_facts": facts}
        explanation = build_contextual_xai(payload, "r1")
        self.assertEqual(explanation["minimized_evidence"]["context_facts"], facts)

    def test_temperature_without_unit_remains_visible_but_unconfirmed(self):
        route_context = {
            "requested_at": "2026-09-12T10:05:00+00:00",
            "raw_observations": [{
                "variable": "temperature", "numeric_value": 19.5,
                "unit": None, "timestamp": "2026-09-12T10:00:00+00:00",
                "source_metadata": {"source_contract_confidence": "WEAKLY_INFERRED", "normalization_eligible": False},
            }],
            "normalized_stressors": [],
        }
        fact = next(item for item in build_context_facts(route_context) if item["variable"] == "temperature")
        self.assertEqual(fact["display_value"], "19.5 (unit unconfirmed)")
        self.assertEqual(fact["status"], "UNKNOWN")
        self.assertFalse(fact["eligible_for_model"])
    def test_deterministic_minimized_evidence_and_leader_change(self):
        first = build_contextual_xai(deliberation(), "r1")
        second = build_contextual_xai(deliberation(), "r1")
        self.assertEqual(first, second)
        self.assertEqual(first["schema_version"], XAI_SCHEMA_VERSION)
        self.assertTrue(first["evidence_version"].startswith("sha256:"))
        self.assertNotIn("evidence_id", first)
        self.assertTrue(first["leader_changed"])
        self.assertEqual(first["contextual_drivers"]["top_positive"][0]["xi"], .2)
        self.assertEqual(first["facts"][0]["provenance"]["tolerance_source"], "passport")
        self.assertEqual(first["data_quality"]["context_status"], "PARTIAL")
        self.assertTrue(first["counterfactual_readiness"]["counterfactual_ready"])
        self.assertFalse(first["counterfactual_readiness"]["computed"])
        self.assertNotIn("participant", json.dumps(first))
        self.assertNotIn("api_key", json.dumps(first).lower())

    def test_no_change_and_unknown_context_do_not_invent_driver(self):
        payload = deliberation(changed=False)
        payload["candidate_results"][0]["context_perturbation"]["contributions"] = []
        payload["candidate_results"][0]["context_perturbation"]["effective_exposures"] = [{"stressor": "rain", "effective_value": None, "tolerance": None}]
        result = build_contextual_xai(payload, "r1")
        self.assertFalse(result["leader_changed"])
        self.assertEqual(result["contextual_drivers"]["all"], [])
        self.assertEqual(result["data_quality"]["context_status"], "UNAVAILABLE")

    def test_endpoint_explains_existing_result_without_new_deliberation(self):
        with app.test_client() as client:
            response = client.post("/api/dyconet/contextual-explanation", json={"deliberation": deliberation(), "route_id": "r1"})
        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.get_json()["route_id"], "r1")

    def test_narration_endpoint_rebuilds_canonical_evidence_on_backend(self):
        captured = {}

        def narrate(payload):
            captured.update(payload)
            return {
                "route_id": payload["route_id"],
                "evidence_version": payload["evidence_version"],
                "status": "available",
            }

        with patch("main.narrate_minimized_evidence", side_effect=narrate):
            with app.test_client() as client:
                response = client.post(
                    "/api/dyconet/contextual-explanation/narrate",
                    json={
                        "deliberation": deliberation(),
                        "route_id": "r1",
                        "language": "en",
                        "explanation_type": "WHY_THIS_TENDENCY",
                        # A transported value cannot override backend identity.
                        "evidence_version": "sha256:frontend-copy",
                    },
                )

        expected = build_contextual_xai(deliberation(), "r1")
        self.assertEqual(response.status_code, 200)
        self.assertEqual(captured["minimized_evidence"], expected["minimized_evidence"])
        self.assertEqual(captured["evidence_version"], expected["evidence_version"])
        self.assertNotEqual(captured["evidence_version"], "sha256:frontend-copy")

    def test_why_not_mode_comparison_uses_existing_candidate(self):
        payload = deliberation()
        payload["candidate_results"].append({"route_id": "r2", "hotco": {"winner": "bike", "probabilities": {"bike": .6}, "final_action_activations": {"bike": .3}}})
        result = build_contextual_xai(payload, "r1", "bike")
        self.assertEqual(result["why_not_mode"]["mode"], "bike")
        self.assertIn("probability_difference", result["why_not_mode"])


if __name__ == "__main__":
    unittest.main()
