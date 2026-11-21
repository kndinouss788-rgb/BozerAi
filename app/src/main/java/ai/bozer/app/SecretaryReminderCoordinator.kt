package ai.bozer.app

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

        if (task.completed || dueAt == null || dueAt <= nowMillis()) {
            return try {
                tasks.updateTask(task.copy(reminderAlarmId = null))
                removeAlarm(previousId)
                true
            } catch (_: Exception) {
                false
            }
        }

        val label = "Échéance : ${task.title}".take(120)
        val existing = previousId?.let(alarms::get)
        if (existing != null && existing.enabled &&
            existing.triggerAtMillis == dueAt && existing.label == label
        ) {
            return runCatching { tasks.updateTask(task) }.isSuccess
        }

        val newId = runCatching { alarms.create(dueAt, label) }.getOrNull() ?: return false
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
