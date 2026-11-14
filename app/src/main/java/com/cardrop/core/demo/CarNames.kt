package com.cardrop.core.demo

import android.content.Context

/**
 * Owner: lead. The 4 demo cars' names. The start question's pencils (StartModeScreen) rename a car; the new name
 * shows on every screen of this device, the bot crew's included ([DemoData] builds its cars with [of]), and stays
 * after the app restarts (SharedPreferences, like [DemoCar]'s pick). Kept on this device only (user's choice,
 * 2026-11-15): in the real crew another device keeps its own names. A car never renamed has the design's name.
 * Clearing the app's data brings the design's names back.
 */
object CarNames {
    /** A typed name longer than this is cut off. */
    const val MAX_LENGTH = 10

    /** The design's names by car id, in crew order. */
    val DEFAULTS: Map<String, String> = linkedMapOf(
        "car-dad" to "아빠 차",
        "car-uncle" to "삼촌 차",
        "car-minji" to "민지 차",
        "car-aunt" to "이모 차",
    )

    /** The renamed cars only (car id -> name). Read once at app start ([load]); unit tests see none. */
    @Volatile
    private var custom: Map<String, String> = emptyMap()

    /** [carId]'s name: the one typed on this device, else the design's. */
    fun of(carId: String): String = custom[carId] ?: DEFAULTS[carId] ?: carId

    /** Has [carId] been renamed on this device? */
    fun isRenamed(carId: String): Boolean = carId in custom

    /** Reads the saved names. CarDropApplication calls it once, before any screen or crew exists. */
    fun load(context: Context) {
        val prefs = demoPrefs(context)
        custom = DEFAULTS.keys.mapNotNull { id ->
            prefs.getString(PREF_PREFIX + id, null)?.let(::clean)?.let { id to it }
        }.toMap()
    }

    /** Saves [typed] as [carId]'s name ([clean] first); a blank name or the design's name gives the design's back. */
    fun rename(context: Context, carId: String, typed: String) {
        require(carId in DEFAULTS) { "unknown car $carId" }
        val name = clean(typed)?.takeIf { it != DEFAULTS[carId] }
        val edit = demoPrefs(context).edit()
        if (name == null) edit.remove(PREF_PREFIX + carId) else edit.putString(PREF_PREFIX + carId, name)
        edit.apply()
        custom = if (name == null) custom - carId else custom + (carId to name)
    }

    /**
     * What a typed name becomes: spaces squeezed, no brackets (the app adds " (나)" to this car's name and cuts names
     * at " ("), at most [MAX_LENGTH] letters. Null = nothing left, i.e. the design's name.
     */
    fun clean(typed: String): String? =
        typed.filterNot { it == '(' || it == ')' }
            .trim()
            .replace(Regex("\\s+"), " ")
            .take(MAX_LENGTH)
            .trimEnd()
            .ifEmpty { null }

    private const val PREF_PREFIX = "carName."
}
