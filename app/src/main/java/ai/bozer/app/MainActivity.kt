package ai.bozer.app

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.net.Uri\nimport android.net.ConnectivityManager\nimport android.net.NetworkCapabilities
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource\nimport androidx.compose.ui.platform.LocalContext
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
private val Lilac = Color(0xFF9870D5)
private val SoftLilac = Color(0xFFF0E8FB)
private val GlassWhite = Color(0xDFFFFFFF)

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
        val repository = ChatRepository(ChatDatabase(applicationContext), engine, remoteBrain = remoteBrain)\n        repository.brainMode = BrainMode.from(getSharedPreferences("bozer_settings", MODE_PRIVATE).getString("brain_mode", "AUTO"))\n        repository.internetAvailable = { hasValidatedInternet() }
        val modelManager = LocalModelManager(applicationContext)
        val actionExecutor = AndroidActionExecutor(this)
        setContent {
            BozerChat(repository, modelManager, engine, actionExecutor,
                ::enableFloatingBubble, ::disableFloatingBubble)
        }
    }

    private fun hasValidatedInternet(): Boolean {\n        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager\n        val network = manager.activeNetwork ?: return false\n        val capabilities = manager.getNetworkCapabilities(network) ?: return false\n        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&\n            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)\n    }\n\n    override fun onDestroy() {
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
    val context = LocalContext.current\n    var brainMode by remember { mutableStateOf(repository.brainMode) }\n    fun chooseBrainMode(mode: BrainMode) {\n        brainMode = mode\n        repository.brainMode = mode\n        context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)\n            .edit().putString("brain_mode", mode.name).apply()\n    }\n    val messages = remember { mutableStateListOf<ChatMessage>().apply { addAll(repository.history()) } }
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

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Pearl) {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFFF4EDFF), Pearl, Color(0xFFF9F2FC)))
            )) {
                Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundControl("☰") { settingsOpen = true }
                        Spacer(Modifier.weight(1f))
                        Text("BOZER", color = Ink, fontSize = 18.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.size(42.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.88f))
                                .clickable { settingsOpen = true },
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.foundation.Image(
                                painter = painterResource(R.drawable.ic_bozer_robot),
                                contentDescription = "Logo BOZER",
                                modifier = Modifier.size(31.dp)
                            )
                        }
                    }

                    if (messages.isEmpty()) {
                        WelcomePanel(
                            onSuggestion = { draft = it },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(top = 8.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(messages) { message -> MessageBubble(message) }
                            if (sending) item {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RobotBadge(34.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Text("BOZER réfléchit…", color = Muted, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 18.dp)
                            .shadow(14.dp, RoundedCornerShape(30.dp))
                            .background(GlassWhite, RoundedCornerShape(30.dp))
                            .padding(start = 18.dp, end = 7.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            textStyle = TextStyle(color = Ink, fontSize = 15.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Lilac),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) Text("Écris à BOZER…",
                                    color = Color(0xFF9A95A6), fontSize = 15.sp)
                                inner()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            singleLine = false
                        )
                        Button(
                            onClick = { send() },
                            enabled = draft.isNotBlank() && !sending && when (brainMode) {\n                                BrainMode.AUTO -> modelReady || repository.remoteConfigured\n                                BrainMode.ONLINE -> repository.remoteConfigured\n                                BrainMode.OFFLINE -> modelReady\n                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.size(46.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Lilac,
                                disabledContainerColor = Color(0xFFE4DDEC)
                            )
                        ) { Text("↑", fontSize = 23.sp, color = Color.White) }
                    }
                }
            }
        }

        if (settingsOpen) {
            AlertDialog(
                onDismissRequest = { settingsOpen = false },
                containerColor = Color(0xFFFFFDFF),
                title = { Text("BOZER", color = Ink, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Mode du cerveau", color = Ink, fontWeight = FontWeight.SemiBold)
                        BrainMode.entries.forEach { mode ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .background(if (brainMode == mode) SoftLilac else Color.Transparent)
                                    .clickable { chooseBrainMode(mode) }
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = brainMode == mode, onClick = { chooseBrainMode(mode) })
                                Column(Modifier.weight(1f)) {
                                    Text(mode.label, color = Ink, fontWeight = FontWeight.Medium)
                                    Text(mode.description, color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                                }
                            }
                        }
                        TextButton(onClick = { installAndLoad() }, enabled = !modelBusy && !modelReady,
                            modifier = Modifier.fillMaxWidth()) {
                            Text(if (modelReady) "Cerveau local prêt" else if (modelBusy)
                                "Installation en cours…" else "Installer le cerveau local", color = Ink)
                        }
                        if (modelBusy || modelStatus.startsWith("Installation impossible") ||
                            modelStatus.startsWith("Chargement impossible")) {
                            Text(modelStatus, color = Muted, fontSize = 12.sp)
                            if (modelBusy) LinearProgressIndicator(
                                progress = { downloadProgress }, modifier = Modifier.fillMaxWidth(),
                                color = Lilac, trackColor = SoftLilac
                            )
                        }
                        TextButton(onClick = { onEnableFloatingBubble(); settingsOpen = false },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Activer la bulle flottante", color = Ink)
                        }
                        TextButton(onClick = { onDisableFloatingBubble(); settingsOpen = false },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Désactiver la bulle flottante", color = Ink)
                        }
                        TextButton(onClick = { confirmClearMemory = true },
                            modifier = Modifier.fillMaxWidth()) {
                            Text("Effacer la mémoire", color = Color(0xFF9A5D69))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { settingsOpen = false }) { Text("Fermer", color = Ink) }
                }
            )
            if (confirmClearMemory) {
                AlertDialog(
                    onDismissRequest = { confirmClearMemory = false },
                    containerColor = Color(0xFFFFFDFF),
                    title = { Text("Effacer la mémoire ?", color = Ink) },
                    text = { Text("Cette action efface uniquement les informations mémorisées, pas les conversations.", color = Muted) },
                    confirmButton = {
                        TextButton(onClick = {
                            repository.clearRememberedFacts()
                            confirmClearMemory = false
                        }) { Text("Effacer", color = Color(0xFF9A5D69)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmClearMemory = false }) { Text("Annuler", color = Ink) }
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomePanel(onSuggestion: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF7E9F6), Color(0xFFEDE8FF), Color(0xFFE8F4F7))))
                .padding(vertical = 28.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RobotBadge(142.dp)
                Spacer(Modifier.height(18.dp))
                Text("Ton compagnon IA.", color = Ink, fontSize = 25.sp,
                    fontWeight = FontWeight.Bold)
                Text("Toujours là. Toujours prêt.", color = Muted, fontSize = 14.sp)
            }
        }
        Text("Salut ! Quel est ton premier objectif aujourd’hui ?",
            color = Ink, fontSize = 21.sp, lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().padding(top = 13.dp))
        Text("Dis-moi ce que tu veux faire. On avance ensemble.",
            color = Muted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            SuggestionCard("✦", "Organiser ma journée", Modifier.weight(1f)) {
                onSuggestion("Aide-moi à organiser ma journée.")
            }
            SuggestionCard("◇", "Faire avancer un projet", Modifier.weight(1f)) {
                onSuggestion("Aide-moi à faire avancer mon projet.")
            }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SuggestionCard(icon: String, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(23.dp))
            .background(Color.White.copy(alpha = 0.78f))
            .clickable(onClick = onClick).padding(15.dp)
    ) {
        Text(icon, color = Lilac, fontSize = 22.sp)
        Spacer(Modifier.height(12.dp))
        Text(label, color = Ink, fontSize = 13.sp, lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoundControl(label: String, onClick: () -> Unit) {
    Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.82f))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Ink, fontSize = 20.sp)
    }
}

@Composable
private fun RobotBadge(size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).shadow(12.dp, CircleShape)
            .background(Brush.radialGradient(listOf(Color.White, Color(0xFFF4EAFE), Color(0xFFE5D5F8))), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(R.drawable.ic_bozer_robot),
            contentDescription = "Robot BOZER",
            modifier = Modifier.size(size * 0.82f)
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    if (isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = Color(0xFFEDE5FA),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp,
                    bottomEnd = 7.dp, bottomStart = 22.dp),
                modifier = Modifier.widthIn(max = 310.dp)
            ) {
                Text(message.content, modifier = Modifier.padding(horizontal = 17.dp, vertical = 13.dp),
                    color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top) {
            RobotBadge(34.dp)
            Spacer(Modifier.width(10.dp))
            Text(message.content, modifier = Modifier.weight(1f).padding(top = 4.dp, bottom = 5.dp),
                color = Ink, fontSize = 15.sp, lineHeight = 23.sp)
        }
    }
}
