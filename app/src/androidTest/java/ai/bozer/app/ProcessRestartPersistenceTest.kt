package ai.bozer.app

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProcessRestartPersistenceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assistantCount(): Int {
        val dbFile = context.getDatabasePath("bozer_chat.db")
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM messages WHERE role='assistant'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }
    }

    private fun lastAssistantReply(): String {
        val dbFile = context.getDatabasePath("bozer_chat.db")
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT content FROM messages WHERE role='assistant' ORDER BY id DESC LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }
        }
    }

    @Test
    fun processRestartRestoresHistoryMemoryAndLocalModel() {
        val database = ChatDatabase(context)
        val history = database.history()
        val facts = database.rememberedFacts()
        database.close()

        assertTrue("L'historique n'a pas survécu à l'arrêt du processus",
            history.any { it.content == "Quel est mon animal préféré ? Réponds en un seul mot." })
        assertTrue("La mémoire explicite n'a pas survécu à l'arrêt du processus",
            facts.any { it.contains("mon animal préféré est le chat", ignoreCase = true) })

        composeRule.onNodeWithTag("bozer_settings").performClick()
        composeRule.waitUntil(timeoutMillis = 180_000) {
            val ready = composeRule.onAllNodesWithText("Cerveau local prêt — hors ligne.")
                .fetchSemanticsNodes().isNotEmpty()
            val failed = listOf("Chargement impossible", "Installation impossible").any { label ->
                composeRule.onAllNodesWithText(label, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            ready || failed
        }
        val ready = composeRule.onAllNodesWithText("Cerveau local prêt — hors ligne.")
            .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Le modèle local n'a pas été rechargé après redémarrage.", ready)
        composeRule.onNodeWithText("Fermer").performClick()

        val input = composeRule.onNodeWithTag("bozer_input")
        val previous = assistantCount()
        input.performTextInput("Quel est mon animal préféré ? Réponds en un seul mot.")
        val started = SystemClock.elapsedRealtime()
        input.performImeAction()

        val deadline = SystemClock.elapsedRealtime() + 300_000
        while (SystemClock.elapsedRealtime() < deadline && assistantCount() <= previous) {
            SystemClock.sleep(500)
        }
        assertTrue("BOZER n'a pas répondu après redémarrage du processus", assistantCount() > previous)
        val reply = lastAssistantReply()
        val latency = SystemClock.elapsedRealtime() - started
        assertTrue("BOZER n'a pas rappelé la mémoire après redémarrage : $reply",
            reply.contains("chat", ignoreCase = true))

        Log.i("BOZER_E2E", "PASS: process restart restored history, memory and model; recall_latency_ms=$latency; reply=" +
            reply.take(320).replace('\n', ' '))
    }
}
