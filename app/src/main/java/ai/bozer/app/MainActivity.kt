package ai.bozer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ChatRepository(ChatDatabase(applicationContext), LocalLlamaClient())
        val modelManager = LocalModelManager(applicationContext)
        setContent { BozerChat(repository, modelManager) }
    }
}

@Composable
private fun BozerChat(repository: ChatRepository, modelManager: LocalModelManager) {
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(repository.history()) } }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var downloadingModel by remember { mutableStateOf(false) }
    var modelProgress by remember { mutableStateOf(0) }
    var modelStatus by remember {
        mutableStateOf(if (modelManager.installedModel() != null) "Modèle téléchargé · prêt à charger" else "Cerveau local non installé")
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        draft = ""
        val userMessage = ChatMessage(role = "user", content = text)
        messages.add(userMessage)
        repository.save(userMessage)
        sending = true
        scope.launch {
            val answer = withContext(Dispatchers.IO) { repository.reply(messages.toList()) }
            val assistantMessage = ChatMessage(role = "assistant", content = answer)
            messages.add(assistantMessage)
            repository.save(assistantMessage)
            sending = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF7F8FA)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(42.dp).shadow(8.dp, CircleShape)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("B", color = Color(0xFF252A31), fontSize = 22.sp, fontWeight = FontWeight.Light)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("BOZER", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF20242A))
                        Text("Ton assistant", fontSize = 12.sp, color = Color(0xFF858B94))
                    }
                    Spacer(Modifier.weight(1f))
                    Text("LOCAL", fontSize = 10.sp, letterSpacing = 1.5.sp, color = Color(0xFF8B929B))
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        modelStatus,
                        modifier = Modifier.weight(1f),
                        color = Color(0xFF858B94),
                        fontSize = 11.sp
                    )
                    if (downloadingModel) {
                        Text("$modelProgress%", color = Color(0xFF687482), fontSize = 11.sp)
                    } else {
                        TextButton(
                            onClick = {
                                downloadingModel = true
                                modelStatus = "Téléchargement du modèle…"
                                scope.launch {
                                    try {
                                        modelManager.download { received, total ->
                                            modelProgress = ((received * 100L) / total).toInt()
                                        }
                                        modelStatus = "Modèle téléchargé · stockage privé vérifié"
                                    } catch (error: Exception) {
                                        modelStatus = error.message ?: "Échec du téléchargement"
                                    } finally {
                                        downloadingModel = false
                                    }
                                }
                            },
                            enabled = modelManager.installedModel() == null
                        ) { Text("Installer le cerveau") }
                    }
                }

                if (messages.isEmpty()) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Bonjour.", fontSize = 30.sp, fontWeight = FontWeight.Light, color = Color(0xFF252A31))
                        Spacer(Modifier.height(8.dp))
                        Text("Je suis BOZER. Que veux-tu faire ?", color = Color(0xFF858B94), fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message -> MessageBubble(message) }
                        if (sending) item {
                            Text("BOZER réfléchit…", color = Color(0xFF9299A2), fontSize = 12.sp,
                                modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp)
                        .shadow(8.dp, RoundedCornerShape(26.dp))
                        .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(26.dp))
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                        textStyle = TextStyle(color = Color(0xFF242932), fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF687482)),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) Text("Écris à BOZER…", color = Color(0xFFA0A6AE), fontSize = 15.sp)
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        singleLine = false
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { send() },
                        enabled = draft.isNotBlank() && !sending,
                        shape = CircleShape,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252A31))
                    ) { Text("↑", fontSize = 21.sp, color = Color.White) }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (isUser) Color(0xFFE8EDF2) else Color.White,
            shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp
            ),
            shadowElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(message.content, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color(0xFF272C34), fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}
