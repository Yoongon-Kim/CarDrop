package com.cardrop.drive

import android.content.Context
import com.cardrop.core.model.DropTopic
import com.cardrop.core.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner: lead. What the driver can put in board 04's four quick-drop slots (the live canvas's 퀵 드랍 편집,
 * `git show 1caa955:design/FinalApp.dc.html`, lines 302-344 and 947-969), in the editor's order.
 *
 * [key] is what this device saves (the canvas's names). [available] = false: cut this week; the editor shows
 * it greyed out and it can't be picked, like board 08's 노래 드랍 and 같이 켜기.
 */
enum class QuickDrop(val key: String, val label: String, val available: Boolean = true) {
    REST("rest", "휴게소"),
    FOOD("food", "식사"),
    FUEL("fuel", "주유"),
    SHOUT("shout", "한마디"),
    /** The song drop (boards 12, 13): cut. */
    SONG("song", "노래", available = false),
    GAME("game", "게임 초대"),
    /** 같이 켜기 (ads and points, boards 18, 19): cut. */
    TOGETHER("together", "같이 켜기", available = false),
    INBOX("inbox", "편지함"),
    /** 운전 기록 (the safety race, boards 16, 17): cut. */
    SCORE("score", "운전 기록", available = false),
    ;

    /**
     * Where its tile on board 04 goes: 휴게소 / 식사 / 주유 open board 10 for the whole crew (no pop-up, as before),
     * 한마디 board 09, 게임 초대 board 15, 편지함 board 07. Null for the cut ones.
     */
    fun screen(): Screen? = when (this) {
        REST -> Screen.AiRecommend(DropTopic.REST)
        FOOD -> Screen.AiRecommend(DropTopic.FOOD)
        FUEL -> Screen.AiRecommend(DropTopic.FUEL)
        SHOUT -> Screen.Shout()
        GAME -> Screen.Games
        INBOX -> Screen.Inbox
        SONG, TOGETHER, SCORE -> null
    }
}

/** The quick-drop rules. Plain Kotlin, so QuickDropsTest covers them. */
object QuickDropRules {
    /** Board 04 has four slots. */
    const val SLOTS = 4

    /** The editor's red line when all four slots are taken (the canvas's words). */
    const val FULL_HINT = "기존 기능 하나를 해제하면 추가할 수 있어요."

    /**
     * Board 04's slots until the driver saves their own list: 휴게소 and 주유, the other two empty (the lead's choice,
     * 2026-11-14; the canvas starts with all four empty).
     */
    val DEFAULTS = listOf(QuickDrop.REST, QuickDrop.FUEL)

    /**
     * The drops for these saved [keys], in their order: only ones that exist and aren't cut, each once, at most
     * [SLOTS].
     */
    fun fromKeys(keys: List<String>): List<QuickDrop> =
        keys
            .mapNotNull { key -> QuickDrop.entries.firstOrNull { it.key == key } }
            .filter { it.available }
            .distinct()
            .take(SLOTS)

    /** What a tap in the editor does. */
    sealed interface Pick {
        /** The new list (in slot order). */
        data class Changed(val drops: List<QuickDrop>) : Pick

        /** All four slots are taken: nothing changes, and the editor shows [FULL_HINT]. */
        data object Full : Pick
    }

    /**
     * Taps [drop] in the editor: a picked drop comes off (the ones after it move up), a new one goes in the next
     * free slot. A cut drop changes nothing.
     */
    fun pick(draft: List<QuickDrop>, drop: QuickDrop): Pick = when {
        drop in draft -> Pick.Changed(draft - drop)
        !drop.available -> Pick.Changed(draft)
        draft.size >= SLOTS -> Pick.Full
        else -> Pick.Changed(draft + drop)
    }

    /** How the device keeps the list: the keys with commas between them, e.g. "rest,shout"; an emptied list is "". */
    fun encode(drops: List<QuickDrop>): String = drops.joinToString(",") { it.key }

    /** The saved list; nothing saved yet ([saved] = null) = [DEFAULTS]. A list the driver emptied stays empty. */
    fun decode(saved: String?): List<QuickDrop> = if (saved == null) DEFAULTS else fromKeys(saved.split(","))
}

/** Where board 04's quick drops are kept (like board 09's phrases in [com.cardrop.ai.PhraseStore]). */
interface QuickDropStore {
    fun load(): List<QuickDrop>
    fun save(drops: List<QuickDrop>)
}

/** Keeps the quick drops in SharedPreferences, Android's built-in key-value storage on the device. */
class PrefsQuickDropStore(context: Context) : QuickDropStore {
    private val prefs = context.applicationContext.getSharedPreferences("cardrop", Context.MODE_PRIVATE)

    override fun load(): List<QuickDrop> = QuickDropRules.decode(prefs.getString(KEY, null))

    override fun save(drops: List<QuickDrop>) {
        prefs.edit().putString(KEY, QuickDropRules.encode(drops)).apply()
    }

    private companion object {
        const val KEY = "quickDrops.v1"
    }
}

/**
 * This device's quick drops: read once when the app starts, replaced by the editor's 홈에 저장하기. It lives in
 * AppContainer, so board 04 and the editor share it and a new activity keeps it.
 */
class QuickDrops(private val store: QuickDropStore) {
    private val current = MutableStateFlow(store.load())

    /** The drops in board 04's slots, in order; the slots after them are empty. */
    val chosen: StateFlow<List<QuickDrop>> = current.asStateFlow()

    fun save(drops: List<QuickDrop>) {
        val clean = QuickDropRules.fromKeys(drops.map { it.key })
        current.value = clean
        store.save(clean)
    }
}
