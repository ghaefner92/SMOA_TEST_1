"""Versioned onboarding contract for Adaptive Cognitive Passport v1.

The initial v1 calibration uses four fixed TRAIN-derived belief questions.
Adaptivity after initialization is handled separately by longitudinal
micro-questioning.

This module does not run HOTCO-CT and never represents estimated beliefs as
raw questionnaire responses.
"""

from __future__ import annotations

from hashlib import sha256
from typing import Any, Dict, Mapping

from adaptive_passport_v1 import (
    AdaptivePassportError,
    AdaptivePassportV1,
)
from input_mapping_v4_3 import MODES, NEEDS


ONBOARDING_SCHEMA_VERSION = "adaptive_cognitive_passport_onboarding_1.0"

_RUNTIME = AdaptivePassportV1()


def _fail(message: str) -> None:
    raise AdaptivePassportError(message)


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


def _exact_integer(
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
            f"{path} must be an integer "
            f"from {minimum} to {maximum}"
        )

    try:
        numeric = float(
            value
        )
    except (
        TypeError,
        ValueError,
    ) as exc:

        raise AdaptivePassportError(
            f"{path} must be an integer "
            f"from {minimum} to {maximum}"
        ) from exc

    if (
        not numeric.is_integer()
        or numeric < minimum
        or numeric > maximum
    ):
        _fail(
            f"{path} must be an integer "
            f"from {minimum} to {maximum}"
        )

    return int(
        numeric
    )


def _validate_root(
    payload: Any,
) -> Mapping[str, Any]:

    root = _mapping(
        payload,
        "$",
    )

    allowed = {
        "schema_version",
        "agent_id",
        "responses",
    }

    unknown = sorted(
        str(key)
        for key in root
        if key not in allowed
    )

    if unknown:
        _fail(
            "$ contains unknown fields: "
            + ", ".join(
                unknown
            )
        )

    if (
        root.get(
            "schema_version"
        )
        != ONBOARDING_SCHEMA_VERSION
    ):
        _fail(
            "schema_version must be "
            f"'{ONBOARDING_SCHEMA_VERSION}'"
        )

    agent_id = str(
        root.get(
            "agent_id",
            "",
        )
    ).strip()

    if not agent_id:
        _fail(
            "agent_id is required"
        )

    return root


def _normalize_needs(
    responses: Mapping[str, Any],
) -> tuple[
    Dict[str, int],
    Dict[str, float],
]:

    raw = _mapping(
        responses.get(
            "needs"
        ),
        "responses.needs",
    )

    missing = [
        need
        for need in NEEDS
        if need not in raw
    ]

    unknown = [
        str(key)
        for key in raw
        if key not in NEEDS
    ]

    if missing:
        _fail(
            "Missing needs: "
            + ", ".join(
                missing
            )
        )

    if unknown:
        _fail(
            "Unknown needs: "
            + ", ".join(
                sorted(
                    unknown
                )
            )
        )

    raw_needs = {
        need:
            _exact_integer(
                raw[
                    need
                ],
                minimum=1,
                maximum=7,
                path=(
                    "responses.needs."
                    + need
                ),
            )
        for need in NEEDS
    }

    normalized = {
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

    return (
        raw_needs,
        normalized,
    )


def _normalize_valences(
    responses: Mapping[str, Any],
) -> tuple[
    Dict[str, int],
    Dict[str, float],
]:

    raw = _mapping(
        responses.get(
            "valences"
        ),
        "responses.valences",
    )

    missing = [
        mode
        for mode in MODES
        if mode not in raw
    ]

    unknown = [
        str(key)
        for key in raw
        if key not in MODES
    ]

    if missing:
        _fail(
            "Missing valences: "
            + ", ".join(
                missing
            )
        )

    if unknown:
        _fail(
            "Unknown valence modes: "
            + ", ".join(
                sorted(
                    unknown
                )
            )
        )

    raw_valences = {
        mode:
            _exact_integer(
                raw[
                    mode
                ],
                minimum=-3,
                maximum=3,
                path=(
                    "responses.valences."
                    + mode
                ),
            )
        for mode in MODES
    }

    normalized = {
        mode:
            raw_valences[
                mode
            ]
            / 3.0
        for mode in MODES
    }

    return (
        raw_valences,
        normalized,
    )


def _session_id(
    *,
    agent_id: str,
) -> str:

    material = (
        f"{agent_id}|"
        f"{_RUNTIME.artifact_sha256}|"
        f"{ONBOARDING_SCHEMA_VERSION}"
    )

    return (
        "acp1_"
        + sha256(
            material.encode(
                "utf-8"
            )
        ).hexdigest()[:20]
    )


def start_adaptive_passport(
    payload: Any,
) -> Dict[str, Any]:

    root = _validate_root(
        payload
    )

    responses = _mapping(
        root.get(
            "responses"
        ),
        "responses",
    )

    allowed_responses = {
        "needs",
        "valences",
    }

    unknown = sorted(
        str(key)
        for key in responses
        if key
        not in allowed_responses
    )

    if unknown:
        _fail(
            "responses contains unknown fields: "
            + ", ".join(
                unknown
            )
        )

    raw_needs, needs = (
        _normalize_needs(
            responses
        )
    )

    raw_valences, valences = (
        _normalize_valences(
            responses
        )
    )

    questions = (
        _RUNTIME
        .select_questions()
    )

    agent_id = str(
        root[
            "agent_id"
        ]
    ).strip()

    return {
        "schema_version":
            ONBOARDING_SCHEMA_VERSION,

        "stage":
            "questions",

        "session_id":
            _session_id(
                agent_id=agent_id,
            ),

        "agent_id":
            agent_id,

        "model_version":
            "v1",

        "initial_question_policy":
            "fixed_train_derived_four_item_calibration",

        "adaptive_initial_selection":
            False,

        "longitudinal_adaptation":
            True,

        "question_count":
            len(
                questions
            ),

        "questions":
            questions,

        "normalized_inputs": {
            "needs":
                needs,

            "valences":
                valences,
        },

        "raw_input_provenance": {
            "needs":
                raw_needs,

            "valences":
                raw_valences,
        },

        "artifact": {
            "sha256":
                _RUNTIME.artifact_sha256,
        },

        "policy": {
            "hotco_run":
                False,

            "automatic_profile_update":
                False,

            "route_choice_is_psychological_measurement":
                False,
        },
    }


def complete_adaptive_passport(
    payload: Any,
) -> Dict[str, Any]:

    root = _mapping(
        payload,
        "$",
    )

    allowed = {
        "schema_version",
        "start",
        "answers",
    }

    unknown = sorted(
        str(key)
        for key in root
        if key not in allowed
    )

    if unknown:
        _fail(
            "$ contains unknown fields: "
            + ", ".join(
                unknown
            )
        )

    if (
        root.get(
            "schema_version"
        )
        != ONBOARDING_SCHEMA_VERSION
    ):
        _fail(
            "schema_version must be "
            f"'{ONBOARDING_SCHEMA_VERSION}'"
        )

    start = _mapping(
        root.get(
            "start"
        ),
        "start",
    )

    if (
        start.get(
            "schema_version"
        )
        != ONBOARDING_SCHEMA_VERSION
    ):
        _fail(
            "start object has wrong schema_version"
        )

    if (
        start.get(
            "stage"
        )
        != "questions"
    ):
        _fail(
            "start object must be a questions-stage response"
        )

    if (
        start.get(
            "artifact",
            {},
        ).get(
            "sha256"
        )
        != _RUNTIME.artifact_sha256
    ):
        _fail(
            "start object was generated with a different "
            "Adaptive Passport artifact"
        )

    questions = start.get(
        "questions"
    )

    if not isinstance(
        questions,
        list,
    ):
        _fail(
            "start.questions must be an array"
        )

    expected_cells = [
        str(
            question.get(
                "cell_id"
            )
        )
        for question in questions
    ]

    canonical_expected = [
        question[
            "cell_id"
        ]
        for question in (
            _RUNTIME
            .select_questions()
        )
    ]

    if (
        expected_cells
        != canonical_expected
    ):
        _fail(
            "start.questions does not match "
            "the frozen v1 question policy"
        )

    answers = _mapping(
        root.get(
            "answers"
        ),
        "answers",
    )

    answer_cells = list(
        answers.keys()
    )

    if set(
        answer_cells
    ) != set(
        expected_cells
    ):
        _fail(
            "answers must contain exactly the four "
            "questions returned by start"
        )

    # -----------------------------------------------------
    # Reconstruct normalized values from the observed raw
    # measurements. Do not trust client-returned normalized
    # values as authoritative state.
    # -----------------------------------------------------

    raw_input = _mapping(
        start.get(
            "raw_input_provenance"
        ),
        "start.raw_input_provenance",
    )

    raw_needs_source = _mapping(
        raw_input.get(
            "needs"
        ),
        "start.raw_input_provenance.needs",
    )

    raw_valences_source = _mapping(
        raw_input.get(
            "valences"
        ),
        "start.raw_input_provenance.valences",
    )

    raw_needs, rebuilt_needs = (
        _normalize_needs(
            {
                "needs":
                    raw_needs_source
            }
        )
    )

    raw_valences, rebuilt_valences = (
        _normalize_valences(
            {
                "valences":
                    raw_valences_source
            }
        )
    )

    normalized_inputs = _mapping(
        start.get(
            "normalized_inputs"
        ),
        "start.normalized_inputs",
    )

    supplied_needs = _mapping(
        normalized_inputs.get(
            "needs"
        ),
        "start.normalized_inputs.needs",
    )

    supplied_valences = _mapping(
        normalized_inputs.get(
            "valences"
        ),
        "start.normalized_inputs.valences",
    )

    for need in NEEDS:

        try:
            supplied = float(
                supplied_needs[
                    need
                ]
            )
        except (
            KeyError,
            TypeError,
            ValueError,
        ) as exc:
            raise AdaptivePassportError(
                f"start.normalized_inputs.needs.{need} "
                "is missing or invalid"
            ) from exc

        if abs(
            supplied
            - rebuilt_needs[
                need
            ]
        ) > 1e-12:
            _fail(
                "start normalized needs do not match "
                "the observed raw measurements"
            )

    for mode in MODES:

        try:
            supplied = float(
                supplied_valences[
                    mode
                ]
            )
        except (
            KeyError,
            TypeError,
            ValueError,
        ) as exc:
            raise AdaptivePassportError(
                f"start.normalized_inputs.valences.{mode} "
                "is missing or invalid"
            ) from exc

        if abs(
            supplied
            - rebuilt_valences[
                mode
            ]
        ) > 1e-12:
            _fail(
                "start normalized valences do not match "
                "the observed raw measurements"
            )

    needs = rebuilt_needs
    valences = rebuilt_valences

    canonical_answers = {
        str(cell_id):
            _exact_integer(
                value,
                minimum=1,
                maximum=7,
                path=(
                    "answers."
                    + str(
                        cell_id
                    )
                ),
            )
        for cell_id, value
        in answers.items()
    }

    personalized = (
        _RUNTIME
        .personalize(
            needs=needs,
            valences=valences,
            observed_answers=
                canonical_answers,
            answers_are_raw_1_to_7=True,
            require_complete_k=True,
        )
    )

    return {
        "schema_version":
            ONBOARDING_SCHEMA_VERSION,

        "stage":
            "complete",

        "session_id":
            start.get(
                "session_id"
            ),

        "agent_id":
            start.get(
                "agent_id"
            ),

        "model_version":
            "v1",

        "profile": {
            "needs":
                dict(
                    needs
                ),

            "valences":
                dict(
                    valences
                ),

            "beliefs":
                personalized[
                    "beliefs"
                ],
        },

        "measurement_provenance": {
            "beliefs":
                personalized[
                    "belief_provenance"
                ],

            "needs":
                "observed",

            "valences":
                "observed",
        },

        # Raw questionnaire values exist ONLY for constructs
        # that were explicitly answered by the participant.
        # Estimated belief cells intentionally have no raw
        # 1..7 representation.
        "observed_measurements": {
            "needs_raw_1_to_7":
                raw_needs,

            "valences_raw_minus3_to_3":
                raw_valences,

            "beliefs_raw_1_to_7":
                canonical_answers,
        },

        "personalization":
            personalized[
                "personalization"
            ],

        "artifact":
            personalized[
                "artifact"
            ],

        "ready_for_hotco":
            True,

        "hotco_run":
            False,

        "policy":
            personalized[
                "policy"
            ],
    }
