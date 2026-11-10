package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidCommandParserTest {
    @Test
    fun parsesDialAndPreparedSms() {
        assertEquals(AndroidCommand.Dial("+22670123456"), AndroidCommandParser.parse("appelle +22670123456"))
        assertEquals(
            AndroidCommand.ComposeSms("+22670123456", "Je serai en retard"),
            AndroidCommandParser.parse("envoie un message à +22670123456 disant Je serai en retard")
        )
    }

    @Test
    fun parsesAlarmTimerAndNavigation() {
        assertEquals(AndroidCommand.SetAlarm(7, 30, "BOZER"), AndroidCommandParser.parse("programme une alarme à 7h30"))
        assertEquals(AndroidCommand.SetTimer(300, "BOZER"), AndroidCommandParser.parse("lance un minuteur 5 minutes"))
        assertEquals(AndroidCommand.Navigate("la gare"), AndroidCommandParser.parse("itinéraire vers la gare"))
    }

    @Test
    fun parsesWebAndAppActions() {
        assertEquals(AndroidCommand.OpenUrl("https://example.com"), AndroidCommandParser.parse("ouvre https://example.com"))
        assertEquals(AndroidCommand.SearchWeb("météo Ouagadougou"), AndroidCommandParser.parse("cherche sur internet météo Ouagadougou"))
        assertEquals(AndroidCommand.OpenApp("TikTok"), AndroidCommandParser.parse("ouvre TikTok"))
    }

    @Test
    fun unrelatedTextIsNotTreatedAsAnAction() {
        assertNull(AndroidCommandParser.parse("Salut BOZER, comment vas-tu ?"))
        assertNull(AndroidCommandParser.parse("envoie un message à maman"))
    }
}
