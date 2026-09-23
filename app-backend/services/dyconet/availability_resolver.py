"""Availability resolution boundary for HOTCO-CT.

Phase 1 deliberately uses only explicit access declarations from the current
user.  Nothing is inferred from mode-use frequency, preference, HOTCO readout,
routing, weather, or population data.

The small provider protocol is an extension seam for later versions in which a
routing/API layer can report access to additional services.  No external
provider is consulted in Phase 1, so the current HOTCO-CT dynamics remain fully
deterministic and auditable.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Mapping, Optional, Protocol, Sequence


PHASE1_POLICY = "explicit_user_declared_access_only"
USER_DECLARED_SOURCE = "user_declared_access"


class AvailabilityProvider(Protocol):
    """Future provider boundary; intentionally unused by the Phase 1 parser."""

    name: str

    def resolve(self, modes: Sequence[str]) -> Mapping[str, Optional[bool]]:
        """Return provider evidence per mode, using None when unknown."""
        ...


@dataclass(frozen=True)
class ResolvedAvailability:
    """Binary HOTCO gates plus an audit trail describing where they came from."""

    by_mode: Dict[str, bool]
    vector: list[float]
    provenance: Dict[str, Any]


def resolve_user_declared_availability(
    raw: Mapping[str, bool],
    modes: Sequence[str],
) -> ResolvedAvailability:
    """Resolve Phase 1 availability from complete explicit user declarations.

    Validation of JSON types and missing keys happens in input_mapping_v4_3;
    this function enforces the semantic contract and produces the auditable
    representation consumed by the model/passport.
    """

    missing = [mode for mode in modes if mode not in raw]
    if missing:
        raise ValueError(
            "availability resolver requires explicit declarations for: "
            + ", ".join(missing)
        )

    by_mode = {mode: bool(raw[mode]) for mode in modes}
    if not any(by_mode.values()):
        raise ValueError("availability resolver requires at least one available mode")

    vector = [1.0 if by_mode[mode] else 0.0 for mode in modes]
    provenance: Dict[str, Any] = {
        "phase": "phase1",
        "policy": PHASE1_POLICY,
        "external_provider_data_used": False,
        "frequency_or_preference_inference_used": False,
        "by_mode": {
            mode: {
                "available": by_mode[mode],
                "source": USER_DECLARED_SOURCE,
                "reason": "explicit questionnaire access declaration",
                "hotco_action_gate": 1 if by_mode[mode] else 0,
            }
            for mode in modes
        },
    }
    return ResolvedAvailability(
        by_mode=by_mode,
        vector=vector,
        provenance=provenance,
    )
