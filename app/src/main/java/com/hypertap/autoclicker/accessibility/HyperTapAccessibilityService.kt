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
import kotlinx.coroutines.suspendCancellableCoroutine

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
                lineTo(x, y + 1f) // Micro-delta de 1 pixel essencial para criar vetor com comprimento > 0 no MotionEventInjector
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, pressDurationMs.coerceAtLeast(40L))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Clique executado com sucesso em ($x, $y)")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Clique cancelado pelo sistema em ($x, $y)")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, mainHandler)

            if (!dispatched) {
                Log.e(TAG, "dispatchGesture retornou false ao enfileirar para ($x, $y)")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    /**
     * Despacha cliques simultâneos em múltiplos pontos (até 10 dedos ao mesmo tempo).
     */
    suspend fun multiClick(points: List<Point2D>, pressDurationMs: Long = 50L): Boolean {
        if (points.isEmpty()) return true
        return suspendCancellableCoroutine { continuation ->
            val builder = GestureDescription.Builder()
            val duration = pressDurationMs.coerceAtLeast(40L)

            points.take(10).forEach { point ->
                val path = Path().apply {
                    moveTo(point.x, point.y)
                    lineTo(point.x, point.y + 1f)
                }
                builder.addStroke(GestureDescription.StrokeDescription(path, 0L, duration))
            }

            val dispatched = dispatchGesture(builder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Multi-clique executado com sucesso (${points.size} pontos)")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Multi-clique cancelado pelo sistema")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, mainHandler)

            if (!dispatched) {
                Log.e(TAG, "dispatchGesture retornou false no multiClick")
                if (continuation.isActive) continuation.resume(false)
            }
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

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Arrasto concluído de ($startX, $startY) até ($endX, $endY)")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Arrasto cancelado pelo sistema")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, mainHandler)

            if (!dispatched) {
                Log.e(TAG, "dispatchGesture retornou false no swipe")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    /**
     * Despacha um arrasto contínuo em loop através de múltiplos pontos
     * sem retirar o dedo da tela utilizando um Path contínuo unificado.
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
        val totalDuration = ((points.size - 1) * stepDuration).coerceAtLeast(100L)

        // Trajeto contínuo unificado passando por todos os waypoints sequencialmente
        val continuousPath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) {
                lineTo(points[i].x, points[i].y)
            }
        }

        val stroke = GestureDescription.StrokeDescription(continuousPath, 0L, totalDuration)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCancellableCoroutine { continuation ->
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "Arrasto contínuo concluído com sucesso (${points.size} pontos, ${totalDuration}ms)")
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "Arrasto contínuo cancelado pelo sistema")
                    if (continuation.isActive) continuation.resume(false)
                }
            }, mainHandler)

            if (!dispatched) {
                Log.e(TAG, "dispatchGesture retornou false no continuousDrag")
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }
}

