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
    val detectedAt: Long = System.currentTimeMillis()
) {
    val key: String get() = "${source.name}:${version.lowercase()}"
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
    val includeBeta: Boolean
)

data class SourceResult(
    val source: Source,
    val releases: List<Release> = emptyList(),
    val error: String? = null
)
