from __future__ import annotations

import json
from pathlib import Path
import unittest

import numpy as np

from adaptive_passport_v1 import (
    AdaptivePassportV1,
    raw_belief_rating_to_normalized,
)


TEST_DIR = Path(__file__).resolve().parent

PARITY_FIXTURE = (
    TEST_DIR
    / "fixtures"
    / "adaptive_passport_v1_parity_fixture.json"
)


class AdaptivePassportV1Tests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.runtime = AdaptivePassportV1()

        cls.fixture = json.loads(
            PARITY_FIXTURE.read_text(
                encoding="utf-8"
            )
        )

    def test_artifact_contract_is_canonical_4x11(self):
        self.assertEqual(
            len(
                self.runtime.all_cells
            ),
            44,
        )

        self.assertEqual(
            self.runtime.k,
            4,
        )

        self.assertEqual(
            len(
                self.runtime.coefficients
            ),
            58,
        )

        self.assertEqual(
            len(
                self.runtime.utility_by_cell
            ),
            44,
        )

        self.assertEqual(
            len(
                self.runtime.artifact_sha256
            ),
            64,
        )

    def test_numpy_runtime_matches_sklearn_fixture(self):
        self.assertEqual(
            self.fixture[
                "generator"
            ],
            "fitted_sklearn_pipeline",
        )

        self.assertFalse(
            self.fixture[
                "test_data_used"
            ]
        )

        self.assertEqual(
            self.fixture[
                "n_cases"
            ],
            5,
        )

        for case in self.fixture[
            "cases"
        ]:

            actual = (
                self.runtime.predict_all(
                    needs=case[
                        "needs"
                    ],
                    valences=case[
                        "valences"
                    ],
                )
            )

            expected = case[
                "expected_predictions"
            ]

            self.assertEqual(
                set(actual),
                set(expected),
            )

            actual_values = np.asarray(
                [
                    actual[
                        cell_id
                    ]
                    for cell_id
                    in self.runtime.all_cells
                ],
                dtype=np.float64,
            )

            expected_values = np.asarray(
                [
                    expected[
                        cell_id
                    ]
                    for cell_id
                    in self.runtime.all_cells
                ],
                dtype=np.float64,
            )

            np.testing.assert_allclose(
                actual_values,
                expected_values,
                rtol=0.0,
                atol=1e-10,
                err_msg=(
                    "NumPy deployment runtime "
                    "does not reproduce sklearn "
                    f"for submission "
                    f"{case['submission_id']}"
                ),
            )

    def test_question_selection_is_deterministic(self):
        first = (
            self.runtime
            .select_questions()
        )

        second = (
            self.runtime
            .select_questions()
        )

        self.assertEqual(
            first,
            second,
        )

        selected_cells = [
            question[
                "cell_id"
            ]
            for question in first
        ]

        self.assertEqual(
            selected_cells,
            [
                "pt__comfort",
                "walk__comfort",
                "bike__privacy",
                "car__reliable",
            ],
        )

        self.assertEqual(
            len(
                set(
                    question[
                        "hotco_mode"
                    ]
                    for question
                    in first
                )
            ),
            4,
        )

    def test_personalization_uses_global_shrinkage_and_exact_answers(
        self,
    ):
        case = self.fixture[
            "cases"
        ][0]

        questions = (
            self.runtime
            .select_questions()
        )

        selected_cells = [
            question[
                "cell_id"
            ]
            for question
            in questions
        ]

        raw_answers = {
            selected_cells[0]: 1,
            selected_cells[1]: 3,
            selected_cells[2]: 5,
            selected_cells[3]: 7,
        }

        cold_start = (
            self.runtime
            .predict_all(
                needs=case[
                    "needs"
                ],
                valences=case[
                    "valences"
                ],
            )
        )

        result = (
            self.runtime
            .personalize(
                needs=case[
                    "needs"
                ],
                valences=case[
                    "valences"
                ],
                observed_answers=
                    raw_answers,
                answers_are_raw_1_to_7=
                    True,
            )
        )

        normalized_answers = {
            cell_id:
                raw_belief_rating_to_normalized(
                    raw_rating
                )
            for cell_id, raw_rating
            in raw_answers.items()
        }

        residuals = np.asarray(
            [
                normalized_answers[
                    cell_id
                ]
                - cold_start[
                    cell_id
                ]
                for cell_id
                in selected_cells
            ],
            dtype=np.float64,
        )

        expected_weight = (
            len(
                selected_cells
            )
            /
            (
                len(
                    selected_cells
                )
                + self.runtime.global_lambda
            )
        )

        expected_offset = (
            expected_weight
            * float(
                residuals.mean()
            )
        )

        self.assertAlmostEqual(
            result[
                "personalization"
            ][
                "shrinkage_weight"
            ],
            expected_weight,
            places=12,
        )

        self.assertAlmostEqual(
            result[
                "personalization"
            ][
                "personal_offset"
            ],
            expected_offset,
            places=12,
        )

        observed_count = 0
        estimated_count = 0

        for mode in (
            "car",
            "bike",
            "pt",
            "walk",
        ):

            for need in (
                "pro_env",
                "physical",
                "privacy",
                "autonomy",
                "cost",
                "speed",
                "safety_accident",
                "safety_crime",
                "comfort",
                "reliable",
                "health_infection",
            ):

                cell_id = (
                    f"{mode}__{need}"
                )

                value = result[
                    "beliefs"
                ][
                    mode
                ][
                    need
                ]

                provenance = result[
                    "belief_provenance"
                ][
                    mode
                ][
                    need
                ]

                if cell_id in normalized_answers:

                    observed_count += 1

                    self.assertAlmostEqual(
                        value,
                        normalized_answers[
                            cell_id
                        ],
                        places=12,
                    )

                    self.assertEqual(
                        provenance[
                            "source"
                        ],
                        "observed",
                    )

                    self.assertTrue(
                        provenance[
                            "direct_user_response"
                        ]
                    )

                else:

                    estimated_count += 1

                    expected_value = float(
                        np.clip(
                            cold_start[
                                cell_id
                            ]
                            + expected_offset,
                            -1.0,
                            1.0,
                        )
                    )

                    self.assertAlmostEqual(
                        value,
                        expected_value,
                        places=12,
                    )

                    self.assertEqual(
                        provenance[
                            "source"
                        ],
                        "estimated",
                    )

                    self.assertFalse(
                        provenance[
                            "direct_user_response"
                        ]
                    )

        self.assertEqual(
            observed_count,
            4,
        )

        self.assertEqual(
            estimated_count,
            40,
        )


if __name__ == "__main__":
    unittest.main()
