package cc.stkmn.shareparser

import android.content.Intent
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import cc.stkmn.shareparser.engine.BoundedWork
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import cc.stkmn.shareparser.data.EditorModeStore
import cc.stkmn.shareparser.data.PendingShareStore
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.notify.ShareSelectionNotifier
import cc.stkmn.shareparser.share.ShareCoordinator
import cc.stkmn.shareparser.share.ShareOverlayService
import cc.stkmn.shareparser.share.SharePayloadFactory

class ShareReceiverActivity : ComponentActivity() {
    private var handling: Job? = null
    companion object { private val inputWork = BoundedWork("ShareParser-content", timeoutMs = 15_000) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLocale.apply(this, ProfileRepository(this).settings().appLanguage)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(incoming: Intent) {
        handling?.cancel()
        handling = lifecycleScope.launch {
            try { handleShare(incoming) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                cc.stkmn.shareparser.notify.UserNotice.showLong(this@ShareReceiverActivity, e.message ?: "Geteilter Inhalt konnte nicht gelesen werden.")
                finish()
            }
        }
    }
    private suspend fun handleShare(incoming: Intent) {
        val sourceHints = SharePayloadFactory.sourceHints(this)
        val appContext = applicationContext
        val payload = withContext(Dispatchers.IO) { inputWork.run { SharePayloadFactory.from(appContext, incoming, sourceHints) } }
        if (payload == null) {
            finish()
            return
        }

        val pendingStore = PendingShareStore(this)
        if (EditorModeStore(this).activeProfileId() != null) {
            val pending = withContext(Dispatchers.IO) { pendingStore.put(payload) }
            openApp(pending.id)
            finish()
            return
        }

        val coordinator = ShareCoordinator(this)
        val matches = withContext(Dispatchers.Default) { coordinator.matchingProfiles(payload) }
        coordinator.executeAlwaysWebhooks(payload, matches)
        val settings = ProfileRepository(this).settings()
        val allChoices = withContext(Dispatchers.Default) { coordinator.choices(payload, ShareSelectionMode.APP) }
        val choices = withContext(Dispatchers.Default) { coordinator.choices(payload, settings.shareSelectionMode) }

        if (matches.size > 1) {
            val pending = withContext(Dispatchers.IO) { pendingStore.put(payload) }
            when (settings.shareSelectionMode) {
                ShareSelectionMode.APP -> openApp(pending.id)
                ShareSelectionMode.NOTIFICATION -> {
                    val profiles = withContext(Dispatchers.Default) { coordinator.profileChoices(payload) }
                    if (!ShareSelectionNotifier.show(this, pending.id, profiles)) openApp(pending.id)
                }
                ShareSelectionMode.OVERLAY -> {
                    if (Settings.canDrawOverlays(this)) {
                        startService(
                            Intent(this, ShareOverlayService::class.java)
                                .putExtra(ShareOverlayService.EXTRA_PENDING_ID, pending.id)
                        )
                    } else {
                        openApp(pending.id)
                    }
                }
            }
            finish()
            return
        }

        if (allChoices.isEmpty() && matches.isNotEmpty()) {
            finish()
            return
        }

        if (allChoices.size == 1 && choices.size == 1 && !ShareCoordinator.isExtraChoice(choices.first()) &&
            coordinator.mayRunAutomatically(choices.first().profileId, choices.first().actionId)) {
            val choice = choices.first()
            coordinator.executeAwait(payload, choice.profileId, choice.actionId)
            finish()
            return
        }

        val pending = withContext(Dispatchers.IO) { pendingStore.put(payload) }
        if (choices.isEmpty()) {
            openApp(pending.id)
            finish()
            return
        }

        when (settings.shareSelectionMode) {
            ShareSelectionMode.APP -> openApp(pending.id)
            ShareSelectionMode.NOTIFICATION -> {
                if (!ShareSelectionNotifier.show(this, pending.id, choices)) openApp(pending.id)
            }
            ShareSelectionMode.OVERLAY -> {
                if (Settings.canDrawOverlays(this)) {
                    startService(
                        Intent(this, ShareOverlayService::class.java)
                            .putExtra(ShareOverlayService.EXTRA_PENDING_ID, pending.id)
                    )
                } else {
                    openApp(pending.id)
                }
            }
        }
        finish()
    }

    private fun openApp(pendingId: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_PENDING_SHARE
                putExtra(MainActivity.EXTRA_PENDING_SHARE_ID, pendingId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }
}
