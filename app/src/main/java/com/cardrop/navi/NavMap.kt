package com.cardrop.navi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.CarPosition
import com.cardrop.core.model.Place
import com.cardrop.core.ui.theme.toColor
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RoutePoint
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.util.Locale

/** Another crew car to draw on the map. */
data class MapCar(
    val id: String,
    val label: String,
    val color: CarColor,
    val position: CarPosition,
)

/**
 * FOLLOW = behind this car, heading up. WAYPOINT = this car and the new stop. OVERVIEW = the whole trip.
 * FREE = the driver dragged or zoomed the map; leave the camera alone until "현재 위치".
 */
enum class NavCamera { FOLLOW, WAYPOINT, OVERVIEW, FREE }

/**
 * Owner: Member B.
 *
 * The map itself: MapLibre (open-source map engine) with free OpenFreeMap tiles (OpenStreetMap data,
 * no API key). Draws the live route, this car ([me]), the other crew cars, the waypoint and the
 * destination, and moves the camera. The driver can drag and zoom; that calls [onUserMovedMap].
 * Needs internet for the map tiles; without it the route and cars still show on a plain background.
 */
@Composable
fun NavMap(
    route: LiveRoute?,
    progressKm: Double,
    me: RoutePoint?,
    cars: List<MapCar>,
    waypoint: Place?,
    destination: Place?,
    camera: NavCamera,
    onUserMovedMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Our UI is scaled to the design (DesignScale); scale the map's labels and icons the same way.
    val pixelRatio = LocalDensity.current.density * MAP_SCALE

    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(
            context,
            MapLibreMapOptions.createFromAttributes(context)
                .pixelRatio(pixelRatio)
                .camera(CameraPosition.Builder().target(LatLng(37.4, 127.9)).zoom(6.5).build()) // Korea
                .logoEnabled(false)
                .attributionEnabled(false)
                .compassEnabled(false),
        )
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var layers by remember { mutableStateOf<NavMapLayers?>(null) }
    val userMoved by rememberUpdatedState(onUserMovedMap)

    // MapView needs the activity's lifecycle calls (start, resume, pause, stop, destroy).
    DisposableEffect(lifecycle, mapView) {
        mapView.onCreate(null)
        mapView.getMapAsync { m ->
            m.uiSettings.isCompassEnabled = false
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) userMoved()
            }
            loadStyle(m, mapView, pixelRatio) { layers = it }
            map = m
        }
        var started = false
        var resumed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            mapView.onDestroy()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    val currentLayers = layers
    LaunchedEffect(currentLayers, route, progressKm, me, cars, waypoint, destination) {
        currentLayers?.update(route, progressKm, me, cars, waypoint, destination)
    }

    val currentMap = map
    // FOLLOW: runs on every GPS reading. OVERVIEW: again when a new route arrives. Others: once.
    val key = when (camera) {
        NavCamera.FOLLOW -> me
        NavCamera.OVERVIEW -> route
        else -> null
    }
    LaunchedEffect(currentMap, currentLayers, camera, key) {
        if (currentMap == null || currentLayers == null || camera == NavCamera.FREE) return@LaunchedEffect
        moveCamera(currentMap, mapView, camera, route, me, cars, waypoint, destination)
    }
}

private fun moveCamera(
    map: MapLibreMap,
    view: MapView,
    camera: NavCamera,
    route: LiveRoute?,
    me: RoutePoint?,
    cars: List<MapCar>,
    waypoint: Place?,
    destination: Place?,
) {
    val height = view.height.toDouble()
    val width = view.width
    if (height <= 0 || width <= 0) return
    if (camera == NavCamera.FOLLOW && me != null) {
        val position = CameraPosition.Builder()
            .target(LatLng(me.lat, me.lng))
            .zoom(FOLLOW_ZOOM)
            .bearing(me.bearing)
            .tilt(FOLLOW_TILT)
            // Top padding moves the car down the screen, below the banner, like a real navigator.
            .padding(0.0, height * 0.42, 0.0, 0.0)
            .build()
        map.easeCamera(CameraUpdateFactory.newCameraPosition(position), 1_000, false)
        return
    }
    val points = buildList {
        if (camera == NavCamera.WAYPOINT && me != null && waypoint != null) {
            add(LatLng(me.lat, me.lng))
            add(LatLng(waypoint.lat, waypoint.lng))
        } else {
            route?.geometry?.points?.let { points ->
                val every = (points.size / 200).coerceAtLeast(1)
                points.filterIndexed { i, _ -> i % every == 0 }.forEach { add(LatLng(it.lat, it.lng)) }
                add(LatLng(points.last().lat, points.last().lng))
            }
            me?.let { add(LatLng(it.lat, it.lng)) }
            cars.forEach { add(LatLng(it.position.lat, it.position.lng)) }
            waypoint?.let { add(LatLng(it.lat, it.lng)) }
            destination?.let { add(LatLng(it.lat, it.lng)) }
        }
    }
    if (points.size < 2) return
    val bounds = LatLngBounds.Builder().includes(points).build()
    val side = (width * 0.1).toInt()
    map.easeCamera(
        CameraUpdateFactory.newLatLngBounds(bounds, 0.0, 0.0, side, (height * 0.22).toInt(), side, (height * 0.2).toInt()),
        1_200,
    )
}

private fun loadStyle(map: MapLibreMap, view: MapView, pixelRatio: Float, onReady: (NavMapLayers) -> Unit) {
    var usedFallback = false
    view.addOnDidFailLoadingMapListener { message ->
        Log.w(TAG, "Map style failed ($message); using the plain offline style")
        if (!usedFallback) {
            usedFallback = true
            map.setStyle(Style.Builder().fromJson(OFFLINE_STYLE)) { onReady(NavMapLayers(it, pixelRatio)) }
        }
    }
    map.setStyle(Style.Builder().fromUri(STYLE_URL)) { onReady(NavMapLayers(it, pixelRatio)) }
}

/** Our sources and layers on top of the base map, plus the marker images. */
private class NavMapLayers(private val style: Style, private val ratio: Float) {

    private val labelImages = mutableSetOf<String>()
    private var waypointLabel: String? = null
    private var destinationLabel: String? = null

    init {
        applyDesignColours()
        showKoreanNames()
        style.addImage(IMG_ME, mePuck())
        style.addImage(IMG_WAYPOINT, squarePin(ORANGE, "경"))
        style.addImage(IMG_DESTINATION, squarePin(BLUE, null))

        listOf(SRC_DONE, SRC_AHEAD, SRC_CARS, SRC_ME, SRC_PLACES).forEach {
            style.addSource(GeoJsonSource(it, FeatureCollection.fromFeatures(emptyList())))
        }
        // Route lines go under the base map's labels, markers on top of everything.
        val firstLabel = style.layers.firstOrNull { it is SymbolLayer }?.id
        fun addBelowLabels(layer: Layer) {
            if (firstLabel != null) style.addLayerBelow(layer, firstLabel) else style.addLayer(layer)
        }
        addBelowLabels(routeLine(LYR_DONE_CASING, SRC_DONE, "#FFFFFF", 5f, 13f))
        addBelowLabels(routeLine(LYR_DONE, SRC_DONE, "#B0B8C1", 3f, 8f))
        addBelowLabels(routeLine(LYR_AHEAD_CASING, SRC_AHEAD, "#FFFFFF", 5f, 14f))
        addBelowLabels(routeLine(LYR_AHEAD, SRC_AHEAD, "#2F6FE0", 3f, 9f))

        style.addLayer(
            CircleLayer(LYR_CARS, SRC_CARS).withProperties(
                PropertyFactory.circleRadius(9f),
                PropertyFactory.circleColor(Expression.get("color")),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circlePitchAlignment(Property.CIRCLE_PITCH_ALIGNMENT_MAP),
            ),
        )
        style.addLayer(labelLayer(LYR_CAR_LABELS, SRC_CARS, offsetY = 12f))
        style.addLayer(
            SymbolLayer(LYR_PLACES, SRC_PLACES).withProperties(
                PropertyFactory.iconImage(Expression.get("icon")),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
        style.addLayer(labelLayer(LYR_PLACE_LABELS, SRC_PLACES, offsetY = 22f))
        style.addLayer(
            SymbolLayer(LYR_ME, SRC_ME).withProperties(
                PropertyFactory.iconImage(IMG_ME),
                PropertyFactory.iconRotate(Expression.get("bearing")),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
        )
    }

    fun update(route: LiveRoute?, progressKm: Double, me: RoutePoint?, cars: List<MapCar>, waypoint: Place?, destination: Place?) {
        val geometry = route?.geometry
        source(SRC_DONE).setGeoJson(line(geometry?.slice(0.0, progressKm).orEmpty()))
        source(SRC_AHEAD).setGeoJson(line(geometry?.slice(progressKm, geometry.totalKm).orEmpty()))

        source(SRC_ME).setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    me?.let {
                        Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat)).apply {
                            addNumberProperty("bearing", it.bearing)
                        }
                    },
                ),
            ),
        )
        source(SRC_CARS).setGeoJson(
            FeatureCollection.fromFeatures(
                cars.map { car ->
                    val labelId = "label-car-${car.id}"
                    if (labelImages.add(labelId)) style.addImage(labelId, labelPill(car.label))
                    Feature.fromGeometry(Point.fromLngLat(car.position.lng, car.position.lat)).apply {
                        addStringProperty("color", hex(car.color.toColor().toArgb()))
                        addStringProperty("label", labelId)
                    }
                },
            ),
        )

        if (waypoint?.name != waypointLabel) {
            waypointLabel = waypoint?.name
            waypoint?.let { style.addImage(IMG_WAYPOINT_LABEL, labelPill(it.name)) }
        }
        if (destination?.name != destinationLabel) {
            destinationLabel = destination?.name
            destination?.let { style.addImage(IMG_DESTINATION_LABEL, labelPill(it.name)) }
        }
        source(SRC_PLACES).setGeoJson(
            FeatureCollection.fromFeatures(
                listOfNotNull(
                    destination?.let { pin(it, IMG_DESTINATION, IMG_DESTINATION_LABEL) },
                    waypoint?.let { pin(it, IMG_WAYPOINT, IMG_WAYPOINT_LABEL) },
                ),
            ),
        )
    }

    private fun source(id: String): GeoJsonSource = style.getSourceAs<GeoJsonSource>(id)!!

    private fun line(points: List<GeoPoint>): FeatureCollection =
        if (points.size < 2) {
            FeatureCollection.fromFeatures(emptyList())
        } else {
            FeatureCollection.fromFeature(Feature.fromGeometry(LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })))
        }

    private fun pin(place: Place, icon: String, label: String) =
        Feature.fromGeometry(Point.fromLngLat(place.lng, place.lat)).apply {
            addStringProperty("icon", icon)
            addStringProperty("label", label)
        }

    /** The design's colours for land, water and parks (board left third). */
    private fun applyDesignColours() {
        (style.getLayer("background") as? BackgroundLayer)?.setProperties(PropertyFactory.backgroundColor("#E9EEF3"))
        (style.getLayer("water") as? FillLayer)?.setProperties(PropertyFactory.fillColor("#C9E0F5"))
        listOf("park", "landcover_wood").forEach {
            (style.getLayer(it) as? FillLayer)?.setProperties(PropertyFactory.fillColor("#DCEFE2"))
        }
        listOf("highway_motorway_casing", "highway_major_casing").forEach {
            (style.getLayer(it) as? LineLayer)?.setProperties(PropertyFactory.lineColor("#CBD3DD"))
        }
    }

    /** The base map labels default to "Latin\nKorean"; show the Korean name only. */
    private fun showKoreanNames() {
        val korean = Expression.coalesce(Expression.get("name:ko"), Expression.get("name:nonlatin"), Expression.get("name"))
        style.layers.filterIsInstance<SymbolLayer>()
            .filterNot { "shield" in it.id }
            .forEach { it.setProperties(PropertyFactory.textField(korean)) }
    }

    private fun routeLine(id: String, source: String, color: String, lowZoomWidth: Float, highZoomWidth: Float) =
        LineLayer(id, source).withProperties(
            PropertyFactory.lineColor(color),
            PropertyFactory.lineWidth(
                Expression.interpolate(
                    Expression.linear(),
                    Expression.zoom(),
                    Expression.stop(6, lowZoomWidth),
                    Expression.stop(13, highZoomWidth),
                ),
            ),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )

    private fun labelLayer(id: String, source: String, offsetY: Float) =
        SymbolLayer(id, source).withProperties(
            PropertyFactory.iconImage(Expression.get("label")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_TOP),
            PropertyFactory.iconOffset(arrayOf(0f, offsetY)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        )

    // ---- Marker images, drawn with Android's Canvas so Korean text uses the system font ----

    /** Creates a bitmap of [w] x [h] design px, sharp at our pixel ratio. */
    private fun bitmap(w: Float, h: Float, draw: Canvas.() -> Unit): Bitmap =
        Bitmap.createBitmap((w * ratio).toInt(), (h * ratio).toInt(), Bitmap.Config.ARGB_8888).apply {
            density = (DisplayMetrics.DENSITY_DEFAULT * ratio).toInt() // tells MapLibre the pixel ratio
            Canvas(this).apply { scale(ratio, ratio); draw() }
        }

    /** This car: the design's blue dot with a white arrow showing the driving direction. */
    private fun mePuck(): Bitmap = bitmap(48f, 48f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x40000000
        drawCircle(24f, 26f, 20f, paint)
        paint.color = WHITE
        drawCircle(24f, 24f, 20f, paint)
        paint.color = BLUE
        drawCircle(24f, 24f, 15f, paint)
        paint.color = WHITE
        drawPath(
            Path().apply {
                moveTo(24f, 14f); lineTo(31f, 31f); lineTo(24f, 27f); lineTo(17f, 31f); close()
            },
            paint,
        )
    }

    /** The waypoint ("경", orange) and destination (flag, blue) markers from the design. */
    private fun squarePin(color: Int, text: String?): Bitmap = bitmap(42f, 42f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = 0x33000000
        drawRoundRect(RectF(2f, 4f, 40f, 42f), 13f, 13f, paint)
        paint.color = WHITE
        drawRoundRect(RectF(2f, 2f, 40f, 40f), 13f, 13f, paint)
        paint.color = color
        drawRoundRect(RectF(4f, 4f, 38f, 38f), 11f, 11f, paint)
        paint.color = WHITE
        if (text != null) {
            paint.textSize = 15f
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textAlign = Paint.Align.CENTER
            drawText(text, 21f, 21f - (paint.ascent() + paint.descent()) / 2, paint)
        } else {
            // Flag icon (design: M4 22V4 M4 4h12l-2 4 2 4H4), 20 px, centred.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.4f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            save()
            translate(11f, 10f)
            scale(20f / 24f, 20f / 24f)
            drawPath(Path().apply { moveTo(4f, 22f); lineTo(4f, 4f) }, paint)
            drawPath(Path().apply { moveTo(4f, 4f); lineTo(16f, 4f); lineTo(14f, 8f); lineTo(16f, 12f); lineTo(4f, 12f) }, paint)
            restore()
        }
    }

    /** A small white name tag under a marker ("삼촌", "여주휴게소"). */
    private fun labelPill(text: String): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
        }
        val width = paint.measureText(text) + 16f
        return bitmap(width + 2f, 24f) {
            paint.color = 0x26000000
            drawRoundRect(RectF(1f, 2f, width + 1f, 24f), 8f, 8f, paint)
            paint.color = WHITE
            drawRoundRect(RectF(1f, 1f, width + 1f, 22f), 8f, 8f, paint)
            paint.color = INK
            drawText(text, 9f, 11.5f - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }

    private fun hex(argb: Int) = String.format(Locale.US, "#%06X", argb and 0xFFFFFF)
}

private const val TAG = "CarDropNavMap"

/** OpenFreeMap: free, no API key, OpenStreetMap data. Attribution is shown in the panel. */
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/positron"

/** Used when the map style can't be downloaded: just the design's land colour. */
private const val OFFLINE_STYLE =
    """{"version":8,"sources":{},"layers":[{"id":"background","type":"background","paint":{"background-color":"#E9EEF3"}}]}"""

/** Map labels and markers a bit larger than the UI scale, so they read on the car screen. */
private const val MAP_SCALE = 1.25f
private const val FOLLOW_ZOOM = 13.0
private const val FOLLOW_TILT = 50.0

private const val WHITE = 0xFFFFFFFF.toInt()
private const val BLUE = 0xFF2F6FE0.toInt()
private const val ORANGE = 0xFFFF8A3D.toInt()
private const val INK = 0xFF191F28.toInt()

private const val SRC_DONE = "cd-route-done"
private const val SRC_AHEAD = "cd-route-ahead"
private const val SRC_CARS = "cd-cars"
private const val SRC_ME = "cd-me"
private const val SRC_PLACES = "cd-places"
private const val LYR_DONE = "cd-route-done"
private const val LYR_DONE_CASING = "cd-route-done-casing"
private const val LYR_AHEAD = "cd-route-ahead"
private const val LYR_AHEAD_CASING = "cd-route-ahead-casing"
private const val LYR_CARS = "cd-cars"
private const val LYR_CAR_LABELS = "cd-car-labels"
private const val LYR_PLACES = "cd-places"
private const val LYR_PLACE_LABELS = "cd-place-labels"
private const val LYR_ME = "cd-me"
private const val IMG_ME = "cd-me"
private const val IMG_WAYPOINT = "cd-waypoint"
private const val IMG_WAYPOINT_LABEL = "cd-waypoint-label"
private const val IMG_DESTINATION = "cd-destination"
private const val IMG_DESTINATION_LABEL = "cd-destination-label"
