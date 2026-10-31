package com.hypertap.autoclicker.engine

import android.util.Log
import com.hypertap.autoclicker.accessibility.HyperTapAccessibilityService
import com.hypertap.autoclicker.model.ExecutionState
import com.hypertap.autoclicker.model.MacroPattern
import com.hypertap.autoclicker.model.TapAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MacroExecutionEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    companion object {
        private const val TAG = "HyperTapEngine"
    }

    private val _executionState = MutableStateFlow(ExecutionState.IDLE)
    val executionState: StateFlow<ExecutionState> = _executionState.asStateFlow()

    private val _currentCycle = MutableStateFlow(0)
    val currentCycle: StateFlow<Int> = _currentCycle.asStateFlow()

    private var executionJob: Job? = null
    private var isPaused = false

    fun start(macro: MacroPattern, onFinished: (() -> Unit)? = null) {
        if (_executionState.value == ExecutionState.RUNNING) return

        val service = HyperTapAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "Falha ao iniciar: AccessibilityService não está conectado.")
            return
        }

        isPaused = false
        _executionState.value = ExecutionState.RUNNING
        _currentCycle.value = 0

        executionJob = scope.launch {
            try {
                val infinite = macro.repeatCount <= 0
                var cycle = 0

                while (isActive && (infinite || cycle < macro.repeatCount)) {
                    // Espera se estiver pausado
                    while (isPaused && isActive) {
                        delay(100)
                    }
                    if (!isActive) break

                    cycle++
                    _currentCycle.value = cycle

                    // Executa cada ação do padrão
                    for (action in macro.actions) {
                        while (isPaused && isActive) {
                            delay(100)
                        }
                        if (!isActive) break

                        // Delay anterior à ação
                        if (action.delayBeforeMs > 0) {
                            delay(action.delayBeforeMs)
                        }

                        executeAction(service, action)
                    }

                    // Intervalo entre repetições
                    if (macro.intervalBetweenCyclesMs > 0 && (infinite || cycle < macro.repeatCount)) {
                        delay(macro.intervalBetweenCyclesMs)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Execução cancelada.")
            } catch (e: Exception) {
                Log.e(TAG, "Erro durante a execução da macro", e)
            } finally {
                _executionState.value = ExecutionState.IDLE
                onFinished?.invoke()
            }
        }
    }

    private suspend fun executeAction(service: HyperTapAccessibilityService, action: TapAction) {
        when (action) {
            is TapAction.SingleTap -> {
                service.click(
                    x = action.point.x,
                    y = action.point.y,
                    pressDurationMs = action.pressDurationMs
                )
            }
            is TapAction.MultiTap -> {
                service.multiClick(
                    points = action.points,
                    pressDurationMs = action.pressDurationMs
                )
            }
            is TapAction.Swipe -> {
                service.swipe(
                    startX = action.startPoint.x,
                    startY = action.startPoint.y,
                    endX = action.endPoint.x,
                    endY = action.endPoint.y,
                    durationMs = action.durationMs
                )
            }
            is TapAction.ContinuousLoop -> {
                val loops = if (action.loopCount <= 0) 1 else action.loopCount
                for (l in 0 until loops) {
                    if (isPaused || _executionState.value != ExecutionState.RUNNING) break
                    service.continuousDrag(
                        waypoints = action.waypoints,
                        stepDurationMs = action.stepDurationMs,
                        isClosedLoop = action.isClosedLoop
                    )
                }
            }
        }
    }

    fun pause() {
        if (_executionState.value == ExecutionState.RUNNING) {
            isPaused = true
            _executionState.value = ExecutionState.PAUSED
        }
    }

    fun resume() {
        if (_executionState.value == ExecutionState.PAUSED) {
            isPaused = false
            _executionState.value = ExecutionState.RUNNING
        }
    }

    fun stop() {
        isPaused = false
        executionJob?.cancel()
        executionJob = null
        _executionState.value = ExecutionState.IDLE
    }
}

