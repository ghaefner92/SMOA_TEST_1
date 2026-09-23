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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onRecreateProfile: () -> Unit,
    onBackClick: () -> Unit
) {
    val s = LocalStrings.current
    var showConfirm by remember { mutableStateOf(false) }
    var companionName by remember { mutableStateOf(TokenManager.companionName().orEmpty()) }
    var companionError by remember { mutableStateOf<String?>(null) }
    val name = remember { TokenManager.getUserName()?.takeIf { it.isNotBlank() } ?: "You" }
    val currentLang = LanguageState.current

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(s.resetDialogTitle, color = Mob.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    s.resetDialogBody,
                    color = Mob.textSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onRecreateProfile() }) {
                    Text(s.reset, color = Mob.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(s.cancel, color = Mob.textSecondary)
                }
            },
            containerColor = Mob.surfaceHi
        )
    }

    MobBackground {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Mob.glass)
                        .border(1.dp, Mob.border, CircleShape)
                        .clickable { onBackClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(s.settings, color = Mob.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                // Profile card
                MobGlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(52.dp).clip(CircleShape).background(Mob.brandGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.first().uppercase(),
                                color = Mob.onPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Mob.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (LanguageState.current == AppLanguage.DE) "Kognitiver Pass" else "Cognitive Passport",
                                color = Mob.primary,
                                fontSize = 13.sp,
                            )
                        }
                        Icon(Icons.Default.Verified, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(24.dp))
                MobSectionLabel(s.generalSection)
                Spacer(Modifier.height(10.dp))
                MobGlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, null, tint = Mob.textSecondary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.language, color = Mob.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(s.languageSub, color = Mob.textMuted, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    LanguageSegmented(current = currentLang)
                }

                Spacer(Modifier.height(24.dp))
                MobSectionLabel(s.profileSection)
                Spacer(Modifier.height(10.dp))
                MobGlassCard {
                    Text("Digital Companion", color = Mob.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Choose any name you like. You can change it later.", color = Mob.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = companionName,
                        onValueChange = { companionName = it; companionError = null },
                        label = { Text("Name your Digital Companion") },
                        singleLine = true,
                        isError = companionError != null,
                        supportingText = companionError?.let { { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { companionName = ""; DigitalCompanionStore.reset() }) { Text("Reset name") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = {
                            try { DigitalCompanionStore.save(companionName); companionName = TokenManager.companionName().orEmpty() }
                            catch (_: IllegalArgumentException) { companionError = "Use 1–24 characters without line breaks." }
                        }) { Text(if (companionName.isBlank()) "Skip" else "Save") }
                    }
                }
                Spacer(Modifier.height(16.dp))
                MobGlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleGlyph(Icons.Default.Refresh, Mob.primary, diameter = 42.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.recreateProfile, color = Mob.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.recreateProfileSub, color = Mob.textSecondary, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    MobButton(s.resetRecreate, onClick = { showConfirm = true }, leadingIcon = Icons.Default.Refresh)
                }

                Spacer(Modifier.height(24.dp))
                MobSectionLabel(s.aboutSection)
                Spacer(Modifier.height(10.dp))
                MobGlassCard {
                    Text("IMIQ Mobility", color = Mob.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text("Version 1.0.0", color = Mob.textSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        s.aboutBlurb,
                        color = Mob.textMuted, fontSize = 12.sp, lineHeight = 17.sp
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/** Two-segment EN/DE switch. Tapping a segment flips the whole app instantly. */
@Composable
private fun LanguageSegmented(current: AppLanguage) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Mob.surfaceHi)
            .border(1.dp, Mob.border, RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LangSegment("🇬🇧  English", current == AppLanguage.EN, Modifier.weight(1f)) {
            LanguageState.set(AppLanguage.EN)
        }
        LangSegment("🇩🇪  Deutsch", current == AppLanguage.DE, Modifier.weight(1f)) {
            LanguageState.set(AppLanguage.DE)
        }
    }
}

@Composable
private fun LangSegment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Mob.primary else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Mob.onPrimary else Mob.textSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF07090D, heightDp = 820)
@Composable
private fun SettingsPreview() {
    SettingsScreen(onRecreateProfile = {}, onBackClick = {})
}
