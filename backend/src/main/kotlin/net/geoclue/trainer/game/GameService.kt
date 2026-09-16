package net.geoclue.trainer.game

import io.ktor.http.HttpStatusCode
import net.geoclue.trainer.data.ClueAmbiguity
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.PlonkItScraper
import net.geoclue.trainer.data.Translations
import net.geoclue.trainer.model.AnswerOption
import net.geoclue.trainer.model.AnswerRequest
import net.geoclue.trainer.model.AnswerResponse
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import net.geoclue.trainer.model.CountryOption
import net.geoclue.trainer.model.ExplanationDto
import net.geoclue.trainer.model.Lang
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
 *
 * The language is a property of the answer, not of the question: it decides how
 * a clue is worded, never which clue is picked. That is why it is passed in per
 * call rather than kept in [QuestionFilter] - switching language re-renders the
 * question the player is already looking at instead of burning a clue.
 */
class GameService(
    private val repository: ClueRepository,
    private val images: ImageAvailability = ImageAvailability.ALWAYS,
    private val minCachedPool: Int = MIN_CACHED_POOL,
    private val translations: Translations = Translations.NONE,
) {

    fun nextQuestion(
        session: GameSession,
        filter: QuestionFilter,
        lang: Lang = Lang.DEFAULT,
    ): QuestionDto {
        val snapshot = repository.snapshot
        synchronized(session) {
            // A reload (or a double click) must not burn a clue: re-serve the
            // pending question as long as the player did not change the filters.
            session.pending?.let { pending ->
                if (pending.filter == filter) {
                    return pending.toDto(snapshot, unseenCount(snapshot, session, filter), lang)
                }
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
            return question.toDto(snapshot, unseenCount(snapshot, session, filter), lang)
        }
    }

    fun answer(
        session: GameSession,
        request: AnswerRequest,
        lang: Lang = Lang.DEFAULT,
    ): AnswerResponse {
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
            session.score(isCorrect, GradedAnswer(pending, chosen.value, isCorrect))

            return render(pending, chosen.value, isCorrect, session.stats(), lang)
        }
    }

    /**
     * The verdict the player is already looking at, re-rendered in [lang].
     * Grading clears the pending question, so without this the explanation on
     * screen would be the one place a language switch could not reach.
     */
    fun lastAnswer(session: GameSession, lang: Lang = Lang.DEFAULT): AnswerResponse {
        synchronized(session) {
            val graded = session.lastGraded ?: throw ApiException(
                HttpStatusCode.NotFound,
                "no_answer_yet",
                "Nothing has been answered in this session yet.",
            )
            return render(graded.question, graded.chosen, graded.correct, session.stats(), lang)
        }
    }

    private fun render(
        pending: PendingQuestion,
        chosen: String,
        isCorrect: Boolean,
        stats: StatsDto,
        lang: Lang,
    ): AnswerResponse {
        val options = pending.options.map { localize(it, lang) }
        return AnswerResponse(
            correct = isCorrect,
            mode = pending.filter.mode.wire,
            correctAnswer = options.first { it.value == pending.answer },
            chosenAnswer = options.first { it.value == chosen },
            options = options,
            country = repository.snapshot.countriesByCode
                .getValue(pending.clue.countryCode)
                .toOption(translations, lang),
            explanation = explain(pending.clue, lang),
            stats = stats,
        )
    }

    /**
     * A country option carries its ISO code, so the label can be swapped for the
     * name in [lang]. A region option is the guide's own spelling of a place -
     * the name written on the sign the player is learning to read - and is left
     * exactly as it is.
     */
    private fun localize(option: AnswerOption, lang: Lang): AnswerOption {
        val translated = translations.countryName(lang, option.value, option.label)
        return if (translated == option.label) option else option.copy(label = translated)
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

    private fun explain(clue: Clue, lang: Lang): ExplanationDto {
        val country = repository.snapshot.countriesByCode[clue.countryCode]
        return ExplanationDto(
            paragraphs = translations.clueText(lang, clue.id, clue.text),
            section = translations.section(lang, clue.section),
            subsection = translations.subsection(lang, clue.subsection),
            tags = translations.tags(lang, clue.tags),
            sourceUrl = PlonkItScraper.BASE_URL + "/" + (country?.slug ?: ""),
            streetViewUrl = clue.streetView,
            translated = translations.hasClueText(lang, clue.id),
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

    private fun PendingQuestion.toDto(
        snapshot: ClueRepository.Snapshot,
        remaining: Int,
        lang: Lang,
    ) = QuestionDto(
        clueId = clue.id,
        mode = filter.mode.wire,
        // The region game is "which part of Japan?", so Japan comes with the question.
        country = when (filter.mode) {
            GameMode.COUNTRY -> null
            GameMode.REGION -> snapshot.countriesByCode
                .getValue(clue.countryCode)
                .toOption(translations, lang)
        },
        imageUrl = clue.imageUrl,
        imageWidth = clue.width,
        tags = translations.tags(lang, clue.tags),
        options = options.map { localize(it, lang) },
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

fun Country.toOption(
    translations: Translations = Translations.NONE,
    lang: Lang = Lang.DEFAULT,
) = CountryOption(
    code = code,
    name = translations.countryName(lang, code, name),
    flag = flag,
    // A grouping key the client sends back, not a label - see [CountryOption].
    continent = continent,
)

/** A country on the board: the name is the answer, the code the quiet second line. */
fun Country.toAnswerOption() = AnswerOption(value = code, label = name, note = code)
