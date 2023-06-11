package io.media.rewind.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.media.rewind.UserSession
import io.media.rewind.database.Database
import mu.KotlinLogging

private val log = KotlinLogging.logger { }
fun mkAuthNPlugin() = createRouteScopedPlugin(name = "AuthN") {
    onCall {
        if (it.sessions.get<UserSession>() == null) {
            it.response.status(HttpStatusCode.Forbidden)
        }
    }
}

fun mkAdminAuthZPlugin(db: Database) = createRouteScopedPlugin(name = "AuthN") {
    onCall {
        val isAdmin =
            it.sessions.get<UserSession>()?.username?.let { user -> db.getUser(user)?.user?.permissions?.isAdmin }
                ?: false
        if (!isAdmin) {
            it.response.status(HttpStatusCode.Forbidden)
        }
    }
}

