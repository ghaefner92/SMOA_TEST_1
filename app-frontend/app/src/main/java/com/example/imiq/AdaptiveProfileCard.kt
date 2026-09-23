package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Optional one-item profile refinement surfaced from deterministic XAI.
 * Nothing is changed until the user selects a fresh response and taps the
 * explicit update button.
 */
@Composable
fun AdaptiveProfileCard(
    passportJson: String,
    onPassportUpdated: (String) -> Unit,
) {
    val candidate = remember(passportJson) { AdaptiveProfileService.candidate(passportJson) }
        ?: return
    val de = LanguageState.current == AppLanguage.DE
    val isEstimated = candidate.measurementStatus == "estimated"
    val scope = rememberCoroutineScope()
    var selected by remember(candidate.questionId) { mutableStateOf<Int?>(null) }
    var submitting by remember(candidate.questionId) { mutableStateOf(false) }
    var error by remember(candidate.questionId) { mutableStateOf<String?>(null) }

    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (isEstimated) {
                if (de)
                    "Neue Profilmessung"
                else
                    "New profile measurement"
            } else {
                if (de)
                    "Kurzer Profil-Check"
                else
                    "Quick profile check"
            },
            color = Mob.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (isEstimated) {
                if (de)
                    "Dieser Zusammenhang wurde bisher aus deinen bisherigen Antworten geschätzt. Eine direkte Antwort kann diese Schätzung durch eine echte Messung ersetzen."
                else
                    "This relationship has so far been estimated from your previous answers. One direct answer can replace that estimate with an explicit measurement."
            } else {
                if (de)
                    "HOTCO-CT hat einen bereits gemessenen Punkt gefunden, bei dem eine erneute Antwort dein Profil pr?zisieren kann."
                else
                    "HOTCO-CT found a previously measured point where a fresh answer can refine your profile."
            },
            color = Mob.textSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            microQuestionPrompt(candidate),
            color = Mob.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (isEstimated) {
                if (de)
                    "F?r diesen Punkt gibt es noch keine fr?here direkte Antwort. Deine Auswahl wird als erste explizite Messung gespeichert."
                else
                    "There is no previous direct answer for this item. Your selection will be stored as its first explicit measurement."
            } else {
                val previous =
                    requireNotNull(
                        candidate.currentRawRating
                    )

                if (de)
                    "Deine letzte Antwort war $previous/7. W?hle bewusst eine neue Messung ? auch dieselbe Antwort wird als neuer Messzeitpunkt gespeichert."
                else
                    "Your last answer was $previous/7. Choose a fresh measurement ? even the same answer is stored as a new measurement wave."
            },
            color = Mob.textSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            (1..7).forEach { value ->
                val active = selected == value
                Box(
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) Mob.primary else Mob.surfaceHi)
                        .border(
                            1.dp,
                            if (active) Mob.primary else Mob.borderHi,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable(enabled = !submitting) { selected = value },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        value.toString(),
                        color = if (active) Mob.onPrimary else Mob.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, fontSize = 11.sp)
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val rating = selected ?: return@Button
                submitting = true
                error = null
                scope.launch {
                    try {
                        val updated = AdaptiveProfileService.submitExplicitUpdate(
                            passportJson = passportJson,
                            candidate = candidate,
                            rawRating = rating,
                        )
                        // save() archives both the previous and new Passport revisions.
                        PassportStore.save(updated)
                        onPassportUpdated(updated)
                    } catch (e: Exception) {
                        error = if (de) "Die Profilaktualisierung konnte nicht gespeichert werden. Bitte versuche es erneut."
                        else "The profile update couldn't be saved. Please try again."
                    } finally {
                        submitting = false
                    }
                }
            },
            enabled = selected != null && !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isEstimated) {
                    if (de)
                        "Als direkte Messung speichern"
                    else
                        "Save as direct measurement"
                } else {
                    if (de)
                        "Als neue Profilmessung speichern"
                    else
                        "Save as new profile measurement"
                }
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            if (isEstimated) {
                if (de)
                    "Diese Antwort ersetzt genau eine bisher geschätzte Verbindung durch eine beobachtete Messung. Die übrigen Schätzungen werden danach neu berechnet; die vorherige Passport-Version bleibt erhalten."
                else
                    "This answer replaces exactly one previously estimated relationship with an observed measurement. Remaining estimates are recalculated, while the previous Passport revision is preserved."
            } else {
                if (de)
                    "Die vorherige Passport-Version bleibt unver?ndert erhalten. Die neue Messung wird als n?chste Revision mit Herkunft und ?nderungsereignis gespeichert."
                else
                    "The previous Passport remains unchanged. The new measurement is stored as the next revision with provenance and an update event."
            },
            color = Mob.textSecondary,
            fontSize = 10.sp,
        )
    }
}
