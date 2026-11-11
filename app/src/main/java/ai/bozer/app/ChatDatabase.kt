package ai.bozer.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabase(context: Context) : SQLiteOpenHelper(context, "bozer_chat.db", null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL CHECK(role IN ('user','assistant')),
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        createMemoryTable(db)
        createSecretaryTable(db)
        createBozerAlarmTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createMemoryTable(db)
        if (oldVersion < 3) createSecretaryTable(db)
        if (oldVersion < 4) createBozerAlarmTable(db)
        if (oldVersion < 5) db.execSQL("ALTER TABLE bozer_alarms ADD COLUMN repeat_days_mask INTEGER NOT NULL DEFAULT 0")
    }

    private fun createSecretaryTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS secretary_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                notes TEXT NOT NULL DEFAULT '',
                due_at INTEGER,
                priority INTEGER NOT NULL DEFAULT 1 CHECK(priority BETWEEN 0 AND 2),
                status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','DONE')),
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_secretary_tasks_due ON secretary_tasks(status, due_at)")
    }

    private fun createBozerAlarmTable(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS bozer_alarms (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            trigger_at INTEGER NOT NULL,
            label TEXT NOT NULL,
            enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
            created_at INTEGER NOT NULL,
            repeat_days_mask INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bozer_alarms_enabled_time ON bozer_alarms(enabled, trigger_at)")
    }

    fun insertBozerAlarm(triggerAtMillis: Long, label: String, repeatDaysMask: Int = 0): Long {
        require(triggerAtMillis > System.currentTimeMillis()) { "L'heure de l'alarme doit être dans le futur." }
        val values = ContentValues().apply {
            put("trigger_at", triggerAtMillis)
            put("label", label.trim().take(120).ifBlank { "Rappel BOZER" })
            put("enabled", 1)
            put("created_at", System.currentTimeMillis())
            put("repeat_days_mask", repeatDaysMask)
        }
        return writableDatabase.insertOrThrow("bozer_alarms", null, values)
    }

    fun bozerAlarm(id: Long): BozerAlarm? =
        readableDatabase.query("bozer_alarms", arrayOf("id","trigger_at","label","enabled","repeat_days_mask"),
            "id = ?", arrayOf(id.toString()), null, null, null).use { c ->
            if (!c.moveToFirst()) null else BozerAlarm(c.getLong(0), c.getLong(1), c.getString(2), c.getInt(3) == 1, c.getInt(4))
        }

    fun enabledBozerAlarms(): List<BozerAlarm> {
        val result = mutableListOf<BozerAlarm>()
        readableDatabase.query("bozer_alarms", arrayOf("id","trigger_at","label","enabled","repeat_days_mask"),
            "enabled = 1 AND trigger_at > ?", arrayOf(System.currentTimeMillis().toString()),
            null, null, "trigger_at ASC").use { c ->
            while (c.moveToNext()) result += BozerAlarm(c.getLong(0), c.getLong(1), c.getString(2), true, c.getInt(4))
        }
        return result
    }

    fun updateBozerAlarmTrigger(id: Long, triggerAtMillis: Long): Boolean {
        val values = ContentValues().apply { put("trigger_at", triggerAtMillis) }
        return writableDatabase.update("bozer_alarms", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun setBozerAlarmEnabled(id: Long, enabled: Boolean): Boolean {
        val values = ContentValues().apply { put("enabled", if (enabled) 1 else 0) }
        return writableDatabase.update("bozer_alarms", values, "id = ?", arrayOf(id.toString())) > 0
    }

    fun deleteBozerAlarm(id: Long): Boolean =
        writableDatabase.delete("bozer_alarms", "id = ?", arrayOf(id.toString())) > 0

    private fun createMemoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS remembered_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL
            )"""
        )
    }

    fun history(limit: Int = 60): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        readableDatabase.query(
            "messages", arrayOf("id", "role", "content", "created_at"),
            null, null, null, null, "id DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(ChatMessage(
                    id = cursor.getLong(0),
                    role = cursor.getString(1),
                    content = cursor.getString(2),
                    createdAt = cursor.getLong(3)
                ))
            }
        }
        return result.asReversed()
    }

    fun clearHistory() {
        writableDatabase.delete("messages", null, null)
    }

    fun save(message: ChatMessage) {
        val values = ContentValues().apply {
            put("role", message.role)
            put("content", message.content)
            put("created_at", message.createdAt)
        }
        writableDatabase.insert("messages", null, values)
    }

    fun saveSecretaryTask(task: SecretaryTask): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", task.title.trim())
            put("notes", task.notes.trim())
            if (task.dueAtMillis == null) putNull("due_at") else put("due_at", task.dueAtMillis)
            put("priority", task.priority.level)
            put("status", if (task.completed) "DONE" else "OPEN")
            put("created_at", if (task.id == 0L) now else task.createdAtMillis)
            put("updated_at", now)
        }
        return if (task.id == 0L) {
            writableDatabase.insertOrThrow("secretary_tasks", null, values)
        } else {
            writableDatabase.update("secretary_tasks", values, "id = ?", arrayOf(task.id.toString()))
            task.id
        }
    }

    fun secretaryTasks(includeCompleted: Boolean = true): List<SecretaryTask> {
        val tasks = mutableListOf<SecretaryTask>()
        val selection = if (includeCompleted) null else "status = ?"
        val args = if (includeCompleted) null else arrayOf("OPEN")
        readableDatabase.query(
            "secretary_tasks",
            arrayOf("id", "title", "notes", "due_at", "priority", "status", "created_at"),
            selection, args, null, null,
            "CASE WHEN status = 'DONE' THEN 1 ELSE 0 END, CASE WHEN due_at IS NULL THEN 1 ELSE 0 END, due_at ASC, priority DESC, id DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val dueAt = if (cursor.isNull(3)) null else cursor.getLong(3)
                tasks.add(
                    SecretaryTask(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        notes = cursor.getString(2),
                        dueAtMillis = dueAt,
                        priority = SecretaryPriority.fromLevel(cursor.getInt(4)),
                        completed = cursor.getString(5) == "DONE",
                        createdAtMillis = cursor.getLong(6)
                    )
                )
            }
        }
        return tasks
    }

    fun setSecretaryTaskCompleted(taskId: Long, completed: Boolean): Boolean {
        val values = ContentValues().apply {
            put("status", if (completed) "DONE" else "OPEN")
            put("updated_at", System.currentTimeMillis())
        }
        return writableDatabase.update("secretary_tasks", values, "id = ?", arrayOf(taskId.toString())) > 0
    }

    fun deleteSecretaryTask(taskId: Long): Boolean =
        writableDatabase.delete("secretary_tasks", "id = ?", arrayOf(taskId.toString())) > 0

    /** Save only facts the user explicitly asks BOZER to remember. */
    fun rememberFact(content: String) {
        val normalized = content.trim().take(240)
        if (normalized.isBlank()) return
        val values = ContentValues().apply {
            put("content", normalized)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            "remembered_facts", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun rememberedFacts(limit: Int = 20): List<String> {
        val facts = mutableListOf<String>()
        readableDatabase.query(
            "remembered_facts", arrayOf("content"), null, null, null, null,
            "created_at DESC", limit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) facts.add(cursor.getString(0))
        }
        return facts
    }

    fun clearRememberedFacts() {
        writableDatabase.delete("remembered_facts", null, null)
    }
}
