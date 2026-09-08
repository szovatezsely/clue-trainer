package net.geoclue.trainer.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.geoclue.trainer.AppConfig
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.routes.ImageCache
import org.slf4j.LoggerFactory

/**
 * Fills the image cache in the background, once, so that playing never depends
 * on the guide site.
 *
 * A fresh deployment starts with an empty cache, which means every clue is a
 * cold fetch from plonkit.net - and a handful of those in a row is exactly what
 * its rate limiter punishes. Pulling the images ahead of time at a deliberately
 * slow pace turns that into a single quiet crawl: afterwards every clue is
 * served from local disk, the origin is never touched while anyone plays, and
 * the rate limit cannot apply.
 *
 * The job is safe to run on every boot. Images already on disk are skipped
 * without a request, so a warm cache makes it a no-op that finishes in seconds.
 */
class ImageWarmer(
    private val config: AppConfig,
    private val repository: ClueRepository,
    private val cache: ImageCache,
) {
    private val log = LoggerFactory.getLogger(ImageWarmer::class.java)

    fun start(scope: CoroutineScope): Job = scope.launch(Dispatchers.IO) { warm() }

    private suspend fun warm() {
        // Let the server finish booting and serve its first requests first.
        delay(config.warmStartDelayMillis)

        val snapshot = repository.snapshot
        val clues = if (config.warmCacheAll) snapshot.clues else snapshot.coreClues
        val paths = clues.map { it.imageUrl }.distinct()
        val scope = if (config.warmCacheAll) "every clue" else "country-level clues"
        log.info(
            "Warming the image cache: {} images ({}), one every {}ms at most",
            paths.size, scope, config.imageMinIntervalMillis,
        )

        var fetched = 0
        var alreadyThere = 0
        var failed = 0
        var index = 0

        while (index < paths.size) {
            // A player asking for a clue always goes ahead of the warmer.
            while (cache.playersAreWaiting()) delay(PLAYER_YIELD_MS)

            try {
                if (cache.cacheIfMissing(paths[index])) fetched++ else alreadyThere++
                index++
            } catch (e: ApiException) {
                if (e.code == "image_rate_limited") {
                    // Wait out the origin's window and try the same image again.
                    val pause = (cache.throttledForSeconds() + 5).coerceAtMost(MAX_PAUSE_SECONDS)
                    log.info("Warming paused for {}s: the origin is rate-limiting us", pause)
                    delay(pause * 1_000L)
                } else {
                    failed++
                    index++
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                log.debug("Warming {} failed: {}", paths[index], t.message)
                failed++
                index++
            }

            if (index % PROGRESS_EVERY == 0 && index > 0) {
                log.info("Warming {}/{} ({} fetched, {} already cached)", index, paths.size, fetched, alreadyThere)
            }
        }

        log.info(
            "Image cache warm: {} images ready ({} fetched now, {} already cached, {} unavailable)",
            paths.size - failed, fetched, alreadyThere, failed,
        )
    }

    private companion object {
        const val PROGRESS_EVERY = 100
        const val PLAYER_YIELD_MS = 500L

        /** Cap on a single pause, so a bad `Retry-After` cannot park the job for a day. */
        const val MAX_PAUSE_SECONDS = 30 * 60
    }
}
