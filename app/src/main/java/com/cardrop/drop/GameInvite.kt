package com.cardrop.drop

import com.cardrop.core.model.Drop
import com.cardrop.core.model.DropKind
import com.cardrop.core.model.DropTopic
import com.cardrop.crew.DropDraft

/**
 * Owner: Member A (2026-11-15). A 게임 초대 travels as a SOCIAL drop ([DropTopic.GAME]) next to the game itself:
 * - an omok 도전장 (`OmokCoordinator.invite`): the game's id in `message`, so the pop-up can accept or turn down that game;
 * - a quiz 도전장 (board 15's 도전장 드랍): no game id, the pop-up's 수락 opens the games.
 * A car in P gets the pop-up ([GameInvitePopup]); a car that is driving only finds it in its inbox. Never spoken.
 * Pure Kotlin, unit tested (GameInviteRulesTest).
 */
object GameInviteRules {
    /** How long an invite asks for an answer: the omok 도전장's minute (`OMOK_INVITE_MS`). */
    const val ANSWER_MILLIS = 60_000L

    /** The drop that goes with an omok 도전장 for [opponentId]. */
    fun omok(gameId: String, opponentId: String): DropDraft = DropDraft(
        kind = DropKind.SOCIAL,
        topic = DropTopic.GAME,
        title = "오목 한 판 할까요?",
        message = gameId,
        toCarIds = listOf(opponentId),
    )

    /** The drop of a quiz 도전장 ([gameTitle] e.g. "상식 퀴즈"), to [toCarIds] (empty = the whole crew). */
    fun quiz(gameTitle: String, toCarIds: List<String>): DropDraft = DropDraft(
        kind = DropKind.SOCIAL,
        topic = DropTopic.GAME,
        title = "$gameTitle 한 판 할까요?",
        toCarIds = toCarIds,
    )

    /** The omok game a 게임 초대 is for, or null for a quiz 도전장 (or any other drop). */
    fun omokGameId(drop: Drop): String? = drop.message.takeIf { drop.topic == DropTopic.GAME && it.isNotBlank() }

    /** True while [drop] still waits for an answer: a minute from when it was sent. */
    fun open(drop: Drop, nowMillis: Long): Boolean = nowMillis - drop.createdAtMillis < ANSWER_MILLIS

    /**
     * True when [drop] opens the pop-up as it arrives: a 게임 초대 from another car, only when this car can play
     * ([gamesOpen]: in P and stopped). Driving, it stays in the inbox, quietly.
     */
    fun popsUp(drop: Drop, gamesOpen: Boolean, nowMillis: Long): Boolean =
        drop.kind == DropKind.SOCIAL && drop.topic == DropTopic.GAME && gamesOpen && open(drop, nowMillis)
}
