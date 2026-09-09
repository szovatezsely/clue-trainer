package net.geoclue.trainer

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.ImageWarmer
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.ClueDataset
import net.geoclue.trainer.model.Country
import net.geoclue.trainer.routes.ImageCache
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageWarmerTest {

    @Test
    fun `warming fetches every core image once, and nothing on a second run`() = runBlocking {
        val requests = AtomicInteger()
        val dir = Files.createTempDirectory("clue-trainer-warm")
        val config = config(dir)
        val repository = repository(config)
        val cache = ImageCache(config, client(requests) { respondImage() })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        ImageWarmer(config, repository, cache).start(scope).join()

        // Two core clues, one spotlight clue: only the core ones are warmed.
        assertEquals(2, requests.get(), "warming must cover the core clues exactly once")

        ImageWarmer(config, repository, cache).start(scope).join()
        assertEquals(2, requests.get(), "a warm cache must not re-fetch anything")
    }

    @Test
    fun `warming covers every clue when asked to`() = runBlocking {
        val requests = AtomicInteger()
        val config = config(Files.createTempDirectory("clue-trainer-warm-all"), warmAll = true)
        val cache = ImageCache(config, client(requests) { respondImage() })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        ImageWarmer(config, repository(config), cache).start(scope).join()

        assertEquals(3, requests.get())
    }

    @Test
    fun `country-level images are fetched before the rest`() = runBlocking {
        val requested = mutableListOf<String>()
        val config = config(Files.createTempDirectory("clue-trainer-warm-order"), warmAll = true)
        val engine = MockEngine { request ->
            requested += request.url.encodedPath
            respondImage()
        }
        val cache = ImageCache(config, HttpClient(engine) { expectSuccess = false })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        ImageWarmer(config, repository(config), cache).start(scope).join()

        // a-1 and a-2 are "Identifying Aaa"; a-3 is a Spotlight clue.
        assertEquals(listOf("/images/a/1.png", "/images/a/2.png", "/images/a/3.png"), requested)
    }

    @Test
    fun `a missing image does not stop the run`() = runBlocking {
        val requests = AtomicInteger()
        val config = config(Files.createTempDirectory("clue-trainer-warm-404"))
        val cache = ImageCache(config, client(requests) { respondError(HttpStatusCode.NotFound) })
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        ImageWarmer(config, repository(config), cache).start(scope).join()

        // It tried both core images and gave up on each rather than looping.
        assertEquals(2, requests.get())
        assertTrue(cache.throttledForSeconds() == 0)
    }

    private fun config(dir: Path, warmAll: Boolean = false) = AppConfig(
        dataDir = dir,
        imageMinIntervalMillis = 0,
        warmCache = true,
        warmCacheAll = warmAll,
        warmStartDelayMillis = 0,
    )

    private fun MockRequestHandleScope.respondImage(): HttpResponseData = respond(
        content = ByteReadChannel(byteArrayOf(1, 2, 3)),
        headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
    )

    private fun client(requests: AtomicInteger, handler: MockRequestHandleScope.() -> HttpResponseData) =
        HttpClient(MockEngine { requests.incrementAndGet(); handler() }) { expectSuccess = false }

    private fun repository(config: AppConfig): ClueRepository {
        val dataset = ClueDataset(
            source = "test",
            scrapedAt = "2026-01-01T00:00:00Z",
            countries = listOf(Country("AA", "Aaa", "a", "Europe", clueCount = 3)),
            clues = listOf(
                Clue("a-1", "AA", "/images/a/1.png", text = listOf("x"), section = "Identifying Aaa"),
                Clue("a-2", "AA", "/images/a/2.png", text = listOf("x"), section = "Identifying Aaa"),
                Clue("a-3", "AA", "/images/a/3.png", text = listOf("x"), section = "Spotlight"),
            ),
        )
        Files.writeString(config.clueCacheFile, Json.encodeToString(dataset))
        return ClueRepository(config).also { it.load() }
    }
}
