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
data class BozerAlarm(val id: Long, val triggerAtMillis: Long, val label: String, val enabled: Boolean = true, val repeatDaysMask: Int = 0) {
    init { require(id > 0); require(triggerAtMillis > 0); require(label.isNotBlank()) }
}

class BozerAlarmRepository(private val database: ChatDatabase) {
    fun create(triggerAtMillis: Long, label: String, repeatDaysMask: Int = 0): Long = database.insertBozerAlarm(triggerAtMillis, label, repeatDaysMask)
    fun get(id: Long): BozerAlarm? = database.bozerAlarm(id)
    fun enabled(): List<BozerAlarm> = database.enabledBozerAlarms()
    fun disable(id: Long): Boolean = database.setBozerAlarmEnabled(id, false)
    fun delete(id: Long): Boolean = database.deleteBozerAlarm(id)
    fun updateTrigger(id: Long, triggerAtMillis: Long): Boolean = database.updateBozerAlarmTrigger(id, triggerAtMillis)
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
    companion object {
        const val ACTION_SNOOZE = "ai.bozer.app.action.SNOOZE_ALARM"
        const val SNOOZE_MINUTES = 10L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(BozerAlarmScheduler.EXTRA_ALARM_ID, -1L)
        if (id <= 0) return
        if (intent.action == ACTION_SNOOZE) {
            val repository = BozerAlarmRepository(ChatDatabase(context))
            val original = repository.get(id) ?: return
            val snoozeId = repository.create(
                System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L,
                "Rappel (après répétition) : ${original.label}"
            )
            val snooze = repository.get(snoozeId) ?: return
            if (BozerAlarmScheduler(context).schedule(snooze)) {
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.toInt())
            } else repository.disable(snoozeId)
            return
        }
        if (intent.action != BozerAlarmScheduler.ACTION_FIRE) return
        if (id <= 0) return
        val repository = BozerAlarmRepository(ChatDatabase(context))
        val alarm = repository.get(id) ?: return
        if (!alarm.enabled) return
        if (alarm.repeatDaysMask != 0) {
            val next = BozerAlarmTime.nextOccurrence(alarm.triggerAtMillis, System.currentTimeMillis(), alarm.repeatDaysMask)
            if (next != null) {
                repository.updateTrigger(id, next)
                val updated = repository.get(id)
                if (updated != null) BozerAlarmScheduler(context).schedule(updated)
            } else repository.disable(id)
        } else repository.disable(id)
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
        val snoozeIntent = Intent(context, BozerAlarmReceiver::class.java)
            .setAction(ACTION_SNOOZE)
            .putExtra(BozerAlarmScheduler.EXTRA_ALARM_ID, alarm.id)
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context, alarm.id.toInt(), snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("BOZER — ${alarm.label}")
            .setContentText("C'est l'heure.")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Répéter 10 min", snoozePendingIntent)
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


/** Calculates the next local-calendar occurrence for a recurring alarm. */
object BozerAlarmTime {
    fun nextOccurrence(previousTriggerMillis: Long, nowMillis: Long, repeatDaysMask: Int): Long? {
        if (repeatDaysMask !in 1..127) return null
        val base = java.util.Calendar.getInstance().apply { timeInMillis = previousTriggerMillis }
        val candidate = java.util.Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(java.util.Calendar.HOUR_OF_DAY, base.get(java.util.Calendar.HOUR_OF_DAY))
            set(java.util.Calendar.MINUTE, base.get(java.util.Calendar.MINUTE))
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            val dayBit = 1 shl (candidate.get(java.util.Calendar.DAY_OF_WEEK) - 1)
            if (candidate.timeInMillis > nowMillis && repeatDaysMask and dayBit != 0) return candidate.timeInMillis
            candidate.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }
}
