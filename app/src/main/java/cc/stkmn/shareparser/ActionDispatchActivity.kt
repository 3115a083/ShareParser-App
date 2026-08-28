package cc.stkmn.shareparser

import android.os.Bundle
import androidx.activity.ComponentActivity
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
            ShareCoordinator(this).executePending(pendingId, profileId, actionId)
        }
        finish()
    }

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_ACTION_ID = "action_id"
    }
}
