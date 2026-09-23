from __future__ import annotations

import copy
import unittest

import numpy as np

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
from adaptive_profile_update import (
    ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,
    AdaptiveProfileUpdateError,
    prepare_adaptive_profile_update,
)
from adaptive_question_trigger import (
    build_adaptive_micro_question,
)
from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import (
    MODES,
    NEEDS,
)


def build_revision_one():
    start = start_adaptive_passport(
        {
            "schema_version":
                ONBOARDING_SCHEMA_VERSION,

            "agent_id":
                "longitudinal-test-user",

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

    completed = complete_adaptive_passport(
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

    adaptive = build_adaptive_hotco_input(
        completed,
        availability={
            "car": True,
            "bike": True,
            "pt": True,
            "walk": True,
        },
    )

    engine = HOTCOCTv43()

    result = engine.simulate(
        adaptive.participant
    )

    output = build_adaptive_cognitive_passport(
        adaptive,
        result,
        engine,
    )

    passport = output[
        "cognitive_passport"
    ]

    # Force a deterministic, genuinely provenance-aware
    # backend question using the actual trigger engine.
    question = build_adaptive_micro_question(
        participant=
            adaptive.participant,

        xai={
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
        },

        belief_provenance=
            adaptive
            .measurement_provenance[
                "beliefs"
            ],

        passport_id=
            passport[
                "lineage"
            ][
                "passport_id"
            ],

        revision=
            passport[
                "lineage"
            ][
                "revision"
            ],

        measurement_history=
            passport[
                "lineage"
            ][
                "measurement_history"
            ],
    )

    if not question[
        "question_needed"
    ]:
        raise AssertionError(
            "Test setup expected an active belief question"
        )

    target = question[
        "candidate"
    ][
        "target"
    ]

    if (
        target[
            "kind"
        ]
        != "belief"
        or target[
            "measurement_status"
        ]
        != "estimated"
    ):
        raise AssertionError(
            "Test setup expected an estimated belief target"
        )

    passport[
        "adaptive_questioning"
    ] = question

    return (
        adaptive,
        passport,
        engine,
    )


class AdaptiveProfileUpdateTests(
    unittest.TestCase
):

    def test_estimated_belief_becomes_observed_and_reestimates_remaining_cells(
        self,
    ):
        (
            previous_adaptive,
            previous_passport,
            engine,
        ) = build_revision_one()

        previous_snapshot = copy.deepcopy(
            previous_passport
        )

        candidate = (
            previous_passport[
                "adaptive_questioning"
            ][
                "candidate"
            ]
        )

        target = candidate[
            "target"
        ]

        mode = target[
            "mode"
        ]

        need = target[
            "need"
        ]

        cell_id = (
            f"{mode}__{need}"
        )

        previous_value = float(
            previous_passport[
                "profile"
            ][
                "beliefs"
            ][
                mode
            ][
                need
            ]
        )

        # Choose the opposite extreme to guarantee a
        # meaningful new residual.
        new_rating = (
            1
            if previous_value >= 0.0
            else 7
        )

        updated_adaptive, lineage = (
            prepare_adaptive_profile_update(
                {
                    "schema_version":
                        ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,

                    "previous_passport":
                        previous_passport,

                    "update": {
                        "question_id":
                            candidate[
                                "question_id"
                            ],

                        "target": {
                            "kind":
                                "belief",

                            "mode":
                                mode,

                            "need":
                                need,
                        },

                        "raw_rating":
                            new_rating,

                        "explicit_user_confirmation":
                            True,
                    },
                }
            )
        )

        # ---------------------------------------------
        # 4 observed -> 5 observed
        # 40 estimated -> 39 estimated
        # ---------------------------------------------

        counts = (
            updated_adaptive
            .measurement_provenance[
                "belief_counts"
            ]
        )

        self.assertEqual(
            counts[
                "observed"
            ],
            5,
        )

        self.assertEqual(
            counts[
                "estimated"
            ],
            39,
        )

        raw_count = sum(
            len(
                updated_adaptive
                .participant
                .raw_beliefs[
                    m
                ]
            )
            for m in MODES
        )

        self.assertEqual(
            raw_count,
            5,
        )

        self.assertEqual(
            updated_adaptive
            .participant
            .raw_beliefs[
                mode
            ][
                need
            ],
            new_rating,
        )

        provenance = (
            updated_adaptive
            .measurement_provenance[
                "beliefs"
            ][
                mode
            ][
                need
            ]
        )

        self.assertEqual(
            provenance[
                "source"
            ],
            "observed",
        )

        # ---------------------------------------------
        # Lineage r1 -> r2
        # ---------------------------------------------

        self.assertEqual(
            lineage[
                "revision"
            ],
            2,
        )

        self.assertEqual(
            lineage[
                "parent_passport_id"
            ],
            previous_passport[
                "lineage"
            ][
                "passport_id"
            ],
        )

        event = lineage[
            "last_update_event"
        ]

        self.assertEqual(
            event[
                "measurement_transition"
            ],
            "estimated_to_observed",
        )

        self.assertEqual(
            event[
                "observed_belief_count_after_update"
            ],
            5,
        )

        self.assertEqual(
            event[
                "estimated_belief_count_after_update"
            ],
            39,
        )

        # ---------------------------------------------
        # Previous Passport remains immutable
        # ---------------------------------------------

        self.assertEqual(
            previous_passport,
            previous_snapshot,
        )

        self.assertEqual(
            len(
                previous_passport[
                    "observed_measurements"
                ][
                    "beliefs_raw_1_to_7"
                ]
            ),
            4,
        )

        # ---------------------------------------------
        # Remaining estimates are recalculated
        # ---------------------------------------------

        old_matrix = np.asarray(
            previous_adaptive
            .participant
            .beliefs,
            dtype=np.float64,
        )

        new_matrix = np.asarray(
            updated_adaptive
            .participant
            .beliefs,
            dtype=np.float64,
        )

        target_i = MODES.index(
            mode
        )

        target_j = NEEDS.index(
            need
        )

        changed_other_estimated_cell = False

        for i, m in enumerate(
            MODES
        ):
            for j, n in enumerate(
                NEEDS
            ):

                if (
                    i == target_i
                    and j == target_j
                ):
                    continue

                meta = (
                    updated_adaptive
                    .measurement_provenance[
                        "beliefs"
                    ][
                        m
                    ][
                        n
                    ]
                )

                if (
                    meta[
                        "source"
                    ]
                    == "estimated"
                    and abs(
                        new_matrix[
                            i,
                            j
                        ]
                        - old_matrix[
                            i,
                            j
                        ]
                    ) > 1e-12
                ):
                    changed_other_estimated_cell = True
                    break

            if changed_other_estimated_cell:
                break

        self.assertTrue(
            changed_other_estimated_cell
        )

        # ---------------------------------------------
        # Revision 2 runs through unchanged HOTCO
        # ---------------------------------------------

        result2 = engine.simulate(
            updated_adaptive.participant
        )

        self.assertTrue(
            np.isfinite(
                result2.trajectory
            ).all()
        )

        # ---------------------------------------------
        # Revision-2 Passport provenance is truthful
        # ---------------------------------------------

        output2 = build_adaptive_cognitive_passport(
            updated_adaptive,
            result2,
            engine,
            lineage=lineage,
        )

        passport2 = output2[
            "cognitive_passport"
        ]

        self.assertEqual(
            passport2[
                "lineage"
            ][
                "revision"
            ],
            2,
        )

        self.assertEqual(
            passport2[
                "input_provenance"
            ][
                "observed_counts"
            ][
                "beliefs"
            ],
            5,
        )

        self.assertEqual(
            passport2[
                "input_provenance"
            ][
                "estimated_counts"
            ][
                "beliefs"
            ],
            39,
        )

        self.assertEqual(
            passport2[
                "topology"
            ][
                "belief_edges_observed"
            ],
            5,
        )

        self.assertEqual(
            passport2[
                "topology"
            ][
                "belief_edges_estimated"
            ],
            39,
        )

        self.assertEqual(
            len(
                passport2[
                    "observed_measurements"
                ][
                    "beliefs_raw_1_to_7"
                ]
            ),
            5,
        )

    def test_update_requires_explicit_confirmation(
        self,
    ):
        (
            _,
            passport,
            _,
        ) = build_revision_one()

        candidate = (
            passport[
                "adaptive_questioning"
            ][
                "candidate"
            ]
        )

        target = candidate[
            "target"
        ]

        with self.assertRaises(
            AdaptiveProfileUpdateError
        ):
            prepare_adaptive_profile_update(
                {
                    "schema_version":
                        ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,

                    "previous_passport":
                        passport,

                    "update": {
                        "question_id":
                            candidate[
                                "question_id"
                            ],

                        "target": {
                            "kind":
                                target[
                                    "kind"
                                ],

                            "mode":
                                target[
                                    "mode"
                                ],

                            "need":
                                target[
                                    "need"
                                ],
                        },

                        "raw_rating":
                            7,

                        "explicit_user_confirmation":
                            False,
                    },
                }
            )


if __name__ == "__main__":
    unittest.main()
