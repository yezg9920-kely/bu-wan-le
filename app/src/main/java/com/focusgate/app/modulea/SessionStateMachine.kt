package com.focusgate.app.modulea

data class ForegroundSession(
    val packageName: String,
    val startTime: Long,
    val endTime: Long? = null
)

data class SessionTransition(
    val startedSession: ForegroundSession? = null,
    val endedSession: ForegroundSession? = null,
    val activeSession: ForegroundSession? = null
)

/**
 * 可独立测试的前台切换状态机。
 * ignoredPackages 用于 Gate/提醒页面：这些页面出现时保留目标会话。
 */
class SessionStateMachine(
    private val targetPackages: Set<String>,
    private val ignoredPackages: Set<String> = emptySet()
) {
    private var active: ForegroundSession? = null

    fun onForegroundChanged(packageName: String, timestamp: Long): SessionTransition {
        if (packageName in ignoredPackages) {
            return SessionTransition(activeSession = active)
        }

        val previous = active
        if (packageName in targetPackages) {
            if (previous?.packageName == packageName) {
                return SessionTransition(activeSession = previous)
            }
            val ended = previous?.copy(endTime = timestamp)
            val started = ForegroundSession(packageName, timestamp)
            active = started
            return SessionTransition(started, ended, started)
        }

        val ended = previous?.copy(endTime = timestamp)
        active = null
        return SessionTransition(endedSession = ended)
    }
}
