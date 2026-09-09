package cc.stkmn.shareparser

import cc.stkmn.shareparser.data.ActionCondition
import cc.stkmn.shareparser.data.ProcessingAction
import cc.stkmn.shareparser.data.Profile

internal object BuildEdition {
    val isFull: Boolean get() = BuildConfig.FULL_SHARE_TARGETS
    val title: String get() = BuildConfig.EDITION_TITLE

    fun profileRequiresFull(profile: Profile): Boolean {
        if (isFull) return false
        val fullKeys = setOf("target", "target_type")

        if (profile.matchers.any { it.variableKey.lowercase() in fullKeys }) return true
        if (profile.extractors.any { it.sourceVariableKey.lowercase() in fullKeys }) return true
        if (profile.actions.any { actionUsesFullInput(it, fullKeys) }) return true
        return false
    }

    private fun actionUsesFullInput(action: ProcessingAction, fullKeys: Set<String>): Boolean {
        if (conditionUsesFullInput(conditionOf(action), fullKeys)) return true
        return when (action) {
            is ProcessingAction.Calendar -> listOf(
                action.titleTemplate,
                action.descriptionTemplate,
                action.locationTemplate,
                action.startTemplate,
                action.endTemplate,
                action.durationTemplate,
                action.calendarNameTemplate
            ).any(::templateUsesFullInput)
            is ProcessingAction.Url -> templateUsesFullInput(action.urlTemplate)
            is ProcessingAction.Share -> listOf(
                action.textTemplate,
                action.subjectTemplate,
                action.fileNameTemplate,
                action.relativePathTemplate
            ).any(::templateUsesFullInput)
            is ProcessingAction.Target -> templateUsesFullInput(action.targetTemplate)
            is ProcessingAction.Webhook -> listOf(
                action.urlTemplate,
                action.bodyTemplate,
                action.fallbackBody
            ).any(::templateUsesFullInput)
        }
    }

    private fun conditionOf(action: ProcessingAction): ActionCondition? = when (action) {
        is ProcessingAction.Calendar -> action.condition
        is ProcessingAction.Url -> action.condition
        is ProcessingAction.Share -> action.condition
        is ProcessingAction.Target -> action.condition
        is ProcessingAction.Webhook -> action.condition
    }

    private fun conditionUsesFullInput(condition: ActionCondition?, fullKeys: Set<String>): Boolean =
        condition?.clauses?.any { it.variableKey.lowercase() in fullKeys } == true

    private fun templateUsesFullInput(template: String): Boolean =
        Regex("""\{\{\s*target(?:_type)?(?:\||\s*\}\})""", RegexOption.IGNORE_CASE).containsMatchIn(template)
}
