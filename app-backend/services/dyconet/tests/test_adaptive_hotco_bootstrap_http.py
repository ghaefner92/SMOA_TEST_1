from __future__ import annotations

import unittest

from adaptive_hotco_bootstrap import (
    ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION,
)
from adaptive_passport_onboarding import (
    ONBOARDING_SCHEMA_VERSION,
    complete_adaptive_passport,
    start_adaptive_passport,
)
from main import app


def completed_profile():
    start = start_adaptive_passport(
        {
            "schema_version":
                ONBOARDING_SCHEMA_VERSION,

            "agent_id":
                "bootstrap-http-user",

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


class AdaptiveHotcoBootstrapHTTPTests(
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

    def test_bootstrap_creates_revision_one_adaptive_passport(
        self,
    ):
        response = self.client.post(
            "/api/dyconet/adaptive-passport/bootstrap",
            json={
                "schema_version":
                    ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION,

                "completed_passport":
                    completed_profile(),

                "availability": {
                    "car": True,
                    "bike": True,
                    "pt": True,
                    "walk": True,
                },
            },
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        body = response.get_json()

        passport = body[
            "cognitive_passport"
        ]

        self.assertEqual(
            passport[
                "lineage"
            ][
                "revision"
            ],
            1,
        )

        self.assertEqual(
            passport[
                "lineage"
            ][
                "origin"
            ],
            "initial_adaptive_calibration",
        )

        self.assertEqual(
            passport[
                "input_provenance"
            ][
                "observed_counts"
            ][
                "beliefs"
            ],
            4,
        )

        self.assertEqual(
            passport[
                "input_provenance"
            ][
                "estimated_counts"
            ][
                "beliefs"
            ],
            40,
        )

        self.assertEqual(
            len(
                passport[
                    "observed_measurements"
                ][
                    "beliefs_raw_1_to_7"
                ]
            ),
            4,
        )

        self.assertIn(
            "deliberation",
            passport,
        )

        self.assertIn(
            "xai_diagnostics",
            passport,
        )

    def test_bootstrap_rejects_all_modes_unavailable(
        self,
    ):
        response = self.client.post(
            "/api/dyconet/adaptive-passport/bootstrap",
            json={
                "schema_version":
                    ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION,

                "completed_passport":
                    completed_profile(),

                "availability": {
                    "car": False,
                    "bike": False,
                    "pt": False,
                    "walk": False,
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
            "invalid_adaptive_hotco_bootstrap",
        )

        self.assertFalse(
            body[
                "availability_inferred"
            ]
        )

    def test_health_advertises_bootstrap_contract(
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
                "bootstrap_schema_version"
            ],
            ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION,
        )

        self.assertTrue(
            adaptive[
                "initial_hotco_bootstrap"
            ]
        )


if __name__ == "__main__":
    unittest.main()
