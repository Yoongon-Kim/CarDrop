package com.cardrop.navi

import com.cardrop.core.model.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class NaverMapHandoffTest {

    private val gyeongpo = Place("gyeongpo", "강릉 경포해변", 37.8055, 128.9081)
    private val deokpyeong = Place("deokpyeong", "덕평자연휴게소", 37.24, 127.437)

    @Test
    fun `destination only`() {
        assertEquals(
            "nmap://navigation?dlat=37.805500&dlng=128.908100" +
                "&dname=%EA%B0%95%EB%A6%89%20%EA%B2%BD%ED%8F%AC%ED%95%B4%EB%B3%80&appname=com.cardrop",
            NaverMapHandoff.buildUrl(gyeongpo, null, "com.cardrop"),
        )
    }

    @Test
    fun `waypoint goes in v1 before appname`() {
        val url = NaverMapHandoff.buildUrl(gyeongpo, deokpyeong, "com.cardrop")
        val keys = url.substringAfter("?").split("&").map { it.substringBefore("=") }
        assertEquals(listOf("dlat", "dlng", "dname", "v1lat", "v1lng", "v1name", "appname"), keys)
    }
}
