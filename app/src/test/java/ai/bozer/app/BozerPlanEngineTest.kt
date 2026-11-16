package ai.bozer.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BozerPlanEngineTest {
    @Test
    fun ordersStepsByDependenciesRatherThanModelOrder() {
        val plan = BozerPlan(
            "Préparer puis ouvrir une tâche",
            listOf(
                BozerPlanStep("second", "Ouvrir", "open", listOf("first")),
                BozerPlanStep("first", "Préparer", "prepare")
            )
        )
        assertEquals(listOf("first", "second"), plan.orderedSteps().map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCircularDependencies() {
        BozerPlan(
            "Cycle",
            listOf(
                BozerPlanStep("a", "A", "a", listOf("b")),
                BozerPlanStep("b", "B", "b", listOf("a"))
            )
        )
    }

    @Test
    fun confirmationGateBlocksDependentActionsUntilApproval() = runBlocking {
        val plan = BozerPlan(
            "Action sensible puis suite",
            listOf(
                BozerPlanStep("send", "Préparer un envoi", "compose", requiresConfirmation = true),
                BozerPlanStep("follow", "Étape suivante", "follow", dependsOn = listOf("send"))
            )
        )
        var executions = 0
        val report = BozerPlanCoordinator(BozerPlanActionExecutor {
            executions++
            BozerActionReceipt("Action vérifiée", BozerEvidenceLevel.VERIFIED)
        }).run(plan)

        assertEquals(0, executions)
        assertEquals(BozerStepStatus.WAITING_CONFIRMATION, report.results[0].status)
        assertEquals(BozerStepStatus.BLOCKED, report.results[1].status)
        assertFalse(report.allVerified)
    }

    @Test
    fun modelCannotDisableConfirmationForSensitiveAndroidActions() {
        val sensitiveActions = listOf(
            "appelle 70000000",
            "envoie un SMS à 70000000 disant Salut",
            "programme une alarme à 06h30",
            "partage ce message : bonjour"
        )

        sensitiveActions.forEachIndexed { index, action ->
            val plan = BozerPlan.fromJson(
                """{"goal":"Action sensible","steps":[{"id":"step_$index","title":"Action","action":"$action","depends_on":[],"requires_confirmation":false}]}"""
            )
            assertTrue("Confirmation obligatoire pour : $action", plan.steps.single().requiresConfirmation)
        }
    }

    @Test
    fun coordinatorAppliesConfirmationPolicyToPlansBuiltWithoutJson() = runBlocking {
        val plan = BozerPlan(
            "Appeler un numéro",
            listOf(BozerPlanStep("call", "Appeler", "appelle 70000000", requiresConfirmation = false))
        )
        var executions = 0
        val report = BozerPlanCoordinator(BozerPlanActionExecutor {
            executions++
            BozerActionReceipt("Composeur ouvert", BozerEvidenceLevel.SUBMITTED)
        }).run(plan)

        assertEquals(0, executions)
        assertEquals(BozerStepStatus.WAITING_CONFIRMATION, report.results.single().status)
        assertTrue(report.results.single().step.requiresConfirmation)
    }

    @Test
    fun unknownActionsFailClosedAndRequireConfirmation() {
        val plan = BozerPlan.fromJson(
            """{"goal":"Action non reconnue","steps":[{"id":"step_1","title":"Action","action":"run_unknown_tool","depends_on":[],"requires_confirmation":false}]}"""
        )
        assertTrue(plan.steps.single().requiresConfirmation)
    }

    @Test
    fun safeAppNavigationDoesNotAddAnUnnecessaryConfirmationGate() {
        val plan = BozerPlan.fromJson(
            """{"goal":"Ouvrir WhatsApp","steps":[{"id":"step_1","title":"Ouvrir WhatsApp","action":"ouvre WhatsApp","depends_on":[],"requires_confirmation":false}]}"""
        )
        assertFalse(plan.steps.single().requiresConfirmation)
    }

    @Test
    fun submittedButUnverifiedActionIsNotReportedAsSuccess() = runBlocking {
        val plan = BozerPlan("Ouvrir une app", listOf(BozerPlanStep("open", "Ouvrir", "ouvre WhatsApp")))
        val report = BozerPlanCoordinator(BozerPlanActionExecutor {
            BozerActionReceipt("Intent envoyé à Android", BozerEvidenceLevel.SUBMITTED)
        }).run(plan)

        assertEquals(BozerStepStatus.FAILED, report.results.single().status)
        assertFalse(report.allVerified)
    }

    @Test
    fun parsesStrictJsonPlan() {
        val plan = BozerPlan.fromJson(
            """{"goal":"Préparer une journée","steps":[{"id":"step_1","title":"Ouvrir WhatsApp","action":"ouvre WhatsApp","depends_on":[],"requires_confirmation":false}]}"""
        )
        assertEquals("Préparer une journée", plan.goal)
        assertEquals("ouvre WhatsApp", plan.steps.single().action)
        assertFalse(plan.steps.single().requiresConfirmation)
    }
}
