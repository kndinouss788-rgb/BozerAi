package ai.bozer.app

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** BOZER-owned alarms: local persistence, independent from the AI gateway. */
data class BozerAlarm(val id: Long, val triggerAtMillis: Long, val label: String, val enabled: Boolean = true) {
    init { require(id > 0); require(triggerAtMillis > 0); require(label.isNotBlank()) }
}

class BozerAlarmRepository(private val database: ChatDatabase) {
    fun create(triggerAtMillis: Long, label: String): Long = database.insertBozerAlarm(triggerAtMillis, label)
    fun get(id: Long): BozerAlarm? = database.bozerAlarm(id)
    fun enabled(): List<BozerAlarm> = database.enabledBozerAlarms()
    fun disable(id: Long): Boolean = database.setBozerAlarmEnabled(id, false)
    fun delete(id: Long): Boolean = database.deleteBozerAlarm(id)
}

class BozerAlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(alarm: BozerAlarm): Boolean {
        if (!alarm.enabled || alarm.triggerAtMillis <= System.currentTimeMillis()) return false
        val operation = alarmPendingIntent(alarm.id) ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                val showIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
                    ?.let { PendingIntent.getActivity(appContext, 0, it, pendingFlags()) }
                alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, showIntent), operation)
            } else {
                // Graceful inexact fallback; BOZER must not claim exact precision without access.
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAtMillis, operation)
            }
            true
        } catch (_: SecurityException) { false }
    }

    fun exactAlarmAccessGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun cancel(id: Long) { alarmPendingIntent(id)?.let(alarmManager::cancel) }

    fun rescheduleEnabled(repository: BozerAlarmRepository) {
        repository.enabled().forEach(::schedule)
    }

    private fun alarmPendingIntent(id: Long): PendingIntent? {
        val intent = Intent(appContext, BozerAlarmReceiver::class.java)
            .setAction(ACTION_FIRE).putExtra(EXTRA_ALARM_ID, id)
        return PendingIntent.getBroadcast(appContext, id.toInt(), intent, pendingFlags())
    }

    private fun pendingFlags() = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    companion object {
        const val ACTION_FIRE = "ai.bozer.app.action.FIRE_ALARM"
        const val EXTRA_ALARM_ID = "bozer_alarm_id"
    }
}

class BozerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BozerAlarmScheduler.ACTION_FIRE) return
        val id = intent.getLongExtra(BozerAlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id <= 0) return
        val repository = BozerAlarmRepository(ChatDatabase(context))
        val alarm = repository.get(id) ?: return
        if (!alarm.enabled) return
        repository.disable(id) // one-shot: persist before notifying
        showAlarmNotification(context, alarm)
    }

    private fun showAlarmNotification(context: Context, alarm: BozerAlarm) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "bozer_alarms"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelId) == null) {
            manager.createNotificationChannel(NotificationChannel(
                channelId, "Alarmes BOZER", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alarmes et rappels programmés dans BOZER"
                setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
                enableVibration(true)
            })
        }
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = openApp?.let {
            PendingIntent.getActivity(context, alarm.id.toInt(), it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("BOZER — ${alarm.label}")
            .setContentText("C'est l'heure.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true).setContentIntent(contentIntent).build()
        manager.notify(alarm.id.toInt(), notification)
    }
}

class BozerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        BozerAlarmScheduler(context).rescheduleEnabled(BozerAlarmRepository(ChatDatabase(context)))
    }
}
