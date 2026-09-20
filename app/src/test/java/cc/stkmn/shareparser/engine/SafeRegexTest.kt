package cc.stkmn.shareparser.engine

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class SafeRegexTest {
    @Test fun supportsLookbehindBackreferencesAndCaptureRanges() {
        val regex = SafeRegex("(?<=code:)([A-Z]+)-\\1")
        val match = regex.find("code:AB-AB")!!
        assertEquals("AB-AB", match.value); assertEquals("AB", match.groups[1]?.value)
        assertEquals(5..6, match.groups[1]?.range)
        assertEquals(listOf("AB-AB", "AB"), match.groupValues)
        assertFalse(regex.containsMatchIn("code:AB-CD"))
        assertEquals("2", SafeRegex("(\\d)").lastMatch("1 2")?.groups?.get(1)?.value)
    }
    @Test fun replacementAndMatchesAreMaterialized() {
        assertEquals("BA", SafeRegex("(A)(B)").replace("AB", "$2$1"))
        assertEquals(listOf("1", "2"), SafeRegex("\\d").findAll("1 2 3", limit = 2).map { it.value })
        assertFailsWith<IllegalArgumentException> { SafeRegex("[") }
    }
    @Test fun workerThreadAndExceptionPropagation() {
        val work = BoundedWork("test-work", timeoutMs = 2000)
        assertNotEquals(Thread.currentThread().id, work.run { Thread.currentThread().id })
        assertFailsWith<IllegalArgumentException> { work.run { throw IllegalArgumentException("invalid") } }
    }
    @Test fun nativeWorkIgnoringInterruptionRemainsBounded() {
        val work = BoundedWork("test-bounded", workers = 1, timeoutMs = 200)
        val release = CountDownLatch(1); val finished = CountDownLatch(1); val starts = AtomicInteger()
        try {
            assertFailsWith<WorkLimitException> { work.run {
                starts.incrementAndGet()
                while (release.count > 0) { try { release.await() } catch (_: InterruptedException) {} }
                finished.countDown()
            } }
            repeat(3) { assertFailsWith<WorkLimitException> { work.run { starts.incrementAndGet() } } }
            assertEquals(1, starts.get())
        } finally { release.countDown() }
        assertTrue(finished.await(2, TimeUnit.SECONDS))
    }
}
