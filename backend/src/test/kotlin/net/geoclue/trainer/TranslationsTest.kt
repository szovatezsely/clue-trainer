package net.geoclue.trainer

import kotlinx.serialization.json.Json
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.ContentBundle
import net.geoclue.trainer.data.Translations
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.game.GameMode
import net.geoclue.trainer.game.GameService
import net.geoclue.trainer.game.QuestionFilter
import net.geoclue.trainer.game.SessionStore
import net.geoclue.trainer.model.AnswerRequest
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.ClueDataset
import net.geoclue.trainer.model.Country
import net.geoclue.trainer.model.Lang
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The dataset is scraped in English and served in whatever language was asked
 * for, falling back string by string. These pin down what that fallback is
 * allowed to mean - and that choosing a language never costs the player a clue.
 */
class TranslationsTest {

    @Test
    fun `a translated clue comes back entirely in the chosen language`() {
        val game = GameService(repository(), translations = translations)
        val session = SessionStore(ttlHours = 1).create()

        val question = game.nextQuestion(session, onlyFrance, Lang.HU)
        val answer = game.answer(session, AnswerRequest(question.clueId, "FR"), Lang.HU)

        assertEquals("Franciaország", answer.correctAnswer.label)
        assertEquals("Franciaország", answer.country.name)
        assertEquals("Franciaország azonosítása", answer.explanation.section)
        assertEquals("Infrastruktúra", answer.explanation.subsection)
        assertEquals(listOf("oszlop"), answer.explanation.tags)
        assertEquals(listOf("Magyarul erről szól."), answer.explanation.paragraphs)
        assertTrue(answer.explanation.translated)
    }

    @Test
    fun `an untranslated clue keeps its English prose but not its English labels`() {
        val game = GameService(repository(), translations = translations)
        val session = SessionStore(ttlHours = 1).create()

        // Germany's clue is deliberately absent from the clue text bundle below.
        val question = game.nextQuestion(session, onlyGermany, Lang.HU)
        val answer = game.answer(session, AnswerRequest(question.clueId, "DE"), Lang.HU)

        assertEquals(listOf("Because of reason DE."), answer.explanation.paragraphs)
        assertFalse(answer.explanation.translated, "the reveal has to be able to say so")
        assertEquals("Németország", answer.country.name, "a country name is not clue prose")
        assertEquals("Németország azonosítása", answer.explanation.section)
    }

    @Test
    fun `an unknown language is served in English rather than refused`() {
        val game = GameService(repository(), translations = translations)
        val session = SessionStore(ttlHours = 1).create()
        val german = Lang.parse("de-DE")

        val question = game.nextQuestion(session, onlyFrance, german)
        val answer = game.answer(session, AnswerRequest(question.clueId, "FR"), german)

        assertEquals("Country FR", answer.correctAnswer.label)
        assertEquals(listOf("Because of reason FR."), answer.explanation.paragraphs)
        assertTrue(answer.explanation.translated, "English is the language the guides are written in")
    }

    @Test
    fun `changing language re-serves the same question instead of burning a clue`() {
        val game = GameService(repository(), translations = translations)
        val session = SessionStore(ttlHours = 1).create()

        val english = game.nextQuestion(session, QuestionFilter(), Lang.EN)
        val hungarian = game.nextQuestion(session, QuestionFilter(), Lang.HU)

        assertEquals(english.clueId, hungarian.clueId)
        assertEquals(english.questionNumber, hungarian.questionNumber)
        assertEquals(
            english.options.map { it.value },
            hungarian.options.map { it.value },
            "the board has to keep its order, so the number keys still mean the same thing",
        )
    }

    @Test
    fun `the last verdict can be re-worded without changing the score`() {
        val game = GameService(repository(), translations = translations)
        val session = SessionStore(ttlHours = 1).create()

        val question = game.nextQuestion(session, onlyFrance, Lang.EN)
        val english = game.answer(session, AnswerRequest(question.clueId, "FR"), Lang.EN)
        val hungarian = game.lastAnswer(session, Lang.HU)

        assertEquals(english.stats, hungarian.stats)
        assertEquals(english.correct, hungarian.correct)
        assertEquals(english.correctAnswer.value, hungarian.correctAnswer.value)
        assertEquals("Country FR", english.correctAnswer.label)
        assertEquals("Franciaország", hungarian.correctAnswer.label)
        assertEquals(
            english.options.map { it.value },
            hungarian.options.map { it.value },
            "the board behind the reveal is relabelled, not reshuffled",
        )
    }

    @Test
    fun `a new clue puts the previous verdict out of reach`() {
        val game = GameService(repository(), translations = translations)
        val session = SessionStore(ttlHours = 1).create()

        val question = game.nextQuestion(session, QuestionFilter(), Lang.EN)
        game.answer(session, AnswerRequest(question.clueId, question.options[0].value), Lang.EN)
        game.nextQuestion(session, QuestionFilter(), Lang.EN)

        val error = assertFailsWith<ApiException> { game.lastAnswer(session, Lang.HU) }
        assertEquals("no_answer_yet", error.code)
    }

    @Test
    fun `region answers keep the guide's own spelling of the place`() {
        val repository = seedRepository()
        val game = GameService(repository, translations = translations)
        val session = SessionStore(ttlHours = 1).create()

        val question = game.nextQuestion(session, QuestionFilter(mode = GameMode.REGION), Lang.HU)
        val clue = repository.snapshot.clues.first { it.id == question.clueId }
        val regions = repository.snapshot.regions.regionsOf(clue.countryCode)

        assertNotNull(question.country, "the region game names the country it means")
        assertTrue(
            question.options.all { it.label == it.value && it.value in regions },
            "a region label is the name written on the sign, not something to translate: " +
                question.options.map { it.label },
        )
    }

    // Each country under test owns its continent, so filtering by continent
    // pins the question down to exactly one clue.
    private val onlyFrance = QuestionFilter(continent = "Europe")
    private val onlyGermany = QuestionFilter(continent = "Africa")

    private val translations = Translations.of(
        Lang.HU,
        ContentBundle(
            countries = mapOf("FR" to "Franciaország", "DE" to "Németország"),
            continents = mapOf("Europe" to "Európa"),
            tags = mapOf("pole" to "oszlop"),
            sections = mapOf(
                "Identifying Country FR" to "Franciaország azonosítása",
                "Identifying Country DE" to "Németország azonosítása",
            ),
            subsections = mapOf("Infrastructure" to "Infrastruktúra"),
        ),
        clueText = mapOf("FR-clue" to listOf("Magyarul erről szól.")),
    )

    private fun repository(): ClueRepository {
        val dir: Path = Files.createTempDirectory("clue-trainer-i18n-test")
        Files.writeString(dir.resolve("clues.json"), Json.encodeToString(dataset()))
        return ClueRepository(AppConfig(dataDir = dir)).also { it.load() }
    }

    /** One clue per country; the three Asian ones are there to fill a board. */
    private fun dataset(): ClueDataset {
        val spec = listOf(
            "FR" to "Europe",
            "DE" to "Africa",
            "JP" to "Asia",
            "TH" to "Asia",
            "IN" to "Asia",
        )
        val countries = spec.map { (code, continent) ->
            Country(code, "Country $code", code.lowercase(), continent, clueCount = 1)
        }
        val clues = spec.map { (code, _) ->
            Clue(
                id = "$code-clue",
                countryCode = code,
                imageUrl = "/images/" + code.lowercase() + "/1.png",
                text = listOf("Because of reason $code."),
                tags = listOf("pole"),
                section = "Identifying Country $code",
                subsection = "Infrastructure",
            )
        }
        return ClueDataset("test", "2026-01-01T00:00:00Z", countries, clues)
    }

    /** The bundled dataset - the only one whose guides actually name regions. */
    private fun seedRepository(): ClueRepository =
        ClueRepository(AppConfig(dataDir = Files.createTempDirectory("clue-trainer-i18n-seed")))
            .also { it.load() }
}
