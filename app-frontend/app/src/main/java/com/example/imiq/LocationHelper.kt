package com.example.imiq

import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class LocationHelper(context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * An explicitly user-selected manual origin takes precedence for the current
     * planning session. Otherwise a fresh device location is requested. No static
     * coordinate is ever substituted silently.
     */
    suspend fun getCurrentLocation(): Pair<Double, Double>? {
        TripOriginStore.manualOrigin()?.let { return it.lat to it.lon }
        return getMeasuredDeviceLocation()
    }

    suspend fun getMeasuredDeviceLocation(): Pair<Double, Double>? = suspendCoroutine { continuation ->
        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).addOnSuccessListener { location: Location? ->
                continuation.resume(location?.let { it.latitude to it.longitude })
            }.addOnFailureListener {
                continuation.resume(null)
            }
        } catch (_: SecurityException) {
            continuation.resume(null)
        }
    }
}
