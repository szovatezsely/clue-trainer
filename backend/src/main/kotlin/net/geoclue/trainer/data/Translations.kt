package net.geoclue.trainer.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.geoclue.trainer.model.Lang
import org.slf4j.LoggerFactory

/** The finite vocabularies of one language: everything that is not clue prose. */
@Serializable
data class ContentBundle(
    /** ISO country code to the country's name in this language. */
    val countries: Map<String, String> = emptyMap(),
    val continents: Map<String, String> = emptyMap(),
    val tags: Map<String, String> = emptyMap(),
    /** Guide chapter headings, keyed by the English heading. */
    val sections: Map<String, String> = emptyMap(),
    val subsections: Map<String, String> = emptyMap(),
)

/**
 * Serves the dataset in a language other than the one it was scraped in.
 *
 * Two kinds of text need translating, and they are stored apart because they
 * behave differently. The finite vocabularies - country names, continents,
 * tags, chapter headings - are a few hundred entries that change only when the
 * guide gains a country, and live in `seed/<lang>/content.json`. The clue prose
 * is a megabyte of guide text keyed by clue id, and lives in
 * `seed/<lang>/clues.json`.
 *
 * Every lookup falls back to the English original, so a missing translation
 * costs that one string and nothing else: the game stays playable while a
 * language is still being filled in, and a re-scrape that adds clues does not
 * blank them out.
 */
class Translations private constructor(private val bundles: Map<Lang, Bundle>) {

    class Bundle(val content: ContentBundle, val clueText: Map<String, List<String>>)

    fun countryName(lang: Lang, code: String, fallback: String): String =
        bundles[lang]?.content?.countries?.get(code) ?: fallback

    fun continent(lang: Lang, name: String): String =
        bundles[lang]?.content?.continents?.get(name) ?: name

    fun tag(lang: Lang, tag: String): String = bundles[lang]?.content?.tags?.get(tag) ?: tag

    fun tags(lang: Lang, tags: List<String>): List<String> =
        if (lang == Lang.DEFAULT) tags else tags.map { tag(lang, it) }

    fun section(lang: Lang, name: String): String = bundles[lang]?.content?.sections?.get(name) ?: name

    /**
     * Subsection headings are a mix of generic words ("Infrastructure") and
     * proper nouns ("Balearic Islands", "M-072"). Only the first kind has an
     * entry, so the rest come back as the guide wrote them - which is right.
     */
    fun subsection(lang: Lang, name: String): String =
        bundles[lang]?.content?.subsections?.get(name) ?: name

    /** The clue's explanation, or the English paragraphs when it is not translated yet. */
    fun clueText(lang: Lang, clueId: String, fallback: List<String>): List<String> =
        translatedClueText(lang, clueId) ?: fallback

    /** Whether [clueId] has its explanation in [lang] at all. */
    fun hasClueText(lang: Lang, clueId: String): Boolean =
        lang == Lang.DEFAULT || translatedClueText(lang, clueId) != null

    private fun translatedClueText(lang: Lang, clueId: String): List<String>? =
        bundles[lang]?.clueText?.get(clueId)?.takeIf { it.isNotEmpty() }

    /** How many clues of [clueIds] this language can explain in its own words. */
    fun clueCoverage(lang: Lang, clueIds: Collection<String>): Int {
        val texts = bundles[lang]?.clueText ?: return 0
        return clueIds.count { texts[it]?.isNotEmpty() == true }
    }

    companion object {
        /** Everything in English: what the tests and a language-less build use. */
        val NONE = Translations(emptyMap())

        /** One language built straight from values, rather than read from the jar. */
        fun of(
            lang: Lang,
            content: ContentBundle = ContentBundle(),
            clueText: Map<String, List<String>> = emptyMap(),
        ) = Translations(mapOf(lang to Bundle(content, clueText)))

        private val log = LoggerFactory.getLogger(Translations::class.java)
        private val json = Json { ignoreUnknownKeys = true }

        /** Reads every non-default language out of the jar; a missing one is simply absent. */
        fun load(): Translations {
            val bundles = Lang.entries
                .filter { it != Lang.DEFAULT }
                .mapNotNull { lang -> read(lang)?.let { lang to it } }
                .toMap()
            return Translations(bundles)
        }

        private fun read(lang: Lang): Bundle? {
            val content = resource("/seed/" + lang.wire + "/content.json")
                ?.let { runCatching { json.decodeFromString<ContentBundle>(it) }.getOrNull() }
            val clueText = resource("/seed/" + lang.wire + "/clues.json")
                ?.let { runCatching { json.decodeFromString<Map<String, List<String>>>(it) }.getOrNull() }
                .orEmpty()
            if (content == null && clueText.isEmpty()) {
                log.warn("No translation bundle for {} - it will be served in English", lang.wire)
                return null
            }
            log.info(
                "Loaded {} translations: {} countries, {} chapter headings, {} explained clues",
                lang.wire,
                content?.countries?.size ?: 0,
                (content?.sections?.size ?: 0) + (content?.subsections?.size ?: 0),
                clueText.size,
            )
            return Bundle(content ?: ContentBundle(), clueText)
        }

        private fun resource(path: String): String? =
            Translations::class.java.getResourceAsStream(path)?.use { it.readBytes().decodeToString() }
    }
}
