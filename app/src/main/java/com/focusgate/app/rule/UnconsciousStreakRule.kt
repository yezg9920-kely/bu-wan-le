package com.focusgate.app.rule

import com.focusgate.app.data.IntentType

class UnconsciousStreakRule(
    private val doubleConfirmThreshold: Int = 3,
    private val blockThreshold: Int = 5
) : Rule {
    override val name: String = "UnconsciousStreak"
    override val priority: Int = 10

    override fun evaluate(context: RuleContext): RuleResult {
        if (!context.isTargetPackage || context.currentIntent != IntentType.UNCONSCIOUS) {
            return RuleResult()
        }

        // Gate 在会话保存前执行规则，因此当前这次选择也必须计入连续次数。
        val streak = context.unconsciousStreak + 1
        return when {
            streak >= blockThreshold -> RuleResult(
                blockEntirely = true,
                message = "Consecutive unconscious opens: take a short break first.",
                triggeredBy = name,
                triggeredRules = listOf(name)
            )
            streak >= doubleConfirmThreshold -> RuleResult(
                requireDoubleConfirm = true,
                message = "Three unconscious opens in a row: confirmation required.",
                triggeredBy = name,
                triggeredRules = listOf(name)
            )
            else -> RuleResult()
        }
    }
}
