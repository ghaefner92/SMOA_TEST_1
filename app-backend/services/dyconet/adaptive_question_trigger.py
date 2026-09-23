"""Provenance-aware longitudinal questioning for Adaptive Passport v1.

This module is separate from HOTCO-CT dynamics and from language generation.

Policy:
- at most one explicit question per deliberation,
- XAI determines why a question may be useful,
- provenance determines whether the target is a new measurement or a re-check,
- estimated beliefs never receive fabricated raw 1..7 values,
- belief-targeting triggers prefer a relevant estimated cell when available,
- no profile is updated automatically.
"""

from __future__ import annotations

from hashlib import sha256
from typing import Any, Dict, Mapping, Optional, Sequence

from input_mapping_v4_3 import (
    MODES,
    NEEDS,
    ParticipantInput,
)


ADAPTIVE_QUESTION_ENGINE_VERSION = (
    "adaptive_question_engine_1.0"
)

MAX_QUESTIONS_PER_DELIBERATION = 1
TARGET_COOLDOWN_REVISIONS = 2


class AdaptiveQuestionTriggerError(
    ValueError
):
    pass


def _target_key(
    target: Mapping[str, Any],
) -> str:

    kind = str(
        target.get(
            "kind",
            "",
        )
    )

    if kind == "belief":
        return (
            "belief:"
            f"{target.get('mode')}:"
            f"{target.get('need')}"
        )

    if kind == "valence":
        return (
            "valence:"
            f"{target.get('mode')}"
        )

    if kind == "need":
        return (
            "need:"
            f"{target.get('need')}"
        )

    return kind


def _recent_target_keys(
    measurement_history: Sequence[
        Mapping[str, Any]
    ],
    current_revision: int,
) -> set[str]:

    keys: set[str] = set()

    for event in measurement_history:

        try:
            revision = int(
                event.get(
                    "revision",
                    0,
                )
            )
        except (
            TypeError,
            ValueError,
        ):
            continue

        if (
            current_revision
            - revision
            > TARGET_COOLDOWN_REVISIONS
        ):
            continue

        target = event.get(
            "target"
        )

        if isinstance(
            target,
            Mapping,
        ):
            keys.add(
                _target_key(
                    target
                )
            )

    return keys


def _belief_source(
    provenance: Mapping[str, Any],
    mode: str,
    need: str,
) -> str:

    mode_values = provenance.get(
        mode
    )

    if not isinstance(
        mode_values,
        Mapping,
    ):
        raise AdaptiveQuestionTriggerError(
            f"Missing belief provenance for mode {mode}"
        )

    metadata = mode_values.get(
        need
    )

    if not isinstance(
        metadata,
        Mapping,
    ):
        raise AdaptiveQuestionTriggerError(
            f"Missing belief provenance for "
            f"{mode}__{need}"
        )

    source = str(
        metadata.get(
            "source",
            "",
        )
    )

    if source not in {
        "observed",
        "estimated",
    }:
        raise AdaptiveQuestionTriggerError(
            f"Unsupported belief provenance "
            f"for {mode}__{need}: {source}"
        )

    return source


def _discriminating_scores(
    participant: ParticipantInput,
    first_mode: str,
    second_mode: str,
) -> list[float]:

    first_index = MODES.index(
        first_mode
    )

    second_index = MODES.index(
        second_mode
    )

    differences = [
        abs(
            participant.beliefs[
                first_index
            ][
                need_index
            ]
            -
            participant.beliefs[
                second_index
            ][
                need_index
            ]
        )
        for need_index
        in range(
            len(
                NEEDS
            )
        )
    ]

    weighted = [
        difference
        * participant.needs[
            need_index
        ]
        for need_index, difference
        in enumerate(
            differences
        )
    ]

    if max(
        weighted,
        default=0.0,
    ) > 0.0:
        return weighted

    if max(
        differences,
        default=0.0,
    ) > 0.0:
        return differences

    return list(
        participant.needs
    )


def _select_discriminating_belief(
    *,
    participant: ParticipantInput,
    provenance: Mapping[str, Any],
    target_mode: str,
    comparison_mode: str,
) -> str:
    """Prefer an estimated relevant edge, then fall back to observed."""

    scores = _discriminating_scores(
        participant,
        target_mode,
        comparison_mode,
    )

    candidates = []

    for index, need in enumerate(
        NEEDS
    ):

        source = _belief_source(
            provenance,
            target_mode,
            need,
        )

        estimated_priority = (
            0
            if source == "estimated"
            else 1
        )

        candidates.append(
            (
                estimated_priority,
                -float(
                    scores[
                        index
                    ]
                ),
                index,
                need,
            )
        )

    candidates.sort()

    return candidates[
        0
    ][
        3
    ]


def _select_mixed_support_belief(
    *,
    participant: ParticipantInput,
    provenance: Mapping[str, Any],
    mode: str,
) -> str:

    mode_index = MODES.index(
        mode
    )

    contributions = [
        participant.beliefs[
            mode_index
        ][
            need_index
        ]
        * participant.needs[
            need_index
        ]
        for need_index
        in range(
            len(
                NEEDS
            )
        )
    ]

    negative_indices = [
        index
        for index, value
        in enumerate(
            contributions
        )
        if value < 0.0
    ]

    candidate_indices = (
        negative_indices
        if negative_indices
        else list(
            range(
                len(
                    NEEDS
                )
            )
        )
    )

    candidates = []

    for index in candidate_indices:

        need = NEEDS[
            index
        ]

        source = _belief_source(
            provenance,
            mode,
            need,
        )

        estimated_priority = (
            0
            if source == "estimated"
            else 1
        )

        candidates.append(
            (
                estimated_priority,
                -abs(
                    float(
                        contributions[
                            index
                        ]
                    )
                ),
                index,
                need,
            )
        )

    candidates.sort()

    return candidates[
        0
    ][
        3
    ]


def _belief_target(
    *,
    participant: ParticipantInput,
    provenance: Mapping[str, Any],
    mode: str,
    need: str,
) -> Dict[str, Any]:

    source = _belief_source(
        provenance,
        mode,
        need,
    )

    target: Dict[
        str,
        Any,
    ] = {
        "kind":
            "belief",

        "mode":
            mode,

        "need":
            need,

        "measurement_status":
            source,

        "question_intent":
            (
                "measure_estimated_construct"
                if source == "estimated"
                else
                "remeasure_observed_construct"
            ),
    }

    if source == "observed":

        raw_mode = (
            participant
            .raw_beliefs
            .get(
                mode,
                {},
            )
        )

        if need not in raw_mode:
            raise AdaptiveQuestionTriggerError(
                "Belief provenance says observed "
                "but no explicit raw measurement exists "
                f"for {mode}__{need}"
            )

        target[
            "current_raw_rating"
        ] = int(
            raw_mode[
                need
            ]
        )

    # Deliberately no current_raw_rating for estimated cells.

    return target


def _question_id(
    passport_id: str,
    trigger: str,
    target: Mapping[str, Any],
) -> str:

    material = (
        f"{passport_id}|"
        f"{trigger}|"
        f"{_target_key(target)}"
    )

    return (
        "amq_"
        + sha256(
            material.encode(
                "utf-8"
            )
        ).hexdigest()[:16]
    )


def build_adaptive_micro_question(
    *,
    participant: ParticipantInput,
    xai: Mapping[str, Any],
    belief_provenance: Mapping[str, Any],
    passport_id: str,
    revision: int,
    measurement_history: Sequence[
        Mapping[str, Any]
    ] = (),
) -> Dict[str, Any]:
    """Return zero or one provenance-aware explicit profile question."""

    patterns = {
        str(
            value
        )
        for value in xai.get(
            "patterns",
            [],
        )
    }

    competition = xai.get(
        "competition",
        {},
    )

    leadership = xai.get(
        "leadership",
        {},
    )

    winner = str(
        competition.get(
            "winner",
            "",
        )
    )

    rival = competition.get(
        "main_rival"
    )

    trigger: Optional[
        str
    ] = None

    target: Optional[
        Dict[str, Any]
    ] = None

    rationale: Optional[
        str
    ] = None

    # -----------------------------------------------------
    # 1. Persistent rivalry -> re-check observed need
    # -----------------------------------------------------

    if (
        "PERSISTENT_RIVALRY"
        in patterns
        and winner in MODES
        and rival in MODES
    ):

        scores = (
            _discriminating_scores(
                participant,
                winner,
                str(
                    rival
                ),
            )
        )

        need_index = max(
            range(
                len(
                    scores
                )
            ),
            key=scores.__getitem__,
        )

        need = NEEDS[
            need_index
        ]

        trigger = (
            "PERSISTENT_RIVALRY"
        )

        target = {
            "kind":
                "need",

            "need":
                need,

            "measurement_status":
                "observed",

            "question_intent":
                "remeasure_observed_construct",

            "current_raw_rating":
                int(
                    participant
                    .raw_needs[
                        need
                    ]
                ),
        }

        rationale = (
            "winner and main rival remained close; "
            "re-check the need weighting that most "
            "separates their current belief profiles"
        )

    # -----------------------------------------------------
    # 2. Cognitive-affective friction -> observed valence
    # -----------------------------------------------------

    elif (
        (
            "HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION"
            in patterns
        )
        or (
            "WINNER_WITH_AFFECTIVE_RESISTANCE"
            in patterns
        )
    ) and winner in MODES:

        trigger = (
            "COGNITIVE_AFFECTIVE_FRICTION"
        )

        target = {
            "kind":
                "valence",

            "mode":
                winner,

            "measurement_status":
                "observed",

            "question_intent":
                "remeasure_observed_construct",

            # Stored valence is -3..+3.
            # User-facing scale remains 1..7.
            "current_raw_rating":
                int(
                    participant
                    .raw_valences[
                        winner
                    ]
                    + 4
                ),
        }

        rationale = (
            "the leading mode has opposing cognitive "
            "and affective signed input; re-check its "
            "explicit affective evaluation"
        )

    # -----------------------------------------------------
    # 3. Leadership reversal -> belief question
    #
    # Prefer a relevant estimated belief so explicit
    # evidence can replace an estimator-derived edge.
    # -----------------------------------------------------

    elif (
        patterns.intersection(
            {
                "EARLY_REVERSAL",
                "REVERSAL",
                "LATE_REVERSAL",
            }
        )
        and winner in MODES
    ):

        initial_leader = leadership.get(
            "first_identifiable_leader"
        )

        comparison_mode = (
            str(
                initial_leader
            )
            if (
                initial_leader in MODES
                and initial_leader
                != winner
            )
            else (
                str(
                    rival
                )
                if rival in MODES
                else None
            )
        )

        if comparison_mode is not None:

            need = (
                _select_discriminating_belief(
                    participant=
                        participant,

                    provenance=
                        belief_provenance,

                    target_mode=
                        winner,

                    comparison_mode=
                        comparison_mode,
                )
            )

            trigger = (
                "LEADERSHIP_REVERSAL"
            )

            target = _belief_target(
                participant=
                    participant,

                provenance=
                    belief_provenance,

                mode=
                    winner,

                need=
                    need,
            )

            rationale = (
                "leadership changed during simulated "
                "deliberation; measure a relevant "
                "winner belief edge, prioritizing an "
                "estimator-derived edge when available"
            )

    # -----------------------------------------------------
    # 4. Mixed cognitive support -> belief question
    # -----------------------------------------------------

    elif (
        "HIGH_WINNER_MIXED_COGNITIVE_SUPPORT"
        in patterns
        and winner in MODES
    ):

        need = (
            _select_mixed_support_belief(
                participant=
                    participant,

                provenance=
                    belief_provenance,

                mode=
                    winner,
            )
        )

        trigger = (
            "MIXED_COGNITIVE_SUPPORT"
        )

        target = _belief_target(
            participant=
                participant,

            provenance=
                belief_provenance,

            mode=
                winner,

            need=
                need,
        )

        rationale = (
            "the leading mode contains opposing "
            "need-based support; explicitly measure "
            "a relevant belief edge, prioritizing an "
            "estimator-derived edge when available"
        )

    base: Dict[
        str,
        Any,
    ] = {
        "schema_version":
            ADAPTIVE_QUESTION_ENGINE_VERSION,

        "policy":
            (
                "xai_trigger_then_provenance_aware_"
                "belief_measurement"
            ),

        "status":
            "experimental",

        "max_questions_per_deliberation":
            MAX_QUESTIONS_PER_DELIBERATION,

        "automatic_profile_updates":
            False,

        "llm_selects_question":
            False,

        "hotco_parameters_changed":
            False,

        "cooldown_revisions_for_same_target":
            TARGET_COOLDOWN_REVISIONS,

        "belief_target_policy":
            (
                "prefer_relevant_estimated_cell_"
                "before_observed_remeasurement"
            ),
    }

    if (
        target is None
        or trigger is None
    ):

        base.update(
            {
                "question_needed":
                    False,

                "candidate":
                    None,
            }
        )

        return base

    if (
        _target_key(
            target
        )
        in _recent_target_keys(
            measurement_history,
            revision,
        )
    ):

        base.update(
            {
                "question_needed":
                    False,

                "candidate":
                    None,

                "suppressed_reason":
                    "same_target_recently_measured",
            }
        )

        return base

    candidate = {
        "question_id":
            _question_id(
                passport_id,
                trigger,
                target,
            ),

        "trigger":
            trigger,

        "scope":
            "profile",

        "target":
            target,

        "response_scale": {
            "minimum":
                1,

            "maximum":
                7,

            "integer_only":
                True,

            "meaning":
                (
                    "explicit participant report; "
                    "same measurement scale used "
                    "for the corresponding construct"
                ),
        },

        "rationale":
            rationale,

        "update_requires_explicit_user_confirmation":
            True,
    }

    base.update(
        {
            "question_needed":
                True,

            "candidate":
                candidate,
        }
    )

    return base
