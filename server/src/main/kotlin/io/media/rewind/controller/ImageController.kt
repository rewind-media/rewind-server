package io.media.rewind.controller

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.media.rewind.ImageJobQueue
import io.media.rewind.cache.Cache
import io.media.rewind.cache.queue.WorkerEvent
import io.media.rewind.database.Database
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.hours

fun Route.imageRoutes(db: Database, cache: Cache, queue: ImageJobQueue) {
    get("/image/{imageId}") {
        call.parameters["imageId"]?.let { imageId ->
            val cachedImage = cache.getImage(imageId)
            if(cachedImage != null) {
                cache.expireImage(imageId, (Clock.System.now() + 1.hours).toJavaInstant())
                call.respond(cachedImage)
            } else {
                val imageInfo = db.getImage(imageId)
                if(imageInfo == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    queue.submit(imageInfo)
                        .let { jobId ->
                            queue.monitor(jobId)
                                .filter { it is WorkerEvent.Success || it is WorkerEvent.Fail }
                        }.first()
                        .let {
                            when (it) {
                                is WorkerEvent.Success -> call.respond(Json.decodeFromString<ByteArray>(it.payload))
                                else -> call.respond(HttpStatusCode.InternalServerError)
                            }
                        }
                }
            }
        } ?: call.respond(HttpStatusCode.BadRequest)
    }
}



