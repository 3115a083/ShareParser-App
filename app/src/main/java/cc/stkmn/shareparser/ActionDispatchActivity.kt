package cc.stkmn.shareparser

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import cc.stkmn.shareparser.data.PendingShareStore
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.notify.ShareSelectionNotifier
import cc.stkmn.shareparser.share.ShareCoordinator

class ActionDispatchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pendingId = intent.getStringExtra(EXTRA_PENDING_ID).orEmpty()
        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID).orEmpty()
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID).orEmpty()
        if (pendingId.isNotBlank() && profileId.isNotBlank() && actionId.isNotBlank()) {
            ShareSelectionNotifier.cancel(this, pendingId)
            val coordinator = ShareCoordinator(this)
            if (actionId == ShareCoordinator.SELECT_PROFILE_ACTION_ID) {
                val pending = PendingShareStore(this).get(pendingId)
                val choices = pending?.let {
                    coordinator.choicesForProfile(it.payload, profileId, ShareSelectionMode.NOTIFICATION)
                }.orEmpty()
                when {
                    choices.size == 1 -> coordinator.executePending(
                        pendingId,
                        choices.first().profileId,
                        choices.first().actionId
                    )
                    choices.isNotEmpty() -> {
                        if (!ShareSelectionNotifier.show(this, pendingId, choices)) openPicker(pendingId)
                    }
                    else -> openPicker(pendingId)
                }
            } else {
                coordinator.executePending(pendingId, profileId, actionId)
            }
        }
        finish()
    }

    private fun openPicker(pendingId: String) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_PENDING_SHARE
                putExtra(MainActivity.EXTRA_PENDING_SHARE_ID, pendingId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_ACTION_ID = "action_id"
    }
}
