package com.cardrop.navi

import com.cardrop.core.demo.DemoData
import com.cardrop.core.model.NavRoute
import com.cardrop.drive.GeoPoint
import com.cardrop.drive.NavArrow
import com.cardrop.drive.NavStep
import com.cardrop.drive.RouteGeometry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class NavPanelStateTest {

    private val zone = ZoneId.of("Asia/Seoul")
    private val noon = 1_794_884_400_000L // 2026-11-17 12:00 KST
    private val yeoju = DemoData.restStops.first { it.id == "yeoju" }

    /** 20 km east along latitude 37.5; waypoint at km 10; a right turn at km 15; 1 km per minute. */
    private val road = RouteGeometry((0..20).map { GeoPoint(37.5, 127.0 + it * 0.01131) })
    private val live = LiveRoute(
        geometry = road,
        steps = listOf(NavStep(15.0, NavArrow.RIGHT, "경포로 우회전", "경포로")),
        timeline = listOf(road.totalKm to road.totalKm * 60),
        waypointKm = 10.0,
        provider = "카카오모빌리티",
    )
    private val toGyeongpo = NavRoute(DemoData.gyeongpo, yeoju)

    private fun guiding(km: Double, offRoute: Boolean = false): NavigatorState {
        val p = road.pointAt(km)
        return NavigatorState(
            status = NavStatus.GUIDING,
            fix = GpsFix(p.lat, p.lng, speedKmh = 98.6, bearing = 90.0, timeMillis = 0),
            route = live,
            progressKm = km,
            snapped = p,
            offRoute = offRoute,
        )
    }

    @Test
    fun bannerPointsAtTheWaypointWhenItComesFirst() {
        val state = navPanelState(toGyeongpo, guiding(8.8), guiding = true, nowMillis = noon, zone = zone)
        assertEquals(NavBanner(NavArrow.RIGHT, "1.2 km", "여주휴게소 진입"), state.banner)
        assertEquals(99, state.speedKmh)
        assertEquals("카카오모빌리티", state.provider)
    }

    @Test
    fun afterTheWaypointTheNextTurnIsShown() {
        val state = navPanelState(NavRoute(DemoData.gyeongpo), guiding(14.65), guiding = true, nowMillis = noon, zone = zone)
        assertEquals(NavBanner(NavArrow.RIGHT, "350 m", "경포로 우회전"), state.banner)
    }

    @Test
    fun longStraightShowsTheRoadName() {
        val state = navPanelState(NavRoute(DemoData.gyeongpo), guiding(15.5), guiding = true, nowMillis = noon, zone = zone)
        assertEquals(NavArrow.STRAIGHT, state.banner.arrow)
        assertEquals("경포로 따라 직진", state.banner.text)
    }

    @Test
    fun arrivalTimesFollowTheRouteTimeline() {
        val state = navPanelState(toGyeongpo, guiding(3.9), guiding = true, nowMillis = noon, zone = zone)
        // 1 minute per km: waypoint 6.1 km away (12:06:06), destination ~16.05 km away (12:16:03).
        assertEquals(EtaRow("여주휴게소", "12:06"), state.waypoint)
        assertEquals("12:16", state.destination?.eta)
    }

    @Test
    fun atTheDestinationItsTimeSaysArrived() {
        val there = guiding(road.totalKm).copy(arrived = true)
        val state = navPanelState(NavRoute(DemoData.gyeongpo), there, guiding = true, nowMillis = noon, zone = zone)
        assertEquals(EtaRow(DemoData.gyeongpo.name, "도착"), state.destination)
        assertEquals("목적지에 도착했어요", state.banner.text)
    }

    @Test
    fun parkedAtTheStopItsRowSaysSo() {
        val parked = guiding(10.0).copy(parkedAt = yeoju)
        val state = navPanelState(NavRoute(DemoData.gyeongpo), parked, guiding = true, nowMillis = noon, zone = zone)
        assertEquals(EtaRow("여주휴게소 · 정차 중", "P"), state.waypoint)
    }

    @Test
    fun offRouteSaysSo() {
        val state = navPanelState(toGyeongpo, guiding(4.0, offRoute = true), guiding = true, nowMillis = noon, zone = zone)
        assertEquals("경로를 벗어났어요 · 새 경로를 찾는 중", state.banner.text)
    }

    @Test
    fun withoutGpsTheBannerSaysSo() {
        val state = navPanelState(toGyeongpo, NavigatorState(), guiding = true, nowMillis = noon, zone = zone)
        assertEquals("GPS 신호를 찾고 있어요", state.banner.text)
        assertEquals("", state.waypoint?.eta)
    }

    @Test
    fun withoutDestinationTheBannerAsksForOne() {
        val state = navPanelState(NavRoute(), NavigatorState(), guiding = false, nowMillis = noon, zone = zone)
        assertEquals("목적지를 정하면 길을 안내해요", state.banner.text)
        assertNull(state.destination)
    }

    @Test
    fun formatsDistances() {
        assertEquals("350 m", formatKm(0.34))
        assertEquals("4.2 km", formatKm(4.21))
        assertEquals("68 km", formatKm(67.6))
    }
}
