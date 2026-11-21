package ai.bozer.app

import android.animation.ValueAnimator
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import android.content.Intent
import android.content.SharedPreferences
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator

/**
 * Draggable BOZER entry point. Its animated triple-spiral emblem matches the
 * in-app identity and never displays a letter inside the mark.
 */
class BozerFloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var spiralBubble: BozerSpiralBubbleView? = null
    private lateinit var preferences: SharedPreferences
    private lateinit var layoutParams: WindowManager.LayoutParams

    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
        if (key == "animations_enabled") {
            spiralBubble?.setAnimationsEnabled(shared.getBoolean("animations_enabled", true))
        }
    }

    private val bubbleVisibilityReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_HIDE_BUBBLE -> bubble?.visibility = View.GONE
                ACTION_SHOW_BUBBLE -> bubble?.visibility = View.VISIBLE
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        preferences = getSharedPreferences("bozer_settings", MODE_PRIVATE)
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        val visibilityFilter = IntentFilter().apply {
            addAction(ACTION_HIDE_BUBBLE)
            addAction(ACTION_SHOW_BUBBLE)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(bubbleVisibilityReceiver, visibilityFilter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bubbleVisibilityReceiver, visibilityFilter)
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = dp(58)
        layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(220)
        }

        val view = BozerSpiralBubbleView(this).apply {
            elevation = dp(12).toFloat()
            visibility = if (MainActivity.isVisible) View.GONE else View.VISIBLE
        }
        view.setOnTouchListener(createDragListener(view))
        spiralBubble = view
        bubble = view
        try {
            windowManager?.addView(view, layoutParams)
            isRunning = true
        } catch (_: RuntimeException) {
            bubble = null
            stopSelf()
        }
    }

    private fun createDragListener(view: View): View.OnTouchListener {
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var dragged = false
        val threshold = dp(8)
        return View.OnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    downX = event.rawX
                    downY = event.rawY
                    dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > threshold || kotlin.math.abs(dy) > threshold) dragged = true
                    layoutParams.x = startX + dx
                    layoutParams.y = startY + dy
                    try {
                        windowManager?.updateViewLayout(view, layoutParams)
                    } catch (_: RuntimeException) {
                        stopSelf()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) openBozer()
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> false
            }
        }
    }

    private fun openBozer() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = ACTION_START_VOICE
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bubble?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: RuntimeException) {
                // The system may already have removed the overlay window.
            }
        }
        if (::preferences.isInitialized) {
            preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
            runCatching { unregisterReceiver(bubbleVisibilityReceiver) }
        }
        bubble = null
        spiralBubble = null
        windowManager = null
        isRunning = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun buildForegroundNotification(): android.app.Notification {
        val channelId = "bozer_floating_bubble"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Bulle flottante BOZER",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Indique que la bulle flottante BOZER reste disponible au-dessus des applications."
                    setShowBadge(false)
                }
            )
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, NOTIFICATION_ID, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, BozerFloatingService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, NOTIFICATION_ID + 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BOZER est actif")
            .setContentText("La bulle flottante est disponible. Appuie pour ouvrir BOZER.")
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Désactiver", stopPendingIntent)
            .build()
    }

    private fun startForeground(notificationId: Int, notification: android.app.Notification) {
        val serviceType = if (Build.VERSION.SDK_INT >= 34) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, notificationId, notification, serviceType)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 19019
        const val ACTION_STOP = "ai.bozer.app.action.STOP_FLOATING_BUBBLE"
        const val ACTION_HIDE_BUBBLE = "ai.bozer.app.action.HIDE_FLOATING_BUBBLE"
        const val ACTION_SHOW_BUBBLE = "ai.bozer.app.action.SHOW_FLOATING_BUBBLE"
        const val ACTION_START_VOICE = "ai.bozer.app.action.START_VOICE"

        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

private class BozerSpiralBubbleView(context: android.content.Context) : View(context) {
    private var rotationDegrees = 0f
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.WHITE
        alpha = 175
    }
    private var animationsEnabled = context.getSharedPreferences("bozer_settings", android.content.Context.MODE_PRIVATE)
        .getBoolean("animations_enabled", true)

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 30_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotationDegrees = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        contentDescription = "Ouvrir BOZER"
        isClickable = true
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        if (animationsEnabled == enabled) return
        animationsEnabled = enabled
        rotationDegrees = 0f
        if (enabled && isAttachedToWindow) animator.start() else animator.cancel()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (animationsEnabled) animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val side = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        val radius = side * 0.43f
        fillPaint.shader = RadialGradient(
            cx - radius * 0.22f,
            cy - radius * 0.28f,
            radius * 1.7f,
            intArrayOf(Color.WHITE, Color.rgb(230, 239, 234), Color.rgb(201, 216, 207)),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, side * 0.48f, fillPaint)
        fillPaint.shader = null
        outlinePaint.strokeWidth = side * 0.018f
        canvas.drawCircle(cx, cy, side * 0.465f, outlinePaint)

        canvas.save()
        canvas.rotate(rotationDegrees, cx, cy)
        repeat(3) { index ->
            canvas.save()
            canvas.rotate(index * 120f, cx, cy)
            val path = Path().apply {
                moveTo(cx - radius * 0.08f, cy - radius * 0.10f)
                cubicTo(
                    cx - radius * 0.02f, cy - radius * 0.79f,
                    cx + radius * 0.96f, cy - radius * 0.82f,
                    cx + radius * 0.91f, cy - radius * 0.18f
                )
                cubicTo(
                    cx + radius * 0.88f, cy + radius * 0.26f,
                    cx + radius * 0.28f, cy + radius * 0.42f,
                    cx + radius * 0.12f, cy + radius * 0.12f
                )
                cubicTo(
                    cx + radius * 0.02f, cy - radius * 0.09f,
                    cx + radius * 0.22f, cy - radius * 0.36f,
                    cx + radius * 0.47f, cy - radius * 0.26f
                )
                cubicTo(
                    cx + radius * 0.67f, cy - radius * 0.17f,
                    cx + radius * 0.52f, cy + radius * 0.05f,
                    cx + radius * 0.37f, cy + radius * 0.04f
                )
                cubicTo(
                    cx + radius * 0.49f, cy - radius * 0.18f,
                    cx + radius * 0.13f, cy - radius * 0.42f,
                    cx - radius * 0.08f, cy - radius * 0.10f
                )
                close()
            }
            fillPaint.shader = LinearGradient(
                cx - radius, cy - radius, cx + radius, cy + radius,
                intArrayOf(Color.rgb(91, 119, 108), Color.rgb(245, 248, 245), Color.rgb(145, 176, 160)),
                null,
                Shader.TileMode.CLAMP
            )
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, outlinePaint)
            canvas.restore()
        }
        canvas.restore()
        fillPaint.shader = null
        fillPaint.color = Color.rgb(113, 145, 130)
        canvas.drawCircle(cx, cy, radius * 0.075f, fillPaint)
        fillPaint.color = Color.WHITE
        canvas.drawCircle(cx - radius * 0.02f, cy - radius * 0.04f, radius * 0.026f, fillPaint)
    }
}
