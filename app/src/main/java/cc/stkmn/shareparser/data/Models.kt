package cc.stkmn.shareparser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProfileBundle(
    val schemaVersion: Int = 10,
    val profile: Profile
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matchers: List<MatcherRule> = emptyList(),
    val extractors: List<ExtractorRule> = emptyList(),
    val actions: List<ProcessingAction> = emptyList(),
    val parseDirection: ParseDirection = ParseDirection.TOP_DOWN
)

@Serializable
enum class ParseDirection {
    TOP_DOWN,
    BOTTOM_UP
}

@Serializable
enum class MatcherJoin {
    AND,
    OR
}

@Serializable
enum class MatcherValueMode {
    REGEX,
    EMPTY,
    NOT_EMPTY
}

@Serializable
data class MatcherRule(
    val regex: String,
    val ignoreCase: Boolean = true,
    val friendlyText: String = "",
    val variableKey: String = "",
    val join: MatcherJoin = MatcherJoin.AND,
    val valueMode: MatcherValueMode = MatcherValueMode.REGEX,
    val negate: Boolean = false
)

@Serializable
enum class InputSource {
    COMBINED,
    TEXT,
    SUBJECT,
    LINKS
}

@Serializable
data class ExtractorRule(
    val key: String,
    val regex: String,
    val group: Int = 1,
    val required: Boolean = false,
    val source: InputSource = InputSource.COMBINED,
    val sourceVariableKey: String = "",
    val transforms: List<ValueTransform> = listOf(ValueTransform.Trim),
    val id: String = UUID.randomUUID().toString(),
    val sampleLabel: String = ""
)

@Serializable
sealed class ValueTransform {
    @Serializable
    @SerialName("trim")
    data object Trim : ValueTransform()

    @Serializable
    @SerialName("regex_replace")
    data class RegexReplace(
        val regex: String,
        val replacement: String = "",
        val ignoreCase: Boolean = false,
        val literal: Boolean = true
    ) : ValueTransform()

    @Serializable
    @SerialName("prefix")
    data class Prefix(val value: String) : ValueTransform()

    @Serializable
    @SerialName("suffix")
    data class Suffix(val value: String) : ValueTransform()

    @Serializable
    @SerialName("case")
    data class ChangeCase(val mode: CaseMode) : ValueTransform()
}

@Serializable
enum class CaseMode {
    LOWER,
    UPPER
}

@Serializable
enum class UrlOpenMode {
    BROWSER,
    WEBVIEW
}

@Serializable
enum class CalendarTargetMode {
    APP_EDITOR,
    DIRECT_SAVE
}

@Serializable
enum class DateTimeLocale {
    DE_DE,
    EN_US,
    EN_GB,
    ISO,
    SYSTEM
}

@Serializable
enum class AppLanguage {
    SYSTEM,
    DE,
    EN
}

@Serializable
enum class AppearanceMode {
    SYSTEM,
    LIGHT,
    DARK
}

@Serializable
enum class ColorPalette {
    MATERIAL_YOU,
    OCEAN,
    FOREST,
    SLATE,
    AMBER
}

@Serializable
enum class EmptyValuePolicy {
    FALLBACK,
    ERROR
}

@Serializable
enum class WebhookMode {
    ON_SELECTION,
    ALWAYS
}

@Serializable
enum class ShareSelectionMode {
    APP,
    OVERLAY,
    NOTIFICATION
}

@Serializable
enum class TextFileMode {
    SHARE,
    OPEN,
    SAVE
}


@Serializable
enum class LauncherIcon {
    LOGO_1,
    LOGO_2,
    LOGO_3,
    LOGO_4,
    LOGO_5,
    LOGO_6
}

@Serializable
data class AppSettings(
    val dateTimeLocale: DateTimeLocale = DateTimeLocale.SYSTEM,
    val shareSelectionMode: ShareSelectionMode = ShareSelectionMode.APP,
    val launcherIcon: LauncherIcon = LauncherIcon.LOGO_3,
    val defaultSaveTreeUri: String = "",
    val appLanguage: AppLanguage = AppLanguage.SYSTEM,
    val appearanceMode: AppearanceMode = AppearanceMode.SYSTEM,
    val colorPalette: ColorPalette = ColorPalette.MATERIAL_YOU
)

@Serializable
sealed class ProcessingAction {
    abstract val id: String
    abstract val friendlyName: String
    abstract val icon: String

    @Serializable
    @SerialName("calendar")
    data class Calendar(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "event",
        val showInOverlay: Boolean = true,
        val showInNotification: Boolean = true,
        val titleTemplate: String = "{{subject}}",
        val descriptionTemplate: String = "{{text}}",
        val locationTemplate: String = "",
        val startTemplate: String = "",
        val endTemplate: String = "",
        val durationTemplate: String = "",
        val startPattern: String = "",
        val endPattern: String = "",
        val allDay: Boolean = false,
        val calendarNameTemplate: String = "",
        val calendarId: Long? = null,
        val targetMode: CalendarTargetMode = CalendarTargetMode.APP_EDITOR
    ) : ProcessingAction()

    @Serializable
    @SerialName("url")
    data class Url(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "link",
        val showInOverlay: Boolean = true,
        val showInNotification: Boolean = true,
        val urlTemplate: String = "https://example.com/?q={{input|url}}",
        val openMode: UrlOpenMode = UrlOpenMode.BROWSER
    ) : ProcessingAction()

    @Serializable
    @SerialName("share")
    data class Share(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "share",
        val showInOverlay: Boolean = true,
        val showInNotification: Boolean = true,
        val textTemplate: String = "{{text}}",
        val subjectTemplate: String = "{{subject}}",
        val mimeType: String = "text/plain",
        val fileExtension: String = "",
        val asFile: Boolean = false,
        val fileMode: TextFileMode = TextFileMode.SHARE,
        val fileNameTemplate: String = "ShareParser.txt",
        val relativePathTemplate: String = "",
        val emptyValuePolicy: EmptyValuePolicy = EmptyValuePolicy.FALLBACK,
        val fallbackFileName: String = "ShareParser.txt",
        val fallbackPath: String = ""
    ) : ProcessingAction()

    @Serializable
    @SerialName("webhook")
    data class Webhook(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "send",
        val showInOverlay: Boolean = true,
        val showInNotification: Boolean = true,
        val urlTemplate: String = "",
        val bodyTemplate: String = """{"text":"{{text}}","subject":"{{subject}}"}""",
        val contentType: String = "application/json; charset=utf-8",
        val mode: WebhookMode = WebhookMode.ON_SELECTION,
        val emptyValuePolicy: EmptyValuePolicy = EmptyValuePolicy.ERROR,
        val fallbackBody: String = "{}"
    ) : ProcessingAction()
}

@Serializable
data class SharedPayload(
    val text: String,
    val subject: String = "",
    val mimeType: String = "text/plain",
    val sourcePackage: String = "",
    val sourceApp: String = "",
    val fileName: String = "",
    val linkTargets: List<String> = emptyList()
) {
    val combined: String
        get() = buildString {
            if (subject.isNotBlank()) append(subject.trim()).append("\n")
            append(text)
        }.trim()
}

@Serializable
data class PendingShare(
    val id: String,
    val payload: SharedPayload,
    val createdAtEpochMs: Long
)

@Serializable
data class FailureReport(
    val id: String,
    val profileId: String?,
    val profileName: String?,
    val actionId: String?,
    val message: String,
    val technicalDetails: String,
    val failingField: String? = null,
    val inputPreview: String,
    val createdAtEpochMs: Long
)
