package io.media.rewind.database

import com.media.rewind.model.*
import io.media.rewind.*
import io.media.rewind.DatabaseConfig
import io.media.rewind.database.PostgresExtensions.upsert
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.math.roundToInt
import org.jetbrains.exposed.sql.Database as ExposedDb

interface SessionStorage {
    suspend fun invalidate(id: String)
    suspend fun write(id: String, value: String)
    suspend fun read(id: String): String
}

sealed interface Database {
    suspend fun init()

    suspend fun getUser(username: String): ServerUser?
    suspend fun upsertUser(user: ServerUser): Boolean
    suspend fun deleteUser(username: String): Boolean
    suspend fun listUsers(): List<ServerUser>

    suspend fun getLibrary(libraryId: String): Library?
    suspend fun upsertLibrary(user: Library): Boolean
    suspend fun deleteLibrary(libraryId: String): Boolean
    suspend fun listLibraries(): List<Library>

    suspend fun getShow(showId: String): ServerShowInfo?
    suspend fun upsertShow(show: ServerShowInfo): Boolean
    suspend fun deleteShow(showId: String): Boolean
    suspend fun listShows(libraryId: String): List<ServerShowInfo>

    suspend fun getSeason(seasonId: String): ServerSeasonInfo?
    suspend fun upsertSeason(season: ServerSeasonInfo): Boolean
    suspend fun deleteSeason(seasonId: String): Boolean
    suspend fun listSeasons(showId: String): List<ServerSeasonInfo>

    suspend fun getEpisode(episodeId: String): ServerEpisodeInfo?
    suspend fun upsertEpisode(episode: ServerEpisodeInfo): Boolean
    suspend fun deleteEpisode(episodeId: String): Boolean
    suspend fun listEpisodes(seasonId: String): List<ServerEpisodeInfo>

    suspend fun getMovie(movieId: String): ServerMovieInfo?
    suspend fun upsertMovie(movieInfo: ServerMovieInfo): Boolean
    suspend fun deleteMovie(movieId: String): Boolean
    suspend fun listMovies(libraryId: String): List<ServerMovieInfo>


    suspend fun getImage(imageId: String): ServerImageInfo?
    suspend fun upsertImage(imageInfo: ServerImageInfo): Boolean
    suspend fun deleteImage(imageId: String): Boolean
    suspend fun listImages(libraryId: String): List<ServerImageInfo>

    suspend fun mkSessionStorage(): SessionStorage
    suspend fun cleanShows(start: Instant, libraryId: String): Int
    suspend fun cleanSeasons(start: Instant, libraryId: String): Int
    suspend fun cleanEpisodes(start: Instant, libraryId: String): Int
    suspend fun cleanImages(start: Instant, libraryId: String): Int

    suspend fun getLibraryIndex(libraryId: String, updatedAfter: Instant? = null): LibraryIndex?
    suspend fun upsertLibraryIndex(index: LibraryIndex): Boolean
    suspend fun deleteLibraryIndex(libraryId: String): Boolean

    suspend fun listLibraryIndexes(): List<LibraryIndex>

    suspend fun getProgress(id: String, username: String): UserProgress?
    suspend fun upsertProgress(progress: UserProgress): Boolean
    suspend fun deleteProgress(id: String, username: String): Boolean

    suspend fun listProgress(
        username: String,
        sortOrder: ProgressSort,
        limit: Int,
        minPercent: Double = 0.0,
        maxPercent: Double = 1.0
    ): List<UserProgress>


    class PostgresDatabase(
        config: DatabaseConfig.PostgresConfig, private val conn: ExposedDb = ExposedDb.connect(config.datasource)
    ) : Database {
        override suspend fun init() {
            transaction(conn) {
                SchemaUtils.createMissingTablesAndColumns(
                    Users,
                    Sessions,
                    Libraries,
                    Shows,
                    Seasons,
                    Episodes,
                    Progression,
                    LibraryIndicies
                )

                if (Users.selectAll().firstOrNull() == null) {
                    val username = "rewind-${UUID.randomUUID()}"
                    val salt = generateSalt()
                    val password = UUID.randomUUID().toString()
                    val hashedPass = hashPassword(password, salt)
                    runBlocking {
                        upsertUser(
                            ServerUser(
                                User(
                                    username, UserPermissions(isAdmin = true), UserPreferences(false)
                                ), hashedPass, salt
                            )
                        )
                    }
                    log.info { "Created user '${username}' with password '${password}'" }
                }
            }
        }

        override suspend fun getUser(username: String): ServerUser? = transaction(conn) {
            Users.select { Users.username eq username }.limit(1).firstOrNull()?.toServerUser()
        }


        override suspend fun upsertUser(user: ServerUser): Boolean = transaction(conn) {
            Users.upsert(Users.username) {
                it[username] = user.user.username
                it[hashedPassword] = user.hashedPass
                it[preferences] = Json.encodeToString(user.user.preferences)
                it[permissions] = Json.encodeToString(user.user.permissions)
                it[salt] = user.salt
            }.insertedCount == 1
        }

        override suspend fun deleteUser(username: String): Boolean = transaction(conn) {
            Users.deleteWhere {
                Users.username eq username
            } == 1
        }

        override suspend fun listUsers(): List<ServerUser> = transaction(conn) {
            Users.selectAll().map {
                it.toServerUser()
            }
        }

        override suspend fun getLibrary(libraryId: String): Library? = transaction(conn) {
            Libraries.select { Libraries.libraryId eq libraryId }.firstOrNull()?.let {
                Library(
                    name = it[Libraries.libraryId],
                    type = it[Libraries.type],
                    rootPaths = Json.decodeFromString<List<String>>(it[Libraries.rootPaths])
                )
            }
        }

        override suspend fun upsertLibrary(lib: Library): Boolean = transaction(conn) {
            Libraries.upsert(Libraries.libraryId) {
                it[libraryId] = lib.name
                it[type] = lib.type
                it[rootPaths] = Json.encodeToString(lib.rootPaths)
            }.insertedCount == 1
        }

        override suspend fun deleteLibrary(libraryId: String): Boolean = transaction(conn) {
            Libraries.deleteWhere {
                Libraries.libraryId eq libraryId
            } == 1
        }

        override suspend fun listLibraries(): List<Library> = transaction(conn) {
            Libraries.selectAll().map {
                Library(
                    name = it[Libraries.libraryId],
                    type = it[Libraries.type],
                    rootPaths = Json.decodeFromString<List<String>>(it[Libraries.rootPaths])
                )
            }
        }

        override suspend fun getShow(showId: String): ServerShowInfo? = transaction(conn) {
            Shows.select { Shows.showId eq showId }.firstOrNull()?.toServerShowInfo()
        }

        override suspend fun upsertShow(show: ServerShowInfo): Boolean = transaction(conn) {
            Shows.upsert(Shows.showId) {
                it[showId] = show.showInfo.id
                it[libraryId] = show.showInfo.libraryId
                it[title] = show.showInfo.title
                it[plot] = show.showInfo.plot
                it[outline] = show.showInfo.outline
                it[originalTitle] = show.showInfo.originalTitle
                it[premiered] = show.showInfo.premiered
                it[releaseDate] = show.showInfo.releaseDate
                it[endDate] = show.showInfo.endDate
                it[mpaa] = show.showInfo.mpaa
                it[imdbId] = show.showInfo.imdbId
                it[tmdbId] = show.showInfo.tmdbId
                it[tvdbId] = show.showInfo.tvdbId
                it[tvRageId] = show.showInfo.tvRageId
                it[rating] = show.showInfo.rating
                it[year] = show.showInfo.year?.roundToInt()
                it[runTime] = show.showInfo.runTime
                it[episode] = show.showInfo.episode?.roundToInt()
                it[episodeNumberEnd] = show.showInfo.episodeNumberEnd?.roundToInt()
                it[season] = show.showInfo.season?.roundToInt()
                it[aired] = show.showInfo.aired
                it[genre] = show.showInfo.genre
                it[studio] = show.showInfo.studio
                it[status] = show.showInfo.status
                it[tag] = show.showInfo.tag?.let(Json.Default::encodeToString)
                it[actors] = show.showInfo.actors?.let(Json.Default::encodeToString)
                it[seriesImageId] = show.showInfo.seriesImageId
                it[backdropImageId] = show.showInfo.backdropImageId
                it[lastUpdated] = show.libraryData.lastUpdated.toEpochMilliseconds()
            }.insertedCount == 1
        }

        override suspend fun deleteShow(showId: String): Boolean = transaction(conn) {
            Shows.deleteWhere { Shows.showId eq showId } == 1
        }

        override suspend fun listShows(libraryId: String): List<ServerShowInfo> = transaction(conn) {
            Shows.selectAll().map { it.toServerShowInfo() }
        }

        override suspend fun getSeason(seasonId: String): ServerSeasonInfo? = transaction(conn) {
            Seasons.select {
                Seasons.seasonId eq seasonId
            }.firstOrNull()?.toServerSeasonInfo()
        }

        override suspend fun upsertSeason(season: ServerSeasonInfo): Boolean = transaction(conn) {
            Seasons.upsert(Seasons.seasonId) {
                it[seasonId] = season.seasonInfo.id
                it[showId] = season.seasonInfo.showId
                it[seasonNumber] = season.seasonInfo.seasonNumber.roundToInt()
                it[libraryId] = season.libraryData.libraryId
                it[showName] = season.seasonInfo.showName ?: "" // TODO should not be nullable in spec
                it[year] = season.seasonInfo.year?.roundToInt()
                it[premiered] = season.seasonInfo.premiered
                it[releaseDate] = season.seasonInfo.releaseDate
                it[folderImageId] = season.seasonInfo.folderImageId
                it[actors] = season.seasonInfo.actors?.let(Json.Default::encodeToString)
                it[libraryId] = season.libraryData.libraryId
                it[lastUpdated] = season.libraryData.lastUpdated.toEpochMilliseconds()
            }.insertedCount == 1
        }

        override suspend fun deleteSeason(seasonId: String): Boolean = transaction(conn) {
            Seasons.deleteWhere { Seasons.seasonId eq seasonId } == 1
        }

        override suspend fun listSeasons(showId: String): List<ServerSeasonInfo> = transaction(conn) {
            Seasons.select { Seasons.showId eq showId }.map { it.toServerSeasonInfo() }
        }

        override suspend fun getEpisode(episodeId: String): ServerEpisodeInfo? = transaction(conn) {
            Episodes.select { Episodes.episodeId eq episodeId }.firstOrNull()?.toServerEpisodeInfo()
        }

        override suspend fun upsertEpisode(episode: ServerEpisodeInfo): Boolean = transaction(conn) {
            Episodes.upsert(Episodes.episodeId) {
                it[showId] = episode.episodeInfo.showId
                it[showName] = episode.episodeInfo.showName ?: "" // TODO should not be nullable in spec
                it[seasonId] = episode.episodeInfo.seasonId
                it[season] = episode.episodeInfo.season?.roundToInt() ?: 0 // TODO should not be nullable in spec
                it[episodeId] = episode.episodeInfo.id
                it[location] = episode.mediaInfo.fileInfo.location.let(Json.Default::encodeToString)
                it[size] = episode.mediaInfo.fileInfo.size
                it[lastUpdated] = episode.mediaInfo.libraryData.lastUpdated.toEpochMilliseconds()
                it[libraryId] = episode.mediaInfo.libraryData.libraryId
                it[audioTracks] = episode.mediaInfo.audioTracks.let(Json.Default::encodeToString)
                it[videoTracks] = episode.mediaInfo.videoTracks.let(Json.Default::encodeToString)
                it[subtitleTracks] = episode.mediaInfo.subtitleTracks.let(Json.Default::encodeToString)
                it[subtitleFiles] = episode.mediaInfo.subtitleFiles.let(Json.Default::encodeToString)
                it[title] = episode.episodeInfo.title
                it[runTime] = episode.episodeInfo.runTime
                it[plot] = episode.episodeInfo.plot
                it[outline] = episode.episodeInfo.outline
                it[directors] = episode.episodeInfo.director?.let(Json.Default::encodeToString)
                it[writers] = episode.episodeInfo.writer?.let(Json.Default::encodeToString)
                it[credits] = episode.episodeInfo.credits?.let(Json.Default::encodeToString)
                it[rating] = episode.episodeInfo.rating
                it[year] = episode.episodeInfo.year?.roundToInt()
                it[Episodes.episode] =
                    episode.episodeInfo.episode?.roundToInt() ?: 0 // TODO should not be nullable in spec
                it[episodeNumberEnd] = episode.episodeInfo.episodeNumberEnd?.roundToInt()
                it[season] = episode.episodeInfo.season?.roundToInt() ?: 0 // TODO should not be nullable in spec
                it[aired] = episode.episodeInfo.aired
                it[episodeImageId] = episode.episodeInfo.episodeImageId
            }.insertedCount == 1
        }

        override suspend fun deleteEpisode(episodeId: String): Boolean = transaction(conn) {
            Episodes.deleteWhere {
                Episodes.episodeId eq episodeId
            } == 1
        }

        override suspend fun listEpisodes(seasonId: String): List<ServerEpisodeInfo> = transaction(conn) {
            Episodes.select { Episodes.seasonId eq seasonId }.map { it.toServerEpisodeInfo() }
        }

        override suspend fun getMovie(movieId: String): ServerMovieInfo? {
            TODO("Not yet implemented")
        }

        override suspend fun upsertMovie(user: ServerMovieInfo): Boolean {
            TODO("Not yet implemented")
        }

        override suspend fun deleteMovie(movieId: String): Boolean {
            TODO("Not yet implemented")
        }

        override suspend fun listMovies(libraryId: String): List<ServerMovieInfo> {
            TODO("Not yet implemented")
        }

        override suspend fun getImage(imageId: String): ServerImageInfo? = transaction(conn) {
            Images.select {
                Images.imageId eq imageId
            }.firstOrNull()?.toServerImageInfo()
        }


        override suspend fun upsertImage(imageInfo: ServerImageInfo): Boolean = transaction(conn) {
            Images.upsert(Images.imageId) {
                it[imageId] = imageInfo.imageId
                it[size] = imageInfo.fileInfo.size
                it[lastUpdated] = imageInfo.libraryData.lastUpdated.toEpochMilliseconds()
                it[libraryId] = imageInfo.libraryData.libraryId
                it[location] = imageInfo.fileInfo.location.let(Json.Default::encodeToString)
            }.insertedCount == 1

        }

        override suspend fun deleteImage(movieId: String): Boolean {
            TODO("Not yet implemented")
        }

        override suspend fun listImages(libraryId: String): List<ServerImageInfo> {
            TODO("Not yet implemented")
        }


        override suspend fun mkSessionStorage(): SessionStorage {
            return object : SessionStorage {
                override suspend fun invalidate(id: String) = transaction(conn) {
                    Sessions.deleteWhere {
                        Sessions.sessionId eq id
                    }
                    Unit
                }

                override suspend fun write(id: String, value: String) = transaction(conn) {
                    Sessions.upsert(Sessions.sessionId) {
                        it[Sessions.sessionId] = id
                        it[Sessions.value] = value
                    }
                    Unit
                }

                override suspend fun read(id: String): String = transaction(conn) {
                    Sessions.select {
                        Sessions.sessionId eq id
                    }.firstOrNull()?.getOrNull(Sessions.value) ?: throw NoSuchElementException("Session $id not found")
                }

            }
        }

        override suspend fun cleanShows(start: Instant, libraryId: String) = transaction(conn) {
            Shows.deleteWhere { lastUpdated less start.toEpochMilliseconds() and (this.libraryId eq libraryId) }
        }

        override suspend fun cleanSeasons(start: Instant, libraryId: String) = transaction(conn) {
            Seasons.deleteWhere { lastUpdated less start.toEpochMilliseconds() and (this.libraryId eq libraryId) }
        }

        override suspend fun cleanEpisodes(start: Instant, libraryId: String) = transaction(conn) {
            Episodes.deleteWhere { lastUpdated less start.toEpochMilliseconds() and (this.libraryId eq libraryId) }
        }

        override suspend fun cleanImages(start: Instant, libraryId: String) = transaction(conn) {
            Images.deleteWhere { lastUpdated less start.toEpochMilliseconds() and (this.libraryId eq libraryId) }
        }

        override suspend fun getLibraryIndex(libraryId: String, updatedAfter: Instant?): LibraryIndex? =
            transaction(conn) {
                LibraryIndicies.select {
                    if (updatedAfter != null) {
                        LibraryIndicies.libraryId eq libraryId and (LibraryIndicies.lastUpdated greater updatedAfter.toEpochMilliseconds())
                    } else {
                        LibraryIndicies.libraryId eq libraryId
                    }
                }.firstOrNull()
            }?.toLibraryIndex()

        override suspend fun upsertLibraryIndex(index: LibraryIndex): Boolean = transaction(conn) {
            LibraryIndicies.upsert(LibraryIndicies.libraryId) {
                it[libraryId] = index.libraryId
                it[lastUpdated] = index.lastUpdated.toEpochMilliseconds()
                it[LibraryIndicies.index] = ExposedBlob(index.index)
            }
        }.insertedCount == 1

        override suspend fun deleteLibraryIndex(libraryId: String): Boolean = transaction(conn) {
            LibraryIndicies.deleteWhere { LibraryIndicies.libraryId eq libraryId }
        } == 1

        override suspend fun listLibraryIndexes(): List<LibraryIndex> {
            TODO("Not yet implemented")
        }

        override suspend fun getProgress(id: String, username: String): UserProgress? = transaction(conn) {
            Progression.select {
                Progression.mediaId eq id
            }.firstOrNull()?.toProgress()
        }

        override suspend fun upsertProgress(progress: UserProgress): Boolean = transaction(conn) {
            Progression.upsert(Progression.mediaId) {
                it[mediaId] = progress.progress.id
                it[timestamp] = progress.progress.timestamp.toLong()
                it[username] = progress.username
                it[percent] = progress.progress.percent
            }.insertedCount == 1
        }

        override suspend fun deleteProgress(id: String, username: String): Boolean = transaction(conn) {
            Progression.deleteWhere {
                mediaId eq id
            } == 1
        }

        override suspend fun listProgress(
            username: String,
            sortOrder: ProgressSort,
            limit: Int,
            minPercent: Double,
            maxPercent: Double
        ): List<UserProgress> = transaction(conn) {
            Progression.select {
                Progression.percent.lessEq(maxPercent) and Progression.percent.greaterEq(minPercent)
            }.limit(limit).apply {
                when (sortOrder) {
                    ProgressSort.Latest -> {
                        orderBy(Progression.timestamp, SortOrder.ASC)
                    }
                }
            }.map {
                it.toProgress()
            }
        }
    }


    object Shows : IntIdTable() {

        val showId = text("show_id").uniqueIndex()

        val libraryId = text("library_id").references(Libraries.libraryId)
        val lastUpdated = long("lastUpdated")

        val title = text("title")
        val plot = text("plot").nullable()
        val outline = text("outline").nullable()
        val originalTitle = text("original_title").nullable()
        val premiered = text("premiered").nullable()
        val releaseDate = text("release_date").nullable()
        val endDate = text("end_date").nullable()
        val mpaa = text("mpaa").nullable()
        val imdbId = text("imdb_id").nullable()
        val tmdbId = text("tmdb_id").nullable()
        val tvdbId = text("tvdb_id").nullable()
        val tvRageId = text("tv_rage_id").nullable()
        val rating = double("rating").nullable()
        val year = integer("year").nullable()
        val runTime = double("run_time").nullable()
        val episode = integer("episode").nullable()
        val episodeNumberEnd = integer("episode_number_end").nullable()
        val season = integer("season").nullable()
        val aired = double("aired").nullable()
        val genre = text("genre").nullable()
        val studio = text("studio").nullable()
        val status = text("status").nullable()
        val tag = text("tag").nullable()
        val actors = text("actors").nullable()
        val seriesImageId = text("series_image_id").references(Images.imageId).nullable()
        val backdropImageId = text("backdrop_image_id").references(Images.imageId).nullable()
    }

    object Episodes : IntIdTable() {
        val showId = text("show_id").references(Shows.showId)
        val showName = text("show_name")
        val seasonId = text("season_id").references(Seasons.seasonId)
        val episodeId = text("episode_id").uniqueIndex()

        val location = text("location")
        val size = long("size")

        val lastUpdated = long("lastUpdated")
        val libraryId = text("library_id").references(Libraries.libraryId)

        val audioTracks = text("audio_tracks")
        val videoTracks = text("video_tracks")
        val subtitleTracks = text("subtitle_tracks")
        val subtitleFiles = text("subtitle_files")

        val title = text("title")
        val runTime = double("run_time")
        val plot = text("plot").nullable()
        val outline = text("outline").nullable()
        val directors = text("directors").nullable()
        val writers = text("writers").nullable()
        val credits = text("credits").nullable()
        val rating = double("rating").nullable()
        val year = integer("year").nullable()
        val episode = integer("episode")
        val episodeNumberEnd = integer("premiered").nullable()
        val season = integer("season")
        val aired = double("aired").nullable()
        val episodeImageId = text("episode_image_id").references(Images.imageId).nullable()
    }

    object Seasons : IntIdTable() {
        val seasonId = text("season_id").uniqueIndex()
        val seasonNumber = integer("season_number")
        val showId = text("show_id").references(Shows.showId)
        val showName = text("show_name")
        val libraryId = text("library_id").references(Libraries.libraryId)
        val lastUpdated = long("lastUpdated")
        val year = integer("year").nullable()
        val premiered = text("premiered").nullable()
        val releaseDate = text("release_date").nullable()
        val folderImageId = text("series_image_id").references(Images.imageId).nullable()
        val actors = text("actors").nullable()
    }

    object Images : IntIdTable() {
        val imageId = text("image_id").uniqueIndex()
        val libraryId = text("library_id").references(Libraries.libraryId)
        val lastUpdated = long("last_updated")
        val location = text("location")
        val size = long("size")
    }

    object Users : IntIdTable() {
        val username = text("username").uniqueIndex()
        val permissions = text("permissions")
        val preferences = text("preferences")
        val hashedPassword = text("hashed_password")
        val salt = text("salt")
    }

    object Sessions : IntIdTable() {
        val sessionId = text("session_id").uniqueIndex()
        val value = text("value")
    }

    object Libraries : IntIdTable() {
        val libraryId = text("session_id").uniqueIndex()
        val rootPaths = text("root_paths")
        val type = enumeration<LibraryType>("type")
    }

    object Progression : IntIdTable() {
        val username = text("username").references(Users.username)
        val mediaId = text("media_id").uniqueIndex()
        val percent = double("percent")
        val timestamp = long("timestamp")
    }

    object LibraryIndicies : IntIdTable() {
        val libraryId = text("library_id").uniqueIndex().references(Libraries.libraryId)
        val lastUpdated = long("last_updated")
        val index = blob("index")
    }

    companion object : KLog() {
        fun fromConfig(config: DatabaseConfig) = when (config) {
            is DatabaseConfig.PostgresConfig -> PostgresDatabase(config)
        }

        private fun ResultRow.toServerImageInfo() = ServerImageInfo(
            fileInfo = FileInfo(
                location = this[Images.location].let { Json.decodeFromString(it) },
                size = this[Images.size],
            ), libraryData = LibraryData(
                libraryId = this[Images.libraryId],
                lastUpdated = Instant.fromEpochMilliseconds(this[Images.lastUpdated]),
            ), imageId = this[Images.imageId]

        )

        private fun ResultRow.toServerShowInfo() = ServerShowInfo(
            showInfo = ShowInfo(
                id = this[Shows.showId],
                libraryId = this[Shows.libraryId],
                title = this[Shows.title],
                plot = this[Shows.plot],
                outline = this[Shows.outline],
                originalTitle = this[Shows.originalTitle],
                premiered = this[Shows.premiered],
                releaseDate = this[Shows.releaseDate],
                endDate = this[Shows.endDate],
                mpaa = this[Shows.mpaa],
                imdbId = this[Shows.imdbId],
                tmdbId = this[Shows.tmdbId],
                tvdbId = this[Shows.tvdbId],
                tvRageId = this[Shows.tvRageId],
                rating = this[Shows.rating],
                year = this[Shows.year]?.toDouble(),
                runTime = this[Shows.runTime],
                episode = this[Shows.episode]?.toDouble(),
                episodeNumberEnd = this[Shows.episodeNumberEnd]?.toDouble(),
                season = this[Shows.season]?.toDouble(),
                aired = this[Shows.aired]?.toDouble(),
                genre = this[Shows.genre],
                studio = this[Shows.studio],
                status = this[Shows.status],
                tag = this[Shows.tag]?.let { Json.decodeFromString(it) },
                actors = this[Shows.actors]?.let { Json.decodeFromString(it) },
                seriesImageId = this[Shows.seriesImageId],
                backdropImageId = this[Shows.backdropImageId]
            ), libraryData = LibraryData(
                libraryId = this[Shows.libraryId],
                lastUpdated = Instant.fromEpochMilliseconds(this[Shows.lastUpdated]),
            )
        )

        private fun ResultRow.toServerSeasonInfo(): ServerSeasonInfo = ServerSeasonInfo(
            seasonInfo = SeasonInfo(
                id = this[Seasons.seasonId],
                showId = this[Seasons.showId],
                seasonNumber = this[Seasons.seasonNumber].toDouble(),
                libraryId = this[Seasons.libraryId],
                showName = this[Seasons.showName],
                year = this[Seasons.year]?.toDouble(),
                premiered = this[Seasons.premiered],
                releaseDate = this[Seasons.releaseDate],
                folderImageId = this[Seasons.folderImageId],
                actors = this[Seasons.actors]?.let(Json.Default::decodeFromString)

            ), libraryData = LibraryData(
                libraryId = this[Seasons.libraryId],
                lastUpdated = Instant.fromEpochMilliseconds(this[Seasons.lastUpdated]),
            )
        )

        private fun ResultRow.toServerEpisodeInfo(): ServerEpisodeInfo = ServerEpisodeInfo(
            episodeInfo = EpisodeInfo(
                id = this[Episodes.episodeId],
                libraryId = this[Episodes.libraryId],
                audioTracks = this[Episodes.audioTracks].let {
                    Json.decodeFromString<Map<String, ServerAudioTrack>>(it).toAudioTracks()
                },
                videoTracks = this[Episodes.videoTracks].let {
                    Json.decodeFromString<Map<String, ServerVideoTrack>>(it).toVideoTracks()
                },
                subtitleTracks = this[Episodes.subtitleTracks].let {
                    Json.decodeFromString<Map<String, ServerSubtitleTrack>>(it).toSubtitleTracks()
                },
                showId = this[Episodes.showId],
                seasonId = this[Episodes.seasonId],
                title = this[Episodes.title],
                runTime = this[Episodes.runTime],
                plot = this[Episodes.plot],
                outline = this[Episodes.outline],
                director = this[Episodes.directors]?.let(Json.Default::decodeFromString),
                writer = this[Episodes.writers]?.let(Json.Default::decodeFromString),
                credits = this[Episodes.credits]?.let(Json.Default::decodeFromString),
                rating = this[Episodes.rating],
                year = this[Episodes.year]?.toDouble(),
                episode = this[Episodes.episode].toDouble(),
                episodeNumberEnd = this[Episodes.episodeNumberEnd]?.toDouble(),
                season = this[Episodes.season].toDouble(),
                showName = this[Episodes.showName],
                aired = this[Episodes.aired]?.toDouble(),
                episodeImageId = this[Episodes.episodeImageId],
            ), mediaInfo = ServerMediaInfo(
                mediaInfo = MediaInfo(
                    id = this[Episodes.episodeId],
                    libraryId = this[Episodes.libraryId],
                    audioTracks = this[Episodes.audioTracks].let {
                        Json.decodeFromString<Map<String, ServerAudioTrack>>(it).toAudioTracks()
                    },
                    videoTracks = this[Episodes.videoTracks].let {
                        Json.decodeFromString<Map<String, ServerVideoTrack>>(it).toVideoTracks()
                    },
                    subtitleTracks = this[Episodes.subtitleTracks].let {
                        Json.decodeFromString<Map<String, ServerSubtitleTrack>>(it).toSubtitleTracks()
                    },
                    runTime = this[Episodes.runTime]
                ),
                libraryData = LibraryData(
                    libraryId = this[Episodes.libraryId],
                    lastUpdated = Instant.fromEpochMilliseconds(this[Episodes.lastUpdated]),
                ),
                fileInfo = FileInfo(
                    location = this[Episodes.location].let(Json.Default::decodeFromString), size = this[Episodes.size]
                ),
                subtitleFiles = this[Episodes.subtitleFiles].let(Json.Default::decodeFromString),
                audioTracks = this[Episodes.audioTracks].let {
                    Json.decodeFromString<Map<String, ServerAudioTrack>>(it)
                },
                videoTracks = this[Episodes.videoTracks].let {
                    Json.decodeFromString<Map<String, ServerVideoTrack>>(it)
                },
                subtitleTracks = this[Episodes.subtitleTracks].let {
                    Json.decodeFromString<Map<String, ServerSubtitleTrack>>(it)
                },
            )
        )

    }
}

private fun ResultRow.toLibraryIndex(): LibraryIndex = LibraryIndex(
    index = this[Database.LibraryIndicies.index].bytes,
    libraryId = this[Database.LibraryIndicies.libraryId],
    lastUpdated = Instant.fromEpochMilliseconds(this[Database.LibraryIndicies.lastUpdated])
)

private fun ResultRow.toProgress() = UserProgress(
    progress = Progress(
        id = this[Database.Progression.mediaId],
        percent = this[Database.Progression.percent],
        timestamp = this[Database.Progression.timestamp].toDouble()
    ), username = this[Database.Progression.username]
)

sealed interface ProgressSort {
    object Latest : ProgressSort
}


private fun ResultRow.toServerUser() = ServerUser(
    user = User(
        username = this[Database.Users.username],
        permissions = Json.decodeFromString(this[Database.Users.permissions]),
        preferences = Json.decodeFromString(this[Database.Users.preferences])
    ), hashedPass = this[Database.Users.hashedPassword], salt = this[Database.Users.salt]
)