package cc.stkmn.shareparser.notify

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

object UserNotice {
    fun showLong(context: Context, message: String) {
        if (message.isBlank()) return
        val appContext = context.applicationContext
        runCatching { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { Toast.makeText(appContext, message, Toast.LENGTH_LONG).show() }
        }, 3_200L)
    }
}
