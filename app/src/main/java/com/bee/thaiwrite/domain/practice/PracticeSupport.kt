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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
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

internal data class PreparedInk(
    val strokes: List<List<StrokePoint>>,
    val width: Float,
    val height: Float,
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
        val preparedInk = prepareInkForRecognition(strokes, width, height)
        val inkBuilder = Ink.builder()
        preparedInk.strokes.forEach { stroke ->
            val strokeBuilder = Ink.Stroke.builder()
            stroke.forEach { point ->
                strokeBuilder.addPoint(Ink.Point.create(point.x, point.y, point.timestamp))
            }
            inkBuilder.addStroke(strokeBuilder.build())
        }
        val context = RecognitionContext.builder()
            .setPreContext(preContext)
            .setWritingArea(WritingArea(preparedInk.width, preparedInk.height))
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
            return matchesAnyExpected(listOf(expected), candidates, topN)
        }

        fun matchesAnyExpected(expectedForms: List<String>, candidates: List<String>, topN: Int = 5): Boolean {
            val normalizedExpected = expectedForms.map(::normalizeThai).toSet()
            return candidates.take(topN).any { candidate ->
                candidateVariants(candidate).any(normalizedExpected::contains)
            }
        }

        private fun candidateVariants(candidate: String): Set<String> {
            val normalizedCandidate = normalizeThai(candidate)
            if (normalizedCandidate.isEmpty()) {
                return emptySet()
            }
            return buildSet {
                add(normalizedCandidate)
                if (normalizedCandidate.length > 1 && normalizedCandidate.first() == 'อ') {
                    add(normalizedCandidate.drop(1))
                }
            }
        }
    }

}

internal fun prepareInkForRecognition(
    strokes: List<List<StrokePoint>>,
    width: Float,
    height: Float,
): PreparedInk {
    val simplifiedStrokes = strokes
        .map(::simplifyStrokeForRecognition)
        .filter { it.isNotEmpty() }
    if (simplifiedStrokes.isEmpty()) {
        return PreparedInk(
            strokes = emptyList(),
            width = width,
            height = height,
        )
    }

    val points = simplifiedStrokes.flatten()
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val sourceWidth = max(maxX - minX, 1f)
    val sourceHeight = max(maxY - minY, 1f)
    val targetWidth = 1000f
    val targetHeight = 1000f
    val margin = 140f
    val scale = min(
        (targetWidth - (margin * 2f)) / sourceWidth,
        (targetHeight - (margin * 2f)) / sourceHeight,
    )
    val scaledWidth = sourceWidth * scale
    val scaledHeight = sourceHeight * scale
    val offsetX = (targetWidth - scaledWidth) / 2f
    val offsetY = (targetHeight - scaledHeight) / 2f

    val normalizedStrokes = simplifiedStrokes.map { stroke ->
        stroke.map { point ->
            StrokePoint(
                x = ((point.x - minX) * scale) + offsetX,
                y = ((point.y - minY) * scale) + offsetY,
                timestamp = point.timestamp,
            )
        }
    }
    return PreparedInk(
        strokes = normalizedStrokes,
        width = targetWidth,
        height = targetHeight,
    )
}

internal fun simplifyStrokeForRecognition(stroke: List<StrokePoint>): List<StrokePoint> {
    if (stroke.size <= 2) {
        return stroke
    }
    val simplified = mutableListOf(stroke.first())
    stroke.drop(1).dropLast(1).forEach { point ->
        val lastPoint = simplified.last()
        if (hypot((point.x - lastPoint.x).toDouble(), (point.y - lastPoint.y).toDouble()) >= 2.5) {
            simplified.add(point)
        }
    }
    val lastPoint = stroke.last()
    val currentLast = simplified.last()
    if (lastPoint != currentLast) {
        simplified.add(lastPoint)
    }
    return simplified
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
