package cc.stkmn.shareparser.engine

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Limits abandoned work too: native providers/regex need not honor interruption. */
class BoundedWork(name: String, workers: Int = 2, private val timeoutMs: Long = 1000) {
    private val pool = ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
        ArrayBlockingQueue(4), { task -> Thread(task, name).apply { isDaemon = true } }, ThreadPoolExecutor.AbortPolicy())
        .apply { allowCoreThreadTimeOut(true) }
    fun <T> run(block: () -> T): T {
        val future = try { pool.submit(Callable(block)) } catch (_: java.util.concurrent.RejectedExecutionException) {
            throw WorkLimitException("Die Verarbeitung ist ausgelastet. Bitte die Eingabe oder Regel prüfen und erneut versuchen.", "input", "Bounded worker capacity reached")
        }
        try { return future.get(timeoutMs, TimeUnit.MILLISECONDS) }
        catch (_: TimeoutException) {
            throw WorkLimitException("Die Verarbeitung dauert zu lange. Bitte die Eingabe oder den regulären Ausdruck vereinfachen.", "input", "Work exceeded ${timeoutMs}ms")
        } catch (e: ExecutionException) {
            val cause = e.cause
            if (cause is StackOverflowError) throw WorkLimitException("Der reguläre Ausdruck ist zu aufwendig. Bitte die Regel vereinfachen.", "regex", "Regex stack limit reached")
            if (cause is Exception) throw cause
            throw e
        } finally { future.cancel(true); pool.purge() }
    }
}
class WorkLimitException(message: String, field: String, details: String) : ProcessingException(message, field, details)
