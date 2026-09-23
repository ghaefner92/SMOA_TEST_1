"""Selectable contextual forcing strategies sharing the production HOTCO solver."""
from __future__ import annotations

import math
import os
from dataclasses import replace

import numpy as np

from context_perturbation import ContextPerturbationBuilder, ContextPerturbationResult, NEED_TARGETS
from hotco_ct_v4_3 import HOTCOCTv43, SimulationResult
from input_mapping_v4_3 import NEEDS
from route_context import ContextStatus, RouteContext

F0_LEGACY_V1 = "F0_LEGACY_V1"
F3_NEED_ONLY_V1 = "F3_NEED_ONLY_V1"
EXPERIMENTAL_UNCALIBRATED = "EXPERIMENTAL_UNCALIBRATED"
DEFAULT_CONTEXT_MODEL = F3_NEED_ONLY_V1
DEFAULT_RHO = 0.25


def active_context_model() -> str:
    value = os.environ.get("IMIQ_CONTEXT_MODEL", "F3").strip().upper()
    if value == "F3": return F3_NEED_ONLY_V1
    if value == "F0": return F0_LEGACY_V1
    raise ValueError("IMIQ_CONTEXT_MODEL must be F3 or F0")


def h_needs(baseline: SimulationResult) -> float:
    trajectory = baseline.trajectory[0, :, :len(NEEDS)]
    weighted = np.einsum("tij,tj->ti", np.broadcast_to(baseline.topology[0], (trajectory.shape[0], *baseline.topology[0].shape)), baseline.trajectory[0])[:, :len(NEEDS)]
    value = float(np.median(np.sqrt(np.mean(np.square(weighted), axis=0))))
    if not math.isfinite(value): raise ValueError("H_NEEDS is not finite")
    return value


def build_f3(route: RouteContext, baseline: SimulationResult, engine: HOTCOCTv43, tolerances: dict[str, float], rho: float = DEFAULT_RHO) -> ContextPerturbationResult:
    if not 0.0 <= rho <= 1.0: raise ValueError("F3 rho must be in [0,1]")
    legacy = ContextPerturbationBuilder(tolerances).build(route)
    route_exposures = [item for item in legacy.effective_exposures if item.source_scope == "route"]
    z = {need: 0.0 for need in NEEDS}
    for exposure in route_exposures:
        if exposure.effective_value is None: continue
        for need, beta in NEED_TARGETS.get(exposure.stressor, {}).items(): z[need] += beta * exposure.effective_value
    scale = h_needs(baseline)
    need = {need: rho * scale * math.tanh(z[need]) for need in NEEDS}
    return replace(
        legacy,
        perturbation_version="f3-need-only-v1",
        need_perturbations=need,
        action_perturbations={mode: 0.0 for mode in legacy.action_perturbations},
        valence_perturbations={mode: 0.0 for mode in legacy.valence_perturbations},
        contributions=tuple(item for item in legacy.contributions if item.target_family == "need"),
        warnings=tuple((*legacy.warnings, "F3 need-only forcing; experimental and not empirically calibrated")),
        data_coverage={**legacy.data_coverage, "context_model": F3_NEED_ONLY_V1, "rho": rho, "H_NEEDS": scale, "z": z},
    )


def build_context_perturbation(route: RouteContext, baseline: SimulationResult, engine: HOTCOCTv43, tolerances: dict[str, float]) -> tuple[ContextPerturbationResult, str, float | None]:
    model = active_context_model()
    if model == F0_LEGACY_V1:
        return ContextPerturbationBuilder(tolerances).build(route), model, None
    rho = float(os.environ.get("IMIQ_CONTEXT_RHO", DEFAULT_RHO))
    return build_f3(route, baseline, engine, tolerances, rho), model, rho
