package io.media.rewind.controller

import com.media.rewind.model.Progress
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.media.rewind.UserProgress
import io.media.rewind.UserSession
import io.media.rewind.database.Database
import io.media.rewind.database.ProgressSort
import kotlinx.datetime.Clock

fun Route.progressRoutes(db: Database) {
    get("/user/progress/get/{id}") {
        call.parameters["id"]?.let { mediaId ->
            call.sessions.get<UserSession>()?.username?.let { username ->
                call.respond(
                    db.getProgress(mediaId, username)?.progress ?: Progress(
                        mediaId,
                        0.0,
                        Clock.System.now().toEpochMilliseconds().toDouble()
                    )
                )
            } ?: call.respond(HttpStatusCode.Forbidden)
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
    post("/user/progress/latest") {
        call.sessions.get<UserSession>()?.username?.let { username ->
            call.respond(
                db.listProgress(
                    username = username,
                    sortOrder = ProgressSort.Latest,
                    limit = 100,
                    minPercent = 0.01,
                    maxPercent = 0.99
                ).map { it.progress }
            )
        } ?: call.respond(HttpStatusCode.Forbidden)
    }
    post("/user/progress/put") {
        call.sessions.get<UserSession>()?.username?.let { username ->
            val req: Progress = call.receive()
            db.upsertProgress(UserProgress(username, req))
            call.respond(HttpStatusCode.OK)
        }
    }
}

