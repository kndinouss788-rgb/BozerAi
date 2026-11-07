package ai.bozer.app

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.util.TypedValue

/**
 * Small, draggable BOZER entry point drawn above other apps.
 * It only opens BOZER; it does not read or control other app content.
 */
class BozerFloatingService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private lateinit var layoutParams: WindowManager.LayoutParams

    override fun onCreate() {
        super.onCreate()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
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

        val view = TextView(this).apply {
            text = "B"
            setTextColor(Color.rgb(76, 100, 113))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            contentDescription = "Ouvrir BOZER"
            isClickable = true
            elevation = dp(12).toFloat()
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.WHITE, Color.rgb(216, 229, 236))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(1), 0xCCFFFFFF.toInt())
            }
        }
        view.setOnTouchListener(createDragListener(view))
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
        bubble = null
        windowManager = null
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
