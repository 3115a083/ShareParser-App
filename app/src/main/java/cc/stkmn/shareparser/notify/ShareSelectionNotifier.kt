package cc.stkmn.shareparser.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cc.stkmn.shareparser.ActionDispatchActivity
import cc.stkmn.shareparser.MainActivity
import cc.stkmn.shareparser.R
import cc.stkmn.shareparser.share.ShareCoordinator

object ShareSelectionNotifier {
    const val CHANNEL = "share_selection"
    private const val TIMEOUT_MS = 60_000L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Auswahl beim Teilen",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Auswahl von Profil und Weiterverarbeitung für geteilte Inhalte"
                    enableVibration(true)
                }
            )
        }
    }

    fun show(context: Context, pendingId: String, choices: List<ShareCoordinator.Choice>): Boolean {
        if (choices.isEmpty()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false

        return runCatching {
            ensureChannel(context)
            val multipleProfiles = choices.map { it.profileId }.distinct().size > 1
            val openPicker = Intent(context.applicationContext, MainActivity::class.java).apply {
                action = MainActivity.ACTION_OPEN_PENDING_SHARE
                putExtra(MainActivity.EXTRA_PENDING_SHARE_ID, pendingId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val contentIntent = PendingIntent.getActivity(
                context.applicationContext,
                pendingId.hashCode(),
                openPicker,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context.applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("ShareParser: Weiterverarbeitung auswählen")
                .setContentText(
                    when {
                        choices.size == 1 -> choices.first().label(multipleProfiles)
                        choices.size <= 3 -> "${choices.size} Möglichkeiten verfügbar"
                        else -> "3 Schnellaktionen, weitere in ShareParser"
                    }
                )
                .setStyle(NotificationCompat.BigTextStyle().bigText(choices.take(3).joinToString("\n") { it.label(multipleProfiles) }))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(TIMEOUT_MS)
                .setContentIntent(contentIntent)

            choices.take(3).forEachIndexed { index, choice ->
                val actionIntent = Intent(context.applicationContext, ActionDispatchActivity::class.java).apply {
                    putExtra(ActionDispatchActivity.EXTRA_PENDING_ID, pendingId)
                    putExtra(ActionDispatchActivity.EXTRA_PROFILE_ID, choice.profileId)
                    putExtra(ActionDispatchActivity.EXTRA_ACTION_ID, choice.actionId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pending = PendingIntent.getActivity(
                    context.applicationContext,
                    pendingId.hashCode() * 31 + index,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, choice.label(multipleProfiles).take(28), pending)
            }

            NotificationManagerCompat.from(context.applicationContext)
                .notify(pendingId.hashCode(), builder.build())
            true
        }.getOrDefault(false)
    }

    fun cancel(context: Context, pendingId: String) {
        runCatching { NotificationManagerCompat.from(context.applicationContext).cancel(pendingId.hashCode()) }
    }
}
