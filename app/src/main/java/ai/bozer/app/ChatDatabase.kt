package ai.bozer.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabase(
    context: Context,
    private val databaseName: String = "bozer_chat.db"
) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {

    private val sessionPreferences = context.getSharedPreferences(
        "bozer_chat_session_$databaseName",
        Context.MODE_PRIVATE
    )

    override fun onCreate(db: SQLiteDatabase) {
        createConversationTable(db)
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL DEFAULT 1,
                role TEXT NOT NULL CHECK(role IN ('user','assistant')),
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        createMemoryTable(db)
        createActionTable(db)
        createSecretaryTaskTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createMemoryTable(db)
        if (oldVersion < 3) createActionTable(db)
        if (oldVersion < 4) createSecretaryTaskTable(db)
        if (oldVersion < 5) {
            createConversationTable(db)
            db.execSQL(
                "ALTER TABLE messages ADD COLUMN conversation_id INTEGER NOT NULL DEFAULT 1"
            )
            db.execSQL(
                """INSERT OR IGNORE INTO conversations(id, title, created_at, updated_at)
                   VALUES(1, 'Conversation précédente', ${System.currentTimeMillis()}, ${System.currentTimeMillis()})"""
            )
        }
    }

    private fun createConversationTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """INSERT OR IGNORE INTO conversations(id, title, created_at, updated_at)
               VALUES(1, 'Nouvelle conversation', ${System.currentTimeMillis()}, ${System.currentTimeMillis()})"""
        )
    }

    private fun ensureActiveConversation(db: SQLiteDatabase): Long {
        val saved = sessionPreferences.getLong(KEY_ACTIVE_CONVERSATION, -1L)
        if (saved > 0L) {
            db.query(
                "conversations", arrayOf("id"), "id = ?", arrayOf(saved.toString()),
                null, null, null, "1"
            ).use { cursor ->
                if (cursor.moveToFirst()) return saved
            }
        }

        val latest = db.query(
            "conversations", arrayOf("id"), null, null, null, null, "updated_at DESC, id DESC", "1"
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else -1L }

        val selected = if (latest > 0L) latest else insertConversation(db, "Nouvelle conversation")
        sessionPreferences.edit().putLong(KEY_ACTIVE_CONVERSATION, selected).apply()
        return selected
    }

    private fun insertConversation(db: SQLiteDatabase, title: String): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("title", title.take(MAX_CONVERSATION_TITLE))
            put("created_at", now)
            put("updated_at", now)
        }
        return db.insert("conversations", null, values)
    }

    fun currentConversationId(): Long = ensureActiveConversation(writableDatabase)

    fun createConversation(): Long {
        val db = writableDatabase
        val id = insertConversation(db, "Nouvelle conversation")
        if (id > 0L) {
            sessionPreferences.edit().putLong(KEY_ACTIVE_CONVERSATION, id).apply()
        }
        return id
    }

    fun switchConversation(id: Long): Boolean {
        if (id <= 0L) return false
        val exists = readableDatabase.query(
            "conversations", arrayOf("id"), "id = ?", arrayOf(id.toString()),
            null, null, null, "1"
        ).use { it.moveToFirst() }
        if (exists) sessionPreferences.edit().putLong(KEY_ACTIVE_CONVERSATION, id).apply()
        return exists
    }

    fun conversations(searchQuery: String = "", limit: Int = 100): List<ConversationRecord> {
        val cleanQuery = searchQuery.trim().take(120)
        val selection: String?
        val args: Array<String>?
        if (cleanQuery.isBlank()) {
            selection = null
            args = null
        } else {
            selection = "c.title LIKE ? OR EXISTS (" +
                "SELECT 1 FROM messages m WHERE m.conversation_id = c.id AND m.content LIKE ?)"
            val like = "%$cleanQuery%"
            args = arrayOf(like, like)
        }

        val result = mutableListOf<ConversationRecord>()
        readableDatabase.rawQuery(
            """SELECT c.id, c.title, c.created_at, c.updated_at,
                 COALESCE((SELECT m.content FROM messages m
                    WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1), '') AS preview,
                 (SELECT COUNT(*) FROM messages count_m WHERE count_m.conversation_id = c.id) AS message_count
               FROM conversations c
               ${if (selection == null) "" else "WHERE $selection"}
               ORDER BY c.updated_at DESC, c.id DESC LIMIT ?""",
            if (args == null) arrayOf(limit.coerceIn(1, 200).toString())
            else args + limit.coerceIn(1, 200).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ConversationRecord(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    preview = cursor.getString(4),
                    messageCount = cursor.getInt(5),
                    createdAt = cursor.getLong(2),
                    updatedAt = cursor.getLong(3)
                )
            }
        }
        return result
    }

    /**
     * Deletes only the selected conversation and its messages. If it was active,
     * BOZER automatically creates a fresh empty conversation rather than
     * redirecting writes into another conversation silently.
     */
    fun deleteConversation(id: Long): Boolean {
        if (id <= 0L) return false
        val activeId = ensureActiveConversation(writableDatabase)
        val db = writableDatabase
        db.beginTransaction()
        val deleted: Boolean
        try {
            db.delete("messages", "conversation_id = ?", arrayOf(id.toString()))
            deleted = db.delete("conversations", "id = ?", arrayOf(id.toString())) > 0
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        if (deleted && activeId == id) {
            val replacement = insertConversation(db, "Nouvelle conversation")
            sessionPreferences.edit().putLong(KEY_ACTIVE_CONVERSATION, replacement).apply()
        } else if (deleted && conversations(limit = 1).isEmpty()) {
            val replacement = insertConversation(db, "Nouvelle conversation")
            sessionPreferences.edit().putLong(KEY_ACTIVE_CONVERSATION, replacement).apply()
        }
        return deleted
    }

    private fun createMemoryTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS remembered_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                content TEXT NOT NULL UNIQUE COLLATE NOCASE,
                created_at INTEGER NOT NULL
            )"""
        )
    }

    private fun createActionTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS action_journal (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                action_type TEXT NOT NULL,
                summary TEXT NOT NULL,
                status TEXT NOT NULL,
                detail TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
    }

    fun recordAction(entry: ActionLogEntry) {
        val values = ContentValues().apply {
            put("action_type", entry.actionType.take(40))
            put("summary", entry.summary.take(180))
            put("status", entry.status.take(40))
            put("detail", entry.detail.take(280))
            put("created_at", entry.createdAt)
        }
        writableDatabase.insert("action_journal", null, values)
    }

    fun actionHistory(limit: Int = 30): List<ActionLogEntry> {
        val result = mutableListOf<ActionLogEntry>()
        readableDatabase.query(
            "action_journal",
            arrayOf("id", "action_type", "summary", "status", "detail", "created_at"),
            null, null, null, null, "id DESC", limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ActionLogEntry(
                    id = cursor.getLong(0),
                    actionType = cursor.getString(1),
                    summary = cursor.getString(2),
                    status = cursor.getString(3),
                    detail = cursor.getString(4),
                    createdAt = cursor.getLong(5)
                )
            }
        }
        return result
    }

    fun clearActionHistory() {
        writableDatabase.delete("action_journal", null, null)
    }

    private fun createSecretaryTaskTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS secretary_tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                is_completed INTEGER NOT NULL DEFAULT 0,
                due_at INTEGER,
                created_at INTEGER NOT NULL
            )"""
        )
    }

    fun addSecretaryTask(title: String, dueAtMillis: Long? = null): Long {
        val normalized = title.trim().take(180)
        if (normalized.isBlank()) return -1L
        val values = ContentValues().apply {
            put("title", normalized)
            put("is_completed", 0)
            if (dueAtMillis != null) put("due_at", dueAtMillis) else putNull("due_at")
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert("secretary_tasks", null, values)
    }

    fun secretaryTasks(limit: Int = 100): List<SecretaryTask> {
        val tasks = mutableListOf<SecretaryTask>()
        readableDatabase.query(
            "secretary_tasks",
            arrayOf("id", "title", "is_completed", "due_at", "created_at"),
            null, null, null, null,
            "is_completed ASC, COALESCE(due_at, 9223372036854775807) ASC, id DESC",
            limit.coerceIn(1, 200).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val dueAt = if (cursor.isNull(3)) null else cursor.getLong(3)
                tasks += SecretaryTask(
                    id = cursor.getLong(0),
                    title = cursor.getString(1),
                    isCompleted = cursor.getInt(2) != 0,
                    dueAtMillis = dueAt,
                    createdAt = cursor.getLong(4)
                )
            }
        }
        return tasks
    }

    fun setSecretaryTaskCompleted(id: Long, completed: Boolean): Boolean {
        val values = ContentValues().apply { put("is_completed", if (completed) 1 else 0) }
        return writableDatabase.update(
            "secretary_tasks", values, "id = ?", arrayOf(id.toString())
        ) > 0
    }

    fun deleteSecretaryTask(id: Long): Boolean =
        writableDatabase.delete("secretary_tasks", "id = ?", arrayOf(id.toString())) > 0

    fun clearSecretaryTasks() {
        writableDatabase.delete("secretary_tasks", null, null)
    }

    fun history(limit: Int = 60): List<ChatMessage> {
        val result = mutableListOf<ChatMessage>()
        val conversationId = ensureActiveConversation(readableDatabase)
        readableDatabase.query(
            "messages", arrayOf("id", "role", "content", "created_at"),
            "conversation_id = ?", arrayOf(conversationId.toString()),
            null, null, "id DESC", limit.coerceIn(1, 200).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    ChatMessage(
                        id = cursor.getLong(0),
                        role = cursor.getString(1),
                        content = cursor.getString(2),
                        createdAt = cursor.getLong(3)
                    )
                )
            }
        }
        return result.asReversed()
    }

    fun save(message: ChatMessage): Boolean =
        save(message, currentConversationId())

    fun save(message: ChatMessage, conversationId: Long): Boolean {
        if (conversationId <= 0L) return false
        val db = writableDatabase
        val conversationExists = db.query(
            "conversations", arrayOf("id"), "id = ?",
            arrayOf(conversationId.toString()), null, null, null, "1"
        ).use { it.moveToFirst() }
        if (!conversationExists) return false

        val values = ContentValues().apply {
            put("conversation_id", conversationId)
            put("role", message.role)
            put("content", message.content)
            put("created_at", message.createdAt)
        }
        val insertedId = db.insert("messages", null, values)
        if (insertedId <= 0L) return false

        val update = ContentValues().apply {
            put("updated_at", System.currentTimeMillis())
            if (message.role == "user") {
                val currentTitle = db.query(
                    "conversations", arrayOf("title"), "id = ?",
                    arrayOf(conversationId.toString()), null, null, null, "1"
                ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else "" }
                if (currentTitle == "Nouvelle conversation" || currentTitle == "Conversation précédente") {
                    val firstUserMessage = message.content.trim().replace(Regex("\\s+"), " ")
                        .take(MAX_CONVERSATION_TITLE)
                    if (firstUserMessage.isNotBlank()) put("title", firstUserMessage)
                }
            }
        }
        db.update("conversations", update, "id = ?", arrayOf(conversationId.toString()))
        return true
    }

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
            "created_at DESC", limit.coerceIn(1, 100).toString()
        ).use { cursor ->
            while (cursor.moveToNext()) facts.add(cursor.getString(0))
        }
        return facts
    }

    /** Update one explicitly stored fact without silently overwriting another fact. */
    fun updateRememberedFact(oldContent: String, newContent: String): Boolean {
        val oldValue = oldContent.trim()
        val newValue = newContent.trim().take(240)
        if (oldValue.isBlank() || newValue.isBlank()) return false
        if (oldValue.equals(newValue, ignoreCase = true)) {
            return readableDatabase.query(
                "remembered_facts", arrayOf("id"), "content = ?", arrayOf(oldValue),
                null, null, null, "1"
            ).use { it.moveToFirst() }
        }
        val values = ContentValues().apply {
            put("content", newValue)
            put("created_at", System.currentTimeMillis())
        }
        return try {
            writableDatabase.updateWithOnConflict(
                "remembered_facts",
                values,
                "content = ?",
                arrayOf(oldValue),
                SQLiteDatabase.CONFLICT_IGNORE
            ) > 0
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            false
        }
    }

    /** Forget only the matching explicit fact. */
    fun forgetRememberedFact(content: String): Boolean {
        val value = content.trim()
        if (value.isBlank()) return false
        return writableDatabase.delete("remembered_facts", "content = ?", arrayOf(value)) > 0
    }

    /** Clear only the active conversation while preserving other threads and saved memories. */
    fun clearHistory() {
        val db = writableDatabase
        val conversationId = ensureActiveConversation(db)
        db.delete("messages", "conversation_id = ?", arrayOf(conversationId.toString()))
        val values = ContentValues().apply {
            put("title", "Nouvelle conversation")
            put("updated_at", System.currentTimeMillis())
        }
        db.update("conversations", values, "id = ?", arrayOf(conversationId.toString()))
    }

    fun clearRememberedFacts() {
        writableDatabase.delete("remembered_facts", null, null)
    }

    /** Clear all local conversation data in one atomic transaction. */
    fun clearAllLocalData() {
        val db = writableDatabase
        db.beginTransaction()
        var replacementId = -1L
        try {
            db.delete("messages", null, null)
            db.delete("conversations", null, null)
            db.delete("remembered_facts", null, null)
            db.delete("action_journal", null, null)
            db.delete("secretary_tasks", null, null)
            replacementId = insertConversation(db, "Nouvelle conversation")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        if (replacementId > 0L) {
            sessionPreferences.edit().putLong(KEY_ACTIVE_CONVERSATION, replacementId).apply()
        }
    }

    private companion object {
        const val DATABASE_VERSION = 5
        const val KEY_ACTIVE_CONVERSATION = "active_conversation_id"
        const val MAX_CONVERSATION_TITLE = 56
    }
}
