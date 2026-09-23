from __future__ import annotations

import unittest

from adaptive_passport_v1 import AdaptivePassportError
from adaptive_passport_onboarding import (
    ONBOARDING_SCHEMA_VERSION,
    complete_adaptive_passport,
    start_adaptive_passport,
)


def example_start_payload():
    return {
        "schema_version": ONBOARDING_SCHEMA_VERSION,
        "agent_id": "test-user-001",
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


class AdaptivePassportOnboardingTests(unittest.TestCase):

    def test_start_returns_frozen_four_question_contract(self):
        result = start_adaptive_passport(
            example_start_payload()
        )

        self.assertEqual(
            result["stage"],
            "questions",
        )

        self.assertEqual(
            result["question_count"],
            4,
        )

        self.assertFalse(
            result["adaptive_initial_selection"]
        )

        self.assertTrue(
            result["longitudinal_adaptation"]
        )

        self.assertFalse(
            result["policy"]["hotco_run"]
        )

        cells = [
            question["cell_id"]
            for question in result["questions"]
        ]

        self.assertEqual(
            cells,
            [
                "pt__comfort",
                "walk__comfort",
                "bike__privacy",
                "car__reliable",
            ],
        )

    def test_start_normalizes_needs_and_valences(self):
        result = start_adaptive_passport(
            example_start_payload()
        )

        needs = (
            result[
                "normalized_inputs"
            ][
                "needs"
            ]
        )

        valences = (
            result[
                "normalized_inputs"
            ][
                "valences"
            ]
        )

        self.assertAlmostEqual(
            needs["cost"],
            1.0,
            places=12,
        )

        self.assertAlmostEqual(
            needs["privacy"],
            0.5,
            places=12,
        )

        self.assertAlmostEqual(
            valences["bike"],
            2.0 / 3.0,
            places=12,
        )

        self.assertAlmostEqual(
            valences["pt"],
            0.0,
            places=12,
        )

    def test_complete_produces_44_beliefs_with_4_observed(self):
        start = start_adaptive_passport(
            example_start_payload()
        )

        answers = {
            "pt__comfort": 1,
            "walk__comfort": 3,
            "bike__privacy": 5,
            "car__reliable": 7,
        }

        result = complete_adaptive_passport(
            {
                "schema_version":
                    ONBOARDING_SCHEMA_VERSION,

                "start":
                    start,

                "answers":
                    answers,
            }
        )

        self.assertEqual(
            result["stage"],
            "complete",
        )

        self.assertTrue(
            result["ready_for_hotco"]
        )

        self.assertFalse(
            result["hotco_run"]
        )

        beliefs = (
            result[
                "profile"
            ][
                "beliefs"
            ]
        )

        provenance = (
            result[
                "measurement_provenance"
            ][
                "beliefs"
            ]
        )

        belief_count = sum(
            len(
                beliefs[
                    mode
                ]
            )
            for mode in beliefs
        )

        self.assertEqual(
            belief_count,
            44,
        )

        observed = 0
        estimated = 0

        for mode in beliefs:
            for need in beliefs[mode]:

                source = (
                    provenance[
                        mode
                    ][
                        need
                    ][
                        "source"
                    ]
                )

                if source == "observed":
                    observed += 1

                elif source == "estimated":
                    estimated += 1

                else:
                    self.fail(
                        f"Unexpected provenance: {source}"
                    )

        self.assertEqual(
            observed,
            4,
        )

        self.assertEqual(
            estimated,
            40,
        )

    def test_explicit_answers_are_preserved_exactly(self):
        start = start_adaptive_passport(
            example_start_payload()
        )

        result = complete_adaptive_passport(
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

        beliefs = (
            result[
                "profile"
            ][
                "beliefs"
            ]
        )

        self.assertAlmostEqual(
            beliefs[
                "pt"
            ][
                "comfort"
            ],
            -1.0,
            places=12,
        )

        self.assertAlmostEqual(
            beliefs[
                "walk"
            ][
                "comfort"
            ],
            -1.0 / 3.0,
            places=12,
        )

        self.assertAlmostEqual(
            beliefs[
                "bike"
            ][
                "privacy"
            ],
            1.0 / 3.0,
            places=12,
        )

        self.assertAlmostEqual(
            beliefs[
                "car"
            ][
                "reliable"
            ],
            1.0,
            places=12,
        )

    def test_complete_rejects_missing_or_extra_question(self):
        start = start_adaptive_passport(
            example_start_payload()
        )

        with self.assertRaises(
            AdaptivePassportError
        ):
            complete_adaptive_passport(
                {
                    "schema_version":
                        ONBOARDING_SCHEMA_VERSION,

                    "start":
                        start,

                    "answers": {
                        "pt__comfort": 4,
                        "walk__comfort": 4,
                        "bike__privacy": 4,
                    },
                }
            )

        with self.assertRaises(
            AdaptivePassportError
        ):
            complete_adaptive_passport(
                {
                    "schema_version":
                        ONBOARDING_SCHEMA_VERSION,

                    "start":
                        start,

                    "answers": {
                        "pt__comfort": 4,
                        "walk__comfort": 4,
                        "bike__privacy": 4,
                        "car__reliable": 4,
                        "car__comfort": 4,
                    },
                }
            )

    def test_invalid_initial_measurement_is_rejected(self):
        payload = example_start_payload()

        payload[
            "responses"
        ][
            "needs"
        ][
            "cost"
        ] = 8

        with self.assertRaises(
            AdaptivePassportError
        ):
            start_adaptive_passport(
                payload
            )


if __name__ == "__main__":
    unittest.main()
