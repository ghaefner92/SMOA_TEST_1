from __future__ import annotations

import copy
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
from adaptive_profile_update import (
    ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,
)
from adaptive_question_trigger import (
    build_adaptive_micro_question,
)
from hotco_ct_v4_3 import HOTCOCTv43
from main import app


def build_revision_one_with_estimated_belief_question():
    start = start_adaptive_passport(
        {
            "schema_version":
                ONBOARDING_SCHEMA_VERSION,

            "agent_id":
                "adaptive-http-update-user",

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

    # Deterministic XAI scenario used only to produce
    # a provenance-aware belief question for this HTTP test.
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
            "Test setup requires an active question"
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
            "Test setup requires an estimated belief target"
        )

    passport[
        "adaptive_questioning"
    ] = question

    return passport


class AdaptiveProfileUpdateHTTPTests(
    unittest.TestCase
):

    @classmethod
    def setUpClass(cls):
        app.config.update(
            TESTING=True
        )

        cls.client = (
            app.test_client()
        )

    def test_confirmed_http_update_creates_revision_two_and_5_39_split(
        self,
    ):
        previous = (
            build_revision_one_with_estimated_belief_question()
        )

        previous_snapshot = copy.deepcopy(
            previous
        )

        candidate = (
            previous[
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

        previous_estimate = float(
            previous[
                "profile"
            ][
                "beliefs"
            ][
                mode
            ][
                need
            ]
        )

        new_rating = (
            1
            if previous_estimate >= 0.0
            else 7
        )

        response = self.client.post(
            "/api/dyconet/adaptive-passport/profile-update",
            json={
                "schema_version":
                    ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,

                "previous_passport":
                    previous,

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
            },
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        body = response.get_json()

        passport2 = body[
            "cognitive_passport"
        ]

        # ---------------------------------------------
        # Revision lineage
        # ---------------------------------------------

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
                "lineage"
            ][
                "parent_passport_id"
            ],
            previous[
                "lineage"
            ][
                "passport_id"
            ],
        )

        event = (
            passport2[
                "lineage"
            ][
                "last_update_event"
            ]
        )

        self.assertEqual(
            event[
                "measurement_transition"
            ],
            "estimated_to_observed",
        )

        # ---------------------------------------------
        # Measurement provenance
        # ---------------------------------------------

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

        # ---------------------------------------------
        # Raw measurements
        # ---------------------------------------------

        observed = (
            passport2[
                "observed_measurements"
            ][
                "beliefs_raw_1_to_7"
            ]
        )

        self.assertEqual(
            len(
                observed
            ),
            5,
        )

        self.assertEqual(
            observed[
                f"{mode}__{need}"
            ],
            new_rating,
        )

        self.assertEqual(
            passport2[
                "measurement_provenance"
            ][
                "beliefs"
            ][
                mode
            ][
                need
            ][
                "source"
            ],
            "observed",
        )

        # ---------------------------------------------
        # Previous Passport was not mutated
        # ---------------------------------------------

        self.assertEqual(
            previous,
            previous_snapshot,
        )

        self.assertEqual(
            len(
                previous[
                    "observed_measurements"
                ][
                    "beliefs_raw_1_to_7"
                ]
            ),
            4,
        )

        # ---------------------------------------------
        # HOTCO ran and returned a deliberation
        # ---------------------------------------------

        self.assertIn(
            "deliberation",
            passport2,
        )

        self.assertIn(
            "terminal_tendency",
            passport2[
                "deliberation"
            ],
        )

        self.assertIn(
            "xai_diagnostics",
            passport2,
        )

    def test_http_update_without_confirmation_returns_422(
        self,
    ):
        previous = (
            build_revision_one_with_estimated_belief_question()
        )

        candidate = (
            previous[
                "adaptive_questioning"
            ][
                "candidate"
            ]
        )

        target = (
            candidate[
                "target"
            ]
        )

        response = self.client.post(
            "/api/dyconet/adaptive-passport/profile-update",
            json={
                "schema_version":
                    ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,

                "previous_passport":
                    previous,

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
            },
        )

        self.assertEqual(
            response.status_code,
            422,
        )

        body = response.get_json()

        self.assertEqual(
            body[
                "error"
            ],
            "invalid_adaptive_profile_update",
        )

        self.assertFalse(
            body[
                "automatic_update_performed"
            ]
        )

        self.assertTrue(
            body[
                "explicit_confirmation_required"
            ]
        )

        self.assertFalse(
            body[
                "hotco_parameters_changed"
            ]
        )

    def test_health_advertises_longitudinal_adaptive_updates(
        self,
    ):
        response = self.client.get(
            "/health"
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        adaptive = (
            response.get_json()[
                "adaptive_passport"
            ]
        )

        self.assertEqual(
            adaptive[
                "profile_update_schema_version"
            ],
            ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,
        )

        self.assertTrue(
            adaptive[
                "longitudinal_estimated_to_observed_updates"
            ]
        )

        self.assertTrue(
            adaptive[
                "strict_dyconet_endpoint_unchanged"
            ]
        )


if __name__ == "__main__":
    unittest.main()
