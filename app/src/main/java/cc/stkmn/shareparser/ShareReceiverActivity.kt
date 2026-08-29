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
        val choices = coordinator.choices(payload)

        if (matches.isNotEmpty() && choices.isEmpty()) {
            finish()
            return
        }

        if (matches.size == 1 && choices.size == 1) {
            val choice = choices.first()
            val action = matches.first().actions.firstOrNull { it.id == choice.actionId }
            if (action != null) coordinator.execute(payload, matches.first(), action)
            finish()
            return
        }

        val pending = pendingStore.put(payload)
        if (matches.isEmpty()) {
            openApp(pending.id)
            finish()
            return
        }

        when (ProfileRepository(this).settings().shareSelectionMode) {
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
