package net.geoclue.trainer.data

import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min

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
 * A region is established by those strict rules, but once a country's regions
 * are known a clue is placed by any mention of one - "Queensland features these
 * pole tops", "stickers on Queensland poles" - because a name that is already
 * a known region of that country is no longer a guess. The guide's own
 * abbreviations count as the full name ("NSW", "QLD").
 *
 * A clue is placed by the first sentence of its main text that names a region.
 * When that sentence names several - "common in Utah, Arizona, and Idaho" -
 * each of them is a right answer, and the board shows one of them against
 * regions the clue does not name at all, so "found in Schleswig-Holstein and
 * Lower Saxony" never becomes a question with two right answers on the board.
 * A sentence that contrasts ("unlike Kansai", "except in Yamaguchi") only
 * counts when it names a single region. Regions only its NOTE paragraphs bring up
 * ("NOTE: a few of these poles can be found in Tasmania") do not disqualify
 * it; they are kept off its board instead, the way [ClueAmbiguity] keeps a
 * country the clue vouches for off the country board. Named regions only play
 * in a country with [MIN_BOARD] of them to fill a board with.
 *
 * Many clues name no region at all and place themselves by bearing instead:
 * "common in northern Finland", "in the northeast you will find", "the southern
 * half of the country". Those become compass questions - North, South-west,
 * Centre, ... - on a board of their own, whose wrong answers point well away
 * from the right one ([COMPASS_SPREAD]), since "north" against "north-east"
 * would be a question with two right answers. A clue that names a region is
 * never turned into a compass question, because "Lapland" and "North" on one
 * board could both be right.
 */
class ClueRegions(countries: List<Country>) {

    // "Israel & the West Bank" is plain "Israel" in the prose.
    private val countryNames: Set<String> = countries.flatMapTo(mutableSetOf()) { country ->
        val name = country.name.lowercase()
        listOf(name) + name.split(" & ").map { it.trim().removePrefix("the ") }
    } + EXTRA_COUNTRY_NAMES

    /** How the guide can say "this country" in a compass phrase, per country code, as words. */
    private val ownNames: Map<String, List<List<String>>> = countries.associate { country ->
        val names = country.name.lowercase().split(" & ").map { it.trim().removePrefix("the ") } +
            country.name.lowercase() + OWN_ALIASES[country.code].orEmpty()
        country.code to names.distinct().map { words(it) }
    }

    /**
     * Clue id to the region its text places it in, each country's named
     * regions, and per clue the wrong answers that may share its board.
     */
    class Index(
        val regionOfClue: Map<String, String>,
        val regionsByCountry: Map<String, List<String>>,
        val distractorsOfClue: Map<String, List<String>> = emptyMap(),
        val answersOfClue: Map<String, List<String>> = regionOfClue.mapValues { listOf(it.value) },
        /** Per country, the regions the guide calls cities or prefectures; the rest are plain regions. */
        val tierOfRegion: Map<String, Map<String, Tier>> = emptyMap(),
    ) {
        /** The region [clue] is about, or null when its text does not pin one down. */
        fun regionOf(clue: Clue): String? = regionOfClue[clue.id]

        /**
         * Every region the clue's own sentence names - "Utah, Arizona, and
         * Idaho" - any one of which may stand as the right answer on a board.
         */
        fun answersOf(clue: Clue): List<String> = answersOfClue[clue.id].orEmpty()

        /** The named regions of a country; compass bearings are not listed here. */
        fun regionsOf(countryCode: String): List<String> = regionsByCountry[countryCode].orEmpty()

        /**
         * What may be offered as a wrong answer for [clue]: the other named
         * regions of its country, or the bearings pointing well away from its
         * own - minus whatever its text says it also fits.
         */
        fun distractorsOf(clue: Clue): List<String> = distractorsOfClue[clue.id].orEmpty()

        /** True when [clue] is placed by a compass bearing rather than a named region. */
        fun isCompass(clue: Clue): Boolean = regionOfClue[clue.id] in COMPASS_LABELS
    }

    fun index(clues: List<Clue>): Index {
        // Regional clues only: a "how to identify this country" clue is the
        // country game's material, and saying "mostly in the north" about the
        // country as a whole does not make it a region question.
        val regional = clues.filterNot { it.isCore }

        val mentions: Map<String, List<Mention>> = regional.associate { it.id to mentionsIn(it) }
        // Counted per spelling-free key, so "Akita" and "Akita Prefecture", or
        // "Goiás" and "Goias", add up to one region rather than two half ones.
        val counted = HashMap<String, MutableMap<String, Count>>()
        val spellings = HashMap<String, MutableMap<String, MutableMap<String, Int>>>()
        for (clue in regional) {
            val perCountry = counted.getOrPut(clue.countryCode) { HashMap() }
            val written = spellings.getOrPut(clue.countryCode) { HashMap() }
            // One clue counts once per name, however often it repeats it.
            for ((name, said) in mentions.getValue(clue.id).groupBy { key(it.name) }) {
                val count = perCountry.getOrPut(name) { Count() }
                count.clues++
                if (said.all { it.attributive }) count.attributive++
                if (said.any { it.located }) count.located++
                for (mention in said) written.getOrPut(name) { HashMap() }.merge(mention.name, 1, Int::plus)
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

        // Each region is then shown the way the guide writes it most often.
        val canonical: Map<String, Map<String, String>> = counted.mapValues { (country, names) ->
            // "In Oklahoma, you can often find..." and "Oklahoma uses..." make a
            // state: one sentence placing the clue there and a second mention,
            // as long as the guide does not mostly use it as an adjective.
            names.filter { (name, count) ->
                count.located >= 1 && count.clues >= MIN_CLUES &&
                    count.attributive * 2 <= count.clues &&
                    count.located >= mostLocated.getValue(name)
            }.mapValues { (name, _) -> spellings.getValue(country).getValue(name).maxBy { it.value }.key }
        }

        // A region is a city once the guide calls it one anywhere - "the city
        // of Cairns" - and a prefecture likewise; the rest are plain regions.
        val tierOfRegion = HashMap<String, MutableMap<String, Tier>>()
        for (clue in regional) {
            val known = canonical[clue.countryCode] ?: continue
            val perCountry = tierOfRegion.getOrPut(clue.countryCode) { HashMap() }
            for (mention in mentions.getValue(clue.id)) {
                val region = known[key(mention.name)] ?: continue
                if (mention.tier.ordinal > (perCountry[region] ?: Tier.REGION).ordinal) perCountry[region] = mention.tier
            }
        }
        fun tier(country: String, region: String) = tierOfRegion[country]?.get(region) ?: Tier.REGION

        val regionsByCountry = canonical
            .mapValues { (_, byFolded) -> byFolded.values.sorted() }
            .filterValues { it.size >= MIN_BOARD }

        // A clue that names exactly two regions tends to name a place and what
        // holds it ("Mount Gambier, South Australia"), or two that neighbour and
        // share the trait - either way, neither is a safe wrong answer for the
        // other. A longer list ("WA, QLD, NSW, ACT and Tas") says much less
        // about any one pair, and would leave some countries without a board.
        val related = HashMap<String, MutableMap<String, MutableSet<String>>>()
        for (clue in clues) {
            val known = canonical[clue.countryCode] ?: continue
            val named = namedIn(mentions[clue.id] ?: mentionsIn(clue), known)
            if (named.size != 2) continue
            val perCountry = related.getOrPut(clue.countryCode) { HashMap() }
            perCountry.getOrPut(named[0]) { HashSet() } += named[1]
            perCountry.getOrPut(named[1]) { HashSet() } += named[0]
        }

        val regionOfClue = HashMap<String, String>()
        val answersOfClue = HashMap<String, List<String>>()
        val distractorsOfClue = HashMap<String, List<String>>()
        for (clue in regional) {
            val known = canonical[clue.countryCode].orEmpty()
            val said = mentions.getValue(clue.id)
            val main = said.filterNot { it.inNote }
            val mainNames = namedIn(main, known)

            val answers: List<String>
            val distractors: List<String>
            if (mainNames.isNotEmpty() || resolve(clue.subsection, known) != null) {
                // The first sentence that names a region says where the clue
                // is; what later sentences add ("they can rarely be seen in
                // Haryana") qualifies it, and stays off the board.
                val first = main.filter { resolve(it.name, known) != null }.minOfOrNull { it.sentence }
                val claim = main.filter { it.sentence == first }
                val named = namedIn(claim, known)
                // "Unlike Kansai, poles in Shikoku..." is about Shikoku: the
                // regions the sentence puts the clue in win over ones it only
                // compares against.
                val located = namedIn(claim.filter { it.located }, known)
                answers = when {
                    first == null -> listOfNotNull(resolve(clue.subsection, known))
                    claim.any { it.contrasted } -> when {
                        located.size == 1 -> located
                        named.size == 1 -> named
                        else -> continue
                    }
                    located.isNotEmpty() -> located
                    else -> named
                }
                if (answers.size > MAX_ANSWERS) continue
                val board = regionsByCountry[clue.countryCode] ?: continue
                val alsoFits = namedIn(said, known) +
                    answers.flatMap { related[clue.countryCode]?.get(it).orEmpty() }
                val kind = tier(clue.countryCode, answers.first())
                distractors = board.filter { other ->
                    other !in answers && other !in alsoFits && tier(clue.countryCode, other) == kind &&
                        answers.none { contains(other, it) || contains(it, other) }
                }
            } else {
                // No named region anywhere in the main text: try the bearing.
                // A bare "in the west" is the country's west only when the clue
                // is not about some place of its own - "the reserve uses short
                // dashes in the west" is the west of the reserve.
                val own = ownNames[clue.countryCode].orEmpty()
                val aboutAPlace = main.any { it.located || ' ' in it.name }
                val bearing = bearingsIn(clue.text.filterNot { isNote(it) }, own, bare = !aboutAPlace)
                    .distinct().singleOrNull() ?: continue
                // Every bearing the text so much as mentions stays off the board,
                // relative or not: "not in the northwest or far south" must not
                // make "South" a wrong answer.
                val alsoFits = clue.text.flatMap { paragraph ->
                    TOKEN.findAll(strip(paragraph)).mapNotNull { COMPASS_WORDS[it.value.lowercase()] }
                }
                answers = listOf(bearing.label)
                distractors = Compass.entries
                    .filter { it.isFarFrom(bearing) && it !in alsoFits }
                    .map { it.label }
            }
            if (distractors.size < MIN_BOARD - 1) continue
            regionOfClue[clue.id] = answers.first()
            answersOfClue[clue.id] = answers
            distractorsOfClue[clue.id] = distractors
        }
        return Index(regionOfClue, regionsByCountry, distractorsOfClue, answersOfClue, tierOfRegion)
    }

    /** The distinct known regions [mentions] name; "Rio Grande" inside "Rio Grande do Sul" is one. */
    private fun namedIn(mentions: List<Mention>, known: Map<String, String>): List<String> {
        val named = mentions.mapNotNull { resolve(it.name, known) }.distinct()
        return named.filterNot { name -> named.any { it != name && contains(it, name) } }
    }

    /**
     * The known region [name] refers to. "Southwest Texas" and "Northern
     * California" are Texas and California when no region of that full name
     * exists - which is what keeps "West Virginia" and "North Carolina" whole.
     */
    private fun resolve(name: String, known: Map<String, String>): String? {
        known[key(name)]?.let { return it }
        val words = name.split(' ')
        if (words.size < 2 || !isBearing(words[0])) return null
        return known[key(words.drop(1).joinToString(" "))]
    }

    /** "Northern", "Southwest", "West-Central": a bearing, however compounded. */
    private fun isBearing(word: String) = word.lowercase().split('-').all { it in COMPASS_WORDS }

    /** How many clues used a name, placed it somewhere, or only modified a noun with it. */
    private class Count(var clues: Int = 0, var located: Int = 0, var attributive: Int = 0)

    /**
     * One capitalised name as the guide wrote it. [located] marks the ones a
     * preposition puts the clue at, which are the only region candidates; the
     * rest are counted anyway, because a name the guide overwhelmingly uses in
     * front of a noun is a demonym ("Spanish signage") rather than a place.
     * [inNote] marks a mention from a NOTE paragraph, which qualifies the clue
     * rather than saying where it is.
     */
    private class Mention(
        val name: String,
        val located: Boolean,
        val attributive: Boolean,
        val inNote: Boolean,
        /** Which sentence of the clue it is in, counting across paragraphs. */
        val sentence: Int,
        /** Its sentence compares or excludes: "unlike Kansai", "except in Yamaguchi". */
        val contrasted: Boolean,
        /** What the words around it call it: "the city of X", "X prefecture". */
        val tier: Tier,
    )

    /**
     * What kind of place a region is, as far as the guide lets on. A city and
     * the state around it, or a prefecture and its region, would both be right
     * on one board, so a board only ever holds regions of one tier.
     */
    enum class Tier { REGION, PREFECTURE, CITY }

    private fun mentionsIn(clue: Clue): List<Mention> {
        val out = mutableListOf<Mention>()
        val aliases = ALIASES[clue.countryCode].orEmpty()
        var index = 0
        for (paragraph in clue.text) {
            val inNote = isNote(paragraph)
            for (sentence in SENTENCE.split(strip(paragraph))) {
                val words = TOKEN.findAll(sentence).map { it.value }.toList()
                val lower = words.map { it.lowercase() }
                val at = index++
                val contrasted = lower.any { it in CONTRASTS }
                fun record(written: String, located: Boolean, from: Int, end: Int) {
                    // "Botswana’s roads": the place, not its possessive.
                    val name = POSSESSIVE.replace(written, "")
                    val full = aliases[name] ?: name
                    if (!isPlausible(full)) return
                    val after = lower.getOrNull(end)
                    // "the Kanto region" is a place; "the Bavarian region" a demonym.
                    val admin = after in ADMIN_NOUNS && DEMONYM.find(full) == null
                    val attributive = after != null && after[0].isLowerCase() && after !in NEUTRAL_AFTER && !admin
                    val tier = tierOf(full, after, lower.getOrNull(end + 1), lower.getOrNull(from - 1), lower.getOrNull(from - 2))
                    out += Mention(full, located, attributive, inNote, at, contrasted, tier)
                }
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
                            record(name, located = true, from = j, end = end)
                            j = end
                            // "Utah, Arizona, and Idaho": ", and" is one joiner.
                            if (j >= words.size || lower[j] !in LIST_JOINERS) break
                            while (j < words.size && lower[j] in LIST_JOINERS) j++
                        }
                        i = if (found) j else i + 1
                    } else if (words[i][0].isUpperCase() && lower[i] !in PROSE) {
                        val end = nameEnd(words, lower, i)
                        val name = nameBetween(words, i, end)
                        if (name.isNotEmpty()) record(name, located = false, from = i, end = end)
                        i = maxOf(end, i + 1)
                    } else {
                        i++
                    }
                }
            }
        }
        return out
    }

    /** "the city of Cairns", "Mexico City", "Aomori prefecture", "the Kanto region". */
    private fun tierOf(name: String, after: String?, afterThat: String?, before: String?, beforeThat: String?): Tier {
        val last = name.substringAfterLast(' ').lowercase()
        val noun = when {
            ' ' in name && last in CITY_NOUNS + PREFECTURE_NOUNS -> last
            // "the QLD city of Cairns" makes Cairns the city, not Queensland.
            after in CITY_NOUNS + PREFECTURE_NOUNS && afterThat != "of" -> after
            before == "of" -> beforeThat
            else -> null
        }
        return when (noun) {
            in CITY_NOUNS -> Tier.CITY
            in PREFECTURE_NOUNS -> Tier.PREFECTURE
            else -> Tier.REGION
        }
    }

    /**
     * What two spellings of one region share: accents, case and the kind of
     * division dropped, so "Goiás" is "Goias" and "Chelyabinsk Oblast" is
     * "Chelyabinsk".
     */
    private fun key(name: String): String {
        val words = fold(name).split(' ')
        val core = if (words.size > 1 && words.last() in DIVISION_SUFFIXES) words.dropLast(1) else words
        return core.joinToString(" ")
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

    // Only a lowercase connector is trimmed: the "La" of "La Paz" is the city's own.
    private fun nameBetween(words: List<String>, from: Int, until: Int): String =
        words.subList(from, until)
            .dropWhile { it in CONNECTORS }
            .dropLastWhile { it in CONNECTORS }
            .joinToString(" ")

    /** True when the word at [i] is a preposition that places whatever follows it. */
    private fun pointsAtAPlace(lower: List<String>, i: Int): Boolean = when (lower[i]) {
        in SPATIAL -> true
        // "of" on its own is far too eager; "north of" and "the state of" are not.
        "of" -> i > 0 && lower[i - 1] in OF_ANCHORS
        // "related to Portuguese" names a language; "unique to Queensland" a place.
        "to" -> i > 0 && lower[i - 1] in TO_ANCHORS
        else -> false
    }

    private fun isPlausible(name: String): Boolean {
        val key = name.lowercase()
        // "the Rocky Mountains", "the Gulf Coast": landforms run across regions.
        val landform = ' ' in key && key.substringAfterLast(' ') in LANDFORMS
        return name.length > 2 && key !in countryNames && key !in NOT_A_REGION && !landform
    }

    /**
     * The compass bearings [paragraphs] place the clue at within its own
     * country, whose names are [own]. Only phrases that can only mean the
     * country's own north count: "northern Finland", "the southern half of the
     * country", "in the far east". A bearing relative to anything else - "north
     * of the Jura", "the south of Queensland" - is not the country's.
     */
    private fun bearingsIn(paragraphs: List<String>, own: List<List<String>>, bare: Boolean): List<Compass> {
        val out = mutableListOf<Compass>()
        for (paragraph in paragraphs) {
            for (sentence in SENTENCE.split(strip(paragraph))) {
                val words = TOKEN.findAll(sentence).map { it.value }.toList()
                val lower = words.map { it.lowercase() }
                // "Generation 4 is not found in the northwest" places nothing.
                if (lower.any { it in NEGATIONS || it.endsWith("n't") || it.endsWith("n’t") }) continue
                for (i in words.indices) {
                    val bearing = COMPASS_WORDS[lower[i]] ?: continue
                    // "Northern Territory", "the North Island": a capitalised
                    // bearing mid-sentence is part of a name - unless the name
                    // is the country's own, as in "Northeast India".
                    if (words[i][0].isUpperCase() && i > 0 && !ownAt(lower, i + 1, own)) continue
                    val hit = if (lower[i] in COMPASS_ADJECTIVES || words[i][0].isUpperCase()) {
                        adjectiveBearing(lower, i, own) &&
                            // ...and "Northern Ireland" opening a sentence still is.
                            (i + 1 >= lower.size || lower[i] + " " + lower[i + 1] !in PROPER_BEARINGS)
                    } else {
                        nounBearing(lower, i, own, bare)
                    }
                    if (!hit) continue
                    out += bearing
                    // "northern and eastern Finland" names both, not just the last.
                    var k = skipBack(lower, i - 1)
                    while (k >= 1 && lower[k] in LIST_JOINERS) {
                        out += COMPASS_WORDS[lower[k - 1]] ?: break
                        k = skipBack(lower, k - 2)
                    }
                    // ...and "the northwest or far south" names both, not just the first.
                    var j = i + 1
                    while (j < lower.size && lower[j] in LIST_JOINERS) {
                        j = skipAhead(lower, j + 1)
                        out += COMPASS_WORDS[lower.getOrNull(j) ?: break] ?: break
                        j++
                    }
                }
            }
        }
        return out
    }

    /** "northern Finland", "the southern half of the country", "the central regions". */
    private fun adjectiveBearing(lower: List<String>, i: Int, own: List<List<String>>): Boolean {
        val j = i + 1
        if (ownAt(lower, j, own)) return true
        if (lower.getOrNull(j) !in AREA_NOUNS) return false
        if (lower.getOrNull(j + 1) == "of") return ownAt(lower, j + 2, own)
        return lower.getOrNull(before(lower, i)) == "the"
    }

    /**
     * "the far north of the country", and when [bare] also "in the northeast"
     * and "along the east coast", which say nothing of what they are the north of.
     */
    private fun nounBearing(lower: List<String>, i: Int, own: List<List<String>>, bare: Boolean): Boolean {
        val the = before(lower, i)
        if (lower.getOrNull(the) != "the" || lower.getOrNull(the - 1) !in BEARING_LEADS) return false
        var j = i + 1
        if (lower.getOrNull(j) in COAST) j++
        return if (lower.getOrNull(j) == "of") ownAt(lower, j + 1, own) else bare
    }

    /** The index before [i], stepping over "far" and friends: "the far north". */
    private fun before(lower: List<String>, i: Int): Int {
        var k = i - 1
        while (k >= 0 && lower[k] in INTENSIFIERS) k--
        return k
    }

    /** From [k] backwards, past any "the" or "far": the word a list joiner would follow. */
    private fun skipBack(lower: List<String>, k: Int): Int {
        var at = k
        while (at >= 0 && (lower[at] == "the" || lower[at] in INTENSIFIERS)) at--
        return at
    }

    /** From [k] forwards, past any "the" or "far": the bearing a list joiner leads to. */
    private fun skipAhead(lower: List<String>, k: Int): Int {
        var at = k
        while (at < lower.size && (lower[at] == "the" || lower[at] in INTENSIFIERS)) at++
        return at
    }

    /** True when the words from [at] name the clue's own country, or say "the country". */
    private fun ownAt(lower: List<String>, at: Int, own: List<List<String>>): Boolean {
        if (lower.getOrNull(at) == "the" && lower.getOrNull(at + 1) == "country") return true
        val from = if (lower.getOrNull(at) == "the") at + 1 else at
        return own.any { name ->
            name.isNotEmpty() && name.indices.all { lower.getOrNull(from + it) == name[it] }
        }
    }

    private fun isNote(paragraph: String) = NOTE.containsMatchIn(paragraph)

    /** True when [outer] contains [inner] as a whole word sequence. */
    private fun contains(outer: String, inner: String) =
        (" " + fold(outer) + " ").contains(" " + fold(inner) + " ")

    /** Drops accents and case, so "Goiás" and "Goias" are the same state. */
    private fun fold(name: String) =
        COMBINING.replace(Normalizer.normalize(name, Normalizer.Form.NFKD), "").lowercase().replace('’', '\'')

    private fun strip(text: String) =
        EMPHASIS.replace(URL.replace(LINK.replace(text, "$1"), " "), " ")

    private fun words(text: String) = TOKEN.findAll(text).map { it.value }.toList()

    /**
     * The compass answers. Each bearing but the centre has a direction, and a
     * wrong answer must point at least [COMPASS_SPREAD] degrees away from the
     * right one; the centre is far from every edge.
     */
    enum class Compass(val label: String, private val degrees: Int?) {
        NORTH("North", 0),
        NORTH_EAST("North-east", 45),
        EAST("East", 90),
        SOUTH_EAST("South-east", 135),
        SOUTH("South", 180),
        SOUTH_WEST("South-west", 225),
        WEST("West", 270),
        NORTH_WEST("North-west", 315),
        CENTRE("Centre", null),
        ;

        fun isFarFrom(other: Compass): Boolean {
            if (this == other) return false
            if (degrees == null || other.degrees == null) return true
            val apart = abs(degrees - other.degrees) % 360
            return min(apart, 360 - apart) >= COMPASS_SPREAD
        }
    }

    companion object {
        /** Mentioned by fewer clues than this and a name is a one-off, not a region. */
        const val MIN_CLUES = 2

        /** A country needs this many regions before its clues can fill a board. */
        const val MIN_BOARD = 3

        /**
         * A sentence naming more regions than this is saying "much of the
         * country" rather than where the clue is.
         */
        const val MAX_ANSWERS = 4

        /**
         * How far apart, in degrees, a wrong bearing must be from the right one.
         * "North" against "North-east" or even "East" is a question the guide's
         * own wording cannot settle; against "South-east" it can.
         */
        const val COMPASS_SPREAD = 135

        /** Every compass answer, as it is sent to the client. */
        val COMPASS_LABELS: Set<String> = Compass.entries.mapTo(LinkedHashSet()) { it.label }

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

        /** "NOTE: ...", "**Note:** ..." - a paragraph that qualifies the clue. */
        private val NOTE = Regex("^\\W*note\\b", RegexOption.IGNORE_CASE)

        /** Prepositions that can only be putting the clue somewhere. */
        private val SPATIAL = setOf(
            "in", "into", "across", "throughout", "around", "near", "within", "along",
            "between", "outside", "through",
        )

        /** What must sit before "of" for it to be spatial: "the south of", "the state of". */
        private val OF_ANCHORS = setOf(
            "north", "south", "east", "west", "northeast", "northwest", "southeast", "southwest",
            "north-east", "north-west", "south-east", "south-west", "parts", "part", "areas", "area",
            "coast", "coasts", "region", "regions", "half", "edge", "edges", "centre", "center",
            "middle", "rest", "all", "most", "much", "out", "side", "sides", "corner", "tip",
            "state", "province", "prefecture", "county", "canton", "department", "district",
            "oblast", "governorate", "territory", "emirate", "voivodeship", "island", "city", "town",
        )

        /** What must sit before "to" for it to be spatial: "unique to", "exclusive to". */
        private val TO_ANCHORS = setOf(
            "unique", "specific", "exclusive", "exclusively", "endemic", "native", "limited",
            "restricted", "confined", "particular", "peculiar", "only",
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

        /** A bearing, however the guide spells it. */
        private val COMPASS_WORDS: Map<String, Compass> = buildMap {
            fun put(compass: Compass, vararg spellings: String) = spellings.forEach { put(it, compass) }
            put(Compass.NORTH, "north", "northern")
            put(Compass.SOUTH, "south", "southern")
            put(Compass.EAST, "east", "eastern")
            put(Compass.WEST, "west", "western")
            put(Compass.NORTH_EAST, "northeast", "north-east", "northeastern", "north-eastern")
            put(Compass.NORTH_WEST, "northwest", "north-west", "northwestern", "north-western")
            put(Compass.SOUTH_EAST, "southeast", "south-east", "southeastern", "south-eastern")
            put(Compass.SOUTH_WEST, "southwest", "south-west", "southwestern", "south-western")
            put(Compass.CENTRE, "central", "centre", "center")
        }

        /** The bearings that qualify a noun ("northern Finland") rather than being one. */
        private val COMPASS_ADJECTIVES = setOf(
            "northern", "southern", "eastern", "western", "northeastern", "north-eastern",
            "northwestern", "north-western", "southeastern", "south-eastern", "southwestern",
            "south-western", "central",
        )

        /** "Northern Ireland" is a place of its own, not the north of Ireland. */
        private val PROPER_BEARINGS = setOf(
            "northern ireland", "northern cyprus", "western australia", "western sahara",
        )

        /** What a bearing adjective may describe: "the southern half of the country". */
        private val AREA_NOUNS = setOf(
            "part", "parts", "half", "region", "regions", "area", "areas", "coast", "coastline",
            "portion", "third", "side", "corner", "tip", "provinces", "states",
        )

        /** What a bearing noun may be followed by and still be one: "the east coast". */
        private val COAST = setOf("coast", "coastline", "side", "part", "parts")

        /**
         * What may put the clue at "the north": "in the north", "along the east
         * coast". "and the east" counts too - it can only add a second bearing,
         * which keeps the clue off the board rather than on it with one answer.
         */
        private val BEARING_LEADS = SPATIAL + setOf("on", "and", "or")

        private val INTENSIFIERS = setOf("far", "extreme", "very", "deep")

        /**
         * A sentence with one of these weighs one region against another ("like
         * Sucre"), or spans a range whose middle it never names ("stretching
         * from Pennsylvania to Georgia").
         */
        private val CONTRASTS = setOf(
            "unlike", "except", "whereas", "instead", "not", "never", "rather", "similar",
            "similarly", "compared", "contrast", "outside", "like", "resemble", "resembles",
            "stretching", "stretches", "stretch", "spanning", "spans", "between",
        )

        /** Words that make a name before them a place: "the Kanto region", "Aomori prefecture". */
        private val ADMIN_NOUNS = setOf(
            "region", "regions", "prefecture", "province", "state", "county", "district", "island",
            "islands", "peninsula", "valley", "oblast", "department", "governorate", "municipality",
            "canton", "territory", "area", "coast",
        )

        private val LANDFORMS = setOf(
            "mountains", "range", "sea", "ocean", "coast", "plains", "plain", "highlands",
            "lowlands", "desert", "plateau", "basin",
        )

        // Not "capital": "the capital of Queensland" is Queensland's, not a city.
        private val CITY_NOUNS = setOf("city", "town", "village", "metropolitan")

        private val PREFECTURE_NOUNS = setOf("prefecture", "county", "district", "municipality")

        /** The kind of division a name may carry or drop: "Tuva Republic", "Tuva". */
        private val DIVISION_SUFFIXES = setOf(
            "oblast", "krai", "republic", "prefecture", "province", "governorate", "region",
            "state", "county", "district", "department",
        )

        private val POSSESSIVE = Regex("['’]s$")

        /** "Bavarian", "Japanese", "Spanish": an adjective of a place, not the place. */
        private val DEMONYM = Regex("(ian|ese|ish)$", RegexOption.IGNORE_CASE)

        /** A sentence with one of these says where the clue is not. */
        private val NEGATIONS = setOf("not", "never", "no", "rarely", "seldom", "absent", "except", "unlike")

        /** Bigger than a country, or a bearing, so never an answer to "which part of it?". */
        private val NOT_A_REGION = setOf(
            "europe", "asia", "africa", "north america", "south america", "oceania", "antarctica",
            "scandinavia", "balkans", "middle east", "caribbean", "mediterranean", "latin america",
            "central america", "atlantic ocean", "pacific ocean", "indian ocean", "arctic",
            "south atlantic ocean", "north atlantic ocean", "the americas",
        ) + COMPASS_WORDS.keys + setOf(
            // Areas that span several regions: "Deep South" against "Louisiana"
            // would be a question with two right answers.
            "deep south", "mid-south", "midwest", "new england", "appalachia", "northeast india",
            "north island", "south island", "amazon", "altiplano", "andes", "alps", "southern alps",
            "pyrenees", "greater metropolitan area",
            // "Road 23", "Ruta 9": a road number, not a place.
            "road", "ruta", "route", "highway",
            "mid-atlantic", "gulf coast", "himalayas", "himalayan", "eastern canada", "caspian sea",
            "rocky mountains", "great plains", "great lakes", "mexican", "borneo", "alpine",
        ) + setOf(
            "tamil", "telugu", "kannada", "malayalam", "marathi", "gujarati", "punjabi", "urdu",
            "hindi", "nepali", "sinhala", "persian", "farsi", "kurdish", "armenian", "georgian",
            "cyrillic", "latin", "devanagari",
        ) + setOf(
            // "in Catalan", "in French": a sign's language, not where it stands.
            "english", "french", "spanish", "catalan", "basque", "galician", "portuguese", "italian",
            "german", "dutch", "russian", "arabic", "hebrew", "swedish", "finnish", "norwegian",
            "danish", "turkish", "greek", "polish", "chinese", "japanese", "korean", "thai", "hindi",
            "bengali", "welsh", "gaelic", "irish",
        )

        /** What the guides call countries whose dataset name is spelled differently. */
        private val EXTRA_COUNTRY_NAMES = setOf(
            "united states", "usa", "america", "uk", "britain", "great britain", "england",
            "holland", "czech republic", "korea", "united arab emirates", "uae", "macedonia",
        )

        /** The same, as the "Finland" of "northern Finland", per country. */
        private val OWN_ALIASES: Map<String, List<String>> = mapOf(
            "US" to listOf("united states", "us", "usa", "america"),
            "GB" to listOf("uk", "britain", "great britain"),
            "CZ" to listOf("czech republic"),
            "NL" to listOf("netherlands", "holland"),
            "KR" to listOf("korea"),
            "AE" to listOf("uae"),
            "MK" to listOf("macedonia"),
        )

        /**
         * The abbreviations a guide uses as freely as the full name. Without
         * them "NSW" and "New South Wales" would be two states on one board.
         */
        private val ALIASES: Map<String, Map<String, String>> = mapOf(
            "ES" to mapOf("Canaries" to "Canary Islands", "Navarre" to "Navarra"),
            "BR" to mapOf("Rio Grande de Norte" to "Rio Grande do Norte"),
            "AU" to mapOf(
                "NSW" to "New South Wales",
                "QLD" to "Queensland",
                "Qld" to "Queensland",
                "WA" to "Western Australia",
                "SA" to "South Australia",
                "NT" to "Northern Territory",
                "ACT" to "Australian Capital Territory",
                "Tas" to "Tasmania",
                "Vic" to "Victoria",
            ),
        )
    }
}
