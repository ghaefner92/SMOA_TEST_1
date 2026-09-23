"""Deterministic XAI-driven micro-question selection for HOTCO-CT.

The trigger engine is deliberately separate from the HOTCO-CT dynamics and
from language generation. It inspects already-computed XAI diagnostics and
selects at most one explicit profile re-check. It never changes a Cognitive
Passport by itself.

Questions re-measure an already observed construct on its original 1..7 user
scale. No missing value is imputed and no behavioural observation is treated
as a psychological response.
"""

from __future__ import annotations

from hashlib import sha256
from typing import Any, Dict, Mapping, Optional, Sequence

from input_mapping_v4_3 import MODES, NEEDS, ParticipantInput


QUESTION_ENGINE_VERSION = "1.0"
MAX_QUESTIONS_PER_DELIBERATION = 1
TARGET_COOLDOWN_REVISIONS = 2


def _target_key(target: Mapping[str, Any]) -> str:
    kind = str(target.get("kind", ""))
    if kind == "belief":
        return f"belief:{target.get('mode')}:{target.get('need')}"
    if kind == "valence":
        return f"valence:{target.get('mode')}"
    if kind == "need":
        return f"need:{target.get('need')}"
    return kind


def _recent_target_keys(
    measurement_history: Sequence[Mapping[str, Any]],
    current_revision: int,
) -> set[str]:
    keys: set[str] = set()
    for event in measurement_history:
        try:
            revision = int(event.get("revision", 0))
        except (TypeError, ValueError):
            continue
        if current_revision - revision > TARGET_COOLDOWN_REVISIONS:
            continue
        target = event.get("target")
        if isinstance(target, Mapping):
            keys.add(_target_key(target))
    return keys


def _discriminating_need(
    participant: ParticipantInput,
    first_mode: str,
    second_mode: str,
) -> str:
    first_index = MODES.index(first_mode)
    second_index = MODES.index(second_mode)
    belief_differences = [
        abs(participant.beliefs[first_index][need_index] - participant.beliefs[second_index][need_index])
        for need_index in range(len(NEEDS))
    ]
    weighted = [
        difference * participant.needs[need_index]
        for need_index, difference in enumerate(belief_differences)
    ]
    # If all current need activations are zero, fall back to the observed belief
    # contrast rather than selecting an arbitrary first need.
    scores = weighted if max(weighted, default=0.0) > 0.0 else belief_differences
    if max(scores, default=0.0) == 0.0:
        scores = list(participant.needs)
    return NEEDS[max(range(len(scores)), key=scores.__getitem__)]


def _strongest_mixed_edge(
    participant: ParticipantInput,
    mode: str,
) -> str:
    mode_index = MODES.index(mode)
    contributions = [
        participant.beliefs[mode_index][need_index] * participant.needs[need_index]
        for need_index in range(len(NEEDS))
    ]
    negative = [
        (abs(value), need_index)
        for need_index, value in enumerate(contributions)
        if value < 0.0
    ]
    if negative:
        return NEEDS[max(negative)[1]]
    return NEEDS[max(range(len(contributions)), key=lambda index: abs(contributions[index]))]


def _question_id(
    passport_id: str,
    trigger: str,
    target: Mapping[str, Any],
) -> str:
    material = f"{passport_id}|{trigger}|{_target_key(target)}"
    return "mq_" + sha256(material.encode("utf-8")).hexdigest()[:16]


def build_micro_question(
    *,
    participant: ParticipantInput,
    xai: Mapping[str, Any],
    passport_id: str,
    revision: int,
    measurement_history: Sequence[Mapping[str, Any]] = (),
) -> Dict[str, Any]:
    """Return zero or one structured micro-question candidate.

    Priority is deterministic: persistent two-mode rivalry, strong
    cognitive-affective friction, reversal, then strong within-option mixed
    support. A recently re-measured target is suppressed for a small revision
    cooldown so an update cannot immediately ask the same item again.
    """

    patterns = set(str(value) for value in xai.get("patterns", []))
    competition = xai.get("competition", {})
    leadership = xai.get("leadership", {})
    winner = str(competition.get("winner", ""))
    rival = competition.get("main_rival")

    trigger: Optional[str] = None
    target: Optional[Dict[str, Any]] = None
    rationale: Optional[str] = None

    if "PERSISTENT_RIVALRY" in patterns and winner in MODES and rival in MODES:
        need = _discriminating_need(participant, winner, str(rival))
        trigger = "PERSISTENT_RIVALRY"
        target = {
            "kind": "need",
            "need": need,
            "current_raw_rating": int(participant.raw_needs[need]),
        }
        rationale = (
            "winner and main rival remained close; re-check the need whose "
            "current weighting most separates their observed belief profiles"
        )
    elif (
        "HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION" in patterns
        or "WINNER_WITH_AFFECTIVE_RESISTANCE" in patterns
    ) and winner in MODES:
        trigger = "COGNITIVE_AFFECTIVE_FRICTION"
        target = {
            "kind": "valence",
            "mode": winner,
            # Stored raw valence is -3..+3; the user-facing measurement remains 1..7.
            "current_raw_rating": int(participant.raw_valences[winner] + 4),
        }
        rationale = (
            "the winning mode has opposing cognitive and affective signed input; "
            "re-check its explicit affective evaluation"
        )
    elif patterns.intersection({"EARLY_REVERSAL", "REVERSAL", "LATE_REVERSAL"}) \
            and winner in MODES:
        initial_leader = leadership.get("first_identifiable_leader")
        comparison_mode = (
            str(initial_leader)
            if initial_leader in MODES and initial_leader != winner
            else str(rival) if rival in MODES else None
        )
        if comparison_mode is not None:
            need = _discriminating_need(participant, winner, comparison_mode)
            trigger = "LEADERSHIP_REVERSAL"
            target = {
                "kind": "belief",
                "mode": winner,
                "need": need,
                "current_raw_rating": int(participant.raw_beliefs[winner][need]),
            }
            rationale = (
                "leadership changed during the simulated deliberation; re-check a "
                "need-mode association that separates the final winner from the "
                "actual initial identifiable leader"
            )
    elif "HIGH_WINNER_MIXED_COGNITIVE_SUPPORT" in patterns and winner in MODES:
        need = _strongest_mixed_edge(participant, winner)
        trigger = "MIXED_COGNITIVE_SUPPORT"
        target = {
            "kind": "belief",
            "mode": winner,
            "need": need,
            "current_raw_rating": int(participant.raw_beliefs[winner][need]),
        }
        rationale = (
            "the winning mode contains substantial positive and negative need-based "
            "support; re-check one influential observed association"
        )

    base: Dict[str, Any] = {
        "schema_version": QUESTION_ENGINE_VERSION,
        "policy": "at_most_one_explicit_profile_recheck_per_passport",
        "max_questions_per_deliberation": MAX_QUESTIONS_PER_DELIBERATION,
        "automatic_profile_updates": False,
        "llm_selects_question": False,
        "cooldown_revisions_for_same_target": TARGET_COOLDOWN_REVISIONS,
    }

    if target is None or trigger is None:
        base.update({"question_needed": False, "candidate": None})
        return base

    if _target_key(target) in _recent_target_keys(measurement_history, revision):
        base.update(
            {
                "question_needed": False,
                "candidate": None,
                "suppressed_reason": "same_target_recently_remeasured",
            }
        )
        return base

    candidate = {
        "question_id": _question_id(passport_id, trigger, target),
        "trigger": trigger,
        "scope": "profile",
        "target": target,
        "response_scale": {
            "minimum": 1,
            "maximum": 7,
            "integer_only": True,
            "meaning": "same explicit user-report scale used in initial calibration",
        },
        "rationale": rationale,
        "update_requires_explicit_user_confirmation": True,
    }
    base.update({"question_needed": True, "candidate": candidate})
    return base
