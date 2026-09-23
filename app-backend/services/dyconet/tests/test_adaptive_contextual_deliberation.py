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
from route_context import CandidateRouteInput, Coordinate


O = Coordinate(52.13, 11.63)
D = Coordinate(52.14, 11.64)


def completed_profile():
    start = start_adaptive_passport(
        {
            "schema_version": ONBOARDING_SCHEMA_VERSION,
            "agent_id": "adaptive-context-user",
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
            "schema_version": ONBOARDING_SCHEMA_VERSION,
            "start": start,
            "answers": {
                "pt__comfort": 1,
                "walk__comfort": 3,
                "bike__privacy": 5,
                "car__reliable": 7,
            },
        }
    )


def candidate():
    return CandidateRouteInput(
        "adaptive-r1",
        O,
        D,
        summary={
            "duration_seconds": 600,
            "distance_meters": 2000,
        },
        legs=(
            {
                "segment_id": "adaptive-r1-s1",
                "mode": "bike",
                "start": O.to_dict(),
                "end": D.to_dict(),
                "duration_seconds": 600,
                "distance_meters": 2000,
            },
        ),
        transport_modes=("bike",),
    )


class AdaptiveContextualDeliberationTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        app.config.update(TESTING=True)
        cls.client = app.test_client()

    def bootstrap_passport(self):
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

                "environmental_tolerances": {
                    "rain": 0.5,
                    "crowding": 0.5,
                    "darkness": 0.5,
                    "traffic": 0.5,
                    "temperature": 0.5,
                },
            },
        )

        self.assertEqual(response.status_code, 200)
        return response.get_json()

    def test_adaptive_passport_runs_contextual_deliberation_without_fabricating_raw_beliefs(
        self,
    ):
        passport = self.bootstrap_passport()

        cp = passport["cognitive_passport"]

        self.assertEqual(
            cp["input_provenance"]["observed_counts"]["beliefs"],
            4,
        )
        self.assertEqual(
            cp["input_provenance"]["estimated_counts"]["beliefs"],
            40,
        )
        self.assertEqual(
            len(cp["observed_measurements"]["beliefs_raw_1_to_7"]),
            4,
        )

        response = self.client.post(
            "/api/dyconet/contextual-deliberation",
            json={
                "schema_version":
                    "contextual-deliberation-request-v1",

                "search_id":
                    "adaptive-context-search",

                "timestamp":
                    "2026-09-17T12:00:00Z",

                "cognitive_passport":
                    passport,

                "tolerance_profile": {
                    "rain": 0.5,
                    "crowding": 0.5,
                    "darkness": 0.5,
                    "traffic": 0.5,
                    "temperature": 0.5,
                },

                "candidate_routes": [
                    candidate().to_dict()
                ],

                "contextual_query": {
                    "query_orion": False,
                },
            },
        )

        self.assertEqual(
            response.status_code,
            200,
            response.get_json(),
        )

        body = response.get_json()

        self.assertEqual(
            body["search_id"],
            "adaptive-context-search",
        )

        self.assertEqual(
            len(body["candidate_results"]),
            1,
        )

        self.assertEqual(
            body["candidate_results"][0]["route_id"],
            "adaptive-r1",
        )

        self.assertIn(
            "baseline",
            body,
        )

        self.assertIn(
            "hotco",
            body["candidate_results"][0],
        )


if __name__ == "__main__":
    unittest.main()