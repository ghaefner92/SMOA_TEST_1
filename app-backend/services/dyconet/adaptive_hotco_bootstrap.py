"""Initial HOTCO bootstrap for Adaptive Cognitive Passport v1.

This boundary converts a completed adaptive onboarding profile plus explicit
user-declared modal availability into the auditable AdaptiveHOTCOInput used by
HOTCO-CT.

It does not infer availability and does not fabricate raw belief responses.
"""

from __future__ import annotations

from typing import Any, Mapping, Optional

from adaptive_hotco_adapter import (
    AdaptiveHOTCOInput,
    AdaptiveHotcoAdapterError,
    build_adaptive_hotco_input,
)


ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION = (
    "adaptive_hotco_bootstrap_1.0"
)


class AdaptiveHotcoBootstrapError(
    ValueError
):
    """Raised when the initial adaptive HOTCO bootstrap is invalid."""


def _fail(
    message: str,
) -> None:
    raise AdaptiveHotcoBootstrapError(
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


def prepare_adaptive_hotco_bootstrap(
    payload: Any,
) -> AdaptiveHOTCOInput:
    """Validate one initial Adaptive Passport → HOTCO transition."""

    root = _mapping(
        payload,
        "$",
    )

    allowed = {
        "schema_version",
        "completed_passport",
        "availability",
        "environmental_tolerances",
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
        != ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION
    ):
        _fail(
            "schema_version must be "
            f"'{ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION}'"
        )

    completed = _mapping(
        root.get(
            "completed_passport"
        ),
        "completed_passport",
    )

    availability = _mapping(
        root.get(
            "availability"
        ),
        "availability",
    )

    tolerances: Optional[
        Mapping[str, Any]
    ] = None

    if (
        "environmental_tolerances"
        in root
        and root[
            "environmental_tolerances"
        ]
        is not None
    ):
        tolerances = _mapping(
            root[
                "environmental_tolerances"
            ],
            "environmental_tolerances",
        )

    try:
        return build_adaptive_hotco_input(
            completed,
            availability=
                availability,
            environmental_tolerances=
                tolerances,
        )

    except AdaptiveHotcoAdapterError as exc:
        raise AdaptiveHotcoBootstrapError(
            str(exc)
        ) from exc
