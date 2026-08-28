package com.focusgate.app.rule

class RuleEngine(rules: List<Rule> = defaultRules) {

    companion object {
        val defaultRules: List<Rule> = listOf(
            UnconsciousStreakRule(),
            NightModeRule(),
            IntentStrengthRule(),
            DefaultDurationRule()
        )
    }

    private val sortedRules = rules.sortedBy { it.priority }

    fun evaluate(context: RuleContext): RuleResult {
        var combined = RuleResult()

        for (rule in sortedRules) {
            val decision = rule.evaluate(context)
            if (decision.isNoOp()) {
                continue
            }

            combined = if (combined.isNoOp()) {
                decision
            } else {
                combined.combineWithLowerPriority(decision)
            }
        }

        return combined
    }
}
