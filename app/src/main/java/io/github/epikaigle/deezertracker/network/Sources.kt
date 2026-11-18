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
    listOf(ApkMirrorFeed(), ApkPureHistory(), GooglePlayPage(context))
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
