package com.focusgate.app

object Constants {
    // 受控 App 包名（独立应用）
    val TARGET_PACKAGES = setOf(
        "com.ss.android.ugc.aweme",          // 抖音
        "com.ss.android.ugc.aweme.lite",     // 抖音极速版
        "tv.danmaku.bili",                   // B站
        "tv.danmaku.bilibilihd",             // B站 HD
        "com.xingin.xhs",                    // 小红书
        "com.smile.gifmaker",                // 快手
        "com.kuaishou.nebula"                // 快手极速版
    )

    /**
     * 受控子页面（容器应用内的特定 Activity）。
     * Key: 容器应用包名
     * Value: 触发拦截的 Activity 完整类名列表
     */
    val SUB_TARGET_ACTIVITIES = mapOf(
        "com.tencent.mm" to listOf(
            "com.tencent.mm.plugin.finder.ui.FinderHomeUI",
            "com.tencent.mm.plugin.finder.ui.FinderHomeAffinityUI"
        )
    )

    /**
     * 解析容器应用中的受控子页面。微信会在不同版本/机型上切换 FinderHome 的
     * Activity 后缀，因此在保留已知精确类名的同时兼容 FinderHome 系列页面。
     */
    fun resolveSubTarget(packageName: String, className: String?): String? {
        if (className.isNullOrBlank()) return null
        val knownActivities = SUB_TARGET_ACTIVITIES[packageName] ?: return null
        val simpleName = className.substringAfterLast('.')
        val exactMatch = knownActivities.any {
            className == it || simpleName == it.substringAfterLast('.')
        }
        val isFinderHomeVariant = packageName == "com.tencent.mm" &&
            className.contains(".plugin.finder.") &&
            simpleName.startsWith("FinderHome")
        return if (exactMatch || isFinderHomeVariant) "finder" else null
    }

    const val PREFS_NAME = "focus_gate_prefs"
    const val KEY_MANAGED_APPS = "managed_apps"
    const val KEY_FIRST_LAUNCH = "first_launch"

    // 闸门与活跃会话状态。集中定义可以避免 Activity / Service 使用不同键名。
    const val KEY_ACTIVE_SESSION = "active_session"
    const val KEY_GATE_BLOCKED_UNTIL = "gate_blocked_until"
    const val KEY_GATE_BLOCKED_TARGET = "gate_blocked_target"
    const val KEY_LAST_GATE_TIME = "last_gate_time"
    const val KEY_LAST_GATE_TARGET = "last_gate_target"
    const val KEY_LAST_FOREGROUND_APP = "last_foreground_app"
    const val KEY_LAST_MONITORED_EVENT_AT = "last_monitored_event_at"
    const val KEY_LAST_MONITORED_TARGET = "last_monitored_target"
    const val KEY_SERVICE_LAST_CONNECTED_AT = "service_last_connected_at"
    const val KEY_VISIBLE_GATE_REQUEST_ID = "visible_gate_request_id"
    const val KEY_PENDING_GATE_ID = "pending_gate_id"
    const val KEY_PENDING_GATE_STARTED_AT = "pending_gate_started_at"
    const val KEY_PENDING_GATE_OUTCOME = "pending_gate_outcome"
    const val KEY_PENDING_INTENT = "pending_intent"
    const val KEY_PENDING_MINUTES = "pending_minutes"
    const val KEY_PENDING_DOUBLE_CONFIRMED = "pending_double_confirmed"
    const val KEY_PENDING_ENFORCED_DURATION = "pending_enforced_duration"
    const val KEY_PENDING_INTENSITY = "pending_intensity"
    const val KEY_PENDING_RULE_MESSAGE = "pending_rule_msg"
    const val KEY_PENDING_MOOD = "pending_mood"

    const val GATE_OUTCOME_CONFIRMED = "confirmed"
    const val GATE_OUTCOME_BLOCKED = "blocked"
    const val GATE_OUTCOME_CANCELLED = "cancelled"

    // 默认会话时长（分钟）
    val DEFAULT_DURATIONS = listOf(5, 10, 15)

    // 夜间模式时段
    const val NIGHT_MODE_START_HOUR = 23
    const val NIGHT_MODE_END_HOUR = 7
}
