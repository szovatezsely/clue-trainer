package net.geoclue.trainer

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Runtime configuration, entirely driven by environment variables so that the
 * container can be tuned from docker-compose without rebuilding.
 */
data class AppConfig(
    /** Port the HTTP server binds to. */
    val port: Int = env("PORT")?.toIntOrNull() ?: 8080,
    /** Writable directory for the clue cache and the proxied image cache. */
    val dataDir: Path = Paths.get(env("DATA_DIR") ?: "data"),
    /** Re-scrape plonkit.net on boot when the cached dataset is missing or stale. */
    val scrapeOnStart: Boolean = env("SCRAPE_ON_START").toBoolean(),
    /** Allow POST /api/dataset/refresh to trigger a live re-scrape. */
    val allowRefresh: Boolean = env("ALLOW_REFRESH")?.toBoolean() ?: true,
    /** A cached dataset older than this is considered stale. */
    val datasetMaxAgeDays: Long = env("DATASET_MAX_AGE_DAYS")?.toLongOrNull() ?: 30,
    /** Politeness delay between two guide page requests while scraping, in ms. */
    val scrapeDelayMillis: Long = env("SCRAPE_DELAY_MS")?.toLongOrNull() ?: 1_200,
    /** How long an idle game session is kept in memory. */
    val sessionTtlHours: Long = env("SESSION_TTL_HOURS")?.toLongOrNull() ?: 12,
    /**
     * Smallest gap between two image fetches from the origin. The guide site
     * rate-limits bursts, and a player reads for far longer than this between
     * clues, so pacing costs nothing and makes a burst impossible.
     */
    val imageMinIntervalMillis: Long = env("IMAGE_MIN_INTERVAL_MS")?.toLongOrNull() ?: 3_000,
    /** Fill the image cache in the background on boot (see ImageWarmer). */
    val warmCache: Boolean = env("WARM_CACHE").toBoolean(),
    /** Warm every clue image rather than only the country-level ones. */
    val warmCacheAll: Boolean = env("WARM_CACHE_ALL").toBoolean(),
    /** Grace period before warming starts, so booting is not competing with it. */
    val warmStartDelayMillis: Long = env("WARM_START_DELAY_MS")?.toLongOrNull() ?: 20_000,
) {
    val clueCacheFile: Path get() = dataDir.resolve("clues.json")
    val imageCacheDir: Path get() = dataDir.resolve("images")

    companion object {
        private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
