"""Mechanistic XAI diagnostics derived from HOTCO-CT trajectories.

This module does not alter the HOTCO-CT state equation, solver, stopping rule,
or terminal readout. It converts an already-computed trajectory into
reproducible process diagnostics that can be serialized in a Cognitive
Passport and, later, verbalized by an LLM.

The diagnostics describe properties of the simulation. They must not be
interpreted as direct measurements of subjective conflict, experienced
emotion, causal importance, or human reaction time.
"""

from __future__ import annotations

from typing import Any, Dict, Mapping, Optional

import numpy as np

from hotco_ct_v4_3 import HOTCOCTv43, SimulationResult
from input_mapping_v4_3 import MODES, NEEDS, ParticipantInput


XAI_DIAGNOSTICS_VERSION = "1.0"

# These thresholds only classify explanations; they never feed back into the
# HOTCO-CT dynamics. The practical-margin threshold itself comes from the
# declared HOTCO-CT parameterization.
PERSISTENT_RIVALRY_FRACTION = 0.25


def ambiguity_state(competition: Mapping[str, Any] | None) -> str:
    """Presentation classification using existing competition diagnostics only."""
    if not isinstance(competition, Mapping):
        return "UNKNOWN"
    try:
        if float(competition.get("co_dominance_fraction")) >= float(competition.get("persistent_rivalry_fraction", PERSISTENT_RIVALRY_FRACTION)):
            return "NEAR_TIE"
        margin = competition.get("final_margin", competition.get("minimum_top2_gap"))
        threshold = competition.get("co_dominance_threshold")
        if margin is not None and threshold is not None and float(margin) <= float(threshold):
            return "NEAR_TIE"
        return "CLEAR"
    except (TypeError, ValueError):
        return "UNKNOWN"
HIGH_FRICTION_THRESHOLD = 0.50
EMERGENCE_EPS = 1e-9


def _number(value: float | np.number, digits: int = 10) -> float:
    return round(float(value), digits)


def _optional_number(value: Optional[float], digits: int = 10) -> Optional[float]:
    return None if value is None else round(float(value), digits)


def _signed_friction(cognitive: float, affective: float) -> float:
    """Opposition between two signed supports, in [0, 1].

    Zero means the channels do not oppose one another. When they have opposite
    signs, the score approaches one as their absolute magnitudes become equal.
    It is a descriptive cancellation index, not a causal decomposition.
    """

    if cognitive * affective >= 0.0:
        return 0.0
    denominator = abs(cognitive) + abs(affective)
    if denominator == 0.0:
        return 0.0
    return 1.0 - abs(cognitive + affective) / denominator


def _mixed_support(positive: float, negative: float) -> float:
    denominator = positive + negative
    if denominator == 0.0:
        return 0.0
    return 2.0 * min(positive, negative) / denominator


def _competition_diagnostics(
    actions: np.ndarray,
    availability: np.ndarray,
    engine: HOTCOCTv43,
) -> Dict[str, Any]:
    p = engine.parameters
    active_indices = np.where(availability == 1)[0]
    active_actions = actions[:, active_indices]

    # All actions start at zero by construction. Excluding the pre-emergence
    # tie prevents the initial condition from being mislabeled as rivalry.
    emergence_candidates = np.where(np.max(active_actions, axis=1) > EMERGENCE_EPS)[0]
    start_index = int(emergence_candidates[0]) if emergence_candidates.size else 0
    evaluation = active_actions[start_index:]

    terminal_order = np.argsort(active_actions[-1])[::-1]
    winner_index = int(active_indices[terminal_order[0]])
    rival_index: Optional[int] = (
        int(active_indices[terminal_order[1]]) if active_indices.size > 1 else None
    )

    if active_indices.size == 1:
        co_dominance_fraction = 0.0
        mean_top2_gap = 1.0
        minimum_top2_gap = 1.0
        winner_lead_fraction = 1.0
        rival_lead_fraction = 0.0
    else:
        ordered = np.sort(evaluation, axis=1)[:, ::-1]
        top2_gap = ordered[:, 0] - ordered[:, 1]
        winner_local = int(np.where(active_indices == winner_index)[0][0])
        rival_local = int(np.where(active_indices == rival_index)[0][0])
        pair_gap = np.abs(evaluation[:, winner_local] - evaluation[:, rival_local])
        co_dominance_fraction = float(
            np.mean(pair_gap <= p.practical_margin_threshold)
        )
        mean_top2_gap = float(np.mean(top2_gap))
        minimum_top2_gap = float(np.min(top2_gap))
        winner_others = np.delete(evaluation, winner_local, axis=1)
        rival_others = np.delete(evaluation, rival_local, axis=1)
        winner_lead_fraction = float(
            np.mean(
                evaluation[:, winner_local] - np.max(winner_others, axis=1)
                > p.practical_margin_threshold
            )
        )
        rival_lead_fraction = float(
            np.mean(
                evaluation[:, rival_local] - np.max(rival_others, axis=1)
                > p.practical_margin_threshold
            )
        )

    return {
        "winner": MODES[winner_index],
        "main_rival": MODES[rival_index] if rival_index is not None else None,
        "evaluation_start_time_model_units": _number(start_index * p.dt),
        "co_dominance_fraction": _number(co_dominance_fraction),
        "mean_top2_gap": _number(mean_top2_gap),
        "minimum_top2_gap": _number(minimum_top2_gap),
        "winner_lead_fraction": _number(winner_lead_fraction),
        "main_rival_lead_fraction": _number(rival_lead_fraction),
        "co_dominance_threshold": p.practical_margin_threshold,
        "interpretation": (
            "trajectory competition diagnostic; co-dominance means the two named "
            "actions were within the declared practical action-margin threshold"
        ),
        "_start_index": start_index,
        "_winner_index": winner_index,
        "_rival_index": rival_index,
    }


def _leadership_diagnostics(
    actions: np.ndarray,
    availability: np.ndarray,
    engine: HOTCOCTv43,
    start_index: int,
    final_winner_index: int,
) -> Dict[str, Any]:
    p = engine.parameters
    active_indices = np.where(availability == 1)[0]

    identifiable: list[tuple[int, int]] = []
    for time_index in range(start_index, actions.shape[0]):
        row = actions[time_index, active_indices]
        if active_indices.size == 1:
            identifiable.append((time_index, int(active_indices[0])))
            continue
        order = np.argsort(row)[::-1]
        margin = float(row[order[0]] - row[order[1]])
        if margin > p.practical_margin_threshold:
            identifiable.append((time_index, int(active_indices[order[0]])))

    first_leader: Optional[int] = identifiable[0][1] if identifiable else None
    first_time: Optional[float] = identifiable[0][0] * p.dt if identifiable else None

    transitions: list[tuple[int, int]] = []
    last: Optional[int] = None
    for time_index, leader in identifiable:
        if leader != last:
            transitions.append((time_index, leader))
            last = leader

    switch_count = max(0, len(transitions) - 1)

    terminal_active = actions[-1, active_indices]
    if active_indices.size == 1:
        final_identifiable = True
    else:
        terminal_order = np.argsort(terminal_active)[::-1]
        terminal_margin = float(
            terminal_active[terminal_order[0]] - terminal_active[terminal_order[1]]
        )
        final_identifiable = terminal_margin > p.practical_margin_threshold

    # A "final leader acquired" time is only meaningful when the last
    # identifiable leadership segment belongs to the terminal winner.
    final_acquired_time: Optional[float] = None
    if transitions and transitions[-1][1] == final_winner_index:
        final_acquired_time = transitions[-1][0] * p.dt

    return {
        "first_identifiable_leader": (
            MODES[first_leader] if first_leader is not None else None
        ),
        "first_identifiable_leader_time_model_units": _optional_number(first_time),
        "final_leader": MODES[final_winner_index],
        "final_leader_identifiable": bool(final_identifiable),
        "final_leader_acquired_time_model_units": _optional_number(final_acquired_time),
        "winner_switch_count": int(switch_count),
        "final_winner_was_first_identifiable_leader": (
            first_leader == final_winner_index if first_leader is not None else None
        ),
        "interpretation": (
            "leader labels use the declared practical action-margin threshold; "
            "ambiguous time points are not forced to have a leader"
        ),
    }


def _support_diagnostics(
    trajectory: np.ndarray,
    participant: ParticipantInput,
    availability: np.ndarray,
    engine: HOTCOCTv43,
    start_index: int,
) -> Dict[str, Any]:
    p = engine.parameters
    needs = trajectory[:, : engine.n_needs]
    valence_start = engine.n_needs + engine.n_modes
    valences = trajectory[:, valence_start:]
    beliefs = np.asarray(participant.beliefs, dtype=np.float64)

    cognitive_scale = p.cognitive_gain * p.need_to_action_gain / engine.n_needs
    cognitive = cognitive_scale * np.einsum("tn,mn->tm", needs, beliefs)
    affective = p.valence_to_action_gain * valences
    net = cognitive + affective

    # Means are computed over the post-emergence explanatory window; "initial"
    # always means the actual model initial state t=0.
    evaluated_cognitive = cognitive[start_index:]
    evaluated_affective = affective[start_index:]
    evaluated_net = net[start_index:]

    by_mode: Dict[str, Any] = {}
    for mode_index, mode in enumerate(MODES):
        if availability[mode_index] != 1:
            by_mode[mode] = {"available": False}
            continue

        instantaneous_friction = np.asarray(
            [
                _signed_friction(c, a)
                for c, a in zip(
                    evaluated_cognitive[:, mode_index],
                    evaluated_affective[:, mode_index],
                )
            ],
            dtype=np.float64,
        )
        by_mode[mode] = {
            "available": True,
            "cognitive_signed_input": {
                "initial": _number(cognitive[0, mode_index]),
                "mean_post_emergence": _number(
                    np.mean(evaluated_cognitive[:, mode_index])
                ),
                "terminal": _number(cognitive[-1, mode_index]),
            },
            "affective_signed_input": {
                "initial": _number(affective[0, mode_index]),
                "mean_post_emergence": _number(
                    np.mean(evaluated_affective[:, mode_index])
                ),
                "terminal": _number(affective[-1, mode_index]),
            },
            "net_signed_input": {
                "initial": _number(net[0, mode_index]),
                "mean_post_emergence": _number(np.mean(evaluated_net[:, mode_index])),
                "terminal": _number(net[-1, mode_index]),
            },
            "cognitive_affective_friction": {
                "initial": _number(
                    _signed_friction(cognitive[0, mode_index], affective[0, mode_index])
                ),
                "mean_post_emergence": _number(np.mean(instantaneous_friction)),
                "terminal": _number(
                    _signed_friction(
                        cognitive[-1, mode_index], affective[-1, mode_index]
                    )
                ),
            },
            "terminal_affective_resistance": bool(affective[-1, mode_index] < 0.0),
            "terminal_cognitive_resistance": bool(cognitive[-1, mode_index] < 0.0),
        }

    return {
        "by_mode": by_mode,
        "interpretation": (
            "signed topology-input decomposition before the HOTCO-CT net-first "
            "rectification and nonlinear shunting transform; descriptive, not a "
            "causal percentage decomposition"
        ),
    }


def _mixed_cognitive_support_diagnostics(
    trajectory: np.ndarray,
    participant: ParticipantInput,
    availability: np.ndarray,
    engine: HOTCOCTv43,
    start_index: int,
) -> Dict[str, Any]:
    p = engine.parameters
    needs = trajectory[:, : engine.n_needs]
    beliefs = np.asarray(participant.beliefs, dtype=np.float64)
    scale = p.cognitive_gain * p.need_to_action_gain / engine.n_needs
    contributions = scale * needs[:, np.newaxis, :] * beliefs[np.newaxis, :, :]

    by_mode: Dict[str, Any] = {}
    for mode_index, mode in enumerate(MODES):
        if availability[mode_index] != 1:
            by_mode[mode] = {"available": False}
            continue

        evaluated = contributions[start_index:, mode_index, :]
        positive_t = np.maximum(evaluated, 0.0).sum(axis=1)
        negative_t = np.maximum(-evaluated, 0.0).sum(axis=1)
        mixed_t = np.asarray(
            [_mixed_support(pv, nv) for pv, nv in zip(positive_t, negative_t)],
            dtype=np.float64,
        )

        terminal = contributions[-1, mode_index, :]
        positive_terminal = float(np.maximum(terminal, 0.0).sum())
        negative_terminal = float(np.maximum(-terminal, 0.0).sum())

        supporters = sorted(
            (
                {"need": need, "signed_input": _number(value)}
                for need, value in zip(NEEDS, terminal)
                if value > 0.0
            ),
            key=lambda item: item["signed_input"],
            reverse=True,
        )[:3]
        inhibitors = sorted(
            (
                {"need": need, "signed_input": _number(value)}
                for need, value in zip(NEEDS, terminal)
                if value < 0.0
            ),
            key=lambda item: item["signed_input"],
        )[:3]

        by_mode[mode] = {
            "available": True,
            "positive_cognitive_mass_terminal": _number(positive_terminal),
            "negative_cognitive_mass_terminal": _number(negative_terminal),
            "mixed_support_index": {
                "mean_post_emergence": _number(np.mean(mixed_t)),
                "terminal": _number(
                    _mixed_support(positive_terminal, negative_terminal)
                ),
            },
            "top_supporters_terminal": supporters,
            "top_inhibitors_terminal": inhibitors,
        }

    return {
        "by_mode": by_mode,
        "interpretation": (
            "within-option coexistence of positive and negative need-action signed "
            "inputs; not a direct measure of experienced psychological conflict"
        ),
    }


def _patterns(
    competition: Dict[str, Any],
    leadership: Dict[str, Any],
    support: Dict[str, Any],
    mixed: Dict[str, Any],
    engine: HOTCOCTv43,
) -> list[str]:
    patterns: list[str] = []
    winner = competition["winner"]

    final_identifiable = leadership["final_leader_identifiable"]
    if not final_identifiable:
        patterns.append("UNDIFFERENTIATED_TERMINAL_STATE")

    if competition["co_dominance_fraction"] >= PERSISTENT_RIVALRY_FRACTION:
        patterns.append("PERSISTENT_RIVALRY")

    switches = leadership["winner_switch_count"]
    first = leadership["first_identifiable_leader"]
    final = leadership["final_leader"]
    final_time = leadership["final_leader_acquired_time_model_units"]
    if switches >= 2:
        patterns.append("UNSTABLE_COMPETITION")
    elif first is not None and first != final and final_time is not None:
        fraction = final_time / engine.parameters.horizon
        if fraction >= 2.0 / 3.0:
            patterns.append("LATE_REVERSAL")
        elif fraction <= 1.0 / 3.0:
            patterns.append("EARLY_REVERSAL")
        else:
            patterns.append("REVERSAL")

    winner_support = support["by_mode"][winner]
    if winner_support.get("terminal_affective_resistance"):
        patterns.append("WINNER_WITH_AFFECTIVE_RESISTANCE")
    if winner_support.get("terminal_cognitive_resistance"):
        patterns.append("WINNER_WITH_COGNITIVE_RESISTANCE")
    if (
        winner_support.get("cognitive_affective_friction", {}).get("terminal", 0.0)
        >= HIGH_FRICTION_THRESHOLD
    ):
        patterns.append("HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION")

    winner_mixed = mixed["by_mode"][winner]
    if (
        winner_mixed.get("mixed_support_index", {}).get("terminal", 0.0)
        >= HIGH_FRICTION_THRESHOLD
    ):
        patterns.append("HIGH_WINNER_MIXED_COGNITIVE_SUPPORT")

    if (
        final_identifiable
        and switches == 0
        and competition["co_dominance_fraction"] < PERSISTENT_RIVALRY_FRACTION
    ):
        patterns.append("STABLE_DOMINANCE")

    return patterns


def build_xai_diagnostics(
    participant: ParticipantInput,
    result: SimulationResult,
    engine: HOTCOCTv43,
) -> Dict[str, Any]:
    """Build deterministic, model-grounded process diagnostics for one simulation."""

    trajectory = result.trajectory[0]
    availability = result.availability[0]
    action_slice = slice(engine.n_needs, engine.n_needs + engine.n_modes)
    actions = trajectory[:, action_slice]

    competition = _competition_diagnostics(actions, availability, engine)
    start_index = int(competition.pop("_start_index"))
    winner_index = int(competition.pop("_winner_index"))
    competition.pop("_rival_index")

    leadership = _leadership_diagnostics(
        actions, availability, engine, start_index, winner_index
    )
    support = _support_diagnostics(
        trajectory, participant, availability, engine, start_index
    )
    mixed = _mixed_cognitive_support_diagnostics(
        trajectory, participant, availability, engine, start_index
    )
    patterns = _patterns(competition, leadership, support, mixed, engine)

    winner = competition["winner"]
    winner_support = support["by_mode"][winner]
    winner_mixed = mixed["by_mode"][winner]

    return {
        "schema_version": XAI_DIAGNOSTICS_VERSION,
        "scope": "simulation_process_explanation",
        "competition": competition,
        "leadership": leadership,
        "support_dynamics": support,
        "mixed_cognitive_support": mixed,
        "winner_summary": {
            "mode": winner,
            "main_rival": competition["main_rival"],
            "terminal_cognitive_signed_input": winner_support[
                "cognitive_signed_input"
            ]["terminal"],
            "terminal_affective_signed_input": winner_support[
                "affective_signed_input"
            ]["terminal"],
            "terminal_cognitive_affective_friction": winner_support[
                "cognitive_affective_friction"
            ]["terminal"],
            "terminal_mixed_cognitive_support": winner_mixed[
                "mixed_support_index"
            ]["terminal"],
            "top_supporters_terminal": winner_mixed["top_supporters_terminal"],
            "top_inhibitors_terminal": winner_mixed["top_inhibitors_terminal"],
        },
        "patterns": patterns,
        "classification_thresholds": {
            "practical_action_margin": engine.parameters.practical_margin_threshold,
            "persistent_rivalry_fraction": PERSISTENT_RIVALRY_FRACTION,
            "high_friction": HIGH_FRICTION_THRESHOLD,
        },
        "interpretation_guardrails": [
            "Metrics describe the HOTCO-CT simulation, not directly observed subjective experience.",
            "Signed cognitive and affective inputs are not causal percentage contributions.",
            "Leader-change times are model time, not human reaction time.",
            "Pattern labels are deterministic descriptive classifications and do not feed back into HOTCO-CT.",
        ],
    }
