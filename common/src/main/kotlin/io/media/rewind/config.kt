package io.media.rewind

import com.typesafe.config.Config
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.lettuce.core.RedisURI
import javax.sql.DataSource


sealed interface DatabaseConfig {
    val url
        get() = when (this) {
            is PostgresConfig -> "jdbc:postgresql://$hostname:$port/$database"
        }

    val driver
        get() = when (this) {
            is PostgresConfig -> "org.postgresql.Driver"
        }

    data class PostgresConfig(
        val hostname: String,
        val username: String,
        val password: String,
        val port: Int,
        val database: String
    ) : DatabaseConfig {
        val datasource: DataSource
            get() {
                val config = HikariConfig()
                config.jdbcUrl = url
                config.username = username
                config.password = password
                return HikariDataSource(config)
            }

        companion object {
            fun fromConfig(config: Config) = with(config) {
                PostgresConfig(
                    hostname = config.getString("hostname"),
                    username = config.getString("username"),
                    password = config.getString("password"),
                    port = config.getInt("port"),
                    database = config.getString("database")
                )
            }
        }
    }

    companion object {
        fun fromConfig(config: Config) = with(config) {
            if (config.hasPath("postgres")) {
                PostgresConfig.fromConfig(config.getConfig("postgres"))
            } else {
                throw IllegalStateException("No database is configured")
            }
        }

    }
}


sealed interface CacheConfig {

    data class RedisConfig(
        val uri: RedisURI
    ) : CacheConfig {


        companion object {
            fun fromConfig(config: Config) = with(config) {
                RedisConfig(
                    RedisURI.create(config.getString("hostname"), config.getInt("port"))
                )
            }
        }
    }

    data class RedisClusterConfig(
        val uris: List<RedisURI>,
    ) : CacheConfig {


        companion object {
            fun fromConfig(config: Config) = with(config) {
                RedisClusterConfig(
                    uris = getString("hosts").split(",").mapNotNull {
                        val split = it.split(":")
                        if (split.isEmpty() || split.size > 2) {
                            log.warn { "Invalid host:port combination: $it" }
                            null
                        } else {
                            val port = split.getOrNull(1)?.toIntOrNull() ?: 6379
                            RedisURI.create(split[0], port)
                        }
                    }
                )
            }
        }
    }

    companion object : KLog() {
        fun fromConfig(config: Config) = with(config) {
            if (config.hasPath("redis")) {
                RedisConfig.fromConfig(config.getConfig("redis"))
            } else if (config.hasPath("redis-cluster")) {
                RedisClusterConfig.fromConfig(config.getConfig("redis-cluster"))
            } else {
                throw IllegalStateException("No database is configured")
            }
        }

    }
}
