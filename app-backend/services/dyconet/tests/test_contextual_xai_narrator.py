from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
import threading
import unittest
from unittest.mock import patch

from contextual_xai_narrator import (
    ContextualXAINarrator,
    ContextFact,
    DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION,
    NARRATION_SCHEMA_VERSION,
    digital_companion_evidence,
    evidence_version,
    NarrationError,
    NarrationRequest,
    deterministic_narration,
    digital_companion_contract_status,
    narrate_minimized_evidence,
    validate_generated_output,
)


def evidence(status="FULL"):
    return {
        "xai_schema_version": "contextual-xai-v1",
        "route_id": "r1",
        "baseline_tendency": "bike",
        "contextual_tendency": "pt",
        "leader_changed": True,
        "baseline_probabilities": {"bike": .7, "pt": .3},
        "contextual_probabilities": {"bike": .3, "pt": .7},
        "top_positive_context_drivers": [{"stressor": "rain", "target": "need_comfort", "xi": .2}],
        "top_negative_context_drivers": [{"stressor": "rain", "target": "valence_bike", "mode": "bike", "xi": -.2}],
        "data_quality": {"context_status": status, "missing_stressors": ["traffic"] if status == "PARTIAL" else [], "missing_tolerances": []},
        "warnings": [],
        "versions": {"model": "hotco", "normalization": "norm", "perturbation": "pert"},
    }


class NarratorTests(unittest.TestCase):
    @staticmethod
    def personal_evidence(mode="bike", status="RECOMMENDED"):
        data = evidence()
        data["ambiguity_state"] = "CLEAR" if status == "RECOMMENDED" else "NEAR_TIE"
        data["personal_deliberation"] = {
            "recommendation_status": status,
            "recommended_mode": mode if status == "RECOMMENDED" else None,
            "selected_route_alignment": "ALIGNED",
        }
        return data

    def test_human_recommendation_must_match_deterministic_leader(self):
        request = self.request(minimized_evidence=self.personal_evidence("bike"))
        valid = {"title": "My take", "summary": "My recommendation is cycling.", "context_effect": "The available surroundings are considered.", "model_reasoning": ["Your need for comfort pulls in another direction."], "uncertainty": "The leading options are not equally supported.", "data_quality": "Some context is unavailable."}
        self.assertTrue(validate_generated_output(valid, request).generated_by_llm)
        with self.assertRaisesRegex(NarrationError, "recommendation mismatch"):
            validate_generated_output({**valid, "summary": "My recommendation is driving."}, request)

    def test_human_recommendation_may_explain_a_rival_after_the_recommendation(self):
        request = self.request(minimized_evidence=self.personal_evidence("car"))
        output = {
            "title": "My take",
            "summary": "My recommendation is driving. Cycling still speaks to your wish to stay active.",
            "context_effect": "The available surroundings are considered.",
            "model_reasoning": ["Your need for comfort pulls in another direction."],
            "uncertainty": "The leading options are not equally supported.",
            "data_quality": "Some context is unavailable.",
        }
        self.assertTrue(validate_generated_output(output, request).generated_by_llm)

    def test_near_tie_rejects_unconditional_personal_recommendation(self):
        request = self.request(minimized_evidence=self.personal_evidence(status="NO_CLEAR_CHOICE"))
        invalid = {"title": "My take", "summary": "My recommendation is cycling.", "context_effect": "The options remain close.", "model_reasoning": [], "uncertainty": "Your priorities are divided.", "data_quality": "Some context is unavailable."}
        with self.assertRaisesRegex(NarrationError, "unresolved deliberation"):
            validate_generated_output(invalid, request)

    def test_follow_up_explanation_types_use_same_evidence_identity(self):
        versions = {
            self.request(explanation_type=kind).evidence_version
            for kind in ("WHY_THIS_TENDENCY", "WHY_NOT_MODE", "WHAT_MATTERS", "WHAT_CHANGED")
        }
        self.assertEqual(len(versions), 1)

    def test_content_repair_is_bounded_and_does_not_resend_provider_output(self):
        valid = {"title": "Evidence", "summary": "The supplied tendency is described.", "context_effect": "Only supplied context is described.", "model_reasoning": [], "uncertainty": "Some information is unavailable.", "data_quality": "Context is partial."}
        for first_invalid, second_invalid, expected_calls in ((False, False, 1), (True, False, 2), (True, True, 2)):
            with self.subTest(first_invalid=first_invalid, second_invalid=second_invalid):
                calls = []
                class Provider:
                    def narrate(self, prompt):
                        calls.append(prompt)
                        invalid = first_invalid if len(calls) == 1 else second_invalid
                        return {**valid, "summary": "Unsupported observation 999999."} if invalid else valid
                request = self.request()
                result = ContextualXAINarrator(provider=Provider()).narrate(request)
                self.assertEqual(len(calls), expected_calls)
                self.assertEqual(result.generated_by_llm, not (first_invalid and second_invalid))
                self.assertEqual(result.evidence_version, request.evidence_version)
                if len(calls) == 2:
                    self.assertEqual(calls[0]["evidence"], calls[1]["evidence"])
                    self.assertIn("repair_constraints", calls[1])
                    self.assertNotIn("999999", str(calls[1]))
                if not result.generated_by_llm:
                    self.assertEqual(result.summary, "")

    def test_transient_transport_failure_gets_one_bounded_retry(self):
        calls = []
        class Provider:
            def narrate(self, prompt):
                calls.append(prompt)
                if len(calls) == 1:
                    raise TimeoutError()
                return {
                    "title": "Evidence",
                    "summary": "The supplied tendency is described.",
                    "context_effect": "Only supplied context is described.",
                    "model_reasoning": [],
                    "uncertainty": "Some information is unavailable.",
                    "data_quality": "Context is partial.",
                }
        result = ContextualXAINarrator(provider=Provider()).narrate(self.request())
        self.assertEqual(len(calls), 2)
        self.assertTrue(result.generated_by_llm)

    def test_non_transient_provider_failure_is_not_retried(self):
        calls = []
        class Provider:
            def narrate(self, prompt):
                calls.append(prompt)
                raise ValueError("invalid provider configuration")
        result = ContextualXAINarrator(provider=Provider()).narrate(self.request())
        self.assertEqual(len(calls), 1)
        self.assertFalse(result.generated_by_llm)

    def test_overlapping_identical_requests_share_one_provider_call(self):
        calls = []
        entered = threading.Event()
        release = threading.Event()
        class Provider:
            def narrate(self, prompt):
                calls.append(prompt)
                entered.set()
                if not release.wait(timeout=2):
                    raise TimeoutError("test release did not arrive")
                return {
                    "title": "Evidence",
                    "summary": "The supplied tendency is described.",
                    "context_effect": "Only supplied context is described.",
                    "model_reasoning": [],
                    "uncertainty": "Some information is unavailable.",
                    "data_quality": "Context is partial.",
                }

        narrator = ContextualXAINarrator(provider=Provider())
        payload = {
            "route_id": "r1",
            "language": "en",
            "explanation_type": "WHY_THIS_TENDENCY",
            "minimized_evidence": evidence(),
        }
        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(narrate_minimized_evidence, payload, narrator=narrator)
            self.assertTrue(entered.wait(timeout=2))
            second = pool.submit(narrate_minimized_evidence, payload, narrator=narrator)
            release.set()
            self.assertTrue(first.result(timeout=2)["generated_by_llm"])
            self.assertTrue(second.result(timeout=2)["generated_by_llm"])
        self.assertEqual(len(calls), 1)

    def request(self, **kwargs):
        data = {"route_id": "r1", "language": "en", "explanation_type": "WHY_THIS_TENDENCY", "minimized_evidence": evidence()}
        data.update(kwargs)
        return NarrationRequest.from_dict(data)

    def test_missing_key_returns_deterministic_fallback(self):
        result = ContextualXAINarrator(api_key="", model="").narrate(self.request())
        self.assertFalse(result.generated_by_llm)
        self.assertTrue(result.evidence_version.startswith("sha256:"))
        self.assertEqual(result.availability_status, "DIGITAL_COMPANION_UNAVAILABLE")
        self.assertEqual(result.summary, "")

    def test_provider_receives_the_shared_identity_free_prompt(self):
        captured = {}
        class Provider:
            def narrate(self, prompt):
                captured["prompt"] = prompt
                return {"title": "Evidence", "summary": "The supplied Digital Companion tendency changed.", "context_effect": "Rain contributed to the supplied nodes.", "model_reasoning": [], "uncertainty": "Reported uncertainty applies.", "data_quality": "Partial context is shown."}
        result = ContextualXAINarrator(provider=Provider()).narrate(self.request())
        self.assertTrue(result.generated_by_llm)
        self.assertEqual(captured["prompt"]["evidence"]["route_id"], "r1")
        self.assertNotIn("evidence_version", captured["prompt"])
        self.assertNotIn("passport", str(captured["prompt"]).lower())

    def test_string_model_reasoning_is_normalized_to_structured_list(self):
        request = self.request()
        result = validate_generated_output({
            "title": "Evidence",
            "summary": "The supplied tendency is described.",
            "context_effect": "No additional claim is made.",
            "model_reasoning": "The explanation follows deterministic evidence.",
            "uncertainty": "Only supplied context is described.",
            "data_quality": "Context quality is reported.",
        }, request)
        self.assertEqual(result.model_reasoning, ("The explanation follows deterministic evidence.",))

    def test_ordinary_mode_alias_is_accepted_when_canonical_mode_is_in_evidence(self):
        raw = evidence()
        raw["contextual_tendency"] = "walk"
        request = self.request(minimized_evidence=raw)
        result = validate_generated_output({
            "title": "Evidence",
            "summary": "The walking tendency is described.",
            "context_effect": "No additional claim is made.",
            "model_reasoning": [],
            "uncertainty": "Only supplied context is described.",
            "data_quality": "Context quality is reported.",
        }, request)
        self.assertTrue(result.generated_by_llm)

    def test_timeout_or_malformed_provider_falls_back(self):
        calls = []
        class Provider:
            def narrate(self, request):
                calls.append(request)
                raise TimeoutError()
        result = ContextualXAINarrator(provider=Provider()).narrate(self.request())
        self.assertEqual(len(calls), 2)
        self.assertFalse(result.generated_by_llm)
        self.assertTrue(result.warnings)
        self.assertEqual(result.availability_status, "DIGITAL_COMPANION_UNAVAILABLE")

    def test_unsupported_claims_are_rejected(self):
        request = self.request()
        base = {"title": "x", "summary": "x", "context_effect": "x", "model_reasoning": [], "uncertainty": "x", "data_quality": "x"}
        for field, value in (("summary", "The optimal route is car."), ("context_effect", "Rain caused 0.99."), ("summary", "You should take the bike.")):
            candidate = dict(base, **{field: value})
            with self.assertRaises(NarrationError): validate_generated_output(candidate, request)

    def test_partial_context_and_missing_data_are_retained_by_fallback(self):
        request = self.request(minimized_evidence=evidence("PARTIAL"))
        result = deterministic_narration(request)
        self.assertIn("partially", result.uncertainty.lower())

    def test_explanation_types_and_german_are_explicit(self):
        for kind in ("WHY_THIS_TENDENCY", "WHY_NOT_MODE", "WHAT_MATTERS", "WHAT_CHANGED", "SUMMARY"):
            request = self.request(language="de", explanation_type=kind)
            self.assertEqual(deterministic_narration(request).language, "de")

    def test_unknown_context_has_no_driver_claim(self):
        data = evidence("UNAVAILABLE")
        data["top_positive_context_drivers"] = []
        data["top_negative_context_drivers"] = []
        result = deterministic_narration(self.request(minimized_evidence=data))
        self.assertNotIn("rain", result.context_effect.lower())

    def test_prompt_evidence_is_minimal_and_context_facts_preserve_unknown(self):
        raw = evidence()
        raw["context_facts"] = [{
            "variable": "crowding", "label": "Crowding", "display_value": "No data",
            "status": "UNKNOWN", "freshness": "UNKNOWN", "source_confidence": "UNKNOWN",
            "displayable": True, "eligible_for_model": False,
        }]
        payload = {"route_id": "r1", "language": "en", "explanation_type": "SUMMARY", "minimized_evidence": raw}
        contract = digital_companion_evidence(NarrationRequest.from_dict(payload))
        self.assertEqual(contract["schema_version"], DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION)
        self.assertEqual(contract["context_facts"][0]["status"], "UNKNOWN")
        self.assertNotIn("baseline_action_activations", contract)
        self.assertNotIn("passport", str(contract).lower())

    def test_context_fact_rejects_line_break_display_value(self):
        # Facts are supplied as display data and must stay a compact value.
        with self.assertRaises(NarrationError):
            ContextFact.from_mapping({"variable": "x", "label": "X", "display_value": "a\nb", "status": "AVAILABLE"})

    def test_development_mock_uses_the_same_validated_contract(self):
        with patch.dict("os.environ", {"IMIQ_XAI_LLM_PROVIDER": "mock"}, clear=False):
            result = ContextualXAINarrator(api_key="", model="").narrate(self.request())
        self.assertTrue(result.generated_by_llm)
        self.assertEqual(result.route_id, "r1")
        self.assertEqual(result.to_dict()["status"], "available")

    def test_health_contract_exposes_bounded_operational_policy(self):
        status = digital_companion_contract_status()
        self.assertEqual(status["provider_transport_retries"], 1)
        self.assertTrue(status["inflight_request_deduplication"])

    def test_provider_cannot_control_backend_evidence_identity(self):
        class Provider:
            def narrate(self, prompt):
                return {
                    "title": "Evidence",
                    "summary": "The supplied Digital Companion tendency changed.",
                    "context_effect": "Rain contributed to the supplied nodes.",
                    "model_reasoning": [],
                    "uncertainty": "Reported uncertainty applies.",
                    "data_quality": "Partial context is shown.",
                    "route_id": "attacker-route",
                    "schema_version": "attacker-schema",
                    "evidence_version": "sha256:attacker",
                    "evidence_id": "attacker-id",
                }
        request = self.request()
        result = ContextualXAINarrator(provider=Provider()).narrate(request)
        self.assertEqual(result.route_id, request.route_id)
        self.assertEqual(result.evidence_version, request.evidence_version)
        self.assertEqual(result.to_dict()["schema_version"], DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION)
        self.assertEqual(result.to_dict()["narration_schema_version"], NARRATION_SCHEMA_VERSION)
        self.assertNotIn("evidence_id", result.to_dict())

    def test_supplied_evidence_version_must_match_backend_hash(self):
        with self.assertRaises(NarrationError) as raised:
            NarrationRequest.from_dict({
                "route_id": "r1",
                "language": "en",
                "explanation_type": "SUMMARY",
                "evidence_version": "sha256:wrong",
                "minimized_evidence": evidence(),
            })
        self.assertEqual(raised.exception.code, "NARRATION_EVIDENCE_VERSION_MISMATCH")

    def test_evidence_version_is_canonical_and_changes_for_deterministic_evidence(self):
        request = self.request()
        first = digital_companion_evidence(request)
        reordered = dict(reversed(list(first.items())))
        self.assertEqual(evidence_version(first), evidence_version(reordered))
        changed = evidence()
        changed["leader_changed"] = False
        self.assertNotEqual(request.evidence_version, self.request(minimized_evidence=changed).evidence_version)

    def test_context_and_route_changes_invalidate_evidence_version(self):
        base = evidence()
        base["context_facts"] = [{"variable": "temperature", "label": "Temperature", "display_value": "18 °C", "status": "AVAILABLE", "displayable": True}]
        changed_temperature = evidence()
        changed_temperature["context_facts"] = [{"variable": "temperature", "label": "Temperature", "display_value": "24 °C", "status": "AVAILABLE", "displayable": True}]
        self.assertNotEqual(self.request(minimized_evidence=base).evidence_version, self.request(minimized_evidence=changed_temperature).evidence_version)

    def test_nickname_and_provider_metadata_do_not_change_evidence_version(self):
        base = evidence()
        decorated = {**base, "nickname": "Nova", "provider": "mock", "generated_by_llm": True}
        self.assertEqual(evidence_version(base), evidence_version(decorated))

    def test_provider_cannot_assert_clear_winner_for_near_tie(self):
        raw = evidence(); raw["ambiguity_state"] = "NEAR_TIE"
        request = self.request(minimized_evidence=raw)
        invalid = {"title": "x", "summary": "Cycling is clearly the leading tendency.", "context_effect": "x", "model_reasoning": [], "uncertainty": "x", "data_quality": "x"}
        with self.assertRaises(NarrationError): validate_generated_output(invalid, request)

    def test_near_tie_mock_can_state_no_clear_leading_tendency(self):
        raw = evidence()
        raw["ambiguity_state"] = "NEAR_TIE"
        with patch.dict("os.environ", {"IMIQ_XAI_LLM_PROVIDER": "mock"}):
            result = ContextualXAINarrator().narrate(self.request(minimized_evidence=raw))
        self.assertTrue(result.generated_by_llm)
        self.assertIn("one clear choice", result.summary)

    def test_scientific_notation_is_not_split_into_invented_numbers(self):
        raw = evidence()
        raw["top_positive_context_drivers"][0]["xi"] = 0.00002
        result = validate_generated_output({
            "title": "Explanation", "summary": "The recorded input was 2e-05.",
            "context_effect": "Only recorded context is described.",
            "model_reasoning": [], "uncertainty": "Information is limited.",
            "data_quality": "Some information is unavailable.",
        }, self.request(minimized_evidence=raw))
        self.assertTrue(result.generated_by_llm)

    def test_provider_cannot_claim_right_now_for_stale_fact(self):
        raw = evidence(); raw["context_facts"] = [{"variable": "temperature", "label": "Temperature", "display_value": "18 °C", "status": "AVAILABLE", "freshness": "STALE", "displayable": True}]
        request = self.request(minimized_evidence=raw)
        invalid = {"title": "x", "summary": "Right now it is 18 °C.", "context_effect": "x", "model_reasoning": [], "uncertainty": "x", "data_quality": "x"}
        with self.assertRaises(NarrationError): validate_generated_output(invalid, request)

    def test_provider_may_repeat_exact_number_from_display_safe_context_fact(self):
        raw = evidence()
        raw["context_facts"] = [{
            "variable": "temperature", "label": "Temperature",
            "display_value": "23.9 °C", "status": "AVAILABLE",
            "freshness": "CURRENT", "displayable": True,
        }]
        request = self.request(minimized_evidence=raw)
        result = validate_generated_output({
            "title": "Available context",
            "summary": "The temperature observation is 23.9 °C.",
            "context_effect": "The supplied temperature is included in the exploratory analysis.",
            "model_reasoning": [],
            "uncertainty": "Only supplied context is described.",
            "data_quality": "The observation is marked current.",
        }, request)
        self.assertTrue(result.generated_by_llm)

    def test_provider_still_cannot_invent_number_absent_from_display_facts(self):
        raw = evidence()
        raw["context_facts"] = [{
            "variable": "temperature", "label": "Temperature",
            "display_value": "23.9 °C", "status": "AVAILABLE",
            "freshness": "CURRENT", "displayable": True,
        }]
        request = self.request(minimized_evidence=raw)
        with self.assertRaises(NarrationError):
            validate_generated_output({
                "title": "Available context",
                "summary": "The temperature observation is 99 °C.",
                "context_effect": "The supplied temperature is included.",
                "model_reasoning": [],
                "uncertainty": "Only supplied context is described.",
                "data_quality": "The observation is marked current.",
            }, request)


if __name__ == "__main__":
    unittest.main()
