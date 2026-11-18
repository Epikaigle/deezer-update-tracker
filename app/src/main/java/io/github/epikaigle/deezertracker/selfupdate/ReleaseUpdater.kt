package io.github.epikaigle.deezertracker.selfupdate

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class OwnRelease(
    val version: String,
    val notes: String,
    val url: String,
    val expectedSha256: String
)

/** GitHub-hosted self updates. The OS still asks the user to confirm APK installation. */
object ReleaseUpdater {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private const val ENDPOINT =
        "https://api.github.com/repos/Epikaigle/deezer-update-tracker/releases/latest"

    suspend fun latest(context: Context): OwnRelease? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(ENDPOINT)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DeezerUpdateTracker/0.1").build()
        val body = client.newCall(req).execute().use { response ->
            if (response.code == 404) return@withContext null
            if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
            response.body?.string() ?: throw IOException("Réponse GitHub vide")
        }
        val json = JSONObject(body)
        val version = json.optString("tag_name").removePrefix("v")
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        if (!newerThan(version, installed)) return@withContext null
        val assets = json.optJSONArray("assets") ?: return@withContext null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.startsWith("deezer-update-tracker-v") || !name.endsWith(".apk")) continue
            val url = asset.optString("browser_download_url")
            val digest = asset.optString("digest").removePrefix("sha256:")
            if (!url.startsWith("https://github.com/Epikaigle/deezer-update-tracker/releases/download/")) continue
            if (!digest.matches(Regex("[a-fA-F0-9]{64}"))) {
                throw IOException("Empreinte SHA-256 GitHub indisponible : installation bloquée")
            }
            return@withContext OwnRelease(version, json.optString("body"), url, digest.lowercase())
        }
        null
    }

    private fun newerThan(a: String, b: String): Boolean {
        val first = a.split(".").map { it.toIntOrNull() ?: 0 }
        val second = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(first.size, second.size)) {
            val x = first.getOrElse(i) { 0 }
            val y = second.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    suspend fun download(context: Context, release: OwnRelease): File = withContext(Dispatchers.IO) {
        require(release.url.startsWith("https://github.com/Epikaigle/deezer-update-tracker/releases/download/"))
        val dir = File(context.cacheDir, "releases").apply { mkdirs() }
        val target = File(dir, "deezer-update-tracker.apk")
        val temporary = File(dir, "incoming.tmp")
        val req = Request.Builder().url(release.url)
            .header("User-Agent", "DeezerUpdateTracker/0.1").build()
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            client.newCall(req).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Téléchargement HTTP ${response.code}")
                val body = response.body ?: throw IOException("APK absente")
                var bytes = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().use { output ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            bytes += n
                            if (bytes > 150L * 1024L * 1024L) throw IOException("APK trop volumineuse")
                            digest.update(buf, 0, n)
                            output.write(buf, 0, n)
                        }
                    }
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(release.expectedSha256, ignoreCase = true))
                throw SecurityException("SHA-256 de l'APK différent de l'empreinte GitHub")
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) throw IOException("Impossible de préparer l'APK")
            target
        } catch (e: Exception) {
            temporary.delete()
            throw e
        }
    }

    /** Returns false if Android requires the user to enable "install unknown apps" first. */
    fun requestInstall(context: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")))
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        return true
    }
}
