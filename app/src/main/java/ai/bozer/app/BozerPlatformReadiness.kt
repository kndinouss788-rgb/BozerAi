package ai.bozer.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.service.voice.VoiceInteractionService
import android.app.role.RoleManager

/**
 * Read-only inventory of Android capabilities relevant to BOZER's future
 * system-assistant integration.
 *
 * This class deliberately does not request permissions, start microphone
 * capture, change system settings, or claim that wake-word detection is active.
 * A positive hardware/permission result is only one prerequisite.
 */
data class BozerPlatformReadiness(
    val hasMicrophoneHardware: Boolean,
    val recordAudioPermissionGranted: Boolean,
    val assistantRoleSupported: Boolean,
    val voiceInteractionServiceDeclared: Boolean,
    val assistantRoleHeldByBozer: Boolean,
    val notificationPermissionGranted: Boolean,
    val batteryOptimizationExempt: Boolean
) {
    /** True only when BOZER has declared a VoiceInteractionService and Android exposes the role. */
    val canOfferAssistantSetup: Boolean
        get() = assistantRoleSupported && voiceInteractionServiceDeclared

    /** True only when Android confirms BOZER currently owns the assistant role. */
    val isSystemAssistant: Boolean
        get() = assistantRoleHeldByBozer
}

object BozerPlatformReadinessProbe {

    fun inspect(context: Context): BozerPlatformReadiness {
        val appContext = context.applicationContext
        val packageManager = appContext.packageManager

        val hasMicrophone = packageManager.hasSystemFeature(
            PackageManager.FEATURE_MICROPHONE
        )
        val recordAudioGranted = appContext.checkSelfPermission(
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val assistantRoleManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appContext.getSystemService(RoleManager::class.java)
        } else {
            null
        }
        val assistantRoleSupported = assistantRoleManager
            ?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true
        val assistantRoleHeld = assistantRoleManager
            ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

        val voiceServiceDeclared = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val serviceIntent = Intent(VoiceInteractionService.SERVICE_INTERFACE)
            packageManager.queryIntentServices(serviceIntent, PackageManager.MATCH_ALL)
                .any { it.serviceInfo?.packageName == appContext.packageName }
        } else {
            false
        }

        val notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryExempt = powerManager?.isIgnoringBatteryOptimizations(
            appContext.packageName
        ) ?: false

        return BozerPlatformReadiness(
            hasMicrophoneHardware = hasMicrophone,
            recordAudioPermissionGranted = recordAudioGranted,
            assistantRoleSupported = assistantRoleSupported,
            voiceInteractionServiceDeclared = voiceServiceDeclared,
            assistantRoleHeldByBozer = assistantRoleHeld,
            notificationPermissionGranted = notificationsGranted,
            batteryOptimizationExempt = batteryExempt
        )
    }
}
