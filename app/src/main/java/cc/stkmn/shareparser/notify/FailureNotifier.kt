package cc.stkmn.shareparser.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.MainActivity
import cc.stkmn.shareparser.R
import cc.stkmn.shareparser.data.FailureReport

object FailureNotifier {
    private const val CHANNEL = "processing_errors"

    fun show(context: Context, report: FailureReport) {
        UserNotice.showLong(context, report.message)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        runCatching {
            createChannel(context)
            val details = Intent(context.applicationContext, MainActivity::class.java).apply {
                data = Uri.parse("shareparser://failure/${report.id}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(
                context.applicationContext,
                report.id.hashCode(),
                details,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ShareParser: Verarbeitung fehlgeschlagen")
                .setContentText(report.message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(true)
                .setTimeoutAfter(20_000)
                .setContentIntent(pending)
                .build()

            NotificationManagerCompat.from(context.applicationContext).notify(report.id.hashCode(), notification)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL,
                        "Verarbeitungsfehler",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "Stumme, kurzzeitige Fehlerhinweise"
                        setSound(null, null)
                        enableVibration(false)
                    }
                )
            }
        }
    }
}
