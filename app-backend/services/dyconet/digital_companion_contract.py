"""Shared, deterministic identity rules for Digital Companion evidence.

Provider-generated prose is deliberately excluded: this module identifies the
deterministic route/context/XAI evidence that narration is allowed to explain.
"""

from __future__ import annotations

import hashlib
import json
import math
from typing import Any, Mapping


DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION = "digital-companion-evidence-v2"
DIGITAL_COMPANION_NARRATION_SCHEMA_VERSION = "digital-companion-narration-v2"

_NON_EVIDENCE_KEYS = {
    "companion_display_name",
    "evidence_id",  # Legacy alias, deliberately excluded from identity.
    "evidence_version",
    "generated_by_llm",
    "loading_state",
    "narration",
    "nickname",
    "provider",
    "provider_latency",
    "provider_model",
    "ui_state",
}


def _evidence_only(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {
            str(key): _evidence_only(item)
            for key, item in sorted(value.items(), key=lambda pair: str(pair[0]))
            if str(key) not in _NON_EVIDENCE_KEYS
        }
    if isinstance(value, (list, tuple)):
        # List order is preserved because ranked drivers and other evidence
        # sequences may be semantically meaningful.
        return [_evidence_only(item) for item in value]
    if isinstance(value, (set, frozenset)):
        normalized = [_evidence_only(item) for item in value]
        return sorted(
            normalized,
            key=lambda item: json.dumps(item, sort_keys=True, separators=(",", ":"), ensure_ascii=False),
        )
    # Gson materializes JSON numbers as doubles on Android (for example 0
    # becomes 0.0). Normalize finite numeric values at the identity boundary
    # so equivalent evidence serialized by Python and Android hashes identically.
    if isinstance(value, (int, float)) and not isinstance(value, bool):
        numeric = float(value)
        if not math.isfinite(numeric):
            raise ValueError("evidence numbers must be finite")
        return numeric
    return value


def canonical_evidence_json(evidence: Mapping[str, Any]) -> str:
    """Serialize deterministic evidence reproducibly without UI/provider data."""

    contract = {
        "schema_version": DIGITAL_COMPANION_EVIDENCE_SCHEMA_VERSION,
        "evidence": _evidence_only(evidence),
    }
    return json.dumps(contract, sort_keys=True, separators=(",", ":"), ensure_ascii=False)


def evidence_version(evidence: Mapping[str, Any]) -> str:
    """Return the sole content identity used by backend and Android."""

    encoded = canonical_evidence_json(evidence).encode("utf-8")
    return "sha256:" + hashlib.sha256(encoded).hexdigest()
