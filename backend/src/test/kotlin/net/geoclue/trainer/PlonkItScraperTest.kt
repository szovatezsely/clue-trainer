package net.geoclue.trainer

import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.PlonkItScraper
import net.geoclue.trainer.model.Country
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlonkItScraperTest {

    private val country = Country(code = "XX", name = "Testland", slug = "testland", continent = "Europe")

    @Test
    fun `clues are read out of the preloaded json island`() {
        val clues = PlonkItScraper().extractClues(country, GUIDE_PAGE)

        assertEquals(listOf("testland-aaa", "testland-bbb", "testland-ddd"), clues.map { it.id })

        val first = clues[0]
        assertEquals("/images/testland/speedsign.png", first.imageUrl)
        assertEquals(listOf("The sign says **Speed Limit**.", "Second paragraph."), first.text)
        assertEquals(listOf("chevron/sign"), first.tags)
        assertEquals("Identifying Testland", first.section)
        assertEquals("Step 1.1", first.subsection)
        assertEquals("https://goo.gl/maps/abc", first.streetView)
        assertEquals(0.5, first.width)
        assertTrue(first.isCore)
    }

    @Test
    fun `reference maps, text-only tips and map chapters are left out`() {
        val clues = PlonkItScraper().extractClues(country, GUIDE_PAGE)

        assertTrue(clues.none { it.imageUrl.contains("population_map") }, "a country map is not a clue")
        assertTrue(clues.none { it.id == "testland-ccc" }, "a tip without text is not a clue")
        assertTrue(clues.none { it.section == "Maps and resources" }, "the map chapter holds no clues")
        // Locator maps would hand the player the answer.
        assertTrue(clues.none { it.id == "testland-fff" }, "'X in Europe.svg' is a locator map")
        assertTrue(clues.none { it.id == "testland-ggg" }, "'X in its region' is a locator map")
    }

    @Test
    fun `non street view links and later chapters are handled`() {
        val spotlight = PlonkItScraper().extractClues(country, GUIDE_PAGE).last()

        assertEquals("Spotlight", spotlight.section)
        assertEquals("Landscape", spotlight.subsection)
        assertNull(spotlight.streetView, "an in-page link is not a Street View link")
        assertTrue(!spotlight.isCore)
    }

    @Test
    fun `angle brackets in guide text are neutralised`() {
        val clues = PlonkItScraper().extractClues(country, GUIDE_PAGE)

        assertEquals("Watch out for (script) tags.", clues[1].text.single())
    }

    @Test
    fun `the bundled dataset is complete and internally consistent`() {
        // Empty data dir: the repository falls back to resources/seed/clues.json.
        val repository = ClueRepository(AppConfig(dataDir = Files.createTempDirectory("clue-trainer-seed")))
        repository.load()
        val snapshot = repository.snapshot

        assertTrue(snapshot.clues.size > 3_000, "seed only has " + snapshot.clues.size + " clues")
        assertTrue(snapshot.coreClues.size > 1_000)
        assertTrue(snapshot.playableCountries.size > 100)
        assertTrue(snapshot.clues.all { it.countryCode in snapshot.countriesByCode })
        assertTrue(snapshot.clues.all { it.imageUrl.startsWith("/images/") })
        assertTrue(snapshot.clues.all { it.text.isNotEmpty() })
        assertEquals(snapshot.clues.size, snapshot.clues.map { it.id }.distinct().size)
        // Every continent must be able to fill a three-country board on its own,
        // or the game has to fall back to other continents for distractors.
        assertTrue(snapshot.continents.count { snapshot.countriesByContinent.getValue(it).size >= 3 } >= 6)
    }

    private companion object {
        val GUIDE_PAGE = """
            <!doctype html><html><head>
            <script id="__PRELOADED_DATA__" type="application/json">
            {"success":true,"data":{"public":{
              "slug":"testland","title":"Testland","code":"XX","cat":["Europe"],
              "heroImage":"/images/testland/hero.png",
              "steps":[
                {"kind":"tip","title":"Identifying Testland","items":[
                  {"kind":"centeredImage","id":"img","imageUrl":"/images/testland/0_summary.png"},
                  {"kind":"subsection","id":"sub","title":"Step 1.1"},
                  {"kind":"tip","id":"aaa","data":{
                    "image":{"imageUrl":"/images/testland/speedsign.png","imageLink":"https://goo.gl/maps/abc","alt":"","width":0.5},
                    "text":["The sign says **Speed Limit**.","Second paragraph."]},"tags":["chevron/sign"]},
                  {"kind":"tip","id":"bbb","data":{
                    "image":{"imageUrl":"/images/testland/tags.png","imageLink":"","alt":"","width":0},
                    "text":["Watch out for <script> tags."]}},
                  {"kind":"tip","id":"ccc","data":{
                    "image":{"imageUrl":"/images/testland/nocaption.png","imageLink":"","alt":""},
                    "text":[]}},
                  {"kind":"tip","id":"eee","data":{
                    "image":{"imageUrl":"/images/testland/population_map.png","imageLink":"#","alt":""},
                    "text":["Population density."]}},
                  {"kind":"tip","id":"fff","data":{
                    "image":{"imageUrl":"/images/testland/Testland_in_Europe.svg.png","imageLink":"#","alt":""},
                    "text":["Where Testland is."]}},
                  {"kind":"tip","id":"ggg","data":{
                    "image":{"imageUrl":"/images/testland/testland_in_its_region2.png","imageLink":"#","alt":""},
                    "text":["Where Testland is, again."]}}
                ]},
                {"kind":"tip","title":"Spotlight","items":[
                  {"kind":"divider","id":"div","title":"Landscape"},
                  {"kind":"tip","id":"ddd","data":{
                    "image":{"imageUrl":"/images/testland/hills.png","imageLink":"#","alt":"","width":0.6},
                    "text":["Rolling hills in the north."]},"tags":["landscape"]}
                ]},
                {"kind":"map","title":"Maps and resources","text":["Community maps"]}
              ]}}}
            </script></head><body></body></html>
        """.trimIndent()
    }
}
