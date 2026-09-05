package cc.stkmn.shareparser.share

import android.content.Context
import cc.stkmn.shareparser.data.FailureReport
import cc.stkmn.shareparser.data.PendingShareStore
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile
import cc.stkmn.shareparser.data.ProfileRepository
import cc.stkmn.shareparser.data.SharedPayload
import cc.stkmn.shareparser.data.ShareSelectionMode
import cc.stkmn.shareparser.data.WebhookMode
import cc.stkmn.shareparser.engine.ActionConditionEvaluator
import cc.stkmn.shareparser.engine.ActionExecutor
import cc.stkmn.shareparser.engine.ParserEngine
import cc.stkmn.shareparser.engine.ProcessingException
import cc.stkmn.shareparser.notify.FailureNotifier
import cc.stkmn.shareparser.notify.WarningNotifier
import java.util.UUID

class ShareCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val repository = ProfileRepository(appContext)
    private val parser = ParserEngine()

    data class Choice(
        val profileId: String,
        val actionId: String,
        val profileName: String,
        val actionName: String,
        val icon: String
    ) {
        fun label(showProfile: Boolean): String = if (showProfile) "$profileName · $actionName" else actionName
    }

    fun matchingProfiles(payload: SharedPayload): List<Profile> = parser.matchingProfiles(payload, repository.profiles())

    fun choices(
        payload: SharedPayload,
        mode: ShareSelectionMode = ShareSelectionMode.APP
    ): List<Choice> = matchingProfiles(payload).flatMap { profile ->
        val values = runCatching { parser.extract(payload, profile) }.getOrDefault(emptyMap())
        profile.actions
            .filterNot { it is ProcessingAction.Webhook && it.mode == WebhookMode.ALWAYS }
            .filter { action -> ActionConditionEvaluator.isAvailable(action, profile.actions, values) }
            .filter { action ->
                when (mode) {
                    ShareSelectionMode.APP -> true
                    ShareSelectionMode.OVERLAY -> actionShownInOverlay(action)
                    ShareSelectionMode.NOTIFICATION -> actionShownInNotification(action)
                }
            }
            .map { action ->
                Choice(
                    profileId = profile.id,
                    actionId = action.id,
                    profileName = profile.name,
                    actionName = action.friendlyName,
                    icon = action.icon
                )
            }
    }

    private fun actionShownInOverlay(action: ProcessingAction): Boolean = when (action) {
        is ProcessingAction.Calendar -> action.showInOverlay
        is ProcessingAction.Url -> action.showInOverlay
        is ProcessingAction.Share -> action.showInOverlay
        is ProcessingAction.Webhook -> action.showInOverlay
    }

    private fun actionShownInNotification(action: ProcessingAction): Boolean = when (action) {
        is ProcessingAction.Calendar -> action.showInNotification
        is ProcessingAction.Url -> action.showInNotification
        is ProcessingAction.Share -> action.showInNotification
        is ProcessingAction.Webhook -> action.showInNotification
    }

    fun execute(payload: SharedPayload, profileId: String, actionId: String): Boolean {
        val profile = repository.profiles().firstOrNull { it.id == profileId } ?: return false
        val action = profile.actions.firstOrNull { it.id == actionId } ?: return false
        return execute(payload, profile, action)
    }

    fun executePending(pendingId: String, profileId: String, actionId: String, consume: Boolean = true): Boolean {
        val store = PendingShareStore(appContext)
        val pending = store.get(pendingId) ?: return false
        val result = execute(pending.payload, profileId, actionId)
        if (consume && result) store.remove(pendingId)
        return result
    }

    fun executeAlwaysWebhooks(payload: SharedPayload, profiles: List<Profile> = matchingProfiles(payload)) {
        profiles.forEach { profile ->
            profile.actions
                .filterIsInstance<ProcessingAction.Webhook>()
                .filter { it.mode == WebhookMode.ALWAYS }
                .forEach { action ->
                    Thread {
                        var succeeded = false
                        repeat(3) { attempt ->
                            if (!succeeded) {
                                succeeded = executeInternal(
                                    payload = payload,
                                    profile = profile,
                                    action = action,
                                    notifyFailure = attempt == 2,
                                    backgroundMode = true
                                )
                                if (!succeeded && attempt < 2) {
                                    Thread.sleep(if (attempt == 0) 2_000L else 5_000L)
                                }
                            }
                        }
                    }.apply {
                        name = "ShareParser-background-webhook"
                        start()
                    }
                }
        }
    }

    fun execute(payload: SharedPayload, profile: Profile, action: ProcessingAction): Boolean {
        if (action is ProcessingAction.Webhook) {
            Thread {
                executeInternal(payload, profile, action)
            }.apply {
                name = "ShareParser-webhook"
                start()
            }
            return true
        }
        return executeInternal(payload, profile, action)
    }

    private fun executeInternal(
        payload: SharedPayload,
        profile: Profile,
        action: ProcessingAction,
        notifyFailure: Boolean = true,
        backgroundMode: Boolean = false
    ): Boolean {
        return try {
            val extracted = parser.extract(payload, profile)
            if (!ActionConditionEvaluator.isAvailable(action, profile.actions, extracted)) {
                return true
            }
            val result = ActionExecutor(appContext, repository.settings(), backgroundMode).execute(action, extracted)
            WarningNotifier.show(appContext, result.warnings)
            true
        } catch (error: Throwable) {
            val processing = error as? ProcessingException
            val report = FailureReport(
                id = UUID.randomUUID().toString(),
                profileId = profile.id,
                profileName = profile.name,
                actionId = action.id,
                message = processing?.userMessage ?: "Verarbeitung fehlgeschlagen.",
                technicalDetails = processing?.technicalDetails ?: error.stackTraceToString(),
                failingField = processing?.failingField,
                inputPreview = payload.combined.take(2000),
                createdAtEpochMs = System.currentTimeMillis()
            )
            if (notifyFailure) {
                runCatching { repository.saveFailure(report) }
                FailureNotifier.show(appContext, report)
            }
            false
        }
    }
}
