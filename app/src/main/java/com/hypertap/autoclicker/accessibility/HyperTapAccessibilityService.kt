package com.hypertap.autoclicker.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.hypertap.autoclicker.model.Point2D
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCancellableCoroutine

class HyperTapAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "HyperTapA11y"

        private var _instance: HyperTapAccessibilityService? = null
        val instance: HyperTapAccessibilityService?
            get() = _instance

        private val _isServiceActive = MutableStateFlow(false)
        val isServiceActive: StateFlow<Boolean> = _isServiceActive.asStateFlow()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _instance = this
        _isServiceActive.value = true
        Log.i(TAG, "HyperTap Accessibility Service conectado com sucesso.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Não é necessário processar eventos de janela para o clicker
    }

    override fun onInterrupt() {
        Log.w(TAG, "HyperTap Accessibility Service interrompido.")
    }

    override fun onDestroy() {
        super.onDestroy()
        _instance = null
        _isServiceActive.value = false
        Log.i(TAG, "HyperTap Accessibility Service destruído.")
    }

    /**
     * Despacha um clique único na posição (x, y).
     */
    suspend fun click(x: Float, y: Float, pressDurationMs: Long = 50L): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(x, y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, pressDurationMs.coerceAtLeast(10L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }, mainHandler)
        }
    }

    /**
     * Despacha cliques simultâneos em múltiplos pontos (até 10 dedos ao mesmo tempo).
     */
    suspend fun multiClick(points: List<Point2D>, pressDurationMs: Long = 50L): Boolean {
        if (points.isEmpty()) return true
        return suspendCancellableCoroutine { continuation ->
            val builder = GestureDescription.Builder()
            val duration = pressDurationMs.coerceAtLeast(10L)

            points.take(10).forEach { point ->
                val path = Path().apply {
                    moveTo(point.x, point.y)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            }

            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }, mainHandler)
        }
    }

    /**
     * Despacha um arrasto (swipe) simples de (startX, startY) até (endX, endY).
     */
    suspend fun swipe(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 300L
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val path = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(50L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }, mainHandler)
        }
    }

    /**
     * Despacha um arrasto contínuo em loop através de múltiplos pontos
     * sem retirar o dedo da tela (usando willContinue = true).
     */
    suspend fun continuousDrag(
        waypoints: List<Point2D>,
        stepDurationMs: Long = 250L,
        isClosedLoop: Boolean = true
    ): Boolean {
        if (waypoints.size < 2) return false

        // Lista de pontos incluindo o retorno ao início se for loop fechado
        val points = if (isClosedLoop && waypoints.first() != waypoints.last()) {
            waypoints + waypoints.first()
        } else {
            waypoints
        }

        val stepDuration = stepDurationMs.coerceAtLeast(50L)

        // Primeiro traço
        val firstSegment = Path().apply {
            moveTo(points[0].x, points[0].y)
            lineTo(points[1].x, points[1].y)
        }

        var currentStroke = GestureDescription.StrokeDescription(
            firstSegment,
            0L,
            stepDuration,
            points.size > 2 // Continua se houver mais pontos
        )

        val strokes = mutableListOf(currentStroke)

        for (i in 2 until points.size) {
            val segmentPath = Path().apply {
                moveTo(points[i - 1].x, points[i - 1].y)
                lineTo(points[i].x, points[i].y)
            }
            val willContinue = (i < points.size - 1)
            currentStroke = currentStroke.continueStroke(segmentPath, 0L, stepDuration, willContinue)
            strokes.add(currentStroke)
        }

        // Despacha os traços de forma encadeada
        return suspendCancellableCoroutine { continuation ->
            val builder = GestureDescription.Builder()
            // Android permite despachar a cadeia de strokes
            strokes.forEach { stroke ->
                builder.addStroke(stroke)
            }

            dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }, mainHandler)
        }
    }
}

