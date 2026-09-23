from __future__ import annotations

import copy
import unittest

import numpy as np

from adaptive_hotco_adapter import (
    AdaptiveHotcoAdapterError,
    build_adaptive_hotco_input,
)
from adaptive_passport_onboarding import (
    ONBOARDING_SCHEMA_VERSION,
    complete_adaptive_passport,
    start_adaptive_passport,
)
from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import MODES, NEEDS


def start_payload():
    return {
        "schema_version": ONBOARDING_SCHEMA_VERSION,
        "agent_id": "adaptive-hotco-test-user",
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


def completed_passport():
    start = start_adaptive_passport(
        start_payload()
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


def full_availability():
    return {
        "car": True,
        "bike": True,
        "pt": True,
        "walk": True,
    }


class AdaptiveHotcoAdapterTests(unittest.TestCase):

    def test_adapter_preserves_four_raw_beliefs_but_supplies_44_to_hotco(self):
        completed = completed_passport()

        adaptive = build_adaptive_hotco_input(
            completed,
            availability=full_availability(),
        )

        participant = adaptive.participant

        # Complete normalized HOTCO matrix.
        beliefs = np.asarray(
            participant.beliefs,
            dtype=np.float64,
        )

        self.assertEqual(
            beliefs.shape,
            (4, 11),
        )

        self.assertEqual(
            beliefs.size,
            44,
        )

        self.assertTrue(
            np.isfinite(
                beliefs
            ).all()
        )

        self.assertTrue(
            np.all(
                beliefs >= -1.0
            )
        )

        self.assertTrue(
            np.all(
                beliefs <= 1.0
            )
        )

        # Raw questionnaire beliefs remain sparse:
        # only the four explicitly answered items exist.
        raw_count = sum(
            len(
                participant.raw_beliefs[
                    mode
                ]
            )
            for mode in MODES
        )

        self.assertEqual(
            raw_count,
            4,
        )

        self.assertEqual(
            participant.raw_beliefs[
                "pt"
            ][
                "comfort"
            ],
            1,
        )

        self.assertEqual(
            participant.raw_beliefs[
                "walk"
            ][
                "comfort"
            ],
            3,
        )

        self.assertEqual(
            participant.raw_beliefs[
                "bike"
            ][
                "privacy"
            ],
            5,
        )

        self.assertEqual(
            participant.raw_beliefs[
                "car"
            ][
                "reliable"
            ],
            7,
        )

        self.assertEqual(
            adaptive.adaptive_metadata[
                "fabricated_raw_beliefs"
            ],
            0,
        )

        self.assertEqual(
            adaptive.measurement_provenance[
                "belief_counts"
            ][
                "observed"
            ],
            4,
        )

        self.assertEqual(
            adaptive.measurement_provenance[
                "belief_counts"
            ][
                "estimated"
            ],
            40,
        )

    def test_adaptive_participant_runs_hotco_end_to_end(self):
        adaptive = build_adaptive_hotco_input(
            completed_passport(),
            availability=full_availability(),
        )

        engine = HOTCOCTv43()

        result = engine.simulate(
            adaptive.participant
        )

        self.assertEqual(
            result.trajectory.shape,
            (
                1,
                engine.n_steps + 1,
                engine.n_nodes,
            ),
        )

        self.assertEqual(
            result.topology.shape,
            (
                1,
                engine.n_nodes,
                engine.n_nodes,
            ),
        )

        self.assertEqual(
            result.availability.shape,
            (
                1,
                len(
                    MODES
                ),
            ),
        )

        self.assertTrue(
            np.isfinite(
                result.trajectory
            ).all()
        )

        self.assertTrue(
            np.isfinite(
                result.topology
            ).all()
        )

        final_state = (
            result.final_state[
                0
            ]
        )

        self.assertEqual(
            len(
                final_state
            ),
            len(NEEDS)
            + 2 * len(MODES),
        )

    def test_estimated_belief_tampering_is_rejected(self):
        completed = completed_passport()

        tampered = copy.deepcopy(
            completed
        )

        original = (
            tampered[
                "profile"
            ][
                "beliefs"
            ][
                "car"
            ][
                "comfort"
            ]
        )

        tampered[
            "profile"
        ][
            "beliefs"
        ][
            "car"
        ][
            "comfort"
        ] = (
            0.5
            if original < 0.4
            else -0.5
        )

        with self.assertRaises(
            AdaptiveHotcoAdapterError
        ):
            build_adaptive_hotco_input(
                tampered,
                availability=
                    full_availability(),
            )

    def test_observed_belief_tampering_is_rejected(self):
        completed = completed_passport()

        tampered = copy.deepcopy(
            completed
        )

        tampered[
            "observed_measurements"
        ][
            "beliefs_raw_1_to_7"
        ][
            "pt__comfort"
        ] = 7

        with self.assertRaises(
            AdaptiveHotcoAdapterError
        ):
            build_adaptive_hotco_input(
                tampered,
                availability=
                    full_availability(),
            )

    def test_availability_remains_explicit_and_gates_hotco(self):
        availability = {
            "car": False,
            "bike": True,
            "pt": True,
            "walk": False,
        }

        adaptive = build_adaptive_hotco_input(
            completed_passport(),
            availability=availability,
        )

        participant = (
            adaptive.participant
        )

        self.assertEqual(
            participant.availability,
            [
                0.0,
                1.0,
                1.0,
                0.0,
            ],
        )

        engine = HOTCOCTv43()

        result = engine.simulate(
            participant
        )

        action_slice = slice(
            engine.n_needs,
            engine.n_needs
            + engine.n_modes,
        )

        actions = (
            result.trajectory[
                0,
                :,
                action_slice,
            ]
        )

        car_index = (
            MODES.index(
                "car"
            )
        )

        walk_index = (
            MODES.index(
                "walk"
            )
        )

        self.assertTrue(
            np.array_equal(
                actions[
                    :,
                    car_index
                ],
                np.zeros(
                    actions.shape[0]
                ),
            )
        )

        self.assertTrue(
            np.array_equal(
                actions[
                    :,
                    walk_index
                ],
                np.zeros(
                    actions.shape[0]
                ),
            )
        )

    def test_all_modes_unavailable_is_rejected(self):
        with self.assertRaises(
            AdaptiveHotcoAdapterError
        ):
            build_adaptive_hotco_input(
                completed_passport(),
                availability={
                    "car": False,
                    "bike": False,
                    "pt": False,
                    "walk": False,
                },
            )


if __name__ == "__main__":
    unittest.main()
