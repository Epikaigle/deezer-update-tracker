package io.github.epikaigle.deezertracker.model

enum class Source(val title: String, val website: String) {
    APKMIRROR("APKMirror", "https://www.apkmirror.com/apk/deezer-music-player/deezer-music-podcast-player/"),
    APKPURE("APKPure", "https://apkpure.net/deezer-people-android-music/deezer.android.app"),
    GOOGLE_PLAY("Google Play", "https://play.google.com/store/apps/details?id=deezer.android.app")
}

data class Release(
    val source: Source,
    val version: String,
    val url: String,
    val beta: Boolean,
    val publishedAt: Long?,
    val detectedAt: Long = System.currentTimeMillis(),
    val versionCode: Long? = null,
    /** File extension/type only when the provider explicitly exposes it. */
    val fileType: String? = null,
    val architectures: List<String> = emptyList(),
    val densities: List<String> = emptyList(),
    val minApi: Int? = null
) {
    // A single display version may have different variants/builds.
    val key: String get() = listOf(source.name, version.lowercase(), versionCode ?: "unknown",
        fileType.orEmpty(), architectures.joinToString(","), densities.joinToString(","),
        minApi ?: "?", url).joinToString(":")
}

data class SourceStatus(
    val source: Source,
    val ok: Boolean,
    val message: String,
    val checkedAt: Long
)

data class Snapshot(
    val releases: List<Release>,
    val statuses: List<SourceStatus>,
    val frequencyMinutes: Int,
    val includeBeta: Boolean,
    val enabledSources: Set<Source> = Source.entries.toSet()
)

data class SourceResult(
    val source: Source,
    val releases: List<Release> = emptyList(),
    val error: String? = null
)
