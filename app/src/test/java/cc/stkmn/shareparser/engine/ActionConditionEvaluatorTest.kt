package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ActionCondition
import cc.stkmn.shareparser.data.ActionConditionClause
import cc.stkmn.shareparser.data.ActionConditionMode
import cc.stkmn.shareparser.data.MatcherJoin
import cc.stkmn.shareparser.data.ProcessingAction
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActionConditionEvaluatorTest {
    @Test
    fun supportsAndOrNotAndElse() {
        val first = ProcessingAction.Url(
            id = "if",
            friendlyName = "Online",
            condition = ActionCondition(
                listOf(
                    ActionConditionClause("ort", ActionConditionMode.NOT_EMPTY),
                    ActionConditionClause(
                        variableKey = "status",
                        mode = ActionConditionMode.REGEX,
                        regex = "^online$",
                        join = MatcherJoin.AND,
                        negate = true
                    )
                )
            )
        )
        val otherwise = ProcessingAction.Url(
            id = "else",
            friendlyName = "Fallback",
            elseOfActionId = first.id
        )
        val actions = listOf(first, otherwise)

        val matching = mapOf("ort" to "Berlin", "status" to "vor ort")
        assertTrue(ActionConditionEvaluator.isAvailable(first, actions, matching))
        assertFalse(ActionConditionEvaluator.isAvailable(otherwise, actions, matching))

        val failing = mapOf("ort" to "", "status" to "online")
        assertFalse(ActionConditionEvaluator.isAvailable(first, actions, failing))
        assertTrue(ActionConditionEvaluator.isAvailable(otherwise, actions, failing))
    }
}
