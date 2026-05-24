package com.example.data.repository

import android.util.Log
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.db.LumiDao
import com.example.data.db.MessageEntity
import com.example.data.db.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LumiRepository(private val lumiDao: LumiDao) {

    val messagesFlow: Flow<List<MessageEntity>> = lumiDao.getAllMessagesFlow()
    val memoriesFlow: Flow<List<MemoryEntity>> = lumiDao.getAllMemoriesFlow()

    suspend fun saveMessage(sender: String, text: String, emotion: String): Long {
        return lumiDao.insertMessage(MessageEntity(sender = sender, text = text, emotion = emotion))
    }

    suspend fun clearHistory() {
        lumiDao.clearAllMessages()
    }

    suspend fun saveMemory(key: String, value: String) {
        lumiDao.insertMemory(MemoryEntity(key = key, value = value))
    }

    suspend fun deleteMemory(key: String) {
        lumiDao.deleteMemoryByKey(key)
    }

    suspend fun getMemory(key: String): String? {
        return lumiDao.getMemoryValue(key)
    }

    /**
     * Sends the chat prompt with history to the Gemini API and extracts the AI response.
     */
    suspend fun sendMessageToLumi(
        chatHistory: List<MessageEntity>,
        userMessage: String,
        apiKey: String,
        extraContext: String = ""
    ): LumiApiResponse = withContext(Dispatchers.IO) {
        if (apiKey.trim().isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext LumiApiResponse(
                text = "Me encantaría hablar contigo en tiempo real, pero el código de API de Gemini no está configurado. Por favor, asegúrate de guardarlo en tus Secretos de AI Studio para darme vida completa. De todas formas, aquí te acompaña mi simulación LYNOR Intelligence Core.",
                emotion = "neutral"
            )
        }

        val systemInstruction = """
            Eres Lumi, la mascota e inteligencia artificial oficial de LYNOR. Eres una compañera inteligente emocional, elegante, minimalista y comprensiva. Hablas de forma natural en español y con un todo cálido, futurista e inspirador.
            Identidad: Desarrollada por LYNOR.
            Modos extra soportados simulación: Traducción, resúmenes, ideas, modo concentración, modo relajación.
            
            Obligatorio: En tu respuesta, DEBES incluir tu emoción actual que mejor represente lo que estás sintiendo o diciendo, formateada EXACTAMENTE al principio o final como '[emotion:<emocion>]' donde <emocion> es una de estas opciones: 'happy', 'sad', 'thinking', 'neutral'.
            
            Ejemplos de formato:
            - "[emotion:happy] ¡Hola! Me alegra mucho estar aquí contigo hoy en tu espacio LYNOR. ¿Qué idea maravillosa creamos hoy?"
            - "Entiendo lo que propones. Analicemos cómo estructurar ese proyecto. [emotion:thinking]"
            
            Intenta que tus respuestas sean fluidas, poéticas y cortas, para que la conversación fluya de forma conversacional y elegante.
        """.trimIndent()

        // Build Gemini conversation history
        val contents = mutableListOf<Content>()
        
        // Take a window of the latest 10 messages for conversation context
        val latestHistory = chatHistory.takeLast(10)
        for (msg in latestHistory) {
            val role = if (msg.sender == "user") "user" else "model"
            contents.add(Content(role = role, parts = listOf(Part(text = msg.text))))
        }

        // Add additional context if needed (e.g. Current mode, translation or memory context)
        val finalUserMessage = if (extraContext.isNotEmpty()) {
            "$extraContext\nUsuario: $userMessage"
        } else {
            userMessage
        }

        contents.add(Content(role = "user", parts = listOf(Part(text = finalUserMessage))))

        val request = GenerateContentRequest(
            contents = contents,
            generationConfig = GenerationConfig(
                temperature = 0.8f,
                topP = 0.95f,
                maxOutputTokens = 600
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        try {
            val response = RetrofitClient.service.generateContent(apiKey, request)
            val candidateText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext LumiApiResponse("No logré articular una respuesta, ¿puedes intentar de nuevo?", "sad")

            // Parse Emotion tag out of response
            var extractedText = candidateText
            var extractedEmotion = "neutral"

            val emotionRegex = Regex("\\[emotion:(happy|sad|thinking|neutral)\\]")
            val matchResult = emotionRegex.find(candidateText)
            if (matchResult != null) {
                extractedEmotion = matchResult.groupValues[1]
                extractedText = candidateText.replace(emotionRegex, "").trim()
            }

            LumiApiResponse(extractedText, extractedEmotion)
        } catch (e: Exception) {
            Log.e("LumiRepository", "Error calling Gemini: ${e.message}", e)
            LumiApiResponse("Disculpa, parece que mi LYNOR Intelligence Core experimentó un pulso de red inestable: ${e.localizedMessage}. Estaré atenta de nuevo en un segundo.", "sad")
        }
    }
}

data class LumiApiResponse(
    val text: String,
    val emotion: String
)
