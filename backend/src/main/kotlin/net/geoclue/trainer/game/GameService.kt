package net.geoclue.trainer.game

import io.ktor.http.HttpStatusCode
import net.geoclue.trainer.data.ClueAmbiguity
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.PlonkItScraper
import net.geoclue.trainer.model.AnswerOption
import net.geoclue.trainer.model.AnswerRequest
import net.geoclue.trainer.model.AnswerResponse
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import net.geoclue.trainer.model.CountryOption
import net.geoclue.trainer.model.ExplanationDto
import net.geoclue.trainer.model.QuestionDto
import net.geoclue.trainer.model.StatsDto
import kotlin.random.Random

/** Any error we want to surface to the client as a structured JSON body. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

/**
 * Builds questions and grades answers.
 *
 * In the country game a question is one clue image plus [OPTION_COUNT] country
 * choices: the country the clue actually belongs to, plus distractors drawn
 * from the same continent, which is what makes the exercise useful - "Canada vs
 * USA vs Mexico" teaches something, "Canada vs Japan vs Peru" does not. A
 * distractor the clue's own explanation names as fitting too is left off the
 * board; see [ClueAmbiguity].
 *
 * The region game asks the next question down. The country is handed over with
 * the clue, and the choices are [OPTION_COUNT] regions of that one country -
 * so the player is placing a Japanese pole plate in Shikoku rather than in
 * Japan. Which regions a country has, and which one a clue is about, are read
 * out of the guide text; see [net.geoclue.trainer.data.ClueRegions].
 */
class GameService(
    private val repository: ClueRepository,
    private val images: ImageAvailability = ImageAvailability.ALWAYS,
    private val minCachedPool: Int = MIN_CACHED_POOL,
) {

    fun nextQuestion(session: GameSession, filter: QuestionFilter): QuestionDto {
        val snapshot = repository.snapshot
        synchronized(session) {
            // A reload (or a double click) must not burn a clue: re-serve the
            // pending question as long as the player did not change the filters.
            session.pending?.let { pending ->
                if (pending.filter == filter) return pending.toDto(snapshot, unseenCount(snapshot, session, filter))
            }

            val pool = poolFor(snapshot, filter)
            if (pool.isEmpty()) {
                throw ApiException(
                    HttpStatusCode.NotFound,
                    "no_clues",
                    "No clues match the selected filters.",
                )
            }

            var candidates = pool.filterNot { it.id in session.seenClueIds }
            if (candidates.isEmpty()) {
                // Endless mode: every clue in the pool has been shown, start over.
                session.seenClueIds.clear()
                candidates = pool
            }

            val clue = pickPlayable(candidates)
            val question = when (filter.mode) {
                GameMode.COUNTRY -> PendingQuestion(
                    clue = clue,
                    options = buildCountryOptions(snapshot, clue).map { it.toAnswerOption() },
                    answer = clue.countryCode,
                    filter = filter,
                    number = session.served + 1,
                )

                GameMode.REGION -> {
                    val region = snapshot.regions.regionOf(clue)
                        ?: error("region clue " + clue.id + " lost its region")
                    PendingQuestion(
                        clue = clue,
                        options = buildRegionOptions(snapshot, clue, region),
                        answer = region,
                        filter = filter,
                        number = session.served + 1,
                    )
                }
            }
            session.serve(question)
            return question.toDto(snapshot, unseenCount(snapshot, session, filter))
        }
    }

    fun answer(session: GameSession, request: AnswerRequest): AnswerResponse {
        synchronized(session) {
            val pending = session.pending
                ?: throw ApiException(HttpStatusCode.Conflict, "no_pending_question", "Ask for a clue first.")
            if (pending.clue.id != request.clueId) {
                throw ApiException(
                    HttpStatusCode.Conflict,
                    "stale_answer",
                    "That answer belongs to an older clue.",
                )
            }
            val chosen = pending.options.firstOrNull { it.value == request.answer }
                ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    "invalid_option",
                    "\"" + request.answer + "\" was not one of the offered answers.",
                )
            val correctOption = pending.options.first { it.value == pending.answer }
            val isCorrect = chosen.value == correctOption.value
            session.score(isCorrect)

            return AnswerResponse(
                correct = isCorrect,
                mode = pending.filter.mode.wire,
                correctAnswer = correctOption,
                chosenAnswer = chosen,
                country = repository.snapshot.countriesByCode.getValue(pending.clue.countryCode).toOption(),
                explanation = explain(pending.clue),
                stats = session.stats(),
            )
        }
    }

    /**
     * Prefers clues whose image is already on disk: those load instantly and
     * cannot fail, however hard the guide site is rate-limiting us.
     *
     * A small share of picks still reaches for an uncached clue, so the cache
     * keeps growing while people play - but never while the origin is actively
     * throttling, when an uncached clue could only disappoint.
     */
    private fun pickPlayable(candidates: List<Clue>): Clue {
        val cached = candidates.filter { images.isCached(it.imageUrl) }
        return when {
            // Nothing cached yet: anything is as good as anything else.
            cached.isEmpty() -> candidates.random()
            images.isThrottled() -> cached.random()
            // Too small a pool to play from without repeating; keep filling it.
            cached.size < minCachedPool -> candidates.random()
            Random.nextInt(100) < EXPLORE_PERCENT -> candidates.random()
            else -> cached.random()
        }
    }

    /** Abandons the current question, for instance when its image is unavailable. */
    fun skip(session: GameSession): StatsDto {
        synchronized(session) {
            session.abandon()
            return session.stats()
        }
    }

    private fun poolFor(snapshot: ClueRepository.Snapshot, filter: QuestionFilter): List<Clue> =
        when (filter.mode) {
            GameMode.COUNTRY -> snapshot.cluesFor(filter.continent, filter.coreOnly)
            GameMode.REGION -> snapshot.regionCluesFor(filter.continent)
        }

    private fun explain(clue: Clue): ExplanationDto {
        val country = repository.snapshot.countriesByCode[clue.countryCode]
        return ExplanationDto(
            paragraphs = clue.text,
            section = clue.section,
            subsection = clue.subsection,
            tags = clue.tags,
            sourceUrl = PlonkItScraper.BASE_URL + "/" + (country?.slug ?: ""),
            streetViewUrl = clue.streetView,
        )
    }

    private fun buildCountryOptions(snapshot: ClueRepository.Snapshot, clue: Clue): List<Country> {
        val answer = snapshot.countriesByCode.getValue(clue.countryCode)
        val board = mutableListOf(answer)
        // Territories share their parent's code prefix (US / US-AK). Two options
        // from the same prefix are either ambiguous (the clue holds for both) or
        // merely confusing, so a prefix may appear on the board only once.
        val usedPrefixes = mutableSetOf(answer.codePrefix)
        // The same goes for a country the clue's own explanation vouches for:
        // "Peru, Brazil and Argentina are the only ones with smallcam" makes
        // Peru a right answer, so marking it wrong would teach the opposite.
        val ambiguous = snapshot.ambiguousFor(clue)

        fun fill(candidates: List<Country>, allowAmbiguous: Boolean = false) {
            for (candidate in candidates.shuffled()) {
                if (board.size == OPTION_COUNT) return
                if (!allowAmbiguous && candidate.code in ambiguous) continue
                if (usedPrefixes.add(candidate.codePrefix)) board += candidate
            }
        }

        fill(snapshot.countriesByContinent[answer.continent].orEmpty())
        // Tiny continents (Antarctica) cannot fill a board on their own.
        if (board.size < OPTION_COUNT) fill(snapshot.playableCountries)
        // Only if the world itself ran out: a full board beats a perfect one.
        if (board.size < OPTION_COUNT) fill(snapshot.playableCountries, allowAmbiguous = true)

        return board.shuffled()
    }

    /**
     * The clue's own region plus other regions of the same country - a board of
     * Japanese regions, never a Japanese one against a Brazilian one.
     */
    private fun buildRegionOptions(
        snapshot: ClueRepository.Snapshot,
        clue: Clue,
        region: String,
    ): List<AnswerOption> {
        val others = snapshot.regions.regionsOf(clue.countryCode)
            .filterNot { it == region }
            .shuffled()
            .take(OPTION_COUNT - 1)
        return (listOf(region) + others).shuffled().map { AnswerOption(value = it, label = it) }
    }

    private fun unseenCount(
        snapshot: ClueRepository.Snapshot,
        session: GameSession,
        filter: QuestionFilter,
    ): Int = poolFor(snapshot, filter).count { it.id !in session.seenClueIds }

    private fun PendingQuestion.toDto(snapshot: ClueRepository.Snapshot, remaining: Int) = QuestionDto(
        clueId = clue.id,
        mode = filter.mode.wire,
        // The region game is "which part of Japan?", so Japan comes with the question.
        country = when (filter.mode) {
            GameMode.COUNTRY -> null
            GameMode.REGION -> snapshot.countriesByCode.getValue(clue.countryCode).toOption()
        },
        imageUrl = clue.imageUrl,
        imageWidth = clue.width,
        tags = clue.tags,
        options = options,
        questionNumber = number,
        remainingClues = remaining,
    )

    companion object {
        const val OPTION_COUNT = 3

        /** Below this many cached images, play from everything and keep fetching. */
        const val MIN_CACHED_POOL = 40

        /** Share of picks that deliberately reach for an image we do not have. */
        const val EXPLORE_PERCENT = 10
    }
}

fun Country.toOption() = CountryOption(code = code, name = name, flag = flag, continent = continent)

/** A country on the board: the name is the answer, the code the quiet second line. */
fun Country.toAnswerOption() = AnswerOption(value = code, label = name, note = code)
