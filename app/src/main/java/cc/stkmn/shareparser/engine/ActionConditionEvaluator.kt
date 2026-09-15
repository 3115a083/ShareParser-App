package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ActionCondition
import cc.stkmn.shareparser.data.ActionConditionClause
import cc.stkmn.shareparser.data.ActionConditionMode
import cc.stkmn.shareparser.data.MatcherJoin
import cc.stkmn.shareparser.data.ProcessingAction

object ActionConditionEvaluator {
    fun condition(action: ProcessingAction): ActionCondition? = when (action) {
        is ProcessingAction.Calendar -> action.condition
        is ProcessingAction.Url -> action.condition
        is ProcessingAction.Share -> action.condition
        is ProcessingAction.Target -> action.condition
        is ProcessingAction.Webhook -> action.condition
    }

    fun elseOf(action: ProcessingAction): String = when (action) {
        is ProcessingAction.Calendar -> action.elseOfActionId
        is ProcessingAction.Url -> action.elseOfActionId
        is ProcessingAction.Share -> action.elseOfActionId
        is ProcessingAction.Target -> action.elseOfActionId
        is ProcessingAction.Webhook -> action.elseOfActionId
    }

    fun isAvailable(
        action: ProcessingAction,
        allActions: List<ProcessingAction>,
        values: Map<String, String>
    ): Boolean {
        val parentId = elseOf(action)
        if (parentId.isNotBlank()) {
            val parent = allActions.firstOrNull { it.id == parentId } ?: return false
            val parentCondition = condition(parent) ?: return false
            if (evaluate(parentCondition, values)) return false
        }
        val own = condition(action) ?: return true
        return evaluate(own, values)
    }

    fun evaluate(condition: ActionCondition, values: Map<String, String>): Boolean {
        if (condition.clauses.isEmpty()) return true
        val normalized = values.mapKeys { it.key.lowercase() }
        val results = condition.clauses.map { clause ->
            val value = normalized[clause.variableKey.lowercase()].orEmpty()
            val base = when (clause.mode) {
                ActionConditionMode.EMPTY -> value.isBlank()
                ActionConditionMode.NOT_EMPTY -> value.isNotBlank()
                ActionConditionMode.REGEX -> runCatching {
                    Regex(clause.regex, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
                        .containsMatchIn(value)
                }.getOrDefault(false)
            }
            if (clause.negate) !base else base
        }
        var combined = results.first()
        for (index in 1 until results.size) {
            combined = when (condition.clauses[index].join) {
                MatcherJoin.AND -> combined && results[index]
                MatcherJoin.OR -> combined || results[index]
            }
        }
        return combined
    }

    fun withCondition(action: ProcessingAction, value: ActionCondition?): ProcessingAction = when (action) {
        is ProcessingAction.Calendar -> action.copy(condition = value)
        is ProcessingAction.Url -> action.copy(condition = value)
        is ProcessingAction.Share -> action.copy(condition = value)
        is ProcessingAction.Target -> action.copy(condition = value)
        is ProcessingAction.Webhook -> action.copy(condition = value)
    }

    fun withElseOf(action: ProcessingAction, actionId: String): ProcessingAction = when (action) {
        is ProcessingAction.Calendar -> action.copy(elseOfActionId = actionId)
        is ProcessingAction.Url -> action.copy(elseOfActionId = actionId)
        is ProcessingAction.Share -> action.copy(elseOfActionId = actionId)
        is ProcessingAction.Target -> action.copy(elseOfActionId = actionId)
        is ProcessingAction.Webhook -> action.copy(elseOfActionId = actionId)
    }
}
