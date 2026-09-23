from __future__ import annotations

import json
from pathlib import Path
import unittest

import numpy as np

from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import MODES, parse_participant_input
from passport_xai import build_cognitive_passport


SERVICE_DIR = Path(__file__).resolve().parents[1]


def example_payload():
    return json.loads((SERVICE_DIR / "example_request.json").read_text(encoding="utf-8"))


class Phase1AvailabilityGatingTests(unittest.TestCase):
    def setUp(self):
        self.engine = HOTCOCTv43()

    def test_unavailable_mode_representation_cannot_change_active_trajectory(self):
        base_payload = example_payload()
        base_payload["responses"]["availability"]["car"] = False
        base = self.engine.simulate(parse_participant_input(base_payload)).trajectory[0]

        changed_payload = example_payload()
        changed_payload["responses"]["availability"]["car"] = False
        # Change the entire latent representation of the unavailable car mode.
        # Because q_car == 0, these values may describe the user's beliefs/affect
        # but must not influence needs or any active action/valence trajectory.
        changed_payload["responses"]["beliefs"]["car"] = {
            need: (1 if index % 2 == 0 else 7)
            for index, need in enumerate(changed_payload["responses"]["beliefs"]["car"])
        }
        changed_payload["responses"]["valences"]["car"] = -3
        changed = self.engine.simulate(parse_participant_input(changed_payload)).trajectory[0]

        car_index = MODES.index("car")
        car_action_index = self.engine.n_needs + car_index
        car_valence_index = self.engine.n_needs + self.engine.n_modes + car_index

        self.assertTrue(np.array_equal(base[:, car_action_index], np.zeros(base.shape[0])))
        self.assertTrue(np.array_equal(changed[:, car_action_index], np.zeros(changed.shape[0])))

        # The unavailable mode's own valence state can differ because its initial
        # reported valence differs. Every other state must remain invariant.
        compared_indices = [
            i for i in range(self.engine.n_nodes) if i != car_valence_index
        ]
        self.assertTrue(
            np.allclose(
                base[:, compared_indices],
                changed[:, compared_indices],
                atol=1e-12,
                rtol=1e-12,
            )
        )

    def test_single_available_mode_has_unit_readout_and_no_rival(self):
        payload = example_payload()
        payload["responses"]["availability"] = {
            "car": False,
            "bike": True,
            "pt": False,
            "walk": False,
        }
        participant = parse_participant_input(payload)
        result = self.engine.simulate(participant)
        passport = build_cognitive_passport(participant, result, self.engine)[
            "cognitive_passport"
        ]

        readout = passport["deliberation"]["comparative_readout"]
        self.assertAlmostEqual(readout["bike"], 1.0, places=12)
        self.assertAlmostEqual(readout["car"], 0.0, places=12)
        self.assertAlmostEqual(readout["pt"], 0.0, places=12)
        self.assertAlmostEqual(readout["walk"], 0.0, places=12)
        self.assertEqual(passport["deliberation"]["terminal_tendency"], "bike")
        self.assertIsNone(passport["xai_diagnostics"]["competition"]["main_rival"])
        self.assertEqual(
            passport["xai_diagnostics"]["competition"]["winner_lead_fraction"],
            1.0,
        )


if __name__ == "__main__":
    unittest.main()
