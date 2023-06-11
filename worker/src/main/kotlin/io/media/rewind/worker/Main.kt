package io.media.rewind.worker

import io.media.rewind.cache.Cache
import io.media.rewind.database.Database
import io.media.rewind.getImageJobQueue
import io.media.rewind.getScanJobQueue
import io.media.rewind.getSearchJobQueue
import io.media.rewind.getStreamJobQueue
import io.media.rewind.worker.image.mkImageJobHandler
import io.media.rewind.worker.scan.mkScanJobHandler
import io.media.rewind.worker.search.SearchHandler
import io.media.rewind.worker.stream.mkStreamJobHandler
import kotlinx.coroutines.*
import mu.KotlinLogging
import kotlin.time.Duration.Companion.minutes

val config by lazy { WorkerConfig.fromConfig() }
val cache by lazy { Cache.fromConfig(config.cache) }

val log = KotlinLogging.logger { }
fun main() = runBlocking(Dispatchers.IO) {
    val db = Database.fromConfig(config.database)
    db.init()

    val searchHandler = SearchHandler(db)
    val searchUpdateJob = launch(Dispatchers.IO) {
        while (true) {
            searchHandler.updateIndicies()
            delay(1.minutes)
        }
    }

    val scanQueue = cache.getScanJobQueue()
    val streamQueue = cache.getStreamJobQueue()
    val imageQueue = cache.getImageJobQueue()
    val searchQueue = cache.getSearchJobQueue()
    val scanJobHandler = scanQueue.register(mkScanJobHandler(db), this)
    val streamJobHandler = streamQueue.register(mkStreamJobHandler(cache), this)
    val searchJobHandler = searchQueue.register(searchHandler.jobHander, this)
    val imageJobHandlers = (0 until 100).map { imageQueue.register(mkImageJobHandler(cache), this) }
    (listOf(
        scanJobHandler,
        streamJobHandler,
        searchJobHandler,
        searchUpdateJob
    ) + imageJobHandlers).joinAll()
}

