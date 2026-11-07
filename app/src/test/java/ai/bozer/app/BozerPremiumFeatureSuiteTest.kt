package ai.bozer.app

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BozerPremiumFeatureSuiteTest {
    @Test
    fun detectsAllSevenSpecialists() {
        val cases = listOf(
            "Suivre les clients et les commandes de mon commerce" to BozerPremiumFeature.BUSINESS,
            "Automatise cette tâche pour moi" to BozerPremiumFeature.AGENT,
            "Fais une recherche approfondie sur ce marché" to BozerPremiumFeature.RESEARCH,
            "Crée un devis pour ce client" to BozerPremiumFeature.DOCUMENTS,
            "Organise ma journée de demain" to BozerPremiumFeature.SECRETARY,
            "Traduis ce message en anglais" to BozerPremiumFeature.TRANSLATOR,
            "Peux-tu debug ce code ?" to BozerPremiumFeature.DEVELOPER
        )

        cases.forEach { (text, expected) ->
            assertEquals("Intent non détectée pour : $text", expected, BozerFeatureIntentDetector.detect(text)?.feature)
        }
    }

    @Test
    fun passExpiresAtTwentyFourHoursAndCannotBeExtendedByClientData() {
        val now = Instant.parse("2026-11-07T15:00:00Z")
        val pass = BozerFeaturePass(
            userId = "user-1",
            feature = BozerPremiumFeature.BUSINESS,
            issuedAt = now,
            expiresAt = now.plusSeconds(24 * 60 * 60),
            receiptId = "verified-receipt"
        )
        assertTrue(pass.isActive(now.plusSeconds(23 * 60 * 60)))
        assertFalse(pass.isActive(now.plusSeconds(24 * 60 * 60)))
        assertFalse(pass.copy(receiptId = "").isActive(now))
        assertFalse(pass.copy(expiresAt = now.plusSeconds(25 * 60 * 60)).isActive(now))
    }

    @Test
    fun workflowsAreExplicitAndSensitiveStepsRequireConfirmation() {
        val coordinator = BozerPremiumFeatureCoordinator(
            Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneOffset.UTC)
        )
        val request = BozerFeatureIntentDetector.detect("Automatise cette tâche pour moi")
        assertNotNull(request)
        val workflow = coordinator.createWorkflow(request!!)
        assertEquals(BozerPremiumFeature.AGENT, workflow.feature)
        assertTrue(workflow.steps.any { it.requiresUserConfirmation })
        assertTrue(workflow.safetyNote.contains("ne doit jamais prétendre"))
    }

    @Test
    fun noPassMeansNoPremiumAccess() {
        val coordinator = BozerPremiumFeatureCoordinator()
        assertFalse(coordinator.hasAccess("user-1", BozerPremiumFeature.BUSINESS, emptyList()))
    }
}
