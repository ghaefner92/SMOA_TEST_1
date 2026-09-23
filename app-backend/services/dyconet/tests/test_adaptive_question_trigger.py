from __future__ import annotations

import unittest

from adaptive_hotco_adapter import (
    build_adaptive_hotco_input,
)
from adaptive_passport_onboarding import (
    ONBOARDING_SCHEMA_VERSION,
    complete_adaptive_passport,
    start_adaptive_passport,
)
from adaptive_question_trigger import (
    build_adaptive_micro_question,
)


def adaptive_input():
    start = start_adaptive_passport(
        {
            "schema_version":
                ONBOARDING_SCHEMA_VERSION,

            "agent_id":
                "adaptive-trigger-test",

            "responses": {
                "needs": {
                    "pro_env": 6,
                    "physical": 5,
                    "privacy": 4,
                    "autonomy": 6,
                    "cost": 7,
                    "speed": 5,
                    "safety_accident": 6,
                    "safety_crime": 5,
                    "comfort": 6,
                    "reliable": 7,
                    "health_infection": 3,
                },

                "valences": {
                    "car": 1,
                    "bike": 2,
                    "pt": 0,
                    "walk": 2,
                },
            },
        }
    )

    completed = (
        complete_adaptive_passport(
            {
                "schema_version":
                    ONBOARDING_SCHEMA_VERSION,

                "start":
                    start,

                "answers": {
                    "pt__comfort": 1,
                    "walk__comfort": 3,
                    "bike__privacy": 5,
                    "car__reliable": 7,
                },
            }
        )
    )

    return build_adaptive_hotco_input(
        completed,
        availability={
            "car": True,
            "bike": True,
            "pt": True,
            "walk": True,
        },
    )


class AdaptiveQuestionTriggerTests(
    unittest.TestCase
):

    def setUp(self):
        self.adaptive = (
            adaptive_input()
        )

        self.participant = (
            self.adaptive.participant
        )

        self.provenance = (
            self.adaptive
            .measurement_provenance[
                "beliefs"
            ]
        )

    def test_reversal_prefers_estimated_belief_without_fake_raw_rating(
        self,
    ):
        xai = {
            "patterns": [
                "LATE_REVERSAL"
            ],

            "competition": {
                "winner":
                    "car",

                "main_rival":
                    "bike",
            },

            "leadership": {
                "first_identifiable_leader":
                    "pt",
            },
        }

        output = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai=
                    xai,

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-a",

                revision=
                    1,
            )
        )

        self.assertTrue(
            output[
                "question_needed"
            ]
        )

        target = (
            output[
                "candidate"
            ][
                "target"
            ]
        )

        self.assertEqual(
            target[
                "kind"
            ],
            "belief",
        )

        self.assertEqual(
            target[
                "measurement_status"
            ],
            "estimated",
        )

        self.assertEqual(
            target[
                "question_intent"
            ],
            "measure_estimated_construct",
        )

        self.assertNotIn(
            "current_raw_rating",
            target,
        )

    def test_mixed_support_can_target_estimated_belief(
        self,
    ):
        xai = {
            "patterns": [
                "HIGH_WINNER_MIXED_COGNITIVE_SUPPORT"
            ],

            "competition": {
                "winner":
                    "bike",

                "main_rival":
                    "walk",
            },
        }

        output = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai=
                    xai,

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-b",

                revision=
                    1,
            )
        )

        self.assertTrue(
            output[
                "question_needed"
            ]
        )

        target = (
            output[
                "candidate"
            ][
                "target"
            ]
        )

        self.assertEqual(
            target[
                "kind"
            ],
            "belief",
        )

        if (
            target[
                "measurement_status"
            ]
            == "estimated"
        ):
            self.assertNotIn(
                "current_raw_rating",
                target,
            )

    def test_persistent_rivalry_rechecks_observed_need(
        self,
    ):
        xai = {
            "patterns": [
                "PERSISTENT_RIVALRY"
            ],

            "competition": {
                "winner":
                    "bike",

                "main_rival":
                    "pt",
            },
        }

        output = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai=
                    xai,

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-c",

                revision=
                    1,
            )
        )

        target = (
            output[
                "candidate"
            ][
                "target"
            ]
        )

        self.assertEqual(
            target[
                "kind"
            ],
            "need",
        )

        self.assertEqual(
            target[
                "measurement_status"
            ],
            "observed",
        )

        self.assertIn(
            "current_raw_rating",
            target,
        )

    def test_affective_friction_rechecks_observed_valence(
        self,
    ):
        xai = {
            "patterns": [
                "HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION"
            ],

            "competition": {
                "winner":
                    "bike",

                "main_rival":
                    "pt",
            },
        }

        output = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai=
                    xai,

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-d",

                revision=
                    2,
            )
        )

        target = (
            output[
                "candidate"
            ][
                "target"
            ]
        )

        self.assertEqual(
            target[
                "kind"
            ],
            "valence",
        )

        self.assertEqual(
            target[
                "measurement_status"
            ],
            "observed",
        )

        self.assertIn(
            "current_raw_rating",
            target,
        )

    def test_recent_target_is_suppressed(
        self,
    ):
        xai = {
            "patterns": [
                "LATE_REVERSAL"
            ],

            "competition": {
                "winner":
                    "car",

                "main_rival":
                    "bike",
            },

            "leadership": {
                "first_identifiable_leader":
                    "pt",
            },
        }

        first = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai=
                    xai,

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-e",

                revision=
                    3,
            )
        )

        target = (
            first[
                "candidate"
            ][
                "target"
            ]
        )

        history = [
            {
                "revision":
                    3,

                "target":
                    target,

                "source":
                    "explicit_user_response",
            }
        ]

        second = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai=
                    xai,

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-e",

                revision=
                    3,

                measurement_history=
                    history,
            )
        )

        self.assertFalse(
            second[
                "question_needed"
            ]
        )

        self.assertEqual(
            second[
                "suppressed_reason"
            ],
            "same_target_recently_measured",
        )

    def test_trigger_never_updates_profile_automatically(
        self,
    ):
        output = (
            build_adaptive_micro_question(
                participant=
                    self.participant,

                xai={
                    "patterns": [],
                    "competition": {},
                },

                belief_provenance=
                    self.provenance,

                passport_id=
                    "passport-f",

                revision=
                    1,
            )
        )

        self.assertFalse(
            output[
                "automatic_profile_updates"
            ]
        )

        self.assertFalse(
            output[
                "llm_selects_question"
            ]
        )

        self.assertFalse(
            output[
                "hotco_parameters_changed"
            ]
        )


if __name__ == "__main__":
    unittest.main()
