package com.cardrop.core.demo

import android.content.Context
import android.content.SharedPreferences
import com.cardrop.BuildConfig

/**
 * Owner: lead. Bot crew or real crew on this device, for the demo.
 *
 * `cardrop.useRealCrew` in local.properties is the default. The DEMO panel's "크루" switch picks the other
 * one on this device and restarts the app, because AppContainer picks the crew once, when the app starts.
 * The device keeps the pick until `cardrop.useRealCrew` itself changes: after editing local.properties and
 * installing again, local.properties decides again. The real crew needs the Firebase settings
 * (app/google-services.json) in the build; a build without them has only the bot crew.
 */
object DemoCrew {
    /**
     * Real crew? The DEMO panel's [picked] crew (null = never picked), but only while local.properties still
     * says what it said at the pick ([defaultWhenPicked] == [buildDefault]); otherwise local.properties.
     * Without the build's Firebase settings ([hasFirebase] false) there is only the bot crew.
     */
    fun resolve(picked: Boolean?, defaultWhenPicked: Boolean?, buildDefault: Boolean, hasFirebase: Boolean): Boolean {
        val pick = if (defaultWhenPicked == buildDefault) picked else null
        return hasFirebase && (pick ?: buildDefault)
    }

    /** Real crew on this device now? AppContainer asks once, when the app starts. */
    fun current(context: Context): Boolean {
        val prefs = demoPrefs(context)
        val picked = prefs.booleanOrNull(PICKED_KEY)
        val defaultWhenPicked = prefs.booleanOrNull(DEFAULT_WHEN_PICKED_KEY)
        if (defaultWhenPicked != null && defaultWhenPicked != BuildConfig.USE_REAL_CREW) {
            // local.properties changed since the pick: forget the pick, so changing it back later doesn't revive it.
            prefs.edit().remove(PICKED_KEY).remove(DEFAULT_WHEN_PICKED_KEY).apply()
        }
        return resolve(picked, defaultWhenPicked, BuildConfig.USE_REAL_CREW, BuildConfig.HAS_FIREBASE)
    }

    /** Remembers [realCrew] and restarts the app with that crew. */
    fun pickAndRestart(context: Context, realCrew: Boolean) {
        // commit(), not apply(): the process ends right below, before apply() would write.
        demoPrefs(context).edit()
            .putBoolean(PICKED_KEY, realCrew)
            .putBoolean(DEFAULT_WHEN_PICKED_KEY, BuildConfig.USE_REAL_CREW)
            .commit()
        restartApp(context)
    }

    private fun SharedPreferences.booleanOrNull(key: String): Boolean? = if (contains(key)) getBoolean(key, false) else null

    private const val PICKED_KEY = "realCrew"
    private const val DEFAULT_WHEN_PICKED_KEY = "realCrewDefaultWhenPicked"
}
