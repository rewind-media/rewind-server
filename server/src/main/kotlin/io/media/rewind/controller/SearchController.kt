package io.media.rewind.controller

import com.media.rewind.model.SearchRequest
import com.media.rewind.model.SearchResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.media.rewind.SearchJobQueue
import io.media.rewind.SearchProps
import io.media.rewind.cache.queue.WorkerEvent
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

fun Route.searchRoutes(queue: SearchJobQueue) {
    post("/search/get") {
        val req = call.receive<SearchRequest>()
        if(req.text.isBlank()) {
            call.respond(SearchResponse(emptyList()))
        } else {
            val jobId = queue.submit(SearchProps(req.text))
            when (val res = queue.monitor(jobId).filter { it.isTerminal() }.first()) {
                is WorkerEvent.Success -> call.respond(Json.decodeFromString<SearchResponse>(res.payload))
                else -> call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }
}



