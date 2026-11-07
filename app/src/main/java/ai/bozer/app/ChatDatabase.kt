package ai.bozer.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatDatabase(context: Context, databaseName: String = "bozer_chat.db") : SQLiteOpenHelper(context, databaseName, null, 2) {
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createMemoryTable(db)
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

    fun save(message: ChatMessage) {
        val values = ContentValues().apply {
            put("role", message.role)
            put("content", message.content)
            put("created_at", message.createdAt)
        }
        writableDatabase.insert("messages", null, values)
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
            "created_at DESC", limit.toString()
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

    /** Clear chat turns while preserving explicitly remembered facts. */
    fun clearHistory() {
        writableDatabase.delete("messages", null, null)
    }

    fun clearRememberedFacts() {
        writableDatabase.delete("remembered_facts", null, null)
    }

    /** Clear all local conversation data in one atomic transaction. */
    fun clearAllLocalData() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("messages", null, null)
            writableDatabase.delete("remembered_facts", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }
}
