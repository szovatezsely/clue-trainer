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
            regional("g", "These markers are found in Shikoku and Chugoku."),
        )

        val index = index(clues)

        assertNull(index.regionOfClue["g"], "a clue with two regions cannot be a single-answer question")
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
            snapshot.regionClues.size > 400,
            "only " + snapshot.regionClues.size + " placeable region clues in the shipped dataset",
        )
        assertTrue(regions.regionsByCountry.size >= 20, "only " + regions.regionsByCountry.size + " countries")
        assertTrue(
            regions.regionsByCountry.values.all { it.size >= ClueRegions.MIN_BOARD },
            "a country was kept that cannot fill a board",
        )
        // Every placeable clue must belong to a country that can be quizzed on.
        assertTrue(
            snapshot.regionClues.all { clue ->
                regions.regionOf(clue) in regions.regionsOf(clue.countryCode)
            },
        )
        assertTrue(regions.regionsOf("JP").contains("Hokkaido"), "Japan: " + regions.regionsOf("JP"))
        assertTrue(regions.regionsOf("ES").contains("Catalonia"), "Spain: " + regions.regionsOf("ES"))
    }
}
