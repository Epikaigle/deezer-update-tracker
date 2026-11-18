package io.github.epikaigle.deezertracker.tracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.epikaigle.deezertracker.MainActivity
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Snapshot
import io.github.epikaigle.deezertracker.network.readAllSources
import io.github.epikaigle.deezertracker.storage.HistoryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TrackerEngine(private val context: Context) {
    /** Manual checks never notify. "Jamais" also disables the worker entirely. */
    suspend fun check(background: Boolean): Snapshot = withContext(Dispatchers.IO) {
        val store = HistoryStore(context)
        if (background && store.frequencyMinutes == 0) return@withContext store.snapshot()
        val results = readAllSources()
        val fresh = store.merge(results)
        if (background && store.frequencyMinutes != 0 && fresh.isNotEmpty()) {
            UpdateNotifications.show(context, fresh)
        }
        store.snapshot()
    }
}

class TrackerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (HistoryStore(applicationContext).frequencyMinutes == 0) return Result.success()
        return try {
            TrackerEngine(applicationContext).check(background = true)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object TrackerScheduler {
    private const val JOB = "deezer-release-check"

    fun configure(context: Context) {
        val work = WorkManager.getInstance(context)
        val interval = HistoryStore(context).frequencyMinutes
        if (interval == 0) {
            work.cancelUniqueWork(JOB)
            UpdateNotifications.dismiss(context)
            return
        }
        val request = PeriodicWorkRequestBuilder<TrackerWorker>(interval.toLong(), TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(JOB, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

object UpdateNotifications {
    private const val CHANNEL = "deezer_versions"
    private const val ID = 101

    fun dismiss(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID)
    }

    fun show(context: Context, newReleases: List<Release>) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Nouvelles versions Deezer", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val first = newReleases.first()
        val title = if (newReleases.size == 1) "Deezer ${first.version} disponible"
                    else "${newReleases.size} nouvelles versions Deezer"
        val message = newReleases.joinToString(" · ") { "${it.version} (${it.source.title})" }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
}
