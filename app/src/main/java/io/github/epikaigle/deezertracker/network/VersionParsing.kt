package io.github.epikaigle.deezertracker.network

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object VersionParsing {
    // A numeric version (e.g. 8.0.21.4) with optional prerelease suffix.
    private val versionPattern = Regex(
        """(?<!\d)(\d+(?:\.\d+){2,4}(?:[-._ ]?(?:beta|alpha|rc)\d*)?)(?!\d)""",
        RegexOption.IGNORE_CASE
    )
    fun extract(text: String): String? = versionPattern.find(text)?.groupValues?.get(1)?.replace(" ", "-")

    fun isBeta(text: String): Boolean =
        Regex("""\b(beta|alpha|preview|canary|rc)(?:\b|\d)""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    /** Compare numeric Android version names without naive alphabetical ordering. */
    fun compare(a: String, b: String): Int {
        fun numbers(v: String): List<Int> =
            v.takeWhile { it.isDigit() || it == '.' }.trimEnd('.')
                .split('.').map { it.toIntOrNull() ?: 0 }
        val av = numbers(a)
        val bv = numbers(b)
        for (i in 0 until maxOf(av.size, bv.size)) {
            val diff = av.getOrElse(i) { 0 }.compareTo(bv.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        fun stage(v: String): Int = when {
            v.contains("alpha", ignoreCase = true) -> 0
            v.contains("beta", ignoreCase = true) -> 1
            v.contains("rc", ignoreCase = true) -> 2
            else -> 3
        }
        return stage(a).compareTo(stage(b))
    }

    fun date(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
            .recoverCatching { java.time.Instant.parse(value).toEpochMilli() }
            .recoverCatching { java.time.OffsetDateTime.parse(value.replace(" ", "T")).toInstant().toEpochMilli() }
            .recoverCatching { LocalDateTime.parse(value.replace(" ", "T")).toInstant(ZoneOffset.UTC).toEpochMilli() }
            .recoverCatching { LocalDate.parse(value.take(10)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
            .getOrNull()
    }
}
