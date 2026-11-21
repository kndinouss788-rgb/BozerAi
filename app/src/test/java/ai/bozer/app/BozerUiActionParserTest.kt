package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BozerUiActionParserTest {
    @Test
    fun parsesVisibleControlsAndTextEntry() {
        assertEquals(
            listOf(BozerUiAction.TapText("Messages")),
            BozerUiActionParser.parse("clique sur Messages")
        )
        assertEquals(
            listOf(BozerUiAction.EnterText("Bonjour, je serai là à 18h")),
            BozerUiActionParser.parse("écris Bonjour, je serai là à 18h")
        )
    }

    @Test
    fun parsesNavigationAndScrolling() {
        assertEquals(listOf(BozerUiAction.ScrollForward), BozerUiActionParser.parse("Fais défiler vers le bas"))
        assertEquals(listOf(BozerUiAction.ScrollBackward), BozerUiActionParser.parse("scroll up"))
        assertEquals(listOf(BozerUiAction.PressBack), BozerUiActionParser.parse("reviens en arrière"))
        assertEquals(listOf(BozerUiAction.PressHome), BozerUiActionParser.parse("retourne à l'accueil"))
    }

    @Test
    fun parsesOrderedUserDirectedSequences() {
        assertEquals(
            listOf(
                BozerUiAction.TapText("Messages"),
                BozerUiAction.EnterText("Salut, tu es disponible ?"),
                BozerUiAction.TapText("Envoyer")
            ),
            BozerUiActionParser.parse("clique sur Messages puis écris Salut, tu es disponible ? puis clique sur Envoyer")
        )
    }

    @Test
    fun rejectsUnclearConversationAndOversizedRequests() {
        assertNull(BozerUiActionParser.parse("Peux-tu m'expliquer comment envoyer un message ?"))
        assertNull(BozerUiActionParser.parse("clique sur un bouton puis fais quelque chose"))
    }

    @Test
    fun recognisesAppLaunchThenActions() {
        assertEquals(
            BozerAppCommand.LaunchAppAndUiActions(
                "whatsapp",
                listOf(BozerUiAction.TapText("Messages"), BozerUiAction.EnterText("Bonjour"))
            ),
            BozerAppCommandParser.parse("Ouvre WhatsApp puis clique sur Messages puis écris Bonjour")
        )
        assertEquals(
            BozerAppCommand.ControlVisibleUi(listOf(BozerUiAction.TapText("Paramètres"))),
            BozerAppCommandParser.parse("BOZER, clique sur Paramètres")
        )
    }
}
