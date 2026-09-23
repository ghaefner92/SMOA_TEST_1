package com.example.imiq

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

private data class QuickDestination(val place: MobilityReferenceData.Place)

@Composable
fun MobilityHomeScreen(userName: String = "", onPlanTrip: (MobilityReferenceData.Place) -> Unit = {}, onOpenProfile: () -> Unit = {}, onOpenSettings: () -> Unit = {}) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val de = LanguageState.current == AppLanguage.DE
    val helper = remember { LocationHelper(context) }
    val quick = remember { MobilityReferenceData.landmarks.take(3).map(::QuickDestination) }
    var permitted by remember { mutableStateOf(hasAnyLocationPermission(context)) }
    var measured by rememberSaveable { mutableStateOf<Pair<Double, Double>?>(null) }
    var center by rememberSaveable { mutableStateOf(TripOriginStore.manualOrigin()?.let { it.lat to it.lon } ?: MAGDEBURG) }
    var destination by rememberSaveable { mutableStateOf<MobilityReferenceData.Place?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }; var selectingOrigin by rememberSaveable { mutableStateOf(false) }; var originChoice by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<MobilityReferenceData.Place?>(null) }; var message by rememberSaveable { mutableStateOf<String?>(null) }; var locating by rememberSaveable { mutableStateOf(false) }
    fun acquireLocation(continuePlan: Boolean = false) { locating = true; scope.launch { val point = helper.getMeasuredDeviceLocation(); locating = false; when {
        point == null -> { message = if (de) "Der Standort ist nicht verfügbar. Wähle stattdessen einen Startpunkt." else "Your location is unavailable. Choose another starting point instead."; if (continuePlan) originChoice = true }
        !RoutingCoveragePolicy.contains(point.first, point.second) -> { message = if (de) "Der aktuelle Standort liegt außerhalb des Routing-Gebiets." else "Your current location is outside the supported routing area."; if (continuePlan) originChoice = true }
        else -> { TripOriginStore.clearManual(); measured = point; center = point; message = null; if (continuePlan) pending?.let { pending = null; onPlanTrip(it) } }
    } } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result -> permitted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true || result[Manifest.permission.ACCESS_COARSE_LOCATION] == true; if (permitted) acquireLocation(true) else { message = if (de) "Standortfreigabe wurde nicht erteilt. Du kannst einen Startpunkt wählen." else "Location permission was not granted. Choose another starting point instead."; originChoice = pending != null } }
    fun requestLocation(continuePlan: Boolean = false) { if (permitted) acquireLocation(continuePlan) else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
    fun plan() { destination?.let { selected -> if (measured != null || TripOriginStore.manualOrigin() != null) onPlanTrip(selected) else { pending = selected; originChoice = true } } }
    LaunchedEffect(permitted) { if (permitted && measured == null && TripOriginStore.manualOrigin() == null) acquireLocation() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val origin = measured ?: TripOriginStore.manualOrigin()?.let { it.lat to it.lon }
        MobMap(Modifier.fillMaxSize(), center = center, currentLocation = origin, zoom = if (origin == null) 14.2 else 15.2)
        HomeTopActions(onOpenSettings, onOpenProfile, userName)
        IconButton({ measured?.let { center = it } ?: requestLocation() }, Modifier.align(Alignment.CenterEnd).padding(end = ImiqSpacing.md).size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)) { Icon(Icons.Default.MyLocation, if (de) "Standort zentrieren" else "Center location") }
        TripPlannerSheet(Modifier.align(Alignment.BottomCenter), originLabel = when { measured != null -> if (de) "Aktueller Standort" else "Current location"; TripOriginStore.manualOrigin() != null -> TripOriginStore.manualOrigin()!!.label; else -> if (de) "Startpunkt wählen" else "Choose starting point" }, originAvailable = origin != null, locating, destination, message, quick, { originChoice = true }, { searching = true }, ::plan, { destination = it; center = it.lat to it.lon; message = if (origin == null) (if (de) "Ziel gewählt. Wähle jetzt einen Startpunkt." else "Destination selected. Choose a starting point.") else (if (de) "Ziel gewählt. Route planen ist bereit." else "Destination selected. Plan route is ready.") })
        if (searching) PlaceSearchOverlay(if (de) "Ziel wählen" else "Choose destination", if (de) "Ziel in Magdeburg suchen" else "Search a destination in Magdeburg", { p -> destination = p; center = p.lat to p.lon; searching = false; message = null }, { searching = false })
        if (selectingOrigin) PlaceSearchOverlay(if (de) "Startpunkt wählen" else "Choose starting point", if (de) "Startpunkt in Magdeburg suchen" else "Search a starting point in Magdeburg", { p -> if (RoutingCoveragePolicy.contains(p.lat, p.lon)) { TripOriginStore.setManual(p); measured = null; center = p.lat to p.lon; selectingOrigin = false; message = null; pending?.let { pending = null; onPlanTrip(it) } } else message = if (de) "Dieser Startpunkt liegt außerhalb des Routing-Gebiets." else "This starting point is outside the supported routing area." }, { selectingOrigin = false }, false)
        if (originChoice) OriginChoiceOverlay({ originChoice = false; requestLocation(true) }, { originChoice = false; selectingOrigin = true }, { originChoice = false; pending = null })
    }
}

@Composable private fun HomeTopActions(onSettings: () -> Unit, onProfile: () -> Unit, userName: String) = Row(Modifier.fillMaxWidth().statusBarsPadding().padding(ImiqSpacing.md), horizontalArrangement = Arrangement.End) {
    IconButton(onSettings, Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)) { Icon(Icons.Default.Settings, "Settings") }
    Spacer(Modifier.width(ImiqSpacing.xs)); FilledTonalButton(onProfile, Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Place, null); Spacer(Modifier.width(ImiqSpacing.xxs)); Text(if (userName.isBlank()) "Cognitive Passport" else userName, style = MaterialTheme.typography.labelLarge) }
}

@Composable private fun TripPlannerSheet(modifier: Modifier, originLabel: String, originAvailable: Boolean, locating: Boolean, destination: MobilityReferenceData.Place?, message: String?, quick: List<QuickDestination>, chooseOrigin: () -> Unit, chooseDestination: () -> Unit, plan: () -> Unit, chooseQuick: (MobilityReferenceData.Place) -> Unit) {
    val de = LanguageState.current == AppLanguage.DE
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) { Column(Modifier.navigationBarsPadding().padding(horizontal = ImiqSpacing.md, vertical = ImiqSpacing.sm)) {
        SectionHeader(if (de) "Route planen" else "Plan a route"); Spacer(Modifier.height(ImiqSpacing.sm))
        TripLocationRow(if (de) "Von" else "From", if (locating) (if (de) "Standort wird ermittelt…" else "Getting location…") else originLabel, Icons.Default.MyLocation, chooseOrigin)
        Spacer(Modifier.height(ImiqSpacing.xs)); TripLocationRow(if (de) "Nach" else "To", destination?.label ?: if (de) "Ziel wählen" else "Choose destination", Icons.Default.Place, chooseDestination)
        message?.let { Spacer(Modifier.height(ImiqSpacing.sm)); InlineErrorState(it, severity = InlineErrorSeverity.Warning) }; Spacer(Modifier.height(ImiqSpacing.sm))
        PrimaryActionButton(if (de) "Route planen" else "Plan route", plan, Modifier.fillMaxWidth(), enabled = destination != null && originAvailable && !locating)
        Spacer(Modifier.height(ImiqSpacing.sm)); Text(if (de) "Schnellziele" else "Quick destinations", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ImiqSpacing.xs)) { quick.forEach { item -> val selected = destination?.lat == item.place.lat && destination?.lon == item.place.lon; AssistChip(onClick = { chooseQuick(item.place) }, label = { Text(if (selected) "✓ ${item.place.label}" else item.place.label, style = MaterialTheme.typography.labelMedium) }, modifier = Modifier.heightIn(min = 48.dp).semantics { stateDescription = if (selected) "Selected destination" else "Destination option" }) } }
    } }
}

@Composable private fun TripLocationRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(value, style = MaterialTheme.typography.bodyLarge) }, overlineContent = { Text(label, style = MaterialTheme.typography.labelMedium) }, leadingContent = { Icon(icon, null) }, trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Change $label") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).semantics { stateDescription = "$label: $value" }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f)))

@Composable private fun OriginChoiceOverlay(onUse: () -> Unit, onManual: () -> Unit, onCancel: () -> Unit) { val de = LanguageState.current == AppLanguage.DE; AlertDialog(onDismissRequest = onCancel, title = { Text(if (de) "Startpunkt auswählen" else "Choose starting point") }, text = { Text(if (de) "Verwende deinen aktuellen Standort oder wähle einen anderen Startpunkt." else "Use your current location or choose another starting point.") }, confirmButton = { PrimaryActionButton(if (de) "Aktuellen Standort verwenden" else "Use my current location", onUse, icon = Icons.Default.MyLocation) }, dismissButton = { AlternativeActionButton(if (de) "Anderen Startpunkt wählen" else "Choose another starting point", onManual) }) }

private fun hasAnyLocationPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
