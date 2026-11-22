package ai.bozer.app

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

private val BozerInk = Color(0xFF202A2F)
private val BozerMuted = Color(0xFF7B898F)
private val BozerPearl = Color(0xFFF6F8F9)
private val BozerSilver = Color(0xFFE8EEF0)
private val BozerLine = Color(0xFFDCE4E7)
private val BozerAccent = Color(0xFF84969E)
private val BozerWhite = Color(0xFFFEFFFF)

/**
 * Final visual direction for the first public BOZER interface:
 * pearl-white glass, quiet silver details, one conversation surface and
 * visible shortcuts that prepare natural-language commands without faking
 * Android capabilities that are not connected yet.
 */
@Composable
internal fun BozerChatExperience(
    activity: MainActivity,
    repository: ChatRepository,
    modelManager: LocalModelManager,
    engine: LocalLlamaEngine,
    voiceOutput: BozerVoiceOutput
) {
    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(repository.history()) }
    }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var modelReady by remember { mutableStateOf(false) }
    var modelBusy by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf("Préparation du cerveau intégré…") }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var settingsOpen by remember { mutableStateOf(false) }
    var terminalOpen by remember { mutableStateOf(false) }
    var historyOpen by remember { mutableStateOf(false) }
    var historyQuery by remember { mutableStateOf("") }
    var conversationRecords by remember { mutableStateOf(repository.conversations()) }
    var secretaryOpen by remember { mutableStateOf(false) }
    var secretaryTasks by remember { mutableStateOf(repository.secretaryTasks()) }
    var memoryOpen by remember { mutableStateOf(false) }
    var memoryFacts by remember { mutableStateOf(repository.rememberedFacts()) }
    var actionRecords by remember { mutableStateOf(repository.actionHistory()) }
    var scheduledAlarms by remember { mutableStateOf(BozerAlarmStore.all(activity)) }
    var editingFact by remember { mutableStateOf<String?>(null) }
    var editedFact by remember { mutableStateOf("") }
    var confirmClearHistory by remember { mutableStateOf(false) }
    var confirmClearMemory by remember { mutableStateOf(false) }
    var pendingPhoneAction by remember { mutableStateOf<PhoneAction?>(null) }
    var plusMenuOpen by remember { mutableStateOf(false) }
    val preferences = remember { activity.getPreferences(Context.MODE_PRIVATE) }
    var voiceEnabled by remember { mutableStateOf(preferences.getBoolean("voice_enabled", true)) }
    var speakingMessageCreatedAt by remember { mutableStateOf<Long?>(null) }
    var speechStart by remember { mutableIntStateOf(0) }
    var speechLength by remember { mutableIntStateOf(0) }
    var floatingBubbleEnabled by remember {
        mutableStateOf(
            preferences.getBoolean("floating_bubble_enabled", false) &&
                activity.hasFloatingBubblePermission()
        )
    }
    var brainMode by remember {
        mutableStateOf(BrainMode.from(preferences.getString("brain_mode_v2", repository.brainMode.name)))
    }

    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val orchestrator = remember(repository) { BozerOrchestrator(repository) }
    var activeTaskPlan by remember { mutableStateOf<BozerTaskPlan?>(null) }

    LaunchedEffect(brainMode) {
        repository.brainMode = brainMode
        preferences.edit().putString("brain_mode_v2", brainMode.name).apply()
    }

    LaunchedEffect(settingsOpen) {
        if (settingsOpen) scheduledAlarms = BozerAlarmStore.all(activity)
    }

    fun prepareCommand(command: String) {
        draft = if (draft.isBlank()) command else draft.trimEnd() + " " + command
    }

    fun installAndLoad() {
        if (modelBusy || modelReady) return
        modelBusy = true
        modelStatus = "Préparation du cerveau intégré à l'APK…"
        scope.launch {
            try {
                var lastReportedPercent = -1
                val file = modelManager.installBundled { copied, total ->
                    val percent = if (total > 0L) ((copied * 100L) / total).toInt() else 0
                    if (percent != lastReportedPercent || copied == total) {
                        lastReportedPercent = percent
                        withContext(Dispatchers.Main) {
                            downloadProgress = if (total > 0L) copied.toFloat() / total else 0f
                            val currentMb = copied / (1024L * 1024L)
                            val totalMb = total / (1024L * 1024L)
                            modelStatus = "Préparation locale : " + currentMb + " / " + totalMb + " Mo"
                        }
                    }
                }
                modelStatus = "Cerveau intégré copié. Démarrage du moteur…"
                withContext(Dispatchers.IO) { engine.load(file.absolutePath) }
                modelReady = true
                modelStatus = "Cerveau BOZER prêt — fonctionnement local."
            } catch (error: Exception) {
                modelStatus = "Le cerveau intégré n'a pas démarré : " +
                    (error.message ?: "erreur inconnue")
            } finally {
                modelBusy = false
            }
        }
    }

    LaunchedEffect(Unit) {
        installAndLoad()
    }

    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        draft = ""
        val conversationIdSnapshot = repository.currentConversationId()
        val userMessage = ChatMessage(role = "user", content = text)
        messages.add(userMessage)
        repository.save(userMessage, conversationIdSnapshot)

        // Snapshot both the conversation and its messages on the UI thread.
        // A later thread switch must never receive this request's reply.
        val historySnapshot = messages.toList()
        val intent = orchestrator.classify(text)
        activeTaskPlan = orchestrator.taskPlanner.plan(intent)
        if (intent is BozerIntent.AndroidAction) {
            pendingPhoneAction = intent.action
            return
        }

        sending = true
        scope.launch {
            try {
                val answer = withContext(Dispatchers.IO) { orchestrator.respond(historySnapshot, intent) }
                val assistantMessage = ChatMessage(role = "assistant", content = answer)
                val saved = repository.save(assistantMessage, conversationIdSnapshot)
                val stillShowingOriginalThread =
                    repository.currentConversationId() == conversationIdSnapshot
                if (stillShowingOriginalThread) {
                    messages.add(assistantMessage)
                    if (!saved) {
                        // The answer remains visible; history diagnostics can report
                        // that it could not be persisted.
                    }
                    if (voiceEnabled) {
                        val speechMessageId = assistantMessage.createdAt
                        speechStart = 0
                        speechLength = 0
                        val accepted = voiceOutput.speak(
                            answer,
                            onStart = { speakingMessageCreatedAt = speechMessageId },
                            onRange = { start, length ->
                                if (speakingMessageCreatedAt == speechMessageId) {
                                    speechStart = start
                                    speechLength = length
                                }
                            },
                            onDone = {
                                if (speakingMessageCreatedAt == speechMessageId) {
                                    speakingMessageCreatedAt = null
                                    speechStart = 0
                                    speechLength = 0
                                }
                            },
                            onError = {
                                if (speakingMessageCreatedAt == speechMessageId) {
                                    speakingMessageCreatedAt = null
                                    speechStart = 0
                                    speechLength = 0
                                }
                            }
                        )
                        if (!accepted) speakingMessageCreatedAt = null
                    }
                } else {
                    conversationRecords = repository.conversations(historyQuery)
                }
                activeTaskPlan = null
            } catch (error: Exception) {
                activeTaskPlan = null
                val failure = ChatMessage(
                    role = "assistant",
                    content = "Je n'ai pas pu terminer cette réponse : " +
                        (error.message ?: "une erreur inattendue est survenue") +
                        ". Tu peux réessayer."
                )
                if (repository.currentConversationId() == conversationIdSnapshot) {
                    messages.add(failure)
                }
                try {
                    repository.save(failure, conversationIdSnapshot)
                } catch (_: Exception) {
                    // The original conversation may have been deleted while processing.
                }
            } finally {
                sending = false
                if (messages.isNotEmpty() &&
                    repository.currentConversationId() == conversationIdSnapshot
                ) {
                    listState.animateScrollToItem(messages.lastIndex)
                }
            }
        }
    }

    fun cancelPendingPhoneAction() {
        if (pendingPhoneAction != null) {
            val cancelled = pendingPhoneAction!!
            repository.recordAction(
                ActionLogEntry(
                    actionType = PhoneActionExecutor.actionType(cancelled),
                    summary = PhoneActionExecutor.actionSummary(cancelled),
                    status = "ANNULÉE",
                    detail = "Confirmation refusée. Aucune action Android n'a été lancée."
                )
            )
            actionRecords = repository.actionHistory()
            val status = ChatMessage(role = "assistant", content = "Action annulée. Rien n'a été lancé.")
            messages.add(status)
            repository.save(status)
            pendingPhoneAction = null
            activeTaskPlan = null
        }
    }

    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = BozerAccent,
            onPrimary = Color.White,
            background = BozerPearl,
            surface = BozerWhite,
            onSurface = BozerInk
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = BozerPearl) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFFFFF), Color(0xFFF7F9FA), Color(0xFFF0F4F5))
                    )
                )
            ) {
                Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                    BozerTopBar(
                        showBrand = messages.isEmpty(),
                        voiceEnabled = voiceEnabled,
                        onSettings = { settingsOpen = true },
                        onTerminal = { terminalOpen = true },
                        onHistory = {
                            historyQuery = ""
                            conversationRecords = repository.conversations()
                            historyOpen = true
                        },
                        onNewConversation = {
                            repository.createConversation()
                            messages.clear()
                            draft = ""
                            historyOpen = false
                            speakingMessageCreatedAt = null
                            voiceOutput.stop()
                        },
                        onVoiceToggle = {
                            voiceEnabled = !voiceEnabled
                            preferences.edit().putBoolean("voice_enabled", voiceEnabled).apply()
                            if (!voiceEnabled) voiceOutput.stop()
                        }
                    )

                    if (modelBusy || !modelReady) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp, bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                modelStatus,
                                color = BozerMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (modelBusy) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    color = BozerAccent,
                                    trackColor = BozerSilver
                                )
                            }
                        }
                    }

                    if (messages.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            BozerWelcome()
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            state = listState,
                            contentPadding = PaddingValues(top = 18.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(messages) { message ->
                                val isCurrentSpeech = message.role == "assistant" &&
                                    message.createdAt == speakingMessageCreatedAt
                                BozerMessage(
                                    message,
                                    highlightedStart = if (isCurrentSpeech) speechStart else null,
                                    highlightedLength = if (isCurrentSpeech) speechLength else 0,
                                    speaking = isCurrentSpeech
                                )
                            }
                            if (sending) {
                                item {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        BozerOrb(size = 28.dp, active = true)
                                        Spacer(Modifier.width(10.dp))
                                        Text("BOZER réfléchit…", color = BozerMuted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // A small independent gesture handle keeps the double-tap
                    // target at the bottom without intercepting text-field or mic taps.
                    Box(
                        modifier = Modifier.fillMaxWidth().height(15.dp).pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { terminalOpen = true })
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.width(30.dp).height(3.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD7E0E3))
                        )
                    }
                    BozerComposer(
                        draft = draft,
                        sending = sending,
                        onDraftChange = { draft = it },
                        onSend = { send() },
                        onMic = {
                            activity.startVoiceInput { spoken ->
                                draft = if (draft.isBlank()) spoken else draft.trimEnd() + " " + spoken
                            }
                        },
                        onPlus = { terminalOpen = true },
                        plusMenuOpen = false,
                        onDismissPlus = { terminalOpen = false },
                        onNewConversation = {
                            terminalOpen = false
                            confirmClearHistory = true
                        },
                        onMemory = {
                            terminalOpen = false
                            memoryFacts = repository.rememberedFacts()
                            memoryOpen = true
                        },
                        onSearch = {
                            terminalOpen = false
                            prepareCommand("Recherche sur Internet : ")
                        },
                        onPhone = {
                            terminalOpen = false
                            prepareCommand("Aide-moi à contrôler mon téléphone : ")
                        },
                        onAlarm = {
                            terminalOpen = false
                            prepareCommand("Crée un réveil à ")
                        }
                    )
                }
            }
        }

        if (terminalOpen) {
            BozerTerminalSheet(
                onDismiss = { terminalOpen = false },
                onCommand = { command ->
                    draft = command
                    terminalOpen = false
                },
                onSecretary = {
                    terminalOpen = false
                    secretaryTasks = repository.secretaryTasks()
                    secretaryOpen = true
                },
                onMemory = {
                    terminalOpen = false
                    memoryFacts = repository.rememberedFacts()
                    memoryOpen = true
                }
            )
        }

        if (historyOpen) {
            BozerConversationHistoryScreen(
                conversations = conversationRecords,
                onSearch = { query ->
                    historyQuery = query
                    conversationRecords = repository.conversations(query)
                },
                onOpen = { conversation ->
                    if (repository.switchConversation(conversation.id)) {
                        messages.clear()
                        messages.addAll(repository.history())
                        draft = ""
                        historyOpen = false
                        speakingMessageCreatedAt = null
                        voiceOutput.stop()
                        if (messages.isNotEmpty()) {
                            scope.launch { listState.animateScrollToItem(messages.lastIndex) }
                        }
                    }
                },
                onDelete = { conversation ->
                    val wasActive = repository.currentConversationId() == conversation.id
                    if (repository.deleteConversation(conversation.id)) {
                        conversationRecords = repository.conversations(historyQuery)
                        if (wasActive) {
                            messages.clear()
                            messages.addAll(repository.history())
                            draft = ""
                            speakingMessageCreatedAt = null
                            voiceOutput.stop()
                        }
                    }
                },
                onNewConversation = {
                    repository.createConversation()
                    messages.clear()
                    draft = ""
                    historyOpen = false
                    speakingMessageCreatedAt = null
                    voiceOutput.stop()
                },
                onClose = { historyOpen = false }
            )
        }

        if (secretaryOpen) {
            BozerSecretaryScreen(
                tasks = secretaryTasks,
                onAdd = { title ->
                    val id = repository.addSecretaryTask(title)
                    if (id > 0L) secretaryTasks = repository.secretaryTasks()
                },
                onToggle = { task, completed ->
                    repository.setSecretaryTaskCompleted(task.id, completed)
                    secretaryTasks = repository.secretaryTasks()
                },
                onDelete = { task ->
                    repository.deleteSecretaryTask(task.id)
                    secretaryTasks = repository.secretaryTasks()
                },
                onClose = { secretaryOpen = false }
            )
        }

        if (settingsOpen) {
            BozerSettingsScreen(
                brainMode = brainMode,
                onBrainModeChange = { brainMode = it },
                remoteConfigured = repository.remoteConfigured,
                modelStatus = modelStatus,
                modelBusy = modelBusy,
                modelReady = modelReady,
                modelProgress = downloadProgress,
                onRetryModel = { installAndLoad() },
                voiceEnabled = voiceEnabled,
                onVoiceEnabledChange = { enabled ->
                    voiceEnabled = enabled
                    preferences.edit().putBoolean("voice_enabled", enabled).apply()
                    if (!enabled) voiceOutput.stop()
                },
                ttsAvailable = voiceOutput.isReady(),
                microphoneGranted = activity.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED,
                exactAlarmsAllowed = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
                    activity.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms(),
                notificationsAllowed = activity.getSystemService(android.app.NotificationManager::class.java)
                    .areNotificationsEnabled(),
                floatingBubbleEnabled = floatingBubbleEnabled,
                overlayPermissionGranted = activity.hasFloatingBubblePermission(),
                onFloatingBubbleChange = { enabled ->
                    activity.setFloatingBubbleEnabled(enabled) { active ->
                        floatingBubbleEnabled = active
                    }
                },
                rememberedCount = repository.rememberedFacts().size,
                actionRecords = actionRecords,
                scheduledAlarms = scheduledAlarms,
                onCancelAlarm = { alarm ->
                    BozerAlarmScheduler.cancel(activity, alarm)
                    scheduledAlarms = BozerAlarmStore.all(activity)
                },
                onManageMemory = {
                    memoryFacts = repository.rememberedFacts()
                    settingsOpen = false
                    memoryOpen = true
                },
                onClearHistory = {
                    settingsOpen = false
                    confirmClearHistory = true
                },
                onClearMemory = {
                    settingsOpen = false
                    confirmClearMemory = true
                },
                onClearActionHistory = {
                    repository.clearActionHistory()
                    actionRecords = emptyList()
                },
                onClose = { settingsOpen = false }
            )
        }

        if (memoryOpen) {
            AlertDialog(
                onDismissRequest = { memoryOpen = false },
                containerColor = BozerWhite,
                title = { Text("Mémoire de BOZER", color = BozerInk, fontWeight = FontWeight.SemiBold) },
                text = {
                    if (memoryFacts.isEmpty()) {
                        Text("Aucun souvenir enregistré pour le moment.", color = BozerMuted)
                    } else {
                        Column(
                            Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            memoryFacts.forEach { fact ->
                                Column {
                                    Text(fact, color = BozerInk, fontSize = 13.sp, lineHeight = 18.sp)
                                    Row {
                                        TextButton(onClick = {
                                            editingFact = fact
                                            editedFact = fact
                                            memoryOpen = false
                                        }) { Text("Modifier", color = BozerAccent) }
                                        TextButton(onClick = {
                                            repository.forgetRememberedFact(fact)
                                            memoryFacts = repository.rememberedFacts()
                                        }) { Text("Oublier", color = Color(0xFF8A5555)) }
                                    }
                                }
                                Divider(color = BozerLine)
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "Export mémoire BOZER",
                                    repository.exportMemoryJson()
                                )
                            )
                            android.widget.Toast.makeText(
                                activity,
                                "Export JSON copié dans le presse-papiers.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }) { Text("Exporter JSON", color = BozerAccent) }
                        TextButton(onClick = { memoryOpen = false }) { Text("Fermer", color = BozerInk) }
                    }
                }
            )
        }

        if (editingFact != null) {
            AlertDialog(
                onDismissRequest = { editingFact = null },
                containerColor = BozerWhite,
                title = { Text("Modifier un souvenir", color = BozerInk, fontWeight = FontWeight.SemiBold) },
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
                    ) { Text("Enregistrer", color = BozerInk) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        editingFact = null
                        memoryOpen = true
                    }) { Text("Annuler", color = BozerMuted) }
                }
            )
        }

        if (confirmClearHistory) {
            AlertDialog(
                onDismissRequest = { confirmClearHistory = false },
                containerColor = BozerWhite,
                title = { Text("Effacer l'historique ?", color = BozerInk) },
                text = {
                    Text(
                        "Les messages de cette conversation seront supprimés. Les souvenirs explicites seront conservés.",
                        color = BozerMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        repository.clearHistory()
                        messages.clear()
                        confirmClearHistory = false
                    }) { Text("Effacer", color = Color(0xFF8A5555)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearHistory = false }) { Text("Annuler", color = BozerInk) }
                }
            )
        }

        if (confirmClearMemory) {
            AlertDialog(
                onDismissRequest = { confirmClearMemory = false },
                containerColor = BozerWhite,
                title = { Text("Effacer la mémoire ?", color = BozerInk) },
                text = {
                    Text(
                        "Tous les souvenirs explicitement enregistrés sur ce téléphone seront supprimés. L'historique restera intact.",
                        color = BozerMuted
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        repository.clearMemory()
                        memoryFacts = emptyList()
                        confirmClearMemory = false
                    }) { Text("Effacer", color = Color(0xFF8A5555)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmClearMemory = false }) { Text("Annuler", color = BozerInk) }
                }
            )
        }

        pendingPhoneAction?.let { action ->
            AlertDialog(
                onDismissRequest = { cancelPendingPhoneAction() },
                containerColor = BozerWhite,
                title = { Text(PhoneActionPlanner.confirmationTitle(action), color = BozerInk) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(PhoneActionPlanner.confirmationDescription(action), color = BozerMuted)
                        activeTaskPlan?.steps?.forEach { step ->
                            Text(
                                "${step.order}. ${step.label}",
                                color = BozerMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val result = PhoneActionExecutor.execute(activity, action, confirmed = true)
                        repository.recordAction(
                            ActionLogEntry(
                                actionType = PhoneActionExecutor.actionType(action),
                                summary = PhoneActionExecutor.actionSummary(action),
                                status = PhoneActionExecutor.resultStatus(action, result),
                                detail = result.message
                            )
                        )
                        actionRecords = repository.actionHistory()
                        if (action is PhoneAction.SetAlarm && result.launched) {
                            scheduledAlarms = BozerAlarmStore.all(activity)
                        }
                        val status = ChatMessage(role = "assistant", content = result.message)
                        messages.add(status)
                        repository.save(status)
                        if (!result.retryable) {
                            pendingPhoneAction = null
                            activeTaskPlan = null
                        }
                    }) { Text("Confirmer / réessayer", color = BozerInk) }
                },
                dismissButton = {
                    TextButton(onClick = { cancelPendingPhoneAction() }) { Text("Annuler", color = BozerMuted) }
                }
            )
        }
    }
}

@Composable
private fun BozerTopBar(
    showBrand: Boolean,
    voiceEnabled: Boolean,
    onSettings: () -> Unit,
    onTerminal: () -> Unit,
    onHistory: () -> Unit,
    onNewConversation: () -> Unit,
    onVoiceToggle: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 11.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            BozerRoundButton(
                kind = "menu",
                onClick = { menuOpen = true },
                modifier = Modifier.testTag("bozer_settings")
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Historique de cette conversation") },
                    onClick = { menuOpen = false; onHistory() }
                )
                DropdownMenuItem(
                    text = { Text("Terminal BOZER") },
                    onClick = { menuOpen = false; onTerminal() }
                )
                DropdownMenuItem(
                    text = { Text("Nouvelle conversation") },
                    onClick = { menuOpen = false; onNewConversation() }
                )
                DropdownMenuItem(
                    text = { Text("Réglages") },
                    onClick = { menuOpen = false; onSettings() }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (showBrand) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BozerOrb(size = 23.dp, active = false)
                Text("BOZER", color = BozerInk, fontSize = 17.sp, letterSpacing = 3.1.sp, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Text("Conversation", color = BozerMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        BozerRoundButton(
            kind = if (voiceEnabled) "sound" else "mute",
            active = voiceEnabled,
            onClick = onVoiceToggle
        )
    }
}

@Composable
private fun BozerWelcome() {
    Column(
        modifier = Modifier.fillMaxSize().padding(bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        BozerOrb(size = 132.dp, active = false)
        Spacer(Modifier.height(17.dp))
        Text(
            "Je suis là.",
            modifier = Modifier.testTag("bozer_welcome"),
            color = BozerInk,
            fontSize = 29.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Dis-moi ce que tu veux faire.",
            color = BozerMuted,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Chaque intention peut devenir une action.",
            color = Color(0xFFA1ADB1),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun BozerMessage(
    message: ChatMessage,
    highlightedStart: Int? = null,
    highlightedLength: Int = 0,
    speaking: Boolean = false
) {
    val isUser = message.role == "user"
    val styledMessage = remember(message.content, highlightedStart, highlightedLength) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(message.content)
            val start = highlightedStart ?: return@buildAnnotatedString
            val end = (start + highlightedLength).coerceAtMost(message.content.length)
            if (start >= 0 && end > start) {
                addStyle(
                    androidx.compose.ui.text.SpanStyle(
                        background = Color(0xFFE6ECEF),
                        fontWeight = FontWeight.Medium
                    ),
                    start,
                    end
                )
            }
        }
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(230)) +
            slideInVertically(
                initialOffsetY = { it / 10 },
                animationSpec = androidx.compose.animation.core.tween(230)
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isUser) {
                BozerOrb(size = 25.dp, active = speaking)
                Spacer(Modifier.width(10.dp))
            }
            Box(
                modifier = Modifier.widthIn(max = 318.dp)
                    .then(
                        if (isUser) Modifier
                            .background(Color(0xFFE9EEF0).copy(alpha = 0.92f), RoundedCornerShape(22.dp))
                            .border(1.dp, Color(0xFFDDE5E8), RoundedCornerShape(22.dp))
                        else Modifier
                    )
                    .padding(horizontal = if (isUser) 15.dp else 0.dp, vertical = if (isUser) 12.dp else 2.dp)
            ) {
                Text(styledMessage, color = BozerInk, fontSize = 15.sp, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun BozerComposer(
    draft: String,
    sending: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    onPlus: () -> Unit,
    plusMenuOpen: Boolean,
    onDismissPlus: () -> Unit,
    onNewConversation: () -> Unit,
    onMemory: () -> Unit,
    onSearch: () -> Unit,
    onPhone: () -> Unit,
    onAlarm: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 13.dp)
            .shadow(12.dp, RoundedCornerShape(29.dp))
            .background(
                Brush.linearGradient(listOf(Color.White, Color(0xFFF9FBFC), Color(0xFFF0F4F5))),
                RoundedCornerShape(29.dp)
            )
            .border(1.dp, Color(0xFFDDE5E8), RoundedCornerShape(29.dp))
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            BozerRoundButton(kind = "plus", onClick = onPlus, compact = true)
            DropdownMenu(expanded = plusMenuOpen, onDismissRequest = onDismissPlus) {
                DropdownMenuItem(text = { Text("Nouvelle conversation") }, onClick = onNewConversation)
                DropdownMenuItem(text = { Text("Mémoire") }, onClick = onMemory)
                DropdownMenuItem(text = { Text("Recherche Internet") }, onClick = onSearch)
                DropdownMenuItem(text = { Text("Contrôler le téléphone") }, onClick = onPhone)
                DropdownMenuItem(text = { Text("Créer un réveil") }, onClick = onAlarm)
            }
        }
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f).padding(horizontal = 9.dp, vertical = 9.dp).testTag("bozer_input"),
            textStyle = TextStyle(color = BozerInk, fontSize = 15.sp, lineHeight = 20.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(BozerAccent),
            decorationBox = { inner ->
                if (draft.isEmpty()) Text("Écris ou parle à BOZER…", color = Color(0xFF9AA6AB), fontSize = 14.sp)
                inner()
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!sending) onSend() }),
            maxLines = 4
        )
        BozerRoundButton(kind = "mic", onClick = onMic, compact = true, modifier = Modifier.testTag("bozer_mic"))
        Spacer(Modifier.width(5.dp))
        Box(
            modifier = Modifier.size(43.dp)
                .shadow(if (draft.isNotBlank() && !sending) 5.dp else 0.dp, CircleShape)
                .background(
                    if (draft.isNotBlank() && !sending) Brush.linearGradient(listOf(Color(0xFF5E7078), Color(0xFF34434A)))
                    else Brush.linearGradient(listOf(Color(0xFFE9EEF0), Color(0xFFE3E9EB))),
                    CircleShape
                )
                .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .clickable(enabled = draft.isNotBlank() && !sending, onClick = onSend)
                .testTag("bozer_send"),
            contentAlignment = Alignment.Center
        ) {
            QuickActionGlyph("send", if (draft.isNotBlank() && !sending) Color.White else Color(0xFF9BA8AD))
        }
    }
}

@Composable
private fun BozerRoundButton(
    kind: String,
    active: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonSize = if (compact) 39.dp else 43.dp
    Box(
        modifier = modifier.size(buttonSize)
            .shadow(if (active) 3.dp else 2.dp, CircleShape)
            .background(Color.White.copy(alpha = 0.84f), CircleShape)
            .border(1.dp, Color(0xFFE1E8EA), CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        QuickActionGlyph(kind, if (active) Color(0xFF53666E) else Color(0xFF7C8B91))
    }
}

@Composable
private fun BozerOrb(size: androidx.compose.ui.unit.Dp, active: Boolean) {
    // Only the live "thinking" orb animates. Message/header orbs remain static
    // so a long conversation does not keep dozens of perpetual animations alive.
    val phase = if (active) {
        val transition = rememberInfiniteTransition(label = "bozer-pearl-orb")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "orb-drift"
        ).value
    } else {
        0f
    }
    Canvas(Modifier.size(size)) {
        val side = this.size.minDimension
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val pulse = sin(phase * PI.toFloat())
        val radius = side * (0.43f + pulse * 0.012f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFE8EEF0), Color(0xFFC7D2D6).copy(alpha = 0.56f), Color.Transparent),
                center = center,
                radius = side * 0.54f
            ),
            radius = side * 0.52f,
            center = center
        )
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color.White, Color(0xFFE2E9EC), Color(0xFFF9FBFC)),
                start = Offset(side * 0.20f, side * 0.12f),
                end = Offset(side * 0.78f, side * 0.86f)
            ),
            radius = radius,
            center = center
        )
        drawCircle(Color.White.copy(alpha = 0.94f), radius, center, style = Stroke(width = side * 0.009f))
        drawCircle(Color(0xFFB7C4C9).copy(alpha = 0.48f), radius * 0.74f, center, style = Stroke(width = side * 0.006f))
        drawArc(
            color = Color.White.copy(alpha = 0.88f),
            startAngle = 205f + phase * 34f,
            sweepAngle = 116f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.88f),
            size = Size(radius * 1.76f, radius * 1.76f),
            style = Stroke(width = side * 0.018f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawCircle(Color.White.copy(alpha = 0.9f), side * 0.018f, Offset(center.x + side * 0.20f, center.y - side * 0.20f))
    }
}

@Composable
private fun QuickActionGlyph(kind: String, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val line = 1.65.dp.toPx()
        when (kind) {
            "settings" -> {
                drawLine(color, Offset(w * 0.18f, h * 0.27f), Offset(w * 0.82f, h * 0.27f), line)
                drawLine(color, Offset(w * 0.18f, h * 0.50f), Offset(w * 0.72f, h * 0.50f), line)
                drawLine(color, Offset(w * 0.18f, h * 0.73f), Offset(w * 0.82f, h * 0.73f), line)
                drawCircle(color, w * 0.07f, Offset(w * 0.40f, h * 0.27f))
                drawCircle(color, w * 0.07f, Offset(w * 0.63f, h * 0.50f))
                drawCircle(color, w * 0.07f, Offset(w * 0.34f, h * 0.73f))
            }
            "plus" -> {
                drawLine(color, Offset(w * 0.50f, h * 0.16f), Offset(w * 0.50f, h * 0.84f), line)
                drawLine(color, Offset(w * 0.16f, h * 0.50f), Offset(w * 0.84f, h * 0.50f), line)
            }
            "mic" -> {
                drawRoundRect(color, Offset(w * 0.37f, h * 0.10f), Size(w * 0.26f, h * 0.48f), CornerRadius(w * 0.13f, h * 0.13f))
                drawArc(color, 0f, 180f, false, Offset(w * 0.21f, h * 0.30f), Size(w * 0.58f, h * 0.52f), style = Stroke(line))
                drawLine(color, Offset(w * 0.50f, h * 0.80f), Offset(w * 0.50f, h * 0.93f), line)
                drawLine(color, Offset(w * 0.34f, h * 0.93f), Offset(w * 0.66f, h * 0.93f), line)
            }
            "sound", "mute" -> {
                val path = Path().apply {
                    moveTo(w * 0.14f, h * 0.40f)
                    lineTo(w * 0.32f, h * 0.40f)
                    lineTo(w * 0.57f, h * 0.20f)
                    lineTo(w * 0.57f, h * 0.80f)
                    lineTo(w * 0.32f, h * 0.60f)
                    lineTo(w * 0.14f, h * 0.60f)
                    close()
                }
                drawPath(path, color)
                if (kind == "sound") {
                    drawArc(color, -55f, 110f, false, Offset(w * 0.42f, h * 0.23f), Size(w * 0.46f, h * 0.54f), style = Stroke(line))
                } else {
                    drawLine(color, Offset(w * 0.66f, h * 0.36f), Offset(w * 0.88f, h * 0.64f), line)
                    drawLine(color, Offset(w * 0.88f, h * 0.36f), Offset(w * 0.66f, h * 0.64f), line)
                }
            }
            "phone", "call" -> {
                val path = Path().apply {
                    moveTo(w * 0.25f, h * 0.12f)
                    quadraticTo(w * 0.12f, h * 0.17f, w * 0.16f, h * 0.37f)
                    quadraticTo(w * 0.27f, h * 0.72f, w * 0.64f, h * 0.84f)
                    quadraticTo(w * 0.84f, h * 0.88f, w * 0.88f, h * 0.72f)
                    lineTo(w * 0.70f, h * 0.57f)
                    lineTo(w * 0.57f, h * 0.67f)
                    quadraticTo(w * 0.39f, h * 0.57f, w * 0.32f, h * 0.40f)
                    lineTo(w * 0.42f, h * 0.27f)
                    close()
                }
                drawPath(path, color)
            }
            "apps" -> {
                val cell = w * 0.28f
                listOf(Offset(w * 0.15f, h * 0.15f), Offset(w * 0.57f, h * 0.15f), Offset(w * 0.15f, h * 0.57f), Offset(w * 0.57f, h * 0.57f)).forEach {
                    drawRoundRect(color, it, Size(cell, cell), CornerRadius(w * 0.06f, w * 0.06f), style = Stroke(line))
                }
            }
            "message" -> {
                drawRoundRect(color, Offset(w * 0.10f, h * 0.14f), Size(w * 0.80f, h * 0.62f), CornerRadius(w * 0.12f, h * 0.12f), style = Stroke(line))
                drawLine(color, Offset(w * 0.28f, h * 0.38f), Offset(w * 0.72f, h * 0.38f), line)
                drawLine(color, Offset(w * 0.28f, h * 0.54f), Offset(w * 0.58f, h * 0.54f), line)
            }
            "alarm" -> {
                drawCircle(color, w * 0.34f, Offset(w * 0.50f, h * 0.54f), style = Stroke(line))
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.50f, h * 0.31f), line)
                drawLine(color, Offset(w * 0.50f, h * 0.54f), Offset(w * 0.67f, h * 0.62f), line)
                drawLine(color, Offset(w * 0.22f, h * 0.17f), Offset(w * 0.10f, h * 0.29f), line)
                drawLine(color, Offset(w * 0.78f, h * 0.17f), Offset(w * 0.90f, h * 0.29f), line)
            }
            "search" -> {
                drawCircle(color, w * 0.25f, Offset(w * 0.42f, h * 0.40f), style = Stroke(line))
                drawLine(color, Offset(w * 0.60f, h * 0.61f), Offset(w * 0.87f, h * 0.88f), line)
            }
            "send" -> {
                drawLine(color, Offset(w * 0.50f, h * 0.84f), Offset(w * 0.50f, h * 0.18f), line)
                drawLine(color, Offset(w * 0.22f, h * 0.46f), Offset(w * 0.50f, h * 0.18f), line)
                drawLine(color, Offset(w * 0.78f, h * 0.46f), Offset(w * 0.50f, h * 0.18f), line)
            }
        }
    }
}
