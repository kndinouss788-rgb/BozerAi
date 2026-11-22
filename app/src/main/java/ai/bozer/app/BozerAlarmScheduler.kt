package ai.bozer.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.Instant

data class StoredBozerAlarm(
    val requestCode: Int,
    val triggerAtMillis: Long,
    val label: String
)

enum class AlarmScheduleState {
    SCHEDULED,
    NEED_EXACT_ALARM_PERMISSION,
    NEED_NOTIFICATION_PERMISSION,
    FAILED
}

data class AlarmScheduleResult(
    val state: AlarmScheduleState,
    val message: String
)

/** Small private store used to restore BOZER alarms after a phone reboot. */
object BozerAlarmStore {
    private const val PREFS = "bozer_scheduled_alarms"
    private const val KEY_ITEMS = "items"

    fun all(context: Context): List<StoredBozerAlarm> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val array = org.json.JSONArray(json)
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    val code = row.optInt("requestCode", -1)
                    val trigger = row.optLong("triggerAtMillis", 0L)
                    val label = row.optString("label").take(100)
                    if (code >= 0 && trigger > 0L && label.isNotBlank()) {
                        add(StoredBozerAlarm(code, trigger, label))
                    }
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun put(context: Context, alarm: StoredBozerAlarm) {
        val items = all(context).filterNot { it.requestCode == alarm.requestCode } + alarm
        write(context, items)
    }

    fun remove(context: Context, requestCode: Int) {
        write(context, all(context).filterNot { it.requestCode == requestCode })
    }

    private fun write(context: Context, items: List<StoredBozerAlarm>) {
        val json = org.json.JSONArray()
        items.take(30).forEach { item ->
            json.put(
                org.json.JSONObject()
                    .put("requestCode", item.requestCode)
                    .put("triggerAtMillis", item.triggerAtMillis)
                    .put("label", item.label)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ITEMS, json.toString())
            .apply()
    }
}

/**
 * Programs real AlarmManager alarm-clock events. It refuses to pretend the
 * alarm is set when Android's exact-alarm or notification permission is missing.
 */
object BozerAlarmScheduler {
    const val CHANNEL_ID = "bozer_alarm_notifications"

    fun schedule(context: Context, action: PhoneAction.SetAlarm): AlarmScheduleResult {
        if (action.triggerAtMillis <= System.currentTimeMillis()) {
            return AlarmScheduleResult(AlarmScheduleState.FAILED, "L'heure du réveil est déjà passée. Choisis une heure future.")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(AlarmManager::class.java)
            if (!manager.canScheduleExactAlarms()) {
                return AlarmScheduleResult(
                    AlarmScheduleState.NEED_EXACT_ALARM_PERMISSION,
                    "Android doit autoriser les alarmes exactes. BOZER ouvre le réglage correspondant ; reviens ensuite confirmer."
                )
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return AlarmScheduleResult(
                AlarmScheduleState.NEED_NOTIFICATION_PERMISSION,
                "Autorise les notifications BOZER dans les réglages Android, puis reviens confirmer le réveil."
            )
        }

        val requestCode = (action.triggerAtMillis / 60_000L).toInt()
        val label = "Réveil BOZER"
        return try {
            scheduleSaved(context, StoredBozerAlarm(requestCode, action.triggerAtMillis, label))
            AlarmScheduleResult(
                AlarmScheduleState.SCHEDULED,
                "Réveil programmé pour " + Instant.ofEpochMilli(action.triggerAtMillis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDateTime()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", java.util.Locale.FRANCE)) +
                    ". Android a accepté l'alarme."
            )
        } catch (error: SecurityException) {
            AlarmScheduleResult(
                AlarmScheduleState.NEED_EXACT_ALARM_PERMISSION,
                "Android a refusé l'alarme exacte. Autorise cette fonction dans les réglages puis réessaie."
            )
        } catch (error: Exception) {
            AlarmScheduleResult(
                AlarmScheduleState.FAILED,
                "Le réveil n'a pas été programmé : " + (error.message ?: "erreur Android inconnue")
            )
        }
    }

    internal fun scheduleSaved(context: Context, alarm: StoredBozerAlarm) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val appIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val showIntent = PendingIntent.getActivity(
            context,
            alarm.requestCode + 1,
            appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val broadcast = Intent(context, BozerAlarmReceiver::class.java)
            .setAction("ai.bozer.app.ALARM_FIRED")
            .putExtra("requestCode", alarm.requestCode)
            .putExtra("label", alarm.label)
        val operation = PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            broadcast,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.setAlarmClock(
            AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, showIntent),
            operation
        )
        BozerAlarmStore.put(context, alarm)
    }

    fun openExactAlarmSettings(context: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    internal fun showNotification(context: Context, alarm: StoredBozerAlarm) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Réveils BOZER",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications des réveils programmés par BOZER"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
                )
            }
            manager.createNotificationChannel(channel)
        }

        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val contentIntent = PendingIntent.getActivity(
            context,
            alarm.requestCode + 1,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // BOZER targets Android 8.0+ (minSdk 26), so a channel-backed builder is always available.
        val notification = Notification.Builder(context, CHANNEL_ID)
        notification
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("C'est l'heure — BOZER")
            .setContentText("Ton réveil est arrivé.")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        manager.notify(alarm.requestCode, notification.build())
    }
}

class BozerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val requestCode = intent?.getIntExtra("requestCode", -1) ?: -1
        val alarm = BozerAlarmStore.all(context).firstOrNull { it.requestCode == requestCode }
            ?: return
        BozerAlarmStore.remove(context, requestCode)
        if (Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            BozerAlarmScheduler.showNotification(context, alarm)
        }
    }
}

class BozerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        val now = System.currentTimeMillis()
        BozerAlarmStore.all(context).filter { it.triggerAtMillis > now }.forEach { alarm ->
            try {
                BozerAlarmScheduler.scheduleSaved(context, alarm)
            } catch (_: SecurityException) {
                // Keep the saved alarm record so the next app opening can explain the permission issue.
            }
        }
    }
}
