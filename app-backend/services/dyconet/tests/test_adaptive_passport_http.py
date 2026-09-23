from __future__ import annotations

import unittest

from adaptive_passport_onboarding import ONBOARDING_SCHEMA_VERSION
from main import app


def start_payload():
    return {
        "schema_version": ONBOARDING_SCHEMA_VERSION,
        "agent_id": "http-test-user",
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


class AdaptivePassportHTTPTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        app.config.update(
            TESTING=True
        )

        cls.client = (
            app.test_client()
        )

    def test_health_exposes_experimental_adaptive_passport(self):
        response = self.client.get(
            "/health"
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        body = response.get_json()

        adaptive = body[
            "adaptive_passport"
        ]

        self.assertEqual(
            adaptive["status"],
            "experimental",
        )

        self.assertEqual(
            adaptive["model_version"],
            "v1",
        )

        self.assertEqual(
            adaptive["initial_question_count"],
            4,
        )

        self.assertTrue(
            adaptive[
                "estimated_beliefs"
            ]
        )

        self.assertTrue(
            adaptive[
                "explicit_belief_provenance"
            ]
        )

        self.assertTrue(
            adaptive[
                "strict_dyconet_endpoint_unchanged"
            ]
        )

        # This remains the contract of the original
        # /api/dyconet endpoint, not the adaptive endpoint.
        self.assertFalse(
            body["imputation"]
        )

    def test_start_endpoint_returns_four_questions(self):
        response = self.client.post(
            "/api/dyconet/adaptive-passport/start",
            json=start_payload(),
        )

        self.assertEqual(
            response.status_code,
            200,
        )

        body = response.get_json()

        self.assertEqual(
            body["stage"],
            "questions",
        )

        self.assertEqual(
            body["question_count"],
            4,
        )

        self.assertFalse(
            body["adaptive_initial_selection"]
        )

        self.assertFalse(
            body["policy"]["hotco_run"]
        )

        cells = [
            item["cell_id"]
            for item in body["questions"]
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

    def test_complete_endpoint_returns_dense_profile_and_provenance(self):
        start_response = (
            self.client.post(
                "/api/dyconet/adaptive-passport/start",
                json=start_payload(),
            )
        )

        self.assertEqual(
            start_response.status_code,
            200,
        )

        start = (
            start_response.get_json()
        )

        complete_response = (
            self.client.post(
                "/api/dyconet/adaptive-passport/complete",
                json={
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
                },
            )
        )

        self.assertEqual(
            complete_response.status_code,
            200,
        )

        body = (
            complete_response.get_json()
        )

        self.assertEqual(
            body["stage"],
            "complete",
        )

        self.assertTrue(
            body["ready_for_hotco"]
        )

        self.assertFalse(
            body["hotco_run"]
        )

        beliefs = (
            body[
                "profile"
            ][
                "beliefs"
            ]
        )

        provenance = (
            body[
                "measurement_provenance"
            ][
                "beliefs"
            ]
        )

        n_beliefs = sum(
            len(values)
            for values
            in beliefs.values()
        )

        self.assertEqual(
            n_beliefs,
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

    def test_invalid_completion_returns_422(self):
        start_response = (
            self.client.post(
                "/api/dyconet/adaptive-passport/start",
                json=start_payload(),
            )
        )

        start = (
            start_response.get_json()
        )

        response = (
            self.client.post(
                "/api/dyconet/adaptive-passport/complete",
                json={
                    "schema_version":
                        ONBOARDING_SCHEMA_VERSION,

                    "start":
                        start,

                    "answers": {
                        "pt__comfort": 4,
                        "walk__comfort": 4,
                        "bike__privacy": 4,
                    },
                },
            )
        )

        self.assertEqual(
            response.status_code,
            422,
        )

        body = response.get_json()

        self.assertEqual(
            body["error"],
            "invalid_adaptive_passport_completion",
        )

        self.assertFalse(
            body["hotco_run"]
        )

        self.assertFalse(
            body[
                "automatic_profile_update"
            ]
        )

    def test_original_dyconet_remains_strict(self):
        # Deliberately send the short adaptive input to the
        # original endpoint. It must NOT silently estimate
        # the missing 44-cell questionnaire contract.
        response = self.client.post(
            "/api/dyconet",
            json=start_payload(),
        )

        self.assertEqual(
            response.status_code,
            422,
        )

        body = response.get_json()

        self.assertEqual(
            body["error"],
            "invalid_questionnaire_input",
        )

        self.assertFalse(
            body["imputation_performed"]
        )


if __name__ == "__main__":
    unittest.main()
