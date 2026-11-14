package com.cardrop.ai.game

import org.json.JSONObject

/** Explicit wire format, shared by Firebase and local recovery; malformed rooms never become boards. */
object OmokCodec {
    fun encode(game: OmokGame): Map<String, Any> = buildMap {
        put("id", game.id); put("black", game.blackId); put("white", game.whiteId)
        put("created", game.createdAt); put("phase", game.phase.name); put("revision", game.revision)
        put("moves", game.moves.joinToString(",")); put("reason", game.reason)
        game.winnerId?.let { put("winner", it) }
        put("presence", game.presence.mapValues { (_, p) -> presence(p) })
    }

    fun presence(p: OmokPresence): Map<String, Any> = mapOf("token" to p.token, "ready" to p.ready, "connected" to p.connected, "at" to p.at)

    fun decode(value: Any?): OmokGame? = runCatching {
        val map = value as? Map<*, *> ?: return null
        val moves = (map["moves"] as? String).orEmpty().let { text ->
            if (text.isEmpty()) emptyList() else text.split(',').map { it.toInt() }
        }
        require(moves.size <= OMOK_CELLS && moves.distinct().size == moves.size && moves.all { it in 0 until OMOK_CELLS })
        val game = OmokGame(
            id = map["id"] as String, blackId = map["black"] as String, whiteId = map["white"] as String,
            createdAt = (map["created"] as Number).toLong(), phase = OmokPhase.valueOf(map["phase"] as String),
            revision = (map["revision"] as Number).toInt(), moves = moves, winnerId = map["winner"] as? String,
            reason = (map["reason"] as? String).orEmpty(),
            presence = (map["presence"] as? Map<*, *>).orEmpty().entries.associate { (key, raw) ->
                val p = raw as Map<*, *>
                key as String to OmokPresence(p["token"] as String, p["ready"] == true, p["connected"] == true, (p["at"] as Number).toLong())
            },
        )
        require(game.id.isNotBlank() && game.blackId != game.whiteId && game.revision >= 0)
        require(game.winnerId == null || game.winnerId in game.players)
        require(game.presence.keys.all { it in game.players })
        require(game.phase != OmokPhase.INVITED || game.moves.isEmpty())
        game
    }.getOrNull()

    fun encodeRooms(games: Map<String, OmokGame>): Map<String, Any> = games.mapValues { encode(it.value) }
    fun decodeRooms(raw: Any?): Map<String, OmokGame> = (raw as? Map<*, *>).orEmpty().entries.mapNotNull { (id, value) ->
        decode(value)?.takeIf { it.id == id }?.let { it.id to it }
    }.toMap()

    fun toJson(games: Map<String, OmokGame>): String = JSONObject(encodeRooms(games)).toString()
    fun fromJson(json: String): Map<String, OmokGame> = runCatching { decodeRooms(toMap(JSONObject(json))) }.getOrDefault(emptyMap())

    private fun toMap(json: JSONObject): Map<String, Any?> = json.keys().asSequence().associateWith { key ->
        when (val value = json.get(key)) { is JSONObject -> toMap(value); JSONObject.NULL -> null; else -> value }
    }
}
