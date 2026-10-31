package com.hypertap.autoclicker.overlay

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
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

data class TouchRippleEffect(
    val id: Long,
    val x: Float,
    val y: Float,
    val startMillis: Long
)

@Composable
fun ContinuousPathOverlayCanvas(
    waypoints: List<FloatingOverlayService.InteractiveTarget>,
    isClosedLoop: Boolean,
    activeRipples: List<TouchRippleEffect>,
    modifier: Modifier = Modifier
) {
    // Animação de fluxo do traçado (efeito neon fluido contínuo)
    val infiniteTransition = rememberInfiniteTransition(label = "path_animations")

    val flowPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "flow_phase"
    )

    // Partícula viajante que percorre o traçado demonstrando o gesto
    val particleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "traveling_particle"
    )

    // Pulso constante para recomposição em alta taxa de atualização (120Hz)
    val animMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            withFrameMillis { value = it }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val points = waypoints.map { Offset(it.x, it.y) }

        // 1. Desenha as conexões entre os waypoints (com setas e fluxo)
        if (points.size >= 2) {
            for (i in 0 until points.size - 1) {
                val start = points[i]
                val end = points[i + 1]

                // Halo neon externo
                drawLine(
                    color = PurpleAccent.copy(alpha = 0.35f),
                    start = start,
                    end = end,
                    strokeWidth = 16f,
                    cap = StrokeCap.Round
                )

                // Linha de fluxo tracejada animada
                val segmentPath = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(end.x, end.y)
                }

                drawPath(
                    path = segmentPath,
                    color = CyanPrimary,
                    style = Stroke(
                        width = 6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(24f, 16f), flowPhase),
                        cap = StrokeCap.Round
                    )
                )

                // Seta de direção
                drawDirectionArrow(start, end, CyanPrimary)
            }

            // Se for loop fechado, desenha o retorno ao início
            if (isClosedLoop) {
                val start = points.last()
                val end = points.first()

                val returnPath = Path().apply {
                    moveTo(start.x, start.y)
                    lineTo(end.x, end.y)
                }

                drawPath(
                    path = returnPath,
                    color = PurpleAccent,
                    style = Stroke(
                        width = 5f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 16f), flowPhase),
                        cap = StrokeCap.Round
                    )
                )

                drawDirectionArrow(start, end, PurpleAccent)
            }

            // 2. Partícula luminosa viajante que corre sobre o traçado
            val totalSegments = if (isClosedLoop) points.size else points.size - 1
            if (totalSegments > 0) {
                val scaledProgress = particleProgress * totalSegments
                val currentSeg = scaledProgress.toInt().coerceIn(0, totalSegments - 1)
                val segProgress = scaledProgress - currentSeg

                val p1 = points[currentSeg]
                val p2 = if (currentSeg == points.size - 1 && isClosedLoop) points[0] else points[(currentSeg + 1).coerceAtMost(points.size - 1)]

                val posX = p1.x + (p2.x - p1.x) * segProgress
                val posY = p1.y + (p2.y - p1.y) * segProgress

                // Brilho exterior da partícula
                drawCircle(
                    color = CyanPrimary.copy(alpha = 0.6f),
                    radius = 18f,
                    center = Offset(posX, posY)
                )
                // Núcleo branco intenso
                drawCircle(
                    color = Color.White,
                    radius = 8f,
                    center = Offset(posX, posY)
                )
            }
        }

        // 3. Efeito de Onda Expansiva (Ripple) nos Cliques
        val now = System.currentTimeMillis()
        activeRipples.forEach { ripple ->
            val elapsed = now - ripple.startMillis
            val progress = (elapsed / 450f).coerceIn(0f, 1f)
            if (progress < 1f) {
                val radius = 12f + progress * 75f
                val alpha = (1f - progress) * 0.9f

                // Anel de choque expansivo
                drawCircle(
                    color = CyanPrimary.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(ripple.x, ripple.y),
                    style = Stroke(width = (8f * (1f - progress)).coerceAtLeast(1.5f))
                )

                // Segundo anel roxo externo mais sutil
                drawCircle(
                    color = PurpleAccent.copy(alpha = alpha * 0.6f),
                    radius = radius * 1.25f,
                    center = Offset(ripple.x, ripple.y),
                    style = Stroke(width = (4f * (1f - progress)).coerceAtLeast(1f))
                )

                // Núcleo brilhante instantâneo no ponto de contato
                drawCircle(
                    color = Color.White.copy(alpha = (1f - progress * 2f).coerceAtLeast(0f)),
                    radius = (16f * (1f - progress)).coerceAtLeast(2f),
                    center = Offset(ripple.x, ripple.y)
                )
            }
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
    val arrowLength = 26f
    val arrowAngle = Math.toRadians(32.0).toFloat()

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
        style = Stroke(width = 5f, cap = StrokeCap.Round)
    )
}
