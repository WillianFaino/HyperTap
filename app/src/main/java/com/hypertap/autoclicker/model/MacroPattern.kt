package com.hypertap.autoclicker.model

import kotlinx.serialization.Serializable

@Serializable
data class MacroPattern(
    val id: Long = 0L,
    val name: String,
    val repeatCount: Int = 1, // 0 = Loop Infinito
    val intervalBetweenCyclesMs: Long = 500L,
    val actions: List<TapAction> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class ExecutionState {
    IDLE,
    RUNNING,
    PAUSED
}

