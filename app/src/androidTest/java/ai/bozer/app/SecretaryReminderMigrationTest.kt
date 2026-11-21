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
                    notes TEXT NOT NULL DEFAULT '',
                    due_at INTEGER,
                    priority INTEGER NOT NULL DEFAULT 1 CHECK(priority BETWEEN 0 AND 2),
                    status TEXT NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','DONE')),
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
                    enabled INTEGER NOT NULL DEFAULT 1 CHECK(enabled IN (0,1)),
                    created_at INTEGER NOT NULL,
                    repeat_days_mask INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO bozer_alarms (id, trigger_at, label, enabled, created_at, repeat_days_mask)
                VALUES (29, 2000000005000, 'Rappel v5', 1, 1000, 0)
                """.trimIndent()
            )
            legacy.execSQL(
                """
                INSERT INTO secretary_tasks
                    (id, title, notes, due_at, priority, status, created_at, updated_at)
                VALUES (17, 'Préparer le dossier', 'Notes déjà enregistrées', 2000000000000, 2, 'OPEN', 1000, 1000)
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
        val alarms = upgraded.bozerAlarms()
        assertEquals(1, alarms.size)
        assertEquals(29L, alarms.single().id)
        assertEquals("Rappel v5", alarms.single().label)

        upgraded.close()
        context.deleteDatabase("bozer_chat.db")
    }
}
