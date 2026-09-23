"""Longitudinal explicit updates for Adaptive Cognitive Passport v1.

This module never infers a profile update from behavior, routes, context, HOTCO,
or an LLM.

A valid update requires:
- the previous Adaptive Cognitive Passport,
- its active backend-generated provenance-aware micro-question,
- one explicit 1..7 participant response,
- explicit user confirmation.

For belief questions:
- an estimated belief can become observed,
- an already observed belief can be re-measured.

After each explicit update, all still-estimated beliefs are recomputed using the
same frozen Adaptive Cognitive Passport v1 estimator and all currently observed
belief responses.

No fabricated raw questionnaire values are created.
"""

from __future__ import annotations

from datetime import datetime, timezone
import math
from typing import Any, Dict, Mapping, Tuple
from uuid import uuid4

from adaptive_hotco_adapter import (
    ADAPTIVE_HOTCO_ADAPTER_VERSION,
    AdaptiveHOTCOInput,
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


ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION = (
    "adaptive_profile_update_1.0"
)

_RUNTIME = AdaptivePassportV1()


class AdaptiveProfileUpdateError(
    ValueError
):
    """Raised when a longitudinal adaptive update is invalid."""


def _fail(
    message: str,
) -> None:
    raise AdaptiveProfileUpdateError(
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
        raise AdaptiveProfileUpdateError(
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


def _unwrap_passport(
    value: Any,
) -> Mapping[str, Any]:

    root = _mapping(
        value,
        "previous_passport",
    )

    if (
        "cognitive_passport"
        in root
    ):
        return _mapping(
            root.get(
                "cognitive_passport"
            ),
            (
                "previous_passport."
                "cognitive_passport"
            ),
        )

    return root


def _same_target(
    submitted: Mapping[str, Any],
    expected: Mapping[str, Any],
) -> bool:

    kind = str(
        expected.get(
            "kind",
            "",
        )
    )

    if (
        submitted.get(
            "kind"
        )
        != kind
    ):
        return False

    if kind == "need":
        return (
            submitted.get(
                "need"
            )
            == expected.get(
                "need"
            )
        )

    if kind == "valence":
        return (
            submitted.get(
                "mode"
            )
            == expected.get(
                "mode"
            )
        )

    if kind == "belief":
        return (
            submitted.get(
                "mode"
            )
            == expected.get(
                "mode"
            )
            and submitted.get(
                "need"
            )
            == expected.get(
                "need"
            )
        )

    return False


def _active_candidate(
    passport: Mapping[str, Any],
) -> Mapping[str, Any]:

    adaptive = _mapping(
        passport.get(
            "adaptive_questioning"
        ),
        (
            "previous_passport."
            "adaptive_questioning"
        ),
    )

    if (
        adaptive.get(
            "question_needed"
        )
        is not True
    ):
        _fail(
            "previous_passport has no active "
            "adaptive micro-question"
        )

    candidate = _mapping(
        adaptive.get(
            "candidate"
        ),
        (
            "previous_passport."
            "adaptive_questioning."
            "candidate"
        ),
    )

    if (
        candidate.get(
            "scope"
        )
        != "profile"
    ):
        _fail(
            "only profile-scope adaptive "
            "questions may update the Passport"
        )

    return candidate


def _raw_measurements(
    passport: Mapping[str, Any],
) -> tuple[
    Dict[str, int],
    Dict[str, int],
    Dict[str, int],
]:

    observed = _mapping(
        passport.get(
            "observed_measurements"
        ),
        (
            "previous_passport."
            "observed_measurements"
        ),
    )

    raw_needs_source = _mapping(
        observed.get(
            "needs_raw_1_to_7"
        ),
        (
            "previous_passport."
            "observed_measurements."
            "needs_raw_1_to_7"
        ),
    )

    raw_valences_source = _mapping(
        observed.get(
            "valences_raw_minus3_to_3"
        ),
        (
            "previous_passport."
            "observed_measurements."
            "valences_raw_minus3_to_3"
        ),
    )

    raw_beliefs_source = _mapping(
        observed.get(
            "beliefs_raw_1_to_7"
        ),
        (
            "previous_passport."
            "observed_measurements."
            "beliefs_raw_1_to_7"
        ),
    )

    raw_needs: Dict[
        str,
        int,
    ] = {}

    for need in NEEDS:

        if need not in raw_needs_source:
            _fail(
                "previous Passport is missing "
                f"observed need {need}"
            )

        raw_needs[
            need
        ] = _integer(
            raw_needs_source[
                need
            ],
            minimum=1,
            maximum=7,
            path=(
                "previous_passport."
                "observed_measurements."
                "needs_raw_1_to_7."
                + need
            ),
        )

    raw_valences: Dict[
        str,
        int,
    ] = {}

    for mode in MODES:

        if mode not in raw_valences_source:
            _fail(
                "previous Passport is missing "
                f"observed valence {mode}"
            )

        raw_valences[
            mode
        ] = _integer(
            raw_valences_source[
                mode
            ],
            minimum=-3,
            maximum=3,
            path=(
                "previous_passport."
                "observed_measurements."
                "valences_raw_minus3_to_3."
                + mode
            ),
        )

    raw_beliefs: Dict[
        str,
        int,
    ] = {}

    for cell_id, value in (
        raw_beliefs_source.items()
    ):

        cell_id = str(
            cell_id
        )

        if cell_id not in (
            _RUNTIME.all_cells
        ):
            _fail(
                "previous Passport contains "
                "unknown observed belief cell "
                + cell_id
            )

        raw_beliefs[
            cell_id
        ] = _integer(
            value,
            minimum=1,
            maximum=7,
            path=(
                "previous_passport."
                "observed_measurements."
                "beliefs_raw_1_to_7."
                + cell_id
            ),
        )

    if len(
        raw_beliefs
    ) < 4:
        _fail(
            "Adaptive Passport must retain at least "
            "the four initial observed beliefs"
        )

    return (
        raw_needs,
        raw_valences,
        raw_beliefs,
    )


def _availability_from_passport(
    passport: Mapping[str, Any],
):
    profile = _mapping(
        passport.get(
            "profile"
        ),
        "previous_passport.profile",
    )

    availability_source = _mapping(
        profile.get(
            "availability"
        ),
        (
            "previous_passport."
            "profile.availability"
        ),
    )

    raw: Dict[
        str,
        bool,
    ] = {}

    for mode in MODES:

        value = availability_source.get(
            mode
        )

        if not isinstance(
            value,
            bool,
        ):
            _fail(
                "previous_passport.profile."
                f"availability.{mode} "
                "must be boolean"
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
        raise AdaptiveProfileUpdateError(
            str(exc)
        ) from exc


def _tolerances_from_passport(
    passport: Mapping[str, Any],
) -> Dict[
    str,
    Any,
]:

    profile = _mapping(
        passport.get(
            "profile"
        ),
        "previous_passport.profile",
    )

    source = profile.get(
        "environmental_tolerances"
    )

    if source is None:
        return {
            name: None
            for name
            in ENVIRONMENTAL_TOLERANCES
        }

    source = _mapping(
        source,
        (
            "previous_passport.profile."
            "environmental_tolerances"
        ),
    )

    result: Dict[
        str,
        Any,
    ] = {}

    for name in (
        ENVIRONMENTAL_TOLERANCES
    ):

        value = source.get(
            name
        )

        if value is None:
            result[
                name
            ] = None
            continue

        if isinstance(
            value,
            bool,
        ):
            _fail(
                "environmental tolerance "
                f"{name} must be numeric or null"
            )

        try:
            numeric = float(
                value
            )
        except (
            TypeError,
            ValueError,
        ) as exc:
            raise AdaptiveProfileUpdateError(
                "environmental tolerance "
                f"{name} must be numeric or null"
            ) from exc

        if (
            not math.isfinite(
                numeric
            )
            or numeric < 0.0
            or numeric > 1.0
        ):
            _fail(
                "environmental tolerance "
                f"{name} must lie within [0,1]"
            )

        result[
            name
        ] = numeric

    return result



def reconstruct_adaptive_participant(
    passport_value: Any,
) -> ParticipantInput:
    """Reconstruct canonical HOTCO input from an Adaptive Cognitive Passport.

    Only genuinely observed raw measurements are treated as raw questionnaire
    responses. The dense belief matrix is regenerated with the frozen Adaptive
    Cognitive Passport estimator, so estimated beliefs are never inverted into
    fabricated 1..7 responses.
    """

    previous = _unwrap_passport(
        passport_value
    )

    adaptive_extension = _mapping(
        previous.get(
            "adaptive_passport"
        ),
        "cognitive_passport.adaptive_passport",
    )

    if (
        adaptive_extension.get(
            "artifact_sha256"
        )
        != _RUNTIME.artifact_sha256
    ):
        _fail(
            "Adaptive Passport uses a different "
            "estimator artifact"
        )

    if (
        adaptive_extension.get(
            "model_version"
        )
        != "v1"
    ):
        _fail(
            "Adaptive Passport model_version is not supported"
        )

    (
        raw_needs,
        raw_valences,
        raw_beliefs,
    ) = _raw_measurements(
        previous
    )

    required_initial_cells = {
        question["cell_id"]
        for question in _RUNTIME.select_questions()
    }

    if not required_initial_cells.issubset(
        raw_beliefs
    ):
        _fail(
            "Adaptive Passport no longer contains "
            "all four initial calibration measurements"
        )

    provenance = _mapping(
        previous.get(
            "input_provenance"
        ),
        "cognitive_passport.input_provenance",
    )

    observed_counts = _mapping(
        provenance.get(
            "observed_counts"
        ),
        (
            "cognitive_passport.input_provenance."
            "observed_counts"
        ),
    )

    estimated_counts = _mapping(
        provenance.get(
            "estimated_counts"
        ),
        (
            "cognitive_passport.input_provenance."
            "estimated_counts"
        ),
    )

    observed_belief_count = _integer(
        observed_counts.get(
            "beliefs"
        ),
        minimum=4,
        maximum=44,
        path=(
            "cognitive_passport.input_provenance."
            "observed_counts.beliefs"
        ),
    )

    estimated_belief_count = _integer(
        estimated_counts.get(
            "beliefs"
        ),
        minimum=0,
        maximum=40,
        path=(
            "cognitive_passport.input_provenance."
            "estimated_counts.beliefs"
        ),
    )

    if (
        observed_belief_count
        != len(raw_beliefs)
    ):
        _fail(
            "Adaptive Passport observed belief count "
            "does not match its raw measurements"
        )

    if (
        observed_belief_count
        + estimated_belief_count
        != 44
    ):
        _fail(
            "Adaptive Passport belief provenance "
            "must account for all 44 cells"
        )

    normalized_needs = {
        need:
            (
                raw_needs[need]
                - 1.0
            )
            / 6.0
        for need in NEEDS
    }

    normalized_valences = {
        mode:
            raw_valences[mode]
            / 3.0
        for mode in MODES
    }

    try:
        personalized = _RUNTIME.personalize(
            needs=
                normalized_needs,

            valences=
                normalized_valences,

            observed_answers=
                raw_beliefs,

            answers_are_raw_1_to_7=
                True,

            # Initial passports contain four observations;
            # longitudinal revisions may contain more.
            require_complete_k=
                False,
        )

    except AdaptivePassportError as exc:
        raise AdaptiveProfileUpdateError(
            str(exc)
        ) from exc

    resolved_availability = (
        _availability_from_passport(
            previous
        )
    )

    tolerances = (
        _tolerances_from_passport(
            previous
        )
    )

    sparse_raw_beliefs: Dict[
        str,
        Dict[str, int],
    ] = {
        mode: {}
        for mode in MODES
    }

    for cell_id, raw_value in (
        raw_beliefs.items()
    ):
        mode, need = cell_id.split(
            "__",
            1,
        )

        sparse_raw_beliefs[
            mode
        ][
            need
        ] = int(
            raw_value
        )

    agent_id = str(
        previous.get(
            "agent_id",
            "",
        )
    ).strip()

    if not agent_id:
        _fail(
            "Adaptive Passport agent_id is required"
        )

    return ParticipantInput(
        agent_id=
            agent_id,

        raw_needs=
            dict(
                raw_needs
            ),

        raw_beliefs=
            sparse_raw_beliefs,

        raw_valences=
            dict(
                raw_valences
            ),

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
                    personalized[
                        "beliefs"
                    ][
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

def prepare_adaptive_profile_update(
    payload: Any,
) -> Tuple[
    AdaptiveHOTCOInput,
    Dict[str, Any],
]:
    """Prepare one explicit Adaptive Passport revision."""

    root = _mapping(
        payload,
        "$",
    )

    allowed = {
        "schema_version",
        "previous_passport",
        "update",
    }

    unknown = sorted(
        str(
            key
        )
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
        != ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION
    ):
        _fail(
            "schema_version must be "
            f"'{ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION}'"
        )

    previous = _unwrap_passport(
        root.get(
            "previous_passport"
        )
    )

    adaptive_extension = _mapping(
        previous.get(
            "adaptive_passport"
        ),
        (
            "previous_passport."
            "adaptive_passport"
        ),
    )

    if (
        adaptive_extension.get(
            "artifact_sha256"
        )
        != _RUNTIME.artifact_sha256
    ):
        _fail(
            "previous Passport uses a different "
            "Adaptive Passport artifact"
        )

    lineage = _mapping(
        previous.get(
            "lineage"
        ),
        "previous_passport.lineage",
    )

    previous_id = str(
        lineage.get(
            "passport_id",
            "",
        )
    ).strip()

    if not previous_id:
        _fail(
            "previous_passport.lineage."
            "passport_id is required"
        )

    previous_revision = _integer(
        lineage.get(
            "revision"
        ),
        minimum=1,
        maximum=1_000_000,
        path=(
            "previous_passport."
            "lineage.revision"
        ),
    )

    expected_candidate = (
        _active_candidate(
            previous
        )
    )

    expected_question_id = str(
        expected_candidate.get(
            "question_id",
            "",
        )
    ).strip()

    if not expected_question_id:
        _fail(
            "active adaptive question "
            "has no question_id"
        )

    expected_target = _mapping(
        expected_candidate.get(
            "target"
        ),
        (
            "previous_passport."
            "adaptive_questioning."
            "candidate.target"
        ),
    )

    update = _mapping(
        root.get(
            "update"
        ),
        "update",
    )

    if (
        update.get(
            "explicit_user_confirmation"
        )
        is not True
    ):
        _fail(
            "update.explicit_user_confirmation "
            "must be true"
        )

    if (
        str(
            update.get(
                "question_id",
                "",
            )
        )
        != expected_question_id
    ):
        _fail(
            "update.question_id does not match "
            "the active adaptive question"
        )

    submitted_target = _mapping(
        update.get(
            "target"
        ),
        "update.target",
    )

    if not _same_target(
        submitted_target,
        expected_target,
    ):
        _fail(
            "update.target does not match "
            "the active adaptive question"
        )

    new_user_rating = _integer(
        update.get(
            "raw_rating"
        ),
        minimum=1,
        maximum=7,
        path="update.raw_rating",
    )

    (
        raw_needs,
        raw_valences,
        raw_beliefs,
    ) = _raw_measurements(
        previous
    )

    kind = str(
        expected_target.get(
            "kind",
            "",
        )
    )

    previous_raw_rating = None
    previous_measurement_status = str(
        expected_target.get(
            "measurement_status",
            "observed",
        )
    )

    previous_profile = _mapping(
        previous.get(
            "profile"
        ),
        "previous_passport.profile",
    )

    previous_normalized_value = None

    # -----------------------------------------------------
    # Apply exactly one explicit measurement.
    # -----------------------------------------------------

    if kind == "need":

        need = str(
            expected_target.get(
                "need",
                "",
            )
        )

        if need not in NEEDS:
            _fail(
                "candidate need is not recognized"
            )

        previous_raw_rating = (
            raw_needs[
                need
            ]
        )

        raw_needs[
            need
        ] = new_user_rating

    elif kind == "valence":

        mode = str(
            expected_target.get(
                "mode",
                "",
            )
        )

        if mode not in MODES:
            _fail(
                "candidate valence mode "
                "is not recognized"
            )

        # User-facing response is 1..7.
        previous_raw_rating = (
            raw_valences[
                mode
            ]
            + 4
        )

        raw_valences[
            mode
        ] = (
            new_user_rating
            - 4
        )

    elif kind == "belief":

        mode = str(
            expected_target.get(
                "mode",
                "",
            )
        )

        need = str(
            expected_target.get(
                "need",
                "",
            )
        )

        if (
            mode not in MODES
            or need not in NEEDS
        ):
            _fail(
                "candidate belief target "
                "is not recognized"
            )

        cell_id = (
            f"{mode}__{need}"
        )

        if cell_id in raw_beliefs:

            previous_raw_rating = (
                raw_beliefs[
                    cell_id
                ]
            )

            if (
                previous_measurement_status
                != "observed"
            ):
                _fail(
                    "candidate provenance conflicts "
                    "with existing raw belief"
                )

        else:

            if (
                previous_measurement_status
                != "estimated"
            ):
                _fail(
                    "candidate says belief is observed "
                    "but no raw measurement exists"
                )

            beliefs_profile = _mapping(
                previous_profile.get(
                    "beliefs"
                ),
                (
                    "previous_passport."
                    "profile.beliefs"
                ),
            )

            mode_profile = _mapping(
                beliefs_profile.get(
                    mode
                ),
                (
                    "previous_passport."
                    f"profile.beliefs.{mode}"
                ),
            )

            try:
                previous_normalized_value = float(
                    mode_profile[
                        need
                    ]
                )
            except (
                KeyError,
                TypeError,
                ValueError,
            ) as exc:
                raise AdaptiveProfileUpdateError(
                    "previous estimated belief "
                    "cannot be read"
                ) from exc

        raw_beliefs[
            cell_id
        ] = new_user_rating

    else:
        _fail(
            "candidate target kind "
            "is not supported"
        )

    # -----------------------------------------------------
    # Rebuild the dense profile from raw observations.
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
        personalized = (
            _RUNTIME.personalize(
                needs=
                    normalized_needs,

                valences=
                    normalized_valences,

                observed_answers=
                    raw_beliefs,

                answers_are_raw_1_to_7=
                    True,

                # Longitudinal mode may contain
                # >4 observed belief measurements.
                require_complete_k=
                    False,
            )
        )

    except AdaptivePassportError as exc:
        raise AdaptiveProfileUpdateError(
            str(exc)
        ) from exc

    resolved_availability = (
        _availability_from_passport(
            previous
        )
    )

    tolerances = (
        _tolerances_from_passport(
            previous
        )
    )

    sparse_raw_beliefs: Dict[
        str,
        Dict[str, int],
    ] = {
        mode: {}
        for mode in MODES
    }

    for cell_id, raw_value in (
        raw_beliefs.items()
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
        ] = int(
            raw_value
        )

    participant = ParticipantInput(
        agent_id=
            str(
                previous.get(
                    "agent_id"
                )
            ),

        raw_needs=
            dict(
                raw_needs
            ),

        raw_beliefs=
            sparse_raw_beliefs,

        raw_valences=
            dict(
                raw_valences
            ),

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
                    personalized[
                        "beliefs"
                    ][
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

    observed_belief_count = len(
        raw_beliefs
    )

    estimated_belief_count = (
        44
        - observed_belief_count
    )

    adaptive_input = AdaptiveHOTCOInput(
        participant=
            participant,

        measurement_provenance={
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
                personalized[
                    "belief_provenance"
                ],

            "belief_counts": {
                "observed":
                    observed_belief_count,

                "estimated":
                    estimated_belief_count,
            },
        },

        adaptive_metadata={
            "schema_version":
                ADAPTIVE_HOTCO_ADAPTER_VERSION,

            "estimator":
                "adaptive_cognitive_passport_v1",

            "artifact_sha256":
                _RUNTIME.artifact_sha256,

            "normalized_beliefs_used_by_hotco":
                44,

            "raw_belief_measurements":
                observed_belief_count,

            "estimated_beliefs":
                estimated_belief_count,

            "fabricated_raw_beliefs":
                0,

            "availability_policy":
                resolved_availability
                .provenance[
                    "policy"
                ],

            "longitudinal_update":
                True,
        },
    )

    # -----------------------------------------------------
    # Append-only lineage.
    # -----------------------------------------------------

    new_revision = (
        previous_revision
        + 1
    )

    timestamp = (
        datetime.now(
            timezone.utc
        )
        .isoformat()
        .replace(
            "+00:00",
            "Z",
        )
    )

    if kind == "belief":

        transition = (
            "estimated_to_observed"
            if previous_measurement_status
            == "estimated"
            else
            "observed_to_observed_remeasurement"
        )

    else:
        transition = (
            "observed_to_observed_remeasurement"
        )

    event: Dict[
        str,
        Any,
    ] = {
        "revision":
            new_revision,

        "timestamp":
            timestamp,

        "source":
            "explicit_user_response",

        "scope":
            "profile",

        "question_id":
            expected_question_id,

        "trigger":
            expected_candidate.get(
                "trigger"
            ),

        "target": {
            key:
                value
            for key, value
            in expected_target.items()
            if key in {
                "kind",
                "mode",
                "need",
            }
        },

        "measurement_transition":
            transition,

        "previous_measurement_status":
            previous_measurement_status,

        "previous_raw_rating":
            previous_raw_rating,

        "previous_normalized_value":
            previous_normalized_value,

        "new_raw_rating":
            new_user_rating,

        "new_normalized_value":
            (
                (
                    new_user_rating
                    - 4.0
                )
                / 3.0
                if kind == "belief"
                else (
                    (
                        new_user_rating
                        - 1.0
                    )
                    / 6.0
                    if kind == "need"
                    else (
                        new_user_rating
                        - 4.0
                    )
                    / 3.0
                )
            ),

        "explicit_user_confirmation":
            True,

        "automatic_update":
            False,

        "observed_belief_count_after_update":
            observed_belief_count,

        "estimated_belief_count_after_update":
            estimated_belief_count,
    }

    previous_history = lineage.get(
        "measurement_history",
        [],
    )

    if not isinstance(
        previous_history,
        list,
    ):
        _fail(
            "previous_passport.lineage."
            "measurement_history must be an array"
        )

    history = [
        dict(
            item
        )
        for item in previous_history
        if isinstance(
            item,
            Mapping,
        )
    ]

    history.append(
        event
    )

    new_lineage: Dict[
        str,
        Any,
    ] = {
        "passport_id":
            str(
                uuid4()
            ),

        "revision":
            new_revision,

        "parent_passport_id":
            previous_id,

        "origin":
            "explicit_adaptive_micro_question_update",

        "measurement_history":
            history,

        "last_update_event":
            event,
    }

    return (
        adaptive_input,
        new_lineage,
    )
