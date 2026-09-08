package net.geoclue.trainer.model

import kotlinx.serialization.Serializable

/** A Google-Street-View-covered country (or territory) that has a PlonkIt guide. */
@Serializable
data class Country(
    val code: String,
    val name: String,
    val slug: String,
    val continent: String,
    val heroImage: String? = null,
    val clueCount: Int = 0,
) {
    /** Territories are coded like `US-AK`; the parent country shares the prefix. */
    val codePrefix: String get() = code.take(2)

    /** Flag emoji derived from the ISO 3166-1 alpha-2 prefix of the code. */
    val flag: String
        get() = codePrefix
            .takeIf { it.length == 2 && it.all { c -> c in 'A'..'Z' } }
            ?.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
            ?.joinToString("")
            ?: "\uD83C\uDF10"
}

/** A single identification clue lifted from a country guide. */
@Serializable
data class Clue(
    val id: String,
    val countryCode: String,
    val imageUrl: String,
    val width: Double = 0.5,
    val text: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val section: String = "",
    val subsection: String = "",
    val streetView: String? = null,
) {
    /**
     * Clues from the "Identifying <country>" chapter describe the country as a
     * whole; everything else (regional chapters, spotlights) is narrower and
     * therefore considerably harder to place.
     */
    val isCore: Boolean get() = section.startsWith("Identifying", ignoreCase = true)
}

/** Everything scraped from plonkit.net in one immutable snapshot. */
@Serializable
data class ClueDataset(
    val source: String,
    val scrapedAt: String,
    val countries: List<Country>,
    val clues: List<Clue>,
)
