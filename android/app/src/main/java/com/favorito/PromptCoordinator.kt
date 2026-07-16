package com.favorito

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.TextUtils
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * The complete decision flow is intentionally short-lived:
 * 1. Duration and route gates run synchronously.
 * 2. Optional focus ducking starts only for voice mode.
 * 3. SpeechRecognizer is destroyed after a strict timeout.
 * 4. Audio focus is always abandoned in cleanup.
 */
object PromptCoordinator {
    private const val LISTEN_TIMEOUT_MS = 4_000L
    private const val PROMPT_UTTERANCE_ID = "favorito_prompt"

    fun start(context: Context, track: TrackSnapshot, onFinished: () -> Unit) {
        val app = context.applicationContext
        Handler(Looper.getMainLooper()).post {
            if (!SettingsStore.masterEnabled) {
                onFinished()
                return@post
            }

            if (track.durationMs < SettingsStore.minimumDurationSeconds * 1_000L) {
                onFinished()
                return@post
            }

            if (!AudioRouteGate.isPrivateOrVehicleOutputAvailable(app)) {
                onFinished()
                return@post
            }

            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                onFinished()
                return@post
            }

            val focus = AudioFocusDuck(app)
            val shouldDuck = SettingsStore.promptType == SettingsStore.PROMPT_VOICE
            if (shouldDuck) focus.request()

            val cleanup = {
                focus.abandon()
                onFinished()
            }

            if (SettingsStore.promptType == SettingsStore.PROMPT_VOICE) {
                speakThenListen(app, track, cleanup)
            } else {
                vibrateOrChime(app)
                listenForDecision(app, track, cleanup)
            }
        }
    }

    private fun speakThenListen(context: Context, track: TrackSnapshot, onFinished: () -> Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { status ->
            if (status != TextToSpeech.SUCCESS) {
                tts?.shutdown()
                listenForDecision(context, track, onFinished)
                return@TextToSpeech
            }

            tts?.language = Locale.getDefault()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit

                override fun onDone(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        tts?.shutdown()
                        listenForDecision(context, track, onFinished)
                    }
                }

                @Deprecated("Deprecated in platform API")
                override fun onError(utteranceId: String?) {
                    Handler(Looper.getMainLooper()).post {
                        tts?.shutdown()
                        listenForDecision(context, track, onFinished)
                    }
                }
            })

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, PROMPT_UTTERANCE_ID)
            }
            tts?.speak("Add to favorites?", TextToSpeech.QUEUE_FLUSH, params, PROMPT_UTTERANCE_ID)
        }
    }

    private fun vibrateOrChime(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

        if (vibrator?.hasVibrator() == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 35, 45, 70), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 35, 45, 70), -1)
            }
            return
        }

        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        // No bundled sample is required for the main path because vibration is
        // available on phones/watches. Keep this as a no-op fallback and release
        // immediately to avoid resident audio resources.
        pool.release()
    }

    private fun listenForDecision(context: Context, track: TrackSnapshot, onFinished: () -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onFinished()
            return
        }

        val main = Handler(Looper.getMainLooper())
        val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
        var finished = false

        fun finish() {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(recognizer)
            recognizer.cancel()
            recognizer.destroy()
            onFinished()
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onError(error: Int) = finish()
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty()
                when (matches.firstDecision()) {
                    Decision.YES -> LikeExecutor.like(context, track)
                    Decision.NO, Decision.UNKNOWN -> Unit
                }
                finish()
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1_000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }

        recognizer.startListening(intent)
        main.postAtTime({ finish() }, recognizer, SystemClock.uptimeMillis() + LISTEN_TIMEOUT_MS)
    }

    private fun List<String>.firstDecision(): Decision {
        val normalized = map {
            it.lowercase(Locale.US).replace(Regex("[^a-z ]"), " ").trim()
        }.filterNot { TextUtils.isEmpty(it) }

        if (normalized.any { it == "yes" || it.startsWith("yes ") || it in setOf("yeah", "yep", "sure") }) {
            return Decision.YES
        }
        if (normalized.any { it == "no" || it.startsWith("no ") || it in setOf("nope", "nah") }) {
            return Decision.NO
        }
        return Decision.UNKNOWN
    }

    private enum class Decision {
        YES,
        NO,
        UNKNOWN
    }
}
