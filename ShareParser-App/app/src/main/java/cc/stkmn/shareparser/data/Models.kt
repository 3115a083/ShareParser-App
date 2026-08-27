package cc.stkmn.shareparser.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileBundle(
    val schemaVersion: Int = 1,
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
data class ExtractorRule(
    val key: String,
    val regex: String,
    val group: Int = 1,
    val required: Boolean = false,
    val trim: Boolean = true
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
        val titleTemplate: String,
        val descriptionTemplate: String = "",
        val locationTemplate: String = "",
        val startTemplate: String = "",
        val endTemplate: String = ""
    ) : ProcessingAction()

    @Serializable
    @SerialName("url")
    data class Url(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "link",
        val urlTemplate: String
    ) : ProcessingAction()

    @Serializable
    @SerialName("share")
    data class Share(
        override val id: String,
        override val friendlyName: String,
        override val icon: String = "share",
        val textTemplate: String,
        val mimeType: String = "text/plain"
    ) : ProcessingAction()
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
