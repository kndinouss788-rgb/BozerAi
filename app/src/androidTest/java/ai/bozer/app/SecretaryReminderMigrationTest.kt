package ai.bozer.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecretaryReminderMigrationTest {
    @Test
    fun upgradesVersionFiveSecretaryTasksWithoutLosingExistingRows() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("bozer_chat.db")
        val file = context.getDatabasePath("bozer_chat.db")
        file.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(file, null).use { legacy ->
            legacy.execSQL(
                """
                CREATE TABLE secretary_tasks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    notes TEXT NOT NULL,
                    due_at INTEGER,
                    priority INTEGER NOT NULL DEFAULT 1,
                    status INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                CREATE TABLE bozer_alarms (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    trigger_at INTEGER NOT NULL,
                    label TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    repeat_days_mask INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO secretary_tasks
                    (id, title, notes, due_at, priority, status, created_at, updated_at)
                VALUES (17, 'Préparer le dossier', 'Notes déjà enregistrées', 2000000000000, 2, 0, 1000, 1000)
                """.trimIndent()
            )
            legacy.version = 5
        }

        val upgraded = ChatDatabase(context)
        val tasks = SecretaryRepository(upgraded).tasks()

        assertEquals(1, tasks.size)
        assertEquals(17L, tasks.single().id)
        assertEquals("Préparer le dossier", tasks.single().title)
        assertEquals("Notes déjà enregistrées", tasks.single().notes)
        assertNull(tasks.single().reminderAlarmId)

        upgraded.close()
        context.deleteDatabase("bozer_chat.db")
    }
}
