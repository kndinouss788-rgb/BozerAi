package ai.bozer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BozerAppCommandTest {
    @Test
    fun parsesInternalSettingsAndSilentMode() {
        assertEquals(BozerAppCommand.OpenSettings, BozerAppCommandParser.parse("BOZER, ouvre tes paramètres"))
        assertEquals(BozerAppCommand.OpenAccessibilitySettings, BozerAppCommandParser.parse("Ouvre les paramètres d'accessibilité Android"))
        assertEquals(BozerAppCommand.SetSilentMode(true), BozerAppCommandParser.parse("Passe en mode silencieux"))
        assertEquals(BozerAppCommand.SetSilentMode(false), BozerAppCommandParser.parse("Désactive le mode silencieux"))
        assertEquals(BozerAppCommand.SetBrainMode(BrainMode.OFFLINE), BozerAppCommandParser.parse("Passe en mode hors ligne"))
        assertEquals(BozerAppCommand.SetBrainMode(BrainMode.ONLINE), BozerAppCommandParser.parse("Active le mode en ligne"))
        assertEquals(BozerAppCommand.SetBrainMode(BrainMode.AUTO), BozerAppCommandParser.parse("PASSE EN MODE AUTOMATIQUE"))
    }

    @Test
    fun parsesThemeChangesAndCycles() {
        assertEquals(BozerAppCommand.SetTheme(BozerTheme.GLACIER), BozerAppCommandParser.parse("Passe au thème glacier"))
        assertEquals(BozerAppCommand.SetTheme(BozerTheme.SAGE), BozerAppCommandParser.parse("BOZER thème sauge"))
        assertEquals(BozerAppCommand.CycleTheme, BozerAppCommandParser.parse("Change de thème"))
        assertEquals(BozerTheme.GLACIER, BozerTheme.PEARL.next())
        assertEquals(BozerTheme.PEARL, BozerTheme.fromStored("unknown"))
    }

    @Test
    fun parsesAnimationSoundAndBubbleControls() {
        assertEquals(BozerAppCommand.SetAnimations(false), BozerAppCommandParser.parse("Désactive les animations fluides"))
        assertEquals(BozerAppCommand.SetSoundEffects(true), BozerAppCommandParser.parse("Active les effets sonores"))
        assertEquals(BozerAppCommand.SetFloatingBubble(false), BozerAppCommandParser.parse("Désactive la bulle flottante"))
    }

    @Test
    fun separatesTiktokLaunchFromSystemVideoCamera() {
        assertEquals(BozerAppCommand.OpenTikTokForRecording, BozerAppCommandParser.parse("Ouvre TikTok et filme-moi"))
        assertEquals(BozerAppCommand.OpenVideoCamera, BozerAppCommandParser.parse("Ouvre la caméra en mode vidéo"))
        assertEquals(BozerAppCommand.OpenVideoCamera, BozerAppCommandParser.parse("Filme-moi"))
    }

    @Test
    fun doesNotCaptureOrdinaryConversationAsAnAction() {
        assertNull(BozerAppCommandParser.parse("J'aime beaucoup le thème de cette application"))
        assertNull(BozerAppCommandParser.parse("Comment filmer une vidéo sur TikTok ?"))
    }
}
