package com.bee.thaiwrite.system

import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
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

    suspend fun isThaiReady(): Boolean {
        if (!ready.await()) {
            return false
        }
        val result = withContext(Dispatchers.Main) {
            tts.setLanguage(Locale.forLanguageTag("th"))
            tts.isLanguageAvailable(Locale.forLanguageTag("th"))
        }
        return result >= TextToSpeech.LANG_AVAILABLE
    }

    suspend fun play(text: String) {
        if (!isThaiReady()) {
            throw IllegalStateException("Thai TTS is not available on this device.")
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

    private suspend fun synthesizeToFile(text: String, file: File) {
        val utteranceId = UUID.randomUUID().toString()
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

    private fun sha1(text: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
