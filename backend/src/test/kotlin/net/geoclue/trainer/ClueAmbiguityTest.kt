package net.geoclue.trainer

import net.geoclue.trainer.data.ClueAmbiguity
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.Country
import kotlin.test.Test
import kotlin.test.assertEquals

class ClueAmbiguityTest {

    @Test
    fun `a country listed next to the answer also fits the clue`() {
        // The clue that prompted all of this: Peru is as right as Argentina.
        assertEquals(
            setOf("PE", "BR"),
            ambiguous(
                "AR",
                "NOTE: Peru, Brazil and Argentina are the only South American countries with smallcam.",
            ),
        )
    }

    @Test
    fun `a country the explanation only contrasts stays a fair distractor`() {
        assertEquals(emptySet(), ambiguous("US", "NOTE: Canada uses the word 'Maximum' on their speed signs."))
        assertEquals(emptySet(), ambiguous("NZ", "NOTE: Australia does not use this design."))
        assertEquals(
            emptySet(),
            ambiguous("GU", "Guam uses green street signs, unlike the Northern Mariana Islands, which use blue."),
        )
        assertEquals(emptySet(), ambiguous("JE", "NOTE: In contrast, the UK typically uses double dotted white lines."))
    }

    @Test
    fun `claims of a shared trait are read in all their usual shapes`() {
        assertEquals(setOf("LI"), ambiguous("CH", "NOTE: Liechtenstein uses identical signs."))
        assertEquals(setOf("ZA"), ambiguous("SZ", "NOTE: Similar plantations can also be found in South Africa."))
        assertEquals(setOf("MX", "PA"), ambiguous("CR", "Like in Mexico and Panama, stop signs read 'ALTO'."))
        assertEquals(setOf("SE", "NO"), ambiguous("FI", "NOTE: Norway and Sweden have similarly coloured chevrons."))
        assertEquals(setOf("IE", "NL"), ambiguous("GB", "The only European countries where these are common are Ireland and the Netherlands."))
        assertEquals(setOf("GH", "NG"), ambiguous("SN", "The other countries in Africa that drive on the right are Ghana and Nigeria."))
        assertEquals(setOf("PE", "BO"), ambiguous("EC", "Countries such as Peru, and especially Bolivia, commonly use red bricks."))
    }

    @Test
    fun `a negated marker is not a claim`() {
        assertEquals(emptySet(), ambiguous("MP", "NOTE: This tree is not common in Guam."))
        assertEquals(emptySet(), ambiguous("AU", "Give-way signs are very rarely seen in South Africa."))
    }

    @Test
    fun `the answer's own territories are never flagged`() {
        // US-AK shares the US prefix, which the board already keeps apart.
        assertEquals(emptySet(), ambiguous("US", "The same poles are also found all over Alaska."))
    }

    @Test
    fun `place names that merely contain a country are not mentions`() {
        assertEquals(emptySet(), ambiguous("US", "Similar pavement can also be seen in New Mexico and New Jersey."))
    }

    @Test
    fun `lowercase 'us' is a pronoun, not a country`() {
        assertEquals(emptySet(), ambiguous("CA", "These signs also help us tell the two apart."))
        assertEquals(setOf("US"), ambiguous("CA", "These poles are also common in the US."))
    }

    @Test
    fun `accents and alternative names still match`() {
        assertEquals(setOf("RE"), ambiguous("MG", "The same trekker also covers Réunion."))
        assertEquals(setOf("GB"), ambiguous("IE", "Postboxes look the same in Britain."))
        assertEquals(setOf("NL"), ambiguous("BE", "Identical bollards are used in Holland."))
    }

    @Test
    fun `country names inside link targets are ignored`() {
        assertEquals(
            emptySet(),
            ambiguous("TZ", "NOTE: Do not use this [boat](https://www.plonkit.net/madagascar#3) as a landmark."),
        )
    }

    private fun ambiguous(answer: String, vararg paragraphs: String): Set<String> =
        ClueAmbiguity(countries).ambiguousFor(
            Clue(id = "t", countryCode = answer, imageUrl = "/i.png", text = paragraphs.toList()),
        )

    private val countries = listOf(
        "AR" to "Argentina", "AU" to "Australia", "BE" to "Belgium", "BO" to "Bolivia", "BR" to "Brazil",
        "CA" to "Canada", "CH" to "Switzerland", "CR" to "Costa Rica", "EC" to "Ecuador", "FI" to "Finland",
        "GB" to "United Kingdom", "GH" to "Ghana", "GU" to "Guam", "IE" to "Ireland", "JE" to "Jersey",
        "LI" to "Liechtenstein", "MG" to "Madagascar", "MP" to "Northern Mariana Islands", "MX" to "Mexico",
        "NG" to "Nigeria", "NL" to "Netherlands", "NO" to "Norway", "NZ" to "New Zealand", "PA" to "Panama",
        "PE" to "Peru", "RE" to "Reunion", "SE" to "Sweden", "SN" to "Senegal", "SZ" to "Eswatini",
        "TZ" to "Tanzania", "US" to "United States of America", "US-AK" to "Alaska", "ZA" to "South Africa",
    ).map { (code, name) -> Country(code = code, name = name, slug = name.lowercase(), continent = "Test") }
}
