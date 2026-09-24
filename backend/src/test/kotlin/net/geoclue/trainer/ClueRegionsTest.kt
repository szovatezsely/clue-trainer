package net.geoclue.trainer

import kotlinx.serialization.json.Json
import net.geoclue.trainer.data.ClueRegions
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.ClueDataset
import net.geoclue.trainer.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClueRegionsTest {

    private val japan = Country("JP", "Japan", "japan", "Asia", clueCount = 9)

    private fun regional(id: String, vararg text: String, country: String = "JP") =
        Clue(id = id, countryCode = country, imageUrl = "/i/$id.png", text = text.toList(), section = "Regional clues")

    private fun index(clues: List<Clue>, countries: List<Country> = listOf(japan)) =
        ClueRegions(countries).index(clues)

    @Test
    fun `a clue is placed in the region its own sentence points at`() {
        val clues = listOf(
            regional("a", "These orange and black bands wrapped around poles can be found in Shikoku."),
            regional("b", "These attachments with a 120-degree angle are common on poles in Shikoku."),
            regional("c", "An orange arrow above the top pole plate appears in Chugoku."),
            regional("d", "Pole plates in Chugoku are printed on a white background."),
            regional("e", "Snow poles are everywhere in Hokkaido."),
            regional("f", "Wide verges are typical in Hokkaido."),
        )

        val index = index(clues)

        assertEquals("Shikoku", index.regionOfClue["a"])
        assertEquals("Chugoku", index.regionOfClue["c"])
        assertEquals(listOf("Chugoku", "Hokkaido", "Shikoku"), index.regionsOf("JP"))
    }

    @Test
    fun `a clue naming two regions is left out rather than given two right answers`() {
        val clues = listOf(
            regional("a", "Found in Shikoku."),
            regional("b", "Also seen in Shikoku."),
            regional("c", "Found in Chugoku."),
            regional("d", "Also in Chugoku."),
            regional("e", "Found in Hokkaido."),
            regional("f", "Also in Hokkaido."),
            regional("h", "Found in Kansai."),
            regional("i", "Also in Kansai."),
            regional("g", "These markers are found in Shikoku and Chugoku."),
        )

        val index = index(clues)

        // Either one is right, so the board shows one of them and never both.
        assertEquals(listOf("Shikoku", "Chugoku"), index.answersOfClue["g"])
        assertEquals(setOf("Hokkaido", "Kansai"), index.distractorsOfClue.getValue("g").toSet())
    }

    @Test
    fun `a sentence that compares or spans regions does not make them all answers`() {
        val clues = listOf(
            regional("a", "Found in Shikoku."), regional("b", "Also seen in Shikoku."),
            regional("c", "Found in Chugoku."), regional("d", "Also in Chugoku."),
            regional("e", "Found in Hokkaido."), regional("f", "Also in Hokkaido."),
            regional("h", "Found in Kansai."), regional("i", "Also in Kansai."),
            regional("j", "Found in Kyushu."), regional("k", "Also in Kyushu."),
            regional("l", "Found in Tohoku."), regional("m", "Also in Tohoku."),
            regional("like", "Shikoku is also hilly, like Chugoku."),
            regional("except", "These stickers are found across Chugoku, except in Shikoku."),
            regional("unlike", "Unlike Kansai, poles in Hokkaido are painted."),
            regional("later", "Plates in Kansai are green. They can rarely be seen in Chugoku."),
        )

        val index = index(clues)

        assertNull(index.regionOfClue["like"], "\"like Chugoku\" compares, it does not place")
        assertNull(index.regionOfClue["except"], "\"except in Shikoku\" is exactly where it is not")
        assertEquals(listOf("Hokkaido"), index.answersOfClue["unlike"])
        assertTrue("Kansai" !in index.distractorsOfClue.getValue("unlike"))
        // The first sentence places it; the second only qualifies, and stays off the board.
        assertEquals(listOf("Kansai"), index.answersOfClue["later"])
        assertTrue("Chugoku" !in index.distractorsOfClue.getValue("later"))
    }

    @Test
    fun `the guide's own ways of naming a place still find it`() {
        val us = Country("US", "United States of America", "united-states", "North America", clueCount = 9)
        fun us(id: String, vararg text: String) = regional(id, *text, country = "US")
        val clues = listOf(
            us("a", "Found in Texas."), us("b", "Texas uses these signs."),
            us("c", "Found in Utah."), us("d", "Utah uses these signs."),
            us("e", "Found in Oregon."), us("f", "Oregon uses these signs."),
            us("g", "In Oklahoma, you can often find green signs."),
            us("h", "Oklahoma uses these signs."),
            us("bearing", "Shrubby trees are common in Southwest Texas."),
            us("list", "Square reflectors are common in Utah, Arizona, and Oregon."),
        )

        val index = index(clues, listOf(us))

        // One "in Oklahoma" and one other mention make a state.
        assertTrue("Oklahoma" in index.regionsOf("US"), index.regionsOf("US").toString())
        assertEquals(listOf("Texas"), index.answersOfClue["bearing"], "Southwest Texas is Texas")
        assertEquals(listOf("Utah", "Oregon"), index.answersOfClue["list"], "\", and\" still continues the list")
    }

    @Test
    fun `a region named with its division is a place, and one spelling`() {
        val clues = listOf(
            regional("a", "These bollards are found in the Kanto region."),
            regional("b", "Transformers in Kanto have three insulators."),
            regional("c", "Found in Aomori prefecture."),
            regional("d", "Aomori prefecture uses yellow stripes."),
            regional("e", "Found in Hokkaido."), regional("f", "Also in Hokkaido."),
            regional("g", "Found in Akita Prefecture."), regional("h", "Akita uses red stripes."),
            regional("i", "Found in Iwate prefecture."), regional("j", "Iwate has blue signs."),
            regional("k", "Found in Shikoku."), regional("l", "Also in Shikoku."),
        )

        val index = index(clues)
        val regions = index.regionsOf("JP")

        assertTrue("Kanto" in regions, "\"the Kanto region\" is not a demonym: $regions")
        assertEquals(1, regions.count { it.startsWith("Akita") }, "Akita and Akita Prefecture are one: $regions")
        // A prefecture never shares a board with a region that may hold it.
        assertEquals(ClueRegions.Tier.PREFECTURE, index.tierOfRegion["JP"]?.get("Aomori prefecture") ?: index.tierOfRegion["JP"]?.get("Aomori"))
        val aomori = index.distractorsOfClue.getValue("c")
        assertTrue(aomori.none { it in setOf("Kanto", "Hokkaido", "Shikoku") }, "prefecture against region: $aomori")
    }

    @Test
    fun `a name the guide only uses as an adjective is not a region`() {
        // "Japanese prefectures", "Japanese signage": always a noun straight after.
        val clues = listOf(
            regional("a", "Utility poles in Japanese prefectures carry a plate."),
            regional("b", "Road markings in Japanese cities are white."),
            regional("c", "Found in Shikoku."),
            regional("d", "Also in Shikoku."),
            regional("e", "Found in Chugoku."),
            regional("f", "Also in Chugoku."),
            regional("g", "Found in Kansai."),
            regional("h", "Also in Kansai."),
        )

        val index = index(clues)

        assertTrue("Japanese" !in index.regionsOf("JP"), "a demonym is not a place: " + index.regionsOf("JP"))
    }

    @Test
    fun `a capitalised modifier stays part of the name`() {
        val clues = listOf(
            regional("a", "These markers are found in Lower Saxony.", country = "DE"),
            regional("b", "Wooden posts appear in Lower Saxony.", country = "DE"),
            regional("c", "Found in southern Bavaria.", country = "DE"),
            regional("d", "Also in Bavaria.", country = "DE"),
            regional("e", "Found in Saxony-Anhalt.", country = "DE"),
            regional("f", "Also in Saxony-Anhalt.", country = "DE"),
        )

        val index = index(clues, listOf(Country("DE", "Germany", "germany", "Europe", clueCount = 6)))

        assertEquals("Lower Saxony", index.regionOfClue["a"])
        // "southern" is only a bearing; "Lower" is the state's actual name.
        assertEquals("Bavaria", index.regionOfClue["c"])
    }

    @Test
    fun `a country without a board of regions is left out of the game`() {
        val clues = listOf(
            regional("a", "Found in Shikoku."),
            regional("b", "Also in Shikoku."),
            regional("c", "Found in Chugoku."),
            regional("d", "Also in Chugoku."),
        )

        val index = index(clues)

        assertTrue(index.regionsOf("JP").isEmpty(), "two regions cannot fill a three-option board")
        assertTrue(index.regionOfClue.isEmpty())
    }

    @Test
    fun `country-level clues are never region questions`() {
        val core = Clue(
            id = "core",
            countryCode = "JP",
            imageUrl = "/i/core.png",
            text = listOf("Snow poles are found in Hokkaido, and elsewhere in the north."),
            section = "Identifying Japan",
        )
        val clues = listOf(
            core,
            regional("a", "Found in Shikoku."), regional("b", "Also in Shikoku."),
            regional("c", "Found in Chugoku."), regional("d", "Also in Chugoku."),
            regional("e", "Found in Hokkaido."), regional("f", "Also in Hokkaido."),
        )

        val index = index(clues)

        assertNull(index.regionOfClue["core"], "the country game owns the country-level clues")
    }

    @Test
    fun `a region belongs to the country that names it most`() {
        val countries = listOf(
            Country("CA", "Canada", "canada", "North America", clueCount = 6),
            Country("US", "United States of America", "united-states", "North America", clueCount = 6),
        )
        val clues = listOf(
            regional("ca1", "Poles in British Columbia are wooden.", country = "CA"),
            regional("ca2", "Chevrons in British Columbia are yellow.", country = "CA"),
            regional("ca3", "Signs in British Columbia are bilingual.", country = "CA"),
            regional("ca4", "Found in Alberta.", country = "CA"),
            regional("ca5", "Also in Alberta.", country = "CA"),
            regional("ca6", "Found in Yukon.", country = "CA"),
            regional("ca7", "Also in Yukon.", country = "CA"),
            regional("us1", "Rainforests grow in Oregon, and the same is true in British Columbia.", country = "US"),
            regional("us2", "Conifers grow in Washington, much as in British Columbia.", country = "US"),
            regional("us3", "Found in Texas.", country = "US"),
            regional("us4", "Also in Texas.", country = "US"),
            regional("us5", "Found in Utah.", country = "US"),
            regional("us6", "Also in Utah.", country = "US"),
            regional("us7", "Found in Nevada.", country = "US"),
            regional("us8", "Also in Nevada.", country = "US"),
        )

        val index = ClueRegions(countries).index(clues)

        assertTrue("British Columbia" in index.regionsOf("CA"))
        assertTrue(
            "British Columbia" !in index.regionsOf("US"),
            "a Canadian province is not a US answer: " + index.regionsOf("US"),
        )
    }

    @Test
    fun `a known region is placed however the sentence names it`() {
        val australia = Country("AU", "Australia", "australia", "Oceania", clueCount = 9)
        fun au(id: String, vararg text: String) = regional(id, *text, country = "AU")
        val clues = listOf(
            au("a", "Found in Queensland."), au("b", "Also in Queensland."),
            au("c", "Found in Tasmania."), au("d", "Also in Tasmania."),
            au("e", "Found in Victoria."), au("f", "Also in Victoria."),
            au("g", "Found in South Australia."),
            au("stobie", "The iconic Stobie pole is specific to South Australia."),
            au("subject", "Queensland features these unique pole tops."),
            au("noun", "Circular blue stickers can be found on Queensland poles."),
            au("abbr", "The landscape in inland NSW is mostly flat."),
            au("full", "Red No Stopping signs are only used in New South Wales."),
        )

        val index = index(clues, listOf(australia))

        assertEquals("South Australia", index.regionOfClue["stobie"], "\"specific to\" points at a place")
        assertEquals("Queensland", index.regionOfClue["subject"])
        assertEquals("Queensland", index.regionOfClue["noun"])
        assertEquals("New South Wales", index.regionOfClue["abbr"], "NSW is New South Wales")
        assertTrue("NSW" !in index.regionsOf("AU"), "an abbreviation must not be a second state: " + index.regionsOf("AU"))
    }

    @Test
    fun `a region a note brings up is kept off the board rather than losing the clue`() {
        val clues = listOf(
            regional("a", "Found in Shikoku."), regional("b", "Also in Shikoku."),
            regional("c", "Found in Chugoku."), regional("d", "Also in Chugoku."),
            regional("e", "Found in Hokkaido."), regional("f", "Also in Hokkaido."),
            regional("g", "Found in Kansai."), regional("h", "Also in Kansai."),
            regional("pole", "These poles are unique to Shikoku.", "NOTE: A few can also be found in Kansai."),
        )

        val index = index(clues)

        assertEquals("Shikoku", index.regionOfClue["pole"])
        val distractors = index.distractorsOfClue.getValue("pole")
        assertTrue("Kansai" !in distractors, "the note says Kansai fits too: $distractors")
        assertTrue("Shikoku" !in distractors)
    }

    @Test
    fun `a clue with no named region is placed by its bearing`() {
        val finland = Country("FI", "Finland", "finland", "Europe", clueCount = 9)
        fun fi(id: String, vararg text: String) = regional(id, *text, country = "FI")
        val clues = listOf(
            fi("adjective", "Small, stunted trees are common in northern Finland."),
            fi("half", "Farmland is concentrated in the southern half of the country."),
            fi("noun", "In the northeast you will find uncultivated grasslands."),
            fi("two", "Swedish is common in coastal areas in the south and the west."),
            fi("negated", "Generation 4 is not found in the northwest."),
            fi("relative", "Rock walls are found just north of Turku."),
            fi("placed", "The reserve at Koli National Park uses short dashes in the west."),
            fi("proper", "Northern Ireland uses these bollards."),
        )

        val index = index(clues, listOf(finland))

        assertEquals("North", index.regionOfClue["adjective"])
        assertEquals("South", index.regionOfClue["half"])
        assertEquals("North-east", index.regionOfClue["noun"])
        assertNull(index.regionOfClue["two"], "two bearings are two right answers")
        assertNull(index.regionOfClue["negated"], "a sentence saying where the clue is not places nothing")
        assertNull(index.regionOfClue["relative"], "north of a town is not the north of the country")
        assertNull(index.regionOfClue["placed"], "the west of a park is not the west of the country")
        assertNull(index.regionOfClue["proper"])
        // A wrong bearing points well away from the right one.
        assertEquals(
            setOf("South-east", "South", "South-west", "Centre"),
            index.distractorsOfClue.getValue("adjective").toSet(),
        )
        assertTrue(index.regionsOf("FI").isEmpty(), "bearings are not named regions")
    }

    @Test
    fun `a clue that names a region never becomes a compass question`() {
        val clues = listOf(
            regional("a", "Found in Shikoku."), regional("b", "Also in Shikoku."),
            regional("c", "Found in Chugoku."), regional("d", "Also in Chugoku."),
            regional("e", "Found in Hokkaido."), regional("f", "Also in Hokkaido."),
            regional("both", "Snow poles are common in northern Japan, most of all in Hokkaido."),
        )

        val index = index(clues)

        // "Hokkaido" and "North" on one board could both be right.
        assertEquals("Hokkaido", index.regionOfClue["both"])
        assertTrue(index.distractorsOfClue.getValue("both").none { it in ClueRegions.COMPASS_LABELS })
    }

    /** The real bundled guides: the only check that this still reads English. */
    private fun seedSnapshot(): ClueRepository.Snapshot {
        val json = Json { ignoreUnknownKeys = true }
        val dataset = javaClass.getResourceAsStream("/seed/clues.json")!!
            .use { json.decodeFromString<ClueDataset>(it.readBytes().decodeToString()) }
        return ClueRepository.Snapshot(dataset)
    }

    @Test
    fun `the bundled guides yield a playable region game`() {
        val snapshot = seedSnapshot()

        val regions = snapshot.regions
        assertTrue(
            snapshot.regionClues.size > 1_400,
            "only " + snapshot.regionClues.size + " placeable region clues in the shipped dataset",
        )
        assertTrue(regions.regionsByCountry.size >= 40, "only " + regions.regionsByCountry.size + " countries")
        assertTrue(snapshot.regionCountryCount >= 80, "only " + snapshot.regionCountryCount + " playable countries")
        assertTrue(
            regions.regionsByCountry.values.all { it.size >= ClueRegions.MIN_BOARD },
            "a country was kept that cannot fill a board",
        )
        // Every placeable clue must belong to a country that can be quizzed on,
        // or be a compass question, and have the wrong answers to fill a board.
        assertTrue(
            snapshot.regionClues.all { clue ->
                regions.isCompass(clue) || regions.answersOf(clue).all { it in regions.regionsOf(clue.countryCode) }
            },
        )
        assertTrue(snapshot.regionClues.all { regions.distractorsOf(it).size >= ClueRegions.MIN_BOARD - 1 })
        // The Australian guide, where "unique to" and "NSW" used to lose most clues.
        val stobie = snapshot.clues.first { it.id == "australia-xuhU" }
        assertEquals("South Australia", regions.regionOf(stobie))
        assertTrue("NSW" !in regions.regionsOf("AU"), "Australia: " + regions.regionsOf("AU"))
        // "the Kanto region", "West Virginia sometimes features...": the phrasings
        // that used to hide most of the Japanese and American regions.
        assertTrue(regions.regionsOf("JP").contains("Kanto"), "Japan: " + regions.regionsOf("JP"))
        assertTrue(regions.regionsOf("US").contains("West Virginia"), "US: " + regions.regionsOf("US"))
        // A board never offers a region the clue itself names.
        assertTrue(snapshot.regionClues.all { clue -> regions.distractorsOf(clue).none { it in regions.answersOf(clue) } })
        assertTrue(regions.regionsOf("JP").contains("Hokkaido"), "Japan: " + regions.regionsOf("JP"))
        assertTrue(regions.regionsOf("ES").contains("Catalonia"), "Spain: " + regions.regionsOf("ES"))
    }
}
