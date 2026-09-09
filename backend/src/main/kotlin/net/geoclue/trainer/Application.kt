package net.geoclue.trainer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.ImageWarmer
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.game.GameService
import net.geoclue.trainer.game.SessionStore
import net.geoclue.trainer.model.ErrorDto
import net.geoclue.trainer.routes.ImageCache
import net.geoclue.trainer.routes.apiRoutes
import net.geoclue.trainer.routes.imageRoutes
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun main() {
    val config = AppConfig()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }.start(wait = true)
}

fun Application.module(config: AppConfig) {
    val log = LoggerFactory.getLogger("net.geoclue.trainer")
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository = ClueRepository(config)
    repository.load()

    val sessions = SessionStore(ttlHours = config.sessionTtlHours)
    val httpClient = HttpClient(CIO) {
        expectSuccess = false
        engine {
            requestTimeout = 30_000
        }
    }
    val imageCache = ImageCache(config, httpClient)

    // Which clue images are already on disk decides which clues the game can
    // hand out without depending on the guide site.
    val cached = imageCache.primeCachedIndex(repository.snapshot.allowedImagePaths)
    log.info("Image cache holds {} of {} clue images", cached, repository.snapshot.allowedImagePaths.size)

    val game = GameService(repository, imageCache)

    monitor.subscribe(io.ktor.server.application.ApplicationStopping) {
        httpClient.close()
        appScope.cancel()
    }

    install(DefaultHeaders)
    install(CallLogging) {
        level = Level.INFO
        // Image requests are frequent and uninteresting once cached.
        filter { call -> !call.request.local.uri.startsWith("/api/image") }
    }
    install(ContentNegotiation) {
        json(Json { prettyPrint = false; encodeDefaults = true; ignoreUnknownKeys = true })
    }
    install(Compression) {
        gzip {
            matchContentType(ContentType.Application.Json, ContentType.Text.Any)
        }
    }
    install(CORS) {
        // The SPA is normally served from the same origin by nginx; this keeps
        // `npm run dev` against a containerised backend working too.
        anyHost()
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
        allowMethod(io.ktor.http.HttpMethod.Post)
        allowMethod(io.ktor.http.HttpMethod.Get)
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorDto(cause.code, cause.message))
        }
        exception<Throwable> { call, cause ->
            log.error("Unhandled failure on {}", call.request.local.uri, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorDto("internal_error", "Something went wrong on the server."),
            )
        }
    }

    routing {
        apiRoutes(config, repository, sessions, game, appScope)
        imageRoutes(repository, imageCache)
    }

    if (config.warmCache) {
        // Pull the clue images in one slow background pass, so that playing
        // never waits on - or gets rate-limited by - the guide site.
        ImageWarmer(config, repository, imageCache).start(appScope)
    }

    if (config.scrapeOnStart) {
        if (repository.isStale()) {
            log.info("SCRAPE_ON_START is set and the dataset is stale - refreshing in the background")
            repository.refreshInBackground(appScope)
        } else {
            log.info("SCRAPE_ON_START is set but the cached dataset is still fresh - skipping")
        }
    }

    log.info("Clue Trainer API listening on port {}", config.port)
}
