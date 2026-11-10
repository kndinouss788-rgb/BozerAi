package ai.bozer.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Prépare le transfert d'une vidéo et de sa légende vers TikTok.
 * Android/TikTok garde la main sur l'aperçu et la publication finale.
 */
object TikTokPublisher {
    private val tiktokPackages = listOf(
        "com.zhiliaoapp.musically",
        "com.zhiliaoapp.musically.go"
    )

    fun buildCaption(description: String, hashtags: String): String {
        val cleanDescription = description.trim()
        val tags = hashtags.split(Regex("\\s+|,"))
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
            .joinToString(" ") { "#$it" }
        return listOf(cleanDescription, tags).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    fun openTikTok(context: Context): Boolean {
        val manager = context.packageManager
        val launchIntent = tiktokPackages.firstNotNullOfOrNull { packageName ->
            manager.getLaunchIntentForPackage(packageName)
        } ?: return false
        context.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    fun shareVideo(context: Context, videoUri: Uri, caption: String): String {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, videoUri)
            putExtra(Intent.EXTRA_TEXT, caption)
            clipData = android.content.ClipData.newUri(context.contentResolver, "BOZER video", videoUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val manager = context.packageManager
        val target = tiktokPackages.firstOrNull { packageName ->
            sendIntent.setPackage(packageName)
            sendIntent.resolveActivity(manager) != null
        }
        if (target != null) {
            sendIntent.setPackage(target)
            context.startActivity(sendIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Vidéo transmise à TikTok. Vérifie l'aperçu, la légende et les paramètres, puis appuie toi-même sur Publier."
        }

        sendIntent.setPackage(null)
        val chooser = Intent.createChooser(sendIntent, "Choisir TikTok pour continuer")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (sendIntent.resolveActivity(manager) == null) {
            throw ActivityNotFoundException("Aucune application ne peut recevoir cette vidéo.")
        }
        context.startActivity(chooser)
        return "Choisis TikTok dans le menu de partage. La publication restera à confirmer dans TikTok."
    }
}
