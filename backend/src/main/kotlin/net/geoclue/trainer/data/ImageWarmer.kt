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
 * Fills the image cache in the background, so that playing never depends on the
 * guide site.
 *
 * It is off by default and should usually stay off: the guide site's robots.txt
 * allowlists three search engines and disallows every other crawler, and it
 * enforces that with rate limits that escalate the longer you fetch - measured,
 * a bulk pass decays from about 3 images a minute to 30 an hour, with blocks of
 * up to half an hour. The game does not need a complete cache; it plays from
 * what it has. This exists for the case where a bulk copy is permitted, and it
 * is deliberately unhurried and self-correcting:
 *
 *  - it waits [AppConfig.warmIntervalMillis] between images (30s by default),
 *  - every rate-limit doubles that wait, and a run of successes eases it back,
 *  - it stands aside while anyone is playing, so a live session gets the whole
 *    request budget,
 *  - images already on disk are skipped without a request.
 *
 * That last property makes it cheap to run against a full cache: the job skips
 * everything and finishes in seconds.
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
        // Country-level clues first, always: they are what the default game mode
        // plays from, so a run that is interrupted still leaves the useful half.
        val clues = if (config.warmCacheAll) {
            snapshot.coreClues + snapshot.clues.filterNot { it.isCore }
        } else {
            snapshot.coreClues
        }
        val paths = clues.map { it.imageUrl }.distinct()
        log.info(
            "Warming the image cache: {} images ({}), {} already on disk, one every {}ms",
            paths.size,
            if (config.warmCacheAll) "every clue" else "country-level clues",
            paths.count { cache.isCached(it) },
            config.warmIntervalMillis,
        )

        var interval = config.warmIntervalMillis
        var successes = 0
        var fetched = 0
        var alreadyThere = 0
        var failed = 0
        var index = 0

        while (index < paths.size) {
            // A player asking for a clue always comes first.
            if (cache.playerActiveWithinMillis(config.warmQuietMillis)) {
                delay(PLAYER_YIELD_MS)
                continue
            }

            var didFetch = false
            try {
                didFetch = cache.cacheIfMissing(paths[index])
                if (didFetch) fetched++ else alreadyThere++
                index++
                successes++
            } catch (e: ApiException) {
                if (e.code == "image_rate_limited") {
                    successes = 0
                    interval = (interval * 2).coerceAtMost(MAX_INTERVAL_MS)
                    val pause = (cache.throttledForSeconds() + 5).coerceAtMost(MAX_PAUSE_SECONDS)
                    log.info(
                        "Rate-limited at {}/{}; waiting {}s and slowing to one image every {}ms",
                        index, paths.size, pause, interval,
                    )
                    delay(pause * 1_000L)
                    continue
                }
                failed++
                index++
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                log.debug("Warming {} failed: {}", paths[index], t.message)
                failed++
                index++
            }

            if (index % PROGRESS_EVERY == 0) {
                log.info(
                    "Warming {}/{}: {} fetched, {} already cached, {} unavailable",
                    index, paths.size, fetched, alreadyThere, failed,
                )
            }

            // A clean run earns back some speed, down to the configured pace.
            if (successes >= EASE_AFTER && interval > config.warmIntervalMillis) {
                interval = (interval / 2).coerceAtLeast(config.warmIntervalMillis)
                successes = 0
                log.info("Warming back up to one image every {}ms", interval)
            }

            // Only a real fetch needs pacing; skipping a cached image is free.
            if (didFetch) delay(interval)
        }

        log.info(
            "Image cache warm: {}/{} images ready ({} fetched now, {} unavailable)",
            paths.size - failed, paths.size, fetched, failed,
        )
    }

    private companion object {
        const val PROGRESS_EVERY = 100
        const val PLAYER_YIELD_MS = 5_000L
        const val EASE_AFTER = 20

        /** However hard we are throttled, keep the pace within reason. */
        const val MAX_INTERVAL_MS = 10 * 60_000L

        /** Cap on a single pause, so a bad `Retry-After` cannot park the job for a day. */
        const val MAX_PAUSE_SECONDS = 30 * 60
    }
}
