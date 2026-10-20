package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.WebhookMode

/** Source-app metadata is deliberately irrelevant to authorization. */
object ActionRunPolicy {
    fun mayRunAutomatically(action: ProcessingAction): Boolean = !action.confirmBeforeAutoRun
    fun isAutomaticWebhook(action: ProcessingAction): Boolean =
        action is ProcessingAction.Webhook && action.mode == WebhookMode.ALWAYS && mayRunAutomatically(action)
    fun isSelectable(action: ProcessingAction): Boolean = !isAutomaticWebhook(action)
}
