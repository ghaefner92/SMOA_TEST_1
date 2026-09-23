package com.example.imiq

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Street-first MapLibre basemap.
 *
 * OpenFreeMap Liberty provides a clearer road hierarchy than the previous
 * dark basemap and works well for route presentation.
 */
private const val MAP_STYLE =
    "https://tiles.openfreemap.org/styles/liberty"

@Composable
fun MobMap(
    modifier: Modifier = Modifier,
    route: List<Pair<Double, Double>> = emptyList(),
    origin: Pair<Double, Double>? = null,
    destination: Pair<Double, Double>? = null,
    routeColor: Color = Mob.primary,
    center: Pair<Double, Double> = MAGDEBURG,
    currentLocation: Pair<Double, Double>? = null,
    zoom: Double = 13.5,
    interactive: Boolean = true,
    topInsetPx: Int = 150,
    bottomInsetPx: Int = 80,
    onMapReady: () -> Unit = {},
) {
    val holder = remember { MapHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val mapView = holder.view ?: return@LifecycleEventObserver

            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)

            holder.view?.onStop()
            holder.view?.onDestroy()

            holder.view = null
            holder.map = null
            holder.style = null
        }
    }

    AndroidView(
        modifier = modifier,

        factory = { context ->
            MapLibre.getInstance(context)

            MapView(context).also { mapView ->
                holder.view = mapView

                mapView.onCreate(null)
                mapView.onStart()
                mapView.onResume()

                mapView.getMapAsync { map ->
                    holder.map = map

                    map.uiSettings.setAllGesturesEnabled(interactive)

                    map.cameraPosition =
                        CameraPosition.Builder()
                            .target(
                                LatLng(
                                    center.first,
                                    center.second,
                                )
                            )
                            .zoom(zoom)
                            .build()

                    map.setStyle(
                        Style.Builder().fromUri(MAP_STYLE)
                    ) { style ->
                        holder.style = style

                        onMapReady()

                        applyMapState(
                            holder = holder,
                            map = map,
                            style = style,
                            route = route,
                            origin = origin,
                            destination = destination,
                            routeColor = routeColor,
                            center = center,
                            currentLocation = currentLocation,
                            zoom = zoom,
                            topInsetPx = topInsetPx,
                            bottomInsetPx = bottomInsetPx,
                        )
                    }
                }
            }
        },

        update = {
            val map = holder.map
            val style = holder.style

            if (
                map != null &&
                style != null &&
                style.isFullyLoaded
            ) {
                applyMapState(
                    holder = holder,
                    map = map,
                    style = style,
                    route = route,
                    origin = origin,
                    destination = destination,
                    routeColor = routeColor,
                    center = center,
                    currentLocation = currentLocation,
                    zoom = zoom,
                    topInsetPx = topInsetPx,
                    bottomInsetPx = bottomInsetPx,
                )
            }
        },
    )
}

private fun applyMapState(
    holder: MapHolder,
    map: MapLibreMap,
    style: Style,
    route: List<Pair<Double, Double>>,
    origin: Pair<Double, Double>?,
    destination: Pair<Double, Double>?,
    routeColor: Color,
    center: Pair<Double, Double>,
    currentLocation: Pair<Double, Double>?,
    zoom: Double,
    topInsetPx: Int,
    bottomInsetPx: Int,
) {
    ensureLayers(
        style = style,
        routeColor = routeColor,
    )

    updateCurrentLocation(
        style = style,
        location = currentLocation,
    )

    val routeSource =
        style.getSourceAs<GeoJsonSource>(SRC_ROUTE)

    val startSource =
        style.getSourceAs<GeoJsonSource>(SRC_START)

    val destinationSource =
        style.getSourceAs<GeoJsonSource>(SRC_DESTINATION)

    val cameraKey =
        routeCameraKey(
            route = route,
            origin = origin,
            destination = destination,
            center = center,
            zoom = zoom,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
        )

    /*
     * Marker provenance is independent from reconstructed geometry.
     *
     * When supplied, origin/destination are the exact points requested by the
     * user/routing call. The GraphHopper polyline itself remains untouched.
     */
    val markerOrigin = origin ?: route.firstOrNull()
    val markerDestination = destination ?: route.lastOrNull()

    fun setPointOrClear(
        source: GeoJsonSource?,
        point: Pair<Double, Double>?,
    ) {
        if (point == null) {
            source?.setGeoJson(
                FeatureCollection.fromFeatures(
                    emptyArray<Feature>()
                )
            )
        } else {
            source?.setGeoJson(
                Feature.fromGeometry(
                    Point.fromLngLat(
                        point.second,
                        point.first,
                    )
                )
            )
        }
    }

    setPointOrClear(startSource, markerOrigin)
    setPointOrClear(destinationSource, markerDestination)

    /*
     * No route geometry.
     *
     * Keep exact requested endpoints visible if they are known. This keeps
     * requested points and GraphHopper reconstruction conceptually separate.
     */
    if (route.isEmpty()) {
        routeSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                emptyArray<Feature>()
            )
        )

        val requestedPoints = listOfNotNull(
            markerOrigin,
            markerDestination,
        ).distinct()

        if (holder.lastCameraKey != cameraKey) {
            if (requestedPoints.isNotEmpty()) {
                fitCoordinates(
                    map = map,
                    coordinates = requestedPoints,
                    topInsetPx = topInsetPx,
                    bottomInsetPx = bottomInsetPx,
                    fallbackZoom = zoom,
                )
            } else {
                map.easeCamera(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(
                                LatLng(
                                    center.first,
                                    center.second,
                                )
                            )
                            .zoom(zoom)
                            .build()
                    ),
                    350,
                )
            }

            holder.lastCameraKey = cameraKey
        }

        return
    }

    /*
     * A two-point route means that only origin and destination are known.
     *
     * Deliberately show the endpoints without drawing a straight line between
     * them. A straight line could otherwise falsely look like actual routing
     * geometry.
     */
    if (route.size == 2) {
        routeSource?.setGeoJson(
            FeatureCollection.fromFeatures(
                emptyArray<Feature>()
            )
        )

        val cameraCoordinates = (
                route + listOfNotNull(markerOrigin, markerDestination)
                ).distinct()

        if (holder.lastCameraKey != cameraKey) {
            fitCoordinates(
                map = map,
                coordinates = cameraCoordinates,
                topInsetPx = topInsetPx,
                bottomInsetPx = bottomInsetPx,
                fallbackZoom = zoom,
            )

            holder.lastCameraKey = cameraKey
        }

        return
    }

    /*
     * Actual route geometry.
     *
     * GraphHopper geometry reaches this component as (latitude, longitude).
     * GeoJSON requires (longitude, latitude).
     */
    val points =
        route.map {
            Point.fromLngLat(
                it.second,
                it.first,
            )
        }

    routeSource?.setGeoJson(
        Feature.fromGeometry(
            LineString.fromLngLats(points)
        )
    )

    val cameraCoordinates = (
            route + listOfNotNull(markerOrigin, markerDestination)
            ).distinct()

    if (holder.lastCameraKey != cameraKey) {
        fitCoordinates(
            map = map,
            coordinates = cameraCoordinates,
            topInsetPx = topInsetPx,
            bottomInsetPx = bottomInsetPx,
            fallbackZoom = zoom,
        )

        holder.lastCameraKey = cameraKey
    }
}

private fun routeCameraKey(
    route: List<Pair<Double, Double>>,
    origin: Pair<Double, Double>?,
    destination: Pair<Double, Double>?,
    center: Pair<Double, Double>,
    zoom: Double,
    topInsetPx: Int,
    bottomInsetPx: Int,
): String =
    if (route.isEmpty()) {
        "empty:" +
                "${center.first}:" +
                "${center.second}:" +
                "$origin:" +
                "$destination:" +
                "$zoom:" +
                "$topInsetPx:" +
                "$bottomInsetPx"
    } else {
        "route:" +
                "${route.size}:" +
                "${route.first()}:" +
                "${route.last()}:" +
                "$origin:" +
                "$destination:" +
                "$topInsetPx:" +
                "$bottomInsetPx"
    }

private fun fitCoordinates(
    map: MapLibreMap,
    coordinates: List<Pair<Double, Double>>,
    topInsetPx: Int,
    bottomInsetPx: Int,
    fallbackZoom: Double,
) {
    val bounds =
        LatLngBounds.Builder()

    coordinates.forEach { coordinate ->
        bounds.include(
            LatLng(
                coordinate.first,
                coordinate.second,
            )
        )
    }

    try {
        map.easeCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds.build(),

                // left
                70,

                // top
                topInsetPx,

                // right
                70,

                // bottom
                bottomInsetPx,
            ),
            600,
        )
    } catch (_: Exception) {
        /*
         * Defensive fallback for malformed / degenerate bounds.
         */
        val first = coordinates.first()

        map.cameraPosition =
            CameraPosition.Builder()
                .target(
                    LatLng(
                        first.first,
                        first.second,
                    )
                )
                .zoom(fallbackZoom)
                .build()
    }
}

private fun ensureLayers(
    style: Style,
    routeColor: Color,
) {
    val coreColor =
        routeColor.toArgb()

    /*
     * A thinner casing works better with the light street-first basemap.
     *
     * The previous 11 px / 6 px combination visually exaggerated corners and
     * made a correct GraphHopper geometry appear less precise.
     */
    val casingColor =
        Mob.bg.toArgb()

    if (style.getSource(SRC_ROUTE) == null) {
        style.addSource(
            GeoJsonSource(SRC_ROUTE)
        )

        style.addSource(
            GeoJsonSource(SRC_START)
        )

        style.addSource(
            GeoJsonSource(SRC_DESTINATION)
        )

        style.addSource(
            GeoJsonSource(SRC_LOCATION)
        )

        /*
         * Route outline / casing.
         */
        style.addLayer(
            LineLayer(
                LYR_CASING,
                SRC_ROUTE,
            ).withProperties(
                PropertyFactory.lineColor(casingColor),
                PropertyFactory.lineWidth(7.5f),
                PropertyFactory.lineOpacity(0.75f),
                PropertyFactory.lineCap(
                    Property.LINE_CAP_ROUND
                ),
                PropertyFactory.lineJoin(
                    Property.LINE_JOIN_ROUND
                ),
            )
        )

        /*
         * Main route line.
         */
        style.addLayer(
            LineLayer(
                LYR_CORE,
                SRC_ROUTE,
            ).withProperties(
                PropertyFactory.lineColor(coreColor),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineOpacity(0.95f),
                PropertyFactory.lineCap(
                    Property.LINE_CAP_ROUND
                ),
                PropertyFactory.lineJoin(
                    Property.LINE_JOIN_ROUND
                ),
            )
        )

        /*
         * Origin marker.
         *
         * A hollow marker identifies the start of the selected route.
         */
        style.addLayer(
            CircleLayer(
                LYR_START,
                SRC_START,
            ).withProperties(
                PropertyFactory.circleRadius(6.5f),
                PropertyFactory.circleColor(
                    android.graphics.Color.WHITE
                ),
                PropertyFactory.circleStrokeColor(
                    coreColor
                ),
                PropertyFactory.circleStrokeWidth(3.5f),
            )
        )

        /*
         * Destination marker.
         *
         * A solid marker gives the route a clear visual direction while
         * preserving the selected mode color.
         */
        style.addLayer(
            CircleLayer(
                LYR_DESTINATION,
                SRC_DESTINATION,
            ).withProperties(
                PropertyFactory.circleRadius(7.0f),
                PropertyFactory.circleColor(
                    coreColor
                ),
                PropertyFactory.circleStrokeColor(
                    android.graphics.Color.WHITE
                ),
                PropertyFactory.circleStrokeWidth(3.0f),
            )
        )

        /*
         * Device/current location marker.
         */
        style.addLayer(
            CircleLayer(
                LYR_LOCATION,
                SRC_LOCATION,
            ).withProperties(
                PropertyFactory.circleRadius(8f),
                PropertyFactory.circleColor(
                    Mob.primary.toArgb()
                ),
                PropertyFactory.circleStrokeColor(
                    android.graphics.Color.WHITE
                ),
                PropertyFactory.circleStrokeWidth(3f),
            )
        )
    } else {
        /*
         * Route mode may change while the map and its style remain alive.
         *
         * Update the existing layers instead of recreating them.
         */
        (style.getLayer(LYR_CORE) as? LineLayer)
            ?.setProperties(
                PropertyFactory.lineColor(coreColor)
            )

        (style.getLayer(LYR_START) as? CircleLayer)
            ?.setProperties(
                PropertyFactory.circleStrokeColor(coreColor)
            )

        (style.getLayer(LYR_DESTINATION) as? CircleLayer)
            ?.setProperties(
                PropertyFactory.circleColor(coreColor)
            )
    }
}

private fun updateCurrentLocation(
    style: Style,
    location: Pair<Double, Double>?,
) {
    val source =
        style.getSourceAs<GeoJsonSource>(
            SRC_LOCATION
        ) ?: return

    if (location == null) {
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                emptyArray<Feature>()
            )
        )
    } else {
        source.setGeoJson(
            Feature.fromGeometry(
                Point.fromLngLat(
                    location.second,
                    location.first,
                )
            )
        )
    }
}

private const val SRC_ROUTE =
    "imiq-route-src"

private const val SRC_START =
    "imiq-start-src"

private const val SRC_DESTINATION =
    "imiq-destination-src"

private const val SRC_LOCATION =
    "imiq-location-src"

private const val LYR_CASING =
    "imiq-route-casing"

private const val LYR_CORE =
    "imiq-route-core"

private const val LYR_START =
    "imiq-start-layer"

private const val LYR_DESTINATION =
    "imiq-destination-layer"

private const val LYR_LOCATION =
    "imiq-location-layer"

val MAGDEBURG =
    52.1305 to 11.6276

class MapHolder {
    var view: MapView? = null
    var map: MapLibreMap? = null
    var style: Style? = null
    var lastCameraKey: String? = null
}
