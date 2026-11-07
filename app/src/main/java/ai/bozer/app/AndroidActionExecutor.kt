package ai.bozer.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import java.text.Normalizer
import java.util.Locale

/**
 * Executes only an explicit, low-risk request to open an installed app.
 * Calls, messages, purchases, settings changes and other sensitive actions
 * are deliberately not inferred or executed here.
 */
class AndroidActionExecutor(private val activity: Activity) {
    fun execute(command: String): String? {
        val match = OPEN_APP.matchEntire(command.trim()) ?: return null
        val requestedName = match.groupValues[1]
            .trim()
            .removeSuffix(".")
            .removeSuffix("!")
            .removeSuffix("?")
            .trim()
        if (requestedName.isBlank()) return null

        val normalizedRequest = normalize(requestedName)
            .removePrefix("l'")
            .removePrefix("le ")
            .removePrefix("la ")
            .removePrefix("les ")
            .trim()
        if (normalizedRequest.isBlank()) return null

        // Android system settings may be labelled "Settings" on an English device
        // even when the user gives BOZER the French command.
        if (normalizedRequest in setOf("parametres", "reglages", "settings", "configuration")) {
            val settingsIntent = activity.packageManager.getLaunchIntentForPackage("com.android.settings")
            if (settingsIntent != null) {
                activity.startActivity(settingsIntent)
                return "J'ouvre les paramètres."
            }
        }

        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = activity.packageManager.queryIntentActivities(
            launcher,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val selected = candidates
            .filter { it.activityInfo.packageName != activity.packageName }
            .map { info ->
                val label = info.loadLabel(activity.packageManager).toString()
                Triple(info.activityInfo.packageName, label, normalize(label))
            }
            .distinctBy { it.first }
            .sortedBy { (_, _, normalizedLabel) ->
                when {
                    normalizedLabel == normalizedRequest -> 0
                    normalizedLabel.contains(normalizedRequest) -> 1
                    normalizedRequest.contains(normalizedLabel) -> 2
                    else -> 3
                }
            }
            .firstOrNull { (_, _, normalizedLabel) ->
                normalizedLabel == normalizedRequest ||
                    normalizedLabel.contains(normalizedRequest) ||
                    normalizedRequest.contains(normalizedLabel)
            }
            ?: return "Je ne trouve pas l'application « $requestedName » parmi celles installées sur ce téléphone."

        return try {
            val launchIntent = activity.packageManager.getLaunchIntentForPackage(selected.first)
                ?: return "L'application « ${selected.second} » ne peut pas être ouverte depuis son icône."
            activity.startActivity(launchIntent)
            "J'ouvre ${selected.second}."
        } catch (_: ActivityNotFoundException) {
            "Je n'ai pas réussi à ouvrir ${selected.second}. Vérifie qu'elle est toujours installée."
        } catch (_: SecurityException) {
            "Android a refusé l'ouverture de ${selected.second}."
        }
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private val OPEN_APP = Regex(
            """(?:s'il te plaît\s+)?(?:ouvre|lance|démarre|demarre)\s+(?:l'application\s+|l'appli\s+|application\s+)?(.+)""",
            RegexOption.IGNORE_CASE
        )
    }
}
