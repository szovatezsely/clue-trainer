package net.geoclue.trainer

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.routes.ImageCache
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

private fun MockRequestHandleScope.respondImage(): HttpResponseData = respond(
    content = ByteReadChannel(PNG),
    headers = headersOf(HttpHeaders.ContentType, ContentType.Image.PNG.toString()),
)

class ImageCacheTest {

    @Test
    fun `an image is fetched once and then served from disk`() = runBlocking {
        val requests = AtomicInteger()
        val cache = cache(requests) { respondImage() }

        val first = cache.get("/images/xx/one.png")
        val second = cache.get("/images/xx/one.png")

        assertEquals(1, requests.get(), "the second read must come from disk")
        assertTrue(first.bytes.contentEquals(PNG))
        assertTrue(second.bytes.contentEquals(PNG))
        assertEquals(ContentType.Image.PNG, second.contentType)
    }

    @Test
    fun `fetches are paced, so a burst cannot happen`() = runBlocking {
        val requests = AtomicInteger()
        val cache = cache(requests, minIntervalMillis = 400) { respondImage() }

        val startedAt = System.currentTimeMillis()
        cache.get("/images/xx/one.png")
        cache.get("/images/xx/two.png")
        cache.get("/images/xx/three.png")
        val elapsed = System.currentTimeMillis() - startedAt

        assertEquals(3, requests.get())
        // Three fetches means at least two gaps of 400ms.
        assertTrue(elapsed >= 800, "three fetches took only ${elapsed}ms, pacing is not applied")
    }

    @Test
    fun `a 429 pauses every fetch instead of hammering the origin`() = runBlocking {
        val requests = AtomicInteger()
        val cache = cache(requests) {
            respondError(
                HttpStatusCode.TooManyRequests,
                headers = headersOf(HttpHeaders.RetryAfter, "60"),
            )
        }

        val first = assertFailsWith<ApiException> { cache.get("/images/xx/one.png") }
        assertEquals("image_rate_limited", first.code)

        // A different image must fail fast, without touching the origin again.
        val second = assertFailsWith<ApiException> { cache.get("/images/xx/two.png") }
        assertEquals("image_rate_limited", second.code)
        assertEquals(1, requests.get(), "a paused cache must not send more requests")
        assertTrue(cache.throttledForSeconds() > 30, "the origin asked for 60s")
    }

    @Test
    fun `cacheIfMissing only downloads what is absent`() = runBlocking {
        val requests = AtomicInteger()
        val cache = cache(requests) { respondImage() }

        assertTrue(cache.cacheIfMissing("/images/xx/one.png"), "first call must fetch")
        assertFalse(cache.cacheIfMissing("/images/xx/one.png"), "second call must skip")
        assertEquals(1, requests.get())
    }

    @Test
    fun `an unavailable image is reported without pausing the cache`() = runBlocking {
        val requests = AtomicInteger()
        val cache = cache(requests) { respondError(HttpStatusCode.NotFound) }

        val error = assertFailsWith<ApiException> { cache.get("/images/xx/gone.png") }

        assertEquals("image_unavailable", error.code)
        assertEquals(0, cache.throttledForSeconds(), "a 404 is not a rate limit")
        assertEquals(1, requests.get())
    }

    /** An ImageCache over a throwaway directory and a scripted HTTP engine. */
    private fun cache(
        requests: AtomicInteger,
        minIntervalMillis: Long = 0,
        handler: MockRequestHandleScope.() -> HttpResponseData,
    ): ImageCache {
        val engine = MockEngine {
            requests.incrementAndGet()
            handler()
        }
        val config = AppConfig(
            dataDir = Files.createTempDirectory("clue-trainer-images"),
            imageMinIntervalMillis = minIntervalMillis,
        )
        return ImageCache(config, HttpClient(engine) { expectSuccess = false })
    }
}
