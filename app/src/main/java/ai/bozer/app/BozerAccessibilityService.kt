package ai.bozer.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.text.Normalizer
import java.util.Locale

/**
 * Optional, user-enabled bridge for explicit voice-driven UI actions.
 *
 * This service examines the active app's visible accessibility tree only while
 * a time-limited action request is pending. It does not stream screen content,
 * collect credentials, or operate in the background without a request.
 */
class BozerAccessibilityService : AccessibilityService() {
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != applicationContext.packageName && !isTransientSystemPackage(packageName)) {
            lastExternalPackage = packageName
        }
        processPendingRequest()
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

    private fun processPendingRequest() {
        val request = pendingRequest ?: return
        if (SystemClock.uptimeMillis() >= request.expiresAt) {
            finishRequest(request.id, "EXPIRED", "La demande a expiré sans terminer toutes les actions.")
            return
        }

        val root = findTargetWindow(request.targetPackage) ?: return
        val rootPackage = root.packageName?.toString() ?: return
        if (rootPackage == applicationContext.packageName) return
        if (request.targetPackage != null && rootPackage != request.targetPackage) return
        if (request.nextActionIndex !in request.actions.indices) {
            finishRequest(request.id, "COMPLETED", "Toutes les actions demandées ont été envoyées à l'application.")
            return
        }

        val action = request.actions[request.nextActionIndex]
        val submitted = runCatching { performAction(root, action) }.getOrDefault(false)
        if (!submitted) return

        request.nextActionIndex += 1
        if (request.nextActionIndex >= request.actions.size) {
            finishRequest(request.id, "ACTIONS_SUBMITTED", "Les actions ont été envoyées. Vérifie le résultat dans l'application.")
        } else {
            // Give the target app time to redraw before looking for the next control.
            mainHandler.postDelayed({
                if (pendingRequest?.id == request.id) processPendingRequest()
            }, STEP_DELAY_MS)
        }
    }

    private fun findTargetWindow(targetPackage: String?): AccessibilityNodeInfo? {
        val active = rootInActiveWindow
        if (active != null) {
            val activePackage = active.packageName?.toString()
            if (targetPackage == null || activePackage == targetPackage) return active
        }
        if (targetPackage == null) return null

        // The input method can own the active window while the app remains
        // visible underneath. Prefer the requested app's accessible window.
        return windows.asSequence()
            .mapNotNull { window -> runCatching { window.root }.getOrNull() }
            .firstOrNull { root -> root.packageName?.toString() == targetPackage }
    }

    private fun performAction(root: AccessibilityNodeInfo, action: BozerUiAction): Boolean =
        when (action) {
            is BozerUiAction.TapText -> clickVisibleControl(root, action.label)
            is BozerUiAction.LongPressText -> clickVisibleControl(root, action.label, longPress = true)
            is BozerUiAction.EnterText -> enterText(root, action.value)
            is BozerUiAction.Swipe -> swipe(action.direction)
            BozerUiAction.ScrollForward -> scrollVisibleContainer(root, forward = true)
            BozerUiAction.ScrollBackward -> scrollVisibleContainer(root, forward = false)
            BozerUiAction.PressBack -> performGlobalAction(GLOBAL_ACTION_BACK)
            BozerUiAction.PressHome -> performGlobalAction(GLOBAL_ACTION_HOME)
        }

    private fun clickVisibleControl(
        root: AccessibilityNodeInfo,
        requestedLabel: String,
        longPress: Boolean = false
    ): Boolean {
        val expected = normalize(requestedLabel)
        if (expected.isBlank()) return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1
            val labels = listOfNotNull(
                node.text?.toString(),
                node.contentDescription?.toString()
            ).map(::normalize)
            val matches = labels.any { label ->
                label == expected || label.startsWith("$expected ")
            }

            if (matches && node.isVisibleToUser && node.isEnabled) {
                var candidate: AccessibilityNodeInfo? = node
                var depth = 0
                while (candidate != null && depth < MAX_CLICKABLE_ANCESTORS) {
                    val current = candidate
                    val canPerform = if (longPress) current.isLongClickable else current.isClickable
                    val action = if (longPress) AccessibilityNodeInfo.ACTION_LONG_CLICK
                        else AccessibilityNodeInfo.ACTION_CLICK
                    if (current.isVisibleToUser && current.isEnabled && canPerform &&
                        current.performAction(action)
                    ) return true
                    candidate = current.parent
                    depth += 1
                }
            }

            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child -> queue.addLast(child) }
            }
        }
        return false
    }

    private fun enterText(root: AccessibilityNodeInfo, value: String): Boolean {
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = if (focused?.isEditable == true) focused else findEditableNode(root)
        if (target == null || !target.isVisibleToUser || !target.isEnabled || target.isPassword) return false
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    private fun findEditableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val candidates = mutableListOf<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1
            if (node.isEditable && node.isVisibleToUser && node.isEnabled && !node.isPassword) {
                candidates += node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child -> queue.addLast(child) }
            }
        }
        // Never guess which of several input fields the user intended.
        return candidates.singleOrNull()
    }

    private fun swipe(direction: BozerUiAction.Direction): Boolean {
        val width = resources.displayMetrics.widthPixels.toFloat()
        val height = resources.displayMetrics.heightPixels.toFloat()
        val path = Path()
        val centerX = width * 0.5f
        val centerY = height * 0.5f
        val startX: Float
        val startY: Float
        val endX: Float
        val endY: Float
        when (direction) {
            BozerUiAction.Direction.LEFT -> {
                startX = width * 0.82f; startY = centerY
                endX = width * 0.18f; endY = centerY
            }
            BozerUiAction.Direction.RIGHT -> {
                startX = width * 0.18f; startY = centerY
                endX = width * 0.82f; endY = centerY
            }
            BozerUiAction.Direction.UP -> {
                startX = centerX; startY = height * 0.78f
                endX = centerX; endY = height * 0.22f
            }
            BozerUiAction.Direction.DOWN -> {
                startX = centerX; startY = height * 0.22f
                endX = centerX; endY = height * 0.78f
            }
        }
        path.moveTo(startX, startY)
        path.lineTo(endX, endY)
        val stroke = GestureDescription.StrokeDescription(path, 0L, SWIPE_DURATION_MS)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, mainHandler)
    }

    private fun scrollVisibleContainer(root: AccessibilityNodeInfo, forward: Boolean): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited += 1
            if (node.isScrollable && node.isVisibleToUser && node.isEnabled && node.performAction(action)) {
                return true
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child -> queue.addLast(child) }
            }
        }
        return false
    }

    private fun finishRequest(id: Long, status: String, message: String) {
        val current = pendingRequest
        if (current?.id != id) return
        pendingRequest = null
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putLong(KEY_LAST_ACTION_AT, System.currentTimeMillis())
            .putString(KEY_LAST_ACTION_STATUS, status)
            .putString(KEY_LAST_ACTION_RESULT, message)
            .apply()
        resultListener?.invoke(status, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’']"), " ")
            .replace(Regex("[-_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private class PendingRequest(
        val id: Long,
        val actions: List<BozerUiAction>,
        val targetPackage: String?,
        val expiresAt: Long,
        var nextActionIndex: Int = 0
    )

    companion object {
        private const val MAX_NODES = 500
        private const val MAX_CLICKABLE_ANCESTORS = 6
        private const val REQUEST_TIMEOUT_MS = 45_000L
        private const val STEP_DELAY_MS = 450L
        private const val SWIPE_DURATION_MS = 360L
        private const val PREFERENCES = "bozer_settings"
        private const val KEY_LAST_ACTION_AT = "accessibility_last_action_at"
        private const val KEY_LAST_ACTION_STATUS = "accessibility_last_action_status"
        private const val KEY_LAST_ACTION_RESULT = "accessibility_last_action_result"

        private val SENSITIVE_LABELS = setOf(
            "envoyer", "send", "envoyer maintenant", "send now",
            "publier", "publish", "poster", "post",
            "supprimer", "delete", "effacer", "vider définitivement",
            "payer", "payer maintenant", "pay", "purchase", "checkout",
            "acheter", "commander", "order", "transférer", "transfer",
            "retirer", "withdraw", "confirmer", "confirm", "valider",
            "appeler", "call", "s'abonner", "subscribe", "start trial"
        )

        @Volatile private var instance: BozerAccessibilityService? = null
        @Volatile private var pendingRequest: PendingRequest? = null
        @Volatile private var lastExternalPackage: String? = null
        @Volatile private var resultListener: ((String, String) -> Unit)? = null

        fun isConnected(): Boolean = instance != null

        fun setResultListener(listener: ((String, String) -> Unit)?) {
            resultListener = listener
        }

        fun lastUsedExternalPackage(): String? = lastExternalPackage

        private fun isTransientSystemPackage(packageName: String): Boolean =
            packageName in setOf(
                "com.android.systemui",
                "com.android.permissioncontroller",
                "com.google.android.permissioncontroller",
                "com.google.android.inputmethod.latin",
                "com.android.inputmethod.latin",
                "com.samsung.android.honeyboard",
                "com.swiftkey.swiftkeyconfigurator"
            )

        /**
         * Queue explicit actions for the current foreground app or a specified
         * app package. Sensitive final controls require a separate confirmation.
         */
        fun requestActions(
            actions: List<BozerUiAction>,
            targetPackage: String? = null,
            allowSensitiveActions: Boolean = false
        ): Long? {
            val service = instance ?: return null
            if (pendingRequest != null) return null
            if (actions.isEmpty() || actions.size > MAX_STEPS) return null
            if (!allowSensitiveActions && actions.any(::isSensitiveAction)) return null
            val target = targetPackage ?: lastExternalPackage
            val id = SystemClock.uptimeMillis()
            pendingRequest = PendingRequest(
                id = id,
                actions = actions.toList(),
                targetPackage = target,
                expiresAt = id + REQUEST_TIMEOUT_MS
            )
            service.mainHandler.postDelayed({
                if (pendingRequest?.id == id) {
                    service.finishRequest(id, "EXPIRED", "La demande a expiré sans terminer toutes les actions.")
                }
            }, REQUEST_TIMEOUT_MS)
            service.mainHandler.postDelayed({
                if (pendingRequest?.id == id) service.processPendingRequest()
            }, STEP_DELAY_MS)
            return id
        }

        fun cancelPendingRequest() {
            pendingRequest = null
        }

        fun isSensitiveAction(action: BozerUiAction): Boolean {
            val requestedLabel = when (action) {
                is BozerUiAction.TapText -> action.label
                is BozerUiAction.LongPressText -> action.label
                else -> return false
            }
            val label = Normalizer.normalize(requestedLabel.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .trim()
            return SENSITIVE_LABELS.any { label == it || label.startsWith("$it ") }
        }

        private const val MAX_STEPS = 8
    }
}
