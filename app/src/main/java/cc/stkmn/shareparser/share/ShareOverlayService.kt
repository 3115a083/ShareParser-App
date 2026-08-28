package cc.stkmn.shareparser.share

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cc.stkmn.shareparser.data.PendingShareStore

class ShareOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: View? = null
    private var windowManager: WindowManager? = null
    private var pendingId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_PENDING_ID).orEmpty()
        if (id.isBlank() || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        pendingId = id
        show(id)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ dismiss(removePending = true) }, 60_000L)
        return START_NOT_STICKY
    }

    private fun show(id: String) {
        removeOverlayOnly()
        val pending = PendingShareStore(this).get(id) ?: run {
            stopSelf()
            return
        }
        val coordinator = ShareCoordinator(this)
        val choices = coordinator.choices(pending.payload)
        if (choices.isEmpty()) {
            stopSelf()
            return
        }
        val multipleProfiles = choices.map { it.profileId }.distinct().size > 1

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 28)
            setBackgroundColor(0xFFF7F7F7.toInt())
        }
        content.addView(TextView(this).apply {
            text = "ShareParser"
            textSize = 20f
            setTextColor(0xFF111111.toInt())
        })
        content.addView(TextView(this).apply {
            text = "Weiterverarbeitung auswählen"
            textSize = 14f
            setTextColor(0xFF444444.toInt())
            setPadding(0, 4, 0, 16)
        })
        choices.take(12).forEach { choice ->
            content.addView(Button(this).apply {
                text = choice.label(multipleProfiles)
                isAllCaps = false
                setOnClickListener {
                    coordinator.executePending(id, choice.profileId, choice.actionId)
                    dismiss(removePending = false)
                }
            })
        }
        content.addView(Button(this).apply {
            text = "Abbrechen"
            isAllCaps = false
            setOnClickListener { dismiss(removePending = true) }
        })

        val root = ScrollView(this).apply { addView(content) }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (72 * resources.displayMetrics.density).toInt()
        }
        root.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) dismiss(removePending = true)
            false
        }

        windowManager = getSystemService(WindowManager::class.java)
        runCatching { windowManager?.addView(root, params) }
            .onSuccess { overlay = root }
            .onFailure {
                PendingShareStore(this).remove(id)
                stopSelf()
            }
    }

    private fun removeOverlayOnly() {
        overlay?.let { view -> runCatching { windowManager?.removeView(view) } }
        overlay = null
    }

    private fun dismiss(removePending: Boolean) {
        handler.removeCallbacksAndMessages(null)
        removeOverlayOnly()
        if (removePending) pendingId?.let { PendingShareStore(this).remove(it) }
        pendingId = null
        stopSelf()
    }

    override fun onDestroy() {
        removeOverlayOnly()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PENDING_ID = "pending_id"
    }
}
