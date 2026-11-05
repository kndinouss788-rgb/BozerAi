package ai.bozer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Pearl = Color(0xFFF8FAFC)
private val Ink = Color(0xFF202630)
private val Muted = Color(0xFF8993A0)
private val GlassEdge = Color(0xFFFFFFFF)
private val Accent = Color(0xFF8C9EAF)

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
    var settingsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val lastUserText = messages.lastOrNull { it.role == "user" }?.content.orEmpty()

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
                var lastReportedPercent = -1
                val file = modelManager.download { downloaded, total ->
                    val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                    if (percent != lastReportedPercent || downloaded == total) {
                        lastReportedPercent = percent
                        withContext(Dispatchers.Main) {
                            downloadProgress = if (total > 0L) downloaded.toFloat() / total else 0f
                            val downloadedMb = downloaded / (1024L * 1024L)
                            val totalMb = total / (1024L * 1024L)
                            modelStatus = "Téléchargement : $downloadedMb / $totalMb Mo"
                        }
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
        Surface(modifier = Modifier.fillMaxSize(), color = Pearl) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF5F8FB), Color(0xFFF9FAFC)))
                )
            ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BozerOrb(size = 46.dp, active = sending || modelBusy)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("BOZER", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Ink, letterSpacing = 1.1.sp)
                        Text(if (sending) "Je suis avec toi" else if (modelReady) "Ton compagnon intelligent" else "Un espace à toi",
                            fontSize = 12.sp, color = Muted)
                    }
                    Spacer(Modifier.weight(1f))
                    Surface(
                        modifier = Modifier.size(42.dp).clickable { settingsOpen = true },
                        color = Color.White.copy(alpha = 0.78f),
                        shape = CircleShape,
                        shadowElevation = 5.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⚙", fontSize = 19.sp, color = Color(0xFF687584))
                        }
                    }
                }

                if (!modelReady) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
                        color = Color.White.copy(alpha = 0.82f),
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 3.dp
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Réveillons le cerveau de BOZER", fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold, color = Ink)
                            Spacer(Modifier.height(6.dp))
                            Text("Qwen 0.5B · environ 491 Mo · stockage privé · fonctionne ensuite sans Internet.",
                                fontSize = 13.sp, lineHeight = 19.sp, color = Muted)
                            Spacer(Modifier.height(12.dp))
                            Text(modelStatus, fontSize = 12.sp, color = Color(0xFF686777))
                            if (modelBusy && downloadProgress > 0f) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(),
                                    color = Accent, trackColor = Color(0xFFEDE7F7))
                            } else if (modelBusy) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent,
                                    trackColor = Color(0xFFEDE7F7))
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { installAndLoad() }, enabled = !modelBusy,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                                Text(if (modelBusy) "Préparation en cours…" else "Installer le cerveau")
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
                        BozerOrb(size = 154.dp, active = modelReady)
                        Spacer(Modifier.height(24.dp))
                        Text(if (modelReady) "Content de te retrouver." else "Un espace à toi.",
                            fontSize = 25.sp, fontWeight = FontWeight.Light, color = Ink)
                        Spacer(Modifier.height(8.dp))
                        Text(if (modelReady) "Qu'est-ce qu'on construit aujourd'hui ?" else "Installe mon cerveau pour commencer.",
                            color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(28.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ContextChip("Réfléchir ensemble", Modifier.weight(1f)) { draft = "Aide-moi à réfléchir à une idée." }
                            ContextChip("M'organiser", Modifier.weight(1f)) { draft = "Aide-moi à organiser ma journée." }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(messages) { message -> MessageBubble(message) }
                        if (sending) item {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
                                BozerOrb(size = 25.dp, active = true)
                                Spacer(Modifier.width(9.dp))
                                Text("BOZER prend le temps de réfléchir…", color = Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (messages.isNotEmpty() && !sending) {
                    val suggestions = contextualSuggestions(lastUserText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        suggestions.forEach { suggestion ->
                            ContextChip(suggestion, Modifier.weight(1f)) { draft = suggestion }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp)
                        .shadow(12.dp, RoundedCornerShape(30.dp))
                        .background(Brush.linearGradient(listOf(Color.White, Color(0xFFFDFBFF))), RoundedCornerShape(30.dp))
                        .padding(start = 19.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier.weight(1f).padding(vertical = 11.dp),
                        textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                        decorationBox = { inner ->
                            if (draft.isEmpty()) Text(if (modelReady) "Parle-moi, je t'écoute…" else "Installe d'abord le cerveau…",
                                color = Color(0xFFA0A2AD), fontSize = 15.sp)
                            inner()
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { send() }),
                        singleLine = false
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { send() }, enabled = draft.isNotBlank() && !sending && modelReady,
                        shape = CircleShape, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF718394), disabledContainerColor = Color(0xFFE7ECF1))) {
                        Text("↑", fontSize = 22.sp, color = Color.White)
                    }
                }
            }
            }
        }
        if (settingsOpen) {
            AlertDialog(
                onDismissRequest = { settingsOpen = false },
                containerColor = Color(0xFFFAFCFE),
                title = { Text("Réglages BOZER", color = Ink, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cerveau", color = Ink, fontWeight = FontWeight.Medium)
                        Text(modelStatus, color = Muted, fontSize = 13.sp)
                        Text("Version 0.2.0 · Interface nacrée", color = Muted, fontSize = 12.sp)
                        Text("Tes conversations restent enregistrées sur ce téléphone.", color = Muted, fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { settingsOpen = false }) {
                        Text("Fermer", color = Ink)
                    }
                }
            )
        }
    }
}

@Composable
private fun BozerOrb(size: androidx.compose.ui.unit.Dp, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "bozer-orb")
    val pulse by transition.animateFloat(
        initialValue = 0.94f, targetValue = if (active) 1.08f else 1.02f,
        animationSpec = infiniteRepeatable(animation = tween(if (active) 1050 else 2200), repeatMode = RepeatMode.Reverse),
        label = "orb-pulse"
    )
    Canvas(Modifier.size(size)) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.43f * pulse
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.98f), Color(0xFFEAF0F5), Color(0xFFC9D5DF)), center, radius * 1.55f), radius * 1.55f, center)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFE7EEF4), Color(0xFFB8C8D6)), Offset(center.x - radius * 0.18f, center.y - radius * 0.2f), radius * 1.12f), radius, center)
        drawCircle(Color.White.copy(alpha = 0.82f), radius * 0.78f, center, style = Stroke(width = this.size.minDimension * 0.018f))
        drawCircle(Color.White.copy(alpha = 0.78f), radius * 0.25f, Offset(center.x - radius * 0.27f, center.y - radius * 0.32f))
        drawCircle(Color(0xFFB7C7D5).copy(alpha = 0.18f), radius * 0.91f, Offset(center.x + radius * 0.13f, center.y + radius * 0.12f), style = Stroke(width = this.size.minDimension * 0.012f))
    }
}

@Composable
private fun ContextChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = Color.White.copy(alpha = 0.82f),
        shape = RoundedCornerShape(18.dp), shadowElevation = 1.dp) {
        Text(label, modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
            color = Color(0xFF777482), fontSize = 11.sp, maxLines = 1)
    }
}

private fun contextualSuggestions(lastMessage: String): List<String> {
    val value = lastMessage.lowercase()
    return when {
        listOf("travail", "projet", "bozer", "construire").any(value::contains) ->
            listOf("Fais un plan", "Quelle est la suite ?")
        listOf("apprendre", "comprendre", "explique", "étudier").any(value::contains) ->
            listOf("Donne un exemple", "Explique plus simplement")
        listOf("problème", "bloqué", "erreur", "marche pas").any(value::contains) ->
            listOf("Aide-moi à corriger", "Quelles sont les causes ?")
        else -> listOf("Développe ton idée", "Donne un exemple")
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom) {
        if (!isUser) {
            BozerOrb(size = 27.dp, active = false)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) Color(0xFFE9EEF3).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f),
            shape = RoundedCornerShape(
                topStart = 22.dp, topEnd = 22.dp,
                bottomEnd = if (isUser) 7.dp else 22.dp,
                bottomStart = if (isUser) 22.dp else 7.dp
            ),
            shadowElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(message.content, modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
                color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
        }
    }
}
