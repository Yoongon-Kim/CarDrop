package com.cardrop.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Kakao Local keyword search JSON -> board 10's suggestions. */
class KakaoPlaceSearchTest {

    /** Shaped like a real answer for "덕평" (trimmed): one with a road address, one without, one broken. */
    private val json = """
        {"documents":[
          {"address_name":"경기 이천시 마장면 각평리 319","category_group_code":"","category_group_name":"",
           "category_name":"교통,수송 > 휴게소 > 고속도로휴게소","distance":"886","id":"8256624","phone":"031-645-0001",
           "place_name":"덕평자연휴게소 양방향","place_url":"http://place.map.kakao.com/8256624",
           "road_address_name":"경기 이천시 마장면 덕이로154번길 287-76","x":"127.3898467270533","y":"37.241339424505625"},
          {"address_name":"경기 이천시 마장면 덕평리","category_name":"교통,수송 > 도로시설 > IC > 고속도로IC","distance":"",
           "id":"27353044","place_name":"덕평IC","road_address_name":"","x":"127.365167955447","y":"37.2414577437089"},
          {"address_name":"어딘가","category_name":"","id":"1","place_name":"좌표 없음","x":"","y":""}
        ],"meta":{"is_end":false,"pageable_count":45,"total_count":120}}
    """.trimIndent()

    @Test
    fun readsNameLocationCategoryAddressAndDistance() {
        val places = parseKakaoPlaces(json)
        assertEquals(2, places.size) // the one without coordinates can't become a waypoint
        val stop = places[0]
        assertEquals("kakao-8256624", stop.place.id)
        assertEquals("덕평자연휴게소 양방향", stop.place.name)
        assertEquals(37.241339, stop.place.lat, 1e-6)
        assertEquals(127.389847, stop.place.lng, 1e-6)
        assertEquals("고속도로휴게소", stop.category)
        assertEquals("경기 이천시 마장면 덕이로154번길 287-76", stop.address)
        assertEquals(stop.address, stop.place.detail)
        assertEquals(0.886, stop.distanceKm!!, 1e-9)
    }

    @Test
    fun noRoadAddressUsesTheLotAddressAndNoDistanceIsNull() {
        val ic = parseKakaoPlaces(json)[1]
        assertEquals("경기 이천시 마장면 덕평리", ic.address)
        assertEquals("고속도로IC", ic.category)
        assertNull(ic.distanceKm)
    }

    @Test
    fun noResultsIsAnEmptyList() {
        assertEquals(emptyList<PlaceSuggestion>(), parseKakaoPlaces("""{"documents":[],"meta":{"total_count":0}}"""))
    }
}
