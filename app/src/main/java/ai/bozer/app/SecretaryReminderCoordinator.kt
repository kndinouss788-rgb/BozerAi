package ai.bozer.app

object SecretaryReminderPolicy {
    fun requiresReminder(task: SecretaryTask, nowMillis: Long): Boolean =
        !task.completed && task.dueAtMillis?.let { it > nowMillis } == true

    fun label(task: SecretaryTask): String = "Échéance : ${task.title}".take(120)
}

/**
 * Keeps a Secretary task's due-date reminder in sync with BOZER's local
 * AlarmManager store. All methods are synchronous and should run on Dispatchers.IO.
 */
class SecretaryReminderCoordinator(
    private val tasks: SecretaryRepository,
    private val alarms: BozerAlarmRepository,
    private val scheduler: BozerAlarmScheduler,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    fun synchronize(task: SecretaryTask): Boolean {
        val previousId = task.reminderAlarmId
        val dueAt = task.dueAtMillis

        if (!SecretaryReminderPolicy.requiresReminder(task, nowMillis())) {
            return try {
                tasks.updateTask(task.copy(reminderAlarmId = null))
                removeAlarm(previousId)
                true
            } catch (_: Exception) {
                false
            }
        }

        val triggerAt = requireNotNull(dueAt)
        val label = SecretaryReminderPolicy.label(task)
        val existing = previousId?.let(alarms::get)
        if (existing != null && existing.enabled &&
            existing.triggerAtMillis == triggerAt && existing.label == label
        ) {
            return runCatching { tasks.updateTask(task) }.isSuccess
        }

        val newId = runCatching { alarms.create(triggerAt, label) }.getOrNull() ?: run {
            runCatching { tasks.updateTask(task.copy(reminderAlarmId = null)) }
            removeAlarm(previousId)
            return false
        }
        val newAlarm = alarms.get(newId)
        if (newAlarm == null || !scheduler.schedule(newAlarm)) {
            removeAlarm(newId)
            runCatching { tasks.updateTask(task.copy(reminderAlarmId = null)) }
            removeAlarm(previousId)
            return false
        }

        val persisted = runCatching {
            tasks.updateTask(task.copy(reminderAlarmId = newId))
        }.isSuccess
        if (!persisted) {
            removeAlarm(newId)
            return false
        }

        if (previousId != null && previousId != newId) removeAlarm(previousId)
        return true
    }

    fun complete(task: SecretaryTask, completed: Boolean): Boolean =
        synchronize(task.copy(completed = completed))

    fun delete(task: SecretaryTask): Boolean {
        val deleted = tasks.deleteTask(task.id)
        if (deleted) removeAlarm(task.reminderAlarmId)
        return deleted
    }

    private fun removeAlarm(id: Long?) {
        if (id == null) return
        runCatching { scheduler.cancel(id) }
        runCatching { alarms.delete(id) }
    }
}
