package ai.bozer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock

/**
 * The only layer allowed to launch Android intents for BOZER.
 * Callers must provide an explicit confirmation for every action.
 */
object PhoneActionExecutor {
    private fun launchIntent(context: Context, appName: String): Intent? {
        val launcherQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val matches = context.packageManager.queryIntentActivities(
            launcherQuery,
            PackageManager.MATCH_ALL
        )
        val normalizedTarget = appName.trim().lowercase()
        val match = matches.firstOrNull { info ->
            info.activityInfo.packageName != context.packageName &&
                info.loadLabel(context.packageManager).toString().trim().lowercase() == normalizedTarget
        } ?: matches.firstOrNull { info ->
            info.activityInfo.packageName != context.packageName &&
                info.loadLabel(context.packageManager).toString().trim().lowercase()
                    .contains(normalizedTarget)
        } ?: return null

        return Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(match.activityInfo.packageName, match.activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createIntent(context: Context, action: PhoneAction): Intent? = when (action) {
        is PhoneAction.OpenApp -> launchIntent(context, action.appName)
        is PhoneAction.SetAlarm -> Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        is PhoneAction.DialNumber -> Intent(
            Intent.ACTION_DIAL,
            Uri.fromParts("tel", action.number, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        is PhoneAction.ComposeSms -> Intent(
            Intent.ACTION_SENDTO,
            Uri.fromParts("smsto", action.number, null)
        ).apply {
            putExtra("sms_body", action.message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun execute(
        context: Context,
        action: PhoneAction,
        confirmed: Boolean = false
    ): ActionExecutionResult {
        if (!confirmed) {
            return ActionExecutionResult(false, "Confirmation requise. Rien n'a été lancé.")
        }
        val intent = createIntent(context, action)
            ?: return ActionExecutionResult(false, "Application introuvable. Rien n'a été ouvert.")
        if (intent.resolveActivity(context.packageManager) == null) {
            return ActionExecutionResult(false, "Aucune application compatible n'est installée. Rien n'a été lancé.")
        }
        return try {
            context.startActivity(intent)
            val message = when (action) {
                is PhoneAction.OpenApp -> "L'application ${action.appName} a été ouverte."
                is PhoneAction.SetAlarm -> "L'application Horloge a été ouverte pour préparer le réveil. Vérifie et confirme l'heure dans Horloge."
                is PhoneAction.DialNumber -> "Le composeur a été ouvert. Aucun appel n'a été passé."
                is PhoneAction.ComposeSms -> "L'application SMS a été ouverte avec le brouillon. Le message n'a pas été envoyé."
            }
            ActionExecutionResult(true, message)
        } catch (_: ActivityNotFoundException) {
            ActionExecutionResult(false, "Aucune application compatible n'a pu être ouverte. Rien n'a été exécuté.")
        } catch (error: SecurityException) {
            ActionExecutionResult(false, "Android a refusé cette action : ${error.message ?: "autorisation manquante"}.")
        }
    }
}
