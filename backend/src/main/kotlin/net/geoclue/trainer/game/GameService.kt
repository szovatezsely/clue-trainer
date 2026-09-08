package net.geoclue.trainer.game

import io.ktor.http.HttpStatusCode
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.PlonkItScraper
import net.geoclue.trainer.model.AnswerRequest
import net.geoclue.trainer.model.AnswerResponse
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import net.geoclue.trainer.model.CountryOption
import net.geoclue.trainer.model.ExplanationDto
import net.geoclue.trainer.model.QuestionDto
import net.geoclue.trainer.model.StatsDto

/** Any error we want to surface to the client as a structured JSON body. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
) : RuntimeException(message)

/**
 * Builds questions and grades answers.
 *
 * A question is one clue image plus [OPTION_COUNT] country choices: the country
 * the clue actually belongs to, plus distractors drawn from the same continent,
 * which is what makes the exercise useful - "Canada vs USA vs Mexico" teaches
 * something, "Canada vs Japan vs Peru" does not.
 */
class GameService(private val repository: ClueRepository) {

    fun nextQuestion(session: GameSession, filter: QuestionFilter): QuestionDto {
        val snapshot = repository.snapshot
        synchronized(session) {
            // A reload (or a double click) must not burn a clue: re-serve the
            // pending question as long as the player did not change the filters.
            session.pending?.let { pending ->
                if (pending.filter == filter) return pending.toDto(unseenCount(snapshot, session, filter))
            }

            val pool = snapshot.cluesFor(filter.continent, filter.coreOnly)
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

            val clue = candidates.random()
            val question = PendingQuestion(
                clue = clue,
                options = buildOptions(snapshot, clue),
                filter = filter,
                number = session.served + 1,
            )
            session.serve(question)
            return question.toDto(unseenCount(snapshot, session, filter))
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
            val chosen = pending.options.firstOrNull { it.code == request.countryCode }
                ?: throw ApiException(
                    HttpStatusCode.BadRequest,
                    "invalid_option",
                    "\"" + request.countryCode + "\" was not one of the offered countries.",
                )
            val correctOption = pending.options.first { it.code == pending.clue.countryCode }
            val isCorrect = chosen.code == correctOption.code
            session.score(isCorrect)

            return AnswerResponse(
                correct = isCorrect,
                correctCountry = correctOption,
                chosenCountry = chosen,
                explanation = explain(pending.clue),
                stats = session.stats(),
            )
        }
    }

    /** Abandons the current question, for instance when its image is unavailable. */
    fun skip(session: GameSession): StatsDto {
        synchronized(session) {
            session.abandon()
            return session.stats()
        }
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

    private fun buildOptions(snapshot: ClueRepository.Snapshot, clue: Clue): List<CountryOption> {
        val answer = snapshot.countriesByCode.getValue(clue.countryCode)
        val board = mutableListOf(answer)
        // Territories share their parent's code prefix (US / US-AK). Two options
        // from the same prefix are either ambiguous (the clue holds for both) or
        // merely confusing, so a prefix may appear on the board only once.
        val usedPrefixes = mutableSetOf(answer.codePrefix)

        fun fill(candidates: List<Country>) {
            for (candidate in candidates.shuffled()) {
                if (board.size == OPTION_COUNT) return
                if (usedPrefixes.add(candidate.codePrefix)) board += candidate
            }
        }

        fill(snapshot.countriesByContinent[answer.continent].orEmpty())
        // Tiny continents (Antarctica) cannot fill a board on their own.
        if (board.size < OPTION_COUNT) fill(snapshot.playableCountries)

        return board.map { it.toOption() }.shuffled()
    }

    private fun unseenCount(
        snapshot: ClueRepository.Snapshot,
        session: GameSession,
        filter: QuestionFilter,
    ): Int = snapshot.cluesFor(filter.continent, filter.coreOnly).count { it.id !in session.seenClueIds }

    private fun PendingQuestion.toDto(remaining: Int) = QuestionDto(
        clueId = clue.id,
        imageUrl = clue.imageUrl,
        imageWidth = clue.width,
        tags = clue.tags,
        options = options,
        questionNumber = number,
        remainingClues = remaining,
    )

    companion object {
        const val OPTION_COUNT = 3
    }
}

fun Country.toOption() = CountryOption(code = code, name = name, flag = flag, continent = continent)
