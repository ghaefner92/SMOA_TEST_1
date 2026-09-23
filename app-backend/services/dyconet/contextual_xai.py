"""Faithful, deterministic XAI for already-computed contextual deliberation.

This module only projects existing route context, perturbation contributions,
and HOTCO readout/process diagnostics into a minimized evidence document. It
never reruns HOTCO, normalizes context, or infers a user's real choice.
"""

from __future__ import annotations

from typing import Any, Mapping

from digital_companion_contract import evidence_version
from context_facts import build_context_facts
from xai_diagnostics import ambiguity_state
from companion_interpretation import interpretation_metrics, metric_changes, personal_deliberation_profile


XAI_SCHEMA_VERSION = "contextual-xai-v1"
XAI_SCOPE = "simulated_model_tendency"


class ContextualXAIError(ValueError):
    pass


def _number(value: Any) -> float | None:
    try:
        return float(value) if value is not None else None
    except (TypeError, ValueError):
        return None


def _delta(current: Mapping[str, Any], baseline: Mapping[str, Any]) -> dict[str, float]:
    keys = sorted(set(current) | set(baseline))
    return {
        key: float(current.get(key, 0.0) or 0.0) - float(baseline.get(key, 0.0) or 0.0)
        for key in keys
    }


def _driver_facts(perturbation: Mapping[str, Any]) -> list[dict[str, Any]]:
    exposures = perturbation.get("effective_exposures", ())
    exposure_by_key = {
        (item.get("stressor"), item.get("source_scope"), item.get("mode")): item
        for item in exposures if isinstance(item, Mapping)
    }
    facts: list[dict[str, Any]] = []
    for item in perturbation.get("contributions", ()):
        if not isinstance(item, Mapping):
            continue
        key = (item.get("stressor"), item.get("source_scope"), item.get("mode"))
        exposure = exposure_by_key.get(key, {})
        signed = _number(item.get("contribution"))
        if signed is None:
            continue
        facts.append({
            "fact_type": "context_node_contribution",
            "stressor": item.get("stressor"),
            "target": item.get("target_node"),
            "target_family": item.get("target_family"),
            "mode": item.get("mode"),
            "context_value": exposure.get("context_value"),
            "tolerance": exposure.get("tolerance"),
            "effective_exposure": item.get("effective_exposure"),
            "coefficient": item.get("coefficient"),
            "xi": signed,
            "contribution_kind": "pre_bounded_mapping" if perturbation.get("perturbation_version") == "f3-need-only-v1" else "direct_input",
            "combined_target_drive": perturbation.get("need_perturbations", {}).get(item.get("target_node")),
            "source_scope": item.get("source_scope"),
            "provenance": {
                "normalization": item.get("source_normalization_metadata", {}),
                "tolerance_source": item.get("tolerance_source"),
                "warnings": item.get("warnings", []),
            },
            "version": perturbation.get("perturbation_version"),
        })
    return sorted(facts, key=lambda item: (-abs(item["xi"]), item["stressor"] or "", item["target"] or "", item.get("mode") or ""))


def _structural(diag: Mapping[str, Any], mode: str | None) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
    by_mode = diag.get("mixed_cognitive_support", {}).get("by_mode", {}) if isinstance(diag, Mapping) else {}
    selected = by_mode.get(mode, {}) if isinstance(by_mode, Mapping) else {}
    supporters = selected.get("top_supporters_terminal", []) if isinstance(selected, Mapping) else []
    inhibitors = selected.get("top_inhibitors_terminal", []) if isinstance(selected, Mapping) else []
    return list(supporters or []), list(inhibitors or [])


def _context_status(route_context: Mapping[str, Any], perturbation: Mapping[str, Any]) -> str:
    missing = perturbation.get("missing_stressors", [])
    warnings = perturbation.get("warnings", [])
    exposures = perturbation.get("effective_exposures", [])
    known = any(item.get("effective_value") is not None for item in exposures if isinstance(item, Mapping))
    if not known:
        return "UNAVAILABLE"
    return "PARTIAL" if missing or warnings else "FULL"


def _context_facts(route_context: Mapping[str, Any]) -> list[dict[str, Any]]:
    """Project only contract-safe observations for the optional companion.

    Source eligibility is owned by the upstream context contract; this
    presentation helper never normalizes data or changes eligibility.
    """
    supplied = route_context.get("context_facts")
    if isinstance(supplied, list) and all(isinstance(item, Mapping) for item in supplied):
        return [dict(item) for item in supplied]
    return build_context_facts(route_context)


def build_contextual_xai(deliberation: Mapping[str, Any], route_id: str, alternative_mode: str | None = None) -> dict[str, Any]:
    """Build explanation evidence from a serialized Phase 5 response."""
    if not isinstance(deliberation, Mapping):
        raise ContextualXAIError("deliberation must be a JSON object")
    baseline = deliberation.get("baseline")
    results = deliberation.get("candidate_results", ())
    candidate = next((item for item in results if isinstance(item, Mapping) and item.get("route_id") == route_id), None)
    if not isinstance(baseline, Mapping) or not isinstance(candidate, Mapping):
        raise ContextualXAIError("route_id does not identify a result in the deliberation")
    contextual = candidate.get("hotco", {})
    perturbation = candidate.get("context_perturbation", {})
    route_context = candidate.get("route_context", {})
    if not isinstance(contextual, Mapping) or not isinstance(perturbation, Mapping):
        raise ContextualXAIError("deliberation result is missing contextual evidence")

    baseline_winner = str(baseline.get("winner", ""))
    contextual_winner = str(contextual.get("winner", ""))
    contextual_ambiguity = str(contextual.get("ambiguity_state") or ambiguity_state(contextual.get("process_diagnostics", {}).get("competition", {})))
    facts = _driver_facts(perturbation)
    positive = [item for item in facts if item["xi"] > 0]
    negative = [item for item in facts if item["xi"] < 0]
    target_mode = alternative_mode or contextual_winner
    supporters, inhibitors = _structural(contextual.get("process_diagnostics", {}), target_mode)
    alternative = None
    if alternative_mode:
        alt = next((item for item in results if isinstance(item, Mapping) and item.get("hotco", {}).get("winner") == alternative_mode), None)
        if isinstance(alt, Mapping):
            alt_hotco = alt.get("hotco", {})
            alternative = {
                "mode": alternative_mode,
                "action_activation_difference": _delta(contextual.get("final_action_activations", {}), alt_hotco.get("final_action_activations", {})),
                "probability_difference": _delta(contextual.get("probabilities", {}), alt_hotco.get("probabilities", {})),
                "opposing_contextual_drivers": [item for item in negative if item.get("mode") == alternative_mode or item.get("target") == f"valence_{alternative_mode}"],
            }

    process = contextual.get("process_diagnostics", {})
    competition = process.get("competition", {}) if isinstance(process, Mapping) else {}
    baseline_nodes = baseline.get("node_activations", {})
    contextual_nodes = contextual.get("node_activations", {})
    need_changes = _delta(
        contextual_nodes.get("needs", {}).get("final", {}),
        baseline_nodes.get("needs", {}).get("final", {}),
    )
    valence_changes = _delta(
        contextual_nodes.get("valences", {}).get("final", {}),
        baseline_nodes.get("valences", {}).get("final", {}),
    )
    data_quality = {
        "context_status": _context_status(route_context, perturbation),
        "stressor_coverage": perturbation.get("data_coverage", {}),
        "missing_stressors": perturbation.get("missing_stressors", []),
        "missing_tolerances": sorted({item.get("stressor") for item in perturbation.get("effective_exposures", []) if isinstance(item, Mapping) and item.get("tolerance") is None}),
        "ignored_context_variables": perturbation.get("ignored_context_variables", []),
        "warnings": list(dict.fromkeys([*(route_context.get("source_metadata", {}).get("warnings", []) if isinstance(route_context.get("source_metadata", {}), Mapping) else []), *perturbation.get("warnings", []), *candidate.get("warnings", [])])),
        "spatial_matching": {
            "completeness": route_context.get("contextual_data_completeness", {}),
            "metadata": route_context.get("source_metadata", {}).get("spatial_matching") if isinstance(route_context.get("source_metadata", {}), Mapping) else None,
            "radius_meters": route_context.get("source_metadata", {}).get("search_radius_meters") if isinstance(route_context.get("source_metadata", {}), Mapping) else None,
            "coordinate_limitations": route_context.get("source_metadata", {}).get("leg_coordinate_limitation") if isinstance(route_context.get("source_metadata", {}), Mapping) else None,
            "geometry_provenance": route_context.get("source_metadata", {}).get("geometry_provenance") if isinstance(route_context.get("source_metadata", {}), Mapping) else None,
            "context_spatial_fidelity": route_context.get("source_metadata", {}).get("context_spatial_fidelity") if isinstance(route_context.get("source_metadata", {}), Mapping) else "UNKNOWN",
            "geometry_sample_count": route_context.get("source_metadata", {}).get("geometry_sample_count") if isinstance(route_context.get("source_metadata", {}), Mapping) else 0,
        },
    }
    summary: list[str] = []
    if positive:
        if positive[0]["contribution_kind"] == "pre_bounded_mapping":
            summary.append(f"{positive[0]['stressor']} contributes to the combined contextual input for {positive[0]['target']}.")
        else:
            summary.append(f"{positive[0]['stressor']} produced a positive contextual contribution to {positive[0]['target']} ({positive[0]['xi']:.6g}).")
    if negative:
        if negative[0]["contribution_kind"] == "pre_bounded_mapping":
            summary.append(f"{negative[0]['stressor']} contributes negatively to the combined contextual input for {negative[0]['target']}.")
        else:
            summary.append(f"{negative[0]['stressor']} produced a negative contextual contribution to {negative[0]['target']} ({negative[0]['xi']:.6g}).")
    if contextual_ambiguity in {"NEAR_TIE", "UNRESOLVED"}:
        summary.append("The exploratory analysis shows similar support for the leading alternatives.")
    elif baseline_winner != contextual_winner and baseline_winner and contextual_winner:
        summary.append(f"The simulated model tendency changed from {baseline_winner} to {contextual_winner} compared with the no-context simulation.")

    baseline_metrics = interpretation_metrics(baseline)
    contextual_metrics = interpretation_metrics(contextual)
    selected_route_modes = [
        segment.get("mode") for segment in route_context.get("segments", [])
        if isinstance(segment, Mapping) and segment.get("mode")
    ]
    companion_profile = personal_deliberation_profile(
        baseline_tendency=baseline_winner,
        contextual_tendency=contextual_winner,
        ambiguity_state=contextual_ambiguity,
        leader_changed=baseline_winner != contextual_winner,
        selected_route_modes=selected_route_modes,
        contextual_metrics=contextual_metrics,
        active_need_values=contextual_nodes.get("needs", {}).get("final", {}),
        supporting_constraints=supporters,
        opposing_constraints=inhibitors,
    )
    minimized_evidence = {
        "xai_schema_version": XAI_SCHEMA_VERSION,
        "route_id": route_id,
        "baseline_tendency": baseline_winner,
        "contextual_tendency": contextual_winner,
        "leader_changed": baseline_winner != contextual_winner,
        "ambiguity_state": contextual_ambiguity,
        "context_model": contextual.get("context_metadata", {}).get("context_model", "UNKNOWN"),
        "rho": contextual.get("context_metadata", {}).get("rho"),
        "deliberation_metrics": {
            "baseline": baseline_metrics,
            "contextual": contextual_metrics,
            "changes": metric_changes(baseline_metrics, contextual_metrics),
        },
        "personal_deliberation": companion_profile,
        "context_spatial_fidelity": data_quality["spatial_matching"]["context_spatial_fidelity"],
        "baseline_probabilities": dict(baseline.get("probabilities", {})),
        "contextual_probabilities": dict(contextual.get("probabilities", {})),
        "baseline_action_activations": dict(baseline.get("final_action_activations", {})),
        "contextual_action_activations": dict(contextual.get("final_action_activations", {})),
        "action_activation_change": _delta(contextual.get("final_action_activations", {}), baseline.get("final_action_activations", {})),
        "probability_change": _delta(contextual.get("probabilities", {}), baseline.get("probabilities", {})),
        "top_positive_context_drivers": positive[:5],
        "top_negative_context_drivers": negative[:5],
        "need_state": contextual_nodes.get("needs", {}),
        "baseline_need_state": baseline_nodes.get("needs", {}),
        "need_state_change": need_changes,
        "valence_state": contextual_nodes.get("valences", {}),
        "baseline_valence_state": baseline_nodes.get("valences", {}),
        "valence_state_change": valence_changes,
        "supporting_constraints": supporters,
        "opposing_constraints": inhibitors,
        "uncertainty": {"context_status": data_quality["context_status"], "minimum_top2_gap": competition.get("minimum_top2_gap"), "co_dominance_fraction": competition.get("co_dominance_fraction"), "winner_lead_fraction": competition.get("winner_lead_fraction")},
        "data_quality": data_quality,
        "warnings": data_quality["warnings"],
        "context_facts": _context_facts(route_context),
        "versions": {"model": deliberation.get("model_version"), "normalization": deliberation.get("normalization_version"), "perturbation": deliberation.get("perturbation_version")},
    }
    evidence_hash = evidence_version(minimized_evidence)
    return {
        "schema_version": XAI_SCHEMA_VERSION,
        "scope": XAI_SCOPE,
        "route_id": route_id,
        "baseline_tendency": baseline_winner,
        "contextual_tendency": contextual_winner,
        "leader_changed": baseline_winner != contextual_winner,
        "ambiguity_state": contextual_ambiguity,
        "baseline_probabilities": dict(baseline.get("probabilities", {})),
        "contextual_probabilities": dict(contextual.get("probabilities", {})),
        "baseline_action_activations": dict(baseline.get("final_action_activations", {})),
        "contextual_action_activations": dict(contextual.get("final_action_activations", {})),
        "action_activation_change": _delta(contextual.get("final_action_activations", {}), baseline.get("final_action_activations", {})),
        "probability_change": _delta(contextual.get("probabilities", {}), baseline.get("probabilities", {})),
        "contextual_drivers": {"all": facts, "top_positive": positive[:5], "top_negative": negative[:5]},
        "need_drivers": [item for item in facts if item.get("target_family") == "need"],
        "valence_drivers": [item for item in facts if item.get("target_family") == "valence"],
        "need_state": contextual_nodes.get("needs", {}),
        "baseline_need_state": baseline_nodes.get("needs", {}),
        "need_state_change": need_changes,
        "valence_state": contextual_nodes.get("valences", {}),
        "baseline_valence_state": baseline_nodes.get("valences", {}),
        "valence_state_change": valence_changes,
        "supporting_constraints": supporters,
        "opposing_constraints": inhibitors,
        "trajectory": {"competition": competition, "process_diagnostics": process},
        "uncertainty": {
            "context_status": data_quality["context_status"],
            "minimum_top2_gap": competition.get("minimum_top2_gap", competition.get("final_margin")),
            "co_dominance_fraction": competition.get("co_dominance_fraction"),
            "winner_lead_fraction": competition.get("winner_lead_fraction"),
            "entropy": competition.get("terminal_entropy"),
            "warnings": data_quality["warnings"],
        },
        "data_quality": data_quality,
        "counterfactual_readiness": {"counterfactual_ready": True, "requires_new_hotco_simulation": True, "computed": False},
        "facts": facts,
        "summary": summary,
        "why_this_mode": {"mode": target_mode, "supporting_constraints": supporters, "contextual_drivers": positive, "opposing_constraints": inhibitors, "uncertainty": data_quality["warnings"]},
        "why_not_mode": alternative,
        "versions": {"xai": XAI_SCHEMA_VERSION, "model": deliberation.get("model_version"), "normalization": deliberation.get("normalization_version"), "perturbation": deliberation.get("perturbation_version")},
        "warnings": data_quality["warnings"],
        "minimized_evidence": minimized_evidence,
        "evidence_version": evidence_hash,
    }
