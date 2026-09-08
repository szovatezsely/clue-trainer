package net.geoclue.trainer.routes

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import net.geoclue.trainer.AppConfig
import net.geoclue.trainer.data.ClueRepository
import net.geoclue.trainer.data.RefreshState
import net.geoclue.trainer.game.ApiException
import net.geoclue.trainer.game.GameService
import net.geoclue.trainer.game.GameSession
import net.geoclue.trainer.game.QuestionFilter
import net.geoclue.trainer.game.SessionStore
import net.geoclue.trainer.game.toOption
import net.geoclue.trainer.model.AnswerRequest
import net.geoclue.trainer.model.ContinentDto
import net.geoclue.trainer.model.HealthDto
import net.geoclue.trainer.model.MetaDto
import net.geoclue.trainer.model.RefreshDto
import net.geoclue.trainer.model.SessionDto

fun Route.apiRoutes(
    config: AppConfig,
    repository: ClueRepository,
    sessions: SessionStore,
    game: GameService,
    appScope: CoroutineScope,
) {
    route("/api") {

        get("/health") {
            val snapshot = repository.snapshot
            call.respond(
                HealthDto(
                    status = "ok",
                    clueCount = snapshot.clues.size,
                    countryCount = snapshot.playableCountries.size,
                    scrapedAt = snapshot.dataset.scrapedAt,
                ),
            )
        }

        /** Everything the UI needs to render its filters and country labels. */
        get("/meta") {
            val snapshot = repository.snapshot
            call.respond(
                MetaDto(
                    source = snapshot.dataset.source,
                    scrapedAt = snapshot.dataset.scrapedAt,
                    clueCount = snapshot.clues.size,
                    coreClueCount = snapshot.coreClues.size,
                    countryCount = snapshot.playableCountries.size,
                    continents = snapshot.continents.map { continent ->
                        val countries = snapshot.countriesByContinent[continent].orEmpty()
                        ContinentDto(
                            name = continent,
                            countryCount = countries.size,
                            clueCount = countries.sumOf { it.clueCount },
                            coreClueCount = snapshot.coreClues.count {
                                snapshot.continentOfCode[it.countryCode] == continent
                            },
                        )
                    },
                    countries = snapshot.playableCountries.map { it.toOption() },
                    tags = snapshot.tags,
                    refreshAllowed = config.allowRefresh,
                    refreshState = repository.refreshState.name.lowercase(),
                ),
            )
        }

        route("/game/sessions") {
            post {
                val session = sessions.create()
                call.respond(HttpStatusCode.Created, SessionDto(session.id, session.stats()))
            }

            /** Lets a reloaded page pick its running score back up. */
            get("/{id}") {
                val session = requireSession(sessions)
                call.respond(SessionDto(session.id, session.stats()))
            }

            get("/{id}/next") {
                val session = requireSession(sessions)
                val snapshot = repository.snapshot
                val continent = call.request.queryParameters["continent"]
                    ?.takeIf { it.isNotBlank() && !it.equals("all", ignoreCase = true) }
                if (continent != null && continent !in snapshot.continents) {
                    throw ApiException(HttpStatusCode.BadRequest, "unknown_continent", "No such continent: $continent")
                }
                val coreOnly = call.request.queryParameters["scope"].equals("core", ignoreCase = true)
                call.respond(game.nextQuestion(session, QuestionFilter(continent, coreOnly)))
            }

            post("/{id}/answer") {
                val session = requireSession(sessions)
                call.respond(game.answer(session, call.receive<AnswerRequest>()))
            }

            /** Gives up on the current clue - it is not scored either way. */
            post("/{id}/skip") {
                val session = requireSession(sessions)
                call.respond(SessionDto(session.id, game.skip(session)))
            }

            post("/{id}/reset") {
                val session = requireSession(sessions)
                synchronized(session) { session.reset() }
                call.respond(SessionDto(session.id, session.stats()))
            }
        }

        route("/dataset") {
            get("/status") {
                call.respond(
                    RefreshDto(
                        state = repository.refreshState.name.lowercase(),
                        message = "Dataset scraped at " + repository.snapshot.dataset.scrapedAt,
                    ),
                )
            }

            /** Re-runs the PlonkIt scrape in the background. */
            post("/refresh") {
                if (!config.allowRefresh) {
                    throw ApiException(HttpStatusCode.Forbidden, "refresh_disabled", "Refreshing is disabled.")
                }
                if (repository.refreshState == RefreshState.RUNNING) {
                    call.respond(HttpStatusCode.Accepted, RefreshDto("running", "A refresh is already running."))
                    return@post
                }
                repository.refreshInBackground(appScope)
                call.respond(
                    HttpStatusCode.Accepted,
                    RefreshDto("running", "Scraping every country guide; this takes a few minutes."),
                )
            }
        }
    }
}

private fun io.ktor.server.routing.RoutingContext.requireSession(sessions: SessionStore): GameSession {
    val id = call.parameters["id"].orEmpty()
    return sessions.find(id) ?: throw ApiException(
        HttpStatusCode.NotFound,
        "unknown_session",
        "This game session expired. Start a new one.",
    )
}
