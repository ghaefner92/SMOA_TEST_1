package com.example.imiq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AdaptiveProfileScreen(
    onBack: () -> Unit,
    onViewPassport: () -> Unit,
) {
    val de = LanguageState.current == AppLanguage.DE
    var passportJson by remember { mutableStateOf(PassportStore.load()) }
    var updatedThisSession by remember { mutableStateOf(false) }
    val candidate = if (updatedThisSession) null
        else passportJson?.let { AdaptiveProfileService.candidate(it) }

    MobBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mob.glass)
                        .border(1.dp, Mob.border, CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        "Back",
                        tint = Mob.textPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (de) "Profil verfeinern" else "Refine your profile",
                        color = Mob.textPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (de) "Kurze, XAI-gesteuerte Nachmessungen" else "Short, XAI-driven re-measurements",
                        color = Mob.textSecondary,
                        fontSize = 11.sp,
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (passportJson == null) {
                    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (de) "Kein Cognitive Passport vorhanden." else "No Cognitive Passport is available.",
                            color = Mob.textPrimary,
                        )
                    }
                } else if (updatedThisSession) {
                    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (de) "Neue Messung gespeichert" else "New measurement saved",
                            color = Mob.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (de)
                                "Für diese Sitzung stellen wir keine weitere Profilfrage. Die vorherige Passport-Version bleibt erhalten und der aktualisierte HOTCO-CT-Lauf wurde als neue Revision gespeichert."
                            else
                                "No further profile question will be asked in this session. The previous Passport remains preserved and the updated HOTCO-CT run was stored as a new revision.",
                            color = Mob.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                } else if (candidate != null) {
                    AdaptiveProfileCard(
                        passportJson = requireNotNull(passportJson),
                        onPassportUpdated = { updated ->
                            passportJson = updated
                            updatedThisSession = true
                        },
                    )
                } else {
                    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (de) "Im Moment ist keine Nachmessung nötig." else "No profile re-check is needed right now.",
                            color = Mob.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (de)
                                "Dein aktueller HOTCO-CT-Lauf liefert keinen XAI-Trigger, der eine zusätzliche Frage rechtfertigt."
                            else
                                "Your current HOTCO-CT run has no XAI trigger that justifies an additional question.",
                            color = Mob.textSecondary,
                            fontSize = 12.sp,
                        )
                    }
                }

                MobGlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (de) "Längsschnittverlauf" else "Longitudinal history",
                        color = Mob.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (de)
                            "Gespeicherte Passport-Snapshots auf diesem Gerät: ${PassportStore.historyCount()}. Frühere Versionen werden bei normalen Aktualisierungen nicht überschrieben."
                        else
                            "Passport snapshots stored on this device: ${PassportStore.historyCount()}. Normal updates never overwrite previous versions.",
                        color = Mob.textSecondary,
                        fontSize = 12.sp,
                    )
                }

                OutlinedButton(
                    onClick = onViewPassport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (de) "Aktuellen Passport ansehen" else "View current Passport")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
