package io.media.rewind

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.media.rewind.cache.Cache
import io.media.rewind.controller.*
import io.media.rewind.database.Database
import io.media.rewind.plugins.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KotlinLogging

val config by lazy { ServerConfig.fromConfig() }
val cache by lazy { Cache.fromConfig(config.cache) }
val db by lazy {
    Database.fromConfig(config.database).apply {
        runBlocking { init() }
    }
}
val logger by lazy { KotlinLogging.logger { } }

@Serializable
data class TestReq(val int: Int)

fun main(): Unit = runBlocking {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    configureHTTP()
    configureMonitoring()
    configureSerialization()

    configureSession(db, false) // TODO make config for this

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            logger.error(cause) { "Error handling ${call.request.httpMethod} ${call.request.path()}" }
            call.respondText(text = "Internal Server Error", status = HttpStatusCode.InternalServerError)
        }
    }
    routing {
        route("/api") {
            authRoutes(db)
            install(mkAuthNPlugin())
            libRoutes(db, cache.getScanJobQueue())
            showRoutes(db)
            seasonRoutes(db)
            episodeRoutes(db)
            imageRoutes(db, cache, cache.getImageJobQueue())
            streamRoutes(db, cache, cache.getStreamJobQueue())
            searchRoutes(cache.getSearchJobQueue())
            progressRoutes(db)
        }

        singlePageApplication {
            useResources = true
            react("")
        }
    }
}
