package cc.stkmn.shareparser

import android.content.Context
import android.os.Build
import android.os.Process
import cc.stkmn.shareparser.data.FailureReport
import cc.stkmn.shareparser.data.ProfileRepository
import java.io.File
import java.util.UUID
import kotlin.system.exitProcess

/**
 * Local-only last-resort crash recorder.
 *
 * Nothing is transmitted. The report and pending marker live in the app's
 * private files directory and are removed with the app on uninstall.
 */
object CrashRecorder {
    private const val PENDING_FILE = "crash_pending"
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val appContext = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching {
                    val details = buildString {
                        appendLine("Uncaught ${error::class.java.name}: ${error.message.orEmpty()}")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Android API: ${Build.VERSION.SDK_INT}")
                        appendLine("Release: ${Build.VERSION.RELEASE}")
                        appendLine("Manufacturer: ${Build.MANUFACTURER}")
                        appendLine("Model: ${Build.MODEL}")
                        appendLine()
                        append(error.stackTraceToString())
                    }
                    ProfileRepository(appContext).saveFailure(
                        FailureReport(
                            id = UUID.randomUUID().toString(),
                            profileId = null,
                            profileName = null,
                            actionId = null,
                            message = "ShareParser wurde unerwartet beendet.",
                            technicalDetails = details,
                            failingField = "app_crash",
                            inputPreview = "",
                            createdAtEpochMs = System.currentTimeMillis()
                        )
                    )
                    File(appContext.filesDir, PENDING_FILE).writeText("1")
                }

                if (previous != null) {
                    previous.uncaughtException(thread, error)
                } else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
            installed = true
        }
    }

    fun consumePending(context: Context): Boolean {
        val marker = File(context.applicationContext.filesDir, PENDING_FILE)
        if (!marker.exists()) return false
        return runCatching {
            marker.delete()
            true
        }.getOrDefault(false)
    }
}
