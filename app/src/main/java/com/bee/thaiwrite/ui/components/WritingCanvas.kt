package com.bee.thaiwrite.ui.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bee.thaiwrite.domain.practice.StrokePoint
import kotlin.math.abs

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

@Composable
fun WritingCanvas(
    state: WritingPadState,
    modifier: Modifier = Modifier,
    guideText: String? = null,
    showGuide: Boolean = true,
) {
    val strokes = state.strokes()
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

            if (showGuide && !guideText.isNullOrBlank()) {
                drawIntoCanvas { canvas ->
                    val nativePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb(90, 86, 54, 26)
                        style = Paint.Style.STROKE
                        strokeWidth = 5f
                        textAlign = Paint.Align.CENTER
                        textSize = minOf(width, height) * 0.55f
                        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    }
                    val textBounds = Rect()
                    nativePaint.getTextBounds(guideText, 0, guideText.length, textBounds)
                    val baseline = height * 0.68f
                    val path = Path()
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
