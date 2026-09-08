package net.geoclue.trainer.model

import kotlinx.serialization.Serializable

@Serializable
data class CountryOption(val code: String, val name: String, val flag: String, val continent: String)

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
    val imageUrl: String,
    val imageWidth: Double,
    val tags: List<String>,
    val options: List<CountryOption>,
    val questionNumber: Int,
    val remainingClues: Int,
)

@Serializable
data class AnswerRequest(val clueId: String, val countryCode: String)

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
    val correctCountry: CountryOption,
    val chosenCountry: CountryOption,
    val explanation: ExplanationDto,
    val stats: StatsDto,
)

@Serializable
data class ContinentDto(
    val name: String,
    val countryCount: Int,
    val clueCount: Int,
    val coreClueCount: Int,
)

@Serializable
data class MetaDto(
    val source: String,
    val scrapedAt: String,
    val clueCount: Int,
    val coreClueCount: Int,
    val countryCount: Int,
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
