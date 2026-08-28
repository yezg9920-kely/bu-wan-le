package com.focusgate.app.rule

class NightModeRule : Rule {
    override val name: String = "NightMode"
    override val priority: Int = 20

    override fun evaluate(context: RuleContext): RuleResult {
        if (!context.isTargetPackage || !context.isNightTime) {
            return RuleResult()
        }

        return RuleResult(
            reminderIntensity = ReminderIntensity.STRICT,
            message = "夜间模式已开启，仍按你选择的时长计时。",
            triggeredBy = name,
            triggeredRules = listOf(name)
        )
    }
}
