package com.cardrop.drop

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owner: Member A (2026-11-15). Board 08's own tiles ([DropSlotRules]), kept in SharedPreferences on this device. */
class DropSlots(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("cardrop", Context.MODE_PRIVATE)
    private val current = MutableStateFlow(DropSlotRules.decode(prefs.getString(KEY, null)))

    val slots: StateFlow<List<PlaceKind>> = current.asStateFlow()

    fun add(kind: PlaceKind) = save(DropSlotRules.add(current.value, kind))

    fun remove(kind: PlaceKind) = save(DropSlotRules.remove(current.value, kind))

    private fun save(kinds: List<PlaceKind>) {
        current.value = kinds
        prefs.edit().putString(KEY, DropSlotRules.encode(kinds)).apply()
    }

    private companion object {
        const val KEY = "dropSlots.v1"
    }
}
