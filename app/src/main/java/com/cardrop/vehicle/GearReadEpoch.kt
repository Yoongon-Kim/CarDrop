package com.cardrop.vehicle

/** Called under the vehicle session lock. An old P reply must never override a newer D event/read. */
internal class GearReadEpoch {
    private var next = 0L
    private var pending: Long? = null
    fun request(): Long = (++next).also { pending = it }
    fun event() { pending = null }
    fun complete(ticket: Long): Boolean {
        if (pending != ticket) return false
        pending = null
        return true
    }
}
