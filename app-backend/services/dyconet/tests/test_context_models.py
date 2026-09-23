import unittest
import numpy as np

from context_models import build_f3, h_needs
from context_perturbation import ContextPerturbationBuilder
from hotco_ct_v4_3 import HOTCOCTv43
from input_mapping_v4_3 import parse_participant_input
from route_context import CandidateRouteInput, ContextObservation, ContextStatus, Coordinate
from route_context_adapter import RouteContextAdapter
import json


class F3NeedOnlyTests(unittest.TestCase):
    def test_need_only_invariances_and_no_leakage(self):
        with open("example_request.json", encoding="utf-8") as handle:
            participant = parse_participant_input(json.load(handle))
        engine = HOTCOCTv43(); baseline = engine.simulate(participant)
        origin, destination = Coordinate(52.13, 11.63), Coordinate(52.14, 11.64)
        candidate = CandidateRouteInput("r", origin, destination, summary={"duration_seconds": 60}, transport_modes=("bike",))
        rain = ContextObservation("synthetic", "rain", 10.0, 10.0, status=ContextStatus.OBSERVED)
        route = RouteContextAdapter().enrich(candidate, observations=(rain,))
        result = build_f3(route, baseline, engine, {"rain": 0., "crowding": 1., "darkness": 1., "traffic": 1., "temperature": 1.})
        self.assertTrue(h_needs(baseline) >= 0)
        self.assertTrue(any(value != 0 for value in result.need_perturbations.values()))
        self.assertTrue(all(value == 0 for value in result.action_perturbations.values()))
        self.assertTrue(all(value == 0 for value in result.valence_perturbations.values()))
        contextual = engine.simulate(participant, context_perturbation=result)
        self.assertTrue(np.isfinite(contextual.trajectory).all())
        zero = build_f3(route, baseline, engine, {"rain": 1., "crowding": 1., "darkness": 1., "traffic": 1., "temperature": 1.})
        self.assertTrue(all(value == 0 for value in zero.need_perturbations.values()))

    def test_frozen_legacy_f0_and_f3_zero_invariances(self):
        with open("example_request.json", encoding="utf-8") as handle: participant = parse_participant_input(json.load(handle))
        engine = HOTCOCTv43(); baseline = engine.simulate(participant)
        o, d = Coordinate(52.13, 11.63), Coordinate(52.14, 11.64)
        route = RouteContextAdapter().enrich(CandidateRouteInput("frozen", o, d, summary={"duration_seconds": 60}, transport_modes=("bike",)), observations=(ContextObservation("synthetic", "rain", 10., 10., status=ContextStatus.OBSERVED),))
        tolerances = {"rain": 0., "crowding": 1., "darkness": 1., "traffic": 1., "temperature": 1.}
        f0 = engine.simulate(participant, context_perturbation=ContextPerturbationBuilder(tolerances).build(route))
        expected = [0.03648625491160144, 0.00037545886182598195, 0.22816687175264733, 0.2404069879756637, 0.01725393339931267, 0.04621289374347203, 0.872152460566592, 0.12059978218354456, 0.8711728870245811, 0.15256274174033008, 0.169686532045544, 0.3423490385776361, 0.0, 0.1083262314014408, 0.20958195232821641, 0.5320110248289381, -0.8695652173913024, 0.2567978953975957, 0.41710555953098943]
        np.testing.assert_allclose(f0.final_state[0], expected, rtol=0, atol=1e-12)
        zero_context = RouteContextAdapter().enrich(CandidateRouteInput("zero", o, d, transport_modes=("bike",)))
        f3_zero = engine.simulate(participant, context_perturbation=build_f3(zero_context, baseline, engine, tolerances))
        f3_rho_zero = engine.simulate(participant, context_perturbation=build_f3(route, baseline, engine, tolerances, rho=0.0))
        np.testing.assert_allclose(f3_zero.final_state, baseline.final_state, rtol=0, atol=1e-12)
        np.testing.assert_allclose(f3_rho_zero.final_state, baseline.final_state, rtol=0, atol=1e-12)
