package ai.bozer.app

import android.os.Bundle
import android.app.AlarmManager
import android.os.Build
import android.content.Intent
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF1B292E)
private val Muted = Color(0xFF74848A)
private val DeepBlue = Color(0xFF2D4247)
private val Pearl = Color(0xFFF4F6F3)
private val Lilac = Color(0xFF91BDB2)
private val SoftLilac = Color(0xFFE7F0EC)
private val GlassWhite = Color(0xEFFFFFFF)
private val Ice = Color(0xFFEAF1EE)
private val Silver = Color(0xFFC8D2CD)

class MainActivity : ComponentActivity() {
    private lateinit var engine: LocalLlamaEngine
    private var pendingVoiceStart = false
    private val voiceResult = mutableStateOf<String?>(null)
    private val actionResult = mutableStateOf<String?>(null)
    private var speechInput: BozerSpeechInput? = null
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) speechInput?.start()
    }
    private fun startVoiceInput() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speechInput?.start()
        } else {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1204)
        }
        speechInput = BozerSpeechInput(this, onText = { voiceResult.value = it }, onFailure = { /* user may retry */ })
        engine = LocalLlamaEngine()
        val remoteBrain = RemoteBrainClient(BuildConfig.BOZER_GATEWAY_URL, applicationContext)
        val repository = ChatRepository(ChatDatabase(applicationContext), engine, remoteBrain = remoteBrain)
        repository.brainMode = BrainMode.from(getSharedPreferences("bozer_settings", MODE_PRIVATE).getString("brain_mode", "AUTO"))
        repository.internetAvailable = { hasValidatedInternet() }
        val modelManager = LocalModelManager(applicationContext)
        val actionExecutor = AndroidActionExecutor(this)
        setContent {
            BozerChat(repository, modelManager, engine, actionExecutor,
                ::enableFloatingBubble, ::disableFloatingBubble, voiceResult, actionResult, ::startVoiceInput)
        }
        pendingVoiceStart = intent?.action == BozerFloatingService.ACTION_START_VOICE
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == BozerFloatingService.ACTION_START_VOICE) {
            pendingVoiceStart = true
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun onResume() {
        super.onResume()
        isVisible = true
        notifyFloatingBubbleVisibility(appVisible = true)
        if (pendingVoiceStart) {
            pendingVoiceStart = false
            window.decorView.post { startVoiceInput() }
        }
    }

    override fun onPause() {
        isVisible = false
        notifyFloatingBubbleVisibility(appVisible = false)
        super.onPause()
    }

    private fun notifyFloatingBubbleVisibility(appVisible: Boolean) {
        if (!BozerFloatingService.isRunning) return
        val action = if (appVisible) {
            BozerFloatingService.ACTION_HIDE_BUBBLE
        } else {
            BozerFloatingService.ACTION_SHOW_BUBBLE
        }
        sendBroadcast(Intent(action).setPackage(packageName))
    }

    override fun onDestroy() {
        isVisible = false
        speechInput?.stop()
        speechInput = null
        if (::engine.isInitialized) engine.close()
        super.onDestroy()
    }

    companion object {
        @Volatile
        var isVisible: Boolean = false
            private set
    }
}

@Composable
private fun BozerChat(
    repository: ChatRepository,
    modelManager: LocalModelManager,
    engine: LocalLlamaEngine,
    actionExecutor: AndroidActionExecutor,
    onEnableFloatingBubble: () -> Unit,
    onDisableFloatingBubble: () -> Unit,
    voiceText: androidx.compose.runtime.MutableState<String?>,
    actionText: androidx.compose.runtime.MutableState<String?>,
    onStartVoiceInput: () -> Unit
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
    var pendingAndroidAction by remember { mutableStateOf<String?>(null) }
    var modelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Cerveau hors ligne non téléchargé (≈491 Mo). Le téléchargement est facultatif et se fait une seule fois.") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var actionPermissionDialogOpen by remember { mutableStateOf(false) }
    var actionAccessibilityEnabled by remember { mutableStateOf(BozerAccessibilityService.isConnected()) }
    var overlayPermissionGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var microphonePermissionGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var notificationPermissionGranted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    var terminalOpen by remember { mutableStateOf(false) }
    var bozerSpaceOpen by remember { mutableStateOf(false) }
    var bozerLabOpen by remember { mutableStateOf(false) }
    var animationsEnabled by remember {
        mutableStateOf(context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE).getBoolean("animations_enabled", true))
    }
    var soundEffectsEnabled by remember {
        mutableStateOf(context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE).getBoolean("sound_effects_enabled", true))
    }
    var bozerTheme by remember {
        mutableStateOf(
            BozerTheme.fromStored(
                context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE).getString("theme_mode", "PEARL")
            )
        )
    }
    fun saveBozerTheme(theme: BozerTheme) {
        bozerTheme = theme
        context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", theme.name).apply()
    }
    var activeSpeechMessageAt by remember { mutableStateOf<Long?>(null) }
    fun toggleTerminal() {
        terminalOpen = !terminalOpen
        if (soundEffectsEnabled) {
            BozerInteractionSound.play(
                if (terminalOpen) BozerInteractionSound.Event.OPEN else BozerInteractionSound.Event.CLOSE
            )
        }
    }

    fun closeTerminal() {
        if (!terminalOpen) return
        terminalOpen = false
        if (soundEffectsEnabled) BozerInteractionSound.play(BozerInteractionSound.Event.CLOSE)
    }

    var activeSpeechRange by remember { mutableStateOf<IntRange?>(null) }
    var voiceRepliesEnabled by remember { mutableStateOf(context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE).getBoolean("voice_replies", true)) }
    var voiceTtsStatus by remember { mutableStateOf("Initialisation du moteur vocal…") }
    val voiceOutput = remember(context) {
        BozerVoiceOutput(context) { status -> voiceTtsStatus = status }
    }
    val piperModelManager = remember(context) { BozerPiperModelManager(context) }
    var piperModelInstalled by remember { mutableStateOf(piperModelManager.isInstalled()) }
    var piperReady by remember { mutableStateOf(false) }
    var piperBusy by remember { mutableStateOf(false) }
    var piperProgress by remember { mutableFloatStateOf(0f) }
    val piperVoiceOutput = remember(context) {
        BozerPiperVoiceOutput { status -> voiceTtsStatus = status }
    }
    DisposableEffect(voiceOutput, piperVoiceOutput) {
        onDispose {
            voiceOutput.release()
            piperVoiceOutput.release()
        }
    }
    var secretaryOpen by remember { mutableStateOf(false) }
    var tiktokOpen by remember { mutableStateOf(false) }
    var tiktokDescription by remember { mutableStateOf("") }
    var tiktokHashtags by remember { mutableStateOf("") }
    var tiktokPrompt by remember { mutableStateOf("") }
    var tiktokVideoUri by remember { mutableStateOf<Uri?>(null) }
    var tiktokStatus by remember { mutableStateOf<String?>(null) }
    var tiktokGenerating by remember { mutableStateOf(false) }
    val tiktokVideoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        tiktokVideoUri = uri
        tiktokStatus = if (uri != null) "Vidéo sélectionnée. Tu peux la transmettre à TikTok." else "Aucune vidéo sélectionnée."
    }
    val secretary = remember { SecretaryRepository(ChatDatabase(context)) }
    val secretaryCommands = remember { SecretaryCommandProcessor(secretary) }
    val secretaryTasks = remember { mutableStateListOf<SecretaryTask>() }
    var secretaryDraft by remember { mutableStateOf("") }
    var confirmClearMemory by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(settingsOpen) {
        if (settingsOpen) {
            while (true) {
                actionAccessibilityEnabled = BozerAccessibilityService.isConnected()
                overlayPermissionGranted = Settings.canDrawOverlays(context)
                microphonePermissionGranted =
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                notificationPermissionGranted = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                delay(900)
            }
        }
    }

    LaunchedEffect(secretaryOpen) {
        if (secretaryOpen) {
            secretaryTasks.clear()
            secretaryTasks.addAll(withContext(Dispatchers.IO) { secretary.tasks() })
        }
    }

    LaunchedEffect(voiceRepliesEnabled, piperModelInstalled) {
        if (voiceRepliesEnabled && piperModelInstalled && !piperReady && !piperBusy) {
            piperBusy = true
            try {
                withContext(Dispatchers.IO) { piperVoiceOutput.initialize(piperModelManager.modelDirectory()) }
                piperReady = true
                voiceTtsStatus = "Voix française locale chargée — profil candidat 55 — hors ligne."
            } catch (error: Exception) {
                voiceTtsStatus = "Chargement de la voix locale impossible : " + (error.message ?: "erreur inconnue")
            } finally {
                piperBusy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        // Do not download a 491 MB model automatically at app launch.
        // If it is already present (for example after an app update), load it.
        try {
            val installed = withContext(Dispatchers.IO) { modelManager.verifiedInstalledModel() }
            if (installed == null) {
                modelStatus = "Cerveau hors ligne non installé (≈491 Mo). Télécharge-le une fois depuis ces paramètres pour discuter sans Internet."
            } else {
                modelBusy = true
                modelStatus = "Démarrage du cerveau local BOZER…"
                withContext(Dispatchers.IO) { engine.load(installed.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt — discussion hors ligne disponible."
            }
        } catch (error: Exception) {
            modelStatus = "Préparation du cerveau impossible : ${error.message ?: "erreur inconnue"}"
        } finally {
            modelBusy = false
        }
    }

    fun installLocalBrain() {
        if (modelBusy || modelReady) return
        modelBusy = true
        downloadProgress = 0f
        modelStatus = "Préparation du téléchargement du cerveau local…"
        scope.launch {
            try {
                var lastProgress = 0L
                val installed = modelManager.installModel { downloaded, total ->
                    if (downloaded == total || downloaded - lastProgress >= 1024L * 1024L) {
                        lastProgress = downloaded
                        withContext(Dispatchers.Main) {
                            downloadProgress = if (total > 0L) downloaded.toFloat() / total.toFloat() else 0f
                            modelStatus = "Téléchargement du cerveau : ${downloaded / 1048576L} / ${total / 1048576L} Mo"
                        }
                    }
                }
                modelStatus = "Vérification et démarrage du cerveau local…"
                withContext(Dispatchers.IO) { engine.load(installed.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau local prêt — il restera sur ce téléphone après les mises à jour."
            } catch (error: Exception) {
                modelStatus = "Téléchargement/préparation interrompu : ${error.message ?: "erreur inconnue"}. Tu peux reprendre depuis ici."
            } finally {
                modelBusy = false
            }
        }
    }

    fun installPiperVoice() {
        if (piperBusy || piperReady) return
        piperBusy = true
        piperProgress = 0f
        voiceTtsStatus = if (piperModelInstalled) {
            "Chargement de la voix française locale…"
        } else {
            "Téléchargement de la voix française locale (environ 77 Mo)…"
        }
        scope.launch {
            try {
                val directory = if (piperModelManager.isInstalled()) {
                    piperModelManager.modelDirectory()
                } else {
                    piperModelManager.install { downloaded, total ->
                        scope.launch {
                            piperProgress = if (total > 0L) {
                                (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                        }
                    }
                }
                piperModelInstalled = true
                withContext(Dispatchers.IO) { piperVoiceOutput.initialize(directory) }
                piperReady = true
                voiceTtsStatus = "Voix française locale chargée — profil candidat 55 — hors ligne ; timbre à confirmer."
            } catch (error: Exception) {
                piperModelInstalled = piperModelManager.isInstalled()
                voiceTtsStatus = "Installation de la voix impossible : " + (error.message ?: "erreur inconnue")
            } finally {
                piperBusy = false
            }
        }
    }


    fun speakAssistantMessage(message: ChatMessage) {
        if (!voiceRepliesEnabled) return
        activeSpeechMessageAt = message.createdAt
        activeSpeechRange = null
        val onSegmentStarted: (Int, Int) -> Unit = { start, end ->
            activeSpeechMessageAt = message.createdAt
            activeSpeechRange = start until end
        }
        val onCompleted: () -> Unit = {
            if (activeSpeechMessageAt == message.createdAt) {
                activeSpeechMessageAt = null
                activeSpeechRange = null
            }
        }
        val utteranceId = "bozer-${message.createdAt}"
        if (piperReady) piperVoiceOutput.speak(message.content, utteranceId, onSegmentStarted, onCompleted)
        else voiceOutput.speak(message.content, utteranceId, onSegmentStarted, onCompleted)
    }

    fun appendActionResult(answer: String) {
        val assistantMessage = ChatMessage(role = "assistant", content = answer)
        messages.add(assistantMessage)
        runCatching { repository.save(assistantMessage) }
        speakAssistantMessage(assistantMessage)
        scope.launch { listState.animateScrollToItem(messages.lastIndex) }
    }

    fun cancelPendingAction() {
        if (pendingAndroidAction == null) return
        pendingAndroidAction = null
        appendActionResult("D'accord, je n'ai rien exécuté.")
    }

    fun confirmPendingAction() {
        val command = pendingAndroidAction ?: return
        pendingAndroidAction = null
        val answer = runCatching { actionExecutor.execute(command, userConfirmed = true) }
            .getOrNull() ?: "Je n'ai pas pu préparer cette action."
        appendActionResult(answer)
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return

        if (actionExecutor.requiresUserConfirmation(text)) {
            draft = ""
            val userMessage = ChatMessage(role = "user", content = text)
            messages.add(userMessage)
            runCatching { repository.save(userMessage) }
            pendingAndroidAction = text
            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
            return
        }

        val actionReply = secretaryCommands.execute(text) ?: actionExecutor.execute(text)
        if (actionReply == null && !modelReady && !repository.remoteConfigured) return
        draft = ""
        val userMessage = ChatMessage(role = "user", content = text)
        messages.add(userMessage)
        repository.save(userMessage)
        sending = true
        scope.launch {
            val answer = try {
                actionReply ?: withContext(Dispatchers.IO) { repository.reply(messages.toList()) }
            } catch (error: Exception) {
                "Une partie de BOZER est momentanément bloquée : ${error.message ?: "erreur inconnue"}. Tes alarmes et rappels locaux restent indépendants du cerveau."
            }
            val assistantMessage = ChatMessage(role = "assistant", content = answer)
            messages.add(assistantMessage)
            runCatching { repository.save(assistantMessage) }
            speakAssistantMessage(assistantMessage)
            sending = false
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    DisposableEffect(actionText) {
        BozerAccessibilityService.setResultListener { _, message ->
            actionText.value = message
        }
        onDispose {
            BozerAccessibilityService.setResultListener(null)
        }
    }

    SideEffect {
        actionExecutor.setBozerAppCommandHandler { command ->
            when (command) {
                BozerAppCommand.OpenAccessibilitySettings -> {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    "J'ouvre les paramètres d'accessibilité Android pour configurer BOZER ACTION."
                }
                BozerAppCommand.RequestAccessibilityConsent -> {
                    actionPermissionDialogOpen = true
                    "Avant d'activer BOZER ACTION, je t'explique l'accès demandé."
                }
                BozerAppCommand.OpenSettings -> {
                    settingsOpen = true
                    terminalOpen = false
                    bozerSpaceOpen = false
                    bozerLabOpen = false
                    secretaryOpen = false
                    "J'ouvre les paramètres de BOZER."
                }
                is BozerAppCommand.SetSilentMode -> {
                    voiceRepliesEnabled = !command.enabled
                    context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("voice_replies", voiceRepliesEnabled).apply()
                    if (command.enabled) {
                        voiceOutput.stop()
                        piperVoiceOutput.stop()
                        activeSpeechMessageAt = null
                        activeSpeechRange = null
                        "Mode silencieux activé. Je continue à comprendre tes commandes, mais je ne lis plus mes réponses à voix haute."
                    } else {
                        "Mode silencieux désactivé. Ma voix est de nouveau activée."
                    }
                }
                is BozerAppCommand.SetAnimations -> {
                    animationsEnabled = command.enabled
                    context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("animations_enabled", command.enabled).apply()
                    if (command.enabled) "Animations fluides activées." else "Animations fluides désactivées."
                }
                is BozerAppCommand.SetSoundEffects -> {
                    soundEffectsEnabled = command.enabled
                    context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("sound_effects_enabled", command.enabled).apply()
                    if (command.enabled) "Effets sonores activés." else "Effets sonores désactivés."
                }
                is BozerAppCommand.SetTheme -> {
                    saveBozerTheme(command.theme)
                    "Thème ${command.theme.label} activé."
                }
                is BozerAppCommand.SetBrainMode -> {
                    chooseBrainMode(command.mode)
                    "Mode ${command.mode.label} activé."
                }
                BozerAppCommand.CycleTheme -> {
                    val next = bozerTheme.next()
                    saveBozerTheme(next)
                    "Nouveau thème activé : ${next.label}."
                }
                is BozerAppCommand.SetFloatingBubble -> {
                    if (command.enabled) {
                        val alreadyAllowed = Settings.canDrawOverlays(context)
                        onEnableFloatingBubble()
                        if (alreadyAllowed) "Bulle flottante BOZER activée."
                        else "Je lance la demande d'autorisation Android pour afficher la bulle BOZER."
                    } else {
                        onDisableFloatingBubble()
                        "Bulle flottante BOZER désactivée."
                    }
                }
                BozerAppCommand.OpenVideoCamera,
                BozerAppCommand.CancelCurrentUiMission,
                is BozerAppCommand.ControlVisibleUi,
                is BozerAppCommand.LaunchAppAndUiActions -> "Cette action doit être traitée par le moteur BOZER ACTION."
            }
        }
    }

    LaunchedEffect(actionText.value) {
        val result = actionText.value?.trim().orEmpty()
        if (result.isNotBlank()) {
            actionText.value = null
            appendActionResult(result)
        }
    }

    LaunchedEffect(voiceText.value) {
        val spoken = voiceText.value?.trim().orEmpty()
        if (spoken.isNotBlank()) {
            voiceText.value = null
            draft = spoken
            if (actionExecutor.isActionCommand(spoken)) {
                // Dispatch in the composition scope so clearing voiceText cannot cancel it.
                scope.launch {
                    delay(80)
                    send()
                }
            }
        }
    }

    val themePalette = BozerThemePalette.forTheme(bozerTheme)
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(themePalette.accent),
            onPrimary = Color.White,
            secondary = Color(themePalette.secondary),
            background = Color(themePalette.background),
            surface = Color(themePalette.surface),
            onSurface = Ink
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = Color(themePalette.background)) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(
                            Color(themePalette.gradientStart),
                            Color(themePalette.gradientMiddle),
                            Color(themePalette.gradientEnd)
                        )
                    )
                )
            ) {
                AmbientLiquidBackground(animationsEnabled)
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton("⚙", "Ouvrir les paramètres") { settingsOpen = true }
                        Spacer(Modifier.weight(1f))
                        if (messages.isEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BOZER", color = DeepBlue, fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold, letterSpacing = 3.6.sp)
                                Text("INTELLIGENCE PERSONNELLE", color = Muted.copy(alpha = .9f),
                                    fontSize = 8.sp, letterSpacing = 1.4.sp)
                            }
                        } else {
                            Text("Conversation", color = DeepBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.weight(1f))
                        GlassIconButton("⌘", "Ouvrir le terminal BOZER") { toggleTerminal() }
                    }

                    if (messages.isEmpty()) {
                        FutureWelcome(
                            onSuggestion = { draft = it },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            animationsEnabled = animationsEnabled
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(17.dp)
                        ) {
                            items(messages) { message ->
                                FutureMessageBubble(
                                    message = message,
                                    speechRange = if (activeSpeechMessageAt == message.createdAt) activeSpeechRange else null,
                                    animationsEnabled = animationsEnabled
                                )
                            }
                            if (sending) item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 5.dp, top = 2.dp)
                                ) {
                                    Text("BOZER réfléchit", color = Muted, fontSize = 13.sp)
                                    ThinkingDots()
                                }
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)
                            .shadow(18.dp, RoundedCornerShape(30.dp), ambientColor = Color(0x222D4742), spotColor = Color(0x222D4742))
                            .clip(RoundedCornerShape(30.dp))
                            .background(Brush.linearGradient(listOf(Color(0xF8FFFFFF), Color(0xEAF0F3EF), Color(0xF8FFFFFF))))
                            .border(1.dp, Color.White.copy(alpha = .95f), RoundedCornerShape(30.dp))
                            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton("＋", "Ouvrir les outils de création") { tiktokOpen = true }
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f).padding(horizontal = 11.dp, vertical = 13.dp),
                            textStyle = TextStyle(color = Ink, fontSize = 15.sp, lineHeight = 22.sp),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF6C9B91)),
                            decorationBox = { inner ->
                                if (draft.isEmpty()) Text("Écris à BOZER…", color = Color(0xFF89969A), fontSize = 15.sp)
                                inner()
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send() }),
                            singleLine = false
                        )
                        if (draft.isBlank()) {
                            Box(
                                Modifier.size(45.dp).clip(CircleShape)
                                    .background(Color(0xFFE6EFEB))
                                    .border(1.dp, Color.White, CircleShape)
                                    .clickable { onStartVoiceInput() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⌁", color = DeepBlue, fontSize = 25.sp, fontWeight = FontWeight.Light)
                            }
                        } else {
                            Box(
                                Modifier.size(45.dp).shadow(8.dp, CircleShape).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(Color(0xFF526F69), Color(0xFF2D4641))))
                                    .clickable(enabled = !sending && when (brainMode) {
                                        BrainMode.AUTO -> modelReady
                                        BrainMode.ONLINE -> repository.remoteConfigured
                                        BrainMode.OFFLINE -> modelReady
                                    }) { send() },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("↑", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Box(
                        Modifier.fillMaxWidth().height(22.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { toggleTerminal() })
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(38.dp).height(3.dp).clip(CircleShape)
                                .background(Color(0x66859A94))
                        )
                    }
                }

                AnimatedVisibility(
                    visible = terminalOpen,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(tween(220)),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(tween(160))
                ) {
                    BozerTerminalPanel(
                        onClose = { closeTerminal() },
                        onSelect = { module ->
                            closeTerminal()
                            when (module) {
                                "Discussion" -> Unit
                                "SPACE" -> bozerSpaceOpen = true
                                "LAB" -> bozerLabOpen = true
                                "Recherche" -> draft = "Recherche sur Internet : "
                                "Téléphone" -> draft = "Aide-moi à utiliser mon téléphone."
                                "Organisation" -> secretaryOpen = true
                                "Applications" -> draft = "Ouvre l'application "
                                "Mémoire" -> settingsOpen = true
                                "Paramètres" -> settingsOpen = true
                            }
                        }
                    )
                }
            }
        }

        if (bozerSpaceOpen) {
            BozerSpaceDialog(
                modelReady = modelReady,
                localModelStatus = if (modelReady) "Prêt sur le téléphone" else modelStatus.take(72),
                remoteConfigured = repository.remoteConfigured,
                voiceEnabled = voiceRepliesEnabled,
                animationsEnabled = animationsEnabled,
                onClose = { bozerSpaceOpen = false },
                onOrganisation = {
                    bozerSpaceOpen = false
                    secretaryOpen = true
                },
                onSearch = {
                    bozerSpaceOpen = false
                    draft = "Recherche sur Internet : "
                },
                onPhone = {
                    bozerSpaceOpen = false
                    draft = "Aide-moi à utiliser mon téléphone."
                },
                onSettings = {
                    bozerSpaceOpen = false
                    settingsOpen = true
                }
            )
        }

        if (bozerLabOpen) {
            BozerLabDialog(
                animationsEnabled = animationsEnabled,
                onClose = { bozerLabOpen = false }
            )
        }

        if (tiktokOpen) {
            Dialog(onDismissRequest = { tiktokOpen = false }) {
                Surface(
                    Modifier.fillMaxWidth().widthIn(max = 440.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xF9FFFFFF),
                    shadowElevation = 24.dp
                ) {
                    Column(
                        Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState()).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("BOZER • TIKTOK", color = DeepBlue, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                Text("Prépare ta publication", color = Muted, fontSize = 12.sp)
                            }
                            FutureIconButton("close", { tiktokOpen = false }, 38.dp)
                        }
                        OutlinedButton(
                            onClick = {
                                tiktokVideoPicker.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.VideoOnly
                                    )
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text(if (tiktokVideoUri == null) "＋ Choisir une vidéo" else "✓ Vidéo sélectionnée") }
                        OutlinedTextField(
                            value = tiktokDescription,
                            onValueChange = { tiktokDescription = it.take(2200) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Description de la vidéo") },
                            placeholder = { Text("Présente ta vidéo…") },
                            minLines = 2,
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = tiktokHashtags,
                            onValueChange = { tiktokHashtags = it.take(500) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Hashtags (avec ou sans #)") },
                            placeholder = { Text("bozer ia technologie innovation") },
                            shape = RoundedCornerShape(15.dp)
                        )
                        OutlinedTextField(
                            value = tiktokPrompt,
                            onValueChange = { tiktokPrompt = it.take(1800) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Prompt pour créer/améliorer la vidéo") },
                            placeholder = { Text("Décris la scène, le style, le mouvement et le format…") },
                            minLines = 2,
                            shape = RoundedCornerShape(15.dp)
                        )
                        if (tiktokStatus != null) {
                            Text(tiktokStatus!!, color = Muted, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                if (!tiktokGenerating) {
                                    tiktokGenerating = true
                                    tiktokStatus = "BOZER prépare la publication…"
                                    scope.launch {
                                        try {
                                            val request = """
Tu es le rédacteur TikTok de BOZER. Prépare une publication en français à partir de ces éléments :
Description/thème : ${tiktokDescription.ifBlank { "Propose une idée adaptée à BOZER et à son public." }}
Hashtags souhaités : ${tiktokHashtags.ifBlank { "Choisis des hashtags pertinents et ciblés." }}
Prompt vidéo : ${tiktokPrompt.ifBlank { "Crée un prompt vidéo vertical TikTok, clair et détaillé." }}
Réponds STRICTEMENT avec ces trois sections, sans texte avant ni après :
DESCRIPTION: une légende courte et accrocheuse
HASHTAGS: 5 à 8 hashtags pertinents
PROMPT: un prompt vidéo détaillé (scène, sujet, mouvement, ambiance, cadrage vertical 9:16)
                                            """.trimIndent()
                                            val answer = withContext(Dispatchers.IO) {
                                                repository.reply(listOf(ChatMessage(role = "user", content = request)))
                                            }
                                            fun section(name: String, next: String): String {
                                                val pattern = Regex("(?is)" + name + "\\s*:\\s*(.*?)(?=" + next + "\\s*:|$)")
                                                return pattern.find(answer)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                            }
                                            val description = section("DESCRIPTION", "HASHTAGS")
                                            val hashtags = section("HASHTAGS", "PROMPT")
                                            val prompt = Regex("(?is)PROMPT\\s*:\\s*(.*)$").find(answer)?.groupValues?.getOrNull(1)?.trim().orEmpty()
                                            if (description.isNotBlank()) tiktokDescription = description
                                            if (hashtags.isNotBlank()) tiktokHashtags = hashtags
                                            if (prompt.isNotBlank()) tiktokPrompt = prompt
                                            tiktokStatus = if (description.isNotBlank() || hashtags.isNotBlank() || prompt.isNotBlank()) {
                                                "Publication préparée par BOZER. Relis et modifie le contenu avant de l'envoyer."
                                            } else answer
                                        } catch (error: Exception) {
                                            tiktokStatus = "Génération impossible : ${error.message ?: "erreur inconnue"}"
                                        } finally {
                                            tiktokGenerating = false
                                        }
                                    }
                                }
                            },
                            enabled = !tiktokGenerating && (modelReady || repository.remoteConfigured),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        ) {
                            Text(if (tiktokGenerating) "BOZER prépare…" else "✧ Générer description, hashtags et prompt")
                        }
                        Button(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Prompt vidéo BOZER", tiktokPrompt))
                                tiktokStatus = "Prompt copié. Colle-le dans ton outil de création vidéo."
                            },
                            enabled = tiktokPrompt.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text("Copier le prompt vidéo") }
                        Button(
                            onClick = {
                                try {
                                    val caption = TikTokPublisher.buildCaption(tiktokDescription, tiktokHashtags)
                                    tiktokStatus = if (tiktokVideoUri != null) {
                                        TikTokPublisher.shareVideo(context, tiktokVideoUri!!, caption)
                                    } else if (TikTokPublisher.openTikTok(context)) {
                                        "TikTok est ouvert. Choisis ta vidéo dans l'application, puis ajoute la légende préparée."
                                    } else {
                                        "TikTok n'est pas installé sur cet appareil."
                                    }
                                } catch (error: Exception) {
                                    tiktokStatus = "Impossible d'ouvrir TikTok : ${error.message ?: "erreur inconnue"}"
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text(if (tiktokVideoUri == null) "Ouvrir TikTok" else "Transmettre la vidéo à TikTok") }
                        Text(
                            "BOZER prépare et transmet le contenu. Vérifie le compte, l'aperçu, la visibilité et la légende dans TikTok, puis confirme toi-même la publication.",
                            color = Muted, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        if (pendingAndroidAction != null) {
            val actionToConfirm = pendingAndroidAction!!
            AlertDialog(
                onDismissRequest = { cancelPendingAction() },
                containerColor = Color(0xFFF9FCFF),
                shape = RoundedCornerShape(26.dp),
                title = { Text("Confirmer l'action BOZER ?", color = DeepBlue) },
                text = { Text(actionExecutor.confirmationSummary(actionToConfirm), color = Muted) },
                confirmButton = {
                    TextButton(onClick = { confirmPendingAction() }) {
                        Text("Confirmer", color = Color(0xFF5A7D70))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cancelPendingAction() }) {
                        Text("Annuler", color = DeepBlue)
                    }
                }
            )
        }

        if (settingsOpen) {
            Dialog(
                onDismissRequest = { settingsOpen = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Surface(
                    Modifier.fillMaxWidth().fillMaxHeight(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(themePalette.surface),
                    shadowElevation = 24.dp
                ) {
                    Column(Modifier.fillMaxSize().padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Paramètres", color = DeepBlue, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                                Text("Ton expérience BOZER", color = Muted, fontSize = 12.sp)
                            }
                            FutureIconButton("close", { settingsOpen = false }, 38.dp)
                        }
                        Spacer(Modifier.height(18.dp))
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(17.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { secretaryOpen = true; settingsOpen = false }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Ouvrir BOZER SECRETARY  →", color = Color(0xFF5A7D70), fontWeight = FontWeight.SemiBold)
                                }
                                Text("INTELLIGENCE", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text("Mode du moteur de réponse", color = Color(0xFF2D4247), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                BrainMode.entries.forEach { mode ->
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .background(if (brainMode == mode) Color(0xFFE7F0EA) else Color(0xFFF5F8F5))
                                        .border(1.dp, if (brainMode == mode) Color(0xFFBCCDC2) else Color(0xFFEAF0F4), RoundedCornerShape(16.dp))
                                        .clickable { chooseBrainMode(mode) }.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = brainMode == mode, onClick = { chooseBrainMode(mode) }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF6D9585)))
                                        Column(Modifier.weight(1f)) {
                                            Text(mode.label, color = Color(0xFF2D4247), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                            Text(mode.description, color = Color(0xFF77877F), fontSize = 11.sp, lineHeight = 15.sp)
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Mode silencieux", color = DeepBlue, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Activé : BOZER répond par écrit, sans lecture vocale.", color = Muted, fontSize = 11.sp)
                                    }
                                    Switch(checked = !voiceRepliesEnabled, onCheckedChange = { silent ->
                                        voiceRepliesEnabled = !silent
                                        context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
                                            .edit().putBoolean("voice_replies", voiceRepliesEnabled).apply()
                                        if (silent) {
                                            voiceOutput.stop()
                                            piperVoiceOutput.stop()
                                            activeSpeechMessageAt = null
                                            activeSpeechRange = null
                                        }
                                    })
                                }
                                Text(
                                    voiceTtsStatus,
                                    color = Color(0xFF77877F),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                Text(
                                    "Voix française locale si installée ; sinon voix française fournie par Android.",
                                    color = Color(0xFF9AA6B3),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        val preview = "Bonjour, je suis BOZER. Je suis prêt à t'aider."
                                        if (piperReady) piperVoiceOutput.speak(preview, "bozer_voice_preview")
                                        else voiceOutput.speak(preview, "bozer_voice_preview")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Écouter un essai de la voix", color = Color(0xFF5A7D70))
                                }
                                TextButton(
                                    onClick = {
                                        voiceOutput.stop()
                                        piperVoiceOutput.stop()
                                        activeSpeechMessageAt = null
                                        activeSpeechRange = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Arrêter la voix", color = Color(0xFF77877F))
                                }
                                TextButton(
                                    onClick = { installPiperVoice() },
                                    enabled = !piperBusy && !piperReady,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        when {
                                            piperReady -> "Voix française locale prête"
                                            piperBusy -> "Préparation de la voix locale…"
                                            piperModelInstalled -> "Charger la voix française locale"
                                            else -> "Installer la voix française locale (≈77 Mo)"
                                        },
                                        color = Color(0xFF5A7D70)
                                    )
                                }
                                if (piperBusy) {
                                    LinearProgressIndicator(
                                        progress = { piperProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF7D9F91),
                                        trackColor = Color(0xFFE4ECE6)
                                    )
                                }
                                Text(
                                    "Modèle MLS : attribution CC BY 4.0 indiquée dans les informations BOZER.",
                                    color = Color(0xFF9AA6B3),
                                    fontSize = 10.sp,
                                    lineHeight = 14.sp
                                )
                                TextButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                                        }.onFailure {
                                            voiceTtsStatus = "Impossible d'ouvrir la gestion des voix Android."
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Gérer / installer les voix françaises", color = Color(0xFF5A7D70))
                                }
                                Text(
                                    if (modelReady) "Cerveau local téléchargé et prêt — discussion hors ligne disponible."
                                    else modelStatus,
                                    color = Color(0xFF77877F),
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                                if (modelBusy) {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF7D9F91),
                                        trackColor = Color(0xFFE4ECE6)
                                    )
                                } else if (!modelReady) {
                                    TextButton(
                                        onClick = { installLocalBrain() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Télécharger / reprendre le cerveau hors ligne (≈491 Mo)", color = Color(0xFF5A7D70))
                                    }
                                }
                            }
                            HorizontalDivider(color = Color(0xFFEAF0F4))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("APPARENCE ET INTERFACE", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                FutureSettingLine("Identité BOZER", "Spirale fluide • Verre nacré • Argent", "◉")
                                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("Thème BOZER", color = DeepBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text("Choisis l'ambiance de ton interface", color = Muted, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BozerTheme.entries.forEach { theme ->
                                            FilterChip(
                                                selected = bozerTheme == theme,
                                                onClick = { saveBozerTheme(theme) },
                                                label = { Text(theme.label, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Animations fluides", color = DeepBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("Mouvements, transitions et logo animé", color = Muted, fontSize = 11.sp)
                                    }
                                    Switch(checked = animationsEnabled, onCheckedChange = { enabled ->
                                        animationsEnabled = enabled
                                        context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
                                            .edit().putBoolean("animations_enabled", enabled).apply()
                                    })
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Effets sonores", color = DeepBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text("Petites signatures sonores du terminal", color = Muted, fontSize = 11.sp)
                                    }
                                    Switch(checked = soundEffectsEnabled, onCheckedChange = { enabled ->
                                        soundEffectsEnabled = enabled
                                        context.getSharedPreferences("bozer_settings", Context.MODE_PRIVATE)
                                            .edit().putBoolean("sound_effects_enabled", enabled).apply()
                                    })
                                }
                                FutureSettingLine("Terminal BOZER", "Double tap en bas de l'écran pour l'ouvrir", "⌘")
                            }
                            HorizontalDivider(color = Color(0xFFEAF0F4))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("AUTORISATIONS BOZER ACTION", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                PermissionStatusLine(
                                    "Contrôle des applications",
                                    if (actionAccessibilityEnabled) "Autorisé" else "À autoriser"
                                )
                                PermissionStatusLine(
                                    "Microphone",
                                    if (microphonePermissionGranted) "Autorisé" else "Demandé au premier usage vocal"
                                )
                                PermissionStatusLine(
                                    "Bulle flottante",
                                    if (overlayPermissionGranted) "Autorisé" else "À autoriser pour utiliser la bulle"
                                )
                                PermissionStatusLine(
                                    "Notifications",
                                    if (notificationPermissionGranted) "Autorisé" else "Non autorisé"
                                )
                                Text(
                                    "BOZER ne reçoit pas un pouvoir illimité : Android garde des protections et certaines applications bloquent des actions. Les autres autorisations (contacts, agenda, caméra…) seront demandées uniquement quand une fonction les nécessite.",
                                    color = Muted,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                                TextButton(
                                    onClick = { actionPermissionDialogOpen = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (actionAccessibilityEnabled) "Gérer le contrôle des applications"
                                        else "Autoriser le contrôle vocal des applications",
                                        color = Color(0xFF5A7D70)
                                    )
                                }
                                if (!overlayPermissionGranted) {
                                    TextButton(
                                        onClick = { onEnableFloatingBubble() },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Autoriser l'affichage de la bulle BOZER", color = Color(0xFF5A7D70))
                                    }
                                }

                                Text("FONCTIONS", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                                    if (!alarmManager.canScheduleExactAlarms()) {
                                        TextButton(onClick = {
                                            context.startActivity(Intent(
                                                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                                Uri.parse("package:${context.packageName}")
                                            ))
                                        }, modifier = Modifier.fillMaxWidth()) {
                                            Text("Autoriser les alarmes précises", color = Color(0xFF5A7D70))
                                        }
                                    }
                                }
                                TextButton(onClick = { onEnableFloatingBubble(); settingsOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Activer la bulle flottante", color = Color(0xFF5A7D70)) }
                                TextButton(onClick = { onDisableFloatingBubble(); settingsOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Désactiver la bulle flottante", color = Color(0xFF5A7D70)) }
                            }
                            HorizontalDivider(color = Color(0xFFEAF0F4))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("DONNÉES ET CONFIDENTIALITÉ", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                TextButton(onClick = { confirmClearMemory = true }, modifier = Modifier.fillMaxWidth()) { Text("Effacer la mémoire", color = Color(0xFFB65E70)) }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { settingsOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Fermer", color = Color(0xFF5A7D70), fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            if (actionPermissionDialogOpen) {
                AlertDialog(
                    onDismissRequest = { actionPermissionDialogOpen = false },
                    containerColor = Color(0xFFF9FCFF),
                    shape = RoundedCornerShape(26.dp),
                    title = { Text("Autoriser BOZER ACTION ?", color = DeepBlue) },
                    text = {
                        Text(
                            "Pour exécuter tes commandes dans les autres applications, BOZER a besoin de l'accès d'accessibilité Android. Cet accès peut voir les libellés des éléments visibles, toucher ou maintenir les contrôles, faire glisser et saisir du texte à ta demande. BOZER ne consulte l'écran que pendant une mission explicite ; il n'envoie pas le contenu de l'écran au cerveau distant. Les champs de mot de passe sont ignorés et les actions sensibles demandent encore une confirmation. Tu peux désactiver cet accès à tout moment dans Android.",
                            color = Muted
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            actionPermissionDialogOpen = false
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        }) {
                            Text("Continuer vers Android", color = Color(0xFF5A7D70))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { actionPermissionDialogOpen = false }) {
                            Text("Pas maintenant", color = DeepBlue)
                        }
                    }
                )
            }
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
        if (secretaryOpen) {
            Dialog(onDismissRequest = { secretaryOpen = false }) {
                Surface(
                    Modifier.fillMaxWidth().widthIn(max = 440.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = Color(0xF9FFFFFF),
                    shadowElevation = 24.dp
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("BOZER SECRETARY", color = DeepBlue, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                                Text("Tes tâches, au même endroit.", color = Muted, fontSize = 12.sp)
                            }
                            FutureIconButton("close", { secretaryOpen = false }, 38.dp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = secretaryDraft,
                                onValueChange = { secretaryDraft = it.take(140) },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Ajouter une tâche…") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val title = secretaryDraft.trim()
                                    if (title.isNotEmpty()) {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { secretary.createTask(title) }
                                            secretaryTasks.clear()
                                            secretaryTasks.addAll(withContext(Dispatchers.IO) { secretary.tasks() })
                                            secretaryDraft = ""
                                        }
                                    }
                                },
                                enabled = secretaryDraft.isNotBlank(),
                                shape = RoundedCornerShape(15.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 13.dp)
                            ) { Text("+") }
                        }
                        if (secretaryTasks.isEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                                Text("Aucune tâche pour le moment. Ajoute ta première tâche.", color = Muted, fontSize = 13.sp)
                            }
                        } else {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                secretaryTasks.forEach { task ->
                                    Row(
                                        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                                            .background(Color(0xFFF4F6F3)).padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = task.completed,
                                            onCheckedChange = { checked ->
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        if (checked) secretary.completeTask(task.id) else secretary.reopenTask(task.id)
                                                    }
                                                    secretaryTasks.clear()
                                                    secretaryTasks.addAll(withContext(Dispatchers.IO) { secretary.tasks() })
                                                }
                                            }
                                        )
                                        Text(
                                            task.title,
                                            Modifier.weight(1f),
                                            color = if (task.completed) Muted else DeepBlue,
                                            fontSize = 13.sp,
                                            textDecoration = if (task.completed) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                        )
                                        TextButton(onClick = {
                                            scope.launch {
                                                withContext(Dispatchers.IO) { secretary.deleteTask(task.id) }
                                                secretaryTasks.remove(task)
                                            }
                                        }) { Text("×", color = Color(0xFFB65F70), fontSize = 20.sp) }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { secretaryOpen = false }, modifier = Modifier.fillMaxWidth()) {
                            Text("Fermer", color = Color(0xFF5A7D70))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmbientLiquidBackground(animationsEnabled: Boolean) {
    val transition = rememberInfiniteTransition(label = "pearl-drift")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) 1f else 0f,
        animationSpec = if (animationsEnabled) {
            infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse)
        } else snap(),
        label = "drift"
    )
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val subtle = if (animationsEnabled) 1f else 0.45f
        drawCircle(
            Color(0x187E9D92).copy(alpha = 0.055f * subtle),
            radius = w * 0.43f,
            center = androidx.compose.ui.geometry.Offset(w * (0.06f + 0.035f * drift), h * 0.14f)
        )
        drawCircle(
            Color(0x1B899C9A).copy(alpha = 0.045f * subtle),
            radius = w * 0.38f,
            center = androidx.compose.ui.geometry.Offset(w * (0.96f - 0.025f * drift), h * 0.72f)
        )
    }
}

@Composable
private fun BozerSpiralLogo(
    size: androidx.compose.ui.unit.Dp,
    animationsEnabled: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "bozer-spiral")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) 360f else 0f,
        animationSpec = if (animationsEnabled) {
            infiniteRepeatable(tween(30000, easing = LinearEasing))
        } else snap(),
        label = "spiral-rotation"
    )
    Canvas(Modifier.size(size)) {
        val center = androidx.compose.ui.geometry.Offset(this.size.width / 2f, this.size.height / 2f)
        val radius = this.size.minDimension * 0.43f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x66FFFFFF), Color(0x2298B7AD), Color.Transparent),
                center = center,
                radius = radius * 1.5f
            ),
            radius = radius * 1.42f,
            center = center
        )
        rotate(rotation, pivot = center) {
            repeat(3) { index ->
                rotate(index * 120f, pivot = center) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(center.x - radius * 0.08f, center.y - radius * 0.10f)
                        cubicTo(
                            center.x - radius * 0.02f, center.y - radius * 0.79f,
                            center.x + radius * 0.96f, center.y - radius * 0.82f,
                            center.x + radius * 0.91f, center.y - radius * 0.18f
                        )
                        cubicTo(
                            center.x + radius * 0.88f, center.y + radius * 0.26f,
                            center.x + radius * 0.28f, center.y + radius * 0.42f,
                            center.x + radius * 0.12f, center.y + radius * 0.12f
                        )
                        cubicTo(
                            center.x + radius * 0.02f, center.y - radius * 0.09f,
                            center.x + radius * 0.22f, center.y - radius * 0.36f,
                            center.x + radius * 0.47f, center.y - radius * 0.26f
                        )
                        cubicTo(
                            center.x + radius * 0.67f, center.y - radius * 0.17f,
                            center.x + radius * 0.52f, center.y + radius * 0.05f,
                            center.x + radius * 0.37f, center.y + radius * 0.04f
                        )
                        cubicTo(
                            center.x + radius * 0.49f, center.y - radius * 0.18f,
                            center.x + radius * 0.13f, center.y - radius * 0.42f,
                            center.x - radius * 0.08f, center.y - radius * 0.10f
                        )
                        close()
                    }
                    drawPath(
                        path,
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF617A74), Color(0xFFE8EEEA), Color(0xFF9FB8B0)),
                            start = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                            end = androidx.compose.ui.geometry.Offset(center.x + radius, center.y + radius)
                        )
                    )
                    drawPath(
                        path,
                        color = Color.White.copy(alpha = 0.70f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = radius * 0.018f)
                    )
                }
            }
        }
        drawCircle(Color(0xFF829D94), radius * 0.075f, center)
        drawCircle(Color.White.copy(alpha = 0.85f), radius * 0.026f,
            androidx.compose.ui.geometry.Offset(center.x - radius * 0.02f, center.y - radius * 0.04f))
    }
}

@Composable
private fun GlassIconButton(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.74f))
            .border(1.dp, Color.White.copy(alpha = 0.96f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = DeepBlue, fontSize = 19.sp)
    }
}

@Composable
private fun FutureIconButton(label: String, onClick: () -> Unit, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .background(Color(0xFFE9F0EC))
            .border(1.dp, Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(if (label == "close") "×" else label, color = DeepBlue, fontSize = 21.sp)
    }
}

@Composable
private fun FutureWelcome(
    onSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BozerSpiralLogo(154.dp, animationsEnabled)
        Spacer(Modifier.height(12.dp))
        Text(
            "Une intelligence qui prend forme.",
            color = DeepBlue,
            fontSize = 21.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            "Une idée, une question, une action. Commençons.",
            color = Muted,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(top = 7.dp, bottom = 22.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FutureSuggestion("◌", "Organiser ma journée", Modifier.weight(1f)) {
                onSuggestion("Aide-moi à organiser ma journée.")
            }
            FutureSuggestion("⌁", "Faire avancer un projet", Modifier.weight(1f)) {
                onSuggestion("Aide-moi à faire avancer mon projet.")
            }
        }
    }
}

@Composable
private fun FutureSuggestion(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, Color.White, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(15.dp)
    ) {
        Text(icon, color = Color(0xFF63877D), fontSize = 22.sp)
        Spacer(Modifier.height(9.dp))
        Text(label, color = DeepBlue, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun FutureMessageBubble(
    message: ChatMessage,
    speechRange: IntRange? = null,
    animationsEnabled: Boolean = true
) {
    val isUser = message.role == "user"
    var visible by remember(message.createdAt, message.role) { mutableStateOf(!animationsEnabled) }
    LaunchedEffect(message.createdAt, message.role, animationsEnabled) { visible = true }

    AnimatedVisibility(
        visible = visible || !animationsEnabled,
        enter = fadeIn(tween(230)) + slideInVertically(initialOffsetY = { it / 12 }, animationSpec = tween(230))
    ) {
        if (isUser) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    Modifier.widthIn(max = 310.dp)
                        .clip(RoundedCornerShape(23.dp, 23.dp, 7.dp, 23.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFEAF0EC), Color(0xFFDDE7E1))))
                        .border(1.dp, Color.White.copy(alpha = 0.94f), RoundedCornerShape(23.dp, 23.dp, 7.dp, 23.dp))
                        .padding(horizontal = 17.dp, vertical = 13.dp)
                ) {
                    Text(message.content, color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        } else {
            val annotated = buildAnnotatedString {
                append(message.content)
                if (speechRange != null && message.content.isNotEmpty()) {
                    val start = speechRange.first.coerceIn(0, message.content.length)
                    val end = (speechRange.last + 1).coerceIn(start, message.content.length)
                    if (end > start) addStyle(
                        SpanStyle(
                            background = Color(0xFFE1ECE6),
                            color = Ink,
                            fontWeight = FontWeight.Medium
                        ),
                        start,
                        end
                    )
                }
            }
            Text(
                annotated,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 3.dp),
                color = Ink,
                fontSize = 15.sp,
                lineHeight = 24.sp
            )
        }
    }
}

@Composable
private fun BozerTerminalPanel(
    onClose: () -> Unit,
    onSelect: (String) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().heightIn(max = 390.dp),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = Color(0xF8FAFBF9),
        shadowElevation = 28.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 17.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BOZER CONTROL", color = DeepBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.8.sp)
                    Text("Ton espace de commande", color = Muted, fontSize = 11.sp)
                }
                FutureIconButton("close", onClose, 38.dp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BozerTerminalItem("Discussion", "◌", Modifier.weight(1f)) { onSelect("Discussion") }
                BozerTerminalItem("Recherche", "⌕", Modifier.weight(1f)) { onSelect("Recherche") }
                BozerTerminalItem("Téléphone", "⌘", Modifier.weight(1f)) { onSelect("Téléphone") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BozerTerminalItem("Organisation", "▦", Modifier.weight(1f)) { onSelect("Organisation") }
                BozerTerminalItem("Applications", "◇", Modifier.weight(1f)) { onSelect("Applications") }
                BozerTerminalItem("Mémoire", "◈", Modifier.weight(1f)) { onSelect("Mémoire") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                BozerTerminalItem("BOZER SPACE", "✧", Modifier.weight(1f)) { onSelect("SPACE") }
                BozerTerminalItem("BOZER LAB", "◌", Modifier.weight(1f)) { onSelect("LAB") }
                BozerTerminalItem("Paramètres", "⚙", Modifier.weight(1f)) { onSelect("Paramètres") }
            }
            Text(
                "Utilise × ou l'icône du terminal pour le refermer.",
                color = Muted,
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun BozerSpaceDialog(
    modelReady: Boolean,
    localModelStatus: String,
    remoteConfigured: Boolean,
    voiceEnabled: Boolean,
    animationsEnabled: Boolean,
    onClose: () -> Unit,
    onOrganisation: () -> Unit,
    onSearch: () -> Unit,
    onPhone: () -> Unit,
    onSettings: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = Color(0xFFF5F8F5),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(17.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BOZER SPACE", color = DeepBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Ton espace de travail intelligent", color = Muted, fontSize = 12.sp)
                    }
                    FutureIconButton("close", onClose, 40.dp)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    BozerSpiralLogo(124.dp, animationsEnabled = animationsEnabled)
                }
                Text("ÉTAT DE BOZER", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        .background(Color.White).padding(15.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BozerSpaceStatus("Cerveau local", localModelStatus, modelReady)
                    BozerSpaceStatus("Passerelle en ligne", if (remoteConfigured) "Configurée" else "Non configurée", remoteConfigured)
                    BozerSpaceStatus("Voix BOZER", if (voiceEnabled) "Activée" else "Mode silencieux", voiceEnabled)
                }
                Text("ACCÈS RAPIDE", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BozerTerminalItem("Organisation", "▦", Modifier.weight(1f), onOrganisation)
                    BozerTerminalItem("Recherche", "⌕", Modifier.weight(1f), onSearch)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BozerTerminalItem("Téléphone", "⌘", Modifier.weight(1f), onPhone)
                    BozerTerminalItem("Paramètres", "⚙", Modifier.weight(1f), onSettings)
                }
                Text(
                    "Les fonctions restent accessibles depuis le terminal, sans encombrer la conversation.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun BozerSpaceStatus(title: String, status: String, ready: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(8.dp).clip(CircleShape)
                .background(if (ready) Color(0xFF79A991) else Color(0xFFC7CFCB))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = DeepBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(status, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BozerLabDialog(
    animationsEnabled: Boolean,
    onClose: () -> Unit
) {
    val experiences = listOf(
        "Spirale en apesanteur" to "Trois courbes fluides tournent autour d'un même centre.",
        "Constellation vivante" to "Des points lumineux se rapprochent et forment des connexions.",
        "Halo de verre" to "Des anneaux de lumière se déploient doucement dans l'espace.",
        "Prisme mouvant" to "Les reflets changent de position et révèlent une nouvelle profondeur.",
        "Onde de matière" to "Une ligne souple se propage comme une vibration dans du verre."
    )
    val dayIndex = (java.time.LocalDate.now().dayOfYear - 1) % experiences.size
    val (title, description) = experiences[dayIndex]
    val transition = rememberInfiniteTransition(label = "daily-discovery")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animationsEnabled) 360f else 0f,
        animationSpec = if (animationsEnabled) {
            infiniteRepeatable(tween(9000, easing = LinearEasing))
        } else snap(),
        label = "daily-phase"
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = Color(0xFFF5F8F5),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("BOZER LAB", color = DeepBlue, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text("Une expérience visuelle nouvelle chaque jour", color = Muted, fontSize = 11.sp)
                    }
                    FutureIconButton("close", onClose, 40.dp)
                }
                Spacer(Modifier.height(20.dp))
                Canvas(Modifier.fillMaxWidth().height(255.dp)) {
                    val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val base = minOf(size.width, size.height) * 0.24f
                    val angleRadians = Math.toRadians(phase.toDouble())
                    when (dayIndex) {
                        0 -> {
                            // Spiral in weightlessness: three moving curved arms.
                            rotate(phase, pivot = center) {
                                repeat(3) { index ->
                                    rotate(index * 120f, pivot = center) {
                                        val path = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(center.x - base * 0.08f, center.y)
                                            cubicTo(center.x - base * 0.1f, center.y - base * 1.9f,
                                                center.x + base * 1.8f, center.y - base * 1.7f,
                                                center.x + base * 1.15f, center.y - base * 0.2f)
                                            cubicTo(center.x + base * 0.85f, center.y + base * 0.5f,
                                                center.x + base * 0.25f, center.y + base * 0.35f,
                                                center.x + base * 0.35f, center.y)
                                        }
                                        drawPath(path, Color(0xFF829F91), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2.dp.toPx()))
                                    }
                                }
                            }
                        }
                        1 -> {
                            // Constellation: six linked nodes and a moving beacon.
                            val points = (0 until 6).map { index ->
                                val angle = angleRadians + index * (Math.PI / 3.0)
                                androidx.compose.ui.geometry.Offset(
                                    center.x + kotlin.math.cos(angle).toFloat() * base * (0.9f + (index % 2) * 0.45f),
                                    center.y + kotlin.math.sin(angle).toFloat() * base * (0.9f + (index % 2) * 0.45f)
                                )
                            }
                            points.forEachIndexed { index, point ->
                                val next = points[(index + 2) % points.size]
                                drawLine(Color(0x66829F91), point, next, strokeWidth = 1.2.dp.toPx())
                                drawCircle(Color(0xFF779A8B), 5.dp.toPx(), point)
                            }
                            drawCircle(Color(0xFFB5C9BF), 11.dp.toPx(), center)
                        }
                        2 -> {
                            // Glass halo: layered rings breathe at different speeds.
                            repeat(5) { index ->
                                val radius = base * (0.55f + index * 0.36f) *
                                    (1f + 0.035f * kotlin.math.sin(angleRadians + index).toFloat())
                                drawCircle(
                                    Color(0xFF8BA99B).copy(alpha = 0.14f + index * 0.025f),
                                    radius,
                                    center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = (1.2f + index * 0.18f).dp.toPx())
                                )
                            }
                        }
                        3 -> {
                            // Moving prism: translucent planes rotate in depth.
                            rotate(phase * 0.55f, pivot = center) {
                                repeat(4) { index ->
                                    val width = base * (1.45f - index * 0.13f)
                                    val height = base * (1.45f - index * 0.13f)
                                    drawRoundRect(
                                        color = Color(0x22829F91).copy(alpha = 0.4f + index * 0.08f),
                                        topLeft = androidx.compose.ui.geometry.Offset(center.x - width / 2f, center.y - height / 2f),
                                        size = androidx.compose.ui.geometry.Size(width, height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx())
                                    )
                                }
                            }
                        }
                        else -> {
                            // Matter wave: a smooth travelling line.
                            val path = androidx.compose.ui.graphics.Path()
                            val amplitude = base * 0.65f
                            for (step in 0..80) {
                                val x = size.width * step / 80f
                                val normalized = step / 80f
                                val y = center.y + kotlin.math.sin(normalized * Math.PI * 3.0 + angleRadians).toFloat() * amplitude
                                if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(0xFF829F91), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.6.dp.toPx()))
                            drawCircle(Color(0xFFB5C9BF), 7.dp.toPx(), androidx.compose.ui.geometry.Offset(center.x, center.y))
                        }
                    }
                }
                Text("DÉCOUVERTE DU JOUR  •  " + (dayIndex + 1) + "/5", color = Color(0xFF718781), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(title, color = DeepBlue, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(description, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.weight(1f))
                Text(
                    "Cette expérience est facultative. Pas de notification : BOZER LAB t'attend quand tu as envie de le découvrir.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Button(
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF536F65)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Revenir à BOZER", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun BozerTerminalItem(
    title: String,
    glyph: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.clip(RoundedCornerShape(17.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .border(1.dp, Color(0xFFE4EBE7), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(glyph, color = Color(0xFF63877D), fontSize = 21.sp)
        Text(title, color = DeepBlue, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ThinkingDots() {
    val transition = rememberInfiniteTransition(label="dots")
    val alpha by transition.animateFloat(.25f,1f,
        infiniteRepeatable(tween(650),RepeatMode.Reverse),label="dotAlpha")
    Text(" ···", color=Color(0xFF8BA99B).copy(alpha=alpha), fontSize=19.sp)
}

@Composable
private fun SettingsSection(kicker: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Color.White.copy(alpha=.76f))
        .border(1.dp, Color.White, RoundedCornerShape(20.dp)).padding(12.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(kicker, color=Color(0xFF6F9585), fontSize=9.sp, letterSpacing=1.5.sp, fontWeight=FontWeight.Bold)
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
        Text("›", color=Color(0xFF6F9585), fontSize=23.sp)
    }
}


@Composable
private fun PermissionStatusLine(title: String, status: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = DeepBlue, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(
            status,
            color = if (status == "Autorisé") Color(0xFF5C806F) else Color(0xFF9A7953),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
