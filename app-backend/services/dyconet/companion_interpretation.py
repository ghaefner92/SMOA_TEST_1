"""Bounded explanation projection; never computes or changes HOTCO dynamics."""
from collections.abc import Mapping
import math


PATTERN_MEANINGS = {
    "UNDIFFERENTIATED_TERMINAL_STATE": "The comparison ended without a distinct leading alternative.",
    "PERSISTENT_RIVALRY": "The leading alternatives stayed close during the comparison.",
    "UNSTABLE_COMPETITION": "The leading alternative changed several times during the comparison.",
    "LATE_REVERSAL": "A different alternative took the lead late in the comparison.",
    "EARLY_REVERSAL": "A different alternative took the lead early in the comparison.",
    "REVERSAL": "The leading alternative changed during the comparison.",
    "WINNER_WITH_AFFECTIVE_RESISTANCE": "The model's attraction-related signals pulled against its final leading alternative.",
    "WINNER_WITH_COGNITIVE_RESISTANCE": "The model's need-related signals pulled against its final leading alternative.",
    "HIGH_WINNER_COGNITIVE_AFFECTIVE_FRICTION": "Need-related and attraction-related signals pulled in different directions in the model.",
    "HIGH_WINNER_MIXED_COGNITIVE_SUPPORT": "Some needs supported the leading alternative while other needs pulled against it.",
    "STABLE_DOMINANCE": "The same alternative kept a distinct lead during the evaluated comparison.",
}

NEED_MEANINGS = {
    "pro_env": "reducing environmental impact",
    "physical": "keeping physically active",
    "privacy": "having personal space",
    "autonomy": "having control over the trip",
    "cost": "keeping the trip affordable",
    "speed": "getting there quickly",
    "safety_accident": "feeling safe from traffic accidents",
    "safety_crime": "feeling personally safe",
    "comfort": "travelling comfortably",
    "reliable": "arriving reliably",
    "health_infection": "avoiding infection exposure",
}

MODE_MEANINGS = {
    "bike": "cycling", "bikeshare": "bike sharing", "walk": "walking",
    "pt": "public transport", "pt_bus_tram": "public transport",
    "car": "driving", "car_driver": "driving", "car_passenger": "travelling by car",
    "train": "the train", "escooter": "the e-scooter",
}

MODE_CANONICAL = {
    "bicycle": "bike", "cycling": "bike", "bikeshare": "bike",
    "walking": "walk", "foot": "walk", "pt_bus_tram": "pt", "train": "pt",
    "car_driver": "car", "car_passenger": "car",
}


def _canonical_mode(value):
    text = str(value or "").strip().lower()
    return MODE_CANONICAL.get(text, text)


def _finite_map(value):
    if not isinstance(value, Mapping):
        return {}
    return {
        str(key): float(item) for key, item in value.items()
        if isinstance(item, (int, float)) and not isinstance(item, bool) and math.isfinite(item)
    }


def _need_cards(values, limit=4):
    """Ordinal, human-readable need signals; raw Passport answers stay excluded."""
    ranked = sorted(_finite_map(values).items(), key=lambda pair: (-pair[1], pair[0]))[:limit]
    return [
        {
            "need": need,
            "meaning": NEED_MEANINGS.get(need, need.replace("_", " ")),
            "relative_position": "strongest" if rank == 1 else f"rank_{rank}",
            "signal_direction": "supportive" if value > 0 else "restraining" if value < 0 else "neutral",
        }
        for rank, (need, value) in enumerate(ranked, 1)
    ]


def _constraint_cards(items):
    cards = []
    for item in (items or [])[:3]:
        if not isinstance(item, Mapping) or not item.get("need"):
            continue
        need = str(item["need"])
        cards.append({
            "need": need,
            "meaning": NEED_MEANINGS.get(need, need.replace("_", " ")),
            "direction": "supports" if (item.get("signed_input") or 0) >= 0 else "holds_back",
        })
    return cards


def personal_deliberation_profile(
    *, baseline_tendency, contextual_tendency, ambiguity_state, leader_changed,
    selected_route_modes, contextual_metrics, active_need_values,
    supporting_constraints, opposing_constraints,
):
    """Create the deterministic story a coach may tell without changing HOTCO.

    This is derived from participant-specific HOTCO output, but contains no raw
    questionnaire answers, Passport document, trajectory, coordinates, or new
    behavioural inference.
    """
    ambiguous = ambiguity_state in {"NEAR_TIE", "UNRESOLVED", "UNKNOWN"}
    recommendation_status = "NO_CLEAR_CHOICE" if ambiguous else "RECOMMENDED"
    recommendation_mode = None if ambiguous else _canonical_mode(contextual_tendency) or None
    competition = contextual_metrics.get("competition", {}) if isinstance(contextual_metrics, Mapping) else {}
    rival = _canonical_mode(competition.get("main_rival")) or None
    selected = list(dict.fromkeys(_canonical_mode(mode) for mode in (selected_route_modes or []) if mode))
    selected_alignment = (
        "UNKNOWN" if not selected or not recommendation_mode else
        "ALIGNED" if recommendation_mode in selected else "DIFFERENT_FROM_RECOMMENDATION"
    )
    support_by_mode = contextual_metrics.get("terminal_action_support", {}) if isinstance(contextual_metrics, Mapping) else {}
    ordered_modes = sorted(_finite_map(support_by_mode).items(), key=lambda pair: (-pair[1], pair[0]))
    mode_comparison = [
        {
            "mode": _canonical_mode(mode),
            "meaning": MODE_MEANINGS.get(_canonical_mode(mode), mode.replace("_", " ")),
            "position": rank,
            "role": "recommended" if _canonical_mode(mode) == recommendation_mode else "main_alternative" if _canonical_mode(mode) == rival else "alternative",
        }
        for rank, (mode, _) in enumerate(ordered_modes, 1)
    ]
    supporters = _constraint_cards(supporting_constraints)
    inhibitors = _constraint_cards(opposing_constraints)
    return {
        "source": "personalized_deliberation_output",
        "address_user_as": "you",
        "selected_route_modes": selected,
        "selected_route_alignment": selected_alignment,
        "baseline_leading_mode": _canonical_mode(baseline_tendency) or None,
        "leading_mode_now": _canonical_mode(contextual_tendency) or None,
        "recommendation_status": recommendation_status,
        "recommended_mode": recommendation_mode,
        "recommended_mode_meaning": MODE_MEANINGS.get(recommendation_mode, str(recommendation_mode).replace("_", " ")) if recommendation_mode else None,
        "main_alternative": rival,
        "main_alternative_meaning": MODE_MEANINGS.get(rival, str(rival).replace("_", " ")) if rival else None,
        "leader_changed_with_context": bool(leader_changed),
        "ambiguity_state": ambiguity_state,
        "most_active_needs": _need_cards(active_need_values),
        "needs_supporting_leader": supporters,
        "needs_holding_back_leader": inhibitors,
        "main_tension": {"pulls_toward_leader": supporters[:2], "pulls_away_from_leader": inhibitors[:2]},
        "mode_comparison": mode_comparison,
        "narrative_plan": [
            "give_clear_recommendation" if recommendation_status == "RECOMMENDED" else "acknowledge_no_clear_choice",
            "reflect_personal_needs",
            "explain_main_tension",
            "explain_context_change",
            "state_clarity_and_missing_information",
        ],
    }


def _scalars(data, keys):
    if not isinstance(data, Mapping):
        return {}
    return {key: data[key] for key in keys if key in data and (
        data[key] is None or isinstance(data[key], (str, bool)) or
        isinstance(data[key], (int, float)) and math.isfinite(data[key])
    )}


def _constraints(items):
    return [_scalars(item, ("need", "signed_input")) for item in (items or [])[:3] if isinstance(item, Mapping)]


def interpretation_metrics(summary):
    """Allowlist every available process family, excluding trajectories/profile/location.

    Missing metrics remain absent. No new thresholds or behavioral inference.
    Model times and comparative readout must never be described as seconds or
    probabilities of a real person's choice.
    """
    process = summary.get("process_diagnostics", {})
    competition = _scalars(process.get("competition"), (
        "winner", "main_rival", "minimum_top2_gap", "mean_top2_gap", "final_margin", "terminal_entropy",
        "co_dominance_fraction", "winner_lead_fraction", "main_rival_lead_fraction",
    ))
    leadership = _scalars(process.get("leadership"), (
        "first_identifiable_leader", "final_leader", "final_leader_identifiable",
        "winner_switch_count", "final_winner_was_first_identifiable_leader",
        "first_identifiable_leader_time_model_units", "final_leader_acquired_time_model_units",
    ))
    support, mixed = {}, {}
    for family, destination in (("support_dynamics", support), ("mixed_cognitive_support", mixed)):
        for mode, values in process.get(family, {}).get("by_mode", {}).items():
            if values.get("available") is False:
                continue
            row = _scalars(values, ("available", "terminal_affective_resistance", "terminal_cognitive_resistance"))
            for key in ("cognitive_signed_input", "affective_signed_input", "net_signed_input",
                        "cognitive_affective_friction", "mixed_support_index"):
                if key in values:
                    row[key] = _scalars(values[key], ("initial", "mean_post_emergence", "terminal"))
            for key in ("top_supporters_terminal", "top_inhibitors_terminal"):
                if key in values:
                    row[key] = _constraints(values[key])
            destination[mode] = row
    patterns = [p for p in process.get("patterns", []) if p in PATTERN_MEANINGS]
    return {
        "competition": competition, "leadership": leadership,
        "support_dynamics": support, "mixed_cognitive_support": mixed,
        "patterns": patterns, "plain_language_patterns": [PATTERN_MEANINGS[p] for p in patterns],
        "terminal_action_support": _scalars(summary.get("final_action_activations", {}), summary.get("final_action_activations", {}).keys()),
        "comparative_readout_not_choice_probability": _scalars(summary.get("probabilities", {}), summary.get("probabilities", {}).keys()),
    }


def metric_changes(baseline, contextual):
    """Exact descriptive directions; never new decision/ambiguity thresholds."""
    result = {}
    for name in ("final_margin", "mean_top2_gap", "minimum_top2_gap", "terminal_entropy", "co_dominance_fraction"):
        before = baseline.get("competition", {}).get(name)
        after = contextual.get("competition", {}).get(name)
        if isinstance(before, (int, float)) and isinstance(after, (int, float)) and math.isfinite(before) and math.isfinite(after):
            result[name] = "increased" if after > before else "decreased" if after < before else "unchanged"
    return result
