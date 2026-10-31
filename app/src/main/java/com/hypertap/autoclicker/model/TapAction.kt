package com.hypertap.autoclicker.model

import kotlinx.serialization.Serializable

@Serializable
data class Point2D(
    val x: Float,
    val y: Float
)

@Serializable
sealed class TapAction {
    abstract val id: String
    abstract val delayBeforeMs: Long

    @Serializable
    data class SingleTap(
        override val id: String,
        val point: Point2D,
        val pressDurationMs: Long = 50L,
        override val delayBeforeMs: Long = 200L
    ) : TapAction()

    @Serializable
    data class MultiTap(
        override val id: String,
        val points: List<Point2D>,
        val pressDurationMs: Long = 50L,
        override val delayBeforeMs: Long = 200L
    ) : TapAction()

    @Serializable
    data class Swipe(
        override val id: String,
        val startPoint: Point2D,
        val endPoint: Point2D,
        val durationMs: Long = 300L,
        override val delayBeforeMs: Long = 200L
    ) : TapAction()

    @Serializable
    data class ContinuousLoop(
        override val id: String,
        val waypoints: List<Point2D>,
        val stepDurationMs: Long = 250L,
        val loopCount: Int = 1, // 0 para infinito até pause
        val isClosedLoop: Boolean = true,
        override val delayBeforeMs: Long = 200L
    ) : TapAction()
}

