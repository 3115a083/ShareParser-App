package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.ActionChainMode
import cc.stkmn.shareparser.data.ProcessingAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/** A run snapshots the profile. Confirmation belongs to the root invocation only. */
object ActionChainRunner {
    const val MAX_DEPTH = 16
    const val MAX_STEPS = 256
    const val MAX_DELAY_MS = 3_600_000L

    fun validate(root: ProcessingAction, actions: List<ProcessingAction>) {
        val byId = actions.associateBy { it.id }
        require(actions.all { it.id.isNotBlank() } && byId.size == actions.size) {
            "Aktionen benötigen eindeutige, nicht leere IDs."
        }
        var steps = 0
        fun visit(action: ProcessingAction, ancestors: Set<String>) {
            require(++steps <= MAX_STEPS) { "Die Aktionskette enthält zu viele Schritte (maximal $MAX_STEPS)." }
            if (action !is ProcessingAction.Chain) return
            require(action.id !in ancestors) { "Die Aktionskette enthält einen Kreis bei „${action.friendlyName}“." }
            require(ancestors.size < MAX_DEPTH) { "Die Aktionskette ist zu tief verschachtelt (maximal $MAX_DEPTH)." }
            require(action.mode == ActionChainMode.PARALLEL || action.delayMs in 0..MAX_DELAY_MS) { "Die Verzögerung muss zwischen 0 und 3600 Sekunden liegen." }
            require(action.actionIds.distinct().size == action.actionIds.size) { "Eine Aktion darf pro Kette nur einmal enthalten sein." }
            action.actionIds.forEach { id ->
                val child = byId[id] ?: throw IllegalArgumentException("Eine Aktion der Kette „${action.friendlyName}“ fehlt. Bitte die Kette bearbeiten.")
                visit(child, ancestors + action.id)
            }
        }
        visit(root, emptySet())
    }

    fun canAdd(chain: ProcessingAction.Chain, candidate: ProcessingAction, actions: List<ProcessingAction>): Boolean {
        if (candidate.id == chain.id || candidate.id in chain.actionIds) return false
        val updated = chain.copy(actionIds = chain.actionIds + candidate.id)
        return runCatching { validate(updated, actions.map { if (it.id == chain.id) updated else it }) }.isSuccess
    }

    suspend fun run(root: ProcessingAction, actions: List<ProcessingAction>, values: Map<String, String>, executeLeaf: suspend (ProcessingAction) -> Unit) {
        validate(root, actions)
        val byId = actions.associateBy { it.id }
        suspend fun visit(action: ProcessingAction): Unit {
            if (!ActionConditionEvaluator.isAvailable(action, actions, values)) return
            if (action !is ProcessingAction.Chain) { executeLeaf(action); return }
            val children = action.actionIds.map { byId.getValue(it) }
            when (action.mode) {
                ActionChainMode.SEQUENTIAL -> children.forEachIndexed { index, child ->
                    if (index > 0 && action.delayMs > 0) delay(action.delayMs)
                    visit(child)
                }
                ActionChainMode.PARALLEL -> coroutineScope {
                    val results = children.map { child -> async {
                        try { visit(child); Result.success(Unit) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { Result.failure<Unit>(e) }
                    } }.awaitAll()
                    results.firstOrNull { it.isFailure }?.getOrThrow()
                }
            }
        }
        visit(root)
    }
}
