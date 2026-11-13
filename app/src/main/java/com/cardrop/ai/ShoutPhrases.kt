package com.cardrop.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONException

/**
 * Owner: lead. Board 09's phrase list (design: 자주 쓰는 한마디): the default phrases, and the rules for
 * adding, changing and deleting one. Plain Kotlin, so ShoutPhrasesTest covers it. The list is kept on
 * the device by a [PhraseStore].
 */
object ShoutPhrases {
    const val MAX_LENGTH = 80

    /** The design's 6 phrases (target line 689), used until the driver changes the list. */
    val DEFAULTS = listOf(
        "먼저 가세요, 따라갈게요",
        "천천히 와요~",
        "다음 휴게소에서 봐요",
        "뒤에 사고 났어요, 조심!",
        "곧 도착해요",
        "배고파요, 밥 먹고 가요",
    )

    /** The saved list, or [DEFAULTS] when nothing usable was saved. A list the driver emptied stays empty. */
    fun fromSaved(saved: List<String>?): List<String> =
        saved?.takeIf { list -> list.all { it.isNotBlank() && it.length <= MAX_LENGTH } } ?: DEFAULTS

    sealed interface SaveResult {
        /** The new list and which phrase is chosen now. */
        data class Saved(val phrases: List<String>, val selected: Int) : SaveResult

        /** Shown in red in the editor. */
        data class Problem(val message: String) : SaveResult
    }

    /**
     * Saves the editor's [text] as a new phrase at the end ([editing] = null) or over the phrase at
     * [editing]. Empty text, or text already in the list, is refused, like in the design.
     */
    fun save(phrases: List<String>, editing: Int?, text: String): SaveResult {
        val value = text.trim().take(MAX_LENGTH)
        if (value.isEmpty()) return SaveResult.Problem("문구를 입력해주세요.")
        if (phrases.withIndex().any { (i, phrase) -> phrase == value && i != editing }) {
            return SaveResult.Problem("이미 있는 문구예요.")
        }
        return if (editing == null) {
            SaveResult.Saved(phrases + value, selected = phrases.size)
        } else {
            SaveResult.Saved(phrases.mapIndexed { i, phrase -> if (i == editing) value else phrase }, selected = editing)
        }
    }

    /** Deletes the phrase at [index]. The phrase that moves into its place (or the new last one) is chosen. */
    fun delete(phrases: List<String>, index: Int): SaveResult.Saved {
        val next = phrases.filterIndexed { i, _ -> i != index }
        return SaveResult.Saved(next, selected = index.coerceAtMost(next.size - 1).coerceAtLeast(0))
    }

    fun encode(phrases: List<String>): String = JSONArray(phrases).toString()

    /** Null when [json] is missing or isn't a list of texts. */
    fun decode(json: String?): List<String>? {
        if (json == null) return null
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }
        } catch (e: JSONException) {
            null
        }
    }
}

/** Where board 09's phrases are kept. */
interface PhraseStore {
    fun load(): List<String>?
    fun save(phrases: List<String>)
}

/** Keeps the phrases in SharedPreferences, Android's built-in key-value storage on the device. */
class PrefsPhraseStore(context: Context) : PhraseStore {
    private val prefs = context.applicationContext.getSharedPreferences("cardrop", Context.MODE_PRIVATE)

    override fun load(): List<String>? = ShoutPhrases.decode(prefs.getString(KEY, null))

    override fun save(phrases: List<String>) {
        prefs.edit().putString(KEY, ShoutPhrases.encode(phrases)).apply()
    }

    private companion object {
        const val KEY = "shouts.v1"
    }
}
