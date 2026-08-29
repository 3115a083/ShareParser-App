package cc.stkmn.shareparser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cc.stkmn.shareparser.data.EditorModeStore
import cc.stkmn.shareparser.data.PendingShareStore
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.ui.FailureScreen
import cc.stkmn.shareparser.ui.HomeScreen
import cc.stkmn.shareparser.ui.ProfileEditorScreen
import cc.stkmn.shareparser.ui.RegionalSettingsScreen
import cc.stkmn.shareparser.ui.SettingsHomeScreen
import cc.stkmn.shareparser.ui.localized
import cc.stkmn.shareparser.ui.SharedScreen

class MainActivity : ComponentActivity() {
    private val latestIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashRecorder.install(this)
        val startupSettings = ProfileRepository(this).settings()
        AppLocale.apply(this, startupSettings.appLanguage)
        val pendingCrash = CrashRecorder.consumePending(this)
        latestIntent.value = if (pendingCrash && intent.action == Intent.ACTION_MAIN) {
            Intent(Intent.ACTION_VIEW, Uri.parse("shareparser://failure/crash"))
        } else {
            intent
        }
        setContent {
            ShareParserApp(
                startIntent = latestIntent.value,
                onIntentConsumed = ::clearIncomingIntent
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        latestIntent.value = intent
    }

    private fun clearIncomingIntent() {
        latestIntent.value = null
        setIntent(Intent())
    }

    companion object {
        const val ACTION_OPEN_PENDING_SHARE = "cc.stkmn.shareparser.OPEN_PENDING_SHARE"
        const val EXTRA_PENDING_SHARE_ID = "pending_share_id"
    }
}

private sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object RegionalSettings : Screen
    data class Editor(
        val profile: Profile?,
        val sample: SharedPayload? = null,
        val highlightField: String? = null
    ) : Screen
    data class Shared(val payload: SharedPayload) : Screen
    data object Failure : Screen
}

private fun previousScreen(screen: Screen): Screen = when (screen) {
    Screen.RegionalSettings -> Screen.Settings
    Screen.Settings, is Screen.Editor, is Screen.Shared, Screen.Failure -> Screen.Home
    Screen.Home -> Screen.Home
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareParserApp(startIntent: Intent?, onIntentConsumed: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { ProfileRepository(context) }
    val editorMode = remember { EditorModeStore(context) }
    val pendingShareStore = remember { PendingShareStore(context) }
    var profiles by remember { mutableStateOf(repository.profiles()) }
    var screen by remember { mutableStateOf<Screen>(if (startIntent?.isFailureLink() == true) Screen.Failure else Screen.Home) }
    var importError by remember { mutableStateOf<String?>(null) }
    var editorDirty by remember { mutableStateOf(false) }
    var editorExitRequest by remember { mutableIntStateOf(0) }
    var editorUndoRequest by remember { mutableIntStateOf(0) }
    var editorRedoRequest by remember { mutableIntStateOf(0) }
    var editorCanUndo by remember { mutableStateOf(false) }
    var editorCanRedo by remember { mutableStateOf(false) }

    fun requestBack() {
        if (screen is Screen.Editor && editorDirty) {
            editorExitRequest += 1
        } else {
            if (screen is Screen.Editor) editorMode.clear()
            screen = previousScreen(screen)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Datei konnte nicht gelesen werden")
                repository.import(text)
            }.onSuccess {
                profiles = repository.profiles()
                screen = Screen.Editor(it)
                importError = null
            }.onFailure {
                importError = "Profil konnte nicht importiert werden: ${it.message}"
            }
        }
    }

    LaunchedEffect(startIntent) {
        if (startIntent == null) return@LaunchedEffect
        when {
            startIntent.isFailureLink() -> screen = Screen.Failure
            startIntent.action == MainActivity.ACTION_OPEN_PENDING_SHARE -> {
                val id = startIntent.getStringExtra(MainActivity.EXTRA_PENDING_SHARE_ID).orEmpty()
                val payload = pendingShareStore.get(id)?.payload
                if (payload != null) {
                    val activeProfileId = editorMode.activeProfileId()
                    val currentEditor = screen as? Screen.Editor
                    screen = when {
                        activeProfileId != null && currentEditor != null -> currentEditor.copy(sample = payload, highlightField = null)
                        activeProfileId != null -> profiles.firstOrNull { it.id == activeProfileId }
                            ?.let { Screen.Editor(it, sample = payload) }
                            ?: if (profiles.isEmpty()) Screen.Editor(null, sample = payload) else Screen.Shared(payload)
                        profiles.isEmpty() -> Screen.Editor(null, sample = payload)
                        else -> Screen.Shared(payload)
                    }
                    pendingShareStore.remove(id)
                }
            }
        }
        onIntentConsumed()
    }

    BackHandler(enabled = screen !is Screen.Home) {
        requestBack()
    }

    MaterialTheme(colorScheme = dynamicOrDefaultScheme()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppArtworkImage(
                                assetPath = AppArtwork.FOREGROUND_ASSET,
                                contentDescription = null,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                localized(when (val current = screen) {
                                    Screen.Home -> "ShareParser"
                                    Screen.Settings -> "Einstellungen"
                                    Screen.RegionalSettings -> "Datum und Uhrzeit"
                                    is Screen.Editor -> if (current.profile == null) "Profil erstellen" else "Profil bearbeiten"
                                    is Screen.Shared -> "Geteilter Inhalt"
                                    Screen.Failure -> "Fehlerbericht"
                                })
                            )
                        }
                    },
                    navigationIcon = {
                        if (screen !is Screen.Home) {
                            IconButton(onClick = { requestBack() }) {
                                Icon(Icons.Outlined.ArrowBack, "Zurück")
                            }
                        }
                    },
                    actions = {
                        if (screen is Screen.Editor) {
                            IconButton(
                                onClick = { editorUndoRequest += 1 },
                                enabled = editorCanUndo
                            ) {
                                Icon(Icons.Outlined.Undo, "Änderung rückgängig")
                            }
                            IconButton(
                                onClick = { editorRedoRequest += 1 },
                                enabled = editorCanRedo
                            ) {
                                Icon(Icons.Outlined.Redo, "Änderung wiederholen")
                            }
                        }
                        if (screen is Screen.Home) {
                            IconButton(onClick = { screen = Screen.Settings }) {
                                Icon(Icons.Outlined.Settings, "Einstellungen")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (screen is Screen.Home) {
                    FloatingActionButton(onClick = { screen = Screen.Editor(null) }) {
                        Icon(Icons.Outlined.Add, "Profil erstellen")
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (val current = screen) {
                    Screen.Home -> HomeScreen(
                        profiles = profiles,
                        importError = importError,
                        onEdit = { screen = Screen.Editor(it) },
                        onCreate = { screen = Screen.Editor(null) },
                        onImport = { importLauncher.launch(arrayOf("application/json", "text/json", "text/plain")) },
                        onToggle = { profile, enabled ->
                            repository.setEnabled(profile.id, enabled)
                            profiles = repository.profiles()
                        },
                        onDelete = { profile ->
                            repository.delete(profile.id)
                            profiles = repository.profiles()
                        },
                        onSettings = { screen = Screen.Settings }
                    )
                    Screen.Settings -> SettingsHomeScreen(
                        repository = repository,
                        onRegionalSettings = { screen = Screen.RegionalSettings }
                    )
                    Screen.RegionalSettings -> RegionalSettingsScreen(repository = repository)
                    is Screen.Editor -> ProfileEditorScreen(
                        existing = current.profile,
                        sample = current.sample,
                        highlightField = current.highlightField,
                        repository = repository,
                        onSaved = {
                            editorMode.clear()
                            editorDirty = false
                            profiles = repository.profiles()
                            screen = Screen.Home
                        },
                        onDeleted = {
                            editorMode.clear()
                            editorDirty = false
                            profiles = repository.profiles()
                            screen = Screen.Home
                        },
                        exitRequest = editorExitRequest,
                        undoRequest = editorUndoRequest,
                        redoRequest = editorRedoRequest,
                        onDirtyChanged = { editorDirty = it },
                        onHistoryChanged = { canUndo, canRedo ->
                            editorCanUndo = canUndo
                            editorCanRedo = canRedo
                        },
                        onExitRequestHandled = { editorExitRequest = 0 },
                        onHistoryRequestHandled = {
                            editorUndoRequest = 0
                            editorRedoRequest = 0
                        },
                        onDiscarded = {
                            editorMode.clear()
                            editorDirty = false
                            screen = Screen.Home
                        }
                    )
                    is Screen.Shared -> SharedScreen(
                        payload = current.payload,
                        profiles = profiles,
                        repository = repository,
                        onEditProfile = { screen = Screen.Editor(it, sample = current.payload) },
                        onCreateFromSample = { screen = Screen.Editor(null, sample = current.payload) }
                    )
                    Screen.Failure -> {
                        val report = repository.lastFailure()
                        FailureScreen(
                            report = report,
                            profile = report?.profileId?.let { id -> profiles.firstOrNull { it.id == id } },
                            onEdit = { profile, field -> screen = Screen.Editor(profile, highlightField = field) }
                        )
                    }
                }
            }
        }
    }
}

private fun Intent.isFailureLink(): Boolean =
    data?.scheme == "shareparser" && data?.host == "failure"

@Composable
private fun dynamicOrDefaultScheme(): ColorScheme {
    val context = LocalContext.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
}
