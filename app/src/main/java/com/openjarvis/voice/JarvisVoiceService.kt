package com.openjarvis.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationCompat
import com.openjarvis.R
import com.openjarvis.agent.AgentCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.util.Locale

/** Foreground hands-free loop. Android's recognizer is used as a wake-word approximation. */
class JarvisVoiceService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var agent: AgentCore
    private var listening = false
    private var lastCommandAt = 0L

    override fun onCreate() {
        super.onCreate()
        agent = AgentCore(applicationContext)
        createChannel()
        startForeground(NOTIFICATION_ID, notification())
        buildRecognizer()
        listen()
    }

    private fun buildRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) { listening = true }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false; scheduleListen() }
                override fun onError(error: Int) { listening = false; scheduleListen() }
                override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    handleSpeech(text)
                    scheduleListen()
                }
            })
        }
    }

    private fun listen() {
        if (listening || recognizer == null) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { scheduleListen() }
    }

    private fun handleSpeech(text: String) {
        val normalized = text.lowercase(Locale.getDefault())
        val wake = listOf("hey jarvis", "ok jarvis", "okay jarvis", "jarvis")
        val prefix = wake.firstOrNull { normalized.startsWith(it) } ?: return
        val command = text.substringAfter(prefix, "").trim().trim(',', ':', '-', ' ')
        if (command.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - lastCommandAt < 1500) return
        lastCommandAt = now
        scope.launch {
            agent.executeTask(command).join()
        }
    }

    private fun scheduleListen() {
        android.os.Handler(mainLooper).postDelayed({ listen() }, 400)
    }

    override fun onDestroy() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Jarvis Voice", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Jarvis listening")
        .setContentText("Say “Hey Jarvis” followed by a command")
        .setOngoing(true)
        .build()


    companion object {
        private const val CHANNEL_ID = "jarvis_voice"
        private const val NOTIFICATION_ID = 4001

        fun start(context: android.content.Context) {
            val intent = Intent(context, JarvisVoiceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, JarvisVoiceService::class.java))
        }
    }
}
