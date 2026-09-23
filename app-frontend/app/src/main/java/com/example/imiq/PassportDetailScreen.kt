package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

// =====================================================================
//  PassportDetailScreen
//  Full read-only display of the saved cognitive passport. Renders
//  each section of the schema as a card, with the raw JSON at the
//  bottom inside an expandable container.
// =====================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PassportDetailScreen(onBack: () -> Unit) {
    val s = LocalStrings.current
    val passportJson = remember { PassportStore.load() }
    val cp = remember(passportJson) {
        try {
            passportJson
                ?.let { Json.parseToJsonElement(it).jsonObject }
                ?.get("cognitive_passport")?.jsonObject
        } catch (_: Exception) { null }
    }

    MobBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mob.glass)
                        .border(1.dp, Mob.border, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(s.cognitivePassportTitle, color = Mob.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (cp == null) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MetadataCard(cp)
                    cp["profile"]?.jsonObject?.let { ProfileCard(it) }
                    cp["deliberation"]?.jsonObject?.let { DeliberationCard(it) }
                    cp["process_diagnostics"]?.jsonObject?.let { ProcessDiagnosticsCard(it) }
                    cp["routing_parameters"]?.jsonObject?.let { RoutingCard(it) }
                    cp["xai_diagnostics"]?.jsonObject?.let { XaiDiagnosticsCard(it) }
                    cp["agent_profile"]?.jsonObject?.let { AgentProfileCard(it) }
                    cp["spatial_context"]?.jsonObject?.let { SpatialContextCard(it) }
                    cp["top_needs_ranking"]?.jsonArray?.let { TopNeedsCard(it) }
                    passportJson?.let { RawJsonCard(it) }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

// =====================================================================
//  Empty state
// =====================================================================

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val s = LocalStrings.current
        Text("📭", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(s.noPassportTitle, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            s.noPassportSub,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// =====================================================================
//  Section cards
// =====================================================================

@Composable
private fun SectionCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Mob.primary)
        subtitle?.let {
            Spacer(Modifier.height(2.dp))
            Text(it, fontSize = 11.sp, color = Mob.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun MetadataCard(cp: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdMetadata, s.pdMetadataSub) {
        KvRow("Agent ID", cp["agent_id"]?.jsonPrimitive?.content)
        KvRow("Passport schema", cp["schema_version"]?.jsonPrimitive?.content)
        KvRow("Version", cp["version"]?.jsonPrimitive?.content)
        KvRow("Model", cp["model"]?.jsonPrimitive?.content)
        KvRow("Generated at", cp["timestamp"]?.jsonPrimitive?.content)
        val topology = cp["topology"]?.jsonObject
        topology?.let {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            KvRow("Needs", it["n_needs"]?.jsonPrimitive?.intOrNull?.toString())
            KvRow("Modes", it["n_modes"]?.jsonPrimitive?.intOrNull?.toString())
            KvRow("Total nodes", it["n_nodes"]?.jsonPrimitive?.intOrNull?.toString())
        }
    }
}

@Composable
private fun ProfileCard(profile: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdProfile, s.pdProfileSub) {
        profile["needs"]?.jsonObject?.let { needs ->
            Text("Needs (0–1)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            needs.forEach { (k, v) ->
                v.jsonPrimitive.doubleOrNull?.let { f ->
                    BarRow(label = k, value = f.toFloat())
                }
            }
        }
        profile["valences"]?.jsonObject?.let { valences ->
            Spacer(Modifier.height(8.dp))
            Text("Action valences (-1 to +1)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            valences.forEach { (mode, value) ->
                KvRow(prettyMode(mode), value.jsonPrimitive.doubleOrNull?.let { "%.3f".format(it) })
            }
        }
        profile["availability"]?.jsonObject?.let { availability ->
            Spacer(Modifier.height(8.dp))
            Text("User-reported availability", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            availability.forEach { (mode, value) ->
                KvRow(
                    prettyMode(mode),
                    value.jsonPrimitive.booleanOrNull?.let {
                        if (it) "available" else "not available"
                    }
                )
            }
        }
        profile["environmental_tolerances"]?.jsonObject?.let { tols ->
            Spacer(Modifier.height(8.dp))
            Text("Environmental tolerances (0–1)",
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            tols.forEach { (k, v) ->
                v.jsonPrimitive.doubleOrNull?.let { f ->
                    BarRow(label = k, value = f.toFloat())
                }
            }
            profile["tolerance_note"]?.jsonPrimitive?.contentOrNull?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        profile["values"]?.jsonObject?.let { values ->
            Spacer(Modifier.height(8.dp))
            Text("Schwartz values (0–1)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            values.forEach { (k, v) ->
                v.jsonPrimitive.doubleOrNull?.let { f ->
                    BarRow(label = k, value = f.toFloat())
                }
            }
        }
    }
}

@Composable
private fun DeliberationCard(delib: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdDeliberation, s.pdDeliberationSub) {
        val tendency = delib["terminal_tendency"]?.jsonPrimitive?.contentOrNull
        tendency?.let {
            Text("Terminal tendency: ${prettyMode(it)}", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        delib["comparative_readout"]?.jsonObject?.let { readout ->
            Text("Comparative latent-action readout", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            readout.forEach { (mode, p) ->
                p.jsonPrimitive.doubleOrNull?.let { value ->
                    BarRow(label = prettyMode(mode), value = value.toFloat(), showPercent = true)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        KvRow("Terminal margin",
            delib["terminal_margin"]?.jsonPrimitive?.doubleOrNull?.let { "%.4f".format(it) })
        KvRow("Practically differentiated",
            delib["practically_differentiated"]?.jsonPrimitive?.booleanOrNull?.toString())
        KvRow("Settling time (model units)",
            delib["settling_time_model_units"]?.jsonPrimitive?.doubleOrNull?.let { "%.2f".format(it) })
        KvRow("Operational settling",
            delib["settling_achieved"]?.jsonPrimitive?.booleanOrNull?.let {
                if (it) "achieved" else "not achieved"
            })
        delib["readout_interpretation"]?.jsonPrimitive?.contentOrNull?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 10.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProcessDiagnosticsCard(diagnostics: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdDissonance, s.pdDissonanceSub) {
        diagnostics["input_organization"]?.jsonObject?.let { input ->
            Text("Input organization", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            KvRow("Cognitive-affective alignment", renderJsonValue(input["cognitive_affective_alignment"] ?: JsonNull))
            KvRow("Cognitive-affective incongruence", renderJsonValue(input["cognitive_affective_incongruence"] ?: JsonNull))
            KvRow("Support cancellation", renderJsonValue(input["support_cancellation"] ?: JsonNull))
            KvRow("Mixed-sign cognitive support", renderJsonValue(input["mixed_sign_cognitive_support"] ?: JsonNull))
        }
        diagnostics["action_process"]?.jsonObject?.let { action ->
            Spacer(Modifier.height(8.dp))
            Text("Action process", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            action.forEach { (key, value) -> KvRow(key, renderJsonValue(value)) }
        }
        diagnostics["settling"]?.jsonObject?.let { settling ->
            Spacer(Modifier.height(8.dp))
            Text("Computational settling", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            settling.forEach { (key, value) -> KvRow(key, renderJsonValue(value)) }
        }
        diagnostics["active_constraint_tension_terminal"]?.jsonObject
            ?.get("total")?.jsonObject?.let { total ->
                Spacer(Modifier.height(8.dp))
                Text("Terminal active tension", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                total.forEach { (key, value) -> KvRow(key, renderJsonValue(value)) }
            }
    }
}

@Composable
private fun RoutingCard(routing: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdRouting, s.pdRoutingSub) {
        routing["mode_weights"]?.jsonObject?.let { mw ->
            Text("Mode weights", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            mw.forEach { (mode, p) ->
                p.jsonPrimitive.doubleOrNull?.let { value ->
                    BarRow(label = prettyMode(mode), value = value.toFloat(), showPercent = true)
                }
            }
        }
        routing["utility_coefficients"]?.jsonObject?.let { uc ->
            Spacer(Modifier.height(10.dp))
            Text("Utility coefficients", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            uc.forEach { (k, v) ->
                KvRow(k, v.jsonPrimitive.doubleOrNull?.let { "%.4f".format(it) })
            }
        }
        routing["contextual_flags"]?.jsonObject?.let { cf ->
            Spacer(Modifier.height(10.dp))
            Text("Contextual flags", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            cf.forEach { (k, v) ->
                val b = v.jsonPrimitive.booleanOrNull
                KvRow(k, if (b == true) "✓ on" else "—")
            }
        }
    }
}

@Composable
private fun XaiDiagnosticsCard(xai: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdXai, s.pdXaiSub) {
        KvRow("XAI schema", xai["schema_version"]?.jsonPrimitive?.contentOrNull)

        val summary = xai["winner_summary"]?.jsonObject
        summary?.let {
            Spacer(Modifier.height(6.dp))
            Text("Winner explanation", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            KvRow("Terminal tendency", it["mode"]?.jsonPrimitive?.contentOrNull?.let(::prettyMode))
            KvRow("Main rival", it["main_rival"]?.jsonPrimitive?.contentOrNull?.let(::prettyMode))
            KvRow("Cognitive signed input", signedNumber(it["terminal_cognitive_signed_input"]))
            KvRow("Affective signed input", signedNumber(it["terminal_affective_signed_input"]))
            KvRow("Cognitive-affective friction", decimalNumber(it["terminal_cognitive_affective_friction"]))
            KvRow("Mixed cognitive support", decimalNumber(it["terminal_mixed_cognitive_support"]))

            it["top_supporters_terminal"]?.jsonArray?.let { supporters ->
                if (supporters.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Strongest supporting needs", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary)
                    supporters.forEach { item ->
                        val obj = item.jsonObject
                        val need = obj["need"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val value = signedNumber(obj["signed_input"])
                        Text("• ${prettyNeed(need)}${value?.let { "  ($it)" } ?: ""}", fontSize = 12.sp)
                    }
                }
            }
            it["top_inhibitors_terminal"]?.jsonArray?.let { inhibitors ->
                if (inhibitors.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Strongest opposing needs", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error)
                    inhibitors.forEach { item ->
                        val obj = item.jsonObject
                        val need = obj["need"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        val value = signedNumber(obj["signed_input"])
                        Text("• ${prettyNeed(need)}${value?.let { "  ($it)" } ?: ""}", fontSize = 12.sp)
                    }
                }
            }
        }

        xai["competition"]?.jsonObject?.let { competition ->
            Spacer(Modifier.height(10.dp))
            Text("Competition", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            KvRow("Co-dominance fraction", decimalNumber(competition["co_dominance_fraction"]))
            KvRow("Winner lead fraction", decimalNumber(competition["winner_lead_fraction"]))
            KvRow("Mean top-two gap", decimalNumber(competition["mean_top2_gap"]))
            KvRow("Minimum top-two gap", decimalNumber(competition["minimum_top2_gap"]))
        }

        xai["leadership"]?.jsonObject?.let { leadership ->
            Spacer(Modifier.height(10.dp))
            Text("Leadership trajectory", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            KvRow("First identifiable leader",
                leadership["first_identifiable_leader"]?.jsonPrimitive?.contentOrNull?.let(::prettyMode))
            KvRow("Final leader",
                leadership["final_leader"]?.jsonPrimitive?.contentOrNull?.let(::prettyMode))
            KvRow("Winner switches", leadership["winner_switch_count"]?.jsonPrimitive?.intOrNull?.toString())
            KvRow("Final leader acquired (model time)",
                leadership["final_leader_acquired_time_model_units"]?.jsonPrimitive?.doubleOrNull?.let { "%.2f".format(it) })
        }

        xai["patterns"]?.jsonArray?.let { patterns ->
            if (patterns.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Detected simulation patterns", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                patterns.forEach { pattern ->
                    Text("• ${prettyPattern(pattern.jsonPrimitive.content)}", fontSize = 12.sp)
                }
            }
        }

        xai["interpretation_guardrails"]?.jsonArray?.let { notes ->
            if (notes.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Interpretation notes", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                notes.forEach { note ->
                    Text("• ${note.jsonPrimitive.content}", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AgentProfileCard(ap: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdAgent, null) {
        ap.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
    }
}

@Composable
private fun SpatialContextCard(sc: JsonObject) {
    val s = LocalStrings.current
    SectionCard(s.pdSpatial, s.pdSpatialSub) {
        sc.forEach { (k, v) -> KvRow(k, renderJsonValue(v)) }
    }
}

@Composable
private fun TopNeedsCard(needs: JsonArray) {
    val s = LocalStrings.current
    SectionCard(s.pdTopNeeds, null) {
        needs.forEachIndexed { idx, e ->
            Text("${idx + 1}. ${e.jsonPrimitive.content}", fontSize = 13.sp)
        }
    }
}

@Composable
private fun RawJsonCard(raw: String) {
    val s = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.pdRawJson, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary)
                    Text(s.pdRawJsonSub,
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Text(
                        prettyPrintJson(raw),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// =====================================================================
//  Small helpers
// =====================================================================

@Composable
private fun KvRow(label: String, value: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value ?: "—",
            modifier = Modifier.weight(1.2f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun BarRow(label: String, value: Float, showPercent: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row {
            Text(label, modifier = Modifier.weight(1f), fontSize = 12.sp)
            Text(
                if (showPercent) "${"%.1f".format(value * 100)}%"
                else "%.3f".format(value),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        LinearProgressIndicator(
            progress = value.coerceIn(0f, 1f),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(4.dp)
        )
    }
}

private fun prettyMode(key: String): String = when (key.lowercase()) {
    "car"  -> "Car"
    "bike" -> "Bicycle"
    "pt"   -> "Public Transport"
    "walk" -> "Walking"
    else   -> key
}

private fun prettyNeed(key: String): String = when (key) {
    "pro_env" -> "Protecting the environment"
    "physical" -> "Staying active"
    "privacy" -> "Personal space"
    "autonomy" -> "Freedom and flexibility"
    "cost" -> "Saving money"
    "speed" -> "Speed and saving time"
    "safety_accident" -> "Traffic safety"
    "safety_crime" -> "Feeling safe"
    "comfort" -> "Comfort"
    "reliable" -> "Reliability"
    "health_infection" -> "Health and hygiene"
    else -> key
}

private fun prettyPattern(value: String): String =
    value.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun decimalNumber(element: JsonElement?): String? =
    element?.jsonPrimitive?.doubleOrNull?.let { "%.3f".format(it) }

private fun signedNumber(element: JsonElement?): String? =
    element?.jsonPrimitive?.doubleOrNull?.let { "%+.3f".format(it) }

private fun renderJsonValue(e: JsonElement): String = when (e) {
    is JsonPrimitive -> {
        if (e.isString) e.content
        else e.doubleOrNull?.let { d ->
            if (d == d.toLong().toDouble()) d.toLong().toString()
            else "%.4f".format(d)
        } ?: e.content
    }
    is JsonArray  -> e.joinToString(", ") { renderJsonValue(it) }
    is JsonObject -> e.toString()
}

private fun prettyPrintJson(raw: String): String = try {
    val parsed = Json.parseToJsonElement(raw)
    Json { prettyPrint = true; prettyPrintIndent = "  " }
        .encodeToString(JsonElement.serializer(), parsed)
} catch (_: Exception) { raw }
