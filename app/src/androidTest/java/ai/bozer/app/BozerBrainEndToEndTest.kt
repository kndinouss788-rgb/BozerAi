package ai.bozer.app

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.onAllNodesWithTag
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
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class BozerBrainEndToEndTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun assistantCount(): Int {
        val dbFile = context.getDatabasePath("bozer_chat.db")
        assertTrue("La base de conversations n'existe pas", dbFile.isFile)
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM messages WHERE role='assistant'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
        }
    }

    private fun rememberedFactCount(): Int {
        val dbFile = context.getDatabasePath("bozer_chat.db")
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM remembered_facts", null).use { cursor ->
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

    private fun waitForAssistantReply(previousCount: Int, timeoutMs: Long = 300_000): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (assistantCount() > previousCount) return lastAssistantReply()
            SystemClock.sleep(500)
        }
        throw AssertionError("BOZER n'a pas enregistré de réponse dans le délai imparti.")
    }

    private fun waitForModelReady() {
        composeRule.onNodeWithTag("bozer_settings").performClick()
        val readyBeforeInstall = composeRule.onAllNodesWithText("Cerveau local prêt sur ce téléphone.")
            .fetchSemanticsNodes().isNotEmpty()
        if (!readyBeforeInstall) {
            val installButtonVisible = composeRule.onAllNodesWithText("Installer le cerveau local")
                .fetchSemanticsNodes().isNotEmpty()
            if (installButtonVisible) {
                composeRule.onNodeWithText("Installer le cerveau local").performClick()
            }
        }
        composeRule.waitUntil(timeoutMillis = 300_000) {
            val ready = composeRule.onAllNodesWithText("Cerveau local prêt sur ce téléphone.")
                .fetchSemanticsNodes().isNotEmpty()
            val failed = listOf("Chargement impossible", "Installation impossible").any { label ->
                composeRule.onAllNodesWithText(label, substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
            }
            ready || failed
        }
        val ready = composeRule.onAllNodesWithText("Cerveau local prêt sur ce téléphone.")
            .fetchSemanticsNodes().isNotEmpty()
        assertTrue("Le chargement du modèle local a échoué ou dépassé 180 secondes.", ready)
        composeRule.onNodeWithText("Fermer").performClick()
    }

    private fun assertRealReply(reply: String, turn: Int) {
        assertTrue("Réponse vide au tour $turn", reply.isNotBlank())
        val lower = reply.lowercase()
        assertTrue(
            "Le moteur a renvoyé une erreur au tour $turn : $reply",
            !lower.contains("je rencontre un problème avec mon moteur") &&
                !lower.contains("je n'ai pas pas pu générer") &&
                !lower.contains("je n'ai pas pu terminer cette réponse") &&
                !lower.contains("le modèle bozer n'est pas chargé") &&
                !lower.contains("le cerveau local n'est pas encore installé")
        )
    }

    private fun sendMessage(text: String, turn: Int): Pair<String, Long> {
        val input = composeRule.onNodeWithTag("bozer_input")
        val previous = assistantCount()
        input.performTextInput(text)
        val started = SystemClock.elapsedRealtime()
        input.performImeAction()
        val reply = waitForAssistantReply(previous)
        val elapsed = SystemClock.elapsedRealtime() - started
        assertRealReply(reply, turn)
        Log.i("BOZER_E2E", "turn_" + turn + "_latency_ms=" + elapsed +
            "; reply=" + reply.take(320).replace('\n', ' '))
        return reply to elapsed
    }

    @Test
    fun realLocalModelHandlesConversationMemoryAndPersistentHistory() {
        val manager = LocalModelManager(
            context,
            "http://127.0.0.1:8765/${LocalModelManager.MODEL_FILENAME}"
        )
        var downloadedBytes = 0L
        runBlocking {
            manager.download { downloaded, _ -> downloadedBytes = downloaded }
            assertTrue("Le modèle téléchargé n'a pas passé la vérification SHA-256.", manager.verifyInstalled())
        }
        assertTrue(
            "Le téléchargement réel du GGUF n'a pas atteint sa taille attendue.",
            downloadedBytes == LocalModelManager.MODEL_BYTES
        )
        Log.i(
            "BOZER_MODEL",
            "downloaded_bytes=$downloadedBytes; sha256=${LocalModelManager.MODEL_SHA256}; verified=true"
        )

        assertTrue(
            "Le champ de saisie BOZER est absent",
            composeRule.onAllNodesWithTag("bozer_input").fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "L'écran d'accueil BOZER est absent",
            composeRule.onAllNodesWithTag("bozer_welcome").fetchSemanticsNodes().isNotEmpty()
        )
        waitForModelReady()

        val (greeting, greetingMs) = sendMessage("Salut BOZER ! Comment vas-tu aujourd'hui ?", 1)
        assertTrue("La salutation semble être une erreur", greeting.length >= 2)

        val (factReply, factMs) = sendMessage(
            "Quelle est la capitale du Burkina Faso ? Réponds directement.",
            2
        )
        assertTrue(
            "BOZER n'a pas répondu correctement à la question factuelle : $factReply",
            factReply.contains("Ouagadougou", ignoreCase = true)
        )

        val (mathReply, mathMs) = sendMessage("Combien font 7 + 5 ? Réponds clairement.", 3)
        assertTrue("BOZER n'a pas répondu correctement au calcul 7 + 5 : $mathReply", mathReply.contains("12"))

        val (memoryReply, memoryMs) = sendMessage(
            "Retiens cette information pour la suite : mon animal préféré est le chat. Réponds uniquement OK.",
            4
        )
        assertTrue(
            "BOZER n'a pas confirmé la mémorisation : $memoryReply",
            memoryReply.contains("ok", ignoreCase = true) || memoryReply.contains("d'accord", ignoreCase = true)
        )
        assertTrue("La mémoire persistante n'a pas enregistré le fait explicite", rememberedFactCount() >= 1)

        val (recallReply, recallMs) = sendMessage(
            "Quel est mon animal préféré ? Réponds en un seul mot.",
            5
        )
        assertTrue("BOZER a oublié l'information précédente : $recallReply",
            recallReply.contains("chat", ignoreCase = true))

        val (followUpReply, followUpMs) = sendMessage(
            "Et pourquoi ronronnent-ils ? Explique-moi simplement.",
            6
        )
        assertTrue("La réponse de suivi est trop courte : $followUpReply", followUpReply.length >= 20)
        assertTrue(
            "BOZER n'a pas résolu le pronom du suivi à partir du contexte précédent : $followUpReply",
            followUpReply.contains("chat", ignoreCase = true)
        )

        val (searchReply, searchMs) = sendMessage(
            "Recherche sur Internet : capitale du Burkina Faso",
            7
        )
        assertTrue(
            "La recherche Android n'a pas joint de source réelle : $searchReply",
            searchReply.contains("Sources Internet", ignoreCase = true) &&
                searchReply.contains("wikipedia.org", ignoreCase = true)
        )
        val sourceLine = searchReply.lineSequence()
            .firstOrNull { it.contains("https://fr.wikipedia.org/") }.orEmpty()
        assertTrue("La source de recherche est absente de la réponse", sourceLine.isNotBlank())
        Log.i("BOZER_E2E", "search_source_verified=true; $sourceLine")

        assertTrue("L'historique local ne contient pas sept réponses", assistantCount() >= 7)
        assertTrue("La mémoire explicite n'est pas persistante", rememberedFactCount() >= 1)
        Log.i(
            "BOZER_E2E",
            "LATENCIES_MS greeting=$greetingMs fact=$factMs math=$mathMs memory=$memoryMs " +
                "recall=$recallMs follow_up=$followUpMs search=$searchMs"
        )
        Log.i(
            "BOZER_E2E",
            "PASS: seven local turns, factual answer, arithmetic, explicit memory, contextual follow-up and real network search"
        )
    }
}
