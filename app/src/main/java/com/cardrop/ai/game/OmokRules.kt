package com.cardrop.ai.game

const val OMOK_SIZE = 13
const val OMOK_CELLS = OMOK_SIZE * OMOK_SIZE
const val OMOK_PRESENCE_MS = 6_000L
const val OMOK_INVITE_MS = 60_000L // an invitation waits 1 minute for an answer (2 until 2026-11-15)

enum class OmokPhase { INVITED, PLAYING, FINISHED, DECLINED, CANCELLED, EXPIRED }

/** A ready lease belongs to one connection/foreground/gear epoch, never to an old cached P value. */
data class OmokPresence(val token: String, val ready: Boolean, val connected: Boolean, val at: Long) {
    fun usable(now: Long): Boolean = ready && connected && now - at in -2_000L..OMOK_PRESENCE_MS
}

data class OmokGame(
    val id: String,
    val blackId: String,
    val whiteId: String,
    val createdAt: Long,
    val phase: OmokPhase = OmokPhase.INVITED,
    val revision: Int = 0,
    val moves: List<Int> = emptyList(),
    val winnerId: String? = null,
    val reason: String = "",
    val presence: Map<String, OmokPresence> = emptyMap(),
) {
    val players: List<String> get() = listOf(blackId, whiteId)
    val turnId: String get() = if (moves.size % 2 == 0) blackId else whiteId
    val terminal: Boolean get() = phase != OmokPhase.INVITED && phase != OmokPhase.PLAYING
    fun invited(now: Long): Boolean = phase == OmokPhase.INVITED && now - createdAt < OMOK_INVITE_MS
    fun busy(now: Long): Boolean = phase == OmokPhase.PLAYING || invited(now)
    fun ready(now: Long): Boolean = players.all { presence[it]?.usable(now) == true }
    fun tokens(): Map<String, String> = players.associateWith { presence[it]?.token.orEmpty() }
    fun other(carId: String): String = if (carId == blackId) whiteId else blackId
    fun board(): IntArray = IntArray(OMOK_CELLS).also { board ->
        moves.forEachIndexed { i, cell -> board[cell] = if (i % 2 == 0) 1 else 2 }
    }
}

class OmokRejected(message: String) : IllegalStateException(message)

/** Pure, deterministic rules. Both local play and every Firebase retry use exactly this code. */
object OmokRules {
    fun invite(games: Map<String, OmokGame>, game: OmokGame, now: Long): Map<String, OmokGame> {
        requireGame(game.blackId != game.whiteId, "다른 차량을 골라 주세요")
        requireGame(game.id !in games, "이미 보낸 도전장이에요")
        requireGame(games.values.none { old -> old.busy(now) && old.players.any { it in game.players } }, "이미 진행 중인 대국이 있어요")
        requireGame(game.presence[game.blackId]?.usable(now) == true, "P 정차 상태에서 도전장을 보낼 수 있어요")
        return games + (game.id to game)
    }

    fun accept(game: OmokGame, actor: String, revision: Int, now: Long): OmokGame {
        checkRevision(game, revision)
        requireGame(game.invited(now), "도전장이 만료되었거나 이미 처리됐어요")
        requireGame(actor == game.whiteId, "초대받은 차량만 수락할 수 있어요")
        requireGame(game.presence[actor]?.usable(now) == true, "P 정차 상태에서 수락할 수 있어요")
        return game.copy(phase = OmokPhase.PLAYING, revision = game.revision + 1)
    }

    fun close(game: OmokGame, actor: String, revision: Int, now: Long): OmokGame {
        checkRevision(game, revision)
        requireGame(actor in game.players && !game.terminal, "이미 종료된 대국이에요")
        if (game.phase == OmokPhase.INVITED) return game.copy(
            phase = if (!game.invited(now)) OmokPhase.EXPIRED else if (actor == game.blackId) OmokPhase.CANCELLED else OmokPhase.DECLINED,
            revision = game.revision + 1,
        )
        return game.copy(phase = OmokPhase.FINISHED, winnerId = game.other(actor), reason = "기권", revision = game.revision + 1)
    }

    fun move(game: OmokGame, actor: String, cell: Int, revision: Int, tokens: Map<String, String>, now: Long): OmokGame {
        checkRevision(game, revision)
        requireGame(game.phase == OmokPhase.PLAYING, "진행 중인 대국이 아니에요")
        requireGame(actor == game.turnId, "상대 차례예요")
        requireGame(game.ready(now), "두 차량 모두 P로 정차하고 게임 화면을 열어 주세요")
        requireGame(tokens == game.tokens(), "정차 또는 연결 상태가 바뀌었어요. 놓을 자리를 다시 골라 주세요")
        requireGame(cell in 0 until OMOK_CELLS && cell !in game.moves, "빈 교차점을 골라 주세요")
        val moved = game.copy(moves = game.moves + cell, revision = game.revision + 1)
        return when {
            winningLine(moved).isNotEmpty() -> moved.copy(phase = OmokPhase.FINISHED, winnerId = actor, reason = "5개 연결")
            moved.moves.size == OMOK_CELLS -> moved.copy(phase = OmokPhase.FINISHED, reason = "무승부")
            else -> moved
        }
    }

    /** Freestyle: five OR MORE, with no forbidden moves. Only the last move can create a new winning line. */
    fun winningLine(game: OmokGame): List<Int> {
        val last = game.moves.lastOrNull() ?: return emptyList()
        val board = game.board()
        val stone = board[last]
        val row = last / OMOK_SIZE
        val col = last % OMOK_SIZE
        for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
            val line = mutableListOf(last)
            for (sign in listOf(-1, 1)) {
                var r = row + dr * sign
                var c = col + dc * sign
                while (r in 0 until OMOK_SIZE && c in 0 until OMOK_SIZE && board[r * OMOK_SIZE + c] == stone) {
                    line += r * OMOK_SIZE + c
                    r += dr * sign
                    c += dc * sign
                }
            }
            if (line.size >= 5) return line
        }
        return emptyList()
    }

    private fun checkRevision(game: OmokGame, revision: Int) =
        requireGame(game.revision == revision, "대국이 갱신됐어요. 현재 보드에서 다시 선택해 주세요")

    private fun requireGame(value: Boolean, message: String) { if (!value) throw OmokRejected(message) }
}

/** A small, deterministic practice opponent: win, block an immediate win, then build nearby lines. */
object OmokBot {
    fun choose(game: OmokGame): Int? {
        val empty = (0 until OMOK_CELLS).filter { it !in game.moves }
        if (empty.isEmpty()) return null
        val board = game.board()
        val mine = if (game.moves.size % 2 == 0) 1 else 2
        fun score(cell: Int, stone: Int): Int {
            val row = cell / OMOK_SIZE
            val col = cell % OMOK_SIZE
            var best = 0
            for ((dr, dc) in listOf(0 to 1, 1 to 0, 1 to 1, 1 to -1)) {
                var count = 1
                for (sign in listOf(-1, 1)) {
                    var r = row + sign * dr
                    var c = col + sign * dc
                    while (r in 0 until OMOK_SIZE && c in 0 until OMOK_SIZE && board[r * OMOK_SIZE + c] == stone) {
                        count++; r += sign * dr; c += sign * dc
                    }
                }
                if (count >= 5) return 100_000
                best += count * count * count
            }
            return best
        }
        return empty.maxByOrNull { cell ->
            val attack = score(cell, mine)
            val defend = score(cell, 3 - mine)
            (if (attack >= 100_000) 2_000_000 else if (defend >= 100_000) 1_000_000 else attack * 3 + defend * 2) -
                kotlin.math.abs(cell / OMOK_SIZE - 6) - kotlin.math.abs(cell % OMOK_SIZE - 6)
        }
    }
}
