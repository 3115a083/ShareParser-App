package cc.stkmn.shareparser.share

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import cc.stkmn.shareparser.AppArtwork
import cc.stkmn.shareparser.AppLocale
import cc.stkmn.shareparser.MainActivity
import cc.stkmn.shareparser.data.PendingShareStore
import cc.stkmn.shareparser.data.ShareSelectionMode

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
        val choices = coordinator.choices(pending.payload, ShareSelectionMode.OVERLAY)
        if (choices.isEmpty()) {
            stopSelf()
            return
        }
        val multipleProfiles = choices.map { it.profileId }.distinct().size > 1
        val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val surface = if (dark) 0xFF1B1B1F.toInt() else 0xFFFDFBFF.toInt()
        val onSurface = if (dark) 0xFFE6E1E5.toInt() else 0xFF1C1B1F.toInt()
        val onSurfaceVariant = if (dark) 0xFFCAC4D0.toInt() else 0xFF49454F.toInt()
        val primary = if (dark) 0xFFB4C5FF.toInt() else 0xFF315DA8.toInt()
        val onPrimary = if (dark) 0xFF002E69.toInt() else Color.WHITE
        val outline = if (dark) 0xFF938F99.toInt() else 0xFF79747E.toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = roundedBackground(surface, 20f)
            elevation = dp(18).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(this).apply {
            AppArtwork.loadBitmap(this@ShareOverlayService, AppArtwork.FOREGROUND_ASSET)?.let { setImageBitmap(it) }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }, LinearLayout.LayoutParams(dp(66), dp(66)).apply { marginEnd = dp(12) })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ShareOverlayService).apply {
                text = "ShareParser"
                textSize = 20f
                setTextColor(onSurface)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(this@ShareOverlayService).apply {
                text = AppLocale.text("Weiterverarbeitung auswählen", "Select processing action")
                textSize = 13f
                setTextColor(onSurfaceVariant)
                setPadding(0, dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        content.addView(header)

        content.addView(TextView(this).apply {
            text = pending.payload.subject.ifBlank { pending.payload.fileName }.take(80)
            textSize = 12f
            setTextColor(onSurfaceVariant)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            setPadding(0, dp(12), 0, dp(4))
        })

        choices.take(4).forEach { choice ->
            content.addView(Button(this).apply {
                text = choice.label(multipleProfiles)
                isAllCaps = false
                textSize = 15f
                setTextColor(onPrimary)
                background = roundedBackground(primary, 12f)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                setOnClickListener {
                    coordinator.executePending(id, choice.profileId, choice.actionId)
                    dismiss(removePending = false)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
        }
        if (choices.size > 4) {
            content.addView(Button(this).apply {
                text = AppLocale.text("Alle Möglichkeiten anzeigen", "Show all options")
                isAllCaps = false
                textSize = 14f
                setTextColor(primary)
                background = roundedStrokeBackground(Color.TRANSPARENT, outline, 12f)
                setOnClickListener {
                    startActivity(
                        Intent(this@ShareOverlayService, MainActivity::class.java).apply {
                            action = MainActivity.ACTION_OPEN_PENDING_SHARE
                            putExtra(MainActivity.EXTRA_PENDING_SHARE_ID, id)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                    )
                    dismiss(removePending = false)
                }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(10)
            })
        }

        content.addView(Button(this).apply {
            text = AppLocale.text("Abbrechen", "Cancel")
            isAllCaps = false
            textSize = 14f
            setTextColor(onSurface)
            background = roundedStrokeBackground(Color.TRANSPARENT, outline, 12f)
            setOnClickListener { dismiss(removePending = true) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val root = ScrollView(this).apply {
            clipToOutline = false
            addView(content)
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.86f).toInt().coerceAtMost(dp(520)),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            dimAmount = 0.28f
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

    private fun roundedBackground(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun roundedStrokeBackground(fill: Int, stroke: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(dp(1), stroke)
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
