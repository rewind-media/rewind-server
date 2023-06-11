package io.media.rewind.worker

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import io.media.rewind.CacheConfig
import io.media.rewind.DatabaseConfig

data class WorkerConfig(val database: DatabaseConfig, val cache: CacheConfig) {
    companion object {
        fun fromConfig(config: Config = ConfigFactory.load()) =
            WorkerConfig(database = DatabaseConfig.fromConfig(config), cache = CacheConfig.fromConfig(config))

    }
}