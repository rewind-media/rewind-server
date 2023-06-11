package io.media.rewind

import io.media.rewind.cache.queue.JobId
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(val id: String, val username: String)
