package com.cardrop.drive

import android.content.Context
import com.cardrop.core.model.DropTopic
import com.cardrop.core.navigation.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owner: lead. What the driver can put in board 04's quick-drop slots (the live canvas's 퀵 드랍 편집,
 * `git show 1caa955:design/FinalApp.dc.html`, lines 302-344 and 947-969), in the editor's order.
 *
 * [key] is what this device saves (the canvas's names). [available] = false: cut this week; the editor shows
 * it greyed out and it can't be picked, like board 08's 같이 켜기.
 */
enum class QuickDrop(val key: String, val label: String, val available: Boolean = true) {
    REST("rest", "휴게소"),
    FOOD("food", "식사"),
    FUEL("fuel", "주유"),
    SHOUT("shout", "한마디"),
    /** The song drop (boards 12, 13): opens board 12 for the whole crew. */
    SONG("song", "노래"),
    GAME("game", "게임 초대"),
    /** 같이 켜기 (ads and points, boards 18, 19): cut. */
    TOGETHER("together", "같이 켜기", available = false),
    INBOX("inbox", "편지함"),
    /** Safety race, boards 16 and 17. Entry speech is attached by CarDropApp only for this slot. */
    SCORE("score", "운전 기록"),
    ;

    /**
     * 휴게소 / 식사 / 주유: their tile opens board 10 as a pop-up over board 04 (`QuickDropPicker`, for the whole
     * crew) with this topic, and the driver stays on 04 after sending. Null for the other tiles ([screen]).
     */
    fun popupTopic(): DropTopic? = when (this) {
        REST -> DropTopic.REST
        FOOD -> DropTopic.FOOD
        FUEL -> DropTopic.FUEL
        SHOUT, SONG, GAME, TOGETHER, INBOX, SCORE -> null
    }

    /**
     * Where the other tiles go: 한마디 board 09, 노래 board 12, 게임 초대 board 15, 편지함 board 07,
     * 운전 기록 boards 16/17. Null for the pop-up ones
     * ([popupTopic]) and the cut ones.
     */
    fun screen(): Screen? = when (this) {
        SHOUT -> Screen.Shout()
        SONG -> Screen.SongSend()
        GAME -> Screen.Games
        INBOX -> Screen.Inbox
        SCORE -> Screen.Safety()
        REST, FOOD, FUEL, TOGETHER -> null
    }
}

/** The quick-drop rules. Plain Kotlin, so QuickDropsTest covers them. */
object QuickDropRules {
    /** Board 04's first row has four slots. */
    const val SLOTS = 4

    /**
     * The most quick drops a driver can keep: the four slots and a second row under them (Member A, 2026-11-14;
     * the canvas stops at four). The second row holds the fifth drop on, and one dashed "+" slot to add another.
     */
    const val MAX_SLOTS = 8

    /** The editor's red line when every slot is taken (the canvas's words). */
    const val FULL_HINT = "기존 기능 하나를 해제하면 추가할 수 있어요."

    /** How many drops the driver can pick at all: the ones that aren't cut, at most [MAX_SLOTS]. */
    val pickable: Int get() = minOf(MAX_SLOTS, QuickDrop.entries.count { it.available })

    /** The drops under the first row: the fifth one on. */
    fun secondRow(chosen: List<QuickDrop>): List<QuickDrop> = chosen.drop(SLOTS)

    /**
     * True when board 04 shows the dashed "+" slot under the four: while the driver can still add a drop
     * (there is one left to pick). It opens the editor, like the empty slots of the first row.
     */
    fun showsAddSlot(chosen: List<QuickDrop>): Boolean = chosen.size < pickable

    /**
     * Slots before the driver saves a custom list. Explicitly saved and empty lists are preserved.
     */
    val DEFAULTS = listOf(QuickDrop.REST, QuickDrop.FUEL, QuickDrop.SCORE)

    /**
     * The drops for these saved [keys], in their order: only ones that exist and aren't cut, each once, at most
     * [MAX_SLOTS].
     */
    fun fromKeys(keys: List<String>): List<QuickDrop> =
        keys
            .mapNotNull { key -> QuickDrop.entries.firstOrNull { it.key == key } }
            .filter { it.available }
            .distinct()
            .take(MAX_SLOTS)

    /** What a tap in the editor does. */
    sealed interface Pick {
        /** The new list (in slot order). */
        data class Changed(val drops: List<QuickDrop>) : Pick

        /** Every slot is taken: nothing changes, and the editor shows [FULL_HINT]. */
        data object Full : Pick
    }

    /**
     * Taps [drop] in the editor: a picked drop comes off (the ones after it move up), a new one goes in the next
     * free slot (the fifth one starts the second row). A cut drop changes nothing. [max] = how many fit.
     */
    fun pick(draft: List<QuickDrop>, drop: QuickDrop, max: Int = MAX_SLOTS): Pick = when {
        drop in draft -> Pick.Changed(draft - drop)
        !drop.available -> Pick.Changed(draft)
        draft.size >= max -> Pick.Full
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
