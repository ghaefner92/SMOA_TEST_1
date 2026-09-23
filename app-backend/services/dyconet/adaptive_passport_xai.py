"""Adaptive Cognitive Passport v1 serializer.

Serializes HOTCO-CT output for an Adaptive Cognitive Passport without
misrepresenting estimated beliefs as observed questionnaire responses.

Important:
- HOTCO dynamics are unchanged.
- 4 beliefs are explicit observed measurements.
- 40 beliefs are estimator-derived values.
- XAI diagnostics may use the normalized matrix actually used by HOTCO.
- The legacy question trigger is NOT invoked because it assumes every belief
  has an observed raw 1..7 value.
"""

from __future__ import annotations

from typing import Any, Dict, Mapping, Optional
from uuid import uuid4

from adaptive_hotco_adapter import (
    AdaptiveHOTCOInput,
    ADAPTIVE_HOTCO_ADAPTER_VERSION,
)
from hotco_ct_v4_3 import (
    HOTCOCTv43,
    SimulationResult,
)
from input_mapping_v4_3 import (
    MODES,
    NEEDS,
)
from passport_v2 import (
    build_cognitive_passport as build_base_cognitive_passport,
)
from xai_diagnostics import (
    build_xai_diagnostics,
)
from adaptive_question_trigger import (
    build_adaptive_micro_question,
)


ADAPTIVE_PASSPORT_EXTENSION_VERSION = (
    "adaptive_cognitive_passport_extension_1.0"
)


def _initial_adaptive_lineage(
    *,
    measurement_provenance: Mapping[str, Any],
) -> Dict[str, Any]:

    observed_beliefs = []

    belief_provenance = (
        measurement_provenance.get(
            "beliefs",
            {}
        )
    )

    if isinstance(
        belief_provenance,
        Mapping,
    ):

        for mode in MODES:

            mode_values = (
                belief_provenance.get(
                    mode,
                    {}
                )
            )

            if not isinstance(
                mode_values,
                Mapping,
            ):
                continue

            for need in NEEDS:

                metadata = (
                    mode_values.get(
                        need,
                        {}
                    )
                )

                if (
                    isinstance(
                        metadata,
                        Mapping,
                    )
                    and metadata.get(
                        "source"
                    )
                    == "observed"
                ):

                    observed_beliefs.append(
                        {
                            "target": {
                                "kind":
                                    "belief",

                                "mode":
                                    mode,

                                "need":
                                    need,
                            },

                            "source":
                                "explicit_user_response",

                            "phase":
                                "initial_adaptive_calibration",

                            "revision":
                                1,
                        }
                    )

    return {
        "passport_id":
            str(
                uuid4()
            ),

        "revision":
            1,

        "parent_passport_id":
            None,

        "origin":
            "initial_adaptive_calibration",

        "measurement_history":
            observed_beliefs,

        "last_update_event":
            None,
    }


def build_adaptive_cognitive_passport(
    adaptive_input: AdaptiveHOTCOInput,
    result: SimulationResult,
    engine: HOTCOCTv43,
    *,
    lineage: Optional[
        Mapping[str, Any]
    ] = None,
) -> Dict[str, Any]:
    """Serialize one adaptive HOTCO run with honest measurement provenance."""

    participant = (
        adaptive_input.participant
    )

    belief_counts = (
        adaptive_input
        .measurement_provenance
        .get(
            "belief_counts",
            {},
        )
    )

    observed_belief_count = int(
        belief_counts.get(
            "observed",
            0,
        )
    )

    estimated_belief_count = int(
        belief_counts.get(
            "estimated",
            0,
        )
    )

    if (
        observed_belief_count
        + estimated_belief_count
        != len(MODES) * len(NEEDS)
    ):
        raise ValueError(
            "Adaptive belief provenance must account "
            "for all 44 canonical belief cells"
        )

    output = (
        build_base_cognitive_passport(
            participant,
            result,
            engine,
        )
    )

    passport = output[
        "cognitive_passport"
    ]

    # -----------------------------------------------------
    # 1. Input provenance
    # -----------------------------------------------------

    input_provenance = passport[
        "input_provenance"
    ]

    input_provenance.update(
        {
            "source_schema":
                ADAPTIVE_PASSPORT_EXTENSION_VERSION,

            "policy":
                (
                    "observed_needs_valences_and_"
                    "four_beliefs_plus_model_"
                    "estimated_beliefs"
                ),

            # In the statistical sense, the missing belief
            # cells are completed by a trained estimator.
            "imputation_used":
                True,

            "belief_estimation_used":
                True,

            "belief_estimator":
                "adaptive_cognitive_passport_v1",

            "population_trained_estimator_used":
                True,

            "synthetic_values_used":
                False,

            # Keep the older aggregate flag conservative:
            # population information is encoded in the
            # trained Ridge coefficients.
            "population_or_synthetic_values_used":
                True,

            "missing_required_values":
                0,

            "observed_counts": {
                "needs":
                    len(
                        NEEDS
                    ),

                "beliefs":
                    observed_belief_count,

                "valences":
                    len(
                        MODES
                    ),

                "availability":
                    len(
                        MODES
                    ),

                "environmental_tolerances":
                    sum(
                        participant
                        .environmental_tolerances
                        .get(
                            name
                        )
                        is not None
                        for name in participant
                        .environmental_tolerances
                    ),
            },

            "estimated_counts": {
                "needs":
                    0,

                "beliefs":
                    estimated_belief_count,

                "valences":
                    0,

                "availability":
                    0,
            },

            "transformations": {
                "needs":
                    "(raw_rating - 1) / 6",

                "beliefs_observed":
                    "(raw_rating - 4) / 3",

                "beliefs_estimated":
                    (
                        "Adaptive Cognitive Passport v1: "
                        "TRAIN-fitted Ridge cold-start + "
                        "global residual shrinkage from "
                        "four explicit belief answers; "
                        "clipped to [-1,1]"
                    ),

                "valences":
                    "raw_rating / 3",

                "availability":
                    (
                        "AvailabilityResolver("
                        "user_declared_boolean) -> {0,1}"
                    ),

                "environmental_tolerances":
                    "none; persisted unchanged",
            },
        }
    )

    # -----------------------------------------------------
    # 2. Topology provenance
    # -----------------------------------------------------

    topology = passport[
        "topology"
    ]

    topology[
        "belief_edges_observed"
    ] = observed_belief_count

    topology[
        "belief_edges_estimated"
    ] = estimated_belief_count

    # Retain existing field for downstream compatibility,
    # but give it its truthful value.
    topology[
        "belief_edges_imputed"
    ] = estimated_belief_count

    topology[
        "belief_edge_total"
    ] = 44

    topology[
        "belief_estimation_method"
    ] = (
        "adaptive_cognitive_passport_v1"
    )

    # -----------------------------------------------------
    # 3. Explicit measurement provenance
    # -----------------------------------------------------

    passport[
        "measurement_provenance"
    ] = (
        adaptive_input
        .measurement_provenance
    )

    # Persist only measurements that actually exist on
    # their original questionnaire scales.
    #
    # Estimated beliefs intentionally have no fabricated
    # raw 1..7 representation.
    passport[
        "observed_measurements"
    ] = {
        "needs_raw_1_to_7": {
            need:
                int(
                    participant.raw_needs[
                        need
                    ]
                )
            for need in NEEDS
        },

        "valences_raw_minus3_to_3": {
            mode:
                int(
                    participant.raw_valences[
                        mode
                    ]
                )
            for mode in MODES
        },

        "beliefs_raw_1_to_7": {
            f"{mode}__{need}":
                int(
                    raw_value
                )
            for mode in MODES
            for need, raw_value
            in participant
            .raw_beliefs
            .get(
                mode,
                {},
            )
            .items()
        },
    }

    passport[
        "adaptive_passport"
    ] = {
        "schema_version":
            ADAPTIVE_PASSPORT_EXTENSION_VERSION,

        "adapter_version":
            ADAPTIVE_HOTCO_ADAPTER_VERSION,

        "model_version":
            "v1",

        "artifact_sha256":
            adaptive_input
            .adaptive_metadata[
                "artifact_sha256"
            ],

        "belief_cells_total":
            44,

        "belief_cells_observed":
            observed_belief_count,

        "belief_cells_estimated":
            estimated_belief_count,

        "fabricated_raw_beliefs":
            0,

        "initial_question_policy":
            "fixed_train_derived_four_item_calibration",

        "status":
            "experimental_internal_validation",

        "validation_scope":
            (
                "internal development validation; "
                "not independent confirmatory evidence"
            ),
    }

    # -----------------------------------------------------
    # 4. Lineage
    # -----------------------------------------------------

    lineage_data = (
        dict(
            lineage
        )
        if lineage is not None
        else _initial_adaptive_lineage(
            measurement_provenance=
                adaptive_input
                .measurement_provenance,
        )
    )

    lineage_data.setdefault(
        "passport_id",
        str(
            uuid4()
        ),
    )

    lineage_data.setdefault(
        "revision",
        1,
    )

    lineage_data.setdefault(
        "parent_passport_id",
        None,
    )

    lineage_data.setdefault(
        "origin",
        "initial_adaptive_calibration",
    )

    lineage_data.setdefault(
        "measurement_history",
        [],
    )

    lineage_data.setdefault(
        "last_update_event",
        None,
    )

    passport[
        "lineage"
    ] = lineage_data

    # -----------------------------------------------------
    # 5. HOTCO/XAI diagnostics
    #
    # Safe: diagnostics operate on the normalized profile
    # and trajectory actually used by HOTCO.
    # -----------------------------------------------------

    passport[
        "xai_diagnostics"
    ] = (
        build_xai_diagnostics(
            participant=
                participant,

            result=
                result,

            engine=
                engine,
        )
    )

    # Synchronize compatibility alias exactly as the
    # existing XAI serializer does.
    passport[
        "process_diagnostics"
    ][
        "action_process"
    ][
        "winner_switch_count"
    ] = (
        passport[
            "xai_diagnostics"
        ][
            "leadership"
        ][
            "winner_switch_count"
        ]
    )

    # -----------------------------------------------------
    # 6. Provenance-aware longitudinal questioning
    #
    # XAI determines whether a measurement may be useful.
    # Provenance determines whether the target is:
    #
    #   - a new explicit measurement replacing an
    #     estimated belief, or
    #   - a re-measurement of an already observed
    #     construct.
    #
    # No automatic profile update occurs here.
    # -----------------------------------------------------

    passport[
        "adaptive_questioning"
    ] = (
        build_adaptive_micro_question(
            participant=
                participant,

            xai=
                passport[
                    "xai_diagnostics"
                ],

            belief_provenance=
                adaptive_input
                .measurement_provenance[
                    "beliefs"
                ],

            passport_id=
                str(
                    lineage_data[
                        "passport_id"
                    ]
                ),

            revision=
                int(
                    lineage_data[
                        "revision"
                    ]
                ),

            measurement_history=
                lineage_data.get(
                    "measurement_history",
                    [],
                ),
        )
    )

    return output
