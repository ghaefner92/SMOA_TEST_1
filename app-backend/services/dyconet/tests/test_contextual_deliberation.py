from __future__ import annotations

import copy
import json
import unittest
from unittest.mock import patch

from contextual_deliberation import (
    ContextualDeliberationRequest,
    ContextualDeliberationService,
    ContextualProviderConfigurationError,
)
from hotco_ct_v4_3 import HOTCOCTv43
from main import app, run_dyconet
from orion_context import OrionHttpError, OrionTimeoutError
from route_context import CandidateRouteInput, ContextObservation, ContextStatus, Coordinate


O = Coordinate(52.13, 11.63)
D = Coordinate(52.14, 11.64)
TOLERANCES = {"rain": .5, "crowding": .5, "darkness": .5, "traffic": .5, "temperature": .5}


def questionnaire():
    with open("example_request.json", encoding="utf-8") as handle:
        return json.load(handle)


def candidate(route_id: str, mode: str = "bike") -> CandidateRouteInput:
    return CandidateRouteInput(
        route_id, O, D,
        summary={"duration_seconds": 600, "distance_meters": 2000},
        legs=({"segment_id": f"{route_id}-s1", "mode": mode, "start": O.to_dict(), "end": D.to_dict(), "duration_seconds": 600, "distance_meters": 2000},),
        transport_modes=(mode,),
    )


def observation(variable: str, value: float) -> ContextObservation:
    return ContextObservation("orion", variable, value, value, status=ContextStatus.OBSERVED, source_entity_id="synthetic-entity")


def request_payload(routes, values_by_route=None, *, query_orion=False):
    values_by_route = values_by_route or {}
    contextual = {}
    for route in routes:
        values = values_by_route.get(route.route_id, ())
        contextual[route.route_id] = {"segments": {f"{route.route_id}-s1": [item.to_dict() for item in values]}}
    return {
        "schema_version": "contextual-deliberation-request-v1",
        "search_id": "search-1",
        "timestamp": "2026-09-12T08:00:00Z",
        "participant": questionnaire(),
        "tolerance_profile": dict(TOLERANCES),
        "candidate_routes": [route.to_dict() for route in routes],
        "contextual_observations": contextual,
        "contextual_query": {"query_orion": query_orion, "entity_families": ["weather", "traffic"]},
    }


class ContextualDeliberationTests(unittest.TestCase):
    def test_one_candidate_runs_end_to_end_and_preserves_audit_data(self):
        route = candidate("r1")
        request = ContextualDeliberationRequest.from_dict(request_payload([route], {"r1": (observation("rain", 8.0),)}))
        result = ContextualDeliberationService().deliberate(request).candidate_results[0]
        self.assertEqual(result.route_id, "r1")
        self.assertEqual(result.route_context.raw_observations[0].raw_value, 8.0)
        self.assertIsNotNone(next(item for item in result.route_context.normalized_stressors if item.name == "rain").value)
        self.assertIsNotNone(next(item for item in result.context_perturbation.effective_exposures if item.stressor == "rain" and item.source_scope == "route").effective_value)
        self.assertNotEqual(result.hotco.context_metadata["xi_context"]["need_comfort"], 0.0)
        self.assertEqual(result.hotco.context_metadata["context_model"], "F3_NEED_ONLY_V1")

    def test_multiple_routes_share_baseline_but_have_route_specific_context(self):
        routes = (candidate("r1"), candidate("r2"))
        request = ContextualDeliberationRequest.from_dict(request_payload(routes, {"r1": (observation("rain", 8.0),), "r2": (observation("rain", 0.0),)}))
        participant_before = copy.deepcopy(request.participant)
        tolerances_before = dict(request.tolerance_profile.values)
        engine = HOTCOCTv43()
        with patch.object(engine, "simulate", wraps=engine.simulate) as simulate:
            response = ContextualDeliberationService(engine=engine).deliberate(request)
        self.assertEqual(simulate.call_count, 3)  # one baseline plus two candidates
        self.assertEqual(request.participant, participant_before)
        self.assertEqual(request.tolerance_profile.values, tolerances_before)
        first, second = response.candidate_results
        self.assertNotEqual(first.context_perturbation.need_perturbations, second.context_perturbation.need_perturbations)
        self.assertNotEqual(first.hotco.final_action_activations, second.hotco.final_action_activations)
        self.assertFalse(response.baseline.context_metadata["context_applied"])
        self.assertTrue(first.hotco.context_metadata["context_applied"])

    def test_zero_context_candidate_matches_baseline(self):
        request = ContextualDeliberationRequest.from_dict(request_payload([candidate("zero")]))
        response = ContextualDeliberationService().deliberate(request)
        contextual = response.candidate_results[0].hotco
        self.assertEqual(contextual.final_action_activations, response.baseline.final_action_activations)
        self.assertEqual(contextual.probabilities, response.baseline.probabilities)
        self.assertFalse(contextual.context_metadata["context_applied"])

    def test_missing_traffic_remains_unknown_and_simulation_continues(self):
        request = ContextualDeliberationRequest.from_dict(request_payload([candidate("r")], {"r": (observation("speedLimit", 50.0),)}))
        result = ContextualDeliberationService().deliberate(request).candidate_results[0]
        traffic = next(item for item in result.route_context.normalized_stressors if item.name == "traffic")
        self.assertIsNone(traffic.value)
        self.assertNotEqual(traffic.value, 0.0)

    def test_missing_tolerance_affects_only_that_dimension(self):
        payload = request_payload([candidate("r")], {"r": (observation("rain", 8.0), observation("temperature", 35.0))})
        payload["tolerance_profile"].pop("rain")
        result = ContextualDeliberationService().deliberate(ContextualDeliberationRequest.from_dict(payload)).candidate_results[0]
        rain = next(item for item in result.context_perturbation.effective_exposures if item.stressor == "rain" and item.source_scope == "route")
        temperature = next(item for item in result.context_perturbation.effective_exposures if item.stressor == "temperature" and item.source_scope == "route")
        self.assertIsNone(rain.effective_value)
        self.assertIsNotNone(temperature.effective_value)

    def test_route_order_is_preserved_and_identical_inputs_are_deterministic(self):
        routes = (candidate("b"), candidate("a"))
        values = {route.route_id: (observation("rain", 4.0),) for route in routes}
        request = ContextualDeliberationRequest.from_dict(request_payload(routes, values))
        first = ContextualDeliberationService().deliberate(request)
        second = ContextualDeliberationService().deliberate(request)
        self.assertEqual([item.route_id for item in first.candidate_results], ["b", "a"])
        self.assertEqual(first.to_dict(), second.to_dict())
        self.assertEqual(first.candidate_results[0].hotco.to_dict(), first.candidate_results[1].hotco.to_dict())

    def test_partial_orion_timeout_is_a_warning_not_neutral_data(self):
        class Provider:
            def get_context(self, family, **query):
                if family == "traffic":
                    raise OrionTimeoutError("timeout")
                return (observation("rain", 5.0),)
        request = ContextualDeliberationRequest.from_dict(request_payload([candidate("r")], query_orion=True))
        result = ContextualDeliberationService(provider=Provider()).deliberate(request).candidate_results[0]
        self.assertEqual(result.source_status["traffic"], "UNAVAILABLE")
        self.assertTrue(any("Orion source traffic unavailable" in warning for warning in result.warnings))
        self.assertIsNone(next(item for item in result.route_context.normalized_stressors if item.name == "traffic").value)

    def test_weather_uses_a_larger_nearest_station_radius_without_changing_other_sources(self):
        calls = []
        class Provider:
            def get_context(self, family, **query):
                calls.append((family, query))
                return ()
        payload = request_payload([candidate("r")], query_orion=True)
        payload["contextual_query"]["entity_families"] = ["weather", "traffic"]
        payload["contextual_query"]["radius_meters"] = 500
        payload["contextual_query"]["weather_radius_meters"] = 5000
        ContextualDeliberationService(provider=Provider()).deliberate(ContextualDeliberationRequest.from_dict(payload))
        weather = next(query for family, query in calls if family == "weather")
        traffic = next(query for family, query in calls if family == "traffic")
        self.assertEqual(weather["georel"], "near;maxDistance:5000")
        self.assertTrue(weather["nearest_only"])
        self.assertEqual(traffic["georel"], "near;maxDistance:500")
        self.assertFalse(traffic["nearest_only"])

    def test_orion_authentication_failure_degrades_to_unavailable_context(self):
        class Provider:
            def get_context(self, family, **query):
                raise OrionHttpError(401)
        request = ContextualDeliberationRequest.from_dict(request_payload([candidate("r")], query_orion=True))
        result = ContextualDeliberationService(provider=Provider()).deliberate(request)
        candidate_result = result.candidate_results[0]
        self.assertEqual(candidate_result.source_status["weather"], "UNAVAILABLE")
        self.assertTrue(any("HTTP 401" in warning for warning in candidate_result.warnings))

    def test_unknown_target_warning_propagates(self):
        result = ContextualDeliberationService().deliberate(ContextualDeliberationRequest.from_dict(request_payload([candidate("r", "train")], {"r": (observation("crowding", 1.0),)}))).candidate_results[0]
        self.assertTrue(any("does not exist" in warning for warning in result.warnings))

    def test_serialized_response_contains_no_api_key(self):
        response = ContextualDeliberationService().deliberate(ContextualDeliberationRequest.from_dict(request_payload([candidate("r")])))
        serialized = json.dumps(response.to_dict())
        self.assertNotIn("x-api-key", serialized.lower())
        self.assertNotIn("IMIQ_ORION_API_KEY", serialized)

    def test_request_rejects_client_supplied_orion_credentials(self):
        payload = request_payload([candidate("r")])
        payload["orion_api_key"] = "must-not-be-accepted"
        with self.assertRaises(ValueError):
            ContextualDeliberationRequest.from_dict(payload)

    def test_endpoint_contract_returns_two_candidates(self):
        routes = (candidate("r1"), candidate("r2"))
        payload = request_payload(routes, {"r1": (observation("rain", 8.0),), "r2": (observation("rain", 0.0),)})
        with app.test_client() as client:
            response = client.post("/api/dyconet/contextual-deliberation", json=payload)
        self.assertEqual(response.status_code, 200)
        body = response.get_json()
        self.assertIn("baseline", body)
        self.assertEqual(len(body["candidate_results"]), 2)
        self.assertIn("route_context", body["candidate_results"][0])
        self.assertIn("context_perturbation", body["candidate_results"][0])
        self.assertIn("hotco", body["candidate_results"][0])

    def test_cognitive_passport_input_extracts_available_tolerances(self):
        passport = run_dyconet(questionnaire())["cognitive_passport"]
        passport["profile"]["environmental_tolerances"] = {"rain": .7, "traffic": .2}
        payload = request_payload([candidate("r")])
        payload.pop("participant")
        payload.pop("tolerance_profile")
        payload["cognitive_passport"] = passport
        request = ContextualDeliberationRequest.from_dict(payload)
        self.assertEqual(request.tolerance_profile.values, {"rain": .7, "traffic": .2})
        self.assertTrue(any("temperature" in warning for warning in request.warnings))


if __name__ == "__main__":
    unittest.main()
