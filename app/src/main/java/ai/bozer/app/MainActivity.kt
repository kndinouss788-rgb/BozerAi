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
    private lateinit var engine: LocalLlamaEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LocalLlamaEngine()
        val repository = ChatRepository(ChatDatabase(applicationContext), engine)
        val modelManager = LocalModelManager(applicationContext)
        setContent { BozerChat(repository, modelManager, engine) }
    }

    override fun onDestroy() {
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }
}

@Composable
private fun BozerChat(repository: ChatRepository, modelManager: LocalModelManager, engine: LocalLlamaEngine) {
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(repository.history()) } }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Le cerveau local n'est pas encore installé.") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (modelManager.verifyInstalled()) {
            modelStatus = "Modèle trouvé. Chargement du cerveau…"
            modelBusy = true
            try {
                withContext(Dispatchers.IO) { engine.load(modelManager.installedModel()!!.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt — hors ligne."
            } catch (error: Exception) {
                modelStatus = "Chargement impossible : ${error.message ?: "erreur inconnue"}"
            } finally {
                modelBusy = false
            }
        }
    }

    fun installAndLoad() {
        if (modelBusy || modelReady) return
        modelBusy = true
        modelStatus = "Téléchargement du modèle Qwen…"
        scope.launch {
            try {
                val file = modelManager.download { downloaded, total ->
                    withContext(Dispatchers.Main) {
                        downloadProgress = if (total > 0L) downloaded.toFloat() / total else 0f
                        val downloadedMb = downloaded / (1024L * 1024L)
                        val totalMb = total / (1024L * 1024L)
                        modelStatus = "Téléchargement : $downloadedMb / $totalMb Mo"
                    }
                }
                modelStatus = "Modèle vérifié. Chargement du cerveau…"
                withContext(Dispatchers.IO) { engine.load(file.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt — tes messages sont traités sur ce téléphone."
            } catch (error: Exception) {
                modelStatus = "Installation impossible : ${error.message ?: "erreur inconnue"}"
            } finally {
                modelBusy = false
            }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending || !modelReady) return
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
                        Text(if (modelReady) "Cerveau local actif" else "Assistant IA local", fontSize = 12.sp, color = Color(0xFF858B94))
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if (modelReady) "HORS LIGNE" else "IA LOCALE", fontSize = 10.sp,
                        letterSpacing = 1.2.sp, color = Color(0xFF8B929B))
                }

                if (!modelReady) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(22.dp),
                        shadowElevation = 2.dp
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Installer le cerveau de BOZER", fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold, color = Color(0xFF252A31))
                            Spacer(Modifier.height(6.dp))
                            Text("Qwen 0.5B · environ 491 Mo · stockage privé · fonctionne ensuite sans Internet.",
                                fontSize = 13.sp, lineHeight = 19.sp, color = Color(0xFF858B94))
                            Spacer(Modifier.height(12.dp))
                            Text(modelStatus, fontSize = 12.sp, color = Color(0xFF626B76))
                            if (modelBusy && downloadProgress > 0f) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                            } else if (modelBusy) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { installAndLoad() },
                                enabled = !modelBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF252A31))
                            ) {
                                Text(if (modelBusy) "Installation en cours…" else "Télécharger et installer")
                            }
                        }
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
                        Text(if (modelReady) "Je suis BOZER. Que veux-tu faire ?" else "Installe mon cerveau pour commencer à discuter.",
                            color = Color(0xFF858B94), fontSize = 14.sp)
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
                            if (draft.isEmpty()) Text(if (modelReady) "Écris à BOZER…" else "Installe d'abord le cerveau…",
                                color = Color(0xFFA0A6AE), fontSize = 15.sp)
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        singleLine = false
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { send() },
                        enabled = draft.isNotBlank() && !sending && modelReady,
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
