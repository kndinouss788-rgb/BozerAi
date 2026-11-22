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
    fun actionJournalRecordsResultsWithoutRetainingPrivateMessageContent() {
        val entry = ActionLogEntry(
            actionType = "PRÉPARER_SMS",
            summary = "Préparer un brouillon SMS",
            status = "BROUILLON OUVERT",
            detail = "L'application SMS a été ouverte. Le message n'a pas été envoyé.",
            createdAt = 456L
        )
        database.recordAction(entry)
        val stored = database.actionHistory().single()
        assertEquals("PRÉPARER_SMS", stored.actionType)
        assertEquals("Préparer un brouillon SMS", stored.summary)
        assertEquals("BROUILLON OUVERT", stored.status)
        assertEquals(456L, stored.createdAt)
        assertFalse("Le journal ne doit pas contenir le contenu d'un SMS.", stored.detail.contains("Je serai en retard"))

        database.clearActionHistory()
        assertTrue(database.actionHistory().isEmpty())
    }

    @Test
    fun secretaryTasksPersistAndCanBeCompletedAndDeleted() {
        val id = database.addSecretaryTask("Terminer le dossier BOZER")
        assertTrue("La tâche doit être enregistrée dans SQLite.", id > 0L)
        var tasks = database.secretaryTasks()
        assertEquals(1, tasks.size)
        assertEquals("Terminer le dossier BOZER", tasks.single().title)
        assertFalse(tasks.single().isCompleted)

        assertTrue(database.setSecretaryTaskCompleted(id, true))
        tasks = database.secretaryTasks()
        assertTrue(tasks.single().isCompleted)

        assertTrue(database.setSecretaryTaskCompleted(id, false))
        assertTrue(database.deleteSecretaryTask(id))
        assertTrue(database.secretaryTasks().isEmpty())
    }

    @Test
    fun conversationsCanBeSearchedSwitchedAndDeletedIndependently() {
        val firstId = database.currentConversationId()
        database.save(ChatMessage(role = "user", content = "Projet BOZER histoire"))
        database.save(ChatMessage(role = "assistant", content = "Nous construisons BOZER."))
        val firstRecord = database.conversations().first { it.id == firstId }
        assertEquals("Projet BOZER histoire", firstRecord.title)
        assertEquals(2, firstRecord.messageCount)

        val secondId = database.createConversation()
        database.save(ChatMessage(role = "user", content = "Préparer un voyage"))
        assertEquals(1, database.history().size)
        assertEquals("Préparer un voyage", database.conversations().first { it.id == secondId }.title)

        assertTrue(database.switchConversation(firstId))
        assertEquals(2, database.history().size)
        assertTrue(database.conversations("histoire").any { it.id == firstId })

        assertTrue(database.deleteConversation(firstId))
        assertTrue("La suppression du fil actif doit créer une conversation vide.", database.history().isEmpty())
        assertFalse(database.conversations().any { it.id == firstId })
        assertTrue("L'autre conversation doit rester intacte.", database.conversations().any { it.id == secondId })
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
        database.recordAction(
            ActionLogEntry(
                actionType = "OUVRIR_APPLICATION",
                summary = "Ouvrir Paramètres",
                status = "OUVERTE",
                detail = "L'application Paramètres a été ouverte."
            )
        )
        assertEquals(1, database.actionHistory().size)
    }
}
