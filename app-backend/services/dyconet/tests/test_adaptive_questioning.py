from __future__ import annotations

import json
from pathlib import Path
import unittest

from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import parse_participant_input
from passport_xai import build_cognitive_passport
from question_trigger_engine import build_micro_question


SERVICE_DIR = Path(__file__).resolve().parents[1]


def example_payload():
    return json.loads((SERVICE_DIR / "example_request.json").read_text(encoding="utf-8"))


def participant():
    return parse_participant_input(example_payload())


class AdaptiveQuestioningTests(unittest.TestCase):
    def test_passport_has_append_only_lineage_identity(self):
        p = participant()
        engine = HOTCOCTv43()
        passport = build_cognitive_passport(p, engine.simulate(p), engine)["cognitive_passport"]
        lineage = passport["lineage"]
        self.assertTrue(lineage["passport_id"])
        self.assertEqual(lineage["revision"], 1)
        self.assertIsNone(lineage["parent_passport_id"])
        self.assertEqual(lineage["origin"], "initial_full_calibration")
        self.assertEqual(lineage["measurement_history"], [])
        self.assertIn("adaptive_questioning", passport)

    def test_persistent_rivalry_yields_at_most_one_structured_profile_question(self):
        p = participant()
        xai = {
            "patterns": ["PERSISTENT_RIVALRY"],
            "competition": {"winner": "bike", "main_rival": "pt"},
        }
        output = build_micro_question(
            participant=p,
            xai=xai,
            passport_id="passport-a",
            revision=1,
        )
        self.assertTrue(output["question_needed"])
        self.assertEqual(output["max_questions_per_deliberation"], 1)
        self.assertFalse(output["automatic_profile_updates"])
        self.assertFalse(output["llm_selects_question"])
        candidate = output["candidate"]
        self.assertEqual(candidate["trigger"], "PERSISTENT_RIVALRY")
        self.assertEqual(candidate["scope"], "profile")
        self.assertEqual(candidate["target"]["kind"], "need")
        self.assertGreaterEqual(candidate["target"]["current_raw_rating"], 1)
        self.assertLessEqual(candidate["target"]["current_raw_rating"], 7)

    def test_cognitive_affective_friction_rechecks_winner_valence(self):
        p = participant()
        xai = {
            "patterns": ["HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION"],
            "competition": {"winner": "bike", "main_rival": "pt"},
        }
        output = build_micro_question(
            participant=p,
            xai=xai,
            passport_id="passport-b",
            revision=3,
        )
        target = output["candidate"]["target"]
        self.assertEqual(target["kind"], "valence")
        self.assertEqual(target["mode"], "bike")

    def test_reversal_uses_actual_initial_leader_not_terminal_rival(self):
        payload = example_payload()
        # Make bike and PT identical across beliefs, while car differs uniquely on env.
        for need in payload["responses"]["beliefs"]["bike"]:
            payload["responses"]["beliefs"]["bike"][need] = 4
            payload["responses"]["beliefs"]["pt"][need] = 4
            payload["responses"]["beliefs"]["car"][need] = 4
        payload["responses"]["beliefs"]["car"]["env"] = 7
        p = parse_participant_input(payload)
        xai = {
            "patterns": ["LATE_REVERSAL"],
            "competition": {"winner": "bike", "main_rival": "pt"},
            "leadership": {"first_identifiable_leader": "car"},
        }
        output = build_micro_question(
            participant=p,
            xai=xai,
            passport_id="passport-reversal",
            revision=1,
        )
        target = output["candidate"]["target"]
        self.assertEqual(target["kind"], "belief")
        self.assertEqual(target["mode"], "bike")
        self.assertEqual(target["need"], "pro_env")

    def test_recently_remeasured_target_is_suppressed(self):
        p = participant()
        xai = {
            "patterns": ["HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION"],
            "competition": {"winner": "bike", "main_rival": "pt"},
        }
        history = [
            {
                "revision": 2,
                "target": {"kind": "valence", "mode": "bike"},
                "source": "explicit_user_response",
            }
        ]
        output = build_micro_question(
            participant=p,
            xai=xai,
            passport_id="passport-c",
            revision=3,
            measurement_history=history,
        )
        self.assertFalse(output["question_needed"])
        self.assertIsNone(output["candidate"])
        self.assertEqual(output["suppressed_reason"], "same_target_recently_remeasured")


if __name__ == "__main__":
    unittest.main()
