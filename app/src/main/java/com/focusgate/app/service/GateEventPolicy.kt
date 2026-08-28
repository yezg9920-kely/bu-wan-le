package com.focusgate.app.service

/** 纯事件判定，集中保护闸门不被窗口事件或跨应用冷却重复触发。 */
object GateEventPolicy {
    /** HyperOS 的控制中心插件可能使用不带 com. 前缀的 miui.systemui.plugin。 */
    fun shouldIgnoreSystemUiEvent(packageName: String, className: String?): Boolean {
        val knownSystemUi = packageName == "android" ||
            packageName.startsWith("com.android.systemui") ||
            packageName.startsWith("miui.systemui") ||
            packageName.startsWith("com.miui.systemui") ||
            packageName.startsWith("com.miui.notification") ||
            packageName.startsWith("com.android.packageinstaller") ||
            packageName.startsWith("com.google.android.packageinstaller") ||
            packageName.startsWith("com.miui.voiceassist") ||
            packageName.startsWith("com.android.incallui")
        if (knownSystemUi) return true

        // HyperOS 展开/收起通知栏时会由安全中心发送一个没有真实 Activity 的
        // android.widget.FrameLayout 事件，且不会补发目标 App 的返回事件。
        // 仅过滤这种框架浮层；用户真正打开安全中心页面时仍会正常结束会话。
        val isFrameworkOverlay = className?.startsWith("android.widget.") == true ||
            className?.startsWith("android.view.") == true
        return packageName == "com.miui.securitycenter" && isFrameworkOverlay
    }

    fun hasReadyOutcome(activeRequestId: String, storedRequestId: String?, outcome: String?): Boolean {
        return storedRequestId == activeRequestId && outcome != null
    }

    fun shouldIgnoreSteadyContentEvent(
        isContentChange: Boolean,
        foregroundTargetId: String?,
        resolvedTargetId: String
    ): Boolean {
        return isContentChange && foregroundTargetId == resolvedTargetId
    }

    fun isCoolingDown(
        lastGateTargetId: String?,
        newTargetId: String,
        elapsedMs: Long,
        cooldownMs: Long
    ): Boolean {
        return lastGateTargetId == newTargetId && elapsedMs in 0 until cooldownMs
    }
}
