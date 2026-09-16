package net.geoclue.trainer.data

import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import java.text.Normalizer

/**
 * Works out which part of its own country a regional clue is about.
 *
 * The guides have no field for it: a regional chapter is filed under headings
 * like "Infrastructure" or "Landscape", and the place itself is only ever named
 * in the prose - "these bands are found in Shikoku", "the most open
 * agricultural landscape is in Skåne". So the region is read out of the
 * sentence, and only where the sentence is unmistakably pointing at a place:
 *
 *  - a capitalised name is a mention when a spatial preposition points at it
 *    ("in", "around", "across", "near", ...), or when "of" does after a word
 *    that makes it spatial ("north of", "the coast of"). Weaker prepositions
 *    are left out on purpose: "related to Portuguese" and "a mix of Spanish
 *    and Basque signs" name languages, not places;
 *  - a lowercase modifier in front of the name is skipped ("in southern
 *    Chelyabinsk Oblast"), a capitalised one is part of it, which is what keeps
 *    "Lower Saxony" and "North Carolina" whole;
 *  - a name the guide mostly uses as an adjective is dropped, because that is
 *    what a demonym looks like: "Brazilian state", "Catalan word" and "Russian
 *    olive" all put a lowercase noun straight after the name, while "in Skåne
 *    you will find" does not;
 *  - a name has to be mentioned by [MIN_CLUES] clues of the country before it
 *    is quizzed on, which drops the one-off villages and the parsing accidents;
 *  - a name claimed more often by another country belongs to that country -
 *    "British Columbia" is Canada's, however many US clues mention it.
 *
 * A clue is finally only playable when it names exactly one region, so that
 * "found in Schleswig-Holstein and Lower Saxony" never becomes a question with
 * two right answers, and only for a country with [MIN_BOARD] regions to fill a
 * board with.
 */
class ClueRegions(countries: List<Country>) {

    private val countryNames: Set<String> =
        countries.mapTo(mutableSetOf()) { it.name.lowercase() } + EXTRA_COUNTRY_NAMES

    /** Clue id to the region its text places it in, plus each country's regions. */
    class Index(
        val regionOfClue: Map<String, String>,
        val regionsByCountry: Map<String, List<String>>,
    ) {
        /** The region [clue] is about, or null when its text does not pin one down. */
        fun regionOf(clue: Clue): String? = regionOfClue[clue.id]

        fun regionsOf(countryCode: String): List<String> = regionsByCountry[countryCode].orEmpty()
    }

    fun index(clues: List<Clue>): Index {
        // Regional clues only: a "how to identify this country" clue is the
        // country game's material, and saying "mostly in the north" about the
        // country as a whole does not make it a region question.
        val regional = clues.filterNot { it.isCore }

        val mentions: Map<String, List<Mention>> = regional.associate { it.id to mentionsIn(it) }
        val counted = HashMap<String, MutableMap<String, Count>>()
        for (clue in regional) {
            val perCountry = counted.getOrPut(clue.countryCode) { HashMap() }
            // One clue counts once per name, however often it repeats it.
            for ((name, said) in mentions.getValue(clue.id).groupBy { it.name }) {
                val count = perCountry.getOrPut(name) { Count() }
                count.clues++
                if (said.all { it.attributive }) count.attributive++
                if (said.any { it.located }) count.located++
            }
        }

        // Whoever places a name most owns it; a tie leaves it with both, which
        // is right for the Basque Country and harmless everywhere else.
        val mostLocated = HashMap<String, Int>()
        for (names in counted.values) {
            for ((name, count) in names) {
                mostLocated[name] = maxOf(mostLocated[name] ?: 0, count.located)
            }
        }

        // "Goiás" and "Goias" are the same state, and two of them on one board
        // would be a question with two right answers. Spellings that differ only
        // in their accents collapse onto the one the guide writes most often.
        val canonical: Map<String, Map<String, String>> = counted.mapValues { (_, names) ->
            names.filter { (name, count) ->
                count.located >= MIN_CLUES &&
                    count.attributive * 2 <= count.clues &&
                    count.located >= mostLocated.getValue(name)
            }.entries
                .groupBy { fold(it.key) }
                .mapValues { (_, spellings) -> spellings.maxBy { it.value.clues }.key }
        }

        val regionsByCountry = canonical
            .mapValues { (_, byFolded) -> byFolded.values.sorted() }
            .filterValues { it.size >= MIN_BOARD }

        val regionOfClue = buildMap {
            for (clue in regional) {
                if (clue.countryCode !in regionsByCountry) continue
                val known = canonical.getValue(clue.countryCode)
                val named = mentions.getValue(clue.id)
                    .filter { it.located }
                    .mapNotNull { known[fold(it.name)] }
                    .distinct()
                // "Rio Grande" inside "Rio Grande do Sul" is the same mention.
                val distinct = named.filterNot { name -> named.any { it != name && contains(it, name) } }
                if (distinct.size == 1) put(clue.id, distinct.first())
            }
        }
        return Index(regionOfClue, regionsByCountry)
    }

    /** How many clues used a name, placed it somewhere, or only modified a noun with it. */
    private class Count(var clues: Int = 0, var located: Int = 0, var attributive: Int = 0)

    /**
     * One capitalised name as the guide wrote it. [located] marks the ones a
     * preposition puts the clue at, which are the only region candidates; the
     * rest are counted anyway, because a name the guide overwhelmingly uses in
     * front of a noun is a demonym ("Spanish signage") rather than a place.
     */
    private class Mention(val name: String, val located: Boolean, val attributive: Boolean)

    private fun mentionsIn(clue: Clue): List<Mention> {
        val out = mutableListOf<Mention>()
        for (paragraph in clue.text) {
            for (sentence in SENTENCE.split(strip(paragraph))) {
                val words = TOKEN.findAll(sentence).map { it.value }.toList()
                val lower = words.map { it.lowercase() }
                var i = 0
                while (i < words.size) {
                    if (pointsAtAPlace(lower, i)) {
                        var j = i + 1
                        var found = false
                        // One preposition can govern a list: "in Bahia, Ceará and Pernambuco".
                        while (j < words.size) {
                            // "in southern Chelyabinsk Oblast" - "Lower Saxony" keeps its "Lower".
                            while (j < words.size && lower[j] in MODIFIERS && words[j][0].isLowerCase()) j++
                            val end = nameEnd(words, lower, j)
                            val name = nameBetween(words, j, end)
                            if (name.isEmpty()) break
                            found = true
                            record(out, name, located = true, after = lower.getOrNull(end))
                            j = end
                            if (j < words.size && lower[j] in LIST_JOINERS) j++ else break
                        }
                        i = if (found) j else i + 1
                    } else if (words[i][0].isUpperCase() && lower[i] !in PROSE) {
                        val end = nameEnd(words, lower, i)
                        val name = nameBetween(words, i, end)
                        if (name.isNotEmpty()) record(out, name, located = false, after = lower.getOrNull(end))
                        i = maxOf(end, i + 1)
                    } else {
                        i++
                    }
                }
            }
        }
        return out
    }

    private fun record(out: MutableList<Mention>, name: String, located: Boolean, after: String?) {
        if (!isPlausible(name)) return
        val attributive = after != null && after[0].isLowerCase() && after !in NEUTRAL_AFTER
        out += Mention(name, located, attributive)
    }

    /** Where the capitalised run starting at [from] ends. */
    private fun nameEnd(words: List<String>, lower: List<String>, from: Int): Int {
        var j = from
        while (j < words.size) {
            val isName = words[j][0].isUpperCase() && lower[j] !in PROSE
            val joins = lower[j] in CONNECTORS && j + 1 < words.size && words[j + 1][0].isUpperCase()
            if (isName || joins) j++ else break
        }
        return j
    }

    private fun nameBetween(words: List<String>, from: Int, until: Int): String =
        words.subList(from, until)
            .dropWhile { it.lowercase() in CONNECTORS }
            .dropLastWhile { it.lowercase() in CONNECTORS }
            .joinToString(" ")

    /** True when the word at [i] is a preposition that places whatever follows it. */
    private fun pointsAtAPlace(lower: List<String>, i: Int): Boolean = when (lower[i]) {
        in SPATIAL -> true
        // "of" on its own is far too eager; "north of" and "the coast of" are not.
        "of" -> i > 0 && lower[i - 1] in OF_ANCHORS
        else -> false
    }

    private fun isPlausible(name: String): Boolean {
        val key = name.lowercase()
        return name.length > 2 && key !in countryNames && key !in NOT_A_REGION
    }

    /** True when [outer] contains [inner] as a whole word sequence. */
    private fun contains(outer: String, inner: String) =
        (" " + fold(outer) + " ").contains(" " + fold(inner) + " ")

    /** Drops accents and case, so "Goiás" and "Goias" are the same state. */
    private fun fold(name: String) =
        COMBINING.replace(Normalizer.normalize(name, Normalizer.Form.NFKD), "").lowercase()

    private fun strip(text: String) =
        EMPHASIS.replace(URL.replace(LINK.replace(text, "$1"), " "), " ")

    companion object {
        /** Mentioned by fewer clues than this and a name is a one-off, not a region. */
        const val MIN_CLUES = 2

        /** A country needs this many regions before its clues can fill a board. */
        const val MIN_BOARD = 3

        /**
         * Words, and every piece of punctuation as a token of its own - a comma
         * holds an enumeration of places together, and a bracket ends a name
         * rather than joining it to the next one ("Manas (previously
         * Jalal-Abad)" is one town, not three words of another).
         */
        private val TOKEN = Regex("[\\p{L}'’]+(?:-[\\p{L}'’]+)*|[^\\p{L}\\s]")
        private val SENTENCE = Regex("(?<=[.!?])\\s+|\\n+")
        private val COMBINING = Regex("\\p{Mn}+")
        private val LINK = Regex("\\[([^\\]]*)]\\([^)]*\\)")
        private val URL = Regex("https?://\\S+")
        private val EMPHASIS = Regex("[*_`]+")

        /** Prepositions that can only be putting the clue somewhere. */
        private val SPATIAL = setOf(
            "in", "into", "across", "throughout", "around", "near", "within", "along",
            "between", "outside", "through",
        )

        /** What must sit before "of" for it to be spatial: "the south of", "areas of". */
        private val OF_ANCHORS = setOf(
            "north", "south", "east", "west", "northeast", "northwest", "southeast", "southwest",
            "north-east", "north-west", "south-east", "south-west", "parts", "part", "areas", "area",
            "coast", "coasts", "region", "regions", "half", "edge", "edges", "centre", "center",
            "middle", "rest", "all", "most", "much", "out", "side", "sides", "corner", "tip",
        )

        /** What keeps "in Bahia, Ceará and Pernambuco" one list of three places. */
        private val LIST_JOINERS = setOf(",", "and", "or")

        /** Lowercase words that qualify a name rather than being part of it. */
        private val MODIFIERS = setOf(
            "the", "a", "an", "both", "either", "all", "most", "much", "part", "parts",
            "north", "south", "east", "west", "northern", "southern", "eastern", "western", "central",
            "northeast", "northwest", "southeast", "southwest", "northeastern", "northwestern",
            "southeastern", "southwestern", "far", "upper", "lower", "coastal", "inland", "rural",
            "mainland",
        )

        /** Lowercase words that can sit inside a name: "Rio de Janeiro", "Castilla y León". */
        private val CONNECTORS = setOf(
            "de", "del", "da", "do", "dos", "das", "di", "du", "van", "von", "der", "den",
            "la", "le", "el", "of", "y",
        )

        /** Capitalised only because a sentence started, or a stock guide word. */
        private val PROSE = setOf(
            "the", "this", "these", "those", "they", "it", "in", "on", "at", "a", "an", "and", "or",
            "but", "of", "for", "to", "from", "with", "is", "are", "was", "were", "be", "you", "your",
            "we", "our", "there", "here", "some", "many", "most", "more", "much", "few", "several",
            "other", "others", "also", "while", "however", "when", "where", "which", "who", "what",
            "why", "how", "if", "then", "than", "so", "such", "as", "by", "not", "no", "nor", "both",
            "all", "any", "each", "every", "either", "neither", "one", "two", "three", "note",
            "beware", "unlike", "similar", "example", "like", "after", "before", "during", "over",
            "under", "up", "down", "out", "off", "near", "far", "left", "right", "top", "bottom",
            "can", "could", "may", "might", "will", "would", "should", "must", "do", "does", "did",
            "have", "has", "had", "google", "street", "view", "generation", "gen", "trekker",
            "trekkers", "wikipedia",
        )

        /** After a name, these do not make it an adjective: "in Skåne you will find". */
        private val NEUTRAL_AFTER = setOf(
            "is", "are", "was", "were", "has", "have", "had", "can", "could", "will", "would",
            "may", "might", "and", "or", "but", "if", "then", "so", "as", "that", "which", "who",
            "where", "when", "while", "because", "although", "though", "than", "you", "it", "they",
        ) + SPATIAL + MODIFIERS

        /** Bigger than a country, so never an answer to "which part of it?". */
        private val NOT_A_REGION = setOf(
            "europe", "asia", "africa", "north america", "south america", "oceania", "antarctica",
            "scandinavia", "balkans", "middle east", "caribbean", "mediterranean", "latin america",
            "central america", "atlantic ocean", "pacific ocean", "indian ocean", "arctic",
            "south atlantic ocean", "north atlantic ocean", "the americas",
        )

        /** What the guides call countries whose dataset name is spelled differently. */
        private val EXTRA_COUNTRY_NAMES = setOf(
            "united states", "usa", "america", "uk", "britain", "great britain", "england",
            "holland", "czech republic", "korea", "united arab emirates", "uae", "macedonia",
        )
    }
}
