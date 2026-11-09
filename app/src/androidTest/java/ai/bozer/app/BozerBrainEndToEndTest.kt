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

    private fun submitPhoneAction(command: String, dialogTitle: String) {
        val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 20_000)
        assertNotNull("Champ de saisie BOZER introuvable pour l'action Android", field)
        field!!.click()
        field.setText(command)
        val send = device.wait(Until.findObject(By.text("↑")), 10_000)
        assertNotNull("Bouton d'envoi BOZER introuvable pour l'action Android", send)
        send!!.click()
        assertNotNull(
            "BOZER n'a pas demandé de confirmation pour : " + command,
            device.wait(Until.findObject(By.text(dialogTitle)), 10_000)
        )
    }

    @Test
    fun realLocalModelHandlesConversationMemoryAndPersistentHistory() {
        device.executeShellCommand("am force-stop ai.bozer.app")
        device.executeShellCommand("am start -n ai.bozer.app/.MainActivity")
        assertNotNull(
            "Le moteur llama.cpp n'a pas affiché l'état prêt. Vérifier le chargement du GGUF.",
            device.wait(Until.findObject(By.text("Ton compagnon intelligent")), 600_000)
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
            device.wait(Until.findObject(By.text("Ton compagnon intelligent")), 180_000)
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

        // User-visible memory clearing must not erase conversation history.
        val historyBeforeMemoryClear = assistantCount()
        device.wait(Until.findObject(By.text("⚙")), 10_000)?.click()
        assertNotNull(
            "Le réglage de mémoire est introuvable",
            device.wait(Until.findObject(By.textContains("Gérer les souvenirs")), 10_000)
        )
        device.findObject(By.textContains("Gérer les souvenirs")).click()
        assertNotNull(
            "Le fait explicite n'apparaît pas dans la mémoire locale",
            device.wait(Until.findObject(By.textContains("animal préféré est le chat")), 10_000)
        )
        device.wait(Until.findObject(By.text("Fermer")), 5_000)?.click()
        device.wait(Until.findObject(By.text("⚙")), 5_000)?.click()
        device.findObject(By.text("Effacer la mémoire")).click()
        assertNotNull(
            "La confirmation d'effacement de mémoire n'est pas affichée",
            device.wait(Until.findObject(By.text("Effacer la mémoire ?")), 10_000)
        )
        device.findObject(By.text("Effacer")).click()
        val memoryClearDeadline = SystemClock.elapsedRealtime() + 10_000
        while (rememberedFactCount() != 0 && SystemClock.elapsedRealtime() < memoryClearDeadline) {
            SystemClock.sleep(200)
        }
        assertTrue("La mémoire explicite n'a pas été effacée depuis l'interface", rememberedFactCount() == 0)
        assertTrue(
            "L'effacement de la mémoire a supprimé l'historique",
            assistantCount() == historyBeforeMemoryClear
        )
        Log.i("BOZER_E2E", "memory_clear_verified=true; history_preserved=true")

        // The app must not open the settings screen until the user confirms.
        submitPhoneAction("Ouvre les paramètres", "Ouvrir les paramètres ?")
        assertTrue("L'action a quitté BOZER avant confirmation", device.hasObject(By.pkg("ai.bozer.app")))
        device.findObject(By.text("Confirmer")).click()
        assertTrue(
            "L'application Paramètres n'a pas été ouverte après confirmation",
            device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000)
        )
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 10_000))
        Log.i("BOZER_E2E", "open_settings_confirmation_verified=true")

        // A dial request must be cancelled without opening the dialer.
        submitPhoneAction("Appelle +226 70 12 34 56", "Ouvrir le composeur ?")
        assertTrue("Le composeur s'est ouvert avant confirmation", device.hasObject(By.pkg("ai.bozer.app")))
        device.findObject(By.text("Annuler")).click()
        assertTrue("BOZER n'est pas resté ouvert après annulation", device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 5_000))
        Log.i("BOZER_E2E", "sensitive_call_cancel_verified=true; no_call_placed=true")

        // A text message must likewise remain only a proposed action until consent.
        submitPhoneAction("Envoie un SMS à +226 70 12 34 56 : Bonjour", "Préparer un SMS ?")
        assertTrue("Le composeur SMS s'est ouvert avant confirmation", device.hasObject(By.pkg("ai.bozer.app")))
        device.findObject(By.text("Annuler")).click()
        assertTrue("BOZER n'est pas resté ouvert après annulation du SMS", device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 5_000))
        Log.i("BOZER_E2E", "sensitive_sms_cancel_verified=true; no_message_sent=true")

        // An alarm is also prepared only after an explicit confirmation.
        val alarmAction = PhoneAction.SetAlarm(6, 45)
        val clockPackage = PhoneActionPlanner.createIntent(context, alarmAction)
            ?.resolveActivity(context.packageManager)?.packageName
        assertNotNull("Aucune application Horloge ne prend en charge les alarmes", clockPackage)
        submitPhoneAction("Mets un réveil à 6h45", "Préparer un réveil ?")
        assertNotNull(
            "L'heure demandée n'apparaît pas dans la confirmation",
            device.wait(Until.findObject(By.textContains("06:45")), 5_000)
        )
        device.findObject(By.text("Confirmer")).click()
        assertTrue(
            "L'application Horloge n'a pas été ouverte après confirmation",
            device.wait(Until.hasObject(By.pkg(clockPackage!!)), 10_000)
        )
        device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.pkg("ai.bozer.app")), 10_000))
        Log.i("BOZER_E2E", "alarm_confirmation_verified=true; clock_package=" + clockPackage)

        Log.i("BOZER_E2E", "PASS: seven local turns, factual answer, arithmetic, persistent memory, follow-up, real network search, restored history, memory clearing, confirmed app/alarm actions and cancelled sensitive actions")
    }
}
