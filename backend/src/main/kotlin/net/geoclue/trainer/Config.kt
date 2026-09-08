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
) {
    val clueCacheFile: Path get() = dataDir.resolve("clues.json")
    val imageCacheDir: Path get() = dataDir.resolve("images")

    companion object {
        private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}
