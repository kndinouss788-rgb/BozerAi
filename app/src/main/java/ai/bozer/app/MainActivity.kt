package ai.bozer.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

private val Pearl = Color(0xFFFCFAFD)
private val Ink = Color(0xFF342B39)
private val Muted = Color(0xFF93889A)
private val Accent = Color(0xFFB17FC0)
private val SoftPink = Color(0xFFF5EAF7)

class MainActivity : ComponentActivity() {
    private lateinit var engine: LocalLlamaEngine
    private lateinit var voiceOutput: BozerVoiceOutput
    private var voiceResultHandler: ((String) -> Unit)? = null

    private val speechRecognitionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) voiceResultHandler?.invoke(spokenText)
        }
        voiceResultHandler = null
    }

    private val speechPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchSpeechRecognition()
        } else {
            voiceResultHandler = null
            Toast.makeText(this, "Autorise le micro pour parler à BOZER.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LocalLlamaEngine()
        voiceOutput = BozerVoiceOutput(applicationContext)
        val remoteBrain = RemoteBrainClient(BuildConfig.BOZER_GATEWAY_URL, applicationContext)
        val repository = ChatRepository(ChatDatabase(applicationContext), engine, remoteBrain = remoteBrain)
        repository.internetAvailable = { hasValidatedInternet() }
        val modelManager = LocalModelManager(applicationContext)
        setContent { BozerChat(this@MainActivity, repository, modelManager, engine, voiceOutput) }
    }

    fun startVoiceInput(onResult: (String) -> Unit) {
        voiceResultHandler = onResult
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            launchSpeechRecognition()
        } else {
            speechPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun launchSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à BOZER")
        }
        try {
            speechRecognitionLauncher.launch(intent)
        } catch (_: Exception) {
            voiceResultHandler = null
            Toast.makeText(this, "La reconnaissance vocale n'est pas disponible sur ce téléphone.", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onDestroy() {
        if (::voiceOutput.isInitialized) voiceOutput.shutdown()
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }
}

@Composable
private fun BozerChat(
    activity: MainActivity,
    repository: ChatRepository,
    modelManager: LocalModelManager,
    engine: LocalLlamaEngine,
    voiceOutput: BozerVoiceOutput
) {
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(repository.history()) } }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Le cerveau local n'est pas encore installé.") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var memoryOpen by remember { mutableStateOf(false) }
    var memoryFacts by remember { mutableStateOf(repository.rememberedFacts()) }
    var editingFact by remember { mutableStateOf<String?>(null) }
    var editedFact by remember { mutableStateOf("") }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmClearMemory by remember { mutableStateOf(false) }
    var pendingPhoneAction by remember { mutableStateOf<PhoneAction?>(null) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    var voiceEnabled by remember {
        mutableStateOf(activity.getPreferences(android.content.Context.MODE_PRIVATE)
            .getBoolean("voice_enabled", false))
    }
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
                modelStatus = "Chargement impossible : " + (error.message ?: "erreur inconnue")
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
                            modelStatus = "Téléchargement : " + downloadedMb + " / " + totalMb + " Mo"
                        }
                    }
                }
                modelStatus = "Modèle vérifié. Chargement du cerveau…"
                withContext(Dispatchers.IO) { engine.load(file.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt — tes messages sont traités sur ce téléphone."
            } catch (error: Exception) {
                modelStatus = "Installation impossible : " + (error.message ?: "erreur inconnue")
            } finally {
                modelBusy = false
            }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        draft = ""
        val userMessage = ChatMessage(role = "user", content = text)
        messages.add(userMessage)
        repository.save(userMessage)
        val phoneAction = PhoneActionPlanner.parse(text)
        if (phoneAction != null) {
            pendingPhoneAction = phoneAction
            return
        }
        sending = true
        scope.launch {
            val answer = withContext(Dispatchers.IO) { repository.reply(messages.toList()) }
            val assistantMessage = ChatMessage(role = "assistant", content = answer)
            messages.add(assistantMessage)
            repository.save(assistantMessage)
            if (voiceEnabled) voiceOutput.speak(answer)
            sending = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    fun cancelPendingPhoneAction() {
        if (pendingPhoneAction != null) {
            val status = ChatMessage(role = "assistant", content = "Action annulée. Rien n'a été lancé.")
            messages.add(status)
            repository.save(status)
            pendingPhoneAction = null
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Pearl) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFDFF), Color(0xFFFCF9FD), Color(0xFFF8F2FA))
                    )
                )
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(kind = "menu", onClick = { settingsOpen = true })
                        Spacer(Modifier.weight(1f))
                        Text(
                            "BOZER",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            letterSpacing = 2.4.sp
                        )
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(
                            kind = "wave",
                            active = voiceEnabled,
                            onClick = {
                                voiceEnabled = !voiceEnabled
                                activity.getPreferences(android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("voice_enabled", voiceEnabled).apply()
                                if (!voiceEnabled) voiceOutput.stop()
                            }
                        )
                    }

                    if (messages.isEmpty()) {
                        Column(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            LiquidPresence(
                                size = 248.dp,
                                active = modelBusy || sending
                            )
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "Je suis là.",
                                fontSize = 31.sp,
                                lineHeight = 38.sp,
                                fontWeight = FontWeight.Medium,
                                color = Ink
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Parlons de ce que tu veux accomplir.",
                                fontSize = 13.sp,
                                color = Muted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(top = 18.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            items(messages) { message -> MessageBubble(message) }
                            if (sending) item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                ) {
                                    LiquidPresence(size = 30.dp, active = true)
                                    Spacer(Modifier.width(9.dp))
                                    Text("BOZER réfléchit…", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                            .shadow(13.dp, RoundedCornerShape(30.dp))
                            .background(
                                Brush.linearGradient(listOf(Color.White, Color(0xFFFFFAFF), Color(0xFFF8F0FA))),
                                RoundedCornerShape(30.dp)
                            )
                            .border(1.dp, Color(0xFFECE0EF), RoundedCornerShape(30.dp))
                            .padding(start = 9.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            ComposerIconButton(kind = "plus", onClick = { plusMenuOpen = true })
                            DropdownMenu(
                                expanded = plusMenuOpen,
                                onDismissRequest = { plusMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Nouvelle conversation") },
                                    onClick = {
                                        plusMenuOpen = false
                                        confirmClearHistory = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Gérer les souvenirs") },
                                    onClick = {
                                        plusMenuOpen = false
                                        memoryFacts = repository.rememberedFacts()
                                        memoryOpen = true
                                    }
                                )
                            }
                        }

                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 9.dp),
                            textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 20.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) {
                                    Text(
                                        if (modelReady) "Écris à BOZER…" else "Active le cerveau dans le menu…",
                                        color = Color(0xFFA69BA9),
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            maxLines = 4
                        )

                        ComposerIconButton(
                            kind = "mic",
                            onClick = {
                                activity.startVoiceInput { spoken ->
                                    draft = if (draft.isBlank()) spoken else draft.trimEnd() + " " + spoken
                                }
                            }
                        )
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier.size(43.dp)
                                .shadow(5.dp, CircleShape)
                                .background(
                                    if (draft.isNotBlank() && !sending) {
                                        Brush.linearGradient(listOf(Color(0xFFC99BD3), Color(0xFFAD79BC)))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFFE9DDEB), Color(0xFFE4DCE8)))
                                    },
                                    CircleShape
                                )
                                .clickable(enabled = draft.isNotBlank() && !sending) { send() },
                            contentAlignment = Alignment.Center
                        ) {
                            ToolbarGlyph("send", Color.White)
                        }
                    }
                }
            }
        }

        if (settingsOpen) {
            AlertDialog(
                onDismissRequest = { settingsOpen = false },
                containerColor = Color(0xFFFFFCFF),
                title = { Text("Réglages BOZER", color = Ink, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Cerveau local", color = Ink, fontWeight = FontWeight.Medium)
                        Text(modelStatus, color = Muted, fontSize = 13.sp)
                        if (modelBusy && downloadProgress > 0f) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent,
                                trackColor = SoftPink
                            )
                        } else if (modelBusy) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Accent,
                                trackColor = SoftPink
                            )
                        }
                        if (!modelReady) {
                            Button(
                                onClick = { installAndLoad() },
                                enabled = !modelBusy,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Accent)
                            ) {
                                Text(if (modelBusy) "Préparation en cours…" else "Installer le cerveau")
                            }
                        }
                        Text("Version 0.2.0 · Interface liquide", color = Muted, fontSize = 12.sp)
                        Text(
                            "Tes conversations et souvenirs restent enregistrés sur ce téléphone.",
                            color = Muted,
                            fontSize = 12.sp
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Lecture vocale", color = Ink, fontWeight = FontWeight.Medium)
                                Text("Lire les réponses avec la voix Android française.", color = Muted, fontSize = 11.sp)
                            }
                            Switch(
                                checked = voiceEnabled,
                                onCheckedChange = { enabled ->
                                    voiceEnabled = enabled
                                    activity.getPreferences(android.content.Context.MODE_PRIVATE)
                                        .edit().putBoolean("voice_enabled", enabled).apply()
                                    if (!enabled) voiceOutput.stop()
                                }
                            )
                        }
                        TextButton(onClick = {
                            memoryFacts = repository.rememberedFacts()
                            settingsOpen = false
                            memoryOpen = true
                        }) {
                            Text("Gérer les souvenirs (" + repository.rememberedFacts().size + ")", color = Ink)
                        }
                        TextButton(onClick = {
                            settingsOpen = false
                            confirmClearHistory = true
                        }) {
                            Text("Effacer l'historique", color = Color(0xFF995D68))
                        }
                        TextButton(onClick = {
                            settingsOpen = false
                            confirmClearMemory = true
                        }) {
                            Text("Effacer la mémoire", color = Color(0xFF995D68))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { settingsOpen = false }) {
                        Text("Fermer", color = Ink)
                    }
                }
            )
        }

        if (memoryOpen) {
            AlertDialog(
                onDismissRequest = { memoryOpen = false },
                containerColor = Color(0xFFFFFCFF),
                title = { Text("Mémoire locale", color = Ink, fontWeight = FontWeight.SemiBold) },
                text = {
                    if (memoryFacts.isEmpty()) {
                        Text("Aucun souvenir enregistré.", color = Muted)
                    } else {
                        Column(
                            Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            memoryFacts.forEach { fact ->
                                Column {
                                    Text(fact, color = Ink, fontSize = 13.sp, lineHeight = 18.sp)
                                    Row {
                                        TextButton(onClick = {
                                            editingFact = fact
                                            editedFact = fact
                                            memoryOpen = false
                                        }) { Text("Modifier", color = Accent) }
                                        TextButton(onClick = {
                                            repository.forgetRememberedFact(fact)
                                            memoryFacts = repository.rememberedFacts()
                                        }) { Text("Oublier", color = Color(0xFF995D68)) }
                                    }
                                }
                                Divider(color = Color(0xFFECE3EE))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { memoryOpen = false }) { Text("Fermer", color = Ink) }
                }
            )
        }

        if (editingFact != null) {
            AlertDialog(
                onDismissRequest = { editingFact = null },
                containerColor = Color(0xFFFFFCFF),
                title = { Text("Corriger un souvenir", color = Ink, fontWeight = FontWeight.SemiBold) },
                text = {
                    OutlinedTextField(
                        value = editedFact,
                        onValueChange = { editedFact = it },
                        label = { Text("Information corrigée") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = editedFact.isNotBlank(),
                        onClick = {
                            val oldFact = editingFact
                            if (oldFact != null && repository.updateRememberedFact(oldFact, editedFact)) {
                                memoryFacts = repository.rememberedFacts()
                            }
                            editingFact = null
                            memoryOpen = true
                        }
                    ) { Text("Enregistrer", color = Ink) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editingFact = null
                        memoryOpen = true
                    }) { Text("Annuler", color = Muted) }
                }
            )
        }

        if (confirmClearHistory) {
            AlertDialog(
                onDismissRequest = { confirmClearHistory = false },
                containerColor = Color(0xFFFFFCFF),
                title = { Text("Effacer l'historique ?", color = Ink) },
                text = {
                    Text(
                        "Les messages de cette conversation seront supprimés. Les souvenirs explicites seront conservés.",
                        color = Muted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        repository.clearHistory()
                        messages.clear()
                        confirmClearHistory = false
                    }) { Text("Effacer", color = Color(0xFF995D68)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearHistory = false }) { Text("Annuler", color = Ink) }
                }
            )
        }

        if (confirmClearMemory) {
            AlertDialog(
                onDismissRequest = { confirmClearMemory = false },
                containerColor = Color(0xFFFFFCFF),
                title = { Text("Effacer la mémoire ?", color = Ink) },
                text = {
                    Text(
                        "Tous les souvenirs explicites enregistrés sur ce téléphone seront supprimés. L'historique restera intact.",
                        color = Muted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        repository.clearMemory()
                        memoryFacts = emptyList()
                        confirmClearMemory = false
                    }) { Text("Effacer", color = Color(0xFF995D68)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearMemory = false }) { Text("Annuler", color = Ink) }
                }
            )
        }

        pendingPhoneAction?.let { action ->
            AlertDialog(
                onDismissRequest = { cancelPendingPhoneAction() },
                containerColor = Color(0xFFFFFCFF),
                title = { Text(PhoneActionPlanner.confirmationTitle(action), color = Ink) },
                text = { Text(PhoneActionPlanner.confirmationDescription(action), color = Muted) },
                confirmButton = {
                    TextButton(onClick = {
                        val result = PhoneActionPlanner.execute(activity, action)
                        val status = ChatMessage(role = "assistant", content = result.message)
                        messages.add(status)
                        repository.save(status)
                        pendingPhoneAction = null
                    }) { Text("Confirmer", color = Ink) }
                },
                dismissButton = {
                    TextButton(onClick = { cancelPendingPhoneAction() }) {
                        Text("Annuler", color = Muted)
                    }
                }
            )
        }
    }
}

@Composable
private fun LiquidPresence(
    size: androidx.compose.ui.unit.Dp,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "bozer-liquid")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (active) 2500 else 5200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "liquid-flow"
    )
    val ripple = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    var rippleCenter by remember { mutableStateOf(Offset.Unspecified) }

    Canvas(
        modifier = modifier.size(size).pointerInput(Unit) {
            detectTapGestures { point ->
                rippleCenter = point
                scope.launch {
                    ripple.snapTo(0f)
                    ripple.animateTo(1f, animationSpec = tween(1050))
                }
            }
        }
    ) {
        val w = this.size.width
        val h = this.size.height
        val sway = sin(phase * 2f * PI.toFloat())
        val center = Offset(w * 0.5f, h * 0.5f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x66E9C9F1), Color(0x22E9C9F1), Color.Transparent),
                center = center,
                radius = w * 0.52f
            ),
            radius = w * 0.52f,
            center = center
        )

        val body = Path().apply {
            moveTo(w * 0.50f, h * (0.10f + sway * 0.018f))
            cubicTo(w * 0.72f, h * 0.01f, w * 0.94f, h * 0.19f, w * (0.87f - sway * 0.025f), h * 0.42f)
            cubicTo(w * 1.03f, h * 0.64f, w * 0.80f, h * 0.91f, w * 0.57f, h * 0.84f)
            cubicTo(w * 0.34f, h * 0.98f, w * 0.10f, h * 0.76f, w * 0.19f, h * 0.54f)
            cubicTo(w * 0.02f, h * 0.33f, w * 0.25f, h * 0.12f, w * 0.50f, h * (0.10f + sway * 0.018f))
            close()
        }
        drawPath(
            body,
            Brush.linearGradient(
                colors = listOf(Color(0xFFFFFDFF), Color(0xFFECD6F1), Color(0xFFF8E9F8)),
                start = Offset(w * 0.18f, h * 0.12f),
                end = Offset(w * 0.82f, h * 0.90f)
            )
        )
        drawPath(
            body,
            Color.White.copy(alpha = 0.70f),
            style = Stroke(width = 1.1.dp.toPx())
        )

        val innerFlow = Path().apply {
            moveTo(w * 0.18f, h * (0.51f + sway * 0.025f))
            cubicTo(w * 0.34f, h * 0.27f, w * 0.50f, h * 0.25f, w * 0.64f, h * 0.42f)
            cubicTo(w * 0.78f, h * 0.59f, w * 0.78f, h * 0.70f, w * 0.56f, h * 0.77f)
        }
        drawPath(
            innerFlow,
            Brush.linearGradient(listOf(Color.White.copy(alpha = 0.78f), Color(0xFFC89BD4).copy(alpha = 0.24f))),
            style = Stroke(width = 3.0.dp.toPx())
        )

        val highlight = Path().apply {
            moveTo(w * 0.28f, h * 0.31f)
            cubicTo(w * 0.42f, h * 0.16f, w * 0.61f, h * 0.16f, w * 0.73f, h * 0.31f)
        }
        drawPath(
            highlight,
            Color.White.copy(alpha = 0.92f),
            style = Stroke(width = 2.2.dp.toPx())
        )

        drawCircle(
            Color.White.copy(alpha = 0.84f),
            radius = w * 0.018f,
            center = Offset(w * 0.72f, h * 0.25f)
        )
        drawCircle(
            Color(0xFFD7B1E0).copy(alpha = 0.76f),
            radius = w * 0.012f,
            center = Offset(w * 0.28f, h * 0.72f)
        )

        if (rippleCenter != Offset.Unspecified && ripple.value < 1f) {
            val progress = ripple.value
            drawCircle(
                color = Color.White.copy(alpha = (1f - progress) * 0.82f),
                radius = w * (0.10f + progress * 0.43f),
                center = rippleCenter,
                style = Stroke(width = (2.4f - progress * 1.4f).dp.toPx())
            )
            drawCircle(
                color = Color(0xFFD2A5DE).copy(alpha = (1f - progress) * 0.48f),
                radius = w * (0.04f + progress * 0.28f),
                center = rippleCenter,
                style = Stroke(width = 1.1.dp.toPx())
            )
        }
    }
}

@Composable
private fun GlassIconButton(
    kind: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier.size(43.dp)
            .shadow(5.dp, CircleShape)
            .background(Color.White.copy(alpha = 0.78f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.95f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ToolbarGlyph(kind, if (active) Accent else Color(0xFF82748A))
    }
}

@Composable
private fun ComposerIconButton(kind: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(39.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ToolbarGlyph(kind, Color(0xFF9775A0))
    }
}

@Composable
private fun ToolbarGlyph(kind: String, color: Color) {
    Canvas(Modifier.size(21.dp)) {
        val w = size.width
        val h = size.height
        val line = 1.8.dp.toPx()
        when (kind) {
            "menu" -> {
                drawLine(color, Offset(w * 0.17f, h * 0.27f), Offset(w * 0.83f, h * 0.27f), line)
                drawLine(color, Offset(w * 0.17f, h * 0.50f), Offset(w * 0.70f, h * 0.50f), line)
                drawLine(color, Offset(w * 0.17f, h * 0.73f), Offset(w * 0.83f, h * 0.73f), line)
            }
            "plus" -> {
                drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.84f), line)
                drawLine(color, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), line)
            }
            "mic" -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(w * 0.36f, h * 0.10f),
                    size = Size(w * 0.28f, h * 0.48f),
                    cornerRadius = CornerRadius(w * 0.14f, h * 0.14f)
                )
                drawArc(
                    color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(w * 0.22f, h * 0.31f),
                    size = Size(w * 0.56f, h * 0.49f),
                    style = Stroke(width = line)
                )
                drawLine(color, Offset(w * 0.50f, h * 0.79f), Offset(w * 0.50f, h * 0.92f), line)
                drawLine(color, Offset(w * 0.34f, h * 0.92f), Offset(w * 0.66f, h * 0.92f), line)
            }
            "send" -> {
                drawLine(color, Offset(w * 0.50f, h * 0.84f), Offset(w * 0.50f, h * 0.20f), line)
                drawLine(color, Offset(w * 0.23f, h * 0.47f), Offset(w * 0.50f, h * 0.20f), line)
                drawLine(color, Offset(w * 0.77f, h * 0.47f), Offset(w * 0.50f, h * 0.20f), line)
            }
            "wave" -> {
                val points = listOf(
                    Offset(w * 0.12f, h * 0.53f),
                    Offset(w * 0.26f, h * 0.35f),
                    Offset(w * 0.39f, h * 0.68f),
                    Offset(w * 0.52f, h * 0.20f),
                    Offset(w * 0.65f, h * 0.75f),
                    Offset(w * 0.78f, h * 0.39f),
                    Offset(w * 0.89f, h * 0.53f)
                )
                for (index in 0 until points.lastIndex) {
                    drawLine(color, points[index], points[index + 1], line)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            LiquidPresence(size = 27.dp, active = false)
            Spacer(Modifier.width(8.dp))
        }
        Surface(
            color = if (isUser) Color(0xFFF7EDF8).copy(alpha = 0.96f) else Color.White.copy(alpha = 0.96f),
            shape = RoundedCornerShape(
                topStart = 22.dp,
                topEnd = 22.dp,
                bottomEnd = if (isUser) 7.dp else 22.dp,
                bottomStart = if (isUser) 22.dp else 7.dp
            ),
            shadowElevation = if (isUser) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Text(
                message.content,
                modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}
