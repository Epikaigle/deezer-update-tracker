package io.github.epikaigle.deezertracker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.epikaigle.deezertracker.model.Release
import io.github.epikaigle.deezertracker.model.Snapshot
import io.github.epikaigle.deezertracker.model.Source
import io.github.epikaigle.deezertracker.network.VersionParsing
import io.github.epikaigle.deezertracker.selfupdate.OwnRelease
import io.github.epikaigle.deezertracker.selfupdate.ReleaseUpdater
import io.github.epikaigle.deezertracker.storage.HistoryStore
import io.github.epikaigle.deezertracker.tracker.TrackerEngine
import io.github.epikaigle.deezertracker.tracker.TrackerScheduler
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private enum class Tab(val label: String) {
    OVERVIEW("Accueil"), HISTORY("Historique"), SETTINGS("Paramètres")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TrackerScheduler.configure(this)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                TrackerScreen()
            }
        }
    }
}

@Composable
private fun TrackerScreen() {
    val context = LocalContext.current
    val store = remember(context) { HistoryStore(context) }
    val scope = rememberCoroutineScope()
    var selectedTab by rememberSaveable { mutableIntStateOf(Tab.OVERVIEW.ordinal) }
    val tab = Tab.entries[selectedTab]
    var snapshot by remember { mutableStateOf(store.snapshot()) }
    var refreshing by remember { mutableStateOf(false) }
    var searchingForUpdate by remember { mutableStateOf(true) }
    var selfRelease by remember { mutableStateOf<OwnRelease?>(null) }
    var updaterMessage by remember { mutableStateOf("") }
    var trackerMessage by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var savedApk by remember { mutableStateOf<File?>(null) }
    var search by rememberSaveable { mutableStateOf("") }

    var permissionRefresh by remember { mutableIntStateOf(0) }
    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionRefresh++ }
    val notificationsGranted = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    // Triggers recomposition of the permission hint after an Android permission decision.
    @Suppress("UNUSED_VARIABLE") val trackedPermission = permissionRefresh

    fun refresh() {
        if (refreshing) return
        if (store.enabledSources.isEmpty()) {
            trackerMessage = "Active au moins une source dans Paramètres."
            return
        }
        scope.launch {
            refreshing = true
            trackerMessage = ""
            try {
                snapshot = TrackerEngine(context).check(background = false)
            } catch (e: Exception) {
                trackerMessage = "Vérification Deezer impossible : " + (e.message ?: "erreur réseau")
            } finally {
                refreshing = false
            }
        }
    }

    fun checkOurUpdate() {
        scope.launch {
            searchingForUpdate = true
            updaterMessage = ""
            try {
                val latest = ReleaseUpdater.latest(context)
                if (latest?.url != selfRelease?.url) savedApk = null
                selfRelease = latest
            } catch (e: Exception) {
                updaterMessage = "Recherche de mise à jour impossible : " + (e.message ?: "erreur réseau")
            } finally {
                searchingForUpdate = false
            }
        }
    }

    fun installLatest() {
        val release = selfRelease ?: return
        if (installing) return
        scope.launch {
            installing = true
            updaterMessage = ""
            try {
                val apk = savedApk?.takeIf { it.isFile && it.length() > 0L }
                    ?: ReleaseUpdater.download(context, release).also { savedApk = it }
                val started = ReleaseUpdater.requestInstall(context, apk)
                updaterMessage = if (started) {
                    "Confirme l'installation proposée par Android."
                } else {
                    "Autorise l'installation depuis cette application puis réappuie sur Installer."
                }
            } catch (e: Exception) {
                updaterMessage = "Installation impossible : " + (e.message ?: "erreur inconnue")
                savedApk = null
            } finally {
                installing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        if (store.enabledSources.isNotEmpty()) refresh()
        checkOurUpdate()
    }

    BackHandler(enabled = tab != Tab.OVERVIEW) { selectedTab = Tab.OVERVIEW.ordinal }

    val frequencies = listOf(
        0 to "Jamais",
        30 to "30 min",
        60 to "1 h",
        180 to "3 h",
        720 to "12 h"
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { option ->
                    NavigationBarItem(
                        selected = tab == option,
                        onClick = { selectedTab = option.ordinal },
                        icon = {
                            when (option) {
                                Tab.OVERVIEW -> Icon(Icons.Default.Home, contentDescription = option.label)
                                Tab.HISTORY -> Icon(Icons.Default.History, contentDescription = option.label)
                                Tab.SETTINGS -> Icon(Icons.Default.Settings, contentDescription = option.label)
                            }
                        },
                        label = null,
                        alwaysShowLabel = false
                    )
                }
            }
        }
    ) { scaffoldPadding ->
        Box(Modifier.fillMaxSize().padding(scaffoldPadding), contentAlignment = Alignment.TopCenter) {
            val listModifier = Modifier.widthIn(max = 760.dp).fillMaxSize()
            when (tab) {
                Tab.OVERVIEW -> LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {

                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Deezer", style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (refreshing) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else IconButton(
                                enabled = snapshot.enabledSources.isNotEmpty(),
                                onClick = { refresh() }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Actualiser Deezer")
                            }
                        }
                        if (trackerMessage.isNotEmpty()) Text(trackerMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                        if (snapshot.frequencyMinutes > 0 && !notificationsGranted) {
                            IconButton(onClick = {
                                if (Build.VERSION.SDK_INT >= 33)
                                    requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) {
                                Icon(Icons.Default.NotificationsOff,
                                    contentDescription = "Autoriser les notifications")
                            }
                        }
                    }
                    item {
                        OutlinedCard {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val current = remember {
                                    context.packageManager.getPackageInfo(context.packageName, 0)
                                        .versionName ?: "?"
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("App $current", style = MaterialTheme.typography.titleMedium)
                                    if (selfRelease != null) Text("→ ${selfRelease!!.version}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall)
                                }
                                if (searchingForUpdate || installing) {
                                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                } else if (selfRelease != null) {
                                    IconButton(onClick = { installLatest() }) {
                                        Icon(Icons.Default.Download,
                                            contentDescription = "Télécharger et installer la mise à jour")
                                    }
                                } else Icon(Icons.Default.CheckCircle,
                                    contentDescription = "Application à jour",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp))
                                IconButton(onClick = { checkOurUpdate() },
                                    enabled = !searchingForUpdate && !installing) {
                                    Icon(Icons.Default.Refresh,
                                        contentDescription = "Rechercher une mise à jour de l'application")
                                }
                            }
                            if (updaterMessage.isNotEmpty()) Text(updaterMessage,
                                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    items(listOf(Source.GOOGLE_PLAY, Source.APKMIRROR, Source.APKPURE),
                        key = { it.name }) { source ->
                        val status = snapshot.statuses.firstOrNull { it.source == source }
                        val active = source in snapshot.enabledSources
                        val latest = snapshot.releases
                            .filter { it.source == source && (snapshot.includeBeta || !it.beta) }
                            .maxWithOrNull { x, y -> VersionParsing.compare(x.version, y.version) }
                        OutlinedCard {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp,
                                    top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(source.title, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(8.dp))
                                        if (active && status != null) Icon(
                                            if (status.ok) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = if (status.ok) "Source OK"
                                                else status.message,
                                            tint = if (status.ok) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(latest?.version ?: "—",
                                        style = MaterialTheme.typography.titleMedium)
                                    if (active && status?.ok == false) Text(status.message,
                                        maxLines = 2,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error)
                                }
                                if (latest != null) IconButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(latest.url)))
                                }) {
                                    Icon(Icons.Default.OpenInNew,
                                        contentDescription = "Ouvrir ${source.title}")
                                }
                            }
                        }
                    }

                }

                Tab.HISTORY -> HistoryColumns(
                    snapshot = snapshot,
                    search = search,
                    onSearch = { search = it },
                    onOpen = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )

                Tab.SETTINGS -> LazyColumn(
                    modifier = listModifier,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        SectionTitle("Réglages")
                    }
                    item {
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Notifications", fontWeight = FontWeight.Bold)
                                Text("Jamais : aucune notification ni vérification en arrière-plan. Les données sont actualisées à l'ouverture.",
                                    style = MaterialTheme.typography.bodySmall)
                                frequencies.forEach { (minutes, description) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            store.frequencyMinutes = minutes
                                            TrackerScheduler.configure(context)
                                            snapshot = store.snapshot()
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = snapshot.frequencyMinutes == minutes,
                                            onClick = {
                                                store.frequencyMinutes = minutes
                                                TrackerScheduler.configure(context)
                                                snapshot = store.snapshot()
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(description, modifier = Modifier.weight(1f))
                                    }
                                }
                                if (snapshot.frequencyMinutes > 0 && !notificationsGranted) {
                                    HorizontalDivider()

                                    IconButton(onClick = {
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            requestNotificationPermission.launch(
                                                Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }) {
                                        Icon(Icons.Default.NotificationsOff,
                                            contentDescription = "Autoriser les notifications")
                                    }
                                }
                                Text("Les horaires peuvent être décalés par l'économie de batterie Android.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        OutlinedCard {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text("Sources", fontWeight = FontWeight.Bold)
                                Text("Une source indisponible n'empêche pas la surveillance des autres.",
                                    style = MaterialTheme.typography.bodySmall)
                                listOf(Source.GOOGLE_PLAY, Source.APKMIRROR, Source.APKPURE).forEach { source ->
                                    Row(
                                        Modifier.fillMaxWidth().clickable {
                                            val newSelection = snapshot.enabledSources.toMutableSet()
                                            if (source in newSelection) newSelection.remove(source)
                                            else newSelection.add(source)
                                            store.enabledSources = newSelection
                                            TrackerScheduler.configure(context)
                                            snapshot = store.snapshot()
                                        }.padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = source in snapshot.enabledSources,
                                            onCheckedChange = { checked ->
                                                val selected = snapshot.enabledSources.toMutableSet()
                                                if (checked) selected.add(source) else selected.remove(source)
                                                store.enabledSources = selected
                                                TrackerScheduler.configure(context)
                                                snapshot = store.snapshot()
                                            }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(source.title)
                                    }
                                }
                                if (snapshot.enabledSources.isEmpty()) Text(
                                    "Aucune source activée : aucune version ne sera récupérée.",
                                    color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item {
                        OutlinedCard {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Bêta", fontWeight = FontWeight.Bold)
                                    
                                }
                                Switch(
                                    checked = snapshot.includeBeta,
                                    onCheckedChange = {
                                        store.includeBeta = it
                                        snapshot = store.snapshot()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}


private fun formatted(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRENCH)
        .format(Date(timestamp))
