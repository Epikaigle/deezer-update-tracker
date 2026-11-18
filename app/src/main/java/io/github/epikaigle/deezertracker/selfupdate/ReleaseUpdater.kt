package io.github.epikaigle.deezertracker.selfupdate

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import io.github.epikaigle.deezertracker.network.VersionParsing
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
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        parseRelease(body, installed)
    }

    /** Pure parser kept separate from the HTTP call for repeatable security tests. */
    internal fun parseRelease(body: String, installed: String): OwnRelease? {
        val json = JSONObject(body)
        val version = json.optString("tag_name").removePrefix("v")
        if (!version.matches(Regex("""[0-9]+\.[0-9]+\.[0-9]+"""))) {
            throw IOException("Numéro de version GitHub invalide")
        }
        if (VersionParsing.compare(version, installed) <= 0) return null
        val assets = json.optJSONArray("assets") ?: return null
        val filename = "deezer-update-tracker-v" + version + ".apk"
        val expectedUrl =
            "https://github.com/Epikaigle/deezer-update-tracker/releases/download/v" +
            version + "/" + filename
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name") != filename) continue
            val url = asset.optString("browser_download_url")
            if (url != expectedUrl) continue
            val digest = asset.optString("digest").removePrefix("sha256:")
            if (!digest.matches(Regex("[a-fA-F0-9]{64}"))) {
                throw IOException("Empreinte SHA-256 GitHub indisponible : installation bloquée")
            }
            return OwnRelease(version, json.optString("body"), url, digest.lowercase())
        }
        return null
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

    /**
     * Check archive package and Android versionCode before asking the OS to install.
     * Android still enforces the same signing certificate for an in-place update.
     */
    @Suppress("DEPRECATION")
    fun validateInstallableApk(context: Context, file: File) {
        if (!file.isFile || file.length() == 0L) throw IOException("APK introuvable")
        val pm = context.packageManager
        val archive = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0L))
        } else {
            pm.getPackageArchiveInfo(file.absolutePath, 0)
        } ?: throw SecurityException("Le fichier téléchargé n'est pas une APK Android valide")
        if (archive.packageName != context.packageName) {
            throw SecurityException("Package Android inattendu : " + archive.packageName)
        }
        val installed = pm.getPackageInfo(context.packageName, 0)
        if (PackageInfoCompat.getLongVersionCode(archive) <=
            PackageInfoCompat.getLongVersionCode(installed)) {
            throw SecurityException("La mise à jour ne contient pas un versionCode supérieur")
        }
    }

    /** Returns false if Android requires the user to enable "install unknown apps" first. */
    fun requestInstall(context: Context, file: File): Boolean {
        validateInstallableApk(context, file)
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
