package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.os.VibrationEffect
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.data.db.AppDatabase
import com.example.data.db.MessageEntity
import com.example.data.db.MemoryEntity
import com.example.data.repository.LumiRepository
import com.example.ui.components.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.utils.AmbientSoundSynth
import com.example.viewmodel.LumiViewModel
import com.example.viewmodel.LumiViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: LumiViewModel
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechIntent: Intent? = null
    private var vibrator: Vibrator? = null

    // Safe permission request launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerSpeechListening()
        } else {
            Toast.makeText(
                this,
                "Se requiere permiso de grabación para hablar con Lumi mediante voz.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Init database, repo & viewModel
        val db = AppDatabase.getDatabase(this)
        val repository = LumiRepository(db.lumiDao())
        val factory = LumiViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, factory)[LumiViewModel::class.java]

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

        initSpeechRecognizer()

        setContent {
            MyApplicationTheme(darkTheme = viewModel.isDarkMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LumiAppMainScreen(viewModel = viewModel, onVoiceMicClick = {
                        checkMicPermissionAndStart()
                    }, onPlaySoundClick = {
                        playChime()
                    }, onHapticClick = {
                        triggerHaptic(50)
                    })
                }
            }
        }
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w("MainActivity", "Speech recognition is not available on this device.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("Speech", "Ready for speech")
                    viewModel.activateSpeechListening()
                    playChime()
                }

                override fun onBeginningOfSpeech() {
                    viewModel.currentEmotion = "listening"
                    triggerHaptic(30)
                }

                override fun onRmsChanged(rmsdB: Float) {
                    // Normalize sound input to a stable 0f..1.0f amplitude range
                    val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                    viewModel.amplitudeWave = normalized
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    viewModel.currentEmotion = "thinking"
                    viewModel.amplitudeWave = 0f
                }

                override fun onError(error: Int) {
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client generic error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permissions missing"
                        SpeechRecognizer.ERROR_NETWORK -> "Network failure"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech input timed out"
                        else -> "Speech recognizer error"
                    }
                    Log.e("Speech", "Speech error description: $message Code: $error")
                    viewModel.isLumiListening = false
                    viewModel.amplitudeWave = 0f
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        viewModel.currentEmotion = "sad"
                    } else {
                        viewModel.currentEmotion = "neutral"
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val bestMatch = matches?.firstOrNull()
                    if (bestMatch != null) {
                        Log.d("Speech", "Lumi recognized: $bestMatch")
                        viewModel.submitSpeechResult(bestMatch)
                        triggerHaptic(70)
                    } else {
                        viewModel.isLumiListening = false
                        viewModel.currentEmotion = "sad"
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    viewModel.sttText = partialText
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
    }

    private fun checkMicPermissionAndStart() {
        triggerHaptic(30)
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                triggerSpeechListening()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun triggerSpeechListening() {
        if (speechRecognizer == null) {
            // Simulated speech recognizer for fallback environments
            Toast.makeText(this, "Simulando micrófono de Lumi (Asistente de Voz)", Toast.LENGTH_SHORT).show()
            viewModel.isLumiListening = true
            viewModel.currentEmotion = "listening"
            CoroutineScope(Dispatchers.Main).launch {
                delay(4000)
                viewModel.isLumiListening = false
                viewModel.submitSpeechResult("¿Hola Lumi, qué opinas de la inteligencia artificial emocional desarrollada por LYNOR?")
            }
            return
        }

        try {
            speechRecognizer?.startListening(speechIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            viewModel.isLumiListening = false
        }
    }

    private fun playChime() {
        if (viewModel.soundEnabled) {
            CoroutineScope(Dispatchers.Default).launch {
                AmbientSoundSynth.playSoftBlip()
            }
        }
    }

    private fun triggerHaptic(ms: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(ms)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LumiAppMainScreen(
    viewModel: LumiViewModel,
    onVoiceMicClick: () -> Unit,
    onPlaySoundClick: () -> Unit,
    onHapticClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    
    // Track message counts for scroll to bottom
    val chatMessages by viewModel.messages.collectAsState()
    val userMemories by viewModel.memories.collectAsState()
    
    // UI Panels Toggle State (for multi-pane interactive components)
    var isChatViewActive by remember { mutableStateOf(false) }
    var isMemoryDialogActive by remember { mutableStateOf(false) }
    
    // Auto-scroll to bottom of chat when messages count updates
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    LumiBackground(isDarkMode = viewModel.isDarkMode) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Minimalist futuristic top header bar
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(
                                text = "LYNOR",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(LumiGlowCyan, CircleShape)
                            )
                        }
                    },
                    actions = {
                        // Sound feedback toggler
                        IconButton(
                            onClick = {
                                onHapticClick()
                                viewModel.soundEnabled = !viewModel.soundEnabled
                                if (viewModel.soundEnabled) onPlaySoundClick()
                            },
                            modifier = Modifier.testTag("sound_toggle")
                        ) {
                            Icon(
                                imageVector = if (viewModel.soundEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff,
                                contentDescription = "Configurar Sonido",
                                tint = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
                            )
                        }

                        // Light/Dark mode toggler
                        IconButton(
                            onClick = {
                                onHapticClick()
                                viewModel.isDarkMode = !viewModel.isDarkMode
                            },
                            modifier = Modifier.testTag("theme_toggle")
                        ) {
                            Icon(
                                imageVector = if (viewModel.isDarkMode) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay,
                                contentDescription = "Cambiar Tema",
                                tint = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f)
                            )
                        }

                        // Memory storage panel toggler
                        IconButton(
                            onClick = {
                                onHapticClick()
                                isMemoryDialogActive = true
                            },
                            modifier = Modifier.testTag("memory_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Psychology,
                                contentDescription = "Núcleo de Memoria LYNOR",
                                tint = if (viewModel.isDarkMode) LumiViolet else Purple40
                            )
                        }

                        // Eraser / Reset chat history
                        IconButton(
                            onClick = {
                                onHapticClick()
                                viewModel.clearChat()
                            },
                            modifier = Modifier.testTag("clear_chat")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = "Limpiar Chat",
                                tint = Color.Red.copy(alpha = 0.65f)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                
                // MAIN AREA SWITCH (Home Floating Lumi vs Scrollable Chat History)
                AnimatedContent(
                    targetState = isChatViewActive,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                        slideOutVertically { height -> height } + fadeOut()
                    },
                    label = "main_screen_transition"
                ) { showChatOnly ->
                    if (showChatOnly) {
                        // CHAT VIEW PANEL
                        LumiChatArea(
                            messages = chatMessages,
                            listState = listState,
                            isDarkMode = viewModel.isDarkMode,
                            isGenerating = viewModel.isGenerating,
                            onCloseChat = {
                                onHapticClick()
                                isChatViewActive = false
                            }
                        )
                    } else {
                        // HOME VIEW PANEL (Floating companion centerpiece)
                        LumiHomeArea(
                            viewModel = viewModel,
                            onHapticClick = onHapticClick,
                            onMicClicked = {
                                viewModel.isVoiceModeActive = true
                                onVoiceMicClick()
                            },
                            onQuickChatClick = {
                                onHapticClick()
                                isChatViewActive = true
                            }
                        )
                    }
                }

                // IMMERSIVE VOICE MODE OVERLAY (Immersive full screen glass screen)
                if (viewModel.isVoiceModeActive) {
                    LumiVoiceModeOverlay(
                        viewModel = viewModel,
                        onClose = {
                            onHapticClick()
                            viewModel.isVoiceModeActive = false
                            viewModel.stopSpeaking()
                        },
                        onVoiceMicClick = {
                            onVoiceMicClick()
                        }
                    )
                }

                // MEMORY LOG CENTER MODAL DIALOG
                if (isMemoryDialogActive) {
                    LumiMemoryCenterDialog(
                        memories = userMemories,
                        isDarkMode = viewModel.isDarkMode,
                        onSaveMemory = { key, valStr ->
                            onHapticClick()
                            viewModel.saveCustomMemory(key, valStr)
                        },
                        onDeleteMemory = { k ->
                            onHapticClick()
                            viewModel.removeMemory(k)
                        },
                        onClose = {
                            onHapticClick()
                            isMemoryDialogActive = false
                        }
                    )
                }
            }
        }
    }
}

// ==========================================================
// VIEW: HOME AREA
// ==========================================================
@Composable
fun LumiHomeArea(
    viewModel: LumiViewModel,
    onHapticClick: () -> Unit,
    onMicClicked: () -> Unit,
    onQuickChatClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var textInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Adaptive assistant recommendations depending on active mode
    val modeSuggestions = when (viewModel.activeMode) {
        "TRANSLATE" -> listOf("Traduce: 'La inteligencia artificial nos une'", "¿Cómo se dice 'espacio digital' en alemán?", "Traduce 'crear mundos elegantes' al inglés")
        "SUMMARY" -> listOf("Resume: 'La simplicidad es la máxima sofisticación'", "Ayúdame a organizar un resumen laboral", "Sintetiza la misión de LYNOR")
        "IDEAS" -> listOf("Idea para un reloj inteligente futurista", "¿Qué proyecto innovador puedo programar hoy?", "Ideas de diseño minimalista Nothing OS")
        "CONCENTRATION" -> listOf("Ayúdame a enfocarme en programar", "Programemos un temporizador mental", "¿Cómo evito distracciones hoy?")
        "RELAX" -> listOf("Guíame en una respiración de 4 segundos", "Dime algo lindo para calmar la ansiedad", "Hagamos un ejercicio zen breve")
        else -> listOf("Háblame del ecosistema LYNOR", "Dame un consejo motivador para el día", "¿Cuál es tu diseño favorito?")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        
        // 1. Center Floating Space
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LumiAvatar(
                emotion = viewModel.currentEmotion,
                amplitude = viewModel.amplitudeWave,
                size = 220.dp
            )
            
            Spacer(modifier = Modifier.height(12.dp))

            // Premium Light font-sans headline greeting
            Text(
                text = "Hola, soy Lumi",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                color = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle status display
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (viewModel.isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                when (viewModel.currentEmotion) {
                                    "happy" -> LumiPink
                                    "sad" -> LumiViolet
                                    "thinking" -> LumiGlowCyan
                                    else -> LumiSkyBlue
                                }, CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when (viewModel.currentEmotion) {
                            "thinking" -> "Conectada • Pensando"
                            "happy" -> "Conectada • Alegre"
                            "sad" -> "Conectada • Pensativa"
                            "listening" -> "Conectada • Escuchando"
                            "speaking" -> "Conectada • Hablando"
                            else -> "Conectada • Sintonizada"
                        }.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.5.sp,
                        color = if (viewModel.isDarkMode) LumiSkyBlue.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Short emotional greeting/thought from past response
            val messagesList by viewModel.messages.collectAsState()
            val lastLumiMessage = messagesList.lastOrNull { it.sender == "lumi" }?.text ?: "Sintonizada y lista para crear. ¿Qué misterio desentrañamos juntos hoy?"
            Text(
                text = if (lastLumiMessage.length > 120) lastLumiMessage.take(120) + "..." else lastLumiMessage,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Light,
                color = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clickable { onQuickChatClick() } // Quick shortcut to full chat history
            )
        }

        // 2. SUGGESTION CARDS GRID
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "SUGERENCIAS INTELIGENTES CORE",
                style = MaterialTheme.typography.labelMedium,
                color = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.4f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                modeSuggestions.forEach { suggestion ->
                    GlassmorphicCard(
                        modifier = Modifier
                            .width(180.dp)
                            .height(82.dp)
                            .clickable {
                                onHapticClick()
                                viewModel.sendMessage(suggestion)
                            },
                        isDarkMode = viewModel.isDarkMode,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = suggestion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.85f),
                            maxLines = 3,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }
                }
            }
        }

        // 3. INTEGRATED LYNOR ASSISTANT MODES SELECTOR
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Pair("NORMAL", Icons.Rounded.AutoAwesome),
                    Pair("TRANSLATE", Icons.Rounded.Translate),
                    Pair("SUMMARY", Icons.Rounded.Assignment),
                    Pair("IDEAS", Icons.Rounded.Lightbulb),
                    Pair("CONCENTRATION", Icons.Rounded.SelfImprovement),
                    Pair("RELAX", Icons.Rounded.NaturePeople)
                ).forEach { (mode, icon) ->
                    val isActive = viewModel.activeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isActive) {
                                    if (viewModel.isDarkMode) LumiSkyBlue.copy(alpha = 0.15f) else Purple40.copy(alpha = 0.15f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) {
                                    if (viewModel.isDarkMode) LumiSkyBlue else Purple40
                                } else {
                                    if (viewModel.isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                onHapticClick()
                                if (mode == "CONCENTRATION" && viewModel.soundEnabled) {
                                    coroutineScope.launch { AmbientSoundSynth.playCosmicHum() }
                                } else {
                                    coroutineScope.launch { AmbientSoundSynth.playSoftBlip() }
                                }
                                viewModel.changeMode(mode)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = icon,
                                contentDescription = mode,
                                tint = if (isActive) {
                                    if (viewModel.isDarkMode) LumiGlowCyan else Purple40
                                } else {
                                    if (viewModel.isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = mode,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isActive) {
                                    if (viewModel.isDarkMode) Color.White else Color.Black
                                } else {
                                    if (viewModel.isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                                },
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // 4. FLOATING CHAT ENTRY & MICROPHONE PILL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            
            // Text Input Pill
            GlassmorphicCard(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                isDarkMode = viewModel.isDarkMode,
                shape = RoundedCornerShape(28.dp)
            ) {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    placeholder = {
                        Text(
                            text = "Escribe a Lumi...",
                            color = if (viewModel.isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp)
                        .testTag("chat_input_text"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = if (viewModel.isDarkMode) Color.White else Color.Black,
                        unfocusedTextColor = if (viewModel.isDarkMode) Color.White else Color.Black
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (textInput.trim().isNotEmpty()) {
                            onHapticClick()
                            viewModel.sendMessage(textInput)
                            textInput = ""
                            focusManager.clearFocus()
                            keyboardController?.hide()
                            onQuickChatClick() // instantly go to chat screen when sending
                        }
                    })
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Glowing Mic / Voice Button (Sophisticated Dark layout)
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clickable { onMicClicked() }
                    .testTag("voice_mode_trigger"),
                contentAlignment = Alignment.Center
            ) {
                // 1. Back blur glow layer (dynamic gradient background aura)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    LumiSkyBlue.copy(alpha = 0.40f),
                                    LumiViolet.copy(alpha = 0.40f),
                                    LumiPink.copy(alpha = 0.40f)
                                )
                            )
                        )
                )

                // 2. Translucent glass layer
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    // 3. Perfect white center core
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        // 4. Perfect dark microphone indicator capsule
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Modo Voz Lumi",
                            tint = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================
// VIEW: CHAT AREA DISPLAY LIST
// ==========================================================
@Composable
fun LumiChatArea(
    messages: List<MessageEntity>,
    listState: LazyListState,
    isDarkMode: Boolean,
    isGenerating: Boolean,
    onCloseChat: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            
            // Header chat toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onCloseChat) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Volver",
                        tint = if (isDarkMode) Color.White else Color.Black
                    )
                }

                Text(
                    text = "LUMI CONVERSACIÓN",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                )

                Box(modifier = Modifier.size(48.dp)) // Spacer placeholder
            }

            // Message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(messages) { message ->
                    LumiChatItemBubble(message = message, isDarkMode = isDarkMode)
                }

                if (isGenerating) {
                    item {
                        LumiTypingIndicator(isDarkMode = isDarkMode)
                    }
                }
            }
        }
    }
}

@Composable
fun LumiChatItemBubble(message: MessageEntity, isDarkMode: Boolean) {
    val isUser = message.sender == "user"
    val timestampStr = remember(message.timestamp) {
        val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
        formatter.format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            
            // Sender & Time label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp, end = 8.dp)
            ) {
                Text(
                    text = if (isUser) "TÚ" else "LUMI",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 9.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = timestampStr,
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 9.sp,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                when (message.emotion) {
                                    "happy" -> LumiPink
                                    "sad" -> LumiViolet
                                    "thinking" -> LumiGlowCyan
                                    else -> LumiSkyBlue
                                }, CircleShape
                            )
                    )
                }
            }

            // Chat bubble background with dynamic translucency
            GlassmorphicCard(
                modifier = Modifier.fillMaxWidth(),
                isDarkMode = isDarkMode,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = if (isUser) 20.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 20.dp
                ),
                borderWidth = if (isUser) 0.dp else 1.dp
            ) {
                // Background colored tint overlay for user messages to contrast them nicely
                if (isUser) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        LumiViolet.copy(alpha = 0.15f),
                                        LumiSkyBlue.copy(alpha = 0.08f)
                                    )
                                )
                            )
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDarkMode) Color.White else Color.Black,
                    modifier = Modifier.padding(2.dp)
                )
            }
        }
    }
}

@Composable
fun LumiTypingIndicator(isDarkMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Column(modifier = Modifier.widthIn(max = 200.dp)) {
            Text(
                text = "LUMI ANALIZANDO...",
                style = MaterialTheme.typography.labelMedium,
                fontSize = 9.sp,
                color = if (isDarkMode) Color.White.copy(alpha = 0.3f) else Color.Black.copy(alpha = 0.3f),
                modifier = Modifier.padding(bottom = 4.dp, start = 8.dp)
            )

            GlassmorphicCard(
                isDarkMode = isDarkMode,
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    // Small circular spinning indicator
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LumiSkyBlue
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Entonando idea...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkMode) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ==========================================================
// VIEW: IMMERSIVE VOICE MODE OVERLAY screen
// ==========================================================
@Composable
fun LumiVoiceModeOverlay(
    viewModel: LumiViewModel,
    onClose: () -> Unit,
    onVoiceMicClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f)) // heavily immersive black
            .clickable { onClose() } // Tap anywhere to dismiss
    ) {
        
        // Liquid glass backdrop shading
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            LumiViolet.copy(alpha = 0.12f),
                            LumiGlowCyan.copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 1200f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cerrar modo de voz",
                        tint = Color.White.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = "LYNOR VOZ SENSIBLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.4f)
                )

                Box(modifier = Modifier.size(48.dp))
            }

            // Real-time voice transcripts display
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = if (viewModel.isLumiListening) "ESCUCHANDO EXPRESIONES" else "LUMI HABLANDO",
                    style = MaterialTheme.typography.labelLarge,
                    color = LumiGlowCyan,
                    letterSpacing = 2.sp
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                // Transcribed user's speech, or Lumi's responding status
                val transcriptText = if (viewModel.isLumiListening) {
                    if (viewModel.sttText.isEmpty()) "di algo natural..." else "\"${viewModel.sttText}\""
                } else {
                    val messagesList by viewModel.messages.collectAsState()
                    val lastLumiText = messagesList.lastOrNull { it.sender == "lumi" }?.text ?: ""
                    if (lastLumiText.length > 90) lastLumiText.take(90) + "..." else lastLumiText
                }

                Text(
                    text = transcriptText,
                    style = MaterialTheme.typography.displayLarge,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Light,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }

            // Centralized Floating Lumi (Enlarged and highly responsive)
            LumiAvatar(
                emotion = viewModel.currentEmotion,
                amplitude = viewModel.amplitudeWave,
                size = 260.dp,
                modifier = Modifier.clickable(enabled = false) {} // Disable click block on avatar itself
            )

            // Dynamic Sine waves and bottom manual mic activator
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VoiceWaveVisualizer(
                    amplitude = viewModel.amplitudeWave,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                FloatingActionButton(
                    onClick = {
                        onVoiceMicClick()
                    },
                    containerColor = Color.White.copy(alpha = 0.1f),
                    contentColor = LumiGlowCyan,
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = if (viewModel.isLumiListening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic,
                        contentDescription = "Grabar de Nuevo",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Presiona para sintonizar o toca en cualquier fondo para salir de Voz",
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 9.sp,
                    color = Color.White.copy(alpha = 0.35f)
                )
            }
        }
    }
}

// ==========================================================
// VIEW: LYNOR INTELLIGENCE MEMORY STORAGE MODAL PANEL
// ==========================================================
@Composable
fun LumiMemoryCenterDialog(
    memories: List<MemoryEntity>,
    isDarkMode: Boolean,
    onSaveMemory: (String, String) -> Unit,
    onDeleteMemory: (String) -> Unit,
    onClose: () -> Unit
) {
    var customKey by remember { mutableStateOf("") }
    var customValue by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onClose() },
        contentAlignment = Alignment.Center
    ) {
        GlassmorphicCard(
            modifier = Modifier
                .width(320.dp)
                .padding(16.dp)
                .clickable(enabled = false) {}, // preventing instant dismiss click
            isDarkMode = isDarkMode
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                
                // Form header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MEMORIA INTELLIGENCE",
                        style = MaterialTheme.typography.labelLarge,
                        color = LumiSkyBlue
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cerrar memoria",
                            tint = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f)
                        )
                    }
                }

                Text(
                    text = "Lumi aprende tus rasgos y notas automáticamente, o puedes agregarlos de forma manual aquí.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDarkMode) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Render Memories List
                Divider(color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))
                
                Box(modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                ) {
                    if (memories.isEmpty()) {
                        Text(
                            text = "Memoria vacía. Di 'Mi nombre es Dióslit' o escribe para enseñarme.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(12.dp),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            items(memories) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDarkMode) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.key.uppercase(),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = LumiGlowCyan,
                                            fontSize = 9.sp
                                        )
                                        Text(
                                            text = item.value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDarkMode) Color.White else Color.Black
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteMemory(item.key) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Delete,
                                            contentDescription = "Borrar",
                                            tint = Color.Red.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Divider(color = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f))

                // Custom Input fields
                Spacer(modifier = Modifier.height(10.dp))
                
                OutlinedTextField(
                    value = customKey,
                    onValueChange = { customKey = it },
                    label = { Text("Clave (ej. edad, deporte)") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDarkMode) Color.White else Color.Black,
                        unfocusedTextColor = if (isDarkMode) Color.White else Color.Black
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = customValue,
                    onValueChange = { customValue = it },
                    label = { Text("Detalle (ej. 24 años, correr)") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = if (isDarkMode) Color.White else Color.Black,
                        unfocusedTextColor = if (isDarkMode) Color.White else Color.Black
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        if (customKey.trim().isNotEmpty() && customValue.trim().isNotEmpty()) {
                            onSaveMemory(customKey.trim().lowercase(), customValue.trim())
                            customKey = ""
                            customValue = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDarkMode) LumiSkyBlue else Purple40, contentColor = if (isDarkMode) LumiAMOLEDBlack else Color.White)
                ) {
                    Text("REGISTRAR EN EL NÚCLEO")
                }
            }
        }
    }
}
