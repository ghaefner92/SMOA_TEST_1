package com.example.imiq

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Deterministic, in-coverage locations used only when the remote geocoder has
 * no usable response.  This keeps manual-origin selection available without
 * replacing or reordering normal geocoder results.
 */
internal fun localFallbackSearch(query: String): List<GeoResult> {
    val normalizedQuery = query.normalizedSearchTokens()
    if (normalizedQuery.isEmpty()) return emptyList()

    return MobilityReferenceData.landmarks
        .filter { place ->
            val searchable = "${place.label} ${place.sub} Magdeburg".normalizedSearchTokens()
            normalizedQuery.all { token -> searchable.any { it.contains(token) } }
        }
        .map { GeoResult(id = "local:${it.lat},${it.lon}", label = it.label, sub = it.sub, lat = it.lat, lon = it.lon, provenance = "LOCAL_FALLBACK") }
}

private fun String.normalizedSearchTokens(): List<String> =
    lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

private fun GeoResult.toPlace() = MobilityReferenceData.Place(
    label = label, sub = sub, lat = lat, lon = lon, id = id, provenance = provenance,
)

/**
 * Shared place search for both destination and manual origin selection.
 * It explains the current routing-coverage boundary instead of claiming that an
 * out-of-area place does not exist.
 */
@Composable
fun PlaceSearchOverlay(
    title: String,
    hint: String,
    onPick: (MobilityReferenceData.Place) -> Unit,
    onClose: () -> Unit,
    showPopular: Boolean = true,
) {
    val de = LanguageState.current == AppLanguage.DE
    var query by remember { mutableStateOf("") }
    var response by remember { mutableStateOf(GeoSearchResponse(emptyList(), false)) }
    var loading by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    BackHandler(enabled = true) { onClose() }

    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length < 2) {
            response = GeoSearchResponse(emptyList(), false)
            loading = false
            return@LaunchedEffect
        }
        // Local landmarks are deterministic and private. They are useful as
        // soon as the user types, so debounce only the network request.
        val local = localFallbackSearch(q)
        if (local.isNotEmpty()) response = GeoSearchResponse(local, false)
        loading = true
        delay(500)
        // Manual origin remains usable when remote search is slow or
        // unavailable. Remote results are merged without hiding local ones.
        val remote = async { GeocodingApiService.searchWithCoverage(q) }.await()
        response = GeoSearchResponse(mergeGeoResults(local, remote.results), remote.outsideCoverageDetected, remote.failed)
        loading = false
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(Mob.bg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Mob.glass)
                    .border(1.dp, Mob.border, CircleShape).clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Mob.textPrimary, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Mob.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Mob.surfaceHi)
                        .border(1.dp, Mob.borderHi, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text(hint, color = Mob.textMuted, fontSize = 15.sp)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Mob.textPrimary, fontSize = 15.sp),
                            cursorBrush = SolidColor(Mob.primary),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            "Clear",
                            tint = Mob.textMuted,
                            modifier = Modifier.size(18.dp).clickable { query = "" },
                        )
                    }
                }
            }
        }

        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
            val q = query.trim()
            when {
                q.length < 2 && showPopular -> {
                    Text(
                        if (de) "Schnellziele in Magdeburg" else "Quick destinations in Magdeburg",
                        color = Mob.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    MobilityReferenceData.landmarks.forEach { place ->
                        PlaceSearchResultRow(place.label, place.sub) { onPick(place) }
                    }
                }
                q.length < 2 -> Unit
                response.results.isNotEmpty() -> {
                    response.results.forEach { r ->
                        PlaceSearchResultRow(r.label, r.sub) { onPick(r.toPlace()) }
                    }
                    if (loading) Text(if (de) "Weitere Ergebnisse werden gesucht…" else "Looking for more results…", color = Mob.textMuted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                    if (response.outsideCoverageDetected) { Spacer(Modifier.height(8.dp)); CoverageNotice(compact = true) }
                }
                loading -> Text(if (de) "Suche…" else "Searching…", color = Mob.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 16.dp))
                response.results.isEmpty() && response.outsideCoverageDetected -> CoverageNotice()
                response.failed -> Text(
                    if (de) "Die Ortssuche ist gerade nicht erreichbar." else "Place search is temporarily unavailable.",
                    color = Mob.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                response.results.isEmpty() -> Text(
                    if (de) "Kein passender Ort gefunden." else "No matching place found.",
                    color = Mob.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> Unit
            }
        }
    }
}

@Composable
private fun CoverageNotice(compact: Boolean = false) {
    val de = LanguageState.current == AppLanguage.DE
    MobGlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            if (de) "Außerhalb des aktuellen Routing-Gebiets" else "Outside the current routing area",
            color = Mob.primary,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 13.sp else 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (de)
                "Der Ort wurde erkannt, aber IMIQ kann derzeit nur Fahrten in der Region Magdeburg planen."
            else
                "The place was recognized, but IMIQ currently plans trips only within the Magdeburg routing region.",
            color = Mob.textSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PlaceSearchResultRow(label: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(Mob.surfaceHi),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Place, null, tint = Mob.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = Mob.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = Mob.textMuted, fontSize = 12.sp)
        }
    }
}
