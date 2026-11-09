package ai.bozer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.util.concurrent.atomic.AtomicBoolean

/**
 * User-enabled floating entry point. It never reads or controls another app.
 * The foreground notification makes the persistent overlay visible to Android
 * and gives the user a direct way to stop it.
 */
class BozerFloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            promoteToForeground()
            if (bubble == null) attachBubble()
        } catch (_: SecurityException) {
            stopSelf()
            return START_NOT_STICKY
        } catch (_: RuntimeException) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun promoteToForeground() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BOZER — bulle flottante", NotificationManager.IMPORTANCE_LOW)
                    .apply { description = "Indique que la bulle BOZER est active." }
            )
        }

        val openPendingIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, BozerFloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BOZER est disponible")
            .setContentText("La bulle flottante est active. Appuie ici pour ouvrir BOZER.")
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(null, "Arrêter la bulle", stopPendingIntent).build()
            )
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun attachBubble() {
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

        val view = TextView(this).apply {
            text = "B"
            setTextColor(0xFF526A78.toInt())
            textSize = 23f
            typeface = android.graphics.Typeface.create(
                "sans-serif-medium",
                android.graphics.Typeface.BOLD
            )
            gravity = Gravity.CENTER
            contentDescription = "Ouvrir BOZER"
            isClickable = true
            elevation = dp(12).toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFFFFFFFF.toInt(), 0xFFD8E5EC.toInt())
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0xCCFFFFFF.toInt())
            }
        }

        view.setOnTouchListener(createDragListener(view))
        windowManager?.addView(view, layoutParams)
        bubble = view
        running.set(true)
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
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        )
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        bubble?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (_: RuntimeException) {
                // Android may already have removed the overlay window.
            }
        }
        bubble = null
        windowManager = null
        running.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "bozer-floating-bubble"
        private const val NOTIFICATION_ID = 1901
        private const val ACTION_STOP = "ai.bozer.app.action.STOP_FLOATING_BUBBLE"
        private val running = AtomicBoolean(false)

        val isRunning: Boolean get() = running.get()

        fun stopIntent(context: android.content.Context): Intent =
            Intent(context, BozerFloatingService::class.java).setAction(ACTION_STOP)
    }
}
