import unittest
from companion_interpretation import interpretation_metrics, metric_changes, personal_deliberation_profile
from contextual_xai import build_contextual_xai
from contextual_xai_narrator import NarrationRequest, digital_companion_evidence
from tests.test_contextual_xai import deliberation


class InterpretationTests(unittest.TestCase):
    def test_personal_profile_turns_hotco_output_into_coaching_story_without_raw_passport(self):
        profile = personal_deliberation_profile(
            baseline_tendency="car", contextual_tendency="bike", ambiguity_state="CLEAR",
            leader_changed=True, selected_route_modes=["car_driver"],
            contextual_metrics={"competition": {"main_rival": "car"}, "terminal_action_support": {"bike": .7, "car": .5}},
            active_need_values={"autonomy": .8, "cost": .6, "comfort": .3},
            supporting_constraints=[{"need": "autonomy", "signed_input": .4}],
            opposing_constraints=[{"need": "comfort", "signed_input": -.2}],
        )
        self.assertEqual(profile["recommended_mode"], "bike")
        self.assertEqual(profile["selected_route_alignment"], "DIFFERENT_FROM_RECOMMENDATION")
        self.assertEqual(profile["most_active_needs"][0]["meaning"], "having control over the trip")
        self.assertEqual(profile["main_alternative"], "car")
        for forbidden in ("passport", "questionnaire", "coordinates", "trajectory"):
            self.assertNotIn(forbidden, str(profile).lower())

    def test_near_tie_profile_does_not_construct_a_winner_recommendation(self):
        profile = personal_deliberation_profile(
            baseline_tendency="bike", contextual_tendency="car", ambiguity_state="NEAR_TIE",
            leader_changed=True, selected_route_modes=["car"], contextual_metrics={},
            active_need_values={}, supporting_constraints=[], opposing_constraints=[],
        )
        self.assertEqual(profile["recommendation_status"], "NO_CLEAR_CHOICE")
        self.assertIsNone(profile["recommended_mode"])
        self.assertEqual(profile["narrative_plan"][0], "acknowledge_no_clear_choice")

    def test_process_families_reach_explanation_without_private_payload(self):
        summary = {
            "process_diagnostics": {
                "competition": {"main_rival": "bike", "co_dominance_fraction": .5},
                "leadership": {"winner_switch_count": 2},
                "patterns": ["PERSISTENT_RIVALRY"],
                "support_dynamics": {"by_mode": {"bike": {"available": True, "cognitive_signed_input": {"terminal": .4}, "passport": "private"}}},
                "mixed_cognitive_support": {"by_mode": {"bike": {"top_supporters_terminal": [{"need": "comfort", "signed_input": .4, "coordinates": [1, 2]}]}}},
            },
            "trajectory": [[1, 2]], "H_NEEDS": 99, "passport": "private",
        }
        result = interpretation_metrics(summary)
        self.assertEqual(result["leadership"]["winner_switch_count"], 2)
        self.assertEqual(result["competition"]["main_rival"], "bike")
        self.assertIn("stayed close", result["plain_language_patterns"][0])
        for forbidden in ("passport", "coordinates", "trajectory", "H_NEEDS"):
            self.assertNotIn(forbidden, str(result))

    def test_metrics_invalidate_evidence_and_reach_minimized_provider_prompt(self):
        payload = deliberation()
        before = build_contextual_xai(payload, "r1")
        payload["candidate_results"][0]["hotco"]["process_diagnostics"]["leadership"] = {"winner_switch_count": 3}
        after = build_contextual_xai(payload, "r1")
        self.assertNotEqual(before["evidence_version"], after["evidence_version"])
        request = NarrationRequest.from_dict({"route_id": "r1", "minimized_evidence": after["minimized_evidence"]})
        self.assertEqual(digital_companion_evidence(request)["deliberation_metrics"]["contextual"]["leadership"]["winner_switch_count"], 3)

    def test_missing_metrics_remain_absent(self):
        result = interpretation_metrics({})
        self.assertEqual(result["competition"], {})
        self.assertEqual(result["plain_language_patterns"], [])

    def test_f3_provider_receives_combined_drive_not_legacy_mapping_as_applied_force(self):
        payload = deliberation()
        perturbation = payload["candidate_results"][0]["context_perturbation"]
        payload["candidate_results"][0]["hotco"]["context_metadata"] = {"context_model": "F3_NEED_ONLY_V1"}
        perturbation["perturbation_version"] = "f3-need-only-v1"
        perturbation["contributions"][0]["target_node"] = "comfort"
        perturbation["need_perturbations"] = {"comfort": .012}
        result = build_contextual_xai(payload, "r1")
        request = NarrationRequest.from_dict({"route_id": "r1", "minimized_evidence": result["minimized_evidence"]})
        driver = digital_companion_evidence(request)["deterministic_drivers"][0]
        self.assertEqual(driver["combined_target_drive"], .012)
        self.assertEqual(driver["contribution_kind"], "pre_bounded_mapping")
        self.assertNotIn("xi", driver)
        self.assertNotIn("(0.2)", " ".join(result["summary"]))

    def test_metric_change_direction_comes_from_backend(self):
        self.assertEqual(metric_changes(
            {"competition": {"final_margin": .2, "terminal_entropy": .6}},
            {"competition": {"final_margin": .3, "terminal_entropy": .5}},
        ), {"final_margin": "increased", "terminal_entropy": "decreased"})
