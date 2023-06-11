package io.media.rewind.controller

import com.media.rewind.model.DeleteLibrariesRequest
import com.media.rewind.model.Library
import com.media.rewind.model.ScanLibrariesRequest
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.media.rewind.ScanJobQueue
import io.media.rewind.database.Database

fun Route.showRoutes(db: Database) {
    get("/show/list/{libraryId}") {
        call.parameters["libraryId"]?.let { libraryId ->
            call.respond(db.listShows(libraryId).map { it.showInfo })
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
    get("/show/get/{showId}") {
        val show = call.parameters["showId"]?.let { db.getShow(it) }?.showInfo
        if (show == null) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            call.respond(show)
        }
    }
}

