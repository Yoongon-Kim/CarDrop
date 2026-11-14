package com.cardrop.safety

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** One atomic journal snapshot includes both the events and their aggregate, including recovery locks. */
class SafetyStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "safety-records.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE records (id TEXT PRIMARY KEY, updated INTEGER NOT NULL, data TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized fun save(record: SafetyRecord) {
        val values = ContentValues().apply { put("id", record.id); put("updated", record.updatedAtMillis); put("data", record.toJson().toString()) }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict("records", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
    }

    @Synchronized fun delete(id: String) {
        writableDatabase.delete("records", "id = ?", arrayOf(id))
    }

    @Synchronized fun load(): List<SafetyRecord> = readableDatabase.query("records", arrayOf("data"), null, null, null, null, "updated DESC", "100").use { cursor ->
        buildList { while (cursor.moveToNext()) runCatching { safetyRecordFromJson(JSONObject(cursor.getString(0))) }.getOrNull()?.let(::add) }
    }
}

internal fun SafetyRecord.toJson(): JSONObject = JSONObject().apply {
    put("tripId", tripId); put("carId", carId); put("carName", carName); put("destination", destination)
    put("startedAt", startedAtMillis); put("updatedAt", updatedAtMillis); put("endedAt", endedAtMillis)
    put("demo", demo); put("status", status.name); put("rulesVersion", rulesVersion); put("revision", revision)
    put("eligible", eligibleSeconds); put("observed", observedSeconds); put("moving", movingSeconds)
    put("distance", distanceKm)
    put("accelerationLocked", accelerationLocked); put("brakingLocked", brakingLocked)
    put("events", JSONArray().apply { events.forEach { e -> put(JSONObject().apply {
        put("id", e.id); put("type", e.type.name); put("at", e.atMillis); put("penalty", e.penalty)
        put("peak", e.peak)
    }) } })
}

internal fun safetyRecordFromJson(j: JSONObject): SafetyRecord {
    val events = j.optJSONArray("events") ?: JSONArray()
    return SafetyRecord(
        tripId = j.getString("tripId"), carId = j.getString("carId"), carName = j.getString("carName"),
        destination = j.getString("destination"), startedAtMillis = j.getLong("startedAt"), updatedAtMillis = j.getLong("updatedAt"),
        demo = j.getBoolean("demo"), status = SafetyTripStatus.valueOf(j.getString("status")),
        endedAtMillis = if (j.isNull("endedAt")) null else j.getLong("endedAt"),
        rulesVersion = j.getInt("rulesVersion").let { if (it == 1) SafetyRules.VERSION else it },
        // Version 1 journals retain motion events and timestamps; retired event types no longer deduct.
        events = (0 until events.length()).mapNotNull { i -> events.getJSONObject(i).let { e ->
            val type = SafetyEventType.entries.firstOrNull { it.name == e.getString("type") } ?: return@mapNotNull null
            SafetyEvent(e.getString("id"), type, e.getLong("at"), e.getInt("penalty"), e.getDouble("peak"))
        } },
        eligibleSeconds = j.getDouble("eligible"), observedSeconds = j.getDouble("observed"), movingSeconds = j.getDouble("moving"),
        distanceKm = j.getDouble("distance"),
        accelerationLocked = j.optBoolean("accelerationLocked"), brakingLocked = j.optBoolean("brakingLocked"),
        revision = j.optLong("revision") + if (j.getInt("rulesVersion") == 1) 1 else 0,
    )
}

internal fun SafetyPeer.toFirebase(): Map<String, Any> = mapOf(
    "score" to score, "acceleration" to accelerationCount, "braking" to brakingCount,
    "penalty" to totalPenalty, "speedCoverage" to speedCoverage,
    "movingSeconds" to movingSeconds, "status" to status.name, "demo" to demo,
    "updatedAt" to updatedAtMillis, "rulesVersion" to rulesVersion,
)

internal fun safetyPeerFromFirebase(carId: String, value: Any?): SafetyPeer? = runCatching {
    val m = value as? Map<*, *> ?: return null
    fun number(key: String): Number = m[key] as? Number ?: error("Missing $key")
    SafetyPeer(carId, number("score").toInt().also { require(it in 0..100) }, number("acceleration").toInt(),
        number("braking").toInt(), number("penalty").toInt(), number("speedCoverage").toDouble(),
        number("movingSeconds").toDouble(), SafetyTripStatus.valueOf(m["status"] as String), m["demo"] as Boolean,
        number("updatedAt").toLong(), number("rulesVersion").toInt()).also {
        require(it.speedCoverage.isFinite() && it.speedCoverage in 0.0..1.0)
        require(it.accelerationCount >= 0 && it.brakingCount >= 0 && it.totalPenalty >= 0 && it.movingSeconds.isFinite())
        require(it.movingSeconds >= 0)
        require(it.score == (100 - it.totalPenalty).coerceAtLeast(0))
    }
}.getOrNull()
