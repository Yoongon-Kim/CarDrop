package com.cardrop.core.demo

import com.cardrop.core.model.Car
import com.cardrop.core.model.CarColor
import com.cardrop.core.model.Place

/**
 * Fixed data for the demo scenario in the design: a family trip from Seoul to 강릉 경포해변
 * on the 영동고속도로 with 4 cars. Coordinates are approximate.
 */
object DemoData {

    val seoul = Place("seoul", "서울 신촌역", 37.5552, 126.9368)

    val gyeongpo = Place(
        id = "gyeongpo",
        name = "강릉 경포해변",
        lat = 37.8055,
        lng = 128.9081,
        detail = "강원 강릉시 · 약 230km · 2시간 40분",
    )

    val destinations = listOf(
        gyeongpo,
        Place("anmok", "강릉 안목해변", 37.7717, 128.9478, "강원 강릉시 · 커피거리"),
        Place("sokcho", "속초 해수욕장", 38.1906, 128.6036, "강원 속초시"),
        Place("yangyang", "양양 서피비치", 38.0303, 128.7176, "강원 양양군"),
    )

    /**
     * Rest stops on the 영동고속도로 (Seoul -> Gangneung), the 강릉-bound side, positions from OpenStreetMap.
     * They lie on the demo route (drive/DemoRoute.kt), km from its start: 65, 82, 114, 148, 182, 226.
     * The AI picks candidates from these.
     */
    val restStops = listOf(
        Place("deokpyeong", "덕평자연휴게소", 37.24158, 127.38902, "주유소 · 푸드코트 · 반려견 놀이터"),
        Place("yeoju", "여주휴게소", 37.23844, 127.56911, "주유소 · 푸드코트"),
        Place("munmak", "문막휴게소", 37.33518, 127.85817, "주유소 · 전기차 충전"),
        Place("hoengseong", "횡성휴게소", 37.46308, 128.13487, "주유소 · 한우 맛집"),
        Place("pyeongchang", "평창휴게소", 37.60574, 128.45341, "주유소 · 산채비빔밥"),
        Place("gangneung-rest", "강릉대관령휴게소", 37.75792, 128.80617, "주유소 · 바다 전망"),
    )

    /**
     * Cafés near the destination (강릉 경포해변), for the 카페 drop. Real places with positions from
     * OpenStreetMap (nodes 9704377417, 11119092279, 4452773889), named by area + their OSM name.
     * Distances are straight-line from 경포해변.
     */
    val cafes = listOf(
        Place("cafe-gangmun-starbucks", "강문 스타벅스", 37.794674, 128.918500, "강문해변 앞 · 경포해변에서 1.5km"),
        Place("cafe-gangmun-denken", "강문 덴켄", 37.794832, 128.918466, "강문해변 앞 · 경포해변에서 1.5km"),
        Place("cafe-anmok-bossanova", "안목 보사노바", 37.772055, 128.947908, "안목해변 커피거리 · 경포해변에서 5km"),
    )

    val me = Car("car-dad", "아빠 차 (나)", CarColor.BLUE, isMe = true)
    val uncle = Car("car-uncle", "삼촌 차", CarColor.PURPLE, isBot = true)
    val minji = Car("car-minji", "민지 차", CarColor.YELLOW, isBot = true)
    val aunt = Car("car-aunt", "이모 차", CarColor.TEAL, isBot = true)

    const val DEMO_CREW_CODE = "482917"
    const val DEMO_CREW_NAME = "강릉 여행"
}
