package ai.bozer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * User-enabled floating entry point for BOZER.
 *
 * It only opens the main activity; it never listens to the microphone or
 * performs phone actions from the overlay. Android's special-use foreground
 * service keeps the bubble alive while the user has explicitly enabled it.
 */
class BozerFloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var downRawX = 0f
    private var downRawY = 0f
    private var downWindowX = 0
    private var downWindowY = 0
    private var dragged = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            removeBubble()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Overlay permission missing; service will stop.")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForegroundService()
        showBubble()
        return START_STICKY
    }

    private fun startAsForegroundService() {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("BOZER est actif")
            .setContentText("La bulle flottante est activée. Touchez-la pour ouvrir BOZER.")
            .setContentIntent(openApp)
            .setOngoing(true)
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

    private fun showBubble() {
        if (bubble != null) return
        val view = TextView(this).apply {
            text = "B"
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(0xFF43525A.toInt())
            contentDescription = "Ouvrir BOZER"
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xF9FFFFFF.toInt())
                setStroke(dp(1), 0xFFDCE4E7.toInt())
            }
            elevation = dp(9).toFloat()
            isClickable = true
            isFocusable = true
            setOnClickListener { openMainActivity() }
            setOnTouchListener(::handleBubbleTouch)
        }

        val layout = WindowManager.LayoutParams(
            dp(58),
            dp(58),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(160)
            title = "BOZER floating bubble"
        }

        try {
            windowManager?.addView(view, layout)
            bubble = view
            bubbleParams = layout
        } catch (error: SecurityException) {
            Log.e(TAG, "Android denied the overlay window.", error)
            stopSelf()
        } catch (error: WindowManager.BadTokenException) {
            Log.e(TAG, "Android rejected the overlay window.", error)
            stopSelf()
        }
    }

    private fun openMainActivity() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        } catch (error: Exception) {
            Log.e(TAG, "Unable to open BOZER from the floating bubble.", error)
        }
    }

    private fun handleBubbleTouch(view: View, event: MotionEvent): Boolean {
        val layout = bubbleParams ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downWindowX = layout.x
                downWindowY = layout.y
                dragged = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = (event.rawX - downRawX).toInt()
                val deltaY = (event.rawY - downRawY).toInt()
                if (!dragged && (abs(deltaX) > dp(6) || abs(deltaY) > dp(6))) {
                    dragged = true
                }
                if (dragged) {
                    layout.x = downWindowX + deltaX
                    layout.y = downWindowY + deltaY
                    try {
                        windowManager?.updateViewLayout(view, layout)
                    } catch (error: IllegalArgumentException) {
                        Log.w(TAG, "The bubble was detached while moving.", error)
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!dragged) view.performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeBubble()
        stopForeground(STOP_FOREGROUND_REMOVE)
        windowManager = null
        super.onDestroy()
    }

    private fun removeBubble() {
        val view = bubble ?: return
        try {
            windowManager?.removeView(view)
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "The bubble was already detached.", error)
        } finally {
            bubble = null
            bubbleParams = null
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "BOZER — bulle flottante",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification nécessaire au maintien de la bulle flottante BOZER."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    companion object {
        const val ACTION_START = "ai.bozer.app.action.START_FLOATING_BUBBLE"
        const val ACTION_STOP = "ai.bozer.app.action.STOP_FLOATING_BUBBLE"
        private const val TAG = "BOZER_FLOAT"
        private const val CHANNEL_ID = "bozer_floating_bubble"
        private const val NOTIFICATION_ID = 19019
    }

}
