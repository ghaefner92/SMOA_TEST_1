"""Cognitive Passport enrichment with deterministic HOTCO-CT XAI diagnostics.

The base Cognitive Passport contract remains schema 2.0 for Android/routing
compatibility. XAI and adaptive questioning are additive, independently
versioned extensions derived only from the already-computed HOTCO-CT trajectory.

Passport lineage is append-only by design: an update creates a new passport ID
and revision while retaining a parent pointer and the accumulated explicit
measurement history.
"""

from __future__ import annotations

from typing import Any, Dict, Mapping, Optional
from uuid import uuid4

from hotco_ct_v4_3 import HOTCOCTv43, SimulationResult
from input_mapping_v4_3 import ParticipantInput
from passport_v2 import build_cognitive_passport as _build_base_passport
from question_trigger_engine import build_micro_question
from xai_diagnostics import build_xai_diagnostics


# Keep the long-standing Passport contract stable. Nested extensions carry
# their own schema_version so they can evolve without breaking mobile/routing
# Passport 2.0 consumers.
PASSPORT_SCHEMA_VERSION = "2.0"


def _initial_lineage() -> Dict[str, Any]:
    return {
        "passport_id": str(uuid4()),
        "revision": 1,
        "parent_passport_id": None,
        "origin": "initial_full_calibration",
        "measurement_history": [],
        "last_update_event": None,
    }


def build_cognitive_passport(
    participant: ParticipantInput,
    result: SimulationResult,
    engine: HOTCOCTv43,
    *,
    lineage: Optional[Mapping[str, Any]] = None,
) -> Dict[str, Any]:
    output = _build_base_passport(participant, result, engine)
    passport = output["cognitive_passport"]
    passport["schema_version"] = PASSPORT_SCHEMA_VERSION

    lineage_data = dict(lineage) if lineage is not None else _initial_lineage()
    lineage_data.setdefault("passport_id", str(uuid4()))
    lineage_data.setdefault("revision", 1)
    lineage_data.setdefault("parent_passport_id", None)
    lineage_data.setdefault("origin", "initial_full_calibration")
    lineage_data.setdefault("measurement_history", [])
    lineage_data.setdefault("last_update_event", None)
    passport["lineage"] = lineage_data

    passport["xai_diagnostics"] = build_xai_diagnostics(
        participant=participant,
        result=result,
        engine=engine,
    )

    # Keep the long-standing action-process alias synchronized with the richer
    # leadership diagnostic so downstream Android code sees one switch count.
    passport["process_diagnostics"]["action_process"]["winner_switch_count"] = (
        passport["xai_diagnostics"]["leadership"]["winner_switch_count"]
    )

    passport["adaptive_questioning"] = build_micro_question(
        participant=participant,
        xai=passport["xai_diagnostics"],
        passport_id=str(lineage_data["passport_id"]),
        revision=int(lineage_data["revision"]),
        measurement_history=lineage_data.get("measurement_history", []),
    )
    return output
