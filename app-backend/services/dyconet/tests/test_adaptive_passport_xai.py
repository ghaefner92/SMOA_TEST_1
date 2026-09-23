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
from adaptive_passport_xai import (
    build_adaptive_cognitive_passport,
)
from hotco_ct_v4_3 import HOTCOCTv43


def completed():
    start = start_adaptive_passport(
        {
            "schema_version":
                ONBOARDING_SCHEMA_VERSION,

            "agent_id":
                "adaptive-xai-test",

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

    return complete_adaptive_passport(
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


class AdaptivePassportXAITests(
    unittest.TestCase
):

    def setUp(self):
        self.adaptive = (
            build_adaptive_hotco_input(
                completed(),
                availability={
                    "car": True,
                    "bike": True,
                    "pt": True,
                    "walk": True,
                },
            )
        )

        self.engine = HOTCOCTv43()

        self.result = (
            self.engine.simulate(
                self.adaptive.participant
            )
        )

        self.output = (
            build_adaptive_cognitive_passport(
                self.adaptive,
                self.result,
                self.engine,
            )
        )

        self.passport = (
            self.output[
                "cognitive_passport"
            ]
        )

    def test_provenance_is_not_falsely_all_observed(self):
        provenance = (
            self.passport[
                "input_provenance"
            ]
        )

        self.assertTrue(
            provenance[
                "imputation_used"
            ]
        )

        self.assertTrue(
            provenance[
                "belief_estimation_used"
            ]
        )

        self.assertTrue(
            provenance[
                "population_trained_estimator_used"
            ]
        )

        self.assertFalse(
            provenance[
                "synthetic_values_used"
            ]
        )

        self.assertEqual(
            provenance[
                "observed_counts"
            ][
                "beliefs"
            ],
            4,
        )

        self.assertEqual(
            provenance[
                "estimated_counts"
            ][
                "beliefs"
            ],
            40,
        )

    def test_topology_reports_four_observed_and_40_estimated_edges(self):
        topology = (
            self.passport[
                "topology"
            ]
        )

        self.assertEqual(
            topology[
                "belief_edges_observed"
            ],
            4,
        )

        self.assertEqual(
            topology[
                "belief_edges_estimated"
            ],
            40,
        )

        self.assertEqual(
            topology[
                "belief_edges_imputed"
            ],
            40,
        )

        self.assertEqual(
            topology[
                "belief_edge_total"
            ],
            44,
        )

    def test_profile_remains_dense_for_hotco_consumers(self):
        beliefs = (
            self.passport[
                "profile"
            ][
                "beliefs"
            ]
        )

        total = sum(
            len(
                mode_values
            )
            for mode_values
            in beliefs.values()
        )

        self.assertEqual(
            total,
            44,
        )

    def test_measurement_provenance_distinguishes_cells(self):
        provenance = (
            self.passport[
                "measurement_provenance"
            ][
                "beliefs"
            ]
        )

        observed = 0
        estimated = 0

        for mode_values in (
            provenance.values()
        ):
            for metadata in (
                mode_values.values()
            ):

                if (
                    metadata[
                        "source"
                    ]
                    == "observed"
                ):
                    observed += 1

                elif (
                    metadata[
                        "source"
                    ]
                    == "estimated"
                ):
                    estimated += 1

        self.assertEqual(
            observed,
            4,
        )

        self.assertEqual(
            estimated,
            40,
        )

    def test_xai_uses_provenance_aware_adaptive_question_trigger(self):
        self.assertIn(
            "xai_diagnostics",
            self.passport,
        )

        questioning = (
            self.passport[
                "adaptive_questioning"
            ]
        )

        self.assertEqual(
            questioning[
                "schema_version"
            ],
            "adaptive_question_engine_1.0",
        )

        self.assertEqual(
            questioning[
                "status"
            ],
            "experimental",
        )

        self.assertFalse(
            questioning[
                "automatic_profile_updates"
            ]
        )

        self.assertFalse(
            questioning[
                "llm_selects_question"
            ]
        )

        self.assertFalse(
            questioning[
                "hotco_parameters_changed"
            ]
        )

        self.assertEqual(
            questioning[
                "max_questions_per_deliberation"
            ],
            1,
        )

        self.assertEqual(
            questioning[
                "belief_target_policy"
            ],
            (
                "prefer_relevant_estimated_cell_"
                "before_observed_remeasurement"
            ),
        )

        if questioning[
            "question_needed"
        ]:

            candidate = (
                questioning[
                    "candidate"
                ]
            )

            self.assertIsNotNone(
                candidate
            )

            target = (
                candidate[
                    "target"
                ]
            )

            self.assertIn(
                target[
                    "measurement_status"
                ],
                {
                    "observed",
                    "estimated",
                },
            )

            if (
                target[
                    "kind"
                ]
                == "belief"
                and target[
                    "measurement_status"
                ]
                == "estimated"
            ):

                self.assertNotIn(
                    "current_raw_rating",
                    target,
                )

    def test_adaptive_lineage_records_initial_observed_beliefs(self):
        lineage = (
            self.passport[
                "lineage"
            ]
        )

        self.assertEqual(
            lineage[
                "origin"
            ],
            "initial_adaptive_calibration",
        )

        history = (
            lineage[
                "measurement_history"
            ]
        )

        self.assertEqual(
            len(
                history
            ),
            4,
        )

        self.assertTrue(
            all(
                event[
                    "source"
                ]
                == "explicit_user_response"
                for event in history
            )
        )

        self.assertTrue(
            all(
                event[
                    "revision"
                ]
                == 1
                for event in history
            )
        )

    def test_passport_persists_only_real_raw_measurements(self):
        observed = (
            self.passport[
                "observed_measurements"
            ]
        )

        self.assertEqual(
            len(
                observed[
                    "needs_raw_1_to_7"
                ]
            ),
            11,
        )

        self.assertEqual(
            len(
                observed[
                    "valences_raw_minus3_to_3"
                ]
            ),
            4,
        )

        self.assertEqual(
            len(
                observed[
                    "beliefs_raw_1_to_7"
                ]
            ),
            4,
        )

        self.assertEqual(
            set(
                observed[
                    "beliefs_raw_1_to_7"
                ]
            ),
            {
                "pt__comfort",
                "walk__comfort",
                "bike__privacy",
                "car__reliable",
            },
        )


if __name__ == "__main__":
    unittest.main()
