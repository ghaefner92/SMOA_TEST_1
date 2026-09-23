#!/usr/bin/env python3
"""Strict DYCONET HTTP service running HOTCO-CT v4.3.

POST /api/dyconet accepts only the versioned questionnaire contract defined in
input_mapping_v4_3.py. Missing model inputs produce a validation error; they
are never filled from population, synthetic, neutral, or behavioral values.

POST /api/dyconet/profile-update accepts one backend-generated micro-question
response with explicit user confirmation. It creates a new Passport revision;
it never mutates or deletes the previous revision.
"""

from __future__ import annotations

import logging
import os
import threading
from typing import Any, Dict, Mapping

from flask import Flask, jsonify, request

from hotco_ct_v4_3 import HOTCOCTv43, MODEL_NAME, MODEL_VERSION
from input_mapping_v4_3 import (
    INPUT_SCHEMA_VERSION,
    InputValidationError,
    parse_participant_input,
)
from passport_xai import PASSPORT_SCHEMA_VERSION, build_cognitive_passport
from profile_update import PROFILE_UPDATE_SCHEMA_VERSION, prepare_profile_update
from adaptive_passport_v1 import AdaptivePassportError
from adaptive_passport_onboarding import (
    ONBOARDING_SCHEMA_VERSION,
    start_adaptive_passport,
    complete_adaptive_passport,
)
from adaptive_profile_update import (
    ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,
    AdaptiveProfileUpdateError,
    prepare_adaptive_profile_update,
)
from adaptive_passport_xai import (
    build_adaptive_cognitive_passport,
)
from adaptive_hotco_bootstrap import (
    ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION,
    AdaptiveHotcoBootstrapError,
    prepare_adaptive_hotco_bootstrap,
)
from contextual_deliberation import (
    ContextualDeliberationError,
    ContextualProviderConfigurationError,
    run_contextual_deliberation,
)
from contextual_xai import ContextualXAIError, build_contextual_xai
from contextual_xai_narrator import (
    NarrationError,
    digital_companion_contract_status,
    narrate_minimized_evidence,
)
from orion_context import DEFAULT_BASE_URL, PUBLIC_TIMEOUT_SECONDS
from context_models import active_context_model, DEFAULT_RHO, EXPERIMENTAL_UNCALIBRATED


logging.basicConfig(level=os.environ.get("DYCONET_LOG_LEVEL", "INFO"))
logger = logging.getLogger("dyconet_api")

app = Flask(__name__)
engine = HOTCOCTv43()
model_lock = threading.Lock()


def _run_one(payload: Any) -> Dict[str, Any]:
    participant = parse_participant_input(payload)
    with model_lock:
        result = engine.simulate(participant)
    return build_cognitive_passport(participant, result, engine)


def run_dyconet(payload: Any) -> Dict[str, Any]:
    """Run one request, a raw list, or a versioned {requests:[...]} batch."""

    if isinstance(payload, list):
        if not payload:
            raise InputValidationError(["batch list must contain at least one request"])
        return {"passports": [_run_one(item)["cognitive_passport"] for item in payload]}

    if isinstance(payload, Mapping) and "requests" in payload:
        requests_value = payload.get("requests")
        if not isinstance(requests_value, list) or not requests_value:
            raise InputValidationError(["requests must be a non-empty array"])
        return {
            "passports": [
                _run_one(item)["cognitive_passport"] for item in requests_value
            ]
        }

    return _run_one(payload)


def run_profile_update(payload: Any) -> Dict[str, Any]:
    """Create a fresh Passport revision from one explicit re-measurement."""

    participant, lineage = prepare_profile_update(payload)
    with model_lock:
        result = engine.simulate(participant)
    return build_cognitive_passport(
        participant,
        result,
        engine,
        lineage=lineage,
    )


def run_adaptive_hotco_bootstrap(
    payload: Any,
) -> Dict[str, Any]:
    """Run initial HOTCO-CT from a completed Adaptive Passport."""

    adaptive_input = (
        prepare_adaptive_hotco_bootstrap(
            payload
        )
    )

    with model_lock:
        result = engine.simulate(
            adaptive_input.participant
        )

    return build_adaptive_cognitive_passport(
        adaptive_input,
        result,
        engine,
    )


def run_adaptive_profile_update(
    payload: Any,
) -> Dict[str, Any]:
    """Create one explicit longitudinal Adaptive Passport revision."""

    adaptive_input, lineage = (
        prepare_adaptive_profile_update(
            payload
        )
    )

    with model_lock:
        result = engine.simulate(
            adaptive_input.participant
        )

    return build_adaptive_cognitive_passport(
        adaptive_input,
        result,
        engine,
        lineage=lineage,
    )


@app.get("/health")
def health() -> Any:
    return jsonify(
        {
            "status": "ok",
            "service": "dyconet",
            "model": MODEL_NAME,
            "model_version": MODEL_VERSION,
            "input_schema_version": INPUT_SCHEMA_VERSION,
            "passport_schema_version": PASSPORT_SCHEMA_VERSION,
            "profile_update_schema_version": PROFILE_UPDATE_SCHEMA_VERSION,
            "adaptive_questioning": True,
            "longitudinal_passport_lineage": True,
            "input_policy": "current_user_responses_only",
            "imputation": False,
            "adaptive_passport": {
                "status": "experimental",
                "schema_version": ONBOARDING_SCHEMA_VERSION,
                "model_version": "v1",
                "initial_question_count": 4,
                "initial_question_policy": "fixed_train_derived_four_item_calibration",
                "estimated_beliefs": True,
                "explicit_belief_provenance": True,
                "strict_dyconet_endpoint_unchanged": True,
                "profile_update_schema_version": ADAPTIVE_PROFILE_UPDATE_SCHEMA_VERSION,
                "bootstrap_schema_version": ADAPTIVE_HOTCO_BOOTSTRAP_SCHEMA_VERSION,
                "initial_hotco_bootstrap": True,
                "longitudinal_estimated_to_observed_updates": True,
            },
            "runtime": "numpy/cpu",
            "digital_companion": digital_companion_contract_status(),
            "orion": {
                "mode": "public",
                "base_url": DEFAULT_BASE_URL,
                "auth_required": False,
                "timeout_seconds": PUBLIC_TIMEOUT_SECONDS,
                "context_contract_version": "route-context-v1",
            },
            "context_model": {"active_context_model": active_context_model(), "context_model_status": EXPERIMENTAL_UNCALIBRATED, "rho": DEFAULT_RHO},
        }
    )


@app.post("/api/dyconet")
def dyconet_route() -> Any:
    try:
        payload = request.get_json(force=True)
        return jsonify(run_dyconet(payload))
    except InputValidationError as exc:
        return (
            jsonify(
                {
                    "error": "invalid_questionnaire_input",
                    "message": "DYCONET requires explicit current-user responses for every model input.",
                    "issues": exc.issues,
                    "imputation_performed": False,
                }
            ),
            422,
        )
    except Exception as exc:  # pragma: no cover - operational safety net
        logger.exception("HOTCO-CT inference failed")
        return (
            jsonify(
                {
                    "error": "dyconet_inference_failed",
                    "detail": str(exc),
                    "imputation_performed": False,
                }
            ),
            500,
        )


@app.post("/api/dyconet/adaptive-passport/start")
def adaptive_passport_start_route() -> Any:
    """Start experimental four-question Adaptive Cognitive Passport v1 onboarding."""

    try:
        payload = request.get_json(force=True)
        return jsonify(
            start_adaptive_passport(
                payload
            )
        )

    except AdaptivePassportError as exc:
        return (
            jsonify(
                {
                    "error":
                        "invalid_adaptive_passport_start",

                    "message":
                        str(exc),

                    "hotco_run":
                        False,

                    "automatic_profile_update":
                        False,
                }
            ),
            422,
        )

    except Exception as exc:  # pragma: no cover - operational safety net
        logger.exception(
            "Adaptive Passport start failed"
        )

        return (
            jsonify(
                {
                    "error":
                        "adaptive_passport_start_failed",

                    "detail":
                        str(exc),

                    "hotco_run":
                        False,

                    "automatic_profile_update":
                        False,
                }
            ),
            500,
        )


@app.post("/api/dyconet/adaptive-passport/complete")
def adaptive_passport_complete_route() -> Any:
    """Complete v1 onboarding and reconstruct the dense 4 x 11 belief profile."""

    try:
        payload = request.get_json(force=True)
        return jsonify(
            complete_adaptive_passport(
                payload
            )
        )

    except AdaptivePassportError as exc:
        return (
            jsonify(
                {
                    "error":
                        "invalid_adaptive_passport_completion",

                    "message":
                        str(exc),

                    "hotco_run":
                        False,

                    "automatic_profile_update":
                        False,
                }
            ),
            422,
        )

    except Exception as exc:  # pragma: no cover - operational safety net
        logger.exception(
            "Adaptive Passport completion failed"
        )

        return (
            jsonify(
                {
                    "error":
                        "adaptive_passport_completion_failed",

                    "detail":
                        str(exc),

                    "hotco_run":
                        False,

                    "automatic_profile_update":
                        False,
                }
            ),
            500,
        )


@app.post("/api/dyconet/adaptive-passport/bootstrap")
def adaptive_passport_bootstrap_route() -> Any:
    """Create Adaptive Cognitive Passport revision 1 and run HOTCO-CT."""

    try:
        payload = request.get_json(
            force=True
        )

        return jsonify(
            run_adaptive_hotco_bootstrap(
                payload
            )
        )

    except AdaptiveHotcoBootstrapError as exc:
        return (
            jsonify(
                {
                    "error":
                        "invalid_adaptive_hotco_bootstrap",

                    "message":
                        str(exc),

                    "automatic_profile_update":
                        False,

                    "availability_inferred":
                        False,

                    "hotco_parameters_changed":
                        False,
                }
            ),
            422,
        )

    except Exception as exc:  # pragma: no cover
        logger.exception(
            "Adaptive HOTCO bootstrap failed"
        )

        return (
            jsonify(
                {
                    "error":
                        "adaptive_hotco_bootstrap_failed",

                    "detail":
                        str(exc),

                    "automatic_profile_update":
                        False,

                    "availability_inferred":
                        False,

                    "hotco_parameters_changed":
                        False,
                }
            ),
            500,
        )


@app.post("/api/dyconet/adaptive-passport/profile-update")
def adaptive_passport_profile_update_route() -> Any:
    """Apply one explicitly confirmed longitudinal Adaptive Passport update."""

    try:
        payload = request.get_json(
            force=True
        )

        return jsonify(
            run_adaptive_profile_update(
                payload
            )
        )

    except AdaptiveProfileUpdateError as exc:
        return (
            jsonify(
                {
                    "error":
                        "invalid_adaptive_profile_update",

                    "message":
                        str(exc),

                    "automatic_update_performed":
                        False,

                    "explicit_confirmation_required":
                        True,

                    "hotco_parameters_changed":
                        False,
                }
            ),
            422,
        )

    except Exception as exc:  # pragma: no cover
        logger.exception(
            "Adaptive Passport profile update failed"
        )

        return (
            jsonify(
                {
                    "error":
                        "adaptive_profile_update_failed",

                    "detail":
                        str(exc),

                    "automatic_update_performed":
                        False,

                    "hotco_parameters_changed":
                        False,
                }
            ),
            500,
        )


@app.post("/api/dyconet/profile-update")
def profile_update_route() -> Any:
    try:
        payload = request.get_json(force=True)
        return jsonify(run_profile_update(payload))
    except InputValidationError as exc:
        return (
            jsonify(
                {
                    "error": "invalid_profile_update",
                    "message": (
                        "Profile updates require the active backend-generated "
                        "micro-question and one explicitly confirmed user response."
                    ),
                    "issues": exc.issues,
                    "automatic_update_performed": False,
                    "imputation_performed": False,
                }
            ),
            422,
        )
    except Exception as exc:  # pragma: no cover - operational safety net
        logger.exception("HOTCO-CT profile update failed")
        return (
            jsonify(
                {
                    "error": "dyconet_profile_update_failed",
                    "detail": str(exc),
                    "automatic_update_performed": False,
                    "imputation_performed": False,
                }
            ),
            500,
        )


@app.post("/api/dyconet/contextual-deliberation")
def contextual_deliberation_route() -> Any:
    """Run one baseline and one route-conditioned simulation per candidate."""

    try:
        return jsonify(run_contextual_deliberation(request.get_json(force=True)))
    except ContextualProviderConfigurationError as exc:
        return jsonify({"error": "context_provider_configuration", "message": str(exc)}), 503
    except (ContextualDeliberationError, InputValidationError, TypeError, ValueError, KeyError) as exc:
        return jsonify({"error": "invalid_contextual_deliberation_request", "message": str(exc)}), 422


@app.post("/api/dyconet/contextual-explanation")
def contextual_explanation_route() -> Any:
    """Explain an existing contextual result without rerunning HOTCO."""

    try:
        payload = request.get_json(force=True) or {}
        deliberation = payload.get("deliberation", payload)
        route_id = str(payload.get("route_id", "")).strip()
        if not route_id:
            raise ContextualXAIError("route_id is required")
        return jsonify(build_contextual_xai(deliberation, route_id, payload.get("alternative_mode")))
    except ContextualXAIError as exc:
        return jsonify({"error": "invalid_contextual_explanation_request", "message": str(exc)}), 422


@app.post("/api/dyconet/contextual-explanation/narrate")
def contextual_explanation_narrate_route() -> Any:
    """Optionally narrate minimized XAI evidence; deterministic fallback is returned on provider failure."""

    try:
        payload = request.get_json(force=True) or {}
        deliberation = payload.get("deliberation")
        if isinstance(deliberation, Mapping):
            route_id = str(payload.get("route_id", "")).strip()
            if not route_id:
                raise ContextualXAIError("route_id is required")
            deterministic = build_contextual_xai(deliberation, route_id, payload.get("alternative_mode"))
            # Evidence identity is reconstructed and owned entirely by the
            # backend. Android only transports the deterministic deliberation;
            # Gson therefore cannot mutate the canonical evidence document.
            payload = {
                "route_id": route_id,
                "language": payload.get("language", "en"),
                "explanation_type": payload.get("explanation_type", "WHY_THIS_TENDENCY"),
                "evidence_version": deterministic["evidence_version"],
                "minimized_evidence": deterministic["minimized_evidence"],
            }
        return jsonify(narrate_minimized_evidence(payload))
    except (NarrationError, ContextualXAIError) as exc:
        payload = request.get_json(silent=True) or {}
        evidence_prefix = str(payload.get("evidence_version", ""))[:18]
        error_code = exc.code if isinstance(exc, NarrationError) else "NARRATION_VALIDATION_FAILED"
        logger.warning(
            "%s route_id=%s evidence=%s",
            error_code,
            str(payload.get("route_id", ""))[:120],
            evidence_prefix,
        )
        return jsonify({"error": "invalid_contextual_narration_request", "message": str(exc)}), 422


if __name__ == "__main__":
    app.run(
        debug=os.environ.get("FLASK_DEBUG", "0") == "1",
        host="0.0.0.0",
        port=int(os.environ.get("PORT", "5000")),
    )
