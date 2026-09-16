package net.geoclue.trainer

import kotlinx.serialization.json.Json
import net.geoclue.trainer.data.ClueAmbiguity
import net.geoclue.trainer.model.ClueDataset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the ambiguity reader over the real bundled guides, which is the only
 * way to know it still reads English rather than just the examples it was
 * written against.
 */
class SeedAmbiguityTest {

    private val dataset: ClueDataset = Json { ignoreUnknownKeys = true }.let { json ->
        javaClass.getResourceAsStream("/seed/clues.json")!!
            .use { json.decodeFromString<ClueDataset>(it.readBytes().decodeToString()) }
    }
    private val index = ClueAmbiguity(dataset.countries).index(dataset.clues)

    @Test
    fun `the guides' own shared-trait notes are picked up`() {
        // "NOTE: Peru, Brazil and Argentina are the only South American
        // countries with smallcam" - offering Peru here would mark a player
        // wrong and then show them a note saying they were right.
        assertEquals(setOf("PE", "BR"), index["argentina-jAh5"])
        assertEquals(setOf("AR", "PE"), index["brazil-bnN1"])
        // "NOTE: Liechtenstein uses identical signs."
        assertEquals(setOf("LI"), index["switzerland-e1U8"])
        // "NOTE: Similar road lines can also be found in South Africa, Lesotho
        // and Botswana."
        assertEquals(setOf("ZA", "LS", "BW"), index["eswatini-XXIL"])
    }

    @Test
    fun `notes that tell two countries apart still make distractors`() {
        // "NOTE: Canada uses the word 'Maximum' on their speed signs" - the
        // whole point of the US speed sign clue.
        assertTrue("united-states-a9Az" !in index)
        // "NOTE: Australia does not use this design."
        assertTrue("new-zealand-JeNW" !in index)
        // "The Northern Mariana Islands uses blue street signs, unlike Guam."
        assertTrue("northern-mariana-islands-jle0" !in index)
    }

    @Test
    fun `a clue is never called ambiguous with itself`() {
        for (clue in dataset.clues) {
            val flagged = index[clue.id] ?: continue
            assertTrue(
                flagged.none { it.take(2) == clue.countryCode.take(2) },
                clue.id + " flags its own country: " + flagged,
            )
            assertTrue(
                flagged.all { code -> dataset.countries.any { it.code == code } },
                clue.id + " flags a country that is not in the dataset: " + flagged,
            )
        }
    }

    @Test
    fun `only the minority of clues that name another country are affected`() {
        // A reader that flagged everything would quietly gut the boards, and
        // one that flagged nothing would be the bug this exists to fix.
        val share = index.size * 100 / dataset.clues.size
        assertTrue(share in 5..25, "flagged $share% of clues (" + index.size + " of " + dataset.clues.size + ")")
    }
}
