package ai.bozer.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ChatDatabaseInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "bozer-test-${UUID.randomUUID()}.db"
    private lateinit var database: ChatDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(databaseName)
        database = ChatDatabase(context, databaseName)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun explicitFactsCanBeCorrectedForgottenAndClearedIndependentlyOfHistory() {
        database.save(ChatMessage(role = "user", content = "Bonjour"))
        database.rememberFact("Animal préféré : chat")
        database.rememberFact("Ville préférée : Ouagadougou")

        assertTrue(
            database.updateRememberedFact(
                "Animal préféré : chat",
                "Animal préféré : chien"
            )
        )
        assertTrue(database.rememberedFacts().contains("Animal préféré : chien"))
        assertFalse(database.rememberedFacts().contains("Animal préféré : chat"))

        // A correction must not silently overwrite a different existing fact.
        assertFalse(
            database.updateRememberedFact(
                "Animal préféré : chien",
                "Ville préférée : Ouagadougou"
            )
        )
        assertTrue(database.rememberedFacts().contains("Animal préféré : chien"))

        assertTrue(database.forgetRememberedFact("Animal préféré : chien"))
        assertFalse(database.forgetRememberedFact("Animal préféré : chien"))

        database.rememberFact("Langue : français")
        database.clearHistory()
        assertTrue(database.history().isEmpty())
        assertTrue(database.rememberedFacts().contains("Langue : français"))

        database.clearAllLocalData()
        assertTrue(database.history().isEmpty())
        assertTrue(database.rememberedFacts().isEmpty())
    }

    @Test
    fun versionOneMigrationPreservesExistingMessagesAndAddsMemoryTable() {
        database.close()
        context.deleteDatabase(databaseName)
        val file = context.getDatabasePath(databaseName)
        file.parentFile?.mkdirs()
        val legacy = SQLiteDatabase.openOrCreateDatabase(file, null)
        legacy.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL CHECK(role IN ('user','assistant')),
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )"""
        )
        legacy.execSQL(
            "INSERT INTO messages(role, content, created_at) VALUES('user', 'Message historique', 123)"
        )
        legacy.version = 1
        legacy.close()

        database = ChatDatabase(context, databaseName)
        assertEquals(
            listOf(ChatMessage(id = 1, role = "user", content = "Message historique", createdAt = 123)),
            database.history()
        )

        database.rememberFact("Projet : BOZER")
        assertEquals(listOf("Projet : BOZER"), database.rememberedFacts())
    }
    @Test
    fun identicalCorrectionIsSafeAndDoesNotCreateDuplicateFacts() {
        database.rememberFact("Langue : français")
        assertTrue(database.updateRememberedFact("Langue : français", "Langue : français"))
        assertFalse(database.updateRememberedFact("Fait absent", "Fait corrigé"))
        assertEquals(1, database.rememberedFacts().count { it.equals("Langue : français", ignoreCase = true) })
    }

}
