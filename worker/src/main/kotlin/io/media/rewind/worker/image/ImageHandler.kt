package io.media.rewind.worker.image

import io.media.rewind.FileLocation
import io.media.rewind.ImageJobHandler
import io.media.rewind.cache.Cache
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

fun mkImageJobHandler(cache: Cache): ImageJobHandler = { context ->
    when (val location = context.request.fileInfo.location) {
        is FileLocation.LocalFile -> Path.of(location.path).toFile().readBytes()
    }.also { cache.putImage(context.request.imageId, it, (Clock.System.now() + 1.hours).toJavaInstant()) }
}