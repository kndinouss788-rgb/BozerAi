package ai.bozer.app

import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BozerBrainEndToEndTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

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
            SystemClock.sleep(1_000)
        }
        throw AssertionError("BOZER n'a pas enregistré de réponse dans le délai imparti.")
    }

    private fun assertRealReply(reply: String, turn: Int) {
        assertTrue("Réponse vide au tour " + turn, reply.isNotBlank())
        val lower = reply.lowercase()
        assertTrue(
            "Le moteur a renvoyé un message d'erreur au tour " + turn + " : " + reply,
            !lower.contains("je rencontre un problème avec mon moteur") &&
                !lower.contains("je n'ai pas pu générer") &&
                !lower.contains("modèle bozer n'est pas chargé")
        )
    }

    private fun sendMessage(text: String, turn: Int): Pair<String, Long> {
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 20_000)
        assertNotNull("Champ de saisie BOZER introuvable", field)
        val previous = assistantCount()
        field!!.click()
        field.setText(text)
        val send = device.wait(Until.findObject(By.text("↑")), 10_000)
        assertNotNull("Bouton d'envoi BOZER introuvable", send)
        val started = SystemClock.elapsedRealtime()
        send!!.click()
        val reply = waitForAssistantReply(previous)
        val elapsed = SystemClock.elapsedRealtime() - started
        assertRealReply(reply, turn)
        Log.i("BOZER_E2E", "turn_" + turn + "_latency_ms=" + elapsed + "; reply=" + reply.take(320).replace('\n', ' '))
        return reply to elapsed
    }

    @Test
    fun realLocalModelHandlesConversationMemoryAndPersistentHistory() {
        device.executeShellCommand("am force-stop ai.bozer.app")
        device.executeShellCommand("am start -n ai.bozer.app/.MainActivity")
        assertNotNull(
            "Le moteur llama.cpp n'a pas affiché l'état prêt. Vérifier le chargement du GGUF.",
            device.wait(Until.findObject(By.textContains("Cerveau local prêt")), 600_000)
        )

        val (greeting, greetingMs) = sendMessage("Salut BOZER ! Comment vas-tu aujourd'hui ?", 1)
        assertTrue("La salutation semble être une erreur", greeting.length >= 2)

        val (factReply, factMs) = sendMessage(
            "Quelle est la capitale du Burkina Faso ? Réponds directement.",
            2
        )
        assertTrue("BOZER n'a pas répondu correctement à la question factuelle : " + factReply,
            factReply.contains("Ouagadougou", ignoreCase = true))

        val (mathReply, mathMs) = sendMessage("Combien font 7 + 5 ? Réponds clairement.", 3)
        assertTrue("BOZER n'a pas répondu correctement au calcul 7 + 5 : " + mathReply, mathReply.contains("12"))

        val (memoryReply, memoryMs) = sendMessage(
            "Retiens cette information pour la suite : mon animal préféré est le chat. Réponds uniquement OK.",
            4
        )
        assertTrue(
            "BOZER n'a pas confirmé la mémorisation : " + memoryReply,
            memoryReply.contains("ok", ignoreCase = true) || memoryReply.contains("d'accord", ignoreCase = true)
        )
        assertTrue("La mémoire persistante n'a pas enregistré le fait explicite", rememberedFactCount() >= 1)

        val (recallReply, recallMs) = sendMessage(
            "Quel est mon animal préféré ? Réponds en un seul mot.",
            5
        )
        assertTrue("BOZER a oublié l'information précédente : " + recallReply, recallReply.contains("chat", ignoreCase = true))

        val (followUpReply, followUpMs) = sendMessage(
            "Pourquoi les chats ronronnent-ils ? Explique-moi simplement.",
            6
        )
        assertTrue("La réponse de suivi est trop courte : " + followUpReply, followUpReply.length >= 20)

        val (searchReply, searchMs) = sendMessage(
            "Recherche sur Internet : capitale du Burkina Faso",
            7
        )
        assertTrue("La recherche Android n'a pas joint de source réelle : " + searchReply,
            searchReply.contains("Sources Internet", ignoreCase = true) &&
                searchReply.contains("wikipedia.org", ignoreCase = true))
        val sourceLine = searchReply.lineSequence()
            .firstOrNull { it.contains("https://fr.wikipedia.org/") }.orEmpty()
        assertTrue("La source de recherche est absente de la réponse", sourceLine.isNotBlank())
        Log.i("BOZER_E2E", "search_source_verified=true; " + sourceLine)

        Log.i(
            "BOZER_E2E",
            "LATENCIES_MS greeting=" + greetingMs + " fact=" + factMs + " math=" + mathMs +
                " memory=" + memoryMs + " recall=" + recallMs + " follow_up=" + followUpMs +
                " search=" + searchMs
        )

        device.executeShellCommand("am force-stop ai.bozer.app")
        device.executeShellCommand("am start -n ai.bozer.app/.MainActivity")
        assertNotNull(
            "BOZER ne retrouve pas son modèle après redémarrage",
            device.wait(Until.findObject(By.textContains("Cerveau local prêt")), 180_000)
        )
        assertNotNull(
            "La salutation n'a pas été restaurée",
            device.wait(Until.findObject(By.text("Salut BOZER ! Comment vas-tu aujourd'hui ?")), 20_000)
        )
        assertNotNull(
            "Le rappel n'a pas été restauré",
            device.wait(Until.findObject(By.text("Quel est mon animal préféré ? Réponds en un seul mot.")), 20_000)
        )
        assertTrue("L'historique ne contient pas sept réponses", assistantCount() >= 7)
        assertTrue("La mémoire explicite n'a pas survécu au redémarrage", rememberedFactCount() >= 1)
        val (persistentRecall, persistentRecallMs) = sendMessage(
            "Quel est mon animal préféré ? Réponds en un seul mot.",
            8
        )
        assertTrue(
            "BOZER n'a pas retrouvé le fait mémorisé après redémarrage : " + persistentRecall,
            persistentRecall.contains("chat", ignoreCase = true)
        )
        Log.i("BOZER_E2E", "turn_8_persistent_memory_latency_ms=" + persistentRecallMs)

        val historyBeforeClear = assistantCount()
        device.wait(Until.findObject(By.text("⚙")), 10_000)?.click()
        assertNotNull("Le bouton Réglages est introuvable", device.wait(Until.findObject(By.text("Effacer la mémoire")), 10_000))
        device.findObject(By.text("Effacer la mémoire")).click()
        assertNotNull("La confirmation d'effacement n'est pas affichée",
            device.wait(Until.findObject(By.textContains("Effacer les informations mémorisées")), 10_000))
        device.findObject(By.text("Effacer")).click()
        val clearDeadline = SystemClock.elapsedRealtime() + 10_000
        while (rememberedFactCount() != 0 && SystemClock.elapsedRealtime() < clearDeadline) SystemClock.sleep(200)
        assertTrue("Les informations mémorisées n'ont pas été effacées depuis les réglages", rememberedFactCount() == 0)
        assertTrue("L'effacement de la mémoire a supprimé l'historique des conversations", assistantCount() == historyBeforeClear)
        device.findObject(By.text("Fermer"))?.click()
        Log.i("BOZER_E2E", "memory_clear_verified=true; conversation_history_preserved=true")

        val (openReply, openMs) = sendMessage("Ouvre les paramètres", 9)
        assertTrue("BOZER n'a pas confirmé l'ouverture des paramètres : " + openReply,
            openReply.contains("J'ouvre", ignoreCase = true))
        assertTrue("BOZER n'a pas réellement ouvert l'application Paramètres",
            device.wait(Until.hasObject(By.pkg("com.android.settings")), 15_000))
        Log.i("BOZER_E2E", "open_settings_verified=true; latency_ms=" + openMs)
        device.executeShellCommand("am force-stop com.android.settings")
        device.executeShellCommand("am start -n ai.bozer.app/.MainActivity")
        assertTrue("BOZER ne revient pas après ouverture d'une application externe",
            device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 15_000))
        Log.i("BOZER_E2E", "PASS: real local conversation, web search, persistent memory, safe memory clearing and Android app launch")
    }
}
