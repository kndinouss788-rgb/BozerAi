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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val HistoryInk = Color(0xFF202A2F)
private val HistoryMuted = Color(0xFF7B898F)
private val HistoryLine = Color(0xFFDCE4E7)

@Composable
internal fun BozerConversationHistoryScreen(
    conversations: List<ConversationRecord>,
    onSearch: (String) -> Unit,
    onOpen: (ConversationRecord) -> Unit,
    onDelete: (ConversationRecord) -> Unit,
    onNewConversation: () -> Unit,
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ConversationRecord?>(null) }

    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F8F9)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    color = HistoryMuted,
                    fontSize = 30.sp
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Historique", color = HistoryInk, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                    Text("Retrouver et gérer tes conversations", color = HistoryMuted, fontSize = 11.sp)
                }
                TextButton(onClick = onNewConversation) {
                    Text("Nouveau +", color = HistoryInk, fontSize = 12.sp)
                }
            }
            Divider(color = HistoryLine)
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    onSearch(it)
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 12.dp),
                placeholder = { Text("Rechercher une conversation…", color = HistoryMuted) },
                shape = RoundedCornerShape(15.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
            Text(
                "${conversations.size} conversation(s)",
                modifier = Modifier.padding(horizontal = 19.dp, vertical = 3.dp),
                color = HistoryMuted,
                fontSize = 10.sp
            )
            if (conversations.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (query.isBlank()) "Aucune conversation pour le moment." else "Aucun résultat pour cette recherche.",
                        color = HistoryInk,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("Les prochains échanges apparaîtront ici.", color = HistoryMuted, fontSize = 11.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 8.dp, bottom = 22.dp
                    )
                ) {
                    items(conversations, key = { it.id }) { conversation ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpen(conversation) }
                                        .padding(vertical = 3.dp)
                                ) {
                                    Text(
                                        conversation.title,
                                        color = HistoryInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    if (conversation.preview.isNotBlank()) {
                                        Text(
                                            conversation.preview,
                                            color = HistoryMuted,
                                            fontSize = 11.sp,
                                            maxLines = 2
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        DateFormat.getDateTimeInstance(
                                            DateFormat.MEDIUM,
                                            DateFormat.SHORT,
                                            Locale.FRANCE
                                        ).format(Date(conversation.updatedAt)) +
                                            "  ·  ${conversation.messageCount} message(s)",
                                        color = HistoryMuted,
                                        fontSize = 9.sp
                                    )
                                }
                                TextButton(onClick = { pendingDelete = conversation }) {
                                    Text("Supprimer", color = Color(0xFF8B5555), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { conversation ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Color.White,
            title = { Text("Supprimer cette conversation ?", color = HistoryInk) },
            text = {
                Text(
                    "« ${conversation.title} » et ses messages seront supprimés de l'historique local. Cette action est définitive.",
                    color = HistoryMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(conversation)
                    pendingDelete = null
                }) {
                    Text("Supprimer", color = Color(0xFF8B5555))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Garder", color = HistoryInk)
                }
            }
        )
    }
}
