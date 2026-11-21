package ai.bozer.app

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
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

        val view = BozerSpiralBubbleView(this).apply {
            elevation = dp(12).toFloat()
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
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
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
