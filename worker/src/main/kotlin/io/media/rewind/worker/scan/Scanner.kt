package io.media.rewind.worker.scan

interface Scanner {
    suspend fun scan()
}