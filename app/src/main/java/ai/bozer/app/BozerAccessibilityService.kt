package ai.bozer.app

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.text.Normalizer
import java.util.Locale

/**
 * Optional, user-enabled UI bridge.
 *
 * It does not inspect other apps continuously. It searches only while a
 * time-limited, explicit BOZER ACTION request is pending, and only in the
 * requested target package. The current supported UI action is the visible
 * TikTok record control.
 */
class BozerAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val request = pendingRequest ?: return
        if (SystemClock.uptimeMillis() > request.expiresAt) {
            clearRequest(request.id, "TIMEOUT")
            return
        }

        val packageName = event?.packageName?.toString() ?: return
        if (packageName !in request.targetPackages) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() !in request.targetPackages) return
        if (clickVisibleControl(root, request.labels)) {
            clearRequest(request.id, "CLICK_REQUESTED")
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun clickVisibleControl(root: AccessibilityNodeInfo, labels: Set<String>): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1

            val nodeLabels = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).map(::normalize)

            val labelMatches = nodeLabels.any { value ->
                labels.any { label ->
                    value == label || value.startsWith("$label ") || value.startsWith("appuyer pour $label")
                }
            }

            if (labelMatches && node.isVisibleToUser && node.isEnabled) {
                var clickable: AccessibilityNodeInfo? = node
                var depth = 0
                while (clickable != null && depth < MAX_CLICKABLE_ANCESTORS) {
                    val candidate = clickable
                    if (candidate.isVisibleToUser && candidate.isEnabled &&
                        candidate.isClickable && candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    ) return true
                    clickable = candidate.parent
                    depth += 1
                }
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child -> queue.addLast(child) }
            }
        }
        return false
    }

    private fun clearRequest(id: Long, result: String) {
        val current = pendingRequest
        if (current?.id != id) return
        pendingRequest = null
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putLong(KEY_LAST_ACTION_AT, System.currentTimeMillis())
            .putString(KEY_LAST_ACTION_RESULT, result)
            .apply()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’']"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private data class PendingRequest(
        val id: Long,
        val targetPackages: Set<String>,
        val labels: Set<String>,
        val expiresAt: Long
    )

    companion object {
        private const val MAX_NODES = 350
        private const val MAX_CLICKABLE_ANCESTORS = 5
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val PREFERENCES = "bozer_settings"
        private const val KEY_LAST_ACTION_AT = "accessibility_last_action_at"
        private const val KEY_LAST_ACTION_RESULT = "accessibility_last_action_result"

        private val TIKTOK_PACKAGES = setOf(
            "com.zhiliaoapp.musically",
            "com.zhiliaoapp.musically.go"
        )
        private val RECORD_LABELS = setOf(
            "enregistrer une video", "enregistrer video",
            "commencer l enregistrement", "demarrer l enregistrement",
            "start recording", "record video", "take video", "record"
        )

        @Volatile
        private var instance: BozerAccessibilityService? = null

        @Volatile
        private var pendingRequest: PendingRequest? = null

        fun isConnected(): Boolean = instance != null

        /**
         * Queues one visible TikTok record-button click. It expires after
         * 30 seconds and cannot act in any package other than TikTok.
         */
        fun requestTikTokRecordingClick(): Boolean {
            val service = instance ?: return false
            val id = SystemClock.uptimeMillis()
            val request = PendingRequest(
                id = id,
                targetPackages = TIKTOK_PACKAGES,
                labels = RECORD_LABELS,
                expiresAt = id + REQUEST_TIMEOUT_MS
            )
            pendingRequest = request
            service.mainHandler.postDelayed({
                if (pendingRequest?.id == id) {
                    service.clearRequest(id, "TIMEOUT")
                }
            }, REQUEST_TIMEOUT_MS)
            return true
        }

        fun cancelPendingRequest() {
            pendingRequest = null
        }
    }
}
