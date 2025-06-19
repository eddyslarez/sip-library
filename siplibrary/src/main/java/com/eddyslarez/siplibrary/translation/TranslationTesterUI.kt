package com.eddyslarez.siplibrary.translation

/**
 * Interfaz de usuario para probar traducción sin llamadas
 * Esta clase proporciona una API simple para que los desarrolladores integren
 * la funcionalidad de prueba de traducción en sus propias aplicidades
 *
 * @author Eddys Larez
 */
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.filled.Translate
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eddyslarez.siplibrary.EddysSipLibrary
import com.eddyslarez.siplibrary.TranslationTester
import kotlinx.coroutines.*

/**
 * Clase principal para la interfaz de prueba de traducción
 */
class TranslationTesterUI(
    private val context: Context,
    private val onPermissionRequest: (Array<String>, (Boolean) -> Unit) -> Unit
) {
    private var translationTester: TranslationTester? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordingJob: Job? = null
    private var isRecording = false

    private val audioBufferSize = AudioRecord.getMinBufferSize(
        16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
    )

    /**
     * Inicializa el tester con configuración
     */
    fun initialize(config: TranslationConfiguration) {
        translationTester = EddysSipLibrary.getInstance().createTranslationTester(config)
        initializeAudio()
    }

    /**
     * Composable principal para la UI de prueba
     */
    @RequiresApi(Build.VERSION_CODES.O)
    @Composable
    fun TranslationTestScreen() {
        var selectedMyLanguage by remember { mutableStateOf(Language.SPANISH) }
        var selectedTargetLanguage by remember { mutableStateOf(Language.ENGLISH) }
        var selectedVoiceGender by remember { mutableStateOf(VoiceGender.FEMALE) }
        var apiKey by remember { mutableStateOf("") }
        var isSessionActive by remember { mutableStateOf(false) }
        var isCurrentlyRecording by remember { mutableStateOf(false) }
        var statusMessage by remember { mutableStateOf("Configurar parámetros") }
        var translationEvents by remember { mutableStateOf(listOf<TranslationEvent>()) }

        val listState = rememberLazyListState()

        LaunchedEffect(translationEvents) {
            if (translationEvents.isNotEmpty()) {
                listState.animateScrollToItem(translationEvents.size - 1)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            TranslationTestHeader(statusMessage)

            // Configuración
            TranslationConfigCard(
                selectedMyLanguage = selectedMyLanguage,
                selectedTargetLanguage = selectedTargetLanguage,
                selectedVoiceGender = selectedVoiceGender,
                apiKey = apiKey,
                isSessionActive = isSessionActive,
                onMyLanguageChanged = { selectedMyLanguage = it },
                onTargetLanguageChanged = { selectedTargetLanguage = it },
                onVoiceGenderChanged = { selectedVoiceGender = it },
                onApiKeyChanged = { apiKey = it }
            )

            // Controles
            TranslationControlsCard(
                isSessionActive = isSessionActive,
                isCurrentlyRecording = isCurrentlyRecording,
                apiKey = apiKey,
                onSessionToggle = { start ->
                    if (start) {
                        startSession(selectedMyLanguage, selectedTargetLanguage, selectedVoiceGender, apiKey) { success ->
                            isSessionActive = success
                            statusMessage = if (success) "Sesión activa - Listo para traducir" else "Error al iniciar sesión"
                        }
                    } else {
                        stopSession()
                        isSessionActive = false
                        statusMessage = "Sesión terminada"
                    }
                },
                onRecordingToggle = { start ->
                    if (start && isSessionActive) {
                        startRecording()
                        isCurrentlyRecording = true
                    } else {
                        stopRecording()
                        isCurrentlyRecording = false
                    }
                }
            )

            // Log de eventos
            TranslationEventsCard(
                events = translationEvents,
                listState = listState,
                modifier = Modifier.weight(1f)
            )
        }

        // Configurar listener de eventos
        LaunchedEffect(Unit) {
            translationTester?.setEventListener(object : TranslationEventListener {
                override fun onTranslationEvent(event: TranslationEvent) {
                    translationEvents = translationEvents + event

                    when (event) {
                        is TranslationEvent.SessionCreated -> {
                            statusMessage = "Sesión activa - Listo para traducir"
                        }
                        is TranslationEvent.TranslationReady -> {
                            statusMessage = "Audio traducido recibido"
                            playTranslatedAudio(event.translatedAudio)
                        }
                        is TranslationEvent.Error -> {
                            statusMessage = "Error: ${event.message}"
                        }
                        is TranslationEvent.LanguageDetected -> {
                            statusMessage = "Idioma detectado: ${event.language.displayName}"
                        }
                        else -> {}
                    }
                }
            })
        }

        // Update recording state
        isCurrentlyRecording = isRecording
    }

    @Composable
    private fun TranslationTestHeader(statusMessage: String) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prueba de Traducción en Tiempo Real",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }

    @Composable
    private fun TranslationConfigCard(
        selectedMyLanguage: Language,
        selectedTargetLanguage: Language,
        selectedVoiceGender: VoiceGender,
        apiKey: String,
        isSessionActive: Boolean,
        onMyLanguageChanged: (Language) -> Unit,
        onTargetLanguageChanged: (Language) -> Unit,
        onVoiceGenderChanged: (VoiceGender) -> Unit,
        onApiKeyChanged: (String) -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Configuración",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // API Key
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = onApiKeyChanged,
                    label = { Text("OpenAI API Key") },
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSessionActive
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Selección de idiomas
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mi idioma:")
                        LanguageDropdown(
                            selectedLanguage = selectedMyLanguage,
                            onLanguageSelected = onMyLanguageChanged,
                            enabled = !isSessionActive
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Idioma objetivo:")
                        LanguageDropdown(
                            selectedLanguage = selectedTargetLanguage,
                            onLanguageSelected = onTargetLanguageChanged,
                            enabled = !isSessionActive
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Selección de voz
                Text("Género de voz:")
                VoiceGenderSelection(
                    selectedGender = selectedVoiceGender,
                    onGenderSelected = onVoiceGenderChanged,
                    enabled = !isSessionActive
                )
            }
        }
    }

    @Composable
    private fun TranslationControlsCard(
        isSessionActive: Boolean,
        isCurrentlyRecording: Boolean,
        apiKey: String,
        onSessionToggle: (Boolean) -> Unit,
        onRecordingToggle: (Boolean) -> Unit
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón de sesión
                    Button(
                        onClick = { onSessionToggle(!isSessionActive) },
                        enabled = apiKey.isNotEmpty(),
                        colors = if (isSessionActive) {
                            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Icon(
                            imageVector = if (isSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSessionActive) "Detener Sesión" else "Iniciar Sesión")
                    }

                    // Botón de grabación
                    val isEnabled = isSessionActive

                    FloatingActionButton(
                        onClick = { if (isEnabled) onRecordingToggle(!isCurrentlyRecording) },
                        containerColor = if (isCurrentlyRecording) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .alpha(if (isEnabled) 1f else 0.4f)
                    ) {
                        Icon(
                            imageVector = if (isCurrentlyRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                }

                if (isCurrentlyRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "🔴 Grabando... Suelta para traducir",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    @Composable
    private fun TranslationEventsCard(
        events: List<TranslationEvent>,
        listState: LazyListState,
        modifier: Modifier = Modifier
    ) {
        Card(modifier = modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Eventos de Traducción",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(events) { event ->
                        EventItem(event = event)
                    }
                }
            }
        }
    }

    @Composable
    private fun LanguageDropdown(
        selectedLanguage: Language,
        onLanguageSelected: (Language) -> Unit,
        enabled: Boolean
    ) {
        var expanded by remember { mutableStateOf(false) }

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(selectedLanguage.displayName)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Language.values().forEach { language ->
                    DropdownMenuItem(
                        text = { Text(language.displayName) },
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun VoiceGenderSelection(
        selectedGender: VoiceGender,
        onGenderSelected: (VoiceGender) -> Unit,
        enabled: Boolean
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VoiceGender.values().forEach { gender ->
                FilterChip(
                    onClick = { onGenderSelected(gender) },
                    label = { Text(gender.displayName) },
                    selected = selectedGender == gender,
                    enabled = enabled
                )
            }
        }
    }

    @Composable
    private fun EventItem(event: TranslationEvent) {
        val backgroundColor = when (event) {
            is TranslationEvent.SessionCreated -> MaterialTheme.colorScheme.primaryContainer
            is TranslationEvent.TranslationReady -> MaterialTheme.colorScheme.secondaryContainer
            is TranslationEvent.Error -> MaterialTheme.colorScheme.errorContainer
            is TranslationEvent.LanguageDetected -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

        val textColor = when (event) {
            is TranslationEvent.SessionCreated -> MaterialTheme.colorScheme.onPrimaryContainer
            is TranslationEvent.TranslationReady -> MaterialTheme.colorScheme.onSecondaryContainer
            is TranslationEvent.Error -> MaterialTheme.colorScheme.onErrorContainer
            is TranslationEvent.LanguageDetected -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = backgroundColor)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = getEventTitle(event),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = getEventDescription(event),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }

    private fun getEventTitle(event: TranslationEvent): String {
        return when (event) {
            is TranslationEvent.SessionCreated -> "✅ Sesión Creada"
            is TranslationEvent.TranslationReady -> "🔊 Audio Traducido"
            is TranslationEvent.Error -> "❌ Error"
            is TranslationEvent.LanguageDetected -> "🌐 Idioma Detectado"
            is TranslationEvent.SessionEnded -> "🛑 Sesión Finalizada"
            else -> "📝 Evento"
        }
    }

    private fun getEventDescription(event: TranslationEvent): String {
        return when (event) {
            is TranslationEvent.SessionCreated -> "ID: ${event.sessionId}"
            is TranslationEvent.TranslationReady -> "Audio de ${event.translatedAudio.size} bytes ${if (event.forRemote) "para remoto" else "para ti"}"
            is TranslationEvent.Error -> "${event.message} (Código: ${event.code})"
            is TranslationEvent.LanguageDetected -> event.language.displayName
            is TranslationEvent.SessionEnded -> "ID: ${event.sessionId}"
            else -> "Evento de traducción"
        }
    }

    private fun checkPermissions(callback: (Boolean) -> Unit) {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET
        )

        val hasPermissions = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (hasPermissions) {
            callback(true)
        } else {
            onPermissionRequest(permissions, callback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeAudio() {
        try {
            // Configurar AudioRecord para captura
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                16000,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize
            )

            // Configurar AudioTrack para reproducción
            audioTrack = AudioTrack(
                AudioManager.STREAM_VOICE_CALL,
                16000,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                audioBufferSize,
                AudioTrack.MODE_STREAM
            )

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startSession(
        myLanguage: Language,
        targetLanguage: Language,
        voiceGender: VoiceGender,
        apiKey: String,
        callback: (Boolean) -> Unit
    ) {
        checkPermissions { hasPermissions ->
            if (!hasPermissions) {
                callback(false)
                return@checkPermissions
            }

            val config = TranslationConfiguration(
                isEnabled = true,
                myLanguage = myLanguage,
                voiceGender = voiceGender,
                autoDetectRemoteLanguage = false,
                remoteLanguage = targetLanguage,
                openAiApiKey = apiKey,
                voice = voiceGender.getDefaultVoice()
            )

            translationTester?.updateConfiguration(config)
            val success = translationTester?.startTestSession() ?: false
            callback(success)
        }
    }

    private fun stopSession() {
        translationTester?.stopTestSession()
        stopRecording()
    }

    private fun startRecording() {
        if (isRecording || audioRecord == null) return

        try {
            audioRecord?.startRecording()
            isRecording = true

            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                val buffer = ByteArray(audioBufferSize)

                while (isRecording) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (bytesRead > 0) {
                        val audioData = buffer.copyOf(bytesRead)
                        translationTester?.processTestAudio(audioData)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
        }
    }

    private fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        recordingJob?.cancel()

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playTranslatedAudio(audioData: ByteArray) {
        try {
            audioTrack?.play()
            audioTrack?.write(audioData, 0, audioData.size)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun dispose() {
        translationTester?.dispose()
        stopRecording()
        audioRecord?.release()
        audioTrack?.release()
    }
}