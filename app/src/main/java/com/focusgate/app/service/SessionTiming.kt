package com.focusgate.app.service

/** 统一所有会话时间计算，避免恢复/延长时从“现在”重新计整段时长。 */
object SessionTiming {
    /** 用户选择一次时长，只生成一个到时提醒，不插入 5 分钟或 70% 检查点。 */
    fun reminderOffsetsMs(selectedMinutes: Int): List<Long> {
        return listOf(selectedMinutes.coerceAtLeast(0) * 60_000L)
    }

    fun deadlineMs(startTimeMs: Long, durationMinutes: Int): Long {
        return startTimeMs + durationMinutes.coerceAtLeast(0) * 60_000L
    }

    fun remainingMs(startTimeMs: Long, durationMinutes: Int, nowMs: Long): Long {
        return (deadlineMs(startTimeMs, durationMinutes) - nowMs).coerceAtLeast(0L)
    }

    fun checkpointRemainingMs(startTimeMs: Long, checkpointMs: Long, nowMs: Long): Long {
        return (startTimeMs + checkpointMs - nowMs).coerceAtLeast(0L)
    }

    fun gateBlockedUntilMs(startTimeMs: Long, durationMinutes: Int, bufferMinutes: Int): Long {
        return deadlineMs(startTimeMs, durationMinutes + bufferMinutes.coerceAtLeast(0))
    }
}
