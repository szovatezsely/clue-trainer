package net.geoclue.trainer.game

import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.CountryOption
import net.geoclue.trainer.model.StatsDto
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Which slice of the dataset a player is currently training on. */
data class QuestionFilter(val continent: String? = null, val coreOnly: Boolean = false)

/** The question a player is currently looking at. The answer never leaves the server. */
data class PendingQuestion(
    val clue: Clue,
    val options: List<CountryOption>,
    val filter: QuestionFilter,
    val number: Int,
)

/**
 * Server-side state of one endless run.
 *
 * Keeping the score and the correct answer here (rather than in the browser)
 * means the client cannot read the answer out of the network tab before
 * guessing, and a page reload resumes the same question.
 */
class GameSession(val id: String) {
    var correct: Int = 0
        private set
    var wrong: Int = 0
        private set
    var streak: Int = 0
        private set
    var bestStreak: Int = 0
        private set
    var served: Int = 0
        private set

    /** Clues already shown in this run, so an endless game does not repeat itself. */
    val seenClueIds: MutableSet<String> = HashSet()
    var pending: PendingQuestion? = null
    @Volatile
    var lastActiveAt: Instant = Instant.now()

    fun serve(question: PendingQuestion) {
        pending = question
        seenClueIds += question.clue.id
        served = question.number
    }

    fun score(isCorrect: Boolean) {
        if (isCorrect) {
            correct++
            streak++
            bestStreak = maxOf(bestStreak, streak)
        } else {
            wrong++
            streak = 0
        }
        pending = null
    }

    /**
     * Drops the current question without scoring it - used when its image
     * cannot be loaded, so a broken clue never blocks the run. The clue stays
     * in [seenClueIds], so it will not come straight back.
     */
    fun abandon() {
        pending = null
    }

    fun reset() {
        correct = 0
        wrong = 0
        streak = 0
        bestStreak = 0
        served = 0
        seenClueIds.clear()
        pending = null
    }

    fun stats(): StatsDto {
        val answered = correct + wrong
        return StatsDto(
            correct = correct,
            wrong = wrong,
            answered = answered,
            streak = streak,
            bestStreak = bestStreak,
            accuracy = if (answered == 0) 0 else Math.round(correct * 100.0 / answered).toInt(),
        )
    }
}

/** In-memory session registry with lazy eviction of idle runs. */
class SessionStore(private val ttlHours: Long, private val maxSessions: Int = 20_000) {
    private val sessions = ConcurrentHashMap<String, GameSession>()
    private val lastPurge = AtomicLong(System.currentTimeMillis())

    fun create(): GameSession {
        purgeIfDue()
        val session = GameSession(UUID.randomUUID().toString())
        sessions[session.id] = session
        return session
    }

    /** Returns null for unknown or already-evicted sessions. */
    fun find(id: String): GameSession? {
        purgeIfDue()
        return sessions[id]?.also { it.lastActiveAt = Instant.now() }
    }

    val size: Int get() = sessions.size

    private fun purgeIfDue() {
        val now = System.currentTimeMillis()
        val previous = lastPurge.get()
        val due = now - previous > PURGE_INTERVAL_MS || sessions.size > maxSessions
        if (!due || !lastPurge.compareAndSet(previous, now)) return
        val cutoff = Instant.now().minusSeconds(ttlHours * 3600)
        sessions.entries.removeIf { it.value.lastActiveAt.isBefore(cutoff) }
    }

    private companion object {
        const val PURGE_INTERVAL_MS = 5 * 60 * 1000L
    }
}
