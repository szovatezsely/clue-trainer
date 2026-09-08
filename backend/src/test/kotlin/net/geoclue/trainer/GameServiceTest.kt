package net.geoclue.trainer

import kotlinx.serialization.json.Json
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.game.GameService
import net.geoclue.trainer.game.QuestionFilter
import net.geoclue.trainer.game.SessionStore
import net.geoclue.trainer.model.AnswerRequest
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.ClueDataset
import net.geoclue.trainer.model.Country
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameServiceTest {

    @Test
    fun `every question offers the right answer plus two distinct distractors`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()

        repeat(60) {
            val question = game.nextQuestion(session, QuestionFilter())
            assertEquals(3, question.options.size)
            assertEquals(3, question.options.map { it.code }.distinct().size)
            val answer = game.answer(session, AnswerRequest(question.clueId, question.options[0].code))
            assertTrue(question.options.any { it.code == answer.correctCountry.code })
        }
    }

    @Test
    fun `distractors come from the same continent when there are enough of them`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()

        repeat(40) {
            val question = game.nextQuestion(session, QuestionFilter(continent = "Europe", coreOnly = false))
            assertTrue(
                question.options.all { it.continent == "Europe" },
                "expected a European board, got " + question.options.map { it.name },
            )
            game.answer(session, AnswerRequest(question.clueId, question.options[0].code))
        }
    }

    @Test
    fun `a territory is never offered next to its parent country`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()

        repeat(60) {
            val question = game.nextQuestion(session, QuestionFilter())
            val prefixes = question.options.map { it.code.take(2) }
            assertEquals(prefixes.size, prefixes.distinct().size, "ambiguous board: " + question.options.map { it.code })
            game.answer(session, AnswerRequest(question.clueId, question.options[0].code))
        }
    }

    @Test
    fun `scoring tracks right, wrong and the streak`() {
        val repository = repository()
        val game = GameService(repository)
        val session = SessionStore(ttlHours = 1).create()

        // Two right, then one wrong.
        repeat(2) {
            val question = game.nextQuestion(session, QuestionFilter())
            val correct = correctCodeFor(repository, question.clueId)
            val answer = game.answer(session, AnswerRequest(question.clueId, correct))
            assertTrue(answer.correct)
        }
        val question = game.nextQuestion(session, QuestionFilter())
        val wrong = question.options.first { it.code != correctCodeFor(repository, question.clueId) }
        val answer = game.answer(session, AnswerRequest(question.clueId, wrong.code))

        assertFalse(answer.correct)
        assertEquals(2, answer.stats.correct)
        assertEquals(1, answer.stats.wrong)
        assertEquals(0, answer.stats.streak)
        assertEquals(2, answer.stats.bestStreak)
        assertEquals(67, answer.stats.accuracy)
        assertTrue(answer.explanation.paragraphs.isNotEmpty())
    }

    @Test
    fun `an unanswered question survives a reload instead of burning a clue`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()

        val first = game.nextQuestion(session, QuestionFilter())
        val second = game.nextQuestion(session, QuestionFilter())

        assertEquals(first.clueId, second.clueId)
        assertEquals(first.options.map { it.code }, second.options.map { it.code })
        assertEquals(1, second.questionNumber)
    }

    @Test
    fun `changing the filter replaces the pending question`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()

        val all = game.nextQuestion(session, QuestionFilter())
        val european = game.nextQuestion(session, QuestionFilter(continent = "Europe"))

        assertEquals(2, european.questionNumber)
        assertTrue(european.options.all { it.continent == "Europe" })
        assertTrue(all.clueId != european.clueId || all.options != european.options)
    }

    @Test
    fun `the game never runs out of clues`() {
        // Two countries, one clue each: the pool is exhausted after two questions.
        val game = GameService(repository(smallDataset()))
        val session = SessionStore(ttlHours = 1).create()

        val served = (1..6).map {
            val question = game.nextQuestion(session, QuestionFilter())
            game.answer(session, AnswerRequest(question.clueId, question.options[0].code))
            question.clueId
        }

        assertEquals(6, served.size)
        assertEquals(setOf("a-1", "b-1"), served.toSet())
    }

    @Test
    fun `a skipped clue is replaced and not scored`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()

        val broken = game.nextQuestion(session, QuestionFilter())
        val stats = game.skip(session)
        val replacement = game.nextQuestion(session, QuestionFilter())

        assertEquals(0, stats.answered, "skipping must not count as an answer")
        assertTrue(replacement.clueId != broken.clueId, "the skipped clue came straight back")

        val error = assertFailsWith<ApiException> {
            game.answer(session, AnswerRequest(broken.clueId, broken.options[0].code))
        }
        assertEquals("stale_answer", error.code)
    }

    @Test
    fun `answers for an older clue are rejected`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()
        val question = game.nextQuestion(session, QuestionFilter())

        val stale = assertFailsWith<ApiException> {
            game.answer(session, AnswerRequest("not-the-current-clue", question.options[0].code))
        }
        assertEquals("stale_answer", stale.code)

        val invalid = assertFailsWith<ApiException> {
            game.answer(session, AnswerRequest(question.clueId, "ZZ"))
        }
        assertEquals("invalid_option", invalid.code)
    }

    @Test
    fun `answering twice needs a new question first`() {
        val game = GameService(repository())
        val session = SessionStore(ttlHours = 1).create()
        val question = game.nextQuestion(session, QuestionFilter())
        game.answer(session, AnswerRequest(question.clueId, question.options[0].code))

        val error = assertFailsWith<ApiException> {
            game.answer(session, AnswerRequest(question.clueId, question.options[0].code))
        }
        assertEquals("no_pending_question", error.code)
    }

    @Test
    fun `an empty filter is reported instead of crashing`() {
        val game = GameService(repository(smallDataset()))
        val session = SessionStore(ttlHours = 1).create()

        val error = assertFailsWith<ApiException> {
            game.nextQuestion(session, QuestionFilter(continent = "Europe", coreOnly = true))
        }
        assertEquals("no_clues", error.code)
    }

    private fun correctCodeFor(repository: ClueRepository, clueId: String): String =
        repository.snapshot.clues.first { it.id == clueId }.countryCode

    /** Builds a repository over a dataset written to a throwaway directory. */
    private fun repository(dataset: ClueDataset = testDataset()): ClueRepository {
        val dir: Path = Files.createTempDirectory("clue-trainer-test")
        Files.writeString(dir.resolve("clues.json"), Json.encodeToString(dataset))
        return ClueRepository(AppConfig(dataDir = dir)).also { it.load() }
    }

    private fun testDataset(): ClueDataset {
        val countries = mutableListOf<Country>()
        val clues = mutableListOf<Clue>()
        val continents = mapOf(
            "Europe" to listOf("FR", "DE", "IT", "ES", "PL", "SE"),
            "Asia" to listOf("JP", "TH", "IN", "KR"),
            "North America" to listOf("US", "CA", "MX", "US-AK"),
        )
        continents.forEach { (continent, codes) ->
            codes.forEach { code ->
                countries += Country(
                    code = code,
                    name = "Country " + code,
                    slug = code.lowercase(),
                    continent = continent,
                    clueCount = 4,
                )
                repeat(4) { index ->
                    clues += Clue(
                        id = code + "-" + index,
                        countryCode = code,
                        imageUrl = "/images/" + code.lowercase() + "/" + index + ".png",
                        text = listOf("Because of reason " + index + "."),
                        section = if (index < 2) "Identifying Country $code" else "Spotlight",
                    )
                }
            }
        }
        return ClueDataset("test", "2026-01-01T00:00:00Z", countries, clues)
    }

    private fun smallDataset() = ClueDataset(
        source = "test",
        scrapedAt = "2026-01-01T00:00:00Z",
        countries = listOf(
            Country("AA", "Aaa", "a", "Oceania", clueCount = 1),
            Country("BB", "Bbb", "b", "Oceania", clueCount = 1),
        ),
        clues = listOf(
            Clue("a-1", "AA", "/images/a/1.png", text = listOf("A"), section = "Spotlight"),
            Clue("b-1", "BB", "/images/b/1.png", text = listOf("B"), section = "Spotlight"),
        ),
    )
}
