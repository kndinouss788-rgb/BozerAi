package ai.bozer.app

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.Manifest
import android.speech.RecognizerIntent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF292638)
private val Muted = Color(0xFF817C91)
private val Pearl = Color(0xFFFAF9FE)
private val Lilac = Color(0xFF6688B8)
private val SoftLilac = Color(0xFFEAF1F8)
private val GlassWhite = Color(0xDFFFFFFF)
private val Ice = Color(0xFFE8F4FA)
private val Silver = Color(0xFFB8C8D8)

class MainActivity : ComponentActivity() {
    private lateinit var engine: LocalLlamaEngine

    private val overlayPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (Settings.canDrawOverlays(this)) startFloatingBubbleService()
        }

    private fun enableFloatingBubble() {
        if (Settings.canDrawOverlays(this)) startFloatingBubbleService()
        else {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(permissionIntent)
        }
    }

    private fun startFloatingBubbleService() {
        startService(Intent(this, BozerFloatingService::class.java))
    }

    private fun disableFloatingBubble() {
        stopService(Intent(this, BozerFloatingService::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        engine = LocalLlamaEngine()
        val remoteBrain = RemoteBrainClient(BuildConfig.BOZER_GATEWAY_URL, applicationContext)
        val repository = ChatRepository(ChatDatabase(applicationContext), engine, remoteBrain = remoteBrain)
        repository.brainMode = BrainMode.from(getSharedPreferences("bozer_settings", MODE_PRIVATE).getString("brain_mode", "AUTO"))
        repository.internetAvailable = { hasValidatedInternet() }
        val modelManager = LocalModelManager(applicationContext)
        val actionExecutor = AndroidActionExecutor(this)
        setContent {
            BozerChat(repository, modelManager, engine, actionExecutor,
                ::enableFloatingBubble, ::disableFloatingBubble)
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
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }
}

@Composable
private fun BozerChat(
    repository: ChatRepository,
    modelManager: LocalModelManager,
    engine: LocalLlamaEngine,
    actionExecutor: AndroidActionExecutor,
    onEnableFloatingBubble: () -> Unit,
    onDisableFloatingBubble: () -> Unit
) {
    val context = LocalContext.current
    var brainMode by remember { mutableStateOf(repository.brainMode) }
    fun chooseBrainMode(mode: BrainMode) {
        brainMode = mode
        repository.brainMode = mode
        context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
            .edit().putString("brain_mode", mode.name).apply()
    }
    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(repository.history()) } }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Le cerveau local n'est pas encore installé.") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var confirmClearMemory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (modelManager.verifyInstalled()) {
            modelStatus = "Chargement du cerveau local…"
            modelBusy = true
            try {
                withContext(Dispatchers.IO) { engine.load(modelManager.installedModel()!!.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt."
            } catch (error: Exception) {
                modelStatus = "Chargement impossible : ${error.message ?: "erreur inconnue"}"
            } finally { modelBusy = false }
        }
    }

    fun installAndLoad() {
        if (modelBusy || modelReady) return
        modelBusy = true
        modelStatus = "Téléchargement du modèle Qwen…"
        scope.launch {
            try {
                var lastPercent = -1
                val file = modelManager.download { downloaded, total ->
                    val percent = if (total > 0L) ((downloaded * 100L) / total).toInt() else 0
                    if (percent != lastPercent || downloaded == total) {
                        lastPercent = percent
                        withContext(Dispatchers.Main) {
                            downloadProgress = if (total > 0L) downloaded.toFloat() / total else 0f
                            modelStatus = "Téléchargement : ${downloaded / 1048576L} / ${total / 1048576L} Mo"
                        }
                    }
                }
                withContext(Dispatchers.IO) { engine.load(file.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt — hors ligne."
            } catch (error: Exception) {
                modelStatus = "Installation impossible : ${error.message ?: "erreur inconnue"}"
            } finally { modelBusy = false }
        }
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        val actionReply = actionExecutor.execute(text)
        if (actionReply == null && !modelReady && !repository.remoteConfigured) return
        draft = ""
        val userMessage = ChatMessage(role = "user", content = text)
        messages.add(userMessage)
        repository.save(userMessage)
        sending = true
        scope.launch {
            val answer = actionReply ?: withContext(Dispatchers.IO) { repository.reply(messages.toList()) }
            val assistantMessage = ChatMessage(role = "assistant", content = answer)
            messages.add(assistantMessage)
            repository.save(assistantMessage)
            sending = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val voiceResult = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) draft = listOf(draft.trim(), spoken).filter { it.isNotBlank() }.joinToString(" ")
    }
    val voicePermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Parle à BOZER")
            }
            voiceResult.launch(intent)
        }
    }
    fun startVoice() { voicePermission.launch(Manifest.permission.RECORD_AUDIO) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Pearl) {
            Box(Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFFF9FCFF), Color(0xFFEAF4FB), Color(0xFFF7FAFF), Color(0xFFFDFEFF)))
            )) {
                AmbientLiquidBackground()
                Column(Modifier.fillMaxSize().padding(horizontal = 17.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton("☰", "Ouvrir les paramètres") { settingsOpen = true }
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("BOZER", color = DeepBlue, fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold, letterSpacing = 3.2.sp)
                            Text("INTELLIGENCE • ACTIVE", color = Muted.copy(alpha = .85f),
                                fontSize = 8.sp, letterSpacing = 1.7.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(46.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFDDF5FF), Color(0xFFB9D9EE))))
                            .border(1.dp, Color.White.copy(alpha=.95f), CircleShape)
                            .clickable { settingsOpen = true }, contentAlignment = Alignment.Center) {
                            FutureOrb(34.dp)
                        }
                    }

                    if (messages.isEmpty()) {
                        FutureWelcome(
                            onSuggestion = { draft = it },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(top = 14.dp, bottom = 22.dp),
                            verticalArrangement = Arrangement.spacedBy(21.dp)
                        ) {
                            items(messages) { message -> FutureMessageBubble(message) }
                            if (sending) item {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 4.dp)) {
                                    FutureOrb(34.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Text("BOZER réfléchit", color = Muted, fontSize = 13.sp)
                                    ThinkingDots()
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 15.dp)
                            .shadow(22.dp, RoundedCornerShape(34.dp), ambientColor = Color(0x333E759D), spotColor = Color(0x333E759D))
                            .clip(RoundedCornerShape(34.dp))
                            .background(Brush.linearGradient(listOf(Color(0xF7FFFFFF), Color(0xEAF1FAFF), Color(0xF9FFFFFF))))
                            .border(1.dp, Brush.linearGradient(listOf(Color.White, Color(0x99B9D8EB), Color.White)), RoundedCornerShape(34.dp))
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton("＋", "Ajouter") { /* reserved for future attachments */ }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 13.dp),
                            textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 21.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF65A9D0)),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) Text("Écris à BOZER…", color = Color(0xFF8D9AAA), fontSize = 15.sp)
                                inner()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            singleLine = false
                        )
                        if (draft.isBlank()) {
                            Box(Modifier.size(46.dp).clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFFECF8FF), Color(0xFFDCECF8))))
                                .border(1.dp, Color.White, CircleShape)
                                .clickable { startVoice() }, contentAlignment = Alignment.Center) {
                                Text("⌁", color = DeepBlue, fontSize = 26.sp, fontWeight = FontWeight.Light)
                            }
                        } else {
                            Box(Modifier.size(46.dp).shadow(9.dp, CircleShape)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(Color(0xFF294E70), Color(0xFF102B49))))
                                .clickable(enabled = !sending && when (brainMode) {
                                    BrainMode.AUTO -> modelReady || repository.remoteConfigured
                                    BrainMode.ONLINE -> repository.remoteConfigured
                                    BrainMode.OFFLINE -> modelReady
                                }) { send() }, contentAlignment = Alignment.Center) {
                                Text("↑", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        if (settingsOpen) {
            AlertDialog(
                onDismissRequest = { settingsOpen = false },
                containerColor = Color(0xFFF9FCFF),
                shape = RoundedCornerShape(30.dp),
                title = {
                    Column {
                        Text("Paramètres", color = DeepBlue, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text("Personnalise ton expérience BOZER", color = Muted, fontSize = 12.sp)
                    }
                },
                text = {
                    Column(Modifier.fillMaxWidth().heightIn(max = 470.dp).padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSection("INTELLIGENCE", "Mode du cerveau") {
                            BrainMode.entries.forEach { mode ->
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                                        .background(if (brainMode == mode) Color(0xFFE7F3FC) else Color.White.copy(alpha=.72f))
                                        .clickable { chooseBrainMode(mode) }
                                        .padding(horizontal = 9.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = brainMode == mode, onClick = { chooseBrainMode(mode) },
                                        colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF477EA8)))
                                    Column(Modifier.weight(1f)) {
                                        Text(mode.label, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(mode.description, color = Muted, fontSize = 10.sp, lineHeight = 14.sp)
                                    }
                                }
                            }
                            TextButton(onClick = { installAndLoad() }, enabled = !modelBusy && !modelReady) {
                                Text(if (modelReady) "Cerveau local prêt" else if (modelBusy) "Installation en cours…" else "Installer le cerveau local")
                            }
                            if (modelBusy || modelStatus.startsWith("Installation impossible") || modelStatus.startsWith("Chargement impossible")) {
                                Text(modelStatus, color = Muted, fontSize = 11.sp)
                                if (modelBusy) LinearProgressIndicator(progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth(), color = Color(0xFF6BA6CE), trackColor = SoftLilac)
                            }
                        }
                        SettingsSection("APPAREIL", "Commandes rapides") {
                            SettingsAction("Bulle flottante", "Activer la bulle sur les autres applications") {
                                onEnableFloatingBubble(); settingsOpen = false
                            }
                            SettingsAction("Bulle flottante", "Désactiver la bulle") {
                                onDisableFloatingBubble(); settingsOpen = false
                            }
                        }
                        SettingsSection("DONNÉES", "Mémoire de BOZER") {
                            SettingsAction("Effacer la mémoire", "Supprimer les faits mémorisés") { confirmClearMemory = true }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { settingsOpen = false }) { Text("Fermer", color = DeepBlue) } }
            )
            if (confirmClearMemory) {
                AlertDialog(
                    onDismissRequest = { confirmClearMemory = false },
                    containerColor = Color(0xFFF9FCFF), shape = RoundedCornerShape(26.dp),
                    title = { Text("Effacer la mémoire ?", color = DeepBlue) },
                    text = { Text("Cette action efface uniquement les informations mémorisées, pas les conversations.", color = Muted) },
                    confirmButton = {
                        TextButton(onClick = { repository.clearRememberedFacts(); confirmClearMemory = false }) {
                            Text("Effacer", color = Color(0xFFB65F70))
                        }
                    },
                    dismissButton = { TextButton(onClick = { confirmClearMemory = false }) { Text("Annuler", color = DeepBlue) } }
                )
            }
        }
    }

@Composable
private fun AmbientLiquidBackground() {
    val transition = rememberInfiniteTransition(label = "liquid")
    val drift by transition.animateFloat(initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawCircle(Color(0x557FD5F0), radius = w * .48f, center = androidx.compose.ui.geometry.Offset(w * (.12f + .08f * drift), h * .20f))
        drawCircle(Color(0x337AA8E8), radius = w * .42f, center = androidx.compose.ui.geometry.Offset(w * (.94f - .06f * drift), h * .58f))
        drawCircle(Color(0x22B8E9E8), radius = w * .55f, center = androidx.compose.ui.geometry.Offset(w * .18f, h * (.94f - .04f * drift)))
    }
}

@Composable
private fun FutureOrb(size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(.88f, 1.04f,
        infiniteRepeatable(tween(2100, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.size(size)) {
        val c = androidx.compose.ui.geometry.Offset(this.size.width/2, this.size.height/2)
        val r = this.size.minDimension/2
        drawCircle(Color(0x668BDDF2), r * pulse, c)
        drawCircle(Color(0xFFEAFBFF), r * .78f, c)
        drawCircle(Color(0xFF7FB6D6), r * .59f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = r*.07f))
        drawCircle(Color(0xFF234B70), r * .39f, c)
        drawCircle(Color.White, r * .12f, androidx.compose.ui.geometry.Offset(c.x-r*.14f,c.y-r*.04f))
        drawCircle(Color(0xFF9DEBFA), r * .09f, androidx.compose.ui.geometry.Offset(c.x+r*.16f,c.y-r*.04f))
        drawCircle(Color(0xFFB7F4FF), r * .045f, androidx.compose.ui.geometry.Offset(c.x+r*.48f,c.y-r*.48f))
        drawCircle(Color(0xFFB7F4FF), r * .035f, androidx.compose.ui.geometry.Offset(c.x-r*.49f,c.y+r*.18f))
    }
}

@Composable
private fun GlassIconButton(label: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(43.dp).clip(CircleShape)
        .background(Color.White.copy(alpha=.78f))
        .border(1.dp, Color.White.copy(alpha=.95f), CircleShape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = DeepBlue, fontSize = 21.sp)
    }
}

@Composable
private fun FutureWelcome(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(178.dp), contentAlignment = Alignment.Center) {
            val transition = rememberInfiniteTransition(label = "welcome")
            val rotation by transition.animateFloat(0f, 360f,
                infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "rotation")
            Canvas(Modifier.fillMaxSize()) {
                val center = androidx.compose.ui.geometry.Offset(size.width/2,size.height/2)
                drawCircle(Color(0x2283CDE8), size.minDimension*.48f, center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width=1.2.dp.toPx()))
                drawCircle(Color(0x4485BDE0), size.minDimension*.39f, center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width=1.dp.toPx()))
                val angle = Math.toRadians(rotation.toDouble())
                drawCircle(Color(0xFF77CDE7), 4.dp.toPx(),
                    androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat()*size.minDimension*.48f,
                        center.y + kotlin.math.sin(angle).toFloat()*size.minDimension*.48f))
            }
            FutureOrb(112.dp)
        }
        Spacer(Modifier.height(12.dp))
        Text("L'intelligence en mouvement.", color = DeepBlue, fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text("Une idée, une question, une action. Commençons.",
            color = Muted, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top=7.dp, bottom=22.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FutureSuggestion("✧", "Organiser ma journée", Modifier.weight(1f)) {
                onSuggestion("Aide-moi à organiser ma journée.")
            }
            FutureSuggestion("◈", "Faire avancer un projet", Modifier.weight(1f)) {
                onSuggestion("Aide-moi à faire avancer mon projet.")
            }
        }
    }
}

@Composable
private fun FutureSuggestion(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp))
        .background(Color.White.copy(alpha=.68f))
        .border(1.dp, Color.White, RoundedCornerShape(22.dp))
        .clickable(onClick=onClick).padding(15.dp)) {
        Text(icon, color = Color(0xFF5D9ABD), fontSize=22.sp)
        Spacer(Modifier.height(10.dp))
        Text(label, color=DeepBlue, fontSize=12.sp, lineHeight=17.sp, fontWeight=FontWeight.SemiBold)
    }
}

@Composable
private fun FutureMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    var visible by remember(message.content) { mutableStateOf(false) }
    LaunchedEffect(message.content) { visible = true }
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(420), label="messageFade")
    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(Modifier.widthIn(max=310.dp).clip(RoundedCornerShape(24.dp,24.dp,8.dp,24.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFE7F2FA), Color(0xFFDCECF7))))
                .border(1.dp, Color.White.copy(alpha=.9f), RoundedCornerShape(24.dp,24.dp,8.dp,24.dp))
                .padding(horizontal=17.dp, vertical=13.dp)) {
                Text(message.content, color=Ink, fontSize=15.sp, lineHeight=22.sp, modifier=Modifier.alpha(alpha))
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.Top) {
            FutureOrb(34.dp)
            Spacer(Modifier.width(12.dp))
            Text(message.content, modifier=Modifier.weight(1f).padding(top=4.dp,bottom=5.dp).alpha(alpha),
                color=Ink, fontSize=15.sp, lineHeight=24.sp)
        }
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label="dots")
    val alpha by transition.animateFloat(.25f,1f,
        infiniteRepeatable(tween(650),RepeatMode.Reverse),label="dotAlpha")
    Text(" ···", color=Color(0xFF6BA6CE).copy(alpha=alpha), fontSize=19.sp)
}

@Composable
private fun SettingsSection(kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Color.White.copy(alpha=.76f))
        .border(1.dp, Color.White, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(kicker, color=Color(0xFF6D9DBD), fontSize=9.sp, letterSpacing=1.5.sp, fontWeight=FontWeight.Bold)
        Text(title, color=DeepBlue, fontSize=14.sp, fontWeight=FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun SettingsAction(title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick=onClick)
        .padding(vertical=8.dp, horizontal=3.dp), verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color=Ink, fontSize=13.sp, fontWeight=FontWeight.Medium)
            Text(subtitle, color=Muted, fontSize=10.sp)
        }
        Text("›", color=Color(0xFF6D9DBD), fontSize=23.sp)
    }
}
