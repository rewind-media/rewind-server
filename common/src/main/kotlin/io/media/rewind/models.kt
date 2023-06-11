package io.media.rewind

import com.media.rewind.model.*
import io.media.rewind.cache.Cache
import io.media.rewind.cache.queue.JobHandler
import io.media.rewind.cache.queue.JobId
import io.media.rewind.cache.queue.JobQueue
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.serializers.InstantComponentSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration

typealias SerializableInstant = @Serializable(InstantComponentSerializer::class) Instant


@Serializable
data class SearchProps(val text: String)

@Serializable
data class LibraryData(val libraryId: String, val lastUpdated: SerializableInstant = Clock.System.now())

@Serializable
sealed interface FileLocation {
    @Serializable
    data class LocalFile(val path: String) : FileLocation
}

@Serializable
data class FileInfo(val location: FileLocation, val size: Long)

@Serializable
data class ServerUser(val user: User, val hashedPass: String, val salt: String)

@Serializable
data class ServerMediaInfo(
    val mediaInfo: MediaInfo,
    val libraryData: LibraryData,
    val fileInfo: FileInfo,
    val videoTracks: Map<String, ServerVideoTrack>,
    val audioTracks: Map<String, ServerAudioTrack>,
    val subtitleTracks: Map<String, ServerSubtitleTrack>,
    val subtitleFiles: Map<String, FileLocation>
)

@Serializable
data class ServerVideoTrack(
    val id: String? = null,
    val index: Int,
    val codecName: String? = null,
    val codecLongName: String? = null,
    val profile: String? = null,
    val codecType: String? = null,
    val codecTagString: String? = null,
    val codecTag: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val codedWidth: Int? = null,
    val codedHeight: Int? = null,
    val closedCaptions: Int? = null,
    val filmGrain: Int? = null,
    val hasBFrames: Int? = null,
    val sampleAspectRatio: String? = null,
    val displayAspectRatio: String? = null,
    val pixFmt: String? = null,
    val level: Int? = null,
    val colorRange: String? = null,
    val chromaLocation: String? = null,
    val refs: Int? = null,
    val rFrameRate: String? = null,
    val avgFrameRate: String? = null,
    val timeBase: String? = null,
    val startPts: Int? = null,
    val duration: Double? = null,
    val durationTs: Double? = null,
    val startTime: Double? = null,
    val extradataSize: Long? = null,
    val default: Int? = null,
    val dub: Int? = null,
    val original: Int? = null,
    val comment: Int? = null,
    val lyrics: Int? = null,
    val karaoke: Int? = null,
    val forced: Int? = null,
    val hearingImpaired: Int? = null,
    val visualImpaired: Int? = null,
    val cleanEffects: Int? = null,
    val attachedPic: Int? = null,
    val timedThumbnails: Int? = null,
    val captions: Int? = null,
    val descriptions: Int? = null,
    val metadata: Int? = null,
    val dependent: Int? = null,
    val stillImage: Int? = null,
    val creationTime: String? = null,
    val language: String? = null,
    val encoder: String? = null
) {
    fun toVideoTrack() = VideoTrack(
        index = index,
        name = "$index - $codecName - $language",
        id = id,
        duration = duration,
        codecName = codecName,
        codecLongName = codecLongName,
        profile = profile
    )
}

@Serializable
data class ServerSubtitleTrack(
    val id: String? = null,
    val index: Int,
    val codecName: String? = null,
    val codecLongName: String? = null,
    val profile: String? = null,
    val codecType: String? = null,
    val codecTagString: String? = null,
    val codecTag: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val codedWidth: Int? = null,
    val codedHeight: Int? = null,
    val closedCaptions: Int? = null,
    val filmGrain: Int? = null,
    val hasBFrames: Int? = null,
    val sampleAspectRatio: String? = null,
    val displayAspectRatio: String? = null,
    val pixFmt: String? = null,
    val level: Int? = null,
    val colorRange: String? = null,
    val chromaLocation: String? = null,
    val refs: Int? = null,
    val rFrameRate: String? = null,
    val avgFrameRate: String? = null,
    val timeBase: String? = null,
    val startPts: Int? = null,
    val duration: Double? = null,
    val durationTs: Double? = null,
    val startTime: Double? = null,
    val extradataSize: Long? = null,
    val default: Int? = null,
    val dub: Int? = null,
    val original: Int? = null,
    val comment: Int? = null,
    val lyrics: Int? = null,
    val karaoke: Int? = null,
    val forced: Int? = null,
    val hearingImpaired: Int? = null,
    val visualImpaired: Int? = null,
    val cleanEffects: Int? = null,
    val attachedPic: Int? = null,
    val timedThumbnails: Int? = null,
    val captions: Int? = null,
    val descriptions: Int? = null,
    val metadata: Int? = null,
    val dependent: Int? = null,
    val stillImage: Int? = null,
    val creationTime: String? = null,
    val language: String? = null,
    val encoder: String? = null
) {
    fun toSubtitleTrack() = SubtitleTrack(
        index = index,
        name = "$index - $codecName - $language",
        id = id,
        duration = duration,
        codecName = codecName,
        codecLongName = codecLongName,
        profile = profile
    )
}

@Serializable
data class ServerAudioTrack(
    val id: String? = null,
    val index: Int,
    val codecName: String? = null,
    val codecLongName: String? = null,
    val profile: String? = null,
    val codecType: String? = null,
    val codecTagString: String? = null,
    val codecTag: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val codedWidth: Int? = null,
    val codedHeight: Int? = null,
    val closedCaptions: Int? = null,
    val filmGrain: Int? = null,
    val hasBFrames: Int? = null,
    val sampleAspectRatio: String? = null,
    val displayAspectRatio: String? = null,
    val pixFmt: String? = null,
    val level: Int? = null,
    val colorRange: String? = null,
    val chromaLocation: String? = null,
    val refs: Int? = null,
    val rFrameRate: String? = null,
    val avgFrameRate: String? = null,
    val timeBase: String? = null,
    val startPts: Int? = null,
    val duration: Double? = null,
    val durationTs: Double? = null,
    val startTime: Double? = null,
    val extradataSize: Long? = null,
    val default: Int? = null,
    val dub: Int? = null,
    val original: Int? = null,
    val comment: Int? = null,
    val lyrics: Int? = null,
    val karaoke: Int? = null,
    val forced: Int? = null,
    val hearingImpaired: Int? = null,
    val visualImpaired: Int? = null,
    val cleanEffects: Int? = null,
    val attachedPic: Int? = null,
    val timedThumbnails: Int? = null,
    val captions: Int? = null,
    val descriptions: Int? = null,
    val metadata: Int? = null,
    val dependent: Int? = null,
    val stillImage: Int? = null,
    val creationTime: String? = null,
    val language: String? = null,
    val encoder: String? = null
) {
    fun toAudioTrack() = AudioTrack(
        index = index,
        name = "$index - $codecName - $language",
        id = id,
        duration = duration,
        codecName = codecName,
        codecLongName = codecLongName,
        profile = profile
    )
}


@Serializable
data class UserProgress(
    val username: String,
    val progress: Progress
)

@Serializable
data class LibraryIndex(
    val index: ByteArray,
    val libraryId: String,
    val lastUpdated: SerializableInstant
)

@Serializable
data class ServerShowInfo(val showInfo: ShowInfo, val libraryData: LibraryData)

@Serializable
data class ServerSeasonInfo(val seasonInfo: SeasonInfo, val libraryData: LibraryData)

@Serializable
data class ServerEpisodeInfo(val episodeInfo: EpisodeInfo, val mediaInfo: ServerMediaInfo)

@Serializable
data class ServerMovieInfo(val movieInfo: MovieInfo, val mediaInfo: ServerMediaInfo)

@Serializable
data class ServerImageInfo(val fileInfo: FileInfo, val libraryData: LibraryData, val imageId: String)

@Serializable
data class StreamProps(
    val id: String,
    val mediaInfo: ServerMediaInfo,
    val audioStreamName: String?,
    val videoStreamName: String?,
    val subtitleStreamName: String?,
    val startOffset: Duration = Duration.ZERO
)

@Serializable
sealed interface ClientStreamEvents {
    @Serializable
    object Heartbeat : ClientStreamEvents
}

@Serializable
sealed interface WorkerStreamEvents {
    @Serializable
    object Start : WorkerStreamEvents

    @Serializable
    data class Progress(val status: Map<String, String>) : WorkerStreamEvents
}

typealias SearchJobHandler = JobHandler<SearchProps, SearchResponse, Unit, Unit>
typealias SearchJobQueue = JobQueue<SearchProps, SearchResponse, Unit, Unit>

typealias ScanJobHandler = JobHandler<Library, Unit, Unit, Unit>
typealias ScanJobQueue = JobQueue<Library, Unit, Unit, Unit>

typealias StreamJobHandler = JobHandler<StreamProps, Unit, ClientStreamEvents, WorkerStreamEvents>
typealias StreamJobQueue = JobQueue<StreamProps, Unit, ClientStreamEvents, WorkerStreamEvents>

typealias ImageJobHandler = JobHandler<ServerImageInfo, ByteArray, Unit, Unit>
typealias ImageJobQueue = JobQueue<ServerImageInfo, ByteArray, Unit, Unit>

fun Cache.getSearchJobQueue(): SearchJobQueue = getJobQueue(
    "SearchJobQueue",
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
)

fun Cache.getStreamJobQueue(): StreamJobQueue = getJobQueue(
    "StreamJobQueue",
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
)

fun Cache.getScanJobQueue(): ScanJobQueue = getJobQueue(
    "ScanJobQueue",
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
)

fun Cache.getImageJobQueue(): ImageJobQueue = getJobQueue(
    "ImageJobQueue",
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.encodeToString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
    { Json.decodeFromString(it) },
)

@Serializable
data class StreamMetadataWrapper(val streamMetadata: StreamMetadata?)

@Serializable
data class StreamSegmentMetadata(
    val index: Int,
    val duration: Duration
)

@Serializable
data class StreamMetadata(
    val streamProps: StreamProps,
    val segments: List<StreamSegmentMetadata>,
    val subtitles: SubtitleMetadata?,
    val mime: Mime,
    val complete: Boolean,
    val processed: Duration,
    val jobId: JobId
)

@Serializable
data class SubtitleMetadata(
    val segments: List<SubtitleSegment>,
    val complete: Boolean
)

@Serializable
data class SubtitleSegment(
    val duration: Duration,
    val content: String
)

@Serializable
data class Mime(
    val mimeType: String,
    val codecs: List<String> // RFC 6381
)

@Serializable
data class StreamMapping(val streamId: String, val jobId: JobId)