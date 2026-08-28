package com.focusgate.app.rule

interface Rule {
    val name: String
    val priority: Int
    fun evaluate(context: RuleContext): RuleResult
}

data class RuleResult(
    val maxDurationMinutes: Int? = null,
    val reminderIntensity: ReminderIntensity = ReminderIntensity.NORMAL,
    val requireDoubleConfirm: Boolean = false,
    val blockEntirely: Boolean = false,
    val message: String? = null,
    val triggeredBy: String? = null,
    val triggeredRules: List<String> = emptyList()
) {
    fun isNoOp(): Boolean {
        return maxDurationMinutes == null &&
            reminderIntensity == ReminderIntensity.NORMAL &&
            !requireDoubleConfirm &&
            !blockEntirely &&
            message == null &&
            triggeredBy == null &&
            triggeredRules.isEmpty()
    }

    /**
     * Combines current (higher-priority) decision with a lower-priority one.
     * High-priority fields win when both sides provide values.
     */
    fun combineWithLowerPriority(lower: RuleResult): RuleResult {
        return RuleResult(
            maxDurationMinutes = this.maxDurationMinutes ?: lower.maxDurationMinutes,
            reminderIntensity = if (this.reminderIntensity != ReminderIntensity.NORMAL) {
                this.reminderIntensity
            } else {
                lower.reminderIntensity
            },
            requireDoubleConfirm = this.requireDoubleConfirm || lower.requireDoubleConfirm,
            blockEntirely = this.blockEntirely || lower.blockEntirely,
            message = this.message ?: lower.message,
            triggeredBy = this.triggeredBy ?: lower.triggeredBy,
            triggeredRules = (this.triggeredRules + lower.triggeredRules).distinct()
        )
    }
}

enum class ReminderIntensity {
    LIGHT,
    NORMAL,
    STRICT
}
