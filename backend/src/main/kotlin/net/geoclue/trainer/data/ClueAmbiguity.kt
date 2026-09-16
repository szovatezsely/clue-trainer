package net.geoclue.trainer.data

import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import java.text.Normalizer

/**
 * Works out which *other* countries a clue's own explanation presents as
 * fitting the clue as well.
 *
 * The guides routinely say so out loud - "NOTE: Peru, Brazil and Argentina are
 * the only South American countries with smallcam" - and a country named like
 * that must not be offered as a wrong answer: the player who picks it is
 * marked wrong and then shown an explanation agreeing with them.
 *
 * The opposite kind of mention is the reason these guides exist and must be
 * kept: "NOTE: Canada uses the word 'Maximum' on their speed signs" tells the
 * two apart, so Canada is exactly the distractor a US speed sign deserves.
 * Every sentence is therefore read for whichever of the two it is:
 *
 *  - a claim of a shared trait ("also", "the same", "similar", "found in",
 *    "like Sweden", "the other countries where ...") makes every country named
 *    in that sentence ambiguous;
 *  - so does being enumerated alongside the answer itself ("Peru, Brazil and
 *    Argentina"), which needs no marker word at all;
 *  - a contrast drawn immediately before the name ("unlike Germany", "in
 *    contrast to the UK") overrides both and keeps the country.
 *
 * When a sentence says neither, the mention is read as a contrast and kept -
 * that is the shape of "Canada uses 'Maximum'".
 */
class ClueAmbiguity(countries: List<Country>) {

    /** Lowercase word sequence (e.g. "south africa") to the countries it names. */
    private val byName: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        for (country in countries) {
            for (name in listOf(country.name) + EXTRA_NAMES[country.code].orEmpty()) {
                getOrPut(words(name).joinToString(" ")) { mutableSetOf() } += country.code
            }
        }
    }

    /** Names only recognised in capitals, so the pronoun "us" is not a country. */
    private val byAcronym: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        for ((code, acronyms) in ACRONYMS) {
            for (acronym in acronyms) getOrPut(acronym) { mutableSetOf() } += code
        }
    }

    private val longestName = byName.keys.maxOf { it.count { c -> c == ' ' } + 1 }

    /** Clue id to the country codes that clue's explanation also vouches for. */
    fun index(clues: List<Clue>): Map<String, Set<String>> = clues.asSequence()
        .map { it.id to ambiguousFor(it) }
        .filter { it.second.isNotEmpty() }
        .toMap()

    /** The countries [clue]'s explanation presents as fitting the clue as well. */
    fun ambiguousFor(clue: Clue): Set<String> {
        val prefix = clue.countryCode.take(2)
        val out = mutableSetOf<String>()
        for (paragraph in clue.text) {
            for (sentence in SENTENCE.split(strip(paragraph))) {
                val tokens = tokenize(sentence)
                val mentions = mentions(tokens)
                if (mentions.isEmpty()) continue
                val shared = mutableSetOf<String>()
                if (claimsSharedTrait(tokens.lower, mentions)) {
                    mentions.forEach { shared += it.codes }
                }
                shared += listedWithAnswer(tokens.lower, mentions, clue.countryCode)
                for (mention in mentions) {
                    if (tokens.lower.subList(maxOf(0, mention.from - 4), mention.from).any { it in CONTRAST }) {
                        shared -= mention.codes
                    }
                }
                shared.filterTo(out) { it.take(2) != prefix }
            }
        }
        return out
    }

    /** A country named in a sentence, as a half-open range of word positions. */
    private class Mention(val codes: Set<String>, val from: Int, val until: Int)

    /** The words of one sentence, both as written and folded to plain lowercase. */
    private class Tokens(val raw: List<String>, val lower: List<String>)

    private fun tokenize(sentence: String): Tokens {
        val raw = WORD.findAll(fold(sentence)).map { it.value }.toList()
        return Tokens(raw, raw.map { it.lowercase() })
    }

    private fun mentions(tokens: Tokens): List<Mention> {
        val out = mutableListOf<Mention>()
        var i = 0
        while (i < tokens.raw.size) {
            var hit: Mention? = null
            var length = minOf(longestName, tokens.raw.size - i)
            while (length > 0) {
                val codes = byName[tokens.lower.subList(i, i + length).joinToString(" ")]
                    ?: byAcronym[tokens.raw.subList(i, i + length).joinToString(" ")]
                // "New Mexico" and "New Jersey" are not Mexico and Jersey.
                if (codes != null && !(i > 0 && tokens.lower[i - 1] in BLOCK_PREFIX)) {
                    hit = Mention(codes, i, i + length)
                    break
                }
                length--
            }
            if (hit != null) {
                out += hit
                i = hit.until
            } else {
                i++
            }
        }
        return out
    }

    /** True when the sentence says the trait is shared with somewhere else. */
    private fun claimsSharedTrait(words: List<String>, mentions: List<Mention>): Boolean {
        val nameStarts = mentions.mapTo(HashSet()) { it.from }
        for ((i, word) in words.withIndex()) {
            if (words.subList(maxOf(0, i - 2), i).any { it in NEGATORS }) continue
            if (STEMS.any { word.startsWith(it) }) return true
            if (word in COUNTING && words.subList(i + 1, minOf(words.size, i + 5)).any { it in COUNTRY_NOUNS }) {
                return true
            }
            // "like Sweden", "like in Mexico" - but not "signs like this one".
            if (word == "like" && (i + 1 until minOf(words.size, i + 4)).any { it in nameStarts }) return true
            if (MARKERS.any { marker -> words.size - i >= marker.size && words.subList(i, i + marker.size) == marker }) {
                return true
            }
        }
        return false
    }

    /** Countries enumerated together with the answer: "Peru, Brazil and Argentina". */
    private fun listedWithAnswer(words: List<String>, mentions: List<Mention>, answer: String): Set<String> {
        val out = mutableSetOf<String>()
        val run = mutableListOf<Mention>()
        fun close() {
            if (run.any { answer in it.codes }) run.forEach { out += it.codes }
            run.clear()
        }
        for (mention in mentions) {
            val joined = run.isNotEmpty() &&
                words.subList(run.last().until, mention.from).all { it in CONJUNCTIONS }
            if (!joined) close()
            run += mention
        }
        close()
        return out
    }

    private fun words(text: String) = WORD.findAll(fold(text)).map { it.value.lowercase() }.toList()

    /** Drops accents, so "Réunion" and "Reunion" are the same word. */
    private fun fold(text: String) = COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFKD), "")

    /** Markdown link targets and bare URLs carry country slugs that are not mentions. */
    private fun strip(text: String) = URL.replace(LINK_TARGET.replace(text, "] "), " ")

    private companion object {
        val WORD = Regex("[A-Za-z]+")
        val COMBINING = Regex("\\p{Mn}+")
        val SENTENCE = Regex("(?<=[.!?])\\s+|\\n+")
        val URL = Regex("https?://\\S+")
        val LINK_TARGET = Regex("]\\([^)]*\\)")

        /** Names the guides use that a country's own dataset name does not cover. */
        val EXTRA_NAMES = mapOf(
            "US" to listOf("United States", "USA"),
            "GB" to listOf("Britain", "Great Britain", "England", "Scotland", "Wales", "Northern Ireland"),
            "NL" to listOf("Holland"),
            "CZ" to listOf("Czech Republic"),
            "AE" to listOf("United Arab Emirates"),
            "KR" to listOf("Korea"),
            "IL" to listOf("Israel", "West Bank", "Palestine"),
            "MK" to listOf("Macedonia"),
            "ST" to listOf("Sao Tome"),
            "CC" to listOf("Cocos Keeling Islands"),
            "GS" to listOf("South Georgia"),
        )
        val ACRONYMS = mapOf("US" to listOf("US"), "GB" to listOf("UK"), "AE" to listOf("UAE"))

        val BLOCK_PREFIX = setOf("new")

        /** Phrases that say the trait is not the answer country's alone. */
        val MARKERS = listOf(
            "also", "too", "as well", "same", "identical", "similar", "likewise", "alike", "both",
            "share", "shared", "shares", "including", "apart from", "except", "other than", "such as",
            "comparable", "additionally", "in addition", "elsewhere", "found", "see in", "seen in",
            "seen on", "appear in", "exist in", "present in", "common in", "used in", "common to",
            "confuse", "confused", "part of", "counted as",
        ).map { it.split(" ") }

        /** Matched on the stem, to cover "similarly" and "resembles" alike. */
        val STEMS = listOf("resembl", "similar")

        /** "the only/other countries where ..." always introduces the rest of them. */
        val COUNTING = setOf("other", "only")
        val COUNTRY_NOUNS = setOf("country", "countries", "nations")

        /** A marker under one of these is not a claim: "not common in Guam". */
        val NEGATORS = setOf("not", "never", "rarely", "seldom", "no", "nor", "hardly", "barely", "without")

        /** What may sit between two names and still be one enumeration. */
        val CONJUNCTIONS = setOf("and", "or", "as", "well", "nor", "the", "a", "an")

        /** Right before a name, these set it against the answer rather than beside it. */
        val CONTRAST = setOf("unlike", "contrast", "whereas", "opposed", "conversely", "versus")
    }
}
