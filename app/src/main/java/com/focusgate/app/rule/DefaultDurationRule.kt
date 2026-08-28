package com.focusgate.app.rule

class DefaultDurationRule : Rule {
    override val name: String = "DefaultDuration"
    override val priority: Int = 50

    override fun evaluate(context: RuleContext): RuleResult {
        if (!context.isTargetPackage) {
            return RuleResult()
        }

        val minutes = if (context.userSelectedDuration > 0) {
            context.userSelectedDuration
        } else {
            10
        }

        return RuleResult(
            maxDurationMinutes = minutes,
            message = "Session duration set to $minutes minutes.",
            triggeredBy = name,
            triggeredRules = listOf(name)
        )
    }
}
