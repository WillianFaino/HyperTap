package com.hypertap.autoclicker.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.hypertap.autoclicker.ui.theme.CyanPrimary
import com.hypertap.autoclicker.ui.theme.PurpleAccent
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ContinuousPathOverlayCanvas(
    waypoints: List<FloatingOverlayService.InteractiveTarget>,
    isClosedLoop: Boolean,
    modifier: Modifier = Modifier
) {
    if (waypoints.size < 2) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val points = waypoints.map { Offset(it.x, it.y) }

        // Desenha traçado conectando os waypoints
        for (i in 0 until points.size - 1) {
            val start = points[i]
            val end = points[i + 1]

            // Linha externa de brilho neon (glow)
            drawLine(
                color = PurpleAccent.copy(alpha = 0.4f),
                start = start,
                end = end,
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )

            // Linha principal de movimento
            drawLine(
                color = CyanPrimary,
                start = start,
                end = end,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            // Seta direcional no meio do segmento
            drawDirectionArrow(start, end, CyanPrimary)
        }

        // Se for loop contínuo fechado, desenha o traço retornando do último ao primeiro
        if (isClosedLoop && points.size >= 2) {
            val start = points.last()
            val end = points.first()

            // Linha tracejada indicando o retorno contínuo sem soltar o dedo
            val path = Path().apply {
                moveTo(start.x, start.y)
                lineTo(end.x, end.y)
            }

            drawPath(
                path = path,
                color = PurpleAccent,
                style = Stroke(
                    width = 5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f), 0f),
                    cap = StrokeCap.Round
                )
            )

            drawDirectionArrow(start, end, PurpleAccent)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDirectionArrow(
    start: Offset,
    end: Offset,
    color: Color
) {
    val midX = (start.x + end.x) / 2f
    val midY = (start.y + end.y) / 2f

    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()).toFloat()
    val arrowLength = 28f
    val arrowAngle = Math.toRadians(30.0).toFloat()

    val x1 = midX - arrowLength * cos(angle - arrowAngle)
    val y1 = midY - arrowLength * sin(angle - arrowAngle)

    val x2 = midX - arrowLength * cos(angle + arrowAngle)
    val y2 = midY - arrowLength * sin(angle + arrowAngle)

    val arrowPath = Path().apply {
        moveTo(midX, midY)
        lineTo(x1, y1)
        moveTo(midX, midY)
        lineTo(x2, y2)
    }

    drawPath(
        path = arrowPath,
        color = color,
        style = Stroke(width = 6f, cap = StrokeCap.Round)
    )
}
