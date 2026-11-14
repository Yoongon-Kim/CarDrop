package com.cardrop.drop

import com.cardrop.core.model.DropTopic

/**
 * Owner: Member A (2026-11-15). Board 08's own tiles: the decision row's second page has 카페 and up to [MAX] tiles the
 * driver picks, like 04's quick drops. A "+" slot shows while one is left; tapping it offers the kinds not picked yet,
 * and a long press on a picked tile takes it out again. Each opens board 10 with the AI's picks. They stay on this
 * device ([DropSlots]). Pure Kotlin, unit tested (DropSlotRulesTest).
 */
enum class PlaceKind(val key: String, val topic: DropTopic, val label: String, val sub: String) {
    DROWSY("drowsy", DropTopic.DROWSY, "졸음쉼터", "AI 3곳 추천"),
    STORE("store", DropTopic.STORE, "편의점", "근처 AI 추천"),
    EV("ev", DropTopic.EV, "전기차 충전", "근처 AI 추천"),
}

object DropSlotRules {
    /** 카페 and these fill the second page's four places. */
    const val MAX = 3

    /** [saved] with [kind] added at the end (once, and only while there is room). */
    fun add(saved: List<PlaceKind>, kind: PlaceKind): List<PlaceKind> =
        if (kind in saved || saved.size >= MAX) saved else saved + kind

    fun remove(saved: List<PlaceKind>, kind: PlaceKind): List<PlaceKind> = saved - kind

    /** What the "+" slot offers: the kinds not picked yet. */
    fun choices(saved: List<PlaceKind>): List<PlaceKind> = PlaceKind.entries.filterNot { it in saved }

    /** True while the "+" slot shows. */
    fun showsAdd(saved: List<PlaceKind>): Boolean = saved.size < MAX && choices(saved).isNotEmpty()

    fun encode(saved: List<PlaceKind>): String = saved.joinToString(",") { it.key }

    /** Back from [encode]; unknown keys (an older or newer build) are skipped. Nothing saved = no tiles yet. */
    fun decode(text: String?): List<PlaceKind> =
        text.orEmpty().split(',').mapNotNull { key -> PlaceKind.entries.firstOrNull { it.key == key } }.distinct().take(MAX)
}
