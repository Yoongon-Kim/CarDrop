package com.cardrop.drive

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.CarPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoRouteTest {

    @Test
    fun decodesGoogleEncodedPolyline() {
        // Example from Google's polyline documentation.
        val points = decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(listOf(GeoPoint(38.5, -120.2), GeoPoint(40.7, -120.95), GeoPoint(43.252, -126.453)), points)
    }

    @Test
    fun routeRunsFromSinchonToGyeongpo() {
        assertTrue("total ${DemoRoute.totalKm}", DemoRoute.totalKm in 240.0..250.0)
        val start = DemoRoute.points.first()
        val end = DemoRoute.points.last()
        assertTrue(haversineKm(start, GeoPoint(DemoData.seoul.lat, DemoData.seoul.lng)) < 0.5)
        assertTrue(haversineKm(end, GeoPoint(DemoData.gyeongpo.lat, DemoData.gyeongpo.lng)) < 0.5)
    }

    @Test
    fun restStopsLieOnTheRouteInDrivingOrder() {
        val kms = DemoData.restStops.map { stop ->
            val km = DemoRoute.kmOf(stop)
            val onRoute = DemoRoute.pointAt(km)
            val offRoute = haversineKm(GeoPoint(stop.lat, stop.lng), GeoPoint(onRoute.lat, onRoute.lng))
            assertTrue("${stop.name} is ${offRoute}km off the route", offRoute < 0.5)
            km
        }
        assertEquals(kms.sorted(), kms)
    }

    @Test
    fun kmOfIsTheInverseOfPointAt() {
        listOf(10.0, 65.0, 150.0, 230.0).forEach { km ->
            val p = DemoRoute.pointAt(km)
            assertEquals(km, DemoRoute.kmOf(p.lat, p.lng), 0.05)
        }
    }

    @Test
    fun yeongdongExpresswayHeadsEast() {
        // Overall direction over 50 km (single segments can point anywhere on a winding road).
        val from = DemoRoute.pointAt(90.0)
        val to = DemoRoute.pointAt(140.0)
        val bearing = bearingDegrees(GeoPoint(from.lat, from.lng), GeoPoint(to.lat, to.lng))
        assertTrue("bearing $bearing", bearing in 30.0..120.0)
        assertEquals("영동고속도로", DemoRoute.roadAt(100.0))
    }

    @Test
    fun placesAheadSkipsStopsTheFrontCarHasPassedOrWillSoon() {
        val front = carAt(70.0) // past 덕평 (65 km); 여주 (82 km) is closer than the 25 km margin
        val ahead = DemoRoute.placesAhead(DemoData.restStops, listOf(carAt(40.0), front))
        assertEquals(listOf("munmak", "hoengseong", "pyeongchang", "gangneung-rest"), ahead.map { it.id })
    }

    private fun carAt(km: Double): CarPosition {
        val p = DemoRoute.pointAt(km)
        return CarPosition("car", p.lat, p.lng, (DemoRoute.totalKm - km).toFloat(), etaMinutes = 0)
    }
}
