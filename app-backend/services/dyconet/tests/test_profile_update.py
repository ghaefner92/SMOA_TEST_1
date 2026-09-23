from __future__ import annotations

import copy
import json
from pathlib import Path
import unittest

from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import InputValidationError, parse_participant_input
from passport_xai import build_cognitive_passport
from profile_update import PROFILE_UPDATE_SCHEMA_VERSION, prepare_profile_update

try:
    from main import app, run_profile_update
except ModuleNotFoundError:
    app = None
    run_profile_update = None


SERVICE_DIR = Path(__file__).resolve().parents[1]


def initial_state():
    payload = json.loads((SERVICE_DIR / "example_request.json").read_text(encoding="utf-8"))
    participant = parse_participant_input(payload)
    engine = HOTCOCTv43()
    passport = build_cognitive_passport(
        participant, engine.simulate(participant), engine
    )["cognitive_passport"]
    # Use a deterministic backend-shaped candidate so the update contract can
    # be tested independently of whichever XAI pattern the example happens to show.
    passport["adaptive_questioning"] = {
        "schema_version": "1.0",
        "question_needed": True,
        "candidate": {
            "question_id": "mq_test_cost",
            "trigger": "PERSISTENT_RIVALRY",
            "scope": "profile",
            "target": {
                "kind": "need",
                "need": "cost",
                "current_raw_rating": participant.raw_needs["cost"],
            },
            "response_scale": {"minimum": 1, "maximum": 7, "integer_only": True},
            "update_requires_explicit_user_confirmation": True,
        },
    }
    return passport, participant


def update_payload(passport, rating: int, confirmed: bool = True):
    return {
        "schema_version": PROFILE_UPDATE_SCHEMA_VERSION,
        "previous_passport": {"cognitive_passport": passport},
        "update": {
            "question_id": "mq_test_cost",
            "target": {"kind": "need", "need": "cost"},
            "raw_rating": rating,
            "explicit_user_confirmation": confirmed,
        },
    }


class ProfileUpdateTests(unittest.TestCase):
    def test_explicit_answer_creates_new_lineage_revision_without_mutating_previous(self):
        passport, participant = initial_state()
        previous_snapshot = copy.deepcopy(passport)
        previous_id = passport["lineage"]["passport_id"]
        new_rating = 7 if participant.raw_needs["cost"] != 7 else 6

        updated_participant, lineage = prepare_profile_update(
            update_payload(passport, new_rating)
        )

        self.assertEqual(passport, previous_snapshot)
        self.assertEqual(lineage["revision"], 2)
        self.assertEqual(lineage["parent_passport_id"], previous_id)
        self.assertNotEqual(lineage["passport_id"], previous_id)
        self.assertEqual(lineage["origin"], "explicit_micro_question_update")
        self.assertEqual(len(lineage["measurement_history"]), 1)
        event = lineage["last_update_event"]
        self.assertEqual(event["source"], "explicit_user_response")
        self.assertEqual(event["target"], {"kind": "need", "need": "cost"})
        self.assertEqual(event["new_raw_rating"], new_rating)
        self.assertEqual(updated_participant.raw_needs["cost"], new_rating)

    def test_same_rating_is_still_a_repeated_measurement_revision(self):
        passport, participant = initial_state()
        current = participant.raw_needs["cost"]
        _, lineage = prepare_profile_update(update_payload(passport, current))
        self.assertEqual(lineage["revision"], 2)
        event = lineage["last_update_event"]
        self.assertFalse(event["value_changed"])
        self.assertEqual(event["delta_raw_rating"], 0)
        self.assertEqual(len(lineage["measurement_history"]), 1)

    def test_update_requires_explicit_confirmation(self):
        passport, participant = initial_state()
        with self.assertRaises(InputValidationError):
            prepare_profile_update(
                update_payload(passport, participant.raw_needs["cost"], confirmed=False)
            )

    def test_update_rejects_target_that_does_not_match_active_question(self):
        passport, participant = initial_state()
        payload = update_payload(passport, participant.raw_needs["cost"])
        payload["update"]["target"] = {"kind": "need", "need": "speed"}
        with self.assertRaises(InputValidationError):
            prepare_profile_update(payload)

    def test_full_update_run_produces_child_passport_and_preserves_history(self):
        if run_profile_update is None:
            self.skipTest("Flask service dependencies are unavailable")
        passport, participant = initial_state()
        current = participant.raw_needs["cost"]
        new_rating = 7 if current != 7 else 6
        updated = run_profile_update(update_payload(passport, new_rating))["cognitive_passport"]
        self.assertEqual(updated["lineage"]["revision"], 2)
        self.assertEqual(
            updated["lineage"]["parent_passport_id"],
            passport["lineage"]["passport_id"],
        )
        self.assertEqual(len(updated["lineage"]["measurement_history"]), 1)
        self.assertAlmostEqual(updated["profile"]["needs"]["cost"], (new_rating - 1) / 6)

    def test_http_endpoint_returns_422_for_unconfirmed_update(self):
        if app is None:
            self.skipTest("Flask is unavailable")
        passport, participant = initial_state()
        payload = update_payload(passport, participant.raw_needs["cost"], confirmed=False)
        with app.test_client() as client:
            response = client.post("/api/dyconet/profile-update", json=payload)
        self.assertEqual(response.status_code, 422)
        body = response.get_json()
        self.assertFalse(body["automatic_update_performed"])
        self.assertFalse(body["imputation_performed"])


if __name__ == "__main__":
    unittest.main()
