package cc.stkmn.shareparser.engine

import cc.stkmn.shareparser.data.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ActionChainRunnerTest {
    private val a = ProcessingAction.Url("a", "A")
    private val b = ProcessingAction.Share("b", "B")
    private fun chain(vararg ids: String) = ProcessingAction.Chain("root", "Kette", actionIds = ids.toList())
    private val json = Json { classDiscriminator = "type"; encodeDefaults = true }

    @Test fun roundTrip() {
        for (mode in ActionChainMode.entries) {
            val root = chain("b", "a").copy(mode = mode, delayMs = 1250, confirmBeforeAutoRun = false,
                condition = ActionCondition(listOf(ActionConditionClause("text"))), elseOfActionId = "parent",
                showInOverlay = false, showInNotification = false, editorDescription = "Test", icon = "bolt")
            val bundle = ProfileBundle(profile = Profile("p", "Test", actions = listOf(a, b, root)))
            assertEquals(14, bundle.schemaVersion)
            assertEquals(bundle, json.decodeFromString<ProfileBundle>(json.encodeToString(bundle)))
        }
    }
    @Test fun legacyActionsLoadWithSafeDefault() {
        for (schema in 1..13) for (type in listOf("calendar", "url", "share", "target", "webhook")) {
            val input = """{"schemaVersion":$schema,"profile":{"id":"p","name":"Old","actions":[{"type":"$type","id":"a","friendlyName":"A"}]}}"""
            val action = json.decodeFromString<ProfileBundle>(input).profile.actions.single()
            assertTrue(action.confirmBeforeAutoRun)
            assertFalse(ActionRunPolicy.mayRunAutomatically(action))
            assertEquals(action, json.decodeFromString<ProcessingAction>(json.encodeToString(action)))
        }
        val explicit = json.decodeFromString<ProcessingAction>("""{"type":"url","id":"a","friendlyName":"A","confirmBeforeAutoRun":false}""")
        assertTrue(ActionRunPolicy.mayRunAutomatically(explicit))
    }
    @Test fun alwaysWebhookRequiresOptOut() {
        val protected = ProcessingAction.Webhook("w", "W", mode = WebhookMode.ALWAYS)
        assertTrue(ActionRunPolicy.isSelectable(protected))
        assertFalse(ActionRunPolicy.isAutomaticWebhook(protected))
        val auto = protected.copy(confirmBeforeAutoRun = false)
        assertFalse(ActionRunPolicy.isSelectable(auto))
        assertTrue(ActionRunPolicy.isAutomaticWebhook(auto))
        assertTrue(ActionRunPolicy.isSelectable(auto.copy(mode = WebhookMode.ON_SELECTION)))
    }
    @Test fun sequentialOrderAndNonBlockingDelay() = runTest {
        val root = chain("b", "a").copy(delayMs = 1250)
        val seen = mutableListOf<Pair<String, Long>>()
        var responsive = false
        val probe = async { delay(500); responsive = true }
        ActionChainRunner.run(root, listOf(root, a, b), emptyMap()) {
            seen += it.id to currentTime
            if (it.id == "a") assertTrue(responsive)
        }
        probe.await()
        assertEquals(listOf("b" to 0L, "a" to 1250L), seen)
    }
    @Test fun parallelStartsBothAndIgnoresDelay() = runTest {
        val root = chain("a", "b").copy(mode = ActionChainMode.PARALLEL, delayMs = 9999)
        val startedA = CompletableDeferred<Unit>(); val startedB = CompletableDeferred<Unit>()
        ActionChainRunner.run(root, listOf(root, a, b), emptyMap()) {
            if (it.id == "a") { startedA.complete(Unit); startedB.await() }
            else { startedB.complete(Unit); startedA.await() }
            delay(100)
        }
        assertEquals(100L, currentTime)
    }
    @Test fun emptyChain() = runTest {
        val root = chain()
        ActionChainRunner.run(root, listOf(root), emptyMap()) { fail("Unexpected child") }
    }
    @Test fun missingIdFailsBeforeSideEffects() = runTest {
        val root = chain("a", "missing"); var calls = 0
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.run(root, listOf(root, a), emptyMap()) { calls++ } }
        assertEquals(0, calls)
    }
    @Test fun directAndIndirectCycles() = runTest {
        val direct = chain("root")
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.run(direct, listOf(direct), emptyMap()) {} }
        val root = chain("nested")
        val nested = ProcessingAction.Chain("nested", "Nested", actionIds = listOf("root"))
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.run(root, listOf(root, nested), emptyMap()) {} }
        assertFalse(ActionChainRunner.canAdd(chain(), chain(), listOf(chain())))
        assertFalse(ActionChainRunner.canAdd(chain(), nested, listOf(chain(), nested)))
    }
    @Test fun maximumDepth() = runTest {
        fun nested(n: Int): List<ProcessingAction> = (0 until n).map {
            ProcessingAction.Chain("c$it", "C$it", actionIds = listOf(if (it == n-1) "a" else "c${it+1}"))
        } + a
        val allowed = nested(ActionChainRunner.MAX_DEPTH); var calls = 0
        ActionChainRunner.run(allowed.first(), allowed, emptyMap()) { calls++ }
        assertEquals(1, calls)
        val forbidden = nested(ActionChainRunner.MAX_DEPTH+1)
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.run(forbidden.first(), forbidden, emptyMap()) {} }
    }
    @Test fun conditionsAndElseForChainsAndChildren() = runTest {
        val condition = ActionCondition(listOf(ActionConditionClause("value")))
        val root = chain("a").copy(condition = condition)
        val otherwise = ProcessingAction.Chain("else", "Else", elseOfActionId = root.id, actionIds = listOf("b"))
        val actions = listOf(root, otherwise, a, b); val seen = mutableListOf<String>()
        ActionChainRunner.run(root, actions, emptyMap()) { seen += it.id }
        ActionChainRunner.run(otherwise, actions, emptyMap()) { seen += it.id }
        assertEquals(listOf("b"), seen); seen.clear()
        ActionChainRunner.run(root, actions, mapOf("value" to "yes")) { seen += it.id }
        ActionChainRunner.run(otherwise, actions, mapOf("value" to "yes")) { seen += it.id }
        assertEquals(listOf("a"), seen); seen.clear()
        ActionChainRunner.run(chain("a"), listOf(chain("a"), a.copy(condition = condition)), emptyMap()) { seen += it.id }
        assertTrue(seen.isEmpty())
    }
    @Test fun confirmedChainRunsProtectedChildrenAndExistingTypes() = runTest {
        val leaves = listOf(a, b, ProcessingAction.Calendar("c", "C"), ProcessingAction.Target("t", "T"), ProcessingAction.Webhook("w", "W"))
        assertTrue(leaves.all { it.confirmBeforeAutoRun })
        val root = chain(*leaves.map { it.id }.toTypedArray()); val seen = mutableListOf<ProcessingAction>()
        ActionChainRunner.run(root, leaves + root, emptyMap()) { seen += it }
        assertEquals(leaves, seen)
    }
    @Test fun invalidDelayDuplicatesAndExpansion() {
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.validate(chain("a", "a"), listOf(chain(), a)) }
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.validate(chain().copy(delayMs = -1), listOf(chain())) }
        val leaves = (0..ActionChainRunner.MAX_STEPS).map { ProcessingAction.Url("u$it", "U") }
        val root = chain(*leaves.map { it.id }.toTypedArray())
        assertFailsWith<IllegalArgumentException> { ActionChainRunner.validate(root, leaves + root) }
    }
    @Test fun sequentialStopsAndParallelAwaitsStartedChildrenOnError() = runTest {
        var secondRan = false; val root = chain("a", "b")
        assertFailsWith<IllegalStateException> { ActionChainRunner.run(root, listOf(root, a, b), emptyMap()) {
            if (it.id == "a") error("failure") else secondRan = true
        } }
        assertFalse(secondRan)
        val parallel = root.copy(mode = ActionChainMode.PARALLEL)
        assertFailsWith<IllegalStateException> { ActionChainRunner.run(parallel, listOf(parallel, a, b), emptyMap()) {
            if (it.id == "a") error("failure") else { delay(100); secondRan = true }
        } }
        assertTrue(secondRan)
    }
}
