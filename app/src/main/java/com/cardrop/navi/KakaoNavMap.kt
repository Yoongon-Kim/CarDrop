package com.cardrop.navi

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cardrop.core.model.Place
import com.cardrop.core.ui.theme.toColor
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.RoutePoint
import com.kakao.vectormap.GestureType
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.KakaoMapSdk
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapAuthException
import com.kakao.vectormap.MapGravity
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraAnimation
import com.kakao.vectormap.camera.CameraPosition
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.CompetitionType
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayer
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.TransformMethod
import com.kakao.vectormap.route.RouteLine
import com.kakao.vectormap.route.RouteLineLayer
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles

/**
 * Owner: Member B.
 *
 * The same map as [NavMap], drawn by Kakao Maps (Kakao's own Korean map, SDK `com.kakao.maps.open:android`)
 * instead of MapLibre + OpenFreeMap. Used when local.properties has `cardrop.kakaoNativeKey`.
 * Draws the live route, this car ([me]), the other crew cars, the waypoint and the destination, and
 * moves the camera the same way. If Kakao refuses the key, or the map can't start, it calls [onFailed]
 * and the panel goes back to [NavMap]. [logoBottomPx]: Kakao's logo must stay visible, so it sits just
 * above the panel's bottom cards.
 */
@Composable
fun KakaoNavMap(
    appKey: String,
    route: LiveRoute?,
    progressKm: Double,
    me: RoutePoint?,
    cars: List<MapCar>,
    waypoint: Place?,
    destination: Place?,
    camera: NavCamera,
    logoBottomPx: Int,
    onUserMovedMap: () -> Unit,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    // Our UI is scaled to the design (DesignScale); our markers use the same size as on the MapLibre map.
    val pixelRatio = density.density * MAP_SCALE
    val logo = LogoSpot(sidePx = with(density) { 18.dp.toPx() }, bottomPx = logoBottomPx.toFloat())
    val userMoved by rememberUpdatedState(onUserMovedMap)
    val failed by rememberUpdatedState(onFailed)

    // The Kakao SDK ships its engine only for ARM CPUs; elsewhere creating the view throws.
    val mapView = remember {
        runCatching {
            if (!KakaoMapSdk.isInitialized()) KakaoMapSdk.init(context.applicationContext, appKey)
            MapView(context).apply { setMapDpScale(density.density * KAKAO_TEXT_SCALE) }
        }.onFailure { Log.w(TAG, "Kakao map can't start; using MapLibre", it) }.getOrNull()
    }
    if (mapView == null) {
        LaunchedEffect(Unit) { failed() }
        return
    }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    var layers by remember { mutableStateOf<KakaoMapLayers?>(null) }

    // start() connects to Kakao (checks the key) and calls onMapReady, or onMapError when that fails.
    // Kakao's callbacks may come on another thread, so they hand over to the main thread.
    DisposableEffect(lifecycle, mapView) {
        val main = context.mainExecutor
        mapView.start(
            object : MapLifeCycleCallback() {
                override fun onMapDestroy() = Unit

                override fun onMapError(error: Exception) {
                    val code = (error as? MapAuthException)?.errorCode
                    Log.w(TAG, "Kakao map failed (code $code: ${error.message}); using MapLibre", error)
                    main.execute { failed() }
                }
            },
            object : KakaoMapReadyCallback() {
                override fun getPosition(): LatLng = LatLng.from(37.4, 127.9) // Korea
                override fun getZoomLevel(): Int = 7

                override fun onMapReady(kakaoMap: KakaoMap) {
                    Log.i(TAG, "Kakao map ready (SDK ${MapView.getApiVersion()})")
                    kakaoMap.setOnCameraMoveStartListener { _, gesture ->
                        // Our own camera moves report Unknown; anything else is the driver's finger.
                        if (gesture != GestureType.Unknown) main.execute { userMoved() }
                    }
                    main.execute {
                        layers = KakaoMapLayers(kakaoMap, MapMarkers(pixelRatio), pixelRatio)
                        map = kakaoMap
                    }
                }
            },
        )
        // The Kakao view needs the activity's resume and pause.
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.finish()
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)

    val currentMap = map
    val frame = remember(currentMap) { currentMap?.let(::KakaoFrame) }
    LaunchedEffect(frame, logo) {
        frame?.logo = logo
    }

    val currentLayers = layers
    LaunchedEffect(currentLayers, route, progressKm, me, cars, waypoint, destination) {
        currentLayers?.update(route, progressKm, me, cars, waypoint, destination)
    }

    // FOLLOW: runs on every GPS reading. OVERVIEW: again when a new route arrives. Others: once.
    val key = when (camera) {
        NavCamera.FOLLOW -> me
        NavCamera.OVERVIEW -> route
        else -> null
    }
    LaunchedEffect(frame, currentLayers, camera, key) {
        if (currentMap == null || frame == null || currentLayers == null || camera == NavCamera.FREE) return@LaunchedEffect
        moveCamera(currentMap, mapView, frame, camera, route, me, cars, waypoint, destination)
    }
}

/** Where Kakao's logo goes: px from the map's left edge and bottom edge. */
private data class LogoSpot(val sidePx: Float, val bottomPx: Float)

/**
 * The camera padding and Kakao's logo, kept together. Kakao measures the logo's position from inside the
 * padding (and hides it at a negative position, so the padding must never reach past the logo). So after
 * every change the logo is put back at [logo] on screen. The padding is remembered here because Kakao's
 * own getPadding() still returns the old value right after a change.
 */
private class KakaoFrame(private val map: KakaoMap) {
    private var left = 0
    private var bottom = 0

    var logo = LogoSpot(0f, 0f)
        set(value) {
            field = value
            placeLogo()
        }

    fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        this.left = left
        this.bottom = bottom
        map.setPadding(left, top, right, bottom)
        placeLogo()
    }

    private fun placeLogo() {
        map.logo?.setPosition(MapGravity.LEFT or MapGravity.BOTTOM, logo.sidePx - left, logo.bottomPx - bottom)
    }
}

private fun moveCamera(
    map: KakaoMap,
    view: MapView,
    frame: KakaoFrame,
    camera: NavCamera,
    route: LiveRoute?,
    me: RoutePoint?,
    cars: List<MapCar>,
    waypoint: Place?,
    destination: Place?,
) {
    val height = view.height
    val width = view.width
    if (height <= 0 || width <= 0) return
    if (camera == NavCamera.FOLLOW && me != null) {
        // Top padding moves the car down the screen, below the banner, like a real navigator.
        frame.setPadding(0, (height * 0.42).toInt(), 0, 0)
        val position = CameraPosition.Builder()
            .setPosition(LatLng.from(me.lat, me.lng))
            .setZoomLevel(FOLLOW_ZOOM)
            .setTiltAngle(Math.toRadians(FOLLOW_TILT))
            .setRotationAngle(kakaoAngle(me.bearing))
        // "Consecutive": each GPS reading's move continues smoothly from the previous one.
        map.moveCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.from(position)), CameraAnimation.from(1_000, false, true))
        return
    }
    val points = buildList {
        if (camera == NavCamera.WAYPOINT && me != null && waypoint != null) {
            add(LatLng.from(me.lat, me.lng))
            add(LatLng.from(waypoint.lat, waypoint.lng))
        } else {
            route?.geometry?.points?.let { points ->
                val every = (points.size / 200).coerceAtLeast(1)
                points.filterIndexed { i, _ -> i % every == 0 }.forEach { add(LatLng.from(it.lat, it.lng)) }
                add(LatLng.from(points.last().lat, points.last().lng))
            }
            me?.let { add(LatLng.from(it.lat, it.lng)) }
            cars.forEach { add(LatLng.from(it.position.lat, it.position.lng)) }
            waypoint?.let { add(LatLng.from(it.lat, it.lng)) }
            destination?.let { add(LatLng.from(it.lat, it.lng)) }
        }
    }
    if (points.size < 2) return
    // Room for the banner at the top and the cards at the bottom (padding), plus a margin all round
    // (fitMapPoints takes one margin for every side). The padding stops at the logo, which Kakao hides
    // if it would sit outside the padding.
    frame.setPadding(0, (height * 0.17).toInt(), 0, frame.logo.bottomPx.toInt())
    // Overview and waypoint views are flat and north up, like the MapLibre map's.
    map.moveCamera(CameraUpdateFactory.rotateTo(0.0))
    map.moveCamera(CameraUpdateFactory.tiltTo(0.0))
    map.moveCamera(CameraUpdateFactory.fitMapPoints(points.toTypedArray(), (width * 0.1).toInt()), CameraAnimation.from(1_200))
}

/**
 * A compass bearing (degrees, clockwise from north) as Kakao's camera angle: radians, counter-clockwise,
 * and between 0 and 2π (Kakao treats a negative angle as 0). Checked on the emulator.
 */
private fun kakaoAngle(bearing: Double): Double = Math.toRadians(((-bearing % 360.0) + 360.0) % 360.0)

/** Our route lines and markers on top of Kakao's map. */
private class KakaoMapLayers(map: KakaoMap, private val markers: MapMarkers, private val ratio: Float) {

    private val routes: RouteLineLayer = map.routeLineManager!!.layer
    // Our own marker layer with no "competition", so Kakao never hides a car or pin to avoid overlaps.
    private val labels: LabelLayer = map.labelManager!!.addLayer(
        LabelLayerOptions.from(MARKER_LAYER).setCompetitionType(CompetitionType.None),
    )!!

    // The design's route (target lines 50-52): a white road with a grey edge along the whole route, and the
    // blue line on it from this car on. Kakao draws a line's stroke on each side, so the edge is 4 + 18 + 4 = 26.
    private val roadStyle = RouteLineStyles.from(RouteLineStyle.from(ROAD_WIDTH, MARKER_WHITE, ROAD_EDGE, ROAD_EDGE_GRAY))
    private val aheadStyle = RouteLineStyles.from(RouteLineStyle.from(LINE_WIDTH, MARKER_BLUE))
    private var roadLine: RouteLine? = null
    private var roadRoute: LiveRoute? = null
    private var aheadLine: RouteLine? = null

    private var meLabel: Label? = null
    private val carLabels = mutableMapOf<String, Pair<MapCar, Label>>()
    private var waypointLabel: Pair<Place, Label>? = null
    private var destinationLabel: Pair<Place, Label>? = null

    fun update(route: LiveRoute?, progressKm: Double, me: RoutePoint?, cars: List<MapCar>, waypoint: Place?, destination: Place?) {
        val geometry = route?.geometry
        // The road is the whole route, so it changes only with a new route (not on every GPS reading).
        if (route !== roadRoute) {
            roadLine = line(roadLine, geometry?.points.orEmpty(), roadStyle, zOrder = 0)
            roadRoute = route
        }
        aheadLine = line(aheadLine, geometry?.slice(progressKm, geometry.totalKm).orEmpty(), aheadStyle, zOrder = 1)

        // This car: the puck turns with the driving direction.
        if (me == null) {
            meLabel?.remove()
            meLabel = null
        } else {
            val label = meLabel ?: labels.addLabel(
                LabelOptions.from(at(me.lat, me.lng))
                    .setStyles(style(markers.mePuck(), anchorY = 0.5f))
                    .setTransform(TransformMethod.AbsoluteRotation)
                    .setRank(RANK_ME),
            )
            label?.moveTo(at(me.lat, me.lng))
            // Unlike the camera, Kakao turns labels clockwise, like a compass (checked on the emulator).
            label?.rotateTo(Math.toRadians(me.bearing).toFloat())
            meLabel = label
        }

        // The other crew cars: a coloured dot with the driver's name. A new look = a new label.
        val ids = cars.map { it.id }.toSet()
        carLabels.keys.filterNot { it in ids }.forEach { carLabels.remove(it)?.second?.remove() }
        cars.forEach { car ->
            val old = carLabels[car.id]
            if (old != null && old.first.label == car.label && old.first.color == car.color) {
                old.second.moveTo(at(car.position.lat, car.position.lng))
                carLabels[car.id] = car to old.second
            } else {
                old?.second?.remove()
                val color = car.color.toColor().toArgb()
                val (image, anchorY) = withTag(carDot(color), gap = 12f, text = car.label)
                labels.addLabel(
                    LabelOptions.from(at(car.position.lat, car.position.lng)).setStyles(style(image, anchorY)).setRank(RANK_CAR),
                )?.let { carLabels[car.id] = car to it }
            }
        }

        waypointLabel = pin(waypointLabel, waypoint) { markers.waypointPin() }
        destinationLabel = pin(destinationLabel, destination) { markers.destinationPin() }
    }

    private fun line(old: RouteLine?, points: List<GeoPoint>, styles: RouteLineStyles, zOrder: Int): RouteLine? {
        if (points.size < 2) {
            old?.remove()
            return null
        }
        val segment = RouteLineSegment.from(points.map { at(it.lat, it.lng) }, styles)
        if (old == null) return routes.addRouteLine(RouteLineOptions.from(segment).setZOrder(zOrder))
        old.changeSegments(segment)
        return old
    }

    /** The waypoint or destination pin with its name; redrawn only when the place changes. */
    private fun pin(old: Pair<Place, Label>?, place: Place?, icon: () -> Bitmap): Pair<Place, Label>? {
        if (old?.first == place) return old
        old?.second?.remove()
        if (place == null) return null
        val (image, anchorY) = withTag(icon(), gap = PIN_TAG_GAP, text = place.name)
        val label = labels.addLabel(LabelOptions.from(at(place.lat, place.lng)).setStyles(style(image, anchorY)).setRank(RANK_PIN))
        return label?.let { place to it }
    }

    private fun at(lat: Double, lng: Double): LatLng = LatLng.from(lat, lng)

    /** Our images are already drawn at the right pixel size, so Kakao must not scale them again. */
    private fun style(image: Bitmap, anchorY: Float): LabelStyles =
        LabelStyles.from(LabelStyle.from(image).setAnchorPoint(0.5f, anchorY).setApplyDpScale(false))

    /** A crew car: a coloured dot with a white ring (24 design px). */
    private fun carDot(color: Int): Bitmap = markers.bitmap(24f, 24f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = MARKER_WHITE
        drawCircle(12f, 12f, 12f, paint)
        paint.color = color
        drawCircle(12f, 12f, 9f, paint)
    }

    /**
     * A Kakao label holds one image, so this draws [marker] with its name tag [gap] design px below the
     * marker's centre. Returns the image and where the marker's centre is (fraction of the height),
     * which becomes the point that sits on the map position.
     */
    private fun withTag(marker: Bitmap, gap: Float, text: String): Pair<Bitmap, Float> {
        val tag = markers.labelPill(text)
        val half = marker.height / 2f
        val tagTop = half + gap * ratio
        val width = maxOf(marker.width, tag.width)
        val height = maxOf(marker.height, (tagTop + tag.height).toInt())
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        // Explicit target rectangles, so Android doesn't rescale the images by their density.
        Canvas(image).apply {
            val tagLeft = (width - tag.width) / 2f
            drawBitmap(tag, null, RectF(tagLeft, tagTop, tagLeft + tag.width, tagTop + tag.height), null)
            val markerLeft = (width - marker.width) / 2f
            drawBitmap(marker, null, RectF(markerLeft, 0f, markerLeft + marker.width, marker.height.toFloat()), null)
        }
        return image to half / height
    }
}

private const val TAG = "CarDropKakaoMap"

/** Kakao's own place names are drawn larger than MapLibre's; this keeps them near our UI's text size. */
private const val KAKAO_TEXT_SCALE = 0.9f

/** Kakao zoom levels are whole numbers; 15 is about the MapLibre map's follow view. */
private const val FOLLOW_ZOOM = 15
private const val FOLLOW_TILT = 50.0

/** The blue route line, 9 wide as in the design. */
private const val LINE_WIDTH = 9f
/** The white road under it (18) and its grey edge on each side (4): 26 across, as in the design. */
private const val ROAD_WIDTH = 18f
private const val ROAD_EDGE = 4f
private const val ROAD_EDGE_GRAY = 0xFFCBD3DD.toInt()

private const val MARKER_LAYER = "cd-markers"
private const val RANK_CAR = 1L
private const val RANK_PIN = 2L
private const val RANK_ME = 3L
