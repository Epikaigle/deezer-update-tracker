package io.github.epikaigle.deezertracker.network

import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.model.SourceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Public pages/feeds only. Never borrow private API credentials or bypass CAPTCHA. */
class SourceClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val builder = Request.Builder().url(url)
                .header("User-Agent", "DeezerUpdateTracker/0.1 (+https://github.com/Epikaigle/deezer-update-tracker)")
                .header("Accept", "application/json, application/rss+xml, text/xml, text/html")
            headers.forEach { (key, value) -> builder.header(key, value) }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} — accès indisponible")
                response.body?.string() ?: throw IOException("Réponse vide")
            }
        }
}

interface VersionSource {
    val source: Source
    suspend fun fetch(client: SourceClient): List<Release>
}

class ApkMirrorFeed : VersionSource {
    override val source = Source.APKMIRROR
    override suspend fun fetch(client: SourceClient): List<Release> {
        // Based on Obtainium's public-RSS fallback (no APKMirror private API token).
        val xml = client.get("${source.website}feed/")
        val doc = Jsoup.parse(xml, "", Parser.xmlParser())
        val releases = doc.select("item").take(30).mapNotNull { item ->
            val title = item.selectFirst("title")?.text().orEmpty()
            val version = VersionParsing.extract(title) ?: return@mapNotNull null
            val url = item.selectFirst("link")?.text().orEmpty().ifBlank { source.website }
            if (!url.startsWith("https://www.apkmirror.com/") && !url.startsWith("https://apkmirror.com/")) {
                return@mapNotNull null
            }
            Release(source, version, url, VersionParsing.isBeta(title + " " + version),
                VersionParsing.date(item.selectFirst("pubDate")?.text()))
        }.distinctBy { it.version.lowercase() }
        if (releases.isEmpty()) throw IOException("Flux RSS vide, modifié ou protégé par CAPTCHA")
        return releases
    }
}

class ApkPureHistory : VersionSource {
    override val source = Source.APKPURE
    override suspend fun fetch(client: SourceClient): List<Release> {
        // Unofficial public-facing endpoint also documented in Obtainium's source.
        val raw = client.get(
            "https://tapi.pureapk.com/v3/get_app_his_version?package_name=deezer.android.app&hl=en",
            mapOf(
                "Ual-Access-Businessid" to "projecta",
                "Ual-Access-ProjectA" to """{"device_info":{"os_ver":"35"}}"""
            )
        )
        val array = JSONObject(raw).optJSONArray("version_list")
            ?: throw IOException("API APKPure modifiée ou inaccessible")
        val entries = mutableListOf<Release>()
        for (i in 0 until minOf(array.length(), 100)) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("package_name") != "deezer.android.app") continue
            val version = item.optString("version_name")
            if (version.isBlank()) continue
            val publishedAt = VersionParsing.date(item.optString("update_date"))
            val channel = VersionParsing.isBeta(version + " " + item.optString("title"))
            entries.add(Release(source, version, source.website, channel, publishedAt))
        }
        if (entries.isEmpty()) throw IOException("Aucun historique Deezer exploitable sur APKPure")
        return entries.distinctBy { it.version.lowercase() }
    }
}

class GooglePlayPage : VersionSource {
    override val source = Source.GOOGLE_PLAY
    override suspend fun fetch(client: SourceClient): List<Release> {
        // Public Play HTML does not consistently expose a version; report unavailable rather than invent one.
        val html = client.get("${source.website}&hl=en&gl=US")
        val doc = Jsoup.parse(html)
        val version = doc.selectFirst("meta[itemprop=softwareVersion]")?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: Regex(""""softwareVersion"\s*:\s*"([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: throw IOException("Google Play n'affiche pas le numéro de version publiquement")
        val parsed = VersionParsing.extract(version)
            ?: throw IOException("Version Play non exploitable")
        return listOf(Release(source, parsed, source.website, VersionParsing.isBeta(version), null))
    }
}

suspend fun readAllSources(): List<SourceResult> = coroutineScope {
    val client = SourceClient()
    listOf(ApkMirrorFeed(), ApkPureHistory(), GooglePlayPage()).map { provider ->
        async(Dispatchers.IO) {
            try {
                SourceResult(provider.source, provider.fetch(client))
            } catch (exception: Exception) {
                SourceResult(provider.source, error = exception.message ?: "Erreur de connexion")
            }
        }
    }.awaitAll()
}
