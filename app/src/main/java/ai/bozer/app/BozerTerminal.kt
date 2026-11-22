package ai.bozer.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TerminalInk = Color(0xFF202A2F)
private val TerminalMuted = Color(0xFF7B898F)
private val TerminalLine = Color(0xFFDCE4E7)
private val TerminalAccent = Color(0xFF71858D)

/**
 * Intent launcher for the lower BOZER terminal.
 * Organiser opens the local Secretary task manager. Other shortcuts prepare
 * requests in the main conversation until their full modules are implemented.
 */
@Composable
internal fun BozerTerminalSheet(
    onDismiss: () -> Unit,
    onCommand: (String) -> Unit,
    onSecretary: () -> Unit,
    onMemory: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color(0x330F1A20))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(max = 690.dp)
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(Color(0xFFFAFCFD))
                .clickable(onClick = {})
                .padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 26.dp)
        ) {
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFC8D2D6))
            )
            Spacer(Modifier.height(15.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TERMINAL BOZER",
                        color = TerminalInk,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp
                    )
                    Text(
                        "Transformer une intention en commande",
                        color = TerminalMuted,
                        fontSize = 12.sp
                    )
                }
                Text(
                    "Fermer",
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = TerminalAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "CHOISIR UNE INTENTION",
                color = TerminalMuted,
                fontSize = 10.sp,
                letterSpacing = 1.4.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(9.dp))
            Column(
                modifier = Modifier.weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TerminalCommandRow(
                    title = "Parler",
                    detail = "Réfléchir, expliquer ou discuter",
                    onClick = { onCommand("Aide-moi à réfléchir à ") }
                )
                TerminalCommandRow(
                    title = "Agir",
                    detail = "Ouvrir une application ou préparer une action",
                    onClick = { onCommand("Aide-moi à effectuer cette action : ") }
                )
                TerminalCommandRow(
                    title = "Rechercher",
                    detail = "Chercher des informations sur Internet",
                    onClick = { onCommand("Recherche sur Internet : ") }
                )
                TerminalCommandRow(
                    title = "Créer",
                    detail = "Préparer un texte ou un document",
                    onClick = { onCommand("Aide-moi à créer un document sur ") }
                )
                TerminalCommandRow(
                    title = "Organiser",
                    detail = "Transformer un objectif en étapes",
                    onClick = onSecretary
                )
                TerminalCommandRow(
                    title = "Traduire",
                    detail = "Traduire un texte en gardant le ton",
                    onClick = { onCommand("Traduis ce texte en ") }
                )
                TerminalCommandRow(
                    title = "Développer",
                    detail = "Comprendre ou améliorer du code",
                    onClick = { onCommand("Aide-moi à comprendre ou développer ce code : ") }
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(17.dp))
                    .background(Color.White)
                    .border(1.dp, TerminalLine, RoundedCornerShape(17.dp))
                    .clickable(onClick = onMemory)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mémoire BOZER", color = TerminalInk, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.weight(1f))
                Text("Gérer les souvenirs  ›", color = TerminalAccent, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ces raccourcis préparent une demande dans la conversation. Les outils spécialisés sont encore en construction.",
                color = TerminalMuted,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
private fun TerminalCommandRow(
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(17.dp))
            .background(Color.White)
            .border(1.dp, TerminalLine, RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF9BAAB0))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TerminalInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = TerminalMuted, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Text("›", color = TerminalAccent, fontSize = 22.sp)
    }
}
