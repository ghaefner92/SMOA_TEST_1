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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.*

/**
 * User-facing Cognitive Passport view. Scientific diagnostics remain available
 * through the existing advanced screen, but are not the default UX.
 */
@Composable
fun PassportSummaryScreen(
    onBack: () -> Unit,
    onAdvanced: () -> Unit,
) {
    val de = LanguageState.current == AppLanguage.DE
    val json = remember { PassportStore.load() }
    val cp = remember(json) {
        runCatching {
            json?.let { Json.parseToJsonElement(it).jsonObject["cognitive_passport"]?.jsonObject }
        }.getOrNull()
    }

    MobBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Mob.glass)
                        .border(1.dp, Mob.border, CircleShape).clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (de) "Dein Mobilitätsprofil" else "Your mobility profile",
                        color = Mob.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (de) "Die verständliche Ansicht deines Cognitive Passport" else "A readable view of your Cognitive Passport",
                        color = Mob.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }

            if (cp == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (de) "Kein Cognitive Passport vorhanden." else "No Cognitive Passport is available.", color = Mob.textSecondary)
                }
                return@Column
            }

            val profile = cp["profile"] as? JsonObject
            val needs = profile?.get("needs") as? JsonObject
            val topNeeds = needs?.mapNotNull { (key, value) ->
                value.jsonPrimitive.doubleOrNull?.let { key to it }
            }?.sortedByDescending { it.second }?.take(3).orEmpty()
            val availability = profile?.get("availability") as? JsonObject
            val tendency = (cp["deliberation"] as? JsonObject)
                ?.get("terminal_tendency")?.jsonPrimitive?.contentOrNull
            val lineage = cp["lineage"] as? JsonObject
            val revision = lineage?.get("revision")?.jsonPrimitive?.intOrNull ?: 1
            val lastEvent = lastUpdateEventOrNull(lineage)

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (de) "Was dir am wichtigsten ist" else "What matters most to you",
                        color = Mob.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    topNeeds.forEachIndexed { index, item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(28.dp).clip(CircleShape).background(Mob.primary.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("${index + 1}", color = Mob.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(userNeedLabel(item.first, de), color = Mob.textPrimary, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            Text("${(item.second * 100).toInt()}%", color = Mob.textSecondary, fontSize = 12.sp)
                        }
                    }
                }

                MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (de) "Aktuelle Modell-Tendenz" else "Current model tendency",
                        color = Mob.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tendency?.let { userModeLabel(it, de) }
                            ?: if (de) "Keine eindeutige Tendenz" else "No terminal tendency available",
                        color = Mob.textPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (de)
                            "Das ist eine simulierte HOTCO-CT-Tendenz, keine kalibrierte Wahlwahrscheinlichkeit."
                        else
                            "This is a simulated HOTCO-CT tendency, not a calibrated choice probability.",
                        color = Mob.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (de) "Deine verfügbaren Optionen" else "Your available options",
                        color = Mob.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf("walk", "bike", "pt", "car").forEach { mode ->
                        val available = availability?.get(mode)?.jsonPrimitive?.booleanOrNull
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(userModeLabel(mode, de), color = Mob.textPrimary, modifier = Modifier.weight(1f))
                            Text(
                                when (available) {
                                    true -> if (de) "verfügbar" else "available"
                                    false -> if (de) "nicht verfügbar" else "not available"
                                    null -> "—"
                                },
                                color = if (available == true) Mob.primary else Mob.textMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }

                MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.History, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(9.dp))
                        Text(
                            if (de) "Längsschnitt" else "Longitudinal profile",
                            color = Mob.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (de)
                            "Revision $revision · ${PassportStore.historyCount()} vollständige Passport-Snapshots auf diesem Gerät"
                        else
                            "Revision $revision · ${PassportStore.historyCount()} complete Passport snapshots on this device",
                        color = Mob.textSecondary,
                        fontSize = 12.sp,
                    )
                    if (lastEvent != null) {
                        val delta = lastEvent["delta_raw_rating"]?.jsonPrimitive?.intOrNull
                        val changed = lastEvent["value_changed"]?.jsonPrimitive?.booleanOrNull
                        Spacer(Modifier.height(7.dp))
                        Text(
                            if (de) {
                                if (changed == true) "Letzte Nachmessung: Änderung ${delta?.let { if (it >= 0) "+$it" else "$it" } ?: ""}"
                                else "Letzte Nachmessung: stabil bestätigt"
                            } else {
                                if (changed == true) "Latest re-measurement: change ${delta?.let { if (it >= 0) "+$it" else "$it" } ?: ""}"
                                else "Latest re-measurement: stability confirmed"
                            },
                            color = Mob.textPrimary,
                            fontSize = 12.sp,
                        )
                    }
                }

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Mob.surfaceHi)
                        .border(1.dp, Mob.borderHi, RoundedCornerShape(16.dp))
                        .clickable { onAdvanced() }.padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Tune, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (de) "Erweiterte Modelldetails" else "Advanced model details",
                            color = Mob.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            if (de) "XAI, Trajektorien, Readout und Diagnostik anzeigen" else "View XAI, trajectory readout and diagnostics",
                            color = Mob.textSecondary,
                            fontSize = 11.sp,
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Mob.textMuted)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

internal fun lastUpdateEventOrNull(lineage: JsonObject?): JsonObject? =
    lineage?.get("last_update_event") as? JsonObject

private fun userModeLabel(key: String, de: Boolean): String = when (key.lowercase()) {
    "walk", "foot" -> if (de) "Zu Fuß" else "Walking"
    "bike" -> if (de) "Fahrrad" else "Cycling"
    "pt" -> if (de) "ÖPNV" else "Public transport"
    "car" -> if (de) "Auto" else "Car"
    else -> key.replace('_', ' ')
}

private fun userNeedLabel(key: String, de: Boolean): String = when (key) {
    "pro_env" -> if (de) "Umweltfreundlichkeit" else "Eco-friendliness"
    "physical" -> if (de) "Körperliche Aktivität" else "Physical activity"
    "privacy" -> if (de) "Privatsphäre und Platz" else "Privacy and space"
    "autonomy" -> if (de) "Flexibilität und Autonomie" else "Flexibility and autonomy"
    "cost" -> if (de) "Kosten" else "Cost"
    "speed" -> if (de) "Zeitersparnis" else "Time saving"
    "safety_accident" -> if (de) "Verkehrssicherheit" else "Traffic safety"
    "safety_crime" -> if (de) "Persönliche Sicherheit" else "Personal security"
    "comfort" -> if (de) "Komfort" else "Comfort"
    "reliable" -> if (de) "Zuverlässigkeit" else "Reliability"
    "health_infection" -> if (de) "Gesundheitsschutz" else "Health protection"
    else -> key.replace('_', ' ')
}
