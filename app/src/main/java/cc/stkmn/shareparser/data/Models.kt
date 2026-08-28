package cc.stkmn.shareparser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProfileBundle(
    val schemaVersion: Int = 6,
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
data class MatcherRule(
    val regex: String,
    val ignoreCase: Boolean = true,
    val friendlyText: String = "",
    val variableKey: String = ""
)

@Serializable
enum class InputSource {
    COMBINED,
    TEXT,
    SUBJECT
}

@Serializable
data class ExtractorRule(
    val key: String,
    val regex: String,
    val group: Int = 1,
    val required: Boolean = false,
    val source: InputSource = InputSource.COMBINED,
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
enum class ShareSelectionMode {
    APP,
    OVERLAY,
    NOTIFICATION
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
    val launcherIcon: LauncherIcon = LauncherIcon.LOGO_5
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
        val urlTemplate: String = "https://example.com/?q={{input|url}}",
        val openMode: UrlOpenMode = UrlOpenMode.BROWSER
    ) : ProcessingAction()

    @Serializable
    @SerialName("share")
    data class Share(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "share",
        val textTemplate: String = "{{text}}",
        val subjectTemplate: String = "{{subject}}",
        val mimeType: String = "text/plain"
    ) : ProcessingAction()
}

@Serializable
data class SharedPayload(
    val text: String,
    val subject: String = "",
    val mimeType: String = "text/plain",
    val sourcePackage: String = "",
    val sourceApp: String = ""
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
