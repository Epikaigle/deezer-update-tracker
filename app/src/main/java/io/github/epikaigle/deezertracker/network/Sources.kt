package io.github.epikaigle.deezertracker.network

import android.content.Context
import com.aurora.gplayapi.data.models.AuthData
import com.aurora.gplayapi.helpers.AppDetailsHelper
import com.google.gson.Gson
import io.github.epikaigle.deezertracker.network.play.NativeDeviceInfoProvider
import io.github.epikaigle.deezertracker.network.play.PlayHttpClient
import okhttp3.Cache
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
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

    suspend fun postJson(url: String, json: String, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(url)
                .header("User-Agent", "DeezerUpdateTracker/0.1 (+https://github.com/Epikaigle/deezer-update-tracker)")
                .header("Accept", "application/json")
                .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))
            headers.forEach { (name, value) -> request.header(name, value) }
            client.newCall(request.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} — accès API APKMirror indisponible")
                response.body?.string() ?: throw IOException("Réponse API APKMirror vide")
            }
        }

}

interface VersionSource {
    val source: Source
    suspend fun fetch(client: SourceClient): List<Release>
}


/**
 * APKUpdater 3.x / Obtainium use APKMirror's JSON app_exists endpoint for
 * latest release and build variants. The published shared credential is not
 * a private user secret; APKMirror may revoke or rate-limit it without notice.
 */
class ApkMirrorFeed(private val context: Context) : VersionSource {
    override val source = Source.APKMIRROR

    override suspend fun fetch(client: SourceClient): List<Release> {
        var apiError: String? = null
        val api = try {
            val response = client.postJson(
                "https://www.apkmirror.com/wp-json/apkm/v1/app_exists/",
                JSONObject().put("pnames", org.json.JSONArray().put("deezer.android.app"))
                    .put("exclude", org.json.JSONArray()).toString(),
                mapOf("Authorization" to ApkMirrorApiParser.PUBLIC_APKUPDATER_AUTH)
            )
            ApkMirrorApiParser.parse(response)
        } catch (e: Exception) {
            apiError = e.message ?: "Réponse API invalide"
            emptyList()
        }

        // The RSS feed may provide older releases. Many devices see Cloudflare
        // HTTP 403, so pause retries for 12h after a failure.
        val prefs = context.getSharedPreferences("apkmirror_rss", Context.MODE_PRIVATE)
        val lastBlocked = prefs.getLong("blocked_at", 0L)
        var feedError: String? = null
        val feed = if (System.currentTimeMillis() - lastBlocked >= 12L * 60 * 60 * 1000) {
            try {
                val releases = parseFeed(client.get("${source.website}feed/"))
                prefs.edit().remove("blocked_at").apply()
                releases
            } catch (e: Exception) {
                feedError = e.message ?: "Flux indisponible"
                prefs.edit().putLong("blocked_at", System.currentTimeMillis()).apply()
                emptyList()
            }
        } else emptyList()

        val latestVersions = api.map { it.version.lowercase() }.toSet()
        // API variants win over a general RSS entry for the same version.
        val result = api + feed.filter { it.version.lowercase() !in latestVersions }
        if (result.isNotEmpty()) return result.distinctBy { it.key }
        throw IOException("APKMirror indisponible : API ${apiError ?: "sans résultat"} ; RSS ${feedError ?: "temporairement bloqué"}")
    }

    internal fun parseFeed(xml: String): List<Release> {
        val document = Jsoup.parse(xml, "", Parser.xmlParser())
        val releases = document.select("item").take(50).mapNotNull { item ->
            val title = item.selectFirst("title")?.text().orEmpty()
            val version = VersionParsing.extract(title) ?: return@mapNotNull null
            val link = item.selectFirst("link")?.text().orEmpty().ifBlank { source.website }
            val url = ApkMirrorApiParser.absoluteLink(link) ?: return@mapNotNull null
            Release(source, version, url, VersionParsing.isBeta(title),
                VersionParsing.date(item.selectFirst("pubDate")?.text()))
        }
        if (releases.isEmpty()) throw IOException("Flux RSS vide ou non exploitable")
        return releases.distinctBy { it.version.lowercase() }
    }
}

/** Only consume fields explicitly provided by APKMirror. Do not guess APK vs bundle. */
object ApkMirrorApiParser {
    // Intentionally public upstream credential, used by APKUpdater and Obtainium.
    // Third-party shared access is not guaranteed and can be withdrawn.
    const val PUBLIC_APKUPDATER_AUTH =
        "Basic YXBpLWFwa3VwZGF0ZXI6cm01cmNmcnVVakt5MDRzTXB5TVBKWFc4"
    private const val HOST = "www.apkmirror.com"

    fun absoluteLink(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val base = URI("https://www.apkmirror.com/")
            val url = base.resolve(raw.trim()).normalize()
            if (url.scheme == "https" && url.host in listOf(HOST, "apkmirror.com"))
                url.toString() else null
        }.getOrNull()
    }

    private fun labels(item: JSONObject, name: String): List<String> {
        val array = item.optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { array.optString(it).trim() }
            .filter { it.isNotBlank() }.distinct()
    }

    private fun typeFrom(item: JSONObject, url: String): String? {
        val raw = listOf("file_type", "format", "download_type", "type")
            .firstNotNullOfOrNull { key -> item.optString(key).takeIf { it.isNotBlank() } }
            ?.trim()?.lowercase()
        val path = runCatching { URI(url).path.lowercase() }.getOrDefault("")
        return when {
            raw in listOf("apk", "single apk") || path.endsWith(".apk") -> "APK"
            raw in listOf("apkm", "apk bundle") || path.endsWith(".apkm") -> "APKM"
            raw == "apks" || path.endsWith(".apks") -> "APKS"
            raw == "xapk" || path.endsWith(".xapk") -> "XAPK"
            raw == "aab" || path.endsWith(".aab") -> "AAB"
            raw == "bundle" || raw == "split" -> "Bundle (extension inconnue)"
            else -> null
        }
    }

    fun parse(json: String): List<Release> {
        val data = JSONObject(json).optJSONArray("data")
            ?: throw IOException("API APKMirror : liste data absente")
        val releases = mutableListOf<Release>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue
            if (item.optString("pname") != "deezer.android.app" ||
                item.optBoolean("exists") != true) continue
            val general = item.optJSONObject("release") ?: continue
            val version = VersionParsing.extract(general.optString("version")) ?: continue
            val releaseUrl = absoluteLink(general.optString("link"))
                ?: Source.APKMIRROR.website
            val releaseDate = VersionParsing.date(general.optString("publish_date"))
            val beta = VersionParsing.isBeta(version)
            val variants = item.optJSONArray("apks")
            if (variants == null || variants.length() == 0) {
                releases.add(Release(Source.APKMIRROR, version, releaseUrl, beta, releaseDate))
                continue
            }
            for (v in 0 until variants.length()) {
                val variant = variants.optJSONObject(v) ?: continue
                val link = absoluteLink(variant.optString("link")) ?: releaseUrl
                val buildCode = variant.optLong("version_code").takeIf { it > 0L }
                releases.add(Release(Source.APKMIRROR, version, link, beta,
                    VersionParsing.date(variant.optString("publish_date")) ?: releaseDate,
                    versionCode = buildCode,
                    fileType = typeFrom(variant, link),
                    architectures = labels(variant, "arches"),
                    densities = labels(variant, "dpis"),
                    minApi = variant.optString("minapi").toIntOrNull()))
            }
        }
        if (releases.isEmpty()) throw IOException("API APKMirror : aucune version Deezer exploitable")
        return releases.distinctBy { it.key }
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
            val code = item.optLong("version_code").takeIf { it > 0 }
            entries.add(Release(source, version, source.website, channel, publishedAt,
                versionCode = code))
        }
        if (entries.isEmpty()) throw IOException("Aucun historique Deezer exploitable sur APKPure")
        return entries.distinctBy { it.key }
    }
}


/** APKUpdater-style anonymous Aurora GPlayAPI Google Play metadata provider. */
class GooglePlayPage(private val context: Context) : VersionSource {
    override val source = Source.GOOGLE_PLAY

    override suspend fun fetch(client: SourceClient): List<Release> = withContext(Dispatchers.IO) {
        val http = PlayHttpClient(Cache(java.io.File(context.cacheDir, "aurora-play-http"), 5L * 1024 * 1024))
        val gson = Gson()
        val prefs = context.getSharedPreferences("aurora_play_anonymous", Context.MODE_PRIVATE)

        fun anonymousAuth(): AuthData {
            val profile = NativeDeviceInfoProvider(context).getNativeDeviceProperties()
            val response = http.postAuth("https://auroraoss.com/api/auth",
                gson.toJson(profile).toByteArray(Charsets.UTF_8))
            if (!response.isSuccessful)
                throw IOException("Aurora OSS : authentification anonyme indisponible (HTTP ${response.code})")
            val auth = gson.fromJson(String(response.responseBytes, Charsets.UTF_8), AuthData::class.java)
                ?: throw IOException("Aurora OSS : réponse d'authentification vide")
            if (auth.email.isBlank()) throw IOException("Aurora OSS : aucun compte anonyme")
            prefs.edit().putString("credentials", gson.toJson(auth)).apply()
            return auth
        }

        fun details(auth: AuthData): Release {
            val app = AppDetailsHelper(auth).using(http).getAppByPackageName("deezer.android.app")
            return PlayVersionMapping.map(app.versionName, app.versionCode)
        }
        val cached = runCatching {
            prefs.getString("credentials", null)?.let { gson.fromJson(it, AuthData::class.java) }
                ?.takeIf { it.email.isNotBlank() }
        }.getOrNull()
        listOf(if (cached == null) details(anonymousAuth())
               else runCatching { details(cached) }.getOrElse { details(anonymousAuth()) })
    }
}

/** Reject missing version names rather than falsely claiming an update. */
object PlayVersionMapping {
    fun map(versionName: String, versionCode: Long): Release {
        val version = VersionParsing.extract(versionName)
            ?: throw IOException("Google Play via Aurora : version Deezer non exploitable")
        return Release(Source.GOOGLE_PLAY, version, Source.GOOGLE_PLAY.website,
            VersionParsing.isBeta(versionName), null, versionCode = versionCode.takeIf { it > 0 })
    }
}

suspend fun readAllSources(
    context: Context,
    enabled: Set<Source> = Source.entries.toSet()
): List<SourceResult> = coroutineScope {
    val client = SourceClient()
    listOf(ApkMirrorFeed(context), ApkPureHistory(), GooglePlayPage(context))
        .filter { it.source in enabled }
        .map { provider ->
        async(Dispatchers.IO) {
            try {
                SourceResult(provider.source, provider.fetch(client))
            } catch (exception: Exception) {
                SourceResult(provider.source, error = exception.message ?: "Erreur de connexion")
            }
        }
    }.awaitAll()
}
