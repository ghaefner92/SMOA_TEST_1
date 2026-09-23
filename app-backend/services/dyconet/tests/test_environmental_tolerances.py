from __future__ import annotations

import copy
import json
from pathlib import Path
import unittest

import numpy as np

from contextual_deliberation import ContextualDeliberationRequest
from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import InputValidationError, parse_participant_input
from passport_xai import build_cognitive_passport
from profile_update import prepare_profile_update


SERVICE_DIR = Path(__file__).resolve().parents[1]
CANONICAL = ("rain", "crowding", "darkness", "traffic", "temperature", "wind")


def questionnaire(tolerances=None):
    payload = json.loads((SERVICE_DIR / "example_request.json").read_text(encoding="utf-8"))
    if tolerances is not None:
        payload["responses"]["environmental_tolerances"] = tolerances
    return payload


def passport_for(tolerances=None):
    participant = parse_participant_input(questionnaire(tolerances))
    engine = HOTCOCTv43()
    passport = build_cognitive_passport(
        participant, engine.simulate(participant), engine
    )["cognitive_passport"]
    return passport, participant, engine


def candidate_route():
    return {
        "route_id": "route-1",
        "summary": {"distance_meters": 1000.0, "duration_seconds": 300.0},
        "legs": [
            {
                "segment_id": "leg-1",
                "mode": "bike",
                "start": {"latitude": 52.1, "longitude": 11.6},
                "end": {"latitude": 52.11, "longitude": 11.61},
                "duration_seconds": 300.0,
                "distance_meters": 1000.0,
            }
        ],
        "origin": {"latitude": 52.1, "longitude": 11.6},
        "destination": {"latitude": 52.11, "longitude": 11.61},
    }


class EnvironmentalToleranceContractTests(unittest.TestCase):
    def test_complete_questionnaire_is_persisted_unchanged(self):
        values = {"rain": 0.7, "crowding": 0.4, "darkness": 0.8, "traffic": 0.3, "temperature": 0.6, "wind": 0.5}
        passport, participant, _ = passport_for(values)
        self.assertEqual(participant.environmental_tolerances, values)
        self.assertEqual(passport["profile"]["environmental_tolerances"], values)
        metadata = passport["profile"]["environmental_tolerance_metadata"]
        self.assertEqual(metadata["schema_version"], "environmental-tolerances-v1")
        self.assertTrue(metadata["complete"])
        self.assertEqual(metadata["missing_dimensions"], [])
        self.assertEqual(metadata["transformation"], "none")

    def test_zero_one_and_partial_missing_survive_json_round_trip(self):
        passport, _, _ = passport_for({"rain": 0, "crowding": 1, "traffic": 0.3, "temperature": None})
        restored = json.loads(json.dumps(passport))
        values = restored["profile"]["environmental_tolerances"]
        self.assertEqual(set(values), set(CANONICAL))
        self.assertEqual(values["rain"], 0.0)
        self.assertEqual(values["crowding"], 1.0)
        self.assertIsNone(values["temperature"])
        self.assertIsNone(values["darkness"])
        self.assertNotEqual(values["temperature"], 0.5)
        self.assertFalse(restored["profile"]["environmental_tolerance_metadata"]["complete"])

    def test_legacy_questionnaire_is_accepted_without_defaults(self):
        participant = parse_participant_input(questionnaire())
        self.assertEqual(participant.environmental_tolerances, {name: None for name in CANONICAL})

    def test_older_passport_without_tolerance_extension_remains_readable(self):
        passport, _, _ = passport_for({"rain": 0.7})
        passport["profile"].pop("environmental_tolerances")
        passport["profile"].pop("environmental_tolerance_metadata")
        request = ContextualDeliberationRequest.from_dict({
            "search_id": "legacy-passport-search",
            "timestamp": "2026-09-12T10:00:00Z",
            "cognitive_passport": passport,
            "candidate_routes": [candidate_route()],
        })
        self.assertEqual(request.tolerance_profile.to_dict(), {})
        self.assertEqual(len(request.warnings), len(CANONICAL))

    def test_invalid_or_unknown_tolerance_is_rejected(self):
        for values in ({"rain": 1.01}, {"rain": -0.01}, {"rain": True}):
            with self.subTest(values=values), self.assertRaises(InputValidationError):
                parse_participant_input(questionnaire(values))

    def test_contextual_deliberation_reads_canonical_passport_field(self):
        passport, _, _ = passport_for({"rain": 0.7, "traffic": 0.3, "temperature": None})
        request = ContextualDeliberationRequest.from_dict({
            "search_id": "search-1",
            "timestamp": "2026-09-12T10:00:00Z",
            "cognitive_passport": passport,
            "candidate_routes": [candidate_route()],
        })
        self.assertEqual(request.tolerance_profile.to_dict(), {"rain": 0.7, "traffic": 0.3})
        self.assertTrue(any("temperature" in warning for warning in request.warnings))

    def test_explicit_request_tolerances_take_precedence(self):
        passport, _, _ = passport_for({"rain": 0.7, "traffic": 0.3})
        request = ContextualDeliberationRequest.from_dict({
            "search_id": "search-1",
            "timestamp": "2026-09-12T10:00:00Z",
            "cognitive_passport": passport,
            "tolerance_profile": {"rain": 0.2},
            "candidate_routes": [candidate_route()],
        })
        self.assertEqual(request.tolerance_profile.to_dict(), {"rain": 0.2})

    def test_profile_update_preserves_tolerances(self):
        values = {"rain": 0.7, "crowding": 0.4, "darkness": None, "traffic": 0.3, "temperature": 0.6, "wind": 0.5}
        passport, participant, _ = passport_for(values)
        passport["adaptive_questioning"] = {
            "question_needed": True,
            "candidate": {
                "question_id": "mq-tolerance-preservation",
                "trigger": "PERSISTENT_RIVALRY",
                "scope": "profile",
                "target": {"kind": "need", "need": "cost", "current_raw_rating": participant.raw_needs["cost"]},
            },
        }
        updated, _ = prepare_profile_update({
            "schema_version": "hotco_ct_profile_update_1.0",
            "previous_passport": passport,
            "update": {
                "question_id": "mq-tolerance-preservation",
                "target": {"kind": "need", "need": "cost"},
                "raw_rating": participant.raw_needs["cost"],
                "explicit_user_confirmation": True,
            },
        })
        self.assertEqual(updated.environmental_tolerances, values)

    def test_tolerance_serialization_alone_does_not_change_hotco_trajectory(self):
        base = parse_participant_input(questionnaire())
        measured = parse_participant_input(questionnaire({name: 0.5 for name in CANONICAL}))
        engine = HOTCOCTv43()
        self.assertTrue(np.array_equal(engine.simulate(base).trajectory, engine.simulate(measured).trajectory))


if __name__ == "__main__":
    unittest.main()
