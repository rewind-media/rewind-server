package io.media.rewind

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

data class ServerConfig(val database: DatabaseConfig, val cache: CacheConfig) {
    companion object {
        fun fromConfig(config: Config = ConfigFactory.load()) = ServerConfig(
            database = DatabaseConfig.fromConfig(config),
            cache = CacheConfig.fromConfig(config)
        )
    }
}