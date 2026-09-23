package com.example.imiq

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

/** Static place and display metadata. No cognitive profile or route score lives here. */
object MobilityReferenceData {

    /** A selected location with identity separate from its displayed text. */
    data class Place(
        val label: String,
        val sub: String,
        val lat: Double,
        val lon: Double,
        val id: String = "coordinate:$lat,$lon",
        val provenance: String = "REFERENCE",
    )

    val origin = Place("Universitätsplatz", "OVGU Campus", 52.13915, 11.67696)
    val destination = Place("Hauptbahnhof", "Magdeburg Central Station", 52.13030, 11.62590)

    /** Quick-pick destinations for the picker. */
    val landmarks = listOf(
        Place("Hauptbahnhof", "Central Station", 52.13030, 11.62590),
        Place("Alter Markt", "Old Town", 52.13139, 11.63916),
        Place("Allee-Center", "Shopping", 52.12089, 11.63170),
        Place("Elbauenpark", "Jahrtausendturm", 52.13639, 11.66480),
        Place("Universitätsplatz", "OVGU Campus", 52.13915, 11.67696),
        Place("Domplatz", "Cathedral", 52.12468, 11.63489)
    )

    data class AiPick(
        val modeKey: String,
        val whyNow: String,
        val headline: String,
    )

    // ---- Value-dimension display metadata --------------------------------------
    data class ValueMeta(val label: String, val blurb: String, val icon: ImageVector, val color: Color)

    fun valueMeta(dimension: String): ValueMeta {
        val base = when (dimension) {
            "physical_activity" -> ValueMeta("Active", "Keeps you moving", Icons.Default.DirectionsRun, Color(0xFF84CC16))
            "cost_saving" -> ValueMeta("Saves money", "No fuel or fare", Icons.Default.Savings, Color(0xFF38BDF8))
            "pro_environment" -> ValueMeta("Eco", "Near-zero emissions", Icons.Default.Eco, Color(0xFF22C55E))
            "speed" -> ValueMeta("Fast", "Shortest travel time", Icons.Default.Bolt, Color(0xFFF59E0B))
            "comfort" -> ValueMeta("Comfort", "Relaxed, sheltered", Icons.Default.Weekend, Color(0xFFA78BFA))
            "autonomy" -> ValueMeta("Freedom", "Go on your schedule", Icons.Default.Explore, Color(0xFFFB7185))
            "safety" -> ValueMeta("Safety", "Lower accident risk", Icons.Default.Shield, Color(0xFF60A5FA))
            "privacy" -> ValueMeta("Privacy", "Your own space", Icons.Default.Lock, Color(0xFF94A3B8))
            "reliable" -> ValueMeta("Reliable", "Predictable arrival", Icons.Default.Schedule, Color(0xFF34D399))
            "hedonism" -> ValueMeta("Enjoyment", "A pleasant trip", Icons.Default.Favorite, Color(0xFFF472B6))
            else -> ValueMeta(dimension.replace('_', ' '), "", Icons.Default.Star, Color(0xFFD4408A))
        }
        // Localize the chip label when a translation exists for this dimension.
        return appStrings().valueLabels[dimension]?.let { base.copy(label = it) } ?: base
    }

    fun modeIcon(modeKey: String): ImageVector = when (modeKey.lowercase()) {
        "car", "car_pt" -> Icons.Default.DirectionsCar
        "bike", "bike_pt" -> Icons.Default.DirectionsBike
        "foot", "walk" -> Icons.Default.DirectionsWalk
        "pt" -> Icons.Default.DirectionsBus
        else -> Icons.Default.Navigation
    }

    /** "Xh Ym" / "Y min" from seconds. */
    fun formatDuration(seconds: Double): String {
        val mins = (seconds / 60.0).toInt()
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "$mins min"
    }

    fun formatDistance(meters: Double): String =
        if (meters >= 1000) String.format("%.1f km", meters / 1000.0) else "${meters.toInt()} m"

}
