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

    private fun lastAssistantReply(): String {
        val dbFile = context.getDatabasePath("bozer_chat.db")
        return SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT content FROM messages WHERE role='assistant' ORDER BY id DESC LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }
        }
    }

    private fun waitForAssistantReply(previousCount: Int, timeoutMs: Long = 600_000): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (assistantCount() > previousCount) return lastAssistantReply()
            SystemClock.sleep(1_000)
        }
        throw AssertionError("BOZER n'a pas enregistré de réponse dans le délai imparti.")
    }

    private fun sendMessage(text: String): Pair<String, Long> {
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
        assertTrue("BOZER a enregistré une réponse vide", reply.isNotBlank())
        return reply to elapsed
    }

    @Test
    fun realLocalModelCarriesContextAcrossTurnsAndHistorySurvivesRestart() {
        device.executeShellCommand("am force-stop ai.bozer.app")
        device.executeShellCommand("am start -n ai.bozer.app/.MainActivity")
        assertNotNull(
            "Le moteur llama.cpp n'a pas affiché l'état prêt. Vérifier le chargement du GGUF.",
            device.wait(Until.findObject(By.textContains("Cerveau local prêt")), 600_000)
        )

        val firstPrompt = "Mon animal préféré est le chat. Garde cette information pour la suite et réponds uniquement : OK."
        val secondPrompt = "Quel animal ai-je dit préférer ? Réponds par un seul mot."
        val (firstReply, firstMs) = sendMessage(firstPrompt)
        Log.i("BOZER_E2E", "turn_1_latency_ms=$firstMs; reply_chars=${firstReply.length}")
        val (secondReply, secondMs) = sendMessage(secondPrompt)
        Log.i("BOZER_E2E", "turn_2_latency_ms=$secondMs; reply_chars=${secondReply.length}; context_check=${secondReply.contains("chat", ignoreCase = true)}")
        assertTrue("La deuxième réponse est vide", secondReply.isNotBlank())
        assertTrue(
            "BOZER n'a pas réutilisé l'information du premier tour : $secondReply",
            secondReply.contains("chat", ignoreCase = true)
        )

        device.executeShellCommand("am force-stop ai.bozer.app")
        device.executeShellCommand("am start -n ai.bozer.app/.MainActivity")
        assertNotNull(
            "BOZER ne retrouve pas son modèle après redémarrage",
            device.wait(Until.findObject(By.textContains("Cerveau local prêt")), 180_000)
        )
        assertNotNull("Le premier message n'a pas été restauré", device.wait(Until.findObject(By.text(firstPrompt)), 20_000))
        assertNotNull("Le deuxième message n'a pas été restauré", device.wait(Until.findObject(By.text(secondPrompt)), 20_000))
        assertTrue("L'historique ne contient pas deux réponses réelles", assistantCount() >= 2)
        Log.i("BOZER_E2E", "PASS: two real local inference turns, context recall, latency measurements and persistent history; total_assistant_replies=${assistantCount()}")
    }
}
