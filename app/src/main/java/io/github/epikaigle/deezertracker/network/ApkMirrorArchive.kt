package io.github.epikaigle.deezertracker.network

import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Source
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Best-effort APKMirror PUBLIC listing-page parser. There is no published,
 * authenticated older-version API that we can rely on. Never invent dates.
 * No Cloudflare bypass, private session, fake user agent or captcha solver.
 */
object ApkMirrorArchive {
    private const val LISTING_PATH =
        "/apk/deezer-music-player/deezer-music-podcast-player/"
    private val monthDate = Regex(
        """\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2},?\s+\d{4}\b""",
        RegexOption.IGNORE_CASE
    )
    private val dateFormats = listOf(
        DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
    )

    private fun dateFrom(text: String): Long? {
        VersionParsing.date(text)?.let { return it }
        val candidate = monthDate.find(text)?.value?.replace(Regex("""\s+"""), " ")?.trim()
            ?: return null
        val normalized = candidate.replace(Regex("""(\d)\s+(\d{4})$"""), "$1, $2")
        for (format in dateFormats) {
            val parsed = runCatching { LocalDate.parse(normalized, format) }.getOrNull()
            if (parsed != null) return parsed.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
        return null
    }

    private fun releaseLink(row: Element): String? {
        val anchors = row.select("h5.appRowTitle a[href], a[href*='-release/']")
        for (a in anchors) {
            val resolved = ApkMirrorApiParser.absoluteLink(a.attr("href")) ?: continue
            val path = runCatching { java.net.URI(resolved).path }.getOrNull() ?: continue
            if (path.startsWith(LISTING_PATH) &&
                path.trimEnd('/') != LISTING_PATH.trimEnd('/') &&
                path.contains("-release/")) return resolved
        }
        return null
    }

    private fun rowDate(row: Element, details: Element?): Long? {
        for (parent in listOfNotNull(row, details)) {
            for (node in parent.select("time[datetime], [itemprop=datePublished]")) {
                val date = node.attr("datetime").ifBlank { node.attr("content") }
                dateFrom(date)?.let { return it }
            }
        }
        val text = listOfNotNull(row.text(), details?.text()).joinToString(" ")
        return dateFrom(text)
    }

    /**
     * The application listing normally contains an appRow followed by an infoSlide.
     * Parse only Deezer release links under the canonical APKMirror listing.
     * The server may return a 403; this parser never treats an HTML challenge as data.
     */
    fun parse(html: String, limit: Int = 8): List<Release> {
        val doc = Jsoup.parse(html)
        val versions = mutableListOf<Release>()
        for (row in doc.select(".appRow")) {
            val link = releaseLink(row) ?: continue
            val next = row.nextElementSibling()
            val details = next?.takeIf { it.hasClass("infoSlide") ||
                it.select(".infoSlide").isNotEmpty() }
            val title = row.selectFirst("h5.appRowTitle")?.text().orEmpty()
            val extra = details?.text().orEmpty()
            val version = VersionParsing.extract(title)
                ?: VersionParsing.extract(extra)
                ?: continue
            val date = rowDate(row, details)
            versions.add(Release(Source.APKMIRROR, version, link,
                VersionParsing.isBeta("$title $extra"), date))
            if (versions.map { it.version }.distinct().size >= limit) break
        }
        return versions.distinctBy { it.version.lowercase() }.take(limit)
    }
}
