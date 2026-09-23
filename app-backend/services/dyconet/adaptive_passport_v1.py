"""Adaptive Cognitive Passport v1 runtime.

Deployment runtime for the frozen 4 × 11 Adaptive Cognitive Passport model.

This module is intentionally independent from HOTCO-CT dynamics:
- it reconstructs a dense 4 × 11 belief matrix,
- selects adaptive belief questions,
- applies explicit-answer personalization,
- records observed/estimated provenance.

It never infers psychological measurements from route choice or context and
never updates a profile without an explicit user answer.
"""

from __future__ import annotations

from hashlib import sha256
import json
from pathlib import Path
from typing import Any, Dict, Iterable, Mapping, Optional, Sequence

import numpy as np

from input_mapping_v4_3 import MODES, NEEDS


ARTIFACT_SCHEMA_VERSION = "adaptive_cognitive_passport_artifact_1.0"
RUNTIME_VERSION = "adaptive_cognitive_passport_runtime_1.0"

DEFAULT_ARTIFACT_PATH = (
    Path(__file__).resolve().parent
    / "artifacts"
    / "adaptive_passport_v1.json"
)


class AdaptivePassportError(ValueError):
    """Raised when Adaptive Cognitive Passport input or artifact is invalid."""


def _finite_number(
    value: Any,
    *,
    path: str,
    minimum: float,
    maximum: float,
) -> float:
    if isinstance(value, bool):
        raise AdaptivePassportError(
            f"{path} must be numeric"
        )

    try:
        numeric = float(value)
    except (TypeError, ValueError) as exc:
        raise AdaptivePassportError(
            f"{path} must be numeric"
        ) from exc

    if not np.isfinite(numeric):
        raise AdaptivePassportError(
            f"{path} must be finite"
        )

    if (
        numeric < minimum
        or numeric > maximum
    ):
        raise AdaptivePassportError(
            f"{path} must be within "
            f"[{minimum}, {maximum}]"
        )

    return numeric


def raw_belief_rating_to_normalized(
    raw_rating: Any,
) -> float:
    """Convert one explicit 1..7 belief response to [-1, 1]."""

    if isinstance(raw_rating, bool):
        raise AdaptivePassportError(
            "raw belief rating must be an integer from 1 to 7"
        )

    try:
        numeric = float(raw_rating)
    except (TypeError, ValueError) as exc:
        raise AdaptivePassportError(
            "raw belief rating must be an integer from 1 to 7"
        ) from exc

    if (
        not numeric.is_integer()
        or numeric < 1
        or numeric > 7
    ):
        raise AdaptivePassportError(
            "raw belief rating must be an integer from 1 to 7"
        )

    return (numeric - 4.0) / 3.0


class AdaptivePassportV1:
    """NumPy runtime for the frozen Adaptive Cognitive Passport v1."""

    def __init__(
        self,
        artifact_path: Path | str = DEFAULT_ARTIFACT_PATH,
    ) -> None:

        self.artifact_path = Path(
            artifact_path
        ).resolve()

        if not self.artifact_path.exists():
            raise AdaptivePassportError(
                f"Adaptive Passport artifact not found: "
                f"{self.artifact_path}"
            )

        raw_bytes = self.artifact_path.read_bytes()

        self.artifact_sha256 = (
            sha256(
                raw_bytes
            ).hexdigest()
        )

        try:
            self.artifact = json.loads(
                raw_bytes.decode("utf-8")
            )
        except (
            UnicodeDecodeError,
            json.JSONDecodeError,
        ) as exc:
            raise AdaptivePassportError(
                "Adaptive Passport artifact is not valid UTF-8 JSON"
            ) from exc

        self._validate_artifact()

        ridge = self.artifact[
            "ridge"
        ]

        adaptive = self.artifact[
            "adaptive_questioning"
        ]

        self.intercept = float(
            ridge[
                "intercept"
            ]
        )

        self.coefficients = np.asarray(
            ridge[
                "coefficients"
            ],
            dtype=np.float64,
        )

        self.transformed_feature_names = tuple(
            str(value)
            for value in ridge[
                "transformed_feature_names"
            ]
        )

        self.numeric_features = tuple(
            str(value)
            for value in ridge[
                "numeric_features"
            ]
        )

        self.categorical_features = tuple(
            str(value)
            for value in ridge[
                "categorical_features"
            ]
        )

        self.imputer_statistics = {
            str(key): float(value)
            for key, value in (
                ridge[
                    "numeric_imputer"
                ][
                    "statistics"
                ]
                .items()
            )
        }

        self.onehot_categories = {
            str(key): tuple(
                str(value)
                for value in values
            )
            for key, values in (
                ridge[
                    "onehot_categories"
                ]
                .items()
            )
        }

        self.k = int(
            adaptive[
                "k"
            ]
        )

        self.global_lambda = float(
            adaptive[
                "global_shrinkage_lambda"
            ]
        )

        self.action_bonus = float(
            adaptive[
                "action_diversity_bonus"
            ]
        )

        self.need_bonus = float(
            adaptive[
                "need_diversity_bonus"
            ]
        )

        self.utility_by_cell = {
            str(row["cell_id"]):
                float(row["utility"])
            for row in adaptive[
                "utility_table"
            ]
        }

        self.cell_metadata = {
            str(row["cell_id"]): {
                "hotco_mode":
                    str(
                        row[
                            "hotco_mode"
                        ]
                    ),
                "model_need":
                    str(
                        row[
                            "model_need"
                        ]
                    ),
                "utility":
                    float(
                        row[
                            "utility"
                        ]
                    ),
            }
            for row in adaptive[
                "utility_table"
            ]
        }

        self.all_cells = tuple(
            f"{mode}__{need}"
            for mode in MODES
            for need in NEEDS
        )

    def _validate_artifact(
        self,
    ) -> None:

        artifact = self.artifact

        if (
            artifact.get(
                "schema_version"
            )
            != ARTIFACT_SCHEMA_VERSION
        ):
            raise AdaptivePassportError(
                "Unsupported Adaptive Passport artifact schema"
            )

        if (
            artifact.get(
                "model_version"
            )
            != "v1"
        ):
            raise AdaptivePassportError(
                "Artifact must contain Adaptive Cognitive Passport v1"
            )

        contract = artifact.get(
            "canonical_contract"
        )

        if not isinstance(
            contract,
            Mapping,
        ):
            raise AdaptivePassportError(
                "Artifact canonical_contract is missing"
            )

        artifact_modes = tuple(
            contract.get(
                "modes",
                (),
            )
        )

        artifact_needs = tuple(
            contract.get(
                "needs",
                (),
            )
        )

        if artifact_modes != tuple(
            MODES
        ):
            raise AdaptivePassportError(
                "Artifact mode order does not match canonical HOTCO modes"
            )

        if artifact_needs != tuple(
            NEEDS
        ):
            raise AdaptivePassportError(
                "Artifact need order does not match canonical HOTCO needs"
            )

        if (
            contract.get(
                "belief_matrix_shape"
            )
            != [4, 11]
        ):
            raise AdaptivePassportError(
                "Artifact belief matrix must be 4 × 11"
            )

        ridge = artifact.get(
            "ridge"
        )

        if not isinstance(
            ridge,
            Mapping,
        ):
            raise AdaptivePassportError(
                "Artifact ridge section is missing"
            )

        names = ridge.get(
            "transformed_feature_names"
        )

        coefficients = ridge.get(
            "coefficients"
        )

        if (
            not isinstance(
                names,
                list,
            )
            or not isinstance(
                coefficients,
                list,
            )
            or len(names)
            != len(coefficients)
            or len(names) == 0
        ):
            raise AdaptivePassportError(
                "Artifact Ridge feature/coefficient contract is invalid"
            )

        adaptive = artifact.get(
            "adaptive_questioning"
        )

        if not isinstance(
            adaptive,
            Mapping,
        ):
            raise AdaptivePassportError(
                "Artifact adaptive_questioning section is missing"
            )

        utility_table = adaptive.get(
            "utility_table"
        )

        if (
            not isinstance(
                utility_table,
                list,
            )
            or len(
                utility_table
            )
            != 44
        ):
            raise AdaptivePassportError(
                "Artifact must contain utility for exactly 44 canonical cells"
            )

        utility_cells = {
            str(
                row.get(
                    "cell_id"
                )
            )
            for row in utility_table
        }

        expected_cells = {
            f"{mode}__{need}"
            for mode in MODES
            for need in NEEDS
        }

        if utility_cells != expected_cells:
            raise AdaptivePassportError(
                "Artifact utility table does not match canonical 4 × 11 cells"
            )

    def _validate_needs(
        self,
        needs: Mapping[str, Any],
    ) -> Dict[str, float]:

        if not isinstance(
            needs,
            Mapping,
        ):
            raise AdaptivePassportError(
                "needs must be an object"
            )

        missing = [
            need
            for need in NEEDS
            if need not in needs
        ]

        unknown = [
            str(key)
            for key in needs
            if key not in NEEDS
        ]

        if missing:
            raise AdaptivePassportError(
                "Missing needs: "
                + ", ".join(
                    missing
                )
            )

        if unknown:
            raise AdaptivePassportError(
                "Unknown needs: "
                + ", ".join(
                    sorted(
                        unknown
                    )
                )
            )

        return {
            need:
                _finite_number(
                    needs[
                        need
                    ],
                    path=f"needs.{need}",
                    minimum=0.0,
                    maximum=1.0,
                )
            for need in NEEDS
        }

    def _validate_valences(
        self,
        valences: Mapping[str, Any],
    ) -> Dict[str, Optional[float]]:

        if not isinstance(
            valences,
            Mapping,
        ):
            raise AdaptivePassportError(
                "valences must be an object"
            )

        unknown = [
            str(key)
            for key in valences
            if key not in MODES
        ]

        if unknown:
            raise AdaptivePassportError(
                "Unknown valence modes: "
                + ", ".join(
                    sorted(
                        unknown
                    )
                )
            )

        normalized: Dict[
            str,
            Optional[float],
        ] = {}

        for mode in MODES:

            value = valences.get(
                mode
            )

            if value is None:
                normalized[
                    mode
                ] = None
                continue

            normalized[
                mode
            ] = _finite_number(
                value,
                path=f"valences.{mode}",
                minimum=-1.0,
                maximum=1.0,
            )

        return normalized

    def _feature_vector(
        self,
        *,
        needs: Mapping[str, float],
        valences: Mapping[str, Optional[float]],
        mode: str,
        need: str,
    ) -> np.ndarray:

        cell_id = (
            f"{mode}__{need}"
        )

        target_valence = (
            valences[
                mode
            ]
        )

        raw_numeric: Dict[
            str,
            Optional[float],
        ] = {
            f"need_{name}":
                float(
                    needs[
                        name
                    ]
                )
            for name in NEEDS
        }

        raw_numeric[
            "target_need_priority"
        ] = float(
            needs[
                need
            ]
        )

        raw_numeric[
            "target_mode_valence"
        ] = target_valence

        raw_numeric[
            "target_mode_valence_missing"
        ] = (
            1.0
            if target_valence is None
            else 0.0
        )

        transformed: Dict[
            str,
            float,
        ] = {}

        for feature in self.numeric_features:

            if feature not in raw_numeric:
                raise AdaptivePassportError(
                    f"Unsupported numeric feature in artifact: {feature}"
                )

            value = raw_numeric[
                feature
            ]

            if value is None:
                if (
                    feature
                    not in self.imputer_statistics
                ):
                    raise AdaptivePassportError(
                        f"No imputation statistic for {feature}"
                    )

                value = (
                    self.imputer_statistics[
                        feature
                    ]
                )

            transformed[
                f"numeric__{feature}"
            ] = float(
                value
            )

        if (
            self.categorical_features
            != ("cell_id",)
        ):
            raise AdaptivePassportError(
                "Adaptive Passport v1 expects cell_id "
                "as the only categorical feature"
            )

        categories = (
            self.onehot_categories.get(
                "cell_id"
            )
        )

        if categories is None:
            raise AdaptivePassportError(
                "Artifact cell_id categories are missing"
            )

        if cell_id not in categories:
            raise AdaptivePassportError(
                f"Canonical cell absent from artifact categories: {cell_id}"
            )

        for category in categories:

            transformed[
                "categorical__cell_id_"
                + category
            ] = (
                1.0
                if category
                == cell_id
                else 0.0
            )

        try:
            vector = np.asarray(
                [
                    transformed[
                        feature_name
                    ]
                    for feature_name
                    in self.transformed_feature_names
                ],
                dtype=np.float64,
            )

        except KeyError as exc:
            raise AdaptivePassportError(
                "Artifact contains an unsupported transformed feature: "
                f"{exc.args[0]}"
            ) from exc

        if vector.shape != (
            len(
                self.coefficients
            ),
        ):
            raise AdaptivePassportError(
                "Runtime feature vector does not match Ridge coefficients"
            )

        return vector

    def predict_cell(
        self,
        *,
        needs: Mapping[str, Any],
        valences: Mapping[str, Any],
        mode: str,
        need: str,
    ) -> float:

        if mode not in MODES:
            raise AdaptivePassportError(
                f"Unknown HOTCO mode: {mode}"
            )

        if need not in NEEDS:
            raise AdaptivePassportError(
                f"Unknown HOTCO need: {need}"
            )

        normalized_needs = (
            self._validate_needs(
                needs
            )
        )

        normalized_valences = (
            self._validate_valences(
                valences
            )
        )

        vector = self._feature_vector(
            needs=normalized_needs,
            valences=normalized_valences,
            mode=mode,
            need=need,
        )

        prediction = (
            self.intercept
            + float(
                np.dot(
                    vector,
                    self.coefficients,
                )
            )
        )

        return float(
            np.clip(
                prediction,
                -1.0,
                1.0,
            )
        )

    def predict_all(
        self,
        *,
        needs: Mapping[str, Any],
        valences: Mapping[str, Any],
    ) -> Dict[str, float]:

        normalized_needs = (
            self._validate_needs(
                needs
            )
        )

        normalized_valences = (
            self._validate_valences(
                valences
            )
        )

        predictions: Dict[
            str,
            float,
        ] = {}

        for mode in MODES:
            for need in NEEDS:

                cell_id = (
                    f"{mode}__{need}"
                )

                vector = (
                    self._feature_vector(
                        needs=normalized_needs,
                        valences=normalized_valences,
                        mode=mode,
                        need=need,
                    )
                )

                value = (
                    self.intercept
                    + float(
                        np.dot(
                            vector,
                            self.coefficients,
                        )
                    )
                )

                predictions[
                    cell_id
                ] = float(
                    np.clip(
                        value,
                        -1.0,
                        1.0,
                    )
                )

        if len(
            predictions
        ) != 44:
            raise AdaptivePassportError(
                "Adaptive Passport prediction did not produce 44 cells"
            )

        return predictions

    def select_questions(
        self,
        *,
        candidate_cells: Optional[
            Iterable[str]
        ] = None,
        k: Optional[int] = None,
    ) -> list[Dict[str, Any]]:

        question_count = (
            self.k
            if k is None
            else int(k)
        )

        if question_count < 1:
            raise AdaptivePassportError(
                "k must be at least 1"
            )

        if candidate_cells is None:
            candidates = list(
                self.all_cells
            )
        else:
            candidates = list(
                dict.fromkeys(
                    str(value)
                    for value in candidate_cells
                )
            )

        unknown = [
            cell_id
            for cell_id in candidates
            if cell_id
            not in self.cell_metadata
        ]

        if unknown:
            raise AdaptivePassportError(
                "Unknown candidate cells: "
                + ", ".join(
                    sorted(
                        unknown
                    )
                )
            )

        if len(
            candidates
        ) < question_count:
            raise AdaptivePassportError(
                "Not enough candidate cells "
                f"for k={question_count}"
            )

        remaining = list(
            candidates
        )

        selected: list[
            Dict[str, Any]
        ] = []

        seen_actions: set[
            str
        ] = set()

        seen_needs: set[
            str
        ] = set()

        for position in range(
            question_count
        ):

            scored = []

            for cell_id in remaining:

                metadata = (
                    self.cell_metadata[
                        cell_id
                    ]
                )

                utility = float(
                    metadata[
                        "utility"
                    ]
                )

                score = utility

                if (
                    metadata[
                        "hotco_mode"
                    ]
                    not in seen_actions
                ):
                    score += (
                        self.action_bonus
                    )

                if (
                    metadata[
                        "model_need"
                    ]
                    not in seen_needs
                ):
                    score += (
                        self.need_bonus
                    )

                scored.append(
                    (
                        -score,
                        -utility,
                        cell_id,
                    )
                )

            scored.sort()

            best_cell = (
                scored[
                    0
                ][
                    2
                ]
            )

            best_metadata = (
                self.cell_metadata[
                    best_cell
                ]
            )

            selected.append(
                {
                    "position":
                        position + 1,

                    "cell_id":
                        best_cell,

                    "hotco_mode":
                        best_metadata[
                            "hotco_mode"
                        ],

                    "model_need":
                        best_metadata[
                            "model_need"
                        ],

                    "utility":
                        float(
                            best_metadata[
                                "utility"
                            ]
                        ),

                    "response_scale": {
                        "minimum":
                            1,

                        "maximum":
                            7,

                        "integer_only":
                            True,

                        "normalization":
                            "(raw_rating - 4) / 3",
                    },
                }
            )

            seen_actions.add(
                best_metadata[
                    "hotco_mode"
                ]
            )

            seen_needs.add(
                best_metadata[
                    "model_need"
                ]
            )

            remaining.remove(
                best_cell
            )

        return selected

    def personalize(
        self,
        *,
        needs: Mapping[str, Any],
        valences: Mapping[str, Any],
        observed_answers: Mapping[
            str,
            Any,
        ],
        answers_are_raw_1_to_7: bool = False,
        require_complete_k: bool = True,
    ) -> Dict[str, Any]:

        estimates = self.predict_all(
            needs=needs,
            valences=valences,
        )

        if not isinstance(
            observed_answers,
            Mapping,
        ):
            raise AdaptivePassportError(
                "observed_answers must be an object"
            )

        if require_complete_k and (
            len(
                observed_answers
            )
            != self.k
        ):
            raise AdaptivePassportError(
                f"Adaptive Passport v1 requires exactly "
                f"{self.k} observed answers"
            )

        if len(
            observed_answers
        ) == 0:
            raise AdaptivePassportError(
                "At least one observed answer is required"
            )

        observed: Dict[
            str,
            float,
        ] = {}

        for cell_id, value in (
            observed_answers.items()
        ):

            cell_id = str(
                cell_id
            )

            if cell_id not in estimates:
                raise AdaptivePassportError(
                    f"Unknown observed belief cell: {cell_id}"
                )

            if answers_are_raw_1_to_7:

                normalized = (
                    raw_belief_rating_to_normalized(
                        value
                    )
                )

            else:

                normalized = (
                    _finite_number(
                        value,
                        path=(
                            "observed_answers."
                            + cell_id
                        ),
                        minimum=-1.0,
                        maximum=1.0,
                    )
                )

            observed[
                cell_id
            ] = normalized

        residuals = np.asarray(
            [
                observed[
                    cell_id
                ]
                - estimates[
                    cell_id
                ]
                for cell_id in observed
            ],
            dtype=np.float64,
        )

        n_observed = len(
            residuals
        )

        shrinkage_weight = (
            n_observed
            / (
                n_observed
                + self.global_lambda
            )
        )

        personal_offset = (
            shrinkage_weight
            * float(
                residuals.mean()
            )
        )

        personalized: Dict[
            str,
            float,
        ] = {}

        provenance: Dict[
            str,
            Dict[str, Any],
        ] = {}

        for cell_id, estimate in (
            estimates.items()
        ):

            if cell_id in observed:

                personalized[
                    cell_id
                ] = observed[
                    cell_id
                ]

                provenance[
                    cell_id
                ] = {
                    "source":
                        "observed",

                    "estimator":
                        None,

                    "direct_user_response":
                        True,
                }

            else:

                value = float(
                    np.clip(
                        estimate
                        + personal_offset,
                        -1.0,
                        1.0,
                    )
                )

                personalized[
                    cell_id
                ] = value

                provenance[
                    cell_id
                ] = {
                    "source":
                        "estimated",

                    "estimator":
                        "adaptive_cognitive_passport_v1",

                    "direct_user_response":
                        False,
                }

        nested_beliefs = {
            mode: {
                need:
                    personalized[
                        f"{mode}__{need}"
                    ]
                for need in NEEDS
            }
            for mode in MODES
        }

        nested_provenance = {
            mode: {
                need:
                    provenance[
                        f"{mode}__{need}"
                    ]
                for need in NEEDS
            }
            for mode in MODES
        }

        return {
            "schema_version":
                RUNTIME_VERSION,

            "model_version":
                "v1",

            "artifact": {
                "path":
                    self.artifact_path.name,

                "sha256":
                    self.artifact_sha256,
            },

            "beliefs":
                nested_beliefs,

            "belief_provenance":
                nested_provenance,

            "personalization": {
                "observed_answer_count":
                    n_observed,

                "expected_answer_count":
                    self.k,

                "global_shrinkage_lambda":
                    self.global_lambda,

                "shrinkage_weight":
                    float(
                        shrinkage_weight
                    ),

                "mean_residual":
                    float(
                        residuals.mean()
                    ),

                "personal_offset":
                    float(
                        personal_offset
                    ),

                "selected_answers_override_estimates":
                    True,
            },

            "policy": {
                "automatic_behavioral_profile_updates":
                    False,

                "route_choice_is_psychological_measurement":
                    False,

                "context_updates_stable_profile":
                    False,
            },
        }
