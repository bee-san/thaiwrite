package com.bee.thaiwrite.system

import android.content.Intent
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

data class ThaiAudioSupport(
    val ready: Boolean,
    val engineLabel: String?,
    val enginePackage: String?,
    val message: String,
)

class AudioPromptPlayer(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "tts-cache").apply { mkdirs() }
    private val mediaPlayer = MediaPlayer()
    private val ready = CompletableDeferred<Boolean>()
    private var tts: TextToSpeech

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready.complete(true)
            } else {
                ready.complete(false)
            }
        }
    }

    suspend fun diagnoseThaiSupport(): ThaiAudioSupport {
        if (!ready.await()) {
            return ThaiAudioSupport(
                ready = false,
                engineLabel = null,
                enginePackage = null,
                message = "Android TextToSpeech could not start on this device.",
            )
        }
        return withContext(Dispatchers.Main) {
            val engines = tts.engines
            val enginePackage = tts.defaultEngine
            val engineLabel = engines
                .firstOrNull { it.name == enginePackage }
                ?.label
                ?.takeIf { it.isNotBlank() }
                ?: enginePackage

            var sawMissingData = false
            val supportedLocale = THAI_LOCALES.firstOrNull { locale ->
                val availability = tts.isLanguageAvailable(locale)
                when (availability) {
                    TextToSpeech.LANG_MISSING_DATA -> {
                        sawMissingData = true
                        false
                    }
                    else -> availability >= TextToSpeech.LANG_AVAILABLE
                }
            }

            if (supportedLocale != null) {
                val setResult = tts.setLanguage(supportedLocale)
                if (setResult >= TextToSpeech.LANG_AVAILABLE) {
                    return@withContext ThaiAudioSupport(
                        ready = true,
                        engineLabel = engineLabel,
                        enginePackage = enginePackage,
                        message = if (engineLabel != null) {
                            "Thai audio is ready through $engineLabel."
                        } else {
                            "Thai audio is ready on this device."
                        },
                    )
                }
                if (setResult == TextToSpeech.LANG_MISSING_DATA) {
                    sawMissingData = true
                }
            }

            val installedEngines = engines.joinToString { engine ->
                engine.label.takeIf { it.isNotBlank() } ?: engine.name
            }
            val baseMessage = when {
                sawMissingData -> {
                    if (engineLabel != null) {
                        "$engineLabel is installed, but Thai voice data is missing."
                    } else {
                        "Thai voice data is missing from the current TextToSpeech engine."
                    }
                }
                engines.isEmpty() -> {
                    "No Android TextToSpeech engine is installed on this device."
                }
                engineLabel != null -> {
                    "$engineLabel does not currently expose a Thai voice."
                }
                else -> {
                    "The current TextToSpeech engine does not currently expose a Thai voice."
                }
            }
            val suffix = when {
                installedEngines.isBlank() -> "Open Text-to-speech settings and install a Thai voice."
                else -> "Open Text-to-speech settings, install a Thai voice, or switch engines. Installed engines: $installedEngines."
            }
            ThaiAudioSupport(
                ready = false,
                engineLabel = engineLabel,
                enginePackage = enginePackage,
                message = "$baseMessage $suffix",
            )
        }
    }

    suspend fun isThaiReady(): Boolean {
        return diagnoseThaiSupport().ready
    }

    suspend fun play(text: String) {
        val support = diagnoseThaiSupport()
        if (!support.ready) {
            throw IllegalStateException(support.message)
        }
        val file = File(cacheDir, "${sha1(text)}.wav")
        if (!file.exists()) {
            synthesizeToFile(text, file)
        }
        withContext(Dispatchers.Main) {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.prepare()
            mediaPlayer.start()
        }
    }

    fun release() {
        mediaPlayer.release()
        tts.stop()
        tts.shutdown()
    }

    fun openThaiSetup() {
        val intents = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        val intent = intents.firstOrNull { candidate ->
            candidate.resolveActivity(appContext.packageManager) != null
        } ?: throw IllegalStateException("Android could not open Text-to-speech settings on this device.")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(intent)
    }

    private suspend fun synthesizeToFile(text: String, file: File) {
        val utteranceId = UUID.randomUUID().toString()
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit

                    override fun onDone(doneId: String?) {
                        if (doneId == utteranceId && continuation.isActive) {
                            continuation.resume(Unit)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(errorId: String?) {
                        if (errorId == utteranceId && continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Audio synthesis failed."))
                        }
                    }

                    override fun onError(errorId: String?, errorCode: Int) {
                        if (errorId == utteranceId && continuation.isActive) {
                            continuation.resumeWithException(IllegalStateException("Audio synthesis failed with code $errorCode."))
                        }
                    }
                }
                tts.setOnUtteranceProgressListener(listener)
                val result = tts.synthesizeToFile(text, Bundle(), file, utteranceId)
                if (result != TextToSpeech.SUCCESS && continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("Unable to synthesize Thai audio."))
                }
            }
        }
    }

    private fun sha1(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val THAI_LOCALES = listOf(
            Locale.forLanguageTag("th-TH"),
            Locale.forLanguageTag("th"),
        )
    }
}
