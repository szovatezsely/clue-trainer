package net.geoclue.trainer.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.geoclue.trainer.AppConfig
import net.geoclue.trainer.model.Clue
import net.geoclue.trainer.model.ClueDataset
import net.geoclue.trainer.model.Country
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/** Where the currently served dataset came from, and whether a re-scrape is running. */
enum class RefreshState { IDLE, RUNNING, FAILED }

/**
 * Owns the clue dataset.
 *
 * Resolution order on boot:
 *  1. `${DATA_DIR}/clues.json` - a previous scrape, kept in the Docker volume.
 *  2. the dataset bundled in `resources/seed/clues.json` - so the game is
 *     playable the second the container is up, with no network access at all.
 *
 * A live re-scrape (startup or `POST /api/dataset/refresh`) replaces both.
 */
class ClueRepository(private val config: AppConfig) {
    private val log = LoggerFactory.getLogger(ClueRepository::class.java)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val refreshMutex = Mutex()
    private val stateRef = AtomicReference(RefreshState.IDLE)
    private val snapshotRef = AtomicReference<Snapshot>()

    /** Immutable view of the dataset plus the lookups the game needs on every request. */
    class Snapshot(val dataset: ClueDataset) {
        val countriesByCode: Map<String, Country> = dataset.countries.associateBy { it.code }
        val clues: List<Clue> = dataset.clues.filter { it.countryCode in countriesByCode }
        val coreClues: List<Clue> = clues.filter { it.isCore }
        val playableCountries: List<Country> = dataset.countries.filter { it.clueCount > 0 }
        val countriesByContinent: Map<String, List<Country>> = playableCountries.groupBy { it.continent }
        val continentOfCode: Map<String, String> = dataset.countries.associate { it.code to it.continent }
        val continents: List<String> = countriesByContinent.keys.sorted()
        val tags: List<String> = clues.flatMap { it.tags }.distinct().sorted()

        /** Image paths we are willing to proxy - anything else is not ours to fetch. */
        val allowedImagePaths: Set<String> =
            (clues.map { it.imageUrl } + dataset.countries.mapNotNull { it.heroImage }).toSet()

        fun cluesFor(continent: String?, coreOnly: Boolean): List<Clue> {
            val base = if (coreOnly) coreClues else clues
            if (continent == null) return base
            val codes = countriesByContinent[continent]?.mapTo(HashSet()) { it.code } ?: return emptyList()
            return base.filter { it.countryCode in codes }
        }
    }

    val snapshot: Snapshot get() = snapshotRef.get() ?: error("dataset not loaded yet")
    val refreshState: RefreshState get() = stateRef.get()

    fun load() {
        val cached = runCatching { readCache() }.getOrElse {
            log.warn("Could not read cached dataset: {}", it.message)
            null
        }
        val dataset = cached ?: readSeed()
        snapshotRef.set(Snapshot(dataset))
        log.info(
            "Loaded {} clues across {} countries (source: {}, scraped {})",
            dataset.clues.size, dataset.countries.size,
            if (cached != null) "cache" else "bundled seed", dataset.scrapedAt,
        )
    }

    /** True when the on-disk cache is missing or older than the configured max age. */
    fun isStale(): Boolean {
        val file = config.clueCacheFile
        if (!Files.exists(file)) return true
        val scrapedAt = runCatching { Instant.parse(snapshot.dataset.scrapedAt) }.getOrNull() ?: return true
        return Duration.between(scrapedAt, Instant.now()).toDays() >= config.datasetMaxAgeDays
    }

    /** Kicks off a re-scrape in the background; returns false when one is already running. */
    fun refreshInBackground(scope: CoroutineScope): Boolean {
        if (!stateRef.compareAndSet(RefreshState.IDLE, RefreshState.RUNNING) &&
            !stateRef.compareAndSet(RefreshState.FAILED, RefreshState.RUNNING)
        ) {
            return false
        }
        scope.launch(Dispatchers.IO) {
            refreshMutex.withLock {
                val scraper = PlonkItScraper(delayMillis = config.scrapeDelayMillis)
                try {
                    log.info("Re-scraping {} ...", PlonkItScraper.BASE_URL)
                    val dataset = scraper.scrape { done, total, country ->
                        if (done % 10 == 0 || done == total) log.info("  scraped {}/{} ({})", done, total, country)
                    }
                    if (dataset.clues.isEmpty()) error("scrape produced no clues")
                    writeCache(dataset)
                    snapshotRef.set(Snapshot(dataset))
                    stateRef.set(RefreshState.IDLE)
                    log.info("Refresh done: {} clues, {} countries", dataset.clues.size, dataset.countries.size)
                } catch (t: Throwable) {
                    stateRef.set(RefreshState.FAILED)
                    log.error("Refresh failed, keeping the previous dataset", t)
                } finally {
                    scraper.close()
                }
            }
        }
        return true
    }

    private fun readCache(): ClueDataset? {
        val file = config.clueCacheFile
        if (!Files.exists(file)) return null
        return json.decodeFromString<ClueDataset>(Files.readString(file))
            .takeIf { it.clues.isNotEmpty() }
    }

    private fun readSeed(): ClueDataset {
        val stream = javaClass.getResourceAsStream(SEED_RESOURCE)
            ?: error("bundled dataset $SEED_RESOURCE is missing from the jar")
        return stream.use { json.decodeFromString<ClueDataset>(it.readBytes().decodeToString()) }
    }

    private suspend fun writeCache(dataset: ClueDataset) = withContext(Dispatchers.IO) {
        runCatching {
            Files.createDirectories(config.dataDir)
            val tmp = config.dataDir.resolve("clues.json.tmp")
            Files.writeString(tmp, json.encodeToString(dataset))
            Files.move(tmp, config.clueCacheFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { log.warn("Could not persist the dataset: {}", it.message) }
        Unit
    }

    private companion object {
        const val SEED_RESOURCE = "/seed/clues.json"
    }
}
