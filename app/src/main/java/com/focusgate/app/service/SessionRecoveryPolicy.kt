package com.focusgate.app.service

/** 服务重连和异常会话修复使用的纯时间判定。 */
object SessionRecoveryPolicy {
    fun isRestorable(
        savedTargetId: String,
        currentTargetId: String,
        startTimeMs: Long,
        effectiveMinutes: Int,
        nowMs: Long,
        bufferMinutes: Int
    ): Boolean {
        if (savedTargetId != currentTargetId || startTimeMs <= 0L || effectiveMinutes <= 0) {
            return false
        }
        if (nowMs < startTimeMs) return false
        val validUntil = safeEndTime(startTimeMs, effectiveMinutes + bufferMinutes)
        return nowMs < validUntil
    }

    /** 无法获知崩溃后的真实离开时刻时，最多按用户选择的时长结算。 */
    fun repairedEndTime(startTimeMs: Long, effectiveMinutes: Int, nowMs: Long): Long {
        val plannedEnd = safeEndTime(startTimeMs, effectiveMinutes.coerceAtLeast(0))
        return minOf(nowMs.coerceAtLeast(startTimeMs), plannedEnd.coerceAtLeast(startTimeMs))
    }

    private fun safeEndTime(startTimeMs: Long, minutes: Int): Long {
        val durationMs = minutes.coerceAtLeast(0).toLong() * 60_000L
        return if (Long.MAX_VALUE - startTimeMs < durationMs) Long.MAX_VALUE else startTimeMs + durationMs
    }
}
