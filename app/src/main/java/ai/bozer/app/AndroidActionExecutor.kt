package ai.bozer.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.Settings
import java.text.Normalizer
import java.util.Locale
import java.util.Calendar

/**
 * Android action gateway.
 *
 * Safe actions launch explicit Android intents. Sensitive communication is
 * prepared in the user's chosen app; the user performs the final Send/Call
 * action there. BOZER never silently sends messages or places calls.
 */
class AndroidActionExecutor(private val activity: Activity) {
    private var bozerAppCommandHandler: ((BozerAppCommand) -> String?)? = null

    fun setBozerAppCommandHandler(handler: ((BozerAppCommand) -> String?)?) {
        bozerAppCommandHandler = handler
    }

    fun isActionCommand(command: String): Boolean {
        val normalized = normalize(command)
        return BozerAppCommandParser.parse(command) != null ||
            AndroidCommandParser.parse(command) != null ||
            normalized in setOf(
                "annule la prochaine alarme", "annule la prochaine alarme bozer",
                "annule mon prochain reveil", "annule toutes les alarmes bozer",
                "supprime toutes les alarmes bozer", "quelles sont mes alarmes",
                "liste mes alarmes", "mes alarmes bozer"
            )
    }

    fun requiresUserConfirmation(command: String): Boolean {
        val appCommand = BozerAppCommandParser.parse(command)
        val actions = when (appCommand) {
            is BozerAppCommand.ControlVisibleUi -> appCommand.actions
            is BozerAppCommand.LaunchAppAndUiActions -> appCommand.actions
            else -> emptyList()
        }
        return actions.any(BozerAccessibilityService::isSensitiveAction) ||
            BozerActionSafetyPolicy.requiresUserConfirmation(command)
    }

    fun confirmationSummary(command: String): String {
        val uiCommand = BozerAppCommandParser.parse(command)
        val uiActions = when (uiCommand) {
            is BozerAppCommand.ControlVisibleUi -> uiCommand.actions
            is BozerAppCommand.LaunchAppAndUiActions -> uiCommand.actions
            else -> emptyList()
        }
        if (uiActions.any(BozerAccessibilityService::isSensitiveAction)) {
            return "BOZER doit toucher « ${uiActions.filterIsInstance<BozerUiAction.TapText>().joinToString(" », « ") { it.label } } » dans l'application affichée. Cette action peut envoyer, publier, supprimer ou payer. Confirme seulement si c'est bien ce que tu veux."
        }
        val normalized = normalize(command)
        return when (val parsed = AndroidCommandParser.parse(command)) {
            is AndroidCommand.Dial ->
                "Ouvrir le composeur avec le numéro ${parsed.number}. Aucun appel ne sera lancé automatiquement."
            is AndroidCommand.ComposeSms ->
                "Préparer un SMS pour ${parsed.number} avec le texte : « ${parsed.body} ». Tu devras vérifier puis appuyer toi-même sur Envoyer."
            is AndroidCommand.ComposeEmail ->
                "Préparer un e-mail pour ${parsed.address}, avec l'objet « ${parsed.subject} ». Tu pourras le relire avant l'envoi."
            is AndroidCommand.SetAlarm ->
                "Programmer une alarme BOZER à %02d:%02d, intitulée « ${parsed.label} ».".format(parsed.hour, parsed.minute)
            is AndroidCommand.SetRecurringAlarm ->
                "Programmer une alarme BOZER récurrente à %02d:%02d, intitulée « ${parsed.label} ».".format(parsed.hour, parsed.minute)
            is AndroidCommand.SetReminder ->
                "Créer un rappel BOZER dans ${parsed.delaySeconds} seconde(s), intitulé « ${parsed.label} »."
            is AndroidCommand.SetTimer ->
                "Ouvrir l'horloge Android pour un minuteur de ${parsed.seconds} seconde(s)."
            is AndroidCommand.CreateCalendarEvent ->
                "Ouvrir le calendrier pour préparer l'événement « ${parsed.title} ». Tu pourras vérifier puis l'enregistrer."
            is AndroidCommand.ShareText ->
                "Ouvrir le menu de partage Android avec ce texte : « ${parsed.text} »."
            is AndroidCommand.OpenApp ->
                "Ouvrir l'application « ${parsed.name} »."
            is AndroidCommand.OpenUrl ->
                "Ouvrir la page ${parsed.url}."
            is AndroidCommand.SearchWeb ->
                "Lancer une recherche sur le Web pour « ${parsed.query} »."
            is AndroidCommand.Navigate ->
                "Ouvrir l'itinéraire vers « ${parsed.destination} »."
            AndroidCommand.OpenSettings ->
                "Ouvrir les paramètres du téléphone."
            is AndroidCommand.OpenDeviceSettings ->
                "Ouvrir les paramètres Android : ${parsed.label}."
            null -> when (normalized) {
                "annule la prochaine alarme",
                "annule la prochaine alarme bozer",
                "annule mon prochain reveil" -> "Annuler la prochaine alarme BOZER ?"
                "annule toutes les alarmes bozer",
                "supprime toutes les alarmes bozer" -> "Annuler toutes les alarmes BOZER ?"
                else -> "Confirmer cette action : $command"
            }
        }
    }

    fun execute(command: String, userConfirmed: Boolean = false): String? {
        val appCommand = BozerAppCommandParser.parse(command)
        if (appCommand != null) {
            return try {
                when (appCommand) {
                    BozerAppCommand.OpenVideoCamera -> openVideoCamera()
                    BozerAppCommand.OpenAccessibilitySettings -> {
                        activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        "J'ouvre les paramètres d'accessibilité Android. Tu peux y activer BOZER ACTION pour permettre les actions vocales dans les applications."
                    }
                    is BozerAppCommand.ControlVisibleUi ->
                        queueUiActions(appCommand.actions, null, userConfirmed)
                    is BozerAppCommand.LaunchAppAndUiActions ->
                        launchAppAndQueueUiActions(appCommand.appName, appCommand.actions, userConfirmed)
                    else -> bozerAppCommandHandler?.invoke(appCommand)
                        ?: "Cette commande BOZER n'est pas encore reliée à ses paramètres."
                }
            } catch (_: ActivityNotFoundException) {
                "Aucune application compatible n'a été trouvée pour cette action."
            } catch (_: SecurityException) {
                "Android a refusé cette action. Vérifie les autorisations de BOZER."
            } catch (error: Exception) {
                "Je n'ai pas pu effectuer cette action : ${error.message ?: "erreur inconnue"}."
            }
        }

        val normalizedCommand = normalize(command)
        if (normalizedCommand in setOf("annule la prochaine alarme", "annule la prochaine alarme bozer", "annule mon prochain reveil")) {
            return cancelNextBozerAlarm()
        }
        if (normalizedCommand in setOf("annule toutes les alarmes bozer", "supprime toutes les alarmes bozer")) {
            return cancelAllBozerAlarms()
        }
        if (normalizedCommand in setOf("quelles sont mes alarmes", "liste mes alarmes", "mes alarmes bozer")) {
            return listBozerAlarms()
        }
        val parsed = AndroidCommandParser.parse(command) ?: return executeOpenApp(command)
        return try {
            when (parsed) {
                is AndroidCommand.OpenApp -> openApp(parsed.name)
                is AndroidCommand.OpenUrl -> openUrl(parsed.url)
                is AndroidCommand.SearchWeb -> openUrl("https://www.google.com/search?q=" + Uri.encode(parsed.query))
                is AndroidCommand.Navigate -> openMaps(parsed.destination)
                is AndroidCommand.Dial -> openDialer(parsed.number)
                is AndroidCommand.ComposeSms -> composeSms(parsed.number, parsed.body)
                is AndroidCommand.ComposeEmail -> composeEmail(parsed.address, parsed.subject, parsed.body)
                is AndroidCommand.SetAlarm -> setAlarm(parsed.hour, parsed.minute, parsed.label)
                is AndroidCommand.SetRecurringAlarm -> setRecurringAlarm(parsed.hour, parsed.minute, parsed.label, parsed.repeatDaysMask)
                is AndroidCommand.SetReminder -> setRelativeReminder(parsed.delaySeconds, parsed.label)
                is AndroidCommand.SetTimer -> setTimer(parsed.seconds, parsed.label)
                is AndroidCommand.CreateCalendarEvent -> createCalendarEvent(parsed.title, parsed.details, parsed.beginAtMillis)
                is AndroidCommand.ShareText -> shareText(parsed.text)
                AndroidCommand.OpenSettings -> openSettings()
                is AndroidCommand.OpenDeviceSettings -> openDeviceSettings(parsed.action, parsed.label)
            }
        } catch (_: ActivityNotFoundException) {
            "Aucune application compatible n'a été trouvée pour cette action."
        } catch (_: SecurityException) {
            "Android a refusé cette action. Vérifie les autorisations de BOZER."
        } catch (error: Exception) {
            "Je n'ai pas pu préparer cette action : ${error.message ?: "erreur inconnue"}."
        }
    }

    private fun queueUiActions(
        actions: List<BozerUiAction>,
        targetPackage: String?,
        userConfirmed: Boolean
    ): String {
        if (!BozerAccessibilityService.isConnected()) {
            return bozerAppCommandHandler?.invoke(BozerAppCommand.RequestAccessibilityConsent)
                ?: "L'accès BOZER ACTION doit d'abord être expliqué et autorisé dans les paramètres Android."
        }
        val resolvedTarget = targetPackage ?: BozerAccessibilityService.lastUsedExternalPackage()
        if (resolvedTarget.isNullOrBlank()) {
            return "Je ne sais pas encore quelle application tu veux contrôler. Ouvre-la ou demande-moi de l'ouvrir, puis redis ta commande."
        }
        val requestId = BozerAccessibilityService.requestActions(
            actions = actions,
            targetPackage = resolvedTarget,
            allowSensitiveActions = userConfirmed
        ) ?: return "Je n'ai pas pu préparer cette action. Vérifie que BOZER ACTION est activé et que l'application affiche bien le contrôle demandé."
        return "Mission BOZER ACTION lancée (n°$requestId). Je vais agir dans l'application au premier plan. Je ne confirmerai le résultat que si l'application le permet ; vérifie l'écran."
    }

    private fun launchAppAndQueueUiActions(
        requestedName: String,
        actions: List<BozerUiAction>,
        userConfirmed: Boolean
    ): String {
        val normalized = normalize(requestedName)
            .removePrefix("l'")
            .removePrefix("le ")
            .removePrefix("la ")
            .removePrefix("les ")
            .trim()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = activity.packageManager.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != activity.packageName }
            .map { Triple(it.activityInfo.packageName, it.loadLabel(activity.packageManager).toString(), normalize(it.loadLabel(activity.packageManager).toString())) }
            .distinctBy { it.first }
        val selected = candidates.sortedBy { (_, _, label) ->
            when {
                label == normalized -> 0
                label.contains(normalized) -> 1
                normalized.contains(label) -> 2
                else -> 3
            }
        }.firstOrNull { (_, _, label) ->
            label == normalized || label.contains(normalized) || normalized.contains(label)
        } ?: return "Je ne trouve pas l'application « $requestedName » sur ce téléphone."

        val intent = activity.packageManager.getLaunchIntentForPackage(selected.first)
            ?: return "L'application « ${selected.second} » ne peut pas être ouverte depuis son icône."
        if (!BozerAccessibilityService.isConnected()) {
            return bozerAppCommandHandler?.invoke(BozerAppCommand.RequestAccessibilityConsent)
                ?: "L'accès BOZER ACTION doit d'abord être expliqué et autorisé dans les paramètres Android."
        }
        val requestId = BozerAccessibilityService.requestActions(
            actions = actions,
            targetPackage = selected.first,
            allowSensitiveActions = userConfirmed
        ) ?: return "Je n'ai pas pu préparer les étapes pour ${selected.second}. Vérifie le service BOZER ACTION."
        activity.startActivity(intent)
        return "J'ouvre ${selected.second} et je lance les étapes de ta mission BOZER ACTION (n°$requestId). Vérifie l'écran : certaines applications peuvent bloquer l'automatisation."
    }

    private fun executeOpenApp(command: String): String? {
        val match = OPEN_APP.matchEntire(command.trim()) ?: return null
        val requested = match.groupValues[1].trim().trimEnd('.', '!', '?')
        return openApp(requested)
    }

    private fun openApp(requestedName: String): String {
        val normalized = normalize(requestedName)
            .removePrefix("l'")
            .removePrefix("le ")
            .removePrefix("la ")
            .removePrefix("les ")
            .trim()
        if (normalized in setOf("parametres", "reglages", "settings", "configuration")) return openSettings()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates = activity.packageManager.queryIntentActivities(launcher, PackageManager.MATCH_DEFAULT_ONLY)
        val selected = candidates
            .filter { it.activityInfo.packageName != activity.packageName }
            .map { Triple(it.activityInfo.packageName, it.loadLabel(activity.packageManager).toString(), normalize(it.loadLabel(activity.packageManager).toString())) }
            .distinctBy { it.first }
            .sortedBy { (_, _, label) ->
                when {
                    label == normalized -> 0
                    label.contains(normalized) -> 1
                    normalized.contains(label) -> 2
                    else -> 3
                }
            }
            .firstOrNull { (_, _, label) -> label == normalized || label.contains(normalized) || normalized.contains(label) }
            ?: return "Je ne trouve pas l'application « $requestedName » sur ce téléphone."
        val intent = activity.packageManager.getLaunchIntentForPackage(selected.first)
            ?: return "L'application « ${selected.second} » ne peut pas être ouverte depuis son icône."
        activity.startActivity(intent)
        return "J'ouvre ${selected.second}."
    }

    private fun openSettings(): String {
        activity.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
        return "J'ouvre les paramètres du téléphone."
    }

    private fun openDeviceSettings(action: String, label: String): String {
        activity.startActivity(Intent(action))
        return "J'ouvre les paramètres Android : $label."
    }

    private fun openVideoCamera(): String {
        val intent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
        activity.startActivity(intent)
        return "J'ouvre la caméra Android en mode vidéo. L'enregistrement ne démarre qu'après ton appui sur le bouton Enregistrer."
    }

    private fun openUrl(value: String): String {
        val url = if (value.startsWith("https://") || value.startsWith("http://")) value else "https://$value"
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return "J'ouvre cette page."
    }

    private fun openMaps(destination: String): String {
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(destination))
        activity.startActivity(Intent(Intent.ACTION_VIEW, uri))
        return "J'ouvre l'itinéraire vers $destination."
    }

    private fun openDialer(number: String): String {
        activity.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))))
        return "J'ouvre le composeur avec le numéro $number. Tu peux vérifier puis lancer l'appel."
    }

    private fun composeSms(number: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
            .putExtra("sms_body", body)
        activity.startActivity(intent)
        return "J'ai préparé le SMS dans ton application de messages. Vérifie le destinataire et appuie sur Envoyer."
    }

    private fun composeEmail(address: String, subject: String, body: String): String {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + Uri.encode(address)))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        activity.startActivity(intent)
        return "J'ai préparé l'e-mail. Vérifie-le puis appuie sur Envoyer."
    }

    private fun cancelNextBozerAlarm(): String {
        val repository = BozerAlarmRepository(ChatDatabase(activity.applicationContext))
        val next = repository.enabled().minByOrNull { it.triggerAtMillis }
            ?: return "Tu n'as aucune alarme BOZER active."
        BozerAlarmScheduler(activity.applicationContext).cancel(next.id)
        repository.disable(next.id)
        return "J'ai annulé la prochaine alarme BOZER : ${next.label}."
    }

    private fun cancelAllBozerAlarms(): String {
        val repository = BozerAlarmRepository(ChatDatabase(activity.applicationContext))
        val alarms = repository.enabled()
        val scheduler = BozerAlarmScheduler(activity.applicationContext)
        alarms.forEach { scheduler.cancel(it.id); repository.disable(it.id) }
        return if (alarms.isEmpty()) "Tu n'as aucune alarme BOZER active."
        else "${alarms.size} alarme(s) BOZER annulée(s)."
    }

    private fun listBozerAlarms(): String {
        val alarms = BozerAlarmRepository(ChatDatabase(activity.applicationContext)).enabled()
            .sortedBy { it.triggerAtMillis }
        if (alarms.isEmpty()) return "Tu n'as aucune alarme BOZER programmée."
        val formatter = java.text.SimpleDateFormat("EEE dd/MM à HH:mm", Locale.FRANCE)
        return "Voici tes alarmes BOZER : " + alarms.take(8).joinToString(" ; ") {
            "${it.label} — ${formatter.format(java.util.Date(it.triggerAtMillis))}"
        }
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): String {
        val now = Calendar.getInstance()
        val trigger = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
        }
        val database = ChatDatabase(activity.applicationContext)
        val repository = BozerAlarmRepository(database)
        val id = repository.create(trigger.timeInMillis, label.ifBlank { "Rappel BOZER" })
        val scheduler = BozerAlarmScheduler(activity.applicationContext)
        val scheduled = scheduler.schedule(
            repository.get(id) ?: return "L'alarme a été enregistrée, mais je n'ai pas pu la charger."
        )
        return if (scheduled) {
            val precision = if (scheduler.exactAlarmAccessGranted()) "Alarme précise BOZER" else "Alarme BOZER enregistrée (Android peut retarder son déclenchement)"
            "$precision pour %02d:%02d : elle est stockée sur ce téléphone.".format(hour, minute)
        } else {
            repository.disable(id)
            "L'alarme a été enregistrée, mais Android a refusé sa programmation. Vérifie les autorisations d'alarmes de BOZER."
        }
    }

    private fun setRecurringAlarm(hour: Int, minute: Int, label: String, repeatDaysMask: Int): String {
        val now = System.currentTimeMillis()
        val candidate = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var found: Long? = null
        repeat(8) {
            val bit = 1 shl (candidate.get(Calendar.DAY_OF_WEEK) - 1)
            if (candidate.timeInMillis > now && repeatDaysMask and bit != 0) {
                found = candidate.timeInMillis
                return@repeat
            }
            if (found == null) candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        val trigger = found ?: return "Je n'ai pas trouvé de prochaine date pour cette alarme."
        val repository = BozerAlarmRepository(ChatDatabase(activity.applicationContext))
        val id = repository.create(trigger, label.ifBlank { "BOZER" }, repeatDaysMask)
        val scheduler = BozerAlarmScheduler(activity.applicationContext)
        val alarm = repository.get(id) ?: return "L'alarme récurrente n'a pas pu être chargée."
        if (!scheduler.schedule(alarm)) {
            repository.disable(id)
            return "Android a refusé de programmer cette alarme. Vérifie les autorisations."
        }
        val days = if (repeatDaysMask == 127) "tous les jours" else "du lundi au vendredi"
        val precision = if (scheduler.exactAlarmAccessGranted()) "Alarme précise" else "Alarme enregistrée (Android peut la retarder)"
        return "$precision BOZER $days à %02d:%02d.".format(hour, minute)
    }

    private fun setRelativeReminder(delaySeconds: Long, label: String): String {
        val triggerAt = System.currentTimeMillis() + delaySeconds * 1000L
        val repository = BozerAlarmRepository(ChatDatabase(activity.applicationContext))
        val id = repository.create(triggerAt, label.ifBlank { "Rappel BOZER" })
        val alarm = repository.get(id) ?: return "Le rappel a été enregistré, mais je n'ai pas pu le charger."
        val scheduler = BozerAlarmScheduler(activity.applicationContext)
        if (!scheduler.schedule(alarm)) {
            repository.disable(id)
            return "Android a refusé de programmer ce rappel. Vérifie les autorisations d'alarmes et de notifications de BOZER."
        }
        val minutes = delaySeconds / 60
        val duration = when {
            delaySeconds < 60 -> "${delaySeconds} seconde(s)"
            minutes < 60 -> "${minutes} minute(s)"
            else -> "${minutes / 60} heure(s)" + if (minutes % 60 > 0) " et ${minutes % 60} minute(s)" else ""
        }
        val precision = if (scheduler.exactAlarmAccessGranted()) "Rappel BOZER précis" else "Rappel BOZER enregistré (Android peut le retarder)"
        return "$precision dans ${duration} : ${alarm.label}."
    }

    private fun setTimer(seconds: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        activity.startActivity(intent)
        return "J'ai ouvert l'horloge pour programmer le minuteur."
    }

    private fun createCalendarEvent(title: String, details: String, beginAtMillis: Long): String {
        val intent = Intent(Intent.ACTION_INSERT).setData(android.provider.CalendarContract.Events.CONTENT_URI)
            .putExtra(android.provider.CalendarContract.Events.TITLE, title)
            .putExtra(android.provider.CalendarContract.Events.DESCRIPTION, details)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginAtMillis)
            .putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, beginAtMillis + 60 * 60 * 1000L)
        activity.startActivity(intent)
        return "J'ai préparé l'événement dans ton calendrier. Vérifie les détails et enregistre-le."
    }

    private fun shareText(text: String): String {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        activity.startActivity(Intent.createChooser(intent, "Partager avec…"))
        return "J'ouvre le menu de partage Android."
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

sealed interface AndroidCommand {
    data class OpenApp(val name: String) : AndroidCommand
    data class OpenUrl(val url: String) : AndroidCommand
    data class SearchWeb(val query: String) : AndroidCommand
    data class Navigate(val destination: String) : AndroidCommand
    data class Dial(val number: String) : AndroidCommand
    data class ComposeSms(val number: String, val body: String) : AndroidCommand
    data class ComposeEmail(val address: String, val subject: String, val body: String) : AndroidCommand
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : AndroidCommand
    data class SetRecurringAlarm(val hour: Int, val minute: Int, val label: String, val repeatDaysMask: Int) : AndroidCommand
    data class SetReminder(val delaySeconds: Long, val label: String) : AndroidCommand
    data class SetTimer(val seconds: Int, val label: String) : AndroidCommand
    data class CreateCalendarEvent(val title: String, val details: String, val beginAtMillis: Long) : AndroidCommand
    data class ShareText(val text: String) : AndroidCommand
    data object OpenSettings : AndroidCommand
    data class OpenDeviceSettings(val action: String, val label: String) : AndroidCommand
}

object AndroidCommandParser {
    fun parse(input: String): AndroidCommand? {
        val text = input.trim().removeSuffix(".").removeSuffix("!").trim()
        val normalized = normalize(text)
        if (normalized in setOf("ouvre les parametres", "ouvre les reglages", "ouvre les parametres du telephone")) return AndroidCommand.OpenSettings
        when (normalized) {
            "ouvre les parametres wifi", "ouvre les reglages wifi", "ouvre les parametres wi fi",
            "ouvre les parametres du wi fi", "ouvre les reglages du wifi", "ouvre les reglages wi fi" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_WIFI_SETTINGS, "Wi-Fi")
            "ouvre les parametres bluetooth", "ouvre les reglages bluetooth" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth")
            "ouvre les parametres de son", "ouvre les reglages de son", "ouvre le son" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_SOUND_SETTINGS, "Son")
            "ouvre les parametres d affichage", "ouvre les reglages d affichage", "ouvre l affichage" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_DISPLAY_SETTINGS, "Affichage")
            "ouvre les parametres de localisation", "ouvre les reglages de localisation", "ouvre la localisation" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS, "Localisation")
            "ouvre les parametres de notifications", "ouvre les reglages de notifications" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_APP_NOTIFICATION_SETTINGS, "Notifications")
            "ouvre les parametres des applications", "ouvre les applications" ->
                return AndroidCommand.OpenDeviceSettings(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "Applications")
        }

        Regex("""^(?:appelle|telephone)\s+(?:le\s+)?([+0-9][+0-9() .-]{5,})$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.Dial(it.groupValues[1].trim()) }

        Regex("""^(?:envoie|envoye)\s+(?:un\s+)?(?:sms|message)\s+(?:a|à|au)\s+([+0-9][+0-9() .-]{5,})\s+(?:disant|qui dit|:)\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.ComposeSms(it.groupValues[1].trim(), it.groupValues[2].trim()) }

        Regex("""^(?:ouvre|va sur|visite)\s+(?:le site\s+)?((?:https?://)?[a-z0-9.-]+\.[a-z]{2,}(?:/[^ ]*)?)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.OpenUrl(it.groupValues[1]) }

        Regex("""^(?:cherche sur internet|recherche sur internet|cherche sur le web)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.SearchWeb(it.groupValues[1].trim()) }

        Regex("""^(?:itineraire|itinéraire|navigue|guide-moi)\s+(?:vers|a|à|au|aux)\s+(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.Navigate(it.groupValues[1].trim()) }


        Regex("""^(?:programme|mets|regle)\s+(?:une\s+)?alarme\s+(?:tous les jours|chaque jour|du lundi au dimanche)\s+(?:a|à)\s+(\d{1,2})(?:h|:)(\d{0,2})(?:\s+(?:pour|intitulee)\s+(.+))?$""", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val hour = it.groupValues[1].toIntOrNull() ?: return null
                val minute = it.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
                if (hour !in 0..23 || minute !in 0..59) return null
                return AndroidCommand.SetRecurringAlarm(hour, minute, it.groupValues.getOrNull(3).orEmpty().ifBlank { "BOZER" }, 127)
            }

        Regex("""^(?:programme|mets|regle)\s+(?:une\s+)?alarme\s+(?:du lundi au vendredi|en semaine)\s+(?:a|à)\s+(\d{1,2})(?:h|:)(\d{0,2})(?:\s+(?:pour|intitulee)\s+(.+))?$""", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val hour = it.groupValues[1].toIntOrNull() ?: return null
                val minute = it.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
                if (hour !in 0..23 || minute !in 0..59) return null
                return AndroidCommand.SetRecurringAlarm(hour, minute, it.groupValues.getOrNull(3).orEmpty().ifBlank { "BOZER" }, 62)
            }

        Regex("""^(?:rappelle-moi|rappelle moi|reveille-moi|réveille-moi)\s+(?:a|à)\s+(\d{1,2})(?:h|:)(\d{0,2})(?:\s+(?:pour|de|intitulee)\s+(.+))?$""", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val hour = it.groupValues[1].toIntOrNull() ?: return null
                val minute = it.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
                if (hour !in 0..23 || minute !in 0..59) return null
                return AndroidCommand.SetAlarm(hour, minute, it.groupValues.getOrNull(3).orEmpty().ifBlank { "BOZER" })
            }

        Regex("""^(?:rappelle-moi|rappelle moi)\s+dans\s+(\d{1,5})\s*(secondes?|secs?|minutes?|mins?|heures?|h)(?:\s+(?:(?:de|pour)\s+|d['’])(.+))?$""", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val amount = it.groupValues[1].toLongOrNull() ?: return null
                val unit = it.groupValues[2].lowercase(Locale.ROOT)
                val factor = when {
                    unit.startsWith("h") -> 3600L
                    unit.startsWith("m") -> 60L
                    else -> 1L
                }
                val delay = amount * factor
                if (delay !in 1L..604800L) return null
                return AndroidCommand.SetReminder(delay, it.groupValues.getOrNull(3).orEmpty().ifBlank { "Rappel BOZER" })
            }

        Regex("""^(?:mets|programme|regle)\s+(?:une\s+)?alarme\s+(?:a|à)\s+(\d{1,2})(?:h|:)(\d{0,2})(?:\s+(?:pour|intitulee)\s+(.+))?$""", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val hour = it.groupValues[1].toIntOrNull() ?: return null
                val minute = it.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: return null
                if (hour !in 0..23 || minute !in 0..59) return null
                return AndroidCommand.SetAlarm(hour, minute, it.groupValues.getOrNull(3).orEmpty().ifBlank { "BOZER" })
            }

        Regex("""^(?:lance|mets)\s+(?:un\s+)?minuteur\s+(\d{1,4})\s*(s|sec|seconde|secondes|min|minute|minutes)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let {
                val amount = it.groupValues[1].toIntOrNull() ?: return null
                val seconds = if (it.groupValues[2].lowercase().startsWith("m")) amount * 60 else amount
                if (seconds !in 1..86400) return null
                return AndroidCommand.SetTimer(seconds, "BOZER")
            }

        Regex("""^partage\s+(?:ce\s+)?message\s*[: ]\s*(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.ShareText(it.groupValues[1]) }

        Regex("""^(?:ouvre|lance|demarre)\s+(?:l'application\s+|l'appli\s+|application\s+)?(.+)$""", RegexOption.IGNORE_CASE)
            .find(text)?.let { return AndroidCommand.OpenApp(it.groupValues[1].trim()) }

        return null
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[’']"), " ")
            .replace(Regex("[-_]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
