package ai.bozer.app

import org.json.JSONArray
import org.json.JSONObject

enum class BozerStepStatus { PENDING, WAITING_CONFIRMATION, RUNNING, SUCCEEDED, FAILED, BLOCKED }
enum class BozerEvidenceLevel { VERIFIED, SUBMITTED, UNKNOWN }

data class BozerPlanStep(
    val id: String,
    val title: String,
    val action: String,
    val dependsOn: List<String> = emptyList(),
    val requiresConfirmation: Boolean = false
)

data class BozerPlan(val goal: String, val steps: List<BozerPlanStep>) {
    init {
        require(goal.isNotBlank()) { "Le but du plan est obligatoire." }
        require(steps.isNotEmpty()) { "Un plan doit contenir au moins une étape." }
        require(steps.size <= MAX_STEPS) { "Un plan est limité à $MAX_STEPS étapes." }
        require(steps.map { it.id }.distinct().size == steps.size) { "Les identifiants d'étape doivent être uniques." }
        val ids = steps.map { it.id }.toSet()
        steps.forEach { step ->
            require(step.id.isNotBlank() && step.title.isNotBlank() && step.action.isNotBlank()) {
                "Chaque étape doit avoir un identifiant, un titre et une action."
            }
            require(step.dependsOn.distinct().size == step.dependsOn.size) { "Dépendance dupliquée dans \${step.id}." }
            require(step.id !in step.dependsOn) { "Une étape ne peut pas dépendre d'elle-même." }
            require(step.dependsOn.all(ids::contains)) { "Une dépendance de \${step.id} n'existe pas." }
        }
        require(hasNoCycle()) { "Le plan contient une dépendance circulaire." }
    }

    fun orderedSteps(): List<BozerPlanStep> {
        val remaining = steps.associateBy { it.id }.toMutableMap()
        val completed = mutableSetOf<String>()
        val ordered = mutableListOf<BozerPlanStep>()
        while (remaining.isNotEmpty()) {
            val ready = steps.filter { it.id in remaining && completed.containsAll(it.dependsOn) }
            check(ready.isNotEmpty()) { "Le plan ne peut pas être ordonné." }
            ready.forEach { step ->
                ordered += step
                completed += step.id
                remaining.remove(step.id)
            }
        }
        return ordered
    }

    private fun hasNoCycle(): Boolean = runCatching { orderedWithoutCycle() }.isSuccess
    private fun orderedWithoutCycle() {
        val remaining = steps.associateBy { it.id }.toMutableMap()
        val completed = mutableSetOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.values.filter { completed.containsAll(it.dependsOn) }
            if (ready.isEmpty()) error("cycle")
            ready.forEach { completed += it.id; remaining.remove(it.id) }
        }
    }

    companion object {
        const val MAX_STEPS = 12
        fun fromJson(raw: String): BozerPlan {
            require(raw.length <= 16_000) { "La réponse de planification est trop longue." }
            val json = JSONObject(raw)
            val array = json.getJSONArray("steps")
            require(array.length() in 1..MAX_STEPS) { "Nombre d'étapes invalide." }
            val steps = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val dependencies = item.optJSONArray("depends_on") ?: JSONArray()
                BozerPlanStep(
                    id = item.getString("id").trim(),
                    title = item.getString("title").trim(),
                    action = item.getString("action").trim(),
                    dependsOn = (0 until dependencies.length()).map { dependencies.getString(it) },
                    requiresConfirmation = item.optBoolean("requires_confirmation", false)
                )
            }
            return BozerPlan(json.getString("goal").trim(), steps)
        }
    }
}

data class BozerActionReceipt(val message: String, val evidence: BozerEvidenceLevel)
data class BozerStepResult(val step: BozerPlanStep, val status: BozerStepStatus, val receipt: BozerActionReceipt? = null)
data class BozerPlanReport(val goal: String, val results: List<BozerStepResult>) {
    val allVerified: Boolean
        get() = results.isNotEmpty() && results.all { it.status == BozerStepStatus.SUCCEEDED && it.receipt?.evidence == BozerEvidenceLevel.VERIFIED }
}

fun interface BozerPlanActionExecutor {
    suspend fun execute(step: BozerPlanStep): BozerActionReceipt
}

class BozerPlanCoordinator(private val executor: BozerPlanActionExecutor) {
    suspend fun run(plan: BozerPlan, approvedStepIds: Set<String> = emptySet()): BozerPlanReport {
        val results = linkedMapOf<String, BozerStepResult>()
        for (step in plan.orderedSteps()) {
            if (step.dependsOn.any { results[it]?.status != BozerStepStatus.SUCCEEDED }) {
                results[step.id] = BozerStepResult(step, BozerStepStatus.BLOCKED)
                continue
            }
            if (step.requiresConfirmation && step.id !in approvedStepIds) {
                results[step.id] = BozerStepResult(step, BozerStepStatus.WAITING_CONFIRMATION)
                continue
            }
            results[step.id] = BozerStepResult(step, BozerStepStatus.RUNNING)
            try {
                val receipt = executor.execute(step)
                val status = if (receipt.evidence == BozerEvidenceLevel.VERIFIED) BozerStepStatus.SUCCEEDED else BozerStepStatus.FAILED
                results[step.id] = BozerStepResult(step, status, receipt)
            } catch (error: Exception) {
                results[step.id] = BozerStepResult(step, BozerStepStatus.FAILED,
                    BozerActionReceipt(error.message ?: "Échec sans détail.", BozerEvidenceLevel.UNKNOWN))
            }
        }
        return BozerPlanReport(plan.goal, plan.orderedSteps().map { results.getValue(it.id) })
    }
}
