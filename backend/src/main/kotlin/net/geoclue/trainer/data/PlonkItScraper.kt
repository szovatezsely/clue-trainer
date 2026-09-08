package net.geoclue.trainer.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.ClueDataset
import net.geoclue.trainer.model.Country
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Extracts identification clues from the PlonkIt GeoGuessr guides.
 *
 * Every guide page ships its content as a JSON island (`#__PRELOADED_DATA__`)
 * that the site's own SPA hydrates from, so we read that instead of scraping
 * rendered markup: it is stable, complete and cheap to parse.
 *
 * Layout of a country page:
 *
 *     data.public.steps[]                    // chapters
 *       .kind  = "tip" | "map"
 *       .title = "Identifying Japan" | "Spotlight" | ...
 *       .items[]
 *         .kind = "tip"                      // <- a clue
 *              .data.image.{imageUrl,imageLink,width}
 *              .data.text[]                  // markdown paragraphs
 *              .tags[]
 *         .kind = "subsection" | "divider"   // heading for the clues below it
 *         .kind = "centeredImage" | "centeredText"
 */
class PlonkItScraper(
    private val baseUrl: String = BASE_URL,
    private val delayMillis: Long = 1_200,
) {
    private val log = LoggerFactory.getLogger(PlonkItScraper::class.java)
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(CIO) {
        expectSuccess = false
        engine {
            requestTimeout = 45_000
        }
    }

    suspend fun scrape(onProgress: (done: Int, total: Int, country: String) -> Unit = { _, _, _ -> }): ClueDataset {
        val countries = fetchCountryIndex()
        log.info("PlonkIt index: {} covered countries", countries.size)

        val allClues = mutableListOf<Clue>()
        val scraped = mutableListOf<Country>()

        countries.forEachIndexed { index, entry ->
            val clues = try {
                extractClues(entry, fetchPage("/" + entry.slug))
            } catch (t: Throwable) {
                log.warn("Skipping {}: {}", entry.slug, t.message)
                emptyList()
            }
            allClues += clues
            scraped += entry.copy(clueCount = clues.size)
            onProgress(index + 1, countries.size, entry.name)
            if (index < countries.lastIndex) delay(delayMillis)
        }

        return ClueDataset(
            source = baseUrl + "/guide",
            scrapedAt = Instant.now().toString(),
            countries = scraped.sortedBy { it.name },
            clues = allClues,
        )
    }

    /** Reads the country list (title, slug, ISO code, continent) off the guide map page. */
    private suspend fun fetchCountryIndex(): List<Country> {
        val entries = preloadedData(fetchPage("/guide")).jsonArray
        return entries.mapNotNull { element ->
            val obj = element.jsonObject
            val code = obj.str("code") ?: return@mapNotNull null
            val slug = obj.str("slug") ?: return@mapNotNull null
            val name = obj.str("title") ?: return@mapNotNull null
            val categories = (obj["cat"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()
            // "General Guide" pages (beginner's guide, map directory, ...) are not countries.
            if (GENERAL_CATEGORY in categories) return@mapNotNull null
            Country(
                code = code,
                name = name,
                slug = slug,
                continent = categories.firstOrNull() ?: "Other",
            )
        }
    }

    internal fun extractClues(country: Country, html: String): List<Clue> {
        val public = preloadedData(html).jsonObject["public"]?.jsonObject
            ?: error("no public payload for " + country.slug)
        val steps = (public["steps"] as? JsonArray) ?: return emptyList()
        val clues = mutableListOf<Clue>()

        for (step in steps) {
            val stepObj = step.jsonObject
            // "map" chapters only link to community maps, they never hold clues.
            if (stepObj.str("kind") != "tip") continue
            val section = stepObj.str("title").orEmpty()
            var subsection = ""

            for (item in (stepObj["items"] as? JsonArray) ?: JsonArray(emptyList())) {
                val itemObj = item.jsonObject
                when (itemObj.str("kind")) {
                    "subsection", "divider" -> {
                        subsection = itemObj.str("title").orEmpty()
                        continue
                    }
                    "tip" -> Unit
                    else -> continue
                }

                val data = itemObj["data"]?.jsonObject ?: continue
                val image = data["image"]?.jsonObject ?: continue
                val imageUrl = image.str("imageUrl")?.takeIf { it.isNotBlank() } ?: continue
                // Maps, summary sheets, locator maps and infographics are
                // reference material, never something a player could spot in
                // Street View - and a locator map gives the answer away.
                val fileName = imageUrl.substringAfterLast('/')
                if (REFERENCE_IMAGE.any { it.containsMatchIn(fileName) }) continue

                val paragraphs = (data["text"] as? JsonArray)
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.map { it.replace('<', '(').replace('>', ')').trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()
                if (paragraphs.isEmpty()) continue

                val link = image.str("imageLink").orEmpty()
                clues += Clue(
                    id = country.slug + "-" + (itemObj.str("id") ?: clues.size.toString()),
                    countryCode = country.code,
                    imageUrl = imageUrl,
                    width = image["width"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0.0 } ?: 0.5,
                    text = paragraphs,
                    tags = (itemObj["tags"] as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    section = section,
                    subsection = subsection,
                    streetView = link.takeIf { url -> STREET_VIEW_HOSTS.any { url.contains(it) } },
                )
            }
        }
        log.debug("{}: {} clues", country.slug, clues.size)
        return clues
    }

    private fun preloadedData(html: String) =
        Jsoup.parse(html).getElementById(PRELOAD_ID)?.data()
            ?.let { json.parseToJsonElement(it).jsonObject["data"] }
            ?: error("page does not contain #" + PRELOAD_ID)

    /** GETs a guide page, backing off politely when the site rate-limits us. */
    private suspend fun fetchPage(path: String): String {
        var backoff = 2_000L
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = client.get(baseUrl + path) {
                header("User-Agent", USER_AGENT)
                header("Referer", baseUrl + "/guide")
                header("Accept", "text/html,application/xhtml+xml")
            }
            when {
                response.status.isSuccess() -> return response.bodyAsText()
                response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500 -> {
                    if (attempt == MAX_ATTEMPTS - 1) error(path + " failed: " + response.status)
                    log.info("{} -> {}, retrying in {}ms", path, response.status, backoff)
                    delay(backoff)
                    backoff *= 2
                }
                else -> error(path + " failed: " + response.status)
            }
        }
        error(path + " failed after " + MAX_ATTEMPTS + " attempts")
    }

    fun close() = client.close()

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    companion object {
        const val BASE_URL = "https://www.plonkit.net"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private const val PRELOAD_ID = "__PRELOADED_DATA__"
        private const val GENERAL_CATEGORY = "General Guide"
        private const val MAX_ATTEMPTS = 5
        private val REFERENCE_IMAGE = listOf(
            // "US_population_map.png", "0_us_summary_2025.png", ...
            Regex("(map|summary|chart|overview|regions|infographic|locator)", RegexOption.IGNORE_CASE),
            // Wikipedia-style locator maps, which would give the answer away:
            // "Pitcairn_Islands_in_United_Kingdom.svg.png", "Jersey_in_its_region2.png".
            Regex("""\.svg"""),
            Regex("_in_(its_region|[A-Z])"),
        )
        private val STREET_VIEW_HOSTS = listOf("goo.gl/maps", "google.com/maps", "maps.app.goo.gl")
    }
}
