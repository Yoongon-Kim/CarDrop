package com.cardrop.core.demo

import android.content.Context
import java.util.UUID

/**
 * Owner: Member A (2026-11-15). The real crew's start (StartModeScreen): the driver types their own name ("홍승현") and
 * then makes a crew or joins one with the code. The name goes to this car's seat in the crew, so every device shows it
 * (FirebaseCrewRepository). Kept on this device, like [DemoCar]'s pick, and offered again at the next start.
 */
object MyName {
    /** The name typed last time, or null before the first. */
    fun get(context: Context): String? = demoPrefs(context).getString(KEY, null)?.let(CarNames::clean)

    /** Saves [typed] ([CarNames.clean]: at most 10 letters, no brackets). commit(): the app may restart right after. */
    fun set(context: Context, typed: String) {
        val name = CarNames.clean(typed) ?: return
        demoPrefs(context).edit().putString(KEY, name).commit()
    }

    /**
     * This device, made once: a car that joins the same crew again (e.g. after an app restart) gets its own seat back
     * (crew/SeatRules.kt).
     */
    fun deviceId(context: Context): String {
        val prefs = demoPrefs(context)
        return prefs.getString(DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(DEVICE, it).commit() }
    }

    private const val KEY = "myName"
    private const val DEVICE = "deviceId"
}
