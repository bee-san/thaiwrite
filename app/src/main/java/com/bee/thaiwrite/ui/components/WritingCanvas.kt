package com.bee.thaiwrite.ui.components

import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bee.thaiwrite.domain.practice.StrokePoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

@Stable
class WritingPadState {
    private val committedStrokes = mutableStateListOf<List<StrokePoint>>()
    private val activeStroke = mutableStateListOf<StrokePoint>()
    var canvasSize by mutableStateOf(IntSize.Zero)
        private set

    fun onSizeChanged(size: IntSize) {
        canvasSize = size
    }

    fun clear() {
        committedStrokes.clear()
        activeStroke.clear()
    }

    fun strokes(): List<List<StrokePoint>> = buildList {
        addAll(committedStrokes)
        if (activeStroke.isNotEmpty()) {
            add(activeStroke.toList())
        }
    }

    fun isEmpty(): Boolean = committedStrokes.isEmpty() && activeStroke.isEmpty()

    fun beginStroke(x: Float, y: Float, timestamp: Long) {
        activeStroke.clear()
        appendPoint(x, y, timestamp)
    }

    fun extendStroke(x: Float, y: Float, timestamp: Long) {
        appendPoint(x, y, timestamp)
    }

    fun endStroke(x: Float, y: Float, timestamp: Long) {
        appendPoint(x, y, timestamp)
        if (activeStroke.isNotEmpty()) {
            committedStrokes.add(activeStroke.toList())
        }
        activeStroke.clear()
    }

    private fun appendPoint(x: Float, y: Float, timestamp: Long) {
        val lastPoint = activeStroke.lastOrNull()
        if (lastPoint != null && abs(lastPoint.x - x) < 0.5f && abs(lastPoint.y - y) < 0.5f) {
            return
        }
        activeStroke.add(StrokePoint(x, y, timestamp))
    }
}

@Composable
fun rememberWritingPadState(): WritingPadState = remember { WritingPadState() }

enum class WritingGuideMode {
    Hidden,
    Trace,
    Fade,
}

/**
 * Ordered guide strokes in an arbitrary guide coordinate space.
 *
 * The default 1x1 size treats points as normalized canvas coordinates.
 */
@Stable
data class WritingStrokeGuide(
    val strokes: List<WritingGuideStroke>,
    val width: Float = 1f,
    val height: Float = 1f,
)

/**
 * One continuous guide stroke. Points are scaled from [WritingStrokeGuide]'s coordinate space.
 */
@Stable
data class WritingGuideStroke(
    val points: List<Offset>,
)

@Composable
fun WritingCanvas(
    state: WritingPadState,
    modifier: Modifier = Modifier,
    guideText: String? = null,
    showGuide: Boolean = true,
    guideMode: WritingGuideMode = if (showGuide) WritingGuideMode.Trace else WritingGuideMode.Hidden,
    strokeGuide: WritingStrokeGuide? = null,
) {
    val strokes = state.strokes()
    val structuredStrokeCount = strokeGuide?.strokes.orEmpty().count { it.points.isNotEmpty() }
    val traceAnimation by rememberInfiniteTransition(label = "writing-guide-trace").animateFloat(
        initialValue = 0f,
        targetValue = max(1, structuredStrokeCount).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = max(1600, structuredStrokeCount * 850),
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "writing-guide-trace-progress",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFFFFFBF4))
            .onSizeChanged(state::onSizeChanged)
            .pointerInput(state) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    state.beginStroke(down.position.x, down.position.y, down.uptimeMillis)
                    down.consume()

                    val pointerId = down.id
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                        if (change.pressed) {
                            change.historical.forEach { historical ->
                                state.extendStroke(
                                    x = historical.position.x,
                                    y = historical.position.y,
                                    timestamp = historical.uptimeMillis,
                                )
                            }
                            state.extendStroke(change.position.x, change.position.y, change.uptimeMillis)
                        } else {
                            state.endStroke(change.position.x, change.position.y, change.uptimeMillis)
                            change.consume()
                            break
                        }
                        change.consume()
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .height(300.dp),
        ) {
            val width = size.width
            val height = size.height
            val rowHeight = height / 4f
            val lineColor = Color(0xFFDFCDB5)
            for (row in 1..3) {
                drawLine(
                    color = lineColor,
                    start = Offset(0f, rowHeight * row),
                    end = Offset(width, rowHeight * row),
                    strokeWidth = 2f,
                )
            }
            drawLine(
                color = Color(0xFFD4793F),
                start = Offset(0f, height * 0.72f),
                end = Offset(width, height * 0.72f),
                strokeWidth = 3f,
            )

            when (guideMode) {
                WritingGuideMode.Hidden -> Unit
                WritingGuideMode.Trace,
                WritingGuideMode.Fade -> {
                    if (strokeGuide.hasDrawableStrokes()) {
                        val guideStrokes = strokeGuide.scaledStrokes(width, height)
                        val guideAlpha = if (guideMode == WritingGuideMode.Trace) 0.26f else 0.16f
                        guideStrokes.forEach { guideStroke ->
                            if (guideStroke.size >= 2) {
                                drawPath(
                                    path = guideStroke.toPath(),
                                    color = Color(0xFF6F4A27).copy(alpha = guideAlpha),
                                    style = Stroke(
                                        width = 10f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }
                        }
                        guideStrokes.forEachIndexed { index, guideStroke ->
                            guideStroke.firstOrNull()?.let { start ->
                                drawStrokeStartMarker(
                                    number = index + 1,
                                    center = start,
                                    active = guideMode == WritingGuideMode.Trace &&
                                        index == traceAnimation.activeStrokeIndex(guideStrokes.size),
                                )
                            }
                        }
                        if (guideMode == WritingGuideMode.Trace && guideStrokes.isNotEmpty()) {
                            val activeProgress = traceAnimation.coerceIn(
                                minimumValue = 0f,
                                maximumValue = guideStrokes.size - 0.001f,
                            )
                            val activeStrokeIndex = activeProgress.activeStrokeIndex(guideStrokes.size)
                            val activeStroke = guideStrokes[activeStrokeIndex]
                            val partialStroke = activeStroke.partialPathPoints(
                                activeProgress - floor(activeProgress),
                            )
                            if (partialStroke.size >= 2) {
                                drawPath(
                                    path = partialStroke.toPath(),
                                    color = Color(0xFFD4793F),
                                    style = Stroke(
                                        width = 14f,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }
                        }
                    } else if (!guideText.isNullOrBlank()) {
                        drawFontOutlineGuide(
                            guideText = guideText,
                            width = width,
                            height = height,
                            alpha = if (guideMode == WritingGuideMode.Trace) 90 else 54,
                        )
                    }
                }
            }

            strokes.forEach { stroke ->
                if (stroke.size >= 2) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(stroke.first().x, stroke.first().y)
                        stroke.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    }
                    drawPath(
                        path = path,
                        color = Color(0xFF101820),
                        style = Stroke(
                            width = 12f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
    }
}

private fun WritingStrokeGuide?.hasDrawableStrokes(): Boolean =
    this?.strokes?.any { it.points.isNotEmpty() } == true

private fun WritingStrokeGuide?.scaledStrokes(canvasWidth: Float, canvasHeight: Float): List<List<Offset>> {
    val guide = this ?: return emptyList()
    val guideWidth = guide.width.takeIf { it > 0f } ?: 1f
    val guideHeight = guide.height.takeIf { it > 0f } ?: 1f
    return guide.strokes
        .filter { it.points.isNotEmpty() }
        .map { stroke ->
            stroke.points.map { point ->
                Offset(
                    x = point.x / guideWidth * canvasWidth,
                    y = point.y / guideHeight * canvasHeight,
                )
            }
        }
}

private fun List<Offset>.toPath(): Path = Path().apply {
    val first = firstOrNull() ?: return@apply
    moveTo(first.x, first.y)
    drop(1).forEach { point -> lineTo(point.x, point.y) }
}

private fun Float.activeStrokeIndex(strokeCount: Int): Int {
    if (strokeCount <= 0) return 0
    return floor(this).toInt().coerceIn(0, strokeCount - 1)
}

private fun List<Offset>.partialPathPoints(progress: Float): List<Offset> {
    if (size <= 1) return this
    val segmentLengths = zipWithNext { start, end -> start.distanceTo(end) }
    val totalLength = segmentLengths.sum()
    if (totalLength <= 0f) return listOf(first())

    val targetLength = totalLength * progress.coerceIn(0f, 1f)
    var traversedLength = 0f
    val partialPoints = mutableListOf(first())

    for (index in 0 until lastIndex) {
        val segmentLength = segmentLengths[index]
        val nextLength = traversedLength + segmentLength
        if (targetLength >= nextLength) {
            partialPoints.add(this[index + 1])
            traversedLength = nextLength
            continue
        }

        val segmentProgress = if (segmentLength == 0f) 0f else (targetLength - traversedLength) / segmentLength
        partialPoints.add(this[index].lerpTo(this[index + 1], segmentProgress))
        break
    }
    return partialPoints
}

private fun Offset.distanceTo(other: Offset): Float {
    val dx = other.x - x
    val dy = other.y - y
    return sqrt(dx * dx + dy * dy)
}

private fun Offset.lerpTo(other: Offset, progress: Float): Offset =
    Offset(
        x = x + (other.x - x) * progress.coerceIn(0f, 1f),
        y = y + (other.y - y) * progress.coerceIn(0f, 1f),
    )

private fun DrawScope.drawStrokeStartMarker(
    number: Int,
    center: Offset,
    active: Boolean,
) {
    val radius = 16f
    val markerColor = if (active) Color(0xFFD4793F) else Color(0xFF6F4A27)
    drawCircle(
        color = Color(0xFFFFFBF4).copy(alpha = 0.92f),
        radius = radius,
        center = center,
    )
    drawCircle(
        color = markerColor,
        radius = radius,
        center = center,
        style = Stroke(width = 3f),
    )
    drawIntoCanvas { canvas ->
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = markerColor.toArgb()
            textAlign = Paint.Align.CENTER
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val textBaseline = center.y - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.nativeCanvas.drawText(number.toString(), center.x, textBaseline, textPaint)
    }
}

private fun DrawScope.drawFontOutlineGuide(
    guideText: String,
    width: Float,
    height: Float,
    alpha: Int,
) {
    drawIntoCanvas { canvas ->
        val nativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(alpha, 86, 54, 26)
            style = Paint.Style.STROKE
            strokeWidth = 5f
            textAlign = Paint.Align.CENTER
            textSize = minOf(width, height) * 0.55f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        }
        val textBounds = Rect()
        nativePaint.getTextBounds(guideText, 0, guideText.length, textBounds)
        val baseline = height * 0.68f
        val path = AndroidPath()
        nativePaint.getTextPath(
            guideText,
            0,
            guideText.length,
            width / 2f - textBounds.exactCenterX(),
            baseline,
            path,
        )
        canvas.nativeCanvas.drawPath(path, nativePaint)
    }
}
