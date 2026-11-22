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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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

private val SecretaryInk = Color(0xFF202A2F)
private val SecretaryMuted = Color(0xFF7B898F)
private val SecretaryLine = Color(0xFFDCE4E7)
private val SecretaryAccent = Color(0xFF71858D)

@Composable
internal fun BozerSecretaryScreen(
    tasks: List<SecretaryTask>,
    onAdd: (String) -> Unit,
    onToggle: (SecretaryTask, Boolean) -> Unit,
    onDelete: (SecretaryTask) -> Unit,
    onClose: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val completed = tasks.count { it.isCompleted }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F8F9)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "‹",
                    modifier = Modifier.clip(CircleShape)
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    color = SecretaryAccent,
                    fontSize = 30.sp
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("SECRETARY", color = SecretaryInk, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp)
                    Text("Tes tâches, au même endroit", color = SecretaryMuted, fontSize = 11.sp)
                }
            }
            Divider(color = SecretaryLine)
            Column(Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SecretaryMetric(tasks.count { !it.isCompleted }.toString(), "À faire", Modifier.weight(1f))
                    SecretaryMetric(completed.toString(), "Terminées", Modifier.weight(1f))
                    SecretaryMetric(tasks.size.toString(), "Total", Modifier.weight(1f))
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 180) draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ajouter une tâche…", color = SecretaryMuted) },
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val item = draft.trim()
                        if (item.isNotEmpty()) {
                            onAdd(item)
                            draft = ""
                        }
                    })
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val item = draft.trim()
                        if (item.isNotEmpty()) {
                            onAdd(item)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SecretaryInk)
                ) {
                    Text("Ajouter à ma liste", color = Color.White)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "LISTE DES TÂCHES",
                    color = SecretaryMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.4.sp
                )
            }
            if (tasks.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ta liste est vide.", color = SecretaryInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Ajoute une première tâche pour commencer.", color = SecretaryMuted, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 17.dp, end = 17.dp, top = 2.dp, bottom = 22.dp
                    )
                ) {
                    items(tasks, key = { it.id }) { task ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color.White,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = task.isCompleted,
                                    onCheckedChange = { checked -> onToggle(task, checked) }
                                )
                                Column(Modifier.weight(1f).padding(start = 3.dp)) {
                                    Text(
                                        task.title,
                                        color = if (task.isCompleted) SecretaryMuted else SecretaryInk,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (task.isCompleted) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                    Text(
                                        "Ajoutée le " + DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRANCE)
                                            .format(Date(task.createdAt)),
                                        color = SecretaryMuted,
                                        fontSize = 10.sp
                                    )
                                }
                                TextButton(onClick = { onDelete(task) }) {
                                    Text("Supprimer", color = Color(0xFF8B5555), fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
            Text(
                "Les alarmes et rappels horaires se programment séparément avec confirmation Android.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                color = SecretaryMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SecretaryMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .border(1.dp, SecretaryLine, RoundedCornerShape(15.dp))
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = SecretaryInk, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = SecretaryMuted, fontSize = 10.sp)
    }
}
