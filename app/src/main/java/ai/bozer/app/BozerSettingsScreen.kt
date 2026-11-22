package ai.bozer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SettingsInk = Color(0xFF202A2F)
private val SettingsMuted = Color(0xFF7B898F)
private val SettingsLine = Color(0xFFDCE4E7)
private val SettingsSurface = Color(0xFFFFFFFF)
private val SettingsAccent = Color(0xFF71858D)

@Composable
internal fun BozerSettingsScreen(
    brainMode: BrainMode,
    onBrainModeChange: (BrainMode) -> Unit,
    remoteConfigured: Boolean,
    modelStatus: String,
    modelBusy: Boolean,
    modelReady: Boolean,
    modelProgress: Float,
    onRetryModel: () -> Unit,
    voiceEnabled: Boolean,
    onVoiceEnabledChange: (Boolean) -> Unit,
    ttsAvailable: Boolean,
    microphoneGranted: Boolean,
    floatingBubbleEnabled: Boolean,
    overlayPermissionGranted: Boolean,
    onFloatingBubbleChange: (Boolean) -> Unit,
    rememberedCount: Int,
    onManageMemory: () -> Unit,
    onClearHistory: () -> Unit,
    onClearMemory: () -> Unit,
    onClose: () -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F8F9)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                    color = SettingsAccent,
                    fontSize = 31.sp
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Réglages", color = SettingsInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("BOZER AI · préférences et contrôle", color = SettingsMuted, fontSize = 11.sp)
                }
            }
            Divider(color = SettingsLine)
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 17.dp, vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                SettingsCard("PROFIL") {
                    Text("Profil local BOZER", color = SettingsInk, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Aucun compte distant n'est nécessaire pour utiliser le cerveau local. La gestion de compte n'est pas encore activée.",
                        color = SettingsMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }

                SettingsCard("CERVEAU ET RÉSEAU") {
                    Text("Mode de fonctionnement", color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BrainMode.values().forEach { mode ->
                            val selected = brainMode == mode
                            Text(
                                mode.label,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) Color(0xFFE5ECEF) else Color(0xFFF7F9FA))
                                    .border(1.dp, if (selected) Color(0xFFB5C4CA) else SettingsLine, RoundedCornerShape(12.dp))
                                    .clickable { onBrainModeChange(mode) }
                                    .padding(horizontal = 4.dp, vertical = 11.dp),
                                color = if (selected) SettingsInk else SettingsMuted,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        when (brainMode) {
                            BrainMode.AUTO -> "Les conversations utilisent le cerveau local. Les recherches demandées peuvent utiliser Internet."
                            BrainMode.OFFLINE -> "Le cerveau et les données de conversation restent sur le téléphone. La recherche Internet est désactivée."
                            BrainMode.ONLINE -> "Le texte de la conversation nécessaire à la réponse est envoyé à la passerelle BOZER configurée."
                        },
                        color = SettingsMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Divider(color = SettingsLine)
                    Text("Cerveau intégré à l'application", color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(modelStatus, color = SettingsMuted, fontSize = 11.sp, lineHeight = 16.sp)
                    if (modelBusy) {
                        LinearProgressIndicator(
                            progress = { modelProgress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = SettingsAccent,
                            trackColor = Color(0xFFE7ECEE)
                        )
                    }
                    if (!modelReady && !modelBusy) {
                        Button(
                            onClick = onRetryModel,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SettingsInk)
                        ) {
                            Text("Réessayer le chargement", color = Color.White)
                        }
                    }
                    Text(
                        if (remoteConfigured) "Passerelle HTTPS : adresse configurée, disponibilité non diagnostiquée."
                        else "Passerelle HTTPS : non configurée.",
                        color = SettingsMuted,
                        fontSize = 10.sp
                    )
                }

                SettingsCard("VOIX") {
                    SettingSwitch(
                        title = "Lire les réponses à voix haute",
                        detail = "Activé par défaut. Tu peux interrompre la lecture avec le mode silencieux.",
                        checked = voiceEnabled,
                        onChange = onVoiceEnabledChange
                    )
                    Divider(color = SettingsLine)
                    StatusLine("Synthèse vocale française", if (ttsAvailable) "Disponible" else "Initialisation ou service indisponible")
                    StatusLine("Microphone", if (microphoneGranted) "Autorisation accordée" else "Autorisation non accordée")
                    Text(
                        "La reconnaissance passe par le service Android installé sur le téléphone. BOZER ne garde pas le micro ouvert en permanence.",
                        color = SettingsMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                SettingsCard("MÉMOIRE ET HISTORIQUE") {
                    Text("$rememberedCount souvenir(s) explicite(s)", color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Les souvenirs durables sont séparés des messages et peuvent être corrigés ou oubliés.", color = SettingsMuted, fontSize = 11.sp)
                    TextButton(onClick = onManageMemory) { Text("Gérer les souvenirs", color = SettingsAccent) }
                    Divider(color = SettingsLine)
                    TextButton(onClick = onClearHistory) { Text("Effacer l'historique", color = Color(0xFF8B5555)) }
                    TextButton(onClick = onClearMemory) { Text("Effacer les souvenirs", color = Color(0xFF8B5555)) }
                }

                SettingsCard("CONFIDENTIALITÉ") {
                    Text(
                        "En mode Automatique ou Hors ligne, BOZER traite la conversation avec le modèle local. En mode En ligne, le contenu envoyé à la passerelle est nécessaire pour obtenir la réponse. Les clés fournisseur ne doivent jamais être stockées dans l'APK.",
                        color = SettingsMuted,
                        fontSize = 11.sp,
                        lineHeight = 17.sp
                    )
                }

                SettingsCard("AUTORISATIONS ANDROID") {
                    StatusLine("Microphone", if (microphoneGranted) "Accordé" else "À demander lors de l'utilisation")
                    StatusLine("Superposition / bulle", if (overlayPermissionGranted) "Autorisé" else "Non autorisé")
                    SettingSwitch(
                        title = "Bulle flottante",
                        detail = "Raccourci déplaçable. Elle n'écoute pas et n'exécute pas d'action.",
                        checked = floatingBubbleEnabled,
                        onChange = onFloatingBubbleChange
                    )
                }

                SettingsCard("APPARENCE ET STOCKAGE") {
                    StatusLine("Thème", "Blanc perlé et argent")
                    StatusLine("Animations", "Effets discrets ; réduction adaptative à finaliser")
                    StatusLine("Cerveau Qwen GGUF", "Environ 1,1 Go intégrés à l'APK")
                    Text(
                        "Après installation, le cerveau est copié dans le stockage privé de BOZER. Prévoir de l'espace supplémentaire pour cette copie.",
                        color = SettingsMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }

                SettingsCard("DIAGNOSTIC") {
                    StatusLine("Modèle", if (modelReady) "Chargé" else if (modelBusy) "Préparation en cours" else "Non prêt")
                    StatusLine("Passerelle", if (remoteConfigured) "Configurée, non vérifiée" else "Non configurée")
                    StatusLine("TTS", if (ttsAvailable) "Disponible" else "Non confirmé")
                    StatusLine("Micro", if (microphoneGranted) "Autorisé" else "Non autorisé")
                    Text(
                        "Le diagnostic décrit l'état local connu. Il ne prouve pas la disponibilité du réseau ni le bon fonctionnement d'un service distant.",
                        color = SettingsMuted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.height(22.dp))
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SettingsSurface,
        shape = RoundedCornerShape(19.dp),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text(title, color = SettingsMuted, fontSize = 10.sp, letterSpacing = 1.3.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, color = SettingsInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(detail, color = SettingsMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StatusLine(label: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = SettingsMuted, fontSize = 11.sp)
        Text(status, color = SettingsInk, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}
