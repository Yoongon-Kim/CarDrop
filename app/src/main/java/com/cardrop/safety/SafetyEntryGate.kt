package com.cardrop.safety

/** Single-use quick-slot tokens survive activity recreation with the app-scoped coordinator. */
internal class SafetyEntryGate {
    private var sequence = 0L
    private var consumedThrough = 0L
    private var active: Long? = null
    private var revision: Long? = null
    private var deadline = 0L

    fun newEntry(): Long = ++sequence
    fun enter(id: Long?, enabled: Boolean, revision: Long?, nowMillis: Long): Boolean {
        leave()
        if (id == null || id <= consumedThrough || id > sequence) return false
        consumedThrough = id // Turning sound back on must not replay this entry either.
        if (!enabled) return false
        active = id; this.revision = revision; deadline = nowMillis + 5000
        return true
    }
    fun valid(id: Long, currentRevision: Long?, nowMillis: Long): Boolean =
        id == active && currentRevision == revision && nowMillis <= deadline
    fun leave() { active = null; revision = null; deadline = 0 }
}
