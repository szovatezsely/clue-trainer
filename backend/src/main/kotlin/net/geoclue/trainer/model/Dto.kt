package net.geoclue.trainer.model

import kotlinx.serialization.Serializable

@Serializable
data class CountryOption(val code: String, val name: String, val flag: String, val continent: String)

/**
 * One button on the board. [value] is what the client sends back - a country
 * code in the country game, a region name in the region game - and [note] is
 * the quiet second line under the label, if there is anything to say.
 */
@Serializable
data class AnswerOption(val value: String, val label: String, val note: String = "")

@Serializable
data class StatsDto(
    val correct: Int,
    val wrong: Int,
    val answered: Int,
    val streak: Int,
    val bestStreak: Int,
    val accuracy: Int,
)

@Serializable
data class SessionDto(val sessionId: String, val stats: StatsDto)

/** A question, deliberately stripped of anything that would reveal the answer. */
@Serializable
data class QuestionDto(
    val clueId: String,
    /** "country" or "region" - see [net.geoclue.trainer.game.GameMode]. */
    val mode: String,
    /**
     * The country the clue is from. Given away on purpose in the region game,
     * where it is the premise of the question, and withheld in the country
     * game, where it is the answer.
     */
    val country: CountryOption? = null,
    val imageUrl: String,
    val imageWidth: Double,
    val tags: List<String>,
    val options: List<AnswerOption>,
    val questionNumber: Int,
    val remainingClues: Int,
)

@Serializable
data class AnswerRequest(val clueId: String, val answer: String)

@Serializable
data class ExplanationDto(
    val paragraphs: List<String>,
    val section: String,
    val subsection: String,
    val tags: List<String>,
    val sourceUrl: String,
    val streetViewUrl: String? = null,
)

@Serializable
data class AnswerResponse(
    val correct: Boolean,
    val mode: String,
    val correctAnswer: AnswerOption,
    val chosenAnswer: AnswerOption,
    /** Always filled in: the reveal links to this country's guide either way. */
    val country: CountryOption,
    val explanation: ExplanationDto,
    val stats: StatsDto,
)

@Serializable
data class ContinentDto(
    val name: String,
    val countryCount: Int,
    val clueCount: Int,
    val coreClueCount: Int,
    val regionClueCount: Int,
)

@Serializable
data class MetaDto(
    val source: String,
    val scrapedAt: String,
    val clueCount: Int,
    val coreClueCount: Int,
    val regionClueCount: Int,
    val countryCount: Int,
    val regionCountryCount: Int,
    val continents: List<ContinentDto>,
    val countries: List<CountryOption>,
    val tags: List<String>,
    val refreshAllowed: Boolean,
    val refreshState: String,
)

@Serializable
data class ImageStatusDto(val throttled: Boolean, val retryInSeconds: Int)

@Serializable
data class HealthDto(val status: String, val clueCount: Int, val countryCount: Int, val scrapedAt: String)

@Serializable
data class ErrorDto(val error: String, val message: String)

@Serializable
data class RefreshDto(val state: String, val message: String)
