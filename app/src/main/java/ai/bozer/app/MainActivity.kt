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
private val Ink = Color(0xFF26313A)
private val Muted = Color(0xFF7D8993)
private val GlassEdge = Color(0xFFFFFFFF)
private val Accent = Color(0xFF718A9A)
private val Lavender = Color(0xFFE8EEF1)
private val SoftLilac = Color(0xFFF2F5F7)

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
    var confirmClearMemory by remember { mutableStateOf(false) }
    val rememberedFacts = remember { mutableStateListOf<String>().apply { addAll(repository.rememberedFacts()) } }
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
            rememberedFacts.clear()
            rememberedFacts.addAll(repository.rememberedFacts())
            sending = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Pearl) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFFF7F9FA), Color(0xFFFBFCFD), Color(0xFFEEF2F4)))
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
                                    color = Accent, trackColor = Color(0xFFDDE6EA))
                            } else if (modelBusy) {
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Accent,
                                    trackColor = Color(0xFFE7EDF0))
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
                        Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("TON ESPACE BOZER", fontSize = 10.sp, letterSpacing = 1.8.sp,
                                    fontWeight = FontWeight.SemiBold, color = Accent)
                                Spacer(Modifier.height(8.dp))
                                Text("Salut ! Prêt à\nfaire avancer tes idées ?",
                                    fontSize = 27.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                                Spacer(Modifier.height(7.dp))
                                Text("Une idée, une question, un projet… commençons ici.",
                                    fontSize = 12.sp, lineHeight = 17.sp, color = Muted)
                            }
                            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                                BozerOrb(size = 108.dp, active = modelReady || sending)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text("TON PROCHAIN PAS", fontSize = 10.sp, letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.SemiBold, color = Muted)
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            FeatureCard("✦", "Réfléchir", "Clarifier une idée", Modifier.weight(1f)) {
                                draft = "Aide-moi à réfléchir à une idée et à trouver la meilleure direction."
                            }
                            FeatureCard("⌁", "S'organiser", "Préparer ma journée", Modifier.weight(1f)) {
                                draft = "Aide-moi à organiser ma journée."
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FeatureCard("◇", "Découvrir avec BOZER", "Apprendre, comprendre et progresser",
                            Modifier.fillMaxWidth()) {
                            draft = "Propose-moi quelque chose d'intéressant à apprendre aujourd'hui."
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(if (modelReady) "BOZER est là. On avance ensemble." else "Installe le cerveau pour commencer.",
                                color = Muted, fontSize = 11.sp)
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
                        .background(Brush.linearGradient(listOf(Color.White, Color(0xFFF5F8FA))), RoundedCornerShape(30.dp))
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF718A9A), disabledContainerColor = Color(0xFFDDE6EA))) {
                        Text("↑", fontSize = 22.sp, color = Color.White)
                    }
                }
            }
            }
        }
        if (settingsOpen) {
            AlertDialog(
                onDismissRequest = { settingsOpen = false },
                containerColor = Color(0xFFFBFCFD),
                title = { Text("Réglages BOZER", color = Ink, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cerveau", color = Ink, fontWeight = FontWeight.Medium)
                        Text(modelStatus, color = Muted, fontSize = 13.sp)
                        Text("Version 0.2.0 · Interface nacrée", color = Muted, fontSize = 12.sp)
                        Text("Tes conversations restent enregistrées sur ce téléphone.", color = Muted, fontSize = 12.sp)
                        Text("Mémoire explicite : ${rememberedFacts.size} information(s) retenue(s).", color = Muted, fontSize = 12.sp)
                        rememberedFacts.asReversed().forEach { fact ->
                            Text("• $fact", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                },
                confirmButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { confirmClearMemory = true }) {
                            Text("Effacer la mémoire", color = Color(0xFF8A5D5D))
                        }
                        TextButton(onClick = { settingsOpen = false }) {
                            Text("Fermer", color = Ink)
                        }
                    }
                }
            )
            if (confirmClearMemory) {
                AlertDialog(
                    onDismissRequest = { confirmClearMemory = false },
                    containerColor = Color(0xFFFBFCFD),
                    title = { Text("Effacer les informations mémorisées ?", color = Ink) },
                    text = {
                        Text(
                            "BOZER oubliera les ${rememberedFacts.size} information(s) que tu lui as explicitement demandé de retenir. Tes conversations ne seront pas supprimées.",
                            color = Muted
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            repository.clearRememberedFacts()
                            rememberedFacts.clear()
                            confirmClearMemory = false
                        }) {
                            Text("Effacer", color = Color(0xFF8A5D5D))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClearMemory = false }) {
                            Text("Annuler", color = Ink)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun BozerOrb(size: androidx.compose.ui.unit.Dp, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "bozer-mascot")
    val pulse by transition.animateFloat(
        initialValue = 0.97f, targetValue = if (active) 1.035f else 1.015f,
        animationSpec = infiniteRepeatable(animation = tween(if (active) 1050 else 2400), repeatMode = RepeatMode.Reverse),
        label = "mascot-pulse"
    )
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val headW = w * 0.78f * pulse
        val headH = h * 0.48f * pulse
        val headTop = h * 0.13f
        // Soft halo behind the little BOZER companion.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f), Color(0xFFDCE5E9).copy(alpha = 0.42f), Color.Transparent),
            Offset(cx, h * 0.47f), w * 0.58f), w * 0.58f, Offset(cx, h * 0.47f))
        // Floating side fins.
        drawOval(Brush.linearGradient(listOf(Color.White, Color(0xFFC7D5DC), Color(0xFF8EA5B2))),
            Offset(w * 0.09f, h * 0.57f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.25f))
        drawOval(Brush.linearGradient(listOf(Color.White, Color(0xFFC7D5DC), Color(0xFF8EA5B2))),
            Offset(w * 0.74f, h * 0.57f), androidx.compose.ui.geometry.Size(w * 0.17f, h * 0.25f))
        // Small glowing body.
        drawOval(Brush.radialGradient(listOf(Color.White, Color(0xFFE4EBEF), Color(0xFFA9BBC4)),
            Offset(cx - w * 0.08f, h * 0.75f), w * 0.29f),
            Offset(w * 0.34f, h * 0.62f), androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.30f))
        // Pearl shell and violet glass face.
        drawOval(Brush.linearGradient(listOf(Color.White, Color(0xFFE7EDF0), Color(0xFFAFC0C8))),
            Offset(cx - headW / 2f, headTop), androidx.compose.ui.geometry.Size(headW, headH))
        drawOval(Brush.linearGradient(listOf(Color(0xFF465D6A), Color(0xFF78909D), Color(0xFF526875))),
            Offset(cx - headW * 0.39f, headTop + headH * 0.22f),
            androidx.compose.ui.geometry.Size(headW * 0.78f, headH * 0.59f))
        // Two gentle luminous eyes.
        drawOval(Color(0xFFF5FAFC), Offset(cx - headW * 0.23f, headTop + headH * 0.48f),
            androidx.compose.ui.geometry.Size(headW * 0.13f, headH * 0.13f))
        drawOval(Color(0xFFF5FAFC), Offset(cx + headW * 0.10f, headTop + headH * 0.48f),
            androidx.compose.ui.geometry.Size(headW * 0.13f, headH * 0.13f))
        // Glass highlight.
        drawOval(Color.White.copy(alpha = 0.62f), Offset(cx - headW * 0.31f, headTop + headH * 0.12f),
            androidx.compose.ui.geometry.Size(headW * 0.28f, headH * 0.09f))
        drawOval(Color.White.copy(alpha = 0.34f), Offset(cx - w * 0.12f, h * 0.68f),
            androidx.compose.ui.geometry.Size(w * 0.10f, h * 0.035f))
    }
}

@Composable
private fun FeatureCard(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = Color.White.copy(alpha = 0.70f),
        shape = RoundedCornerShape(23.dp),
        shadowElevation = 5.dp,
        tonalElevation = 1.dp
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 15.dp)) {
            Box(
                Modifier.size(34.dp).background(
                    Brush.linearGradient(listOf(Color.White, Color(0xFFE2EAEE))),
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(icon, color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun ContextChip(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = Color.White.copy(alpha = 0.76f),
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
            color = if (isUser) Color(0xFFEAF0F3).copy(alpha = 0.94f) else Color.White.copy(alpha = 0.94f),
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
