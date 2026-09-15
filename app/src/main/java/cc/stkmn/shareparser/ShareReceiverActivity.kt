package cc.stkmn.shareparser

import android.content.Intent
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
        val payload = SharePayloadFactory.from(this, incoming)
        if (payload == null) {
            finish()
            return
        }

        val pendingStore = PendingShareStore(this)
        if (EditorModeStore(this).activeProfileId() != null) {
            val pending = pendingStore.put(payload)
            openApp(pending.id)
            finish()
            return
        }

        val coordinator = ShareCoordinator(this)
        val matches = coordinator.matchingProfiles(payload)
        coordinator.executeAlwaysWebhooks(payload, matches)
        val settings = ProfileRepository(this).settings()
        val allChoices = coordinator.choices(payload, ShareSelectionMode.APP)
        val choices = coordinator.choices(payload, settings.shareSelectionMode)

        if (matches.size > 1) {
            val pending = pendingStore.put(payload)
            when (settings.shareSelectionMode) {
                ShareSelectionMode.APP -> openApp(pending.id)
                ShareSelectionMode.NOTIFICATION -> {
                    val profiles = coordinator.profileChoices(payload)
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

        if (choices.size == 1 && !ShareCoordinator.isExtraChoice(choices.first())) {
            val choice = choices.first()
            coordinator.execute(payload, choice.profileId, choice.actionId)
            finish()
            return
        }

        val pending = pendingStore.put(payload)
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
