package com.cardrop.navi

import com.cardrop.drive.NavArrow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RouteParsersTest {

    /** Shaped like the Kakao Mobility docs: origin -> waypoint -> destination = 2 sections. */
    private val kakaoJson = """
        {"trans_id":"t1","routes":[{"result_code":0,"result_msg":"길찾기 성공",
          "summary":{"distance":2000,"duration":180},
          "sections":[
            {"distance":880,"duration":60,
             "roads":[{"name":"경강로","distance":880,"duration":60,"traffic_speed":50.0,"traffic_state":4,
                       "vertexes":[128.88,37.76,128.89,37.76]}],
             "guides":[{"name":"출발지","x":128.88,"y":37.76,"distance":0,"duration":0,"type":100,"guidance":"출발지","road_index":0},
                       {"name":"경유지","x":128.89,"y":37.76,"distance":880,"duration":60,"type":1000,"guidance":"경유지","road_index":-1}]},
            {"distance":1990,"duration":120,
             "roads":[{"name":"경포로","distance":1990,"duration":120,"traffic_speed":60.0,"traffic_state":4,
                       "vertexes":[128.89,37.76,128.89,37.77,128.90,37.77]}],
             "guides":[{"name":"경포사거리","x":128.89,"y":37.77,"distance":1110,"duration":60,"type":2,"guidance":"우회전","road_index":0},
                       {"name":"목적지","x":128.90,"y":37.77,"distance":880,"duration":60,"type":101,"guidance":"목적지","road_index":-1}]}
          ]}]}
    """.trimIndent()

    @Test
    fun kakaoRouteJoinsSectionsAndFindsTheWaypoint() {
        val route = parseKakaoRoute(kakaoJson, hasWaypoint = true)
        assertEquals(4, route.geometry.points.size) // the shared point between sections is kept once
        assertEquals(0.88, route.waypointKm!!, 0.02)
        assertEquals(2.87, route.totalKm, 0.03) // 0.88 east + 1.11 north + 0.88 east
        assertEquals("카카오모빌리티", route.provider)
        assertEquals(60.0, route.secondsAt(route.waypointKm!!), 0.5)
        assertEquals(180.0, route.secondsAt(route.totalKm), 0.5)
    }

    @Test
    fun kakaoGuidesBecomeKoreanSteps() {
        val steps = parseKakaoRoute(kakaoJson, hasWaypoint = true).steps
        assertEquals(listOf(NavArrow.RIGHT, NavArrow.ARRIVE), steps.map { it.arrow })
        assertEquals("경포사거리 우회전", steps[0].text)
        assertEquals("경포로", steps[0].road)
        assertEquals(1.99, steps[0].km, 0.03)
        assertEquals("목적지 도착", steps[1].text)
    }

    @Test
    fun theStartRoadNamesTheFirstStretch() {
        assertEquals("경강로", parseKakaoRoute(kakaoJson, hasWaypoint = true).roadAt(0.1))
    }

    @Test
    fun kakaoErrorCodeThrows() {
        val json = """{"routes":[{"result_code":102,"result_msg":"시작 지점 주변의 도로를 탐색할 수 없음"}]}"""
        assertThrows(IOException::class.java) { parseKakaoRoute(json, hasWaypoint = false) }
    }

    @Test
    fun osrmRouteFromARealResponse() {
        // Real answer from router.project-osrm.org: 강릉 -> waypoint -> 경포해변 (5.9 km, 2 legs).
        val json = javaClass.getResource("/osrm_route.json")!!.readText()
        val route = parseOsrmRoute(json, hasWaypoint = true)
        assertEquals(5.86, route.totalKm, 0.3)
        assertEquals(1.145, route.waypointKm!!, 0.01)
        assertEquals(577.8, route.secondsAt(route.totalKm), 1.0)
        val texts = route.steps.map { it.text }
        assertTrue(texts.toString(), "명륜로 좌회전" in texts)
        assertTrue(texts.toString(), "경포로 우회전" in texts)
        assertEquals(NavArrow.ARRIVE, route.steps.last().arrow)
        assertEquals(route.steps.map { it.km }.sorted(), route.steps.map { it.km })
    }

    @Test
    fun kakaoTypeCodesMapToArrows() {
        assertEquals(NavArrow.LEFT, kakaoArrow(1))
        assertEquals(NavArrow.RIGHT, kakaoArrow(2))
        assertEquals(NavArrow.UTURN, kakaoArrow(3))
        assertEquals(NavArrow.RIGHT, kakaoArrow(9)) // 오른쪽에 고속 도로 출구
        assertEquals(NavArrow.STRAIGHT, kakaoArrow(301)) // 휴게소
    }
}
