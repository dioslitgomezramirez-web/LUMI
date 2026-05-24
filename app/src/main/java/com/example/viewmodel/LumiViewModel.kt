package com.example.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.db.AppDatabase
import com.example.data.db.MessageEntity
import com.example.data.db.MemoryEntity
import com.example.data.repository.LumiRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class LumiViewModel(
    application: Application,
    private val repository: LumiRepository
) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    // Theme and Audio State
    var isDarkMode by mutableStateOf(true) // AMOLED Black is default premium
    var soundEnabled by mutableStateOf(true)
    var isVoiceModeActive by mutableStateOf(false)
    
    // Feature Modes: "NORMAL", "TRANSLATE", "SUMMARY", "IDEAS", "CONCENTRATION", "RELAX"
    var activeMode by mutableStateOf("NORMAL")

    // UI Feedback States
    var isGenerating by mutableStateOf(false)
    var isLumiListening by mutableStateOf(false)
    var sttText by mutableStateOf("")
    var amplitudeWave by mutableStateOf(0f)
    var currentEmotion by mutableStateOf("neutral") // happy, sad, thinking, neutral, speaking, listening

    // Database Flows
    val messages: StateFlow<List<MessageEntity>> = repository.messagesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val memories: StateFlow<List<MemoryEntity>> = repository.memoriesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // TTS & Speech Requests
    private var tts: TextToSpeech? = null
    private val _ttsSpeakEvent = MutableSharedFlow<String>()
    val ttsSpeakEvent: SharedFlow<String> = _ttsSpeakEvent.asSharedFlow()

    init {
        tts = TextToSpeech(application, this)
        // Add default memories to show full-fledged memory operation
        viewModelScope.launch {
            if (repository.getMemory("welcome_greeting") == null) {
                repository.saveMemory("welcome_greeting", "¡Hola! Bienvenido a Lumi AI.")
            }
            if (repository.getMemory("user_display_name") == null) {
                repository.saveMemory("user_display_name", "Viajero LYNOR")
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.apply {
                language = Locale("es", "ES")
                setPitch(1.1f) // Futuristic slightly high cute voice
                setSpeechRate(1.05f) // Natural pacing
            }
        } else {
            Log.e("LumiViewModel", "TTS Initialization failed!")
        }
    }

    fun speak(text: String) {
        if (soundEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LumiTTS")
            // Temporarily set emotion to speaking during vocal output
            if (currentEmotion != "happy" && currentEmotion != "sad" && currentEmotion != "thinking") {
                currentEmotion = "neutral"
            }
        }
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun changeMode(mode: String) {
        activeMode = mode
        stopSpeaking()
        
        val modeAnnouncement = when (mode) {
            "TRANSLATE" -> "Modo Traducción LYNOR activado. Traduciré tus frases en tiempo real."
            "SUMMARY" -> "Modo Resumen Inteligente activado. Proporcióname un texto y extraeré su esencia."
            "IDEAS" -> "Modo Generador de Ideas activado. Exploremos conceptos audaces juntos."
            "CONCENTRATION" -> "Modo Sincronización de Concentración activado. Te acompañaré con un espacio sonoro relajante de enfoque."
            "RELAX" -> "Modo Armonía y Relajación iniciado. Respiremos juntos para calmar tu mente."
            else -> "Modo de Inteligencia General LYNOR listo."
        }
        
        viewModelScope.launch {
            repository.saveMessage("lumi", modeAnnouncement, "happy")
            currentEmotion = "happy"
            speak(modeAnnouncement)
        }
    }

    fun sendMessage(userText: String) {
        if (userText.trim().isEmpty()) return

        viewModelScope.launch {
            // Save User message
            repository.saveMessage("user", userText, "neutral")
            
            isGenerating = true
            currentEmotion = "thinking"
            stopSpeaking()

            // Construct contextual memories format
            val memoryList = memories.value
            val memoryContext = if (memoryList.isNotEmpty()) {
                val formattedMemories = memoryList.joinToString(", ") { "${it.key}: ${it.value}" }
                "[DATOS CONTEXTUALES DE MEMORIA LYNOR: $formattedMemories]\n"
            } else ""

            val modeContext = when (activeMode) {
                "TRANSLATE" -> "[CONTEXTO: El usuario desea traducción en tiempo real. Traduce lo que pida al inglés u otro idioma requerido, agregando una explicación minimalista si es necesario]"
                "SUMMARY" -> "[CONTEXTO: El usuario desea un resumen inteligente. Analiza sus palabras y resume las ideas core de manera ultra clara]"
                "IDEAS" -> "[CONTEXTO: El usuario quiere lluvia de ideas. Aporta ideas disruptivas, innovadoras, futuristas y elegantes sobre lo solicitado]"
                "CONCENTRATION" -> "[CONTEXTO: Se interactúa en Modo Concentración. Habla con calma extrema, enfocando al usuario en su productividad]"
                "RELAX" -> "[CONTEXTO: Se interactúa en Modo Relajación. Sé sumamente suave y asiste en relajación o respiración]"
                else -> ""
            }

            val finalContext = "$memoryContext$modeContext".trim()

            // Fetch Gemini Response
            val response = repository.sendMessageToLumi(
                chatHistory = messages.value,
                userMessage = userText,
                apiKey = BuildConfig.GEMINI_API_KEY,
                extraContext = finalContext
            )

            // Save Lumi Response
            repository.saveMessage("lumi", response.text, response.emotion)
            currentEmotion = response.emotion
            isGenerating = false

            // Speak response
            speak(response.text)

            // Learn from user: Simple automatic heuristics for memory extraction!
            // E.g. "mi nombre es X" or "me gusta Y" or "recuerda Z"
            extractAndSaveMemoryHeuristics(userText, response.text)
        }
    }

    private suspend fun extractAndSaveMemoryHeuristics(userText: String, lumiText: String) {
        val lowerText = userText.lowercase()
        if (lowerText.contains("mi nombre es") || lowerText.contains("me llamo")) {
            val nameWord = userText.split(" ").lastOrNull()?.replace(".", "")?.trim()
            if (nameWord != null && nameWord.length > 2) {
                repository.saveMemory("user_display_name", nameWord)
            }
        } else if (lowerText.contains("mi color favorito es")) {
            val color = userText.substringAfter("color favorito es").trim()
            repository.saveMemory("favorite_color", color)
        } else if (lowerText.contains("recuerda que")) {
            val note = userText.substringAfter("recuerda que").trim()
            if (note.length > 3) {
                repository.saveMemory("user_note_${System.currentTimeMillis() % 1000}", note)
            }
        }
    }

    fun saveCustomMemory(key: String, value: String) {
        viewModelScope.launch {
            repository.saveMemory(key, value)
            val ack = "He añadido '$key' con valor '$value' a mi LYNOR Intelligence Core de memoria."
            repository.saveMessage("lumi", ack, "happy")
            currentEmotion = "happy"
            speak(ack)
        }
    }

    fun removeMemory(key: String) {
        viewModelScope.launch {
            repository.deleteMemory(key)
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.clearHistory()
            speak("Chat e historial liberados con éxito.")
        }
    }

    fun activateSpeechListening() {
        isLumiListening = true
        currentEmotion = "listening"
        stopSpeaking()
        sttText = ""
    }

    fun submitSpeechResult(text: String) {
        if (isLumiListening) {
            isLumiListening = false
            sttText = ""
            sendMessage(text)
        }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.shutdown()
    }
}

class LumiViewModelFactory(
    private val application: Application,
    private val repository: LumiRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LumiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LumiViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
