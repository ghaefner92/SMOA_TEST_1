"""Bridge from Adaptive Cognitive Passport v1 to HOTCO-CT v4.3.

Important measurement rule:
estimated normalized beliefs are valid HOTCO inputs, but they are NOT
fabricated into raw 1..7 questionnaire responses.

The returned ParticipantInput therefore contains:
- complete observed raw needs,
- complete observed raw valences,
- sparse raw beliefs containing only explicit user answers,
- complete normalized 4 x 11 belief matrix for HOTCO,
- explicit current-user availability.

This adapter runs no HOTCO simulation itself.
"""

from __future__ import annotations

from dataclasses import dataclass
import math
from typing import Any, Dict, Mapping, Optional

from adaptive_passport_onboarding import (
    ONBOARDING_SCHEMA_VERSION,
)
from adaptive_passport_v1 import (
    AdaptivePassportError,
    AdaptivePassportV1,
)
from availability_resolver import (
    resolve_user_declared_availability,
)
from input_mapping_v4_3 import (
    ENVIRONMENTAL_TOLERANCES,
    MODES,
    NEEDS,
    ParticipantInput,
)


ADAPTIVE_HOTCO_ADAPTER_VERSION = (
    "adaptive_hotco_adapter_1.0"
)

_RUNTIME = AdaptivePassportV1()


class AdaptiveHotcoAdapterError(
    ValueError
):
    """Raised when an adaptive profile cannot safely enter HOTCO."""


@dataclass(frozen=True)
class AdaptiveHOTCOInput:
    participant: ParticipantInput
    measurement_provenance: Dict[str, Any]
    adaptive_metadata: Dict[str, Any]


def _fail(
    message: str,
) -> None:
    raise AdaptiveHotcoAdapterError(
        message
    )


def _mapping(
    value: Any,
    path: str,
) -> Mapping[str, Any]:

    if not isinstance(
        value,
        Mapping,
    ):
        _fail(
            f"{path} must be a JSON object"
        )

    return value


def _finite(
    value: Any,
    *,
    minimum: float,
    maximum: float,
    path: str,
) -> float:

    if isinstance(
        value,
        bool,
    ):
        _fail(
            f"{path} must be numeric"
        )

    try:
        numeric = float(
            value
        )
    except (
        TypeError,
        ValueError,
    ) as exc:

        raise AdaptiveHotcoAdapterError(
            f"{path} must be numeric"
        ) from exc

    if not math.isfinite(
        numeric
    ):
        _fail(
            f"{path} must be finite"
        )

    if (
        numeric < minimum
        or numeric > maximum
    ):
        _fail(
            f"{path} must lie within "
            f"[{minimum}, {maximum}]"
        )

    return numeric


def _integer(
    value: Any,
    *,
    minimum: int,
    maximum: int,
    path: str,
) -> int:

    if isinstance(
        value,
        bool,
    ):
        _fail(
            f"{path} must be an integer from "
            f"{minimum} to {maximum}"
        )

    try:
        numeric = float(
            value
        )
    except (
        TypeError,
        ValueError,
    ) as exc:

        raise AdaptiveHotcoAdapterError(
            f"{path} must be an integer from "
            f"{minimum} to {maximum}"
        ) from exc

    if (
        not math.isfinite(
            numeric
        )
        or not numeric.is_integer()
        or numeric < minimum
        or numeric > maximum
    ):
        _fail(
            f"{path} must be an integer from "
            f"{minimum} to {maximum}"
        )

    return int(
        numeric
    )


def _raw_needs(
    source: Any,
) -> Dict[str, int]:

    values = _mapping(
        source,
        "observed_measurements.needs_raw_1_to_7",
    )

    missing = [
        need
        for need in NEEDS
        if need not in values
    ]

    unknown = [
        str(key)
        for key in values
        if key not in NEEDS
    ]

    if missing:
        _fail(
            "Missing observed raw needs: "
            + ", ".join(
                missing
            )
        )

    if unknown:
        _fail(
            "Unknown observed raw needs: "
            + ", ".join(
                sorted(
                    unknown
                )
            )
        )

    return {
        need:
            _integer(
                values[
                    need
                ],
                minimum=1,
                maximum=7,
                path=(
                    "observed_measurements."
                    "needs_raw_1_to_7."
                    + need
                ),
            )
        for need in NEEDS
    }


def _raw_valences(
    source: Any,
) -> Dict[str, int]:

    values = _mapping(
        source,
        "observed_measurements."
        "valences_raw_minus3_to_3",
    )

    missing = [
        mode
        for mode in MODES
        if mode not in values
    ]

    unknown = [
        str(key)
        for key in values
        if key not in MODES
    ]

    if missing:
        _fail(
            "Missing observed raw valences: "
            + ", ".join(
                missing
            )
        )

    if unknown:
        _fail(
            "Unknown observed raw valences: "
            + ", ".join(
                sorted(
                    unknown
                )
            )
        )

    return {
        mode:
            _integer(
                values[
                    mode
                ],
                minimum=-3,
                maximum=3,
                path=(
                    "observed_measurements."
                    "valences_raw_minus3_to_3."
                    + mode
                ),
            )
        for mode in MODES
    }


def _raw_belief_answers(
    source: Any,
) -> Dict[str, int]:

    values = _mapping(
        source,
        "observed_measurements."
        "beliefs_raw_1_to_7",
    )

    if len(
        values
    ) != _RUNTIME.k:
        _fail(
            "Adaptive Cognitive Passport v1 "
            f"requires exactly {_RUNTIME.k} "
            "observed belief answers"
        )

    result: Dict[
        str,
        int,
    ] = {}

    for cell_id, value in (
        values.items()
    ):

        cell = str(
            cell_id
        )

        if cell not in (
            _RUNTIME.all_cells
        ):
            _fail(
                "Unknown observed belief cell: "
                + cell
            )

        result[
            cell
        ] = _integer(
            value,
            minimum=1,
            maximum=7,
            path=(
                "observed_measurements."
                "beliefs_raw_1_to_7."
                + cell
            ),
        )

    expected_questions = {
        question[
            "cell_id"
        ]
        for question
        in _RUNTIME.select_questions()
    }

    if set(
        result
    ) != expected_questions:
        _fail(
            "Observed belief answers do not match "
            "the frozen v1 calibration questions"
        )

    return result


def _availability(
    source: Any,
):
    values = _mapping(
        source,
        "availability",
    )

    missing = [
        mode
        for mode in MODES
        if mode not in values
    ]

    unknown = [
        str(key)
        for key in values
        if key not in MODES
    ]

    if missing:
        _fail(
            "availability requires explicit "
            "declarations for: "
            + ", ".join(
                missing
            )
        )

    if unknown:
        _fail(
            "availability contains unknown modes: "
            + ", ".join(
                sorted(
                    unknown
                )
            )
        )

    raw: Dict[
        str,
        bool,
    ] = {}

    for mode in MODES:

        value = values[
            mode
        ]

        if not isinstance(
            value,
            bool,
        ):
            _fail(
                f"availability.{mode} "
                "must be true or false"
            )

        raw[
            mode
        ] = value

    try:
        return (
            resolve_user_declared_availability(
                raw,
                MODES,
            )
        )

    except ValueError as exc:
        raise AdaptiveHotcoAdapterError(
            str(exc)
        ) from exc


def _tolerances(
    source: Optional[
        Mapping[str, Any]
    ],
) -> Dict[
    str,
    Optional[float],
]:

    if source is None:
        return {
            name: None
            for name
            in ENVIRONMENTAL_TOLERANCES
        }

    values = _mapping(
        source,
        "environmental_tolerances",
    )

    unknown = [
        str(key)
        for key in values
        if key
        not in ENVIRONMENTAL_TOLERANCES
    ]

    if unknown:
        _fail(
            "environmental_tolerances contains "
            "unknown dimensions: "
            + ", ".join(
                sorted(
                    unknown
                )
            )
        )

    result: Dict[
        str,
        Optional[float],
    ] = {}

    for name in (
        ENVIRONMENTAL_TOLERANCES
    ):

        if (
            name not in values
            or values[
                name
            ]
            is None
        ):
            result[
                name
            ] = None

        else:
            result[
                name
            ] = _finite(
                values[
                    name
                ],
                minimum=0.0,
                maximum=1.0,
                path=(
                    "environmental_tolerances."
                    + name
                ),
            )

    return result


def build_adaptive_hotco_input(
    completed_passport: Any,
    *,
    availability: Mapping[
        str,
        Any,
    ],
    environmental_tolerances: Optional[
        Mapping[
            str,
            Any,
        ]
    ] = None,
) -> AdaptiveHOTCOInput:
    """Build an auditable HOTCO ParticipantInput from Adaptive Passport v1."""

    completed = _mapping(
        completed_passport,
        "$",
    )

    if (
        completed.get(
            "schema_version"
        )
        != ONBOARDING_SCHEMA_VERSION
    ):
        _fail(
            "Adaptive Passport completion has "
            "the wrong schema_version"
        )

    if (
        completed.get(
            "stage"
        )
        != "complete"
    ):
        _fail(
            "Adaptive Passport must be at "
            "stage='complete'"
        )

    if not completed.get(
        "ready_for_hotco",
        False,
    ):
        _fail(
            "Adaptive Passport is not marked "
            "ready_for_hotco"
        )

    agent_id = str(
        completed.get(
            "agent_id",
            "",
        )
    ).strip()

    if not agent_id:
        _fail(
            "Adaptive Passport agent_id is required"
        )

    artifact = _mapping(
        completed.get(
            "artifact"
        ),
        "artifact",
    )

    if (
        artifact.get(
            "sha256"
        )
        != _RUNTIME.artifact_sha256
    ):
        _fail(
            "Adaptive Passport was produced by "
            "a different estimator artifact"
        )

    observed = _mapping(
        completed.get(
            "observed_measurements"
        ),
        "observed_measurements",
    )

    raw_needs = _raw_needs(
        observed.get(
            "needs_raw_1_to_7"
        )
    )

    raw_valences = _raw_valences(
        observed.get(
            "valences_raw_minus3_to_3"
        )
    )

    raw_answer_cells = (
        _raw_belief_answers(
            observed.get(
                "beliefs_raw_1_to_7"
            )
        )
    )

    # -----------------------------------------------------
    # Reconstruct the complete adaptive matrix directly
    # from the frozen estimator and observed measurements.
    # This prevents client-side alteration of estimated
    # beliefs before they enter HOTCO.
    # -----------------------------------------------------

    normalized_needs = {
        need:
            (
                raw_needs[
                    need
                ]
                - 1.0
            )
            / 6.0
        for need in NEEDS
    }

    normalized_valences = {
        mode:
            raw_valences[
                mode
            ]
            / 3.0
        for mode in MODES
    }

    try:
        reconstructed = (
            _RUNTIME.personalize(
                needs=
                    normalized_needs,
                valences=
                    normalized_valences,
                observed_answers=
                    raw_answer_cells,
                answers_are_raw_1_to_7=
                    True,
                require_complete_k=
                    True,
            )
        )

    except AdaptivePassportError as exc:
        raise AdaptiveHotcoAdapterError(
            str(exc)
        ) from exc

    profile = _mapping(
        completed.get(
            "profile"
        ),
        "profile",
    )

    supplied_needs = _mapping(
        profile.get(
            "needs"
        ),
        "profile.needs",
    )

    supplied_valences = _mapping(
        profile.get(
            "valences"
        ),
        "profile.valences",
    )

    supplied_beliefs = _mapping(
        profile.get(
            "beliefs"
        ),
        "profile.beliefs",
    )

    # Needs must exactly reproduce their observed raw input.
    for need in NEEDS:

        if need not in supplied_needs:
            _fail(
                f"profile.needs.{need} is missing"
            )

        supplied = _finite(
            supplied_needs[
                need
            ],
            minimum=0.0,
            maximum=1.0,
            path=(
                "profile.needs."
                + need
            ),
        )

        if abs(
            supplied
            - normalized_needs[
                need
            ]
        ) > 1e-12:
            _fail(
                "profile needs do not match "
                "observed measurements"
            )

    # Valences must exactly reproduce their observed raw input.
    for mode in MODES:

        if mode not in supplied_valences:
            _fail(
                f"profile.valences.{mode} is missing"
            )

        supplied = _finite(
            supplied_valences[
                mode
            ],
            minimum=-1.0,
            maximum=1.0,
            path=(
                "profile.valences."
                + mode
            ),
        )

        if abs(
            supplied
            - normalized_valences[
                mode
            ]
        ) > 1e-12:
            _fail(
                "profile valences do not match "
                "observed measurements"
            )

    # Beliefs must exactly reproduce the frozen v1 runtime.
    expected_beliefs = (
        reconstructed[
            "beliefs"
        ]
    )

    for mode in MODES:

        supplied_mode = _mapping(
            supplied_beliefs.get(
                mode
            ),
            (
                "profile.beliefs."
                + mode
            ),
        )

        for need in NEEDS:

            if need not in supplied_mode:
                _fail(
                    f"profile.beliefs.{mode}.{need} "
                    "is missing"
                )

            supplied = _finite(
                supplied_mode[
                    need
                ],
                minimum=-1.0,
                maximum=1.0,
                path=(
                    "profile.beliefs."
                    + mode
                    + "."
                    + need
                ),
            )

            expected = float(
                expected_beliefs[
                    mode
                ][
                    need
                ]
            )

            if abs(
                supplied
                - expected
            ) > 1e-12:
                _fail(
                    "Adaptive belief matrix does not "
                    "match the frozen v1 estimator"
                )

    # -----------------------------------------------------
    # Sparse raw beliefs:
    # ONLY the four explicit questionnaire answers.
    # -----------------------------------------------------

    sparse_raw_beliefs: Dict[
        str,
        Dict[
            str,
            int,
        ],
    ] = {
        mode: {}
        for mode in MODES
    }

    for cell_id, raw_value in (
        raw_answer_cells.items()
    ):

        mode, need = (
            cell_id.split(
                "__",
                1,
            )
        )

        sparse_raw_beliefs[
            mode
        ][
            need
        ] = raw_value

    observed_count = sum(
        len(
            sparse_raw_beliefs[
                mode
            ]
        )
        for mode in MODES
    )

    if observed_count != 4:
        _fail(
            "Expected exactly four observed "
            "raw belief measurements"
        )

    resolved_availability = (
        _availability(
            availability
        )
    )

    tolerances = _tolerances(
        environmental_tolerances
    )

    participant = ParticipantInput(
        agent_id=
            agent_id,

        raw_needs=
            raw_needs,

        raw_beliefs=
            sparse_raw_beliefs,

        raw_valences=
            raw_valences,

        raw_availability=
            resolved_availability.by_mode,

        availability_provenance=
            resolved_availability.provenance,

        raw_ranking=
            None,

        needs=[
            normalized_needs[
                need
            ]
            for need in NEEDS
        ],

        beliefs=[
            [
                float(
                    expected_beliefs[
                        mode
                    ][
                        need
                    ]
                )
                for need in NEEDS
            ]
            for mode in MODES
        ],

        valences=[
            normalized_valences[
                mode
            ]
            for mode in MODES
        ],

        availability=
            resolved_availability.vector,

        ranking=
            None,

        environmental_tolerances=
            tolerances,
    )

    measurement_provenance = {
        "needs": {
            "source":
                "observed",
            "count":
                len(
                    NEEDS
                ),
        },

        "valences": {
            "source":
                "observed",
            "count":
                len(
                    MODES
                ),
        },

        "beliefs":
            reconstructed[
                "belief_provenance"
            ],

        "belief_counts": {
            "observed":
                4,

            "estimated":
                40,
        },
    }

    adaptive_metadata = {
        "schema_version":
            ADAPTIVE_HOTCO_ADAPTER_VERSION,

        "estimator":
            "adaptive_cognitive_passport_v1",

        "artifact_sha256":
            _RUNTIME.artifact_sha256,

        "normalized_beliefs_used_by_hotco":
            44,

        "raw_belief_measurements":
            4,

        "estimated_beliefs":
            40,

        "fabricated_raw_beliefs":
            0,

        "availability_policy":
            resolved_availability.provenance[
                "policy"
            ],

        "legacy_passport_serializer_safe":
            False,

        "legacy_question_trigger_safe":
            False,

        "reason":
            (
                "legacy Passport/XAI code assumes every "
                "belief has an observed raw 1..7 value; "
                "adaptive estimated beliefs intentionally "
                "do not"
            ),
    }

    return AdaptiveHOTCOInput(
        participant=
            participant,

        measurement_provenance=
            measurement_provenance,

        adaptive_metadata=
            adaptive_metadata,
    )
