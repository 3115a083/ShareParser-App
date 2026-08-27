package cc.stkmn.shareparser.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.R

object WarningNotifier {
    private const val CHANNEL = "processing_warnings"

    fun show(context: Context, warnings: List<String>) {
        if (warnings.isEmpty()) return
        val message = warnings.joinToString(" ")
        runCatching { Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        runCatching {
            createChannel(context)
            val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ShareParser: Bitte Angaben prüfen")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(true)
                .setTimeoutAfter(20_000)
                .build()
            NotificationManagerCompat.from(context.applicationContext).notify(message.hashCode(), notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Hinweise zur Verarbeitung",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Stumme Hinweise zu Angaben, die manuell ergänzt werden müssen"
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
        }
    }
}
