package io.media.rewind.plugins

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import io.media.rewind.UserSession
import io.media.rewind.database.Database
import io.media.rewind.database.SessionStorage
import kotlinx.coroutines.runBlocking
import kotlin.time.Duration.Companion.days

fun Application.configureSession(database: Database, secure: Boolean = true) = runBlocking {
    val dbSessionStorage = database.mkSessionStorage()
    install(Sessions) {
        cookie<UserSession>(
            if (secure) "Rewind_Session" else "Rewind_Session_Insecure",
            dbSessionStorage.toKtorSessionStorage()
        ) {
            cookie.maxAgeInSeconds = 30.days.inWholeSeconds
            cookie.secure = secure
        }
    }
}

fun SessionStorage.toKtorSessionStorage(): io.ktor.server.sessions.SessionStorage =
    object : io.ktor.server.sessions.SessionStorage {
        override suspend fun invalidate(id: String) = this@toKtorSessionStorage.invalidate(id)

        override suspend fun read(id: String): String = this@toKtorSessionStorage.read(id)

        override suspend fun write(id: String, value: String) = this@toKtorSessionStorage.write(id, value)

    }