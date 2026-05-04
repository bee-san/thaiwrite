package com.bee.thaiwrite.domain.practice

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.MlKitException
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.recognition.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.recognition.Ink
import com.google.mlkit.vision.digitalink.recognition.RecognitionContext
import com.google.mlkit.vision.digitalink.recognition.WritingArea
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class StrokePoint(
    val x: Float,
    val y: Float,
    val timestamp: Long,
)

data class HandwritingResult(
    val topText: String?,
    val candidates: List<String>,
)

class HandwritingRecognitionService(context: Context) {
    private val model: DigitalInkRecognitionModel
    private val recognizer: DigitalInkRecognizer
    private val remoteModelManager = RemoteModelManager.getInstance()

    init {
        val identifier = requireNotNull(DigitalInkRecognitionModelIdentifier.fromLanguageTag("th")) {
            "Thai digital ink model is not available on this device."
        }
        model = DigitalInkRecognitionModel.builder(identifier).build()
        recognizer = DigitalInkRecognition.getClient(
            DigitalInkRecognizerOptions.builder(model).build(),
        )
    }

    suspend fun isModelDownloaded(): Boolean = remoteModelManager.isModelDownloaded(model).await()

    suspend fun ensureModelDownloaded(requireWifi: Boolean = false): Boolean {
        val builder = DownloadConditions.Builder()
        if (requireWifi) {
            builder.requireWifi()
        }
        remoteModelManager.download(model, builder.build()).await()
        return true
    }

    suspend fun recognize(
        strokes: List<List<StrokePoint>>,
        width: Float,
        height: Float,
        preContext: String = "",
    ): HandwritingResult {
        val inkBuilder = Ink.builder()
        strokes.filter { it.isNotEmpty() }.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.forEach { point ->
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val context = RecognitionContext.builder()
            .setPreContext(preContext)
            .setWritingArea(WritingArea(width, height))
            .build()
        val result = recognizer.recognize(inkBuilder.build(), context).await()
        return HandwritingResult(
            topText = result.candidates.firstOrNull()?.text,
            candidates = result.candidates.map { it.text },
        )
    }

    companion object {
        fun normalizeThai(text: String): String = text
            .trim()
            .replace("\\s+".toRegex(), "")
            .replace("ํา", "ำ")
            .replace("[\\p{Punct}]".toRegex(), "")

        fun matchesExpected(expected: String, candidates: List<String>, topN: Int = 3): Boolean {
            val normalizedExpected = normalizeThai(expected)
            return candidates.take(topN).any { normalizeThai(it) == normalizedExpected }
        }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { error ->
        if (error is MlKitException) {
            cont.resumeWithException(error)
        } else {
            cont.resumeWithException(error)
        }
    }
}
