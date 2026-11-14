package com.cardrop.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cardrop.core.model.DropTopic

/**
 * Every screen in week 1. The number is the design board (design/FinalApp.dc.html, F01..F19),
 * and the owner is who builds it.
 */
sealed interface Screen {
    /** 01 크루 만들기 · Member A */
    data object CreateCrew : Screen
    /** 02 목적지 검색 · Member A */
    data object DestinationSearch : Screen
    /** 03 코드로 참여 · Member A */
    data object JoinCrew : Screen
    /** 04 주행 메인 · Member B */
    data object Drive : Screen
    /**
     * 04's 퀵 드랍 편집: which drops go in 04's four quick slots · lead. Not in design/FinalApp.dc.html; built from
     * the live canvas (`quickEdit`, drive/QuickDropEditScreen.kt).
     */
    data object QuickDropEdit : Screen
    /** 05 연료 경고 (내 차) · Member B */
    data object FuelMe : Screen
    /** 06 연료 알림 받은 차 · Member B */
    data class FuelThem(val dropId: String) : Screen
    /** 07 드랍 편지함 · Member A */
    data object Inbox : Screen
    /** 08 드랍 보내기 · Member A */
    data object DropSend : Screen
    /**
     * 09 일행에게 한마디 · lead. [initialText] comes from a voice suggestion. [toCarIds]: the cars chosen
     * on board 08; empty = the whole crew.
     */
    data class Shout(val initialText: String = "", val toCarIds: List<String> = emptyList()) : Screen
    /** 10 AI 추천 · lead. [toCarIds]: the cars chosen on board 08; empty = the whole crew. */
    data class AiRecommend(val topic: DropTopic, val toCarIds: List<String> = emptyList()) : Screen
    /**
     * 12 노래 드랍 보내기 · Member A. [toCarIds]: the cars chosen on board 08; empty = the whole crew (04's quick drop).
     */
    data class SongSend(val toCarIds: List<String> = emptyList()) : Screen
    /** 13 노래 받기: a pop-up over board 04 for another car's song drop · Member A */
    data class SongGet(val dropId: String) : Screen
    /** 11 투표 · Member A */
    data class Vote(val dropId: String) : Screen
    /** 14 결정 · Member B */
    data class Decided(val dropId: String) : Screen
    /** 15 게임 (정차 중, 지역 퀴즈) · lead */
    data object Games : Screen
}

/**
 * Minimal navigation: one current screen plus a back stack. No library to learn.
 * The app's one navigator lives in AppContainer, not in a screen's `remember`, so a new activity
 * (day/night theme, language, a second MainActivity on top) keeps the driver on the same screen.
 */
class AppNavigator(start: Screen = Screen.CreateCrew) {
    var current: Screen by mutableStateOf(start)
        private set

    private val backStack = ArrayDeque<Screen>()

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun go(screen: Screen) {
        if (screen == current) return
        backStack.addLast(current)
        current = screen
    }

    /** Jumps to a rail tab and forgets the history. */
    fun switchTab(screen: Screen) {
        backStack.clear()
        current = screen
    }

    fun back() {
        current = backStack.removeLastOrNull() ?: Screen.Drive
    }
}
