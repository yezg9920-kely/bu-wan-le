package com.focusgate.app.rule

import com.focusgate.app.data.IntentType

class IntentStrengthRule : Rule {
    override val name: String = "IntentStrength"
    override val priority: Int = 40

    override fun evaluate(context: RuleContext): RuleResult {
        if (!context.isTargetPackage) {
            return RuleResult()
        }

        return when (context.currentIntent) {
            IntentType.RELAX -> RuleResult(
                reminderIntensity = ReminderIntensity.NORMAL,
                message = "Relax intent: medium reminder level.",
                triggeredBy = name,
                triggeredRules = listOf(name)
            )
            IntentType.INFO, IntentType.INSPIRATION -> RuleResult(
                reminderIntensity = ReminderIntensity.LIGHT,
                message = "Goal-oriented intent: light reminder level.",
                triggeredBy = name,
                triggeredRules = listOf(name)
            )
            IntentType.UNCONSCIOUS -> RuleResult(
                reminderIntensity = ReminderIntensity.STRICT,
                message = "Unconscious open: strict reminder level.",
                triggeredBy = name,
                triggeredRules = listOf(name)
            )
            else -> RuleResult()
        }
    }
}
