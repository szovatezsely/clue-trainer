package net.geoclue.trainer.routes

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.geoclue.trainer.AppConfig
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.PlonkItScraper
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.game.ImageAvailability
import net.geoclue.trainer.model.ImageStatusDto
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Serves the clue images.
 *
 * plonkit.net answers image requests with 403 unless they carry a matching
 * `Referer`, which a browser cannot fake, so the images have to come through
 * the backend. Every fetched file is cached on disk, meaning a clue is pulled
 * from the origin at most once per container volume.
 *
 * The origin also rate-limits bursts with 429, so fetches are paced: one at a
 * time, never closer than [AppConfig.imageMinIntervalMillis]. If a 429 arrives
 * anyway, every fetch pauses for as long as the origin asks and the affected
 * clues fail fast until the window drains; cached clues keep working.
 *
 * Only paths that appear in the scraped dataset are proxied - the endpoint
 * cannot be used as an open relay.
 */
class ImageCache(
    private val config: AppConfig,
    private val client: HttpClient,
) : ImageAvailability {
    private val log = LoggerFactory.getLogger(ImageCache::class.java)

    /**
     * One fetch from the origin at a time, never closer together than
     * [AppConfig.imageMinIntervalMillis]. The rate limiter punishes bursts, so
     * the queue is the point: it is what keeps normal play under the limit.
     */
    private val pacer = Mutex()
    private val lastFetchAt = AtomicLong(0)

    /** Fetches a player is waiting on; the warmer stands aside while any are. */
    private val playersWaiting = AtomicInteger(0)

    /** Epoch millis until which the origin has asked us to stop asking. */
    private val throttledUntil = AtomicLong(0)

    /**
     * Image paths known to be on disk. The files are named by hash, so this is
     * the only way back from a clue to "can I serve it right now?" - which is
     * what lets the game offer clues that are guaranteed to display.
     */
    private val cachedPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** When a player last asked for an image, so warming can stand aside. */
    private val lastPlayerAt = AtomicLong(0)

    data class Entry(val bytes: ByteArray, val contentType: ContentType, val etag: String)

    override fun isCached(imagePath: String): Boolean = cachedPaths.contains(imagePath)

    override fun isThrottled(): Boolean = throttledUntil.get() > System.currentTimeMillis()

    /**
     * Works out which of the dataset's images are already on disk. Called once
     * on boot: 5,000 file checks cost milliseconds and save the game from
     * offering clues it cannot show.
     */
    fun primeCachedIndex(imagePaths: Collection<String>): Int {
        cachedPaths.clear()
        imagePaths.forEach { path -> if (isOnDisk(sha256(path))) cachedPaths.add(path) }
        return cachedPaths.size
    }

    fun cachedCount(): Int = cachedPaths.size

    /** True if a player asked for an image within the given window. */
    fun playerActiveWithinMillis(window: Long): Boolean =
        System.currentTimeMillis() - lastPlayerAt.get() < window

    /** Seconds left of the origin's rate-limit window, 0 when images are flowing. */
    fun throttledForSeconds(): Int {
        val left = throttledUntil.get() - System.currentTimeMillis()
        return if (left <= 0) 0 else ((left / 1000) + 1).toInt()
    }

    suspend fun get(imagePath: String): Entry {
        lastPlayerAt.set(System.currentTimeMillis())
        val key = sha256(imagePath)
        readFromDisk(key)?.let { return it }
        failIfThrottled()

        playersWaiting.incrementAndGet()
        try {
            return pacer.withLock {
                // Another request may have populated the cache while we queued.
                readFromDisk(key) ?: paceThenDownload(imagePath, key)
            }
        } finally {
            playersWaiting.decrementAndGet()
        }
    }

    /**
     * Downloads an image only if it is missing, without reading it back.
     * Returns true when it actually had to fetch. Used by [ImageWarmer].
     */
    suspend fun cacheIfMissing(imagePath: String): Boolean {
        val key = sha256(imagePath)
        if (isOnDisk(key)) return false
        failIfThrottled()
        return pacer.withLock {
            if (isOnDisk(key)) false else { paceThenDownload(imagePath, key); true }
        }
    }

    /** True while a player is queued for an image; warming should yield. */
    fun playersAreWaiting(): Boolean = playersWaiting.get() > 0

    private fun failIfThrottled() {
        val pauseLeft = throttledUntil.get() - System.currentTimeMillis()
        if (pauseLeft > 0) {
            throw ApiException(
                HttpStatusCode.BadGateway,
                "image_rate_limited",
                "The guide site is rate-limiting image requests; retry in " +
                    ((pauseLeft / 1000) + 1) + "s.",
            )
        }
    }

    /** Holds the pacer's lock, so the wait also spaces out everyone behind it. */
    private suspend fun paceThenDownload(imagePath: String, key: String): Entry {
        val since = System.currentTimeMillis() - lastFetchAt.get()
        val wait = config.imageMinIntervalMillis - since
        if (wait > 0) delay(wait)
        try {
            return download(imagePath, key)
        } finally {
            lastFetchAt.set(System.currentTimeMillis())
        }
    }

    /**
     * Fetches one image.
     *
     * A 429 trips the shared pause above; a 5xx is retried a couple of times
     * with a doubling delay, honouring `Retry-After` when the origin sends it.
     */
    private suspend fun download(imagePath: String, key: String): Entry = withContext(Dispatchers.IO) {
        val url = PlonkItScraper.BASE_URL + encodePath(imagePath)
        var backoff = INITIAL_BACKOFF_MS

        repeat(MAX_ATTEMPTS) { attempt ->
            val response = client.get(url) {
                header("User-Agent", PlonkItScraper.USER_AGENT)
                header("Referer", PlonkItScraper.BASE_URL + "/guide")
                header("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
            }

            if (response.status.isSuccess()) {
                val bytes = response.bodyAsBytes()
                val contentType = response.headers[HttpHeaders.ContentType]
                    ?.let { runCatching { ContentType.parse(it) }.getOrNull() }
                    ?: ContentType.Image.PNG
                writeToDisk(key, bytes, contentType)
                cachedPaths.add(imagePath)
                return@withContext Entry(bytes, contentType, quotedEtag(key))
            }

            val retryAfter = response.headers[HttpHeaders.RetryAfter]?.toLongOrNull()?.times(1_000)

            if (response.status == HttpStatusCode.TooManyRequests) {
                // Pause every fetch for a while: the limit is per client, so one
                // more request now would only push the window further out.
                val pause = (retryAfter ?: DEFAULT_THROTTLE_PAUSE_MS)
                    .coerceIn(DEFAULT_THROTTLE_PAUSE_MS, MAX_THROTTLE_PAUSE_MS)
                throttledUntil.set(System.currentTimeMillis() + pause)
                log.warn("Upstream is rate-limiting us; pausing image fetches for {}ms", pause)
                throw ApiException(
                    HttpStatusCode.BadGateway,
                    "image_rate_limited",
                    "The guide site is rate-limiting image requests; retry in " +
                        ((pause / 1000) + 1) + "s.",
                )
            }

            if (response.status.value < 500 || attempt == MAX_ATTEMPTS - 1) {
                log.warn("Upstream image {} -> {}", imagePath, response.status)
                throw ApiException(
                    HttpStatusCode.BadGateway,
                    "image_unavailable",
                    "Could not load the clue image.",
                )
            }

            val wait = retryAfter?.coerceAtMost(MAX_BACKOFF_MS) ?: backoff
            log.info("Upstream image {} -> {}, retrying in {}ms", imagePath, response.status, wait)
            delay(wait)
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
        throw ApiException(HttpStatusCode.BadGateway, "image_unavailable", "Could not load the clue image.")
    }

    private fun isOnDisk(key: String): Boolean =
        Files.exists(blobPath(key)) && Files.exists(metaPath(key))

    private suspend fun readFromDisk(key: String): Entry? = withContext(Dispatchers.IO) {
        val blob = blobPath(key)
        val meta = metaPath(key)
        if (!Files.exists(blob) || !Files.exists(meta)) return@withContext null
        runCatching {
            Entry(
                bytes = Files.readAllBytes(blob),
                contentType = ContentType.parse(Files.readString(meta).trim()),
                etag = quotedEtag(key),
            )
        }.getOrNull()
    }

    private fun writeToDisk(key: String, bytes: ByteArray, contentType: ContentType) {
        runCatching {
            Files.createDirectories(config.imageCacheDir)
            val tmp = config.imageCacheDir.resolve("$key.tmp")
            Files.write(tmp, bytes)
            Files.move(tmp, blobPath(key), StandardCopyOption.REPLACE_EXISTING)
            Files.writeString(metaPath(key), contentType.toString())
        }.onFailure { log.warn("Could not cache image {}: {}", key, it.message) }
    }

    private fun blobPath(key: String): Path = config.imageCacheDir.resolve(key)
    private fun metaPath(key: String): Path = config.imageCacheDir.resolve("$key.type")
    private fun quotedEtag(key: String) = "\"" + key.take(24) + "\""

    /**
     * Percent-encodes each segment: guide images legitimately contain spaces,
     * exclamation marks and non-ASCII characters.
     */
    private fun encodePath(path: String): String = path.split('/')
        .joinToString("/") { segment ->
            if (segment.isEmpty()) segment else segment.encodeURLPathPart()
        }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val INITIAL_BACKOFF_MS = 800L
        const val MAX_BACKOFF_MS = 8_000L
        const val DEFAULT_THROTTLE_PAUSE_MS = 20_000L

        /**
         * The origin has been seen asking for 1390 seconds, so the ceiling has
         * to be generous: asking again inside its window only extends it.
         */
        const val MAX_THROTTLE_PAUSE_MS = 30 * 60_000L
    }
}

fun Route.imageRoutes(repository: ClueRepository, cache: ImageCache) {
    /**
     * Lets the UI explain a broken clue: an `<img>` tag cannot read the JSON
     * error body, so it asks here why the image did not arrive.
     */
    get("/api/image/status") {
        val seconds = cache.throttledForSeconds()
        call.respond(ImageStatusDto(throttled = seconds > 0, retryInSeconds = seconds))
    }

    get("/api/image") {
        val path = call.request.queryParameters["path"]
            ?: throw ApiException(HttpStatusCode.BadRequest, "missing_path", "Query parameter 'path' is required.")

        // Allowlist: only images referenced by the dataset can be proxied.
        if (path !in repository.snapshot.allowedImagePaths) {
            throw ApiException(HttpStatusCode.NotFound, "unknown_image", "That image is not part of the dataset.")
        }

        val entry = cache.get(path)
        if (call.request.headers[HttpHeaders.IfNoneMatch] == entry.etag) {
            call.response.header(HttpHeaders.ETag, entry.etag)
            call.respond(HttpStatusCode.NotModified)
            return@get
        }
        call.response.header(HttpHeaders.ETag, entry.etag)
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(entry.bytes, entry.contentType)
    }
}
