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
import cc.stkmn.shareparser.engine.GuidedRuleFactory
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import cc.stkmn.shareparser.engine.ParserEngine
import cc.stkmn.shareparser.engine.ProcessingException
import cc.stkmn.shareparser.notify.FailureNotifier
import cc.stkmn.shareparser.notify.WarningNotifier
import java.util.UUID

class ShareCoordinator(context: Context) {
    companion object {
        const val EXTRA_PROFILE_ID = "__shareparser_extra__"
        private const val EXTRA_MAP = "__extra_map__"
        private const val EXTRA_WEB = "__extra_web__"
        private const val EXTRA_PHONE = "__extra_phone__"
        private const val EXTRA_EMAIL = "__extra_email__"
        private const val EXTRA_FILE = "__extra_file__"

        fun isExtraChoice(choice: Choice): Boolean = choice.profileId == EXTRA_PROFILE_ID
    }
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
    ): List<Choice> {
        val profileChoices = matchingProfiles(payload).flatMap { profile ->
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
        return if (mode == ShareSelectionMode.APP) profileChoices + extraChoices(payload) else profileChoices
    }

    private fun extraChoices(payload: SharedPayload): List<Choice> {
        val settings = repository.settings()
        val candidates = GuidedRuleFactory.candidates(payload)
        val address = candidates.firstOrNull { it.suggestedKey == "adresse" }?.value
        val web = candidates.firstOrNull { it.suggestedKey == "link" && !it.value.startsWith("mailto:", true) && !it.value.startsWith("tel:", true) }?.value
        val phone = candidates.firstOrNull { it.suggestedKey == "telefon" }?.value
        val email = candidates.firstOrNull { it.suggestedKey == "email" }?.value
        return buildList {
            if (settings.extraShareMap && !address.isNullOrBlank()) {
                add(Choice(EXTRA_PROFILE_ID, EXTRA_MAP, "ShareParser", "Adresse in Karten-App öffnen", "map"))
            }
            if (settings.extraShareWebLink && !web.isNullOrBlank()) {
                add(Choice(EXTRA_PROFILE_ID, EXTRA_WEB, "ShareParser", "Web-Link öffnen", "link"))
            }
            if (settings.extraSharePhone && !phone.isNullOrBlank()) {
                add(Choice(EXTRA_PROFILE_ID, EXTRA_PHONE, "ShareParser", "Telefonnummer öffnen", "phone"))
            }
            if (settings.extraShareEmail && !email.isNullOrBlank()) {
                add(Choice(EXTRA_PROFILE_ID, EXTRA_EMAIL, "ShareParser", "E-Mail öffnen", "mail"))
            }
            if (settings.extraShareFileOpen && payload.fileName.isNotBlank()) {
                add(Choice(EXTRA_PROFILE_ID, EXTRA_FILE, "ShareParser", "Datei direkt öffnen", "description"))
            }
        }
    }

    private fun executeExtra(payload: SharedPayload, actionId: String): Boolean {
        val settings = repository.settings()
        val candidates = GuidedRuleFactory.candidates(payload)
        val values = mapOf(
            "input" to payload.combined,
            "text" to payload.text,
            "subject" to payload.subject,
            "source_app" to payload.sourceApp,
            "source_package" to payload.sourcePackage,
            "file_name" to payload.fileName,
            "mime_type" to payload.mimeType
        )
        val action: ProcessingAction = when (actionId) {
            EXTRA_MAP -> {
                val address = candidates.firstOrNull { it.suggestedKey == "adresse" }?.value ?: return false
                val encoded = URLEncoder.encode(address, StandardCharsets.UTF_8.toString())
                ProcessingAction.Url(EXTRA_MAP, "Adresse in Karten-App öffnen", "map", urlTemplate = "geo:0,0?q=$encoded")
            }
            EXTRA_WEB -> {
                val url = candidates.firstOrNull { it.suggestedKey == "link" && !it.value.startsWith("mailto:", true) && !it.value.startsWith("tel:", true) }?.value ?: return false
                ProcessingAction.Url(EXTRA_WEB, "Web-Link öffnen", "link", urlTemplate = if (url.startsWith("www.", true)) "https://$url" else url)
            }
            EXTRA_PHONE -> {
                val raw = candidates.firstOrNull { it.suggestedKey == "telefon" }?.value ?: return false
                val value = if (raw.startsWith("tel:", true)) raw else "tel:" + raw.filter { it.isDigit() || it == '+' }
                ProcessingAction.Url(EXTRA_PHONE, "Telefonnummer öffnen", "phone", urlTemplate = value)
            }
            EXTRA_EMAIL -> {
                val raw = candidates.firstOrNull { it.suggestedKey == "email" }?.value ?: return false
                val value = if (raw.startsWith("mailto:", true)) raw else "mailto:$raw"
                ProcessingAction.Url(EXTRA_EMAIL, "E-Mail öffnen", "mail", urlTemplate = value)
            }
            EXTRA_FILE -> {
                val ext = payload.fileName.substringAfterLast('.', "txt").lowercase().take(12)
                ProcessingAction.Share(
                    id = EXTRA_FILE,
                    friendlyName = "Datei direkt öffnen",
                    icon = "description",
                    textTemplate = "{{text}}",
                    subjectTemplate = "{{subject}}",
                    asFile = true,
                    fileMode = cc.stkmn.shareparser.data.TextFileMode.OPEN,
                    fileNameTemplate = payload.fileName,
                    fileExtension = ext
                )
            }
            else -> return false
        }
        return try {
            val result = ActionExecutor(appContext, settings).execute(action, values)
            WarningNotifier.show(appContext, result.warnings)
            true
        } catch (error: Throwable) {
            val processing = error as? ProcessingException
            val report = FailureReport(
                id = UUID.randomUUID().toString(),
                profileId = null,
                profileName = "ShareParser",
                actionId = actionId,
                message = processing?.userMessage ?: "Zusätzliche Teilaktion fehlgeschlagen.",
                technicalDetails = processing?.technicalDetails ?: error.stackTraceToString(),
                failingField = processing?.failingField,
                inputPreview = payload.combined.take(2000),
                createdAtEpochMs = System.currentTimeMillis()
            )
            runCatching { repository.saveFailure(report) }
            FailureNotifier.show(appContext, report)
            false
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
        if (profileId == EXTRA_PROFILE_ID) return executeExtra(payload, actionId)
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
