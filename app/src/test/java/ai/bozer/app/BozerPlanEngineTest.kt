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
    fun submittedButUnverifiedActionIsNotReportedAsSuccess() = runBlocking {
        val plan = BozerPlan("Ouvrir une app", listOf(BozerPlanStep("open", "Ouvrir", "open")))
        val report = BozerPlanCoordinator(BozerPlanActionExecutor {
            BozerActionReceipt("Intent envoyé à Android", BozerEvidenceLevel.SUBMITTED)
        }).run(plan)

        assertEquals(BozerStepStatus.FAILED, report.results.single().status)
        assertFalse(report.allVerified)
    }

    @Test
    fun parsesStrictJsonPlan() {
        val plan = BozerPlan.fromJson(
            """{"goal":"Préparer une journée","steps":[{"id":"step_1","title":"Créer rappel","action":"set_reminder","depends_on":[],"requires_confirmation":false}]}"""
        )
        assertEquals("Préparer une journée", plan.goal)
        assertEquals("set_reminder", plan.steps.single().action)
    }
}
