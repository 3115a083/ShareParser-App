package cc.stkmn.shareparser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileBundle(
    val schemaVersion: Int = 2,
    val profile: Profile
)

@Serializable
data class Profile(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val matchers: List<MatcherRule> = emptyList(),
    val extractors: List<ExtractorRule> = emptyList(),
    val actions: List<ProcessingAction> = emptyList()
)

@Serializable
data class MatcherRule(
    val regex: String,
    val ignoreCase: Boolean = true
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
    val transforms: List<ValueTransform> = listOf(ValueTransform.Trim)
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
        val ignoreCase: Boolean = false
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
        val startPattern: String = "",
        val endPattern: String = "",
        val allDay: Boolean = false
    ) : ProcessingAction()

    @Serializable
    @SerialName("url")
    data class Url(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "link",
        val urlTemplate: String = "https://example.com/?q={{input|url}}"
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

data class SharedPayload(
    val text: String,
    val subject: String = "",
    val mimeType: String = "text/plain"
) {
    val combined: String
        get() = buildString {
            if (subject.isNotBlank()) append(subject.trim()).append("\n")
            append(text)
        }.trim()
}

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
