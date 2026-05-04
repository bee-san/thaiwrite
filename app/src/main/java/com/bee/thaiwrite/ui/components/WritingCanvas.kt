package com.bee.thaiwrite.ui.components

import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bee.thaiwrite.domain.practice.StrokePoint

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

    fun onTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activeStroke.clear()
                activeStroke.add(StrokePoint(event.x, event.y, event.eventTime))
            }
            MotionEvent.ACTION_MOVE -> {
                activeStroke.add(StrokePoint(event.x, event.y, event.eventTime))
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activeStroke.add(StrokePoint(event.x, event.y, event.eventTime))
                committedStrokes.add(activeStroke.toList())
                activeStroke.clear()
            }
        }
        return true
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
            .pointerInteropFilter { event -> state.onTouch(event) },
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
