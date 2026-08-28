package com.focusgate.app.intervention

import com.focusgate.app.rule.ReminderIntensity

enum class InterventionStage {
    FIVE_MINUTES,
    SEVENTY_PERCENT,
    TIME_UP
}

data class InterventionAction(
    val type: InterventionType,
    val reason: String
)

class InterventionPlanner {

    fun plan(
        stage: InterventionStage,
        intensity: ReminderIntensity,
        overlayAvailable: Boolean
    ): InterventionAction {
        return when (stage) {
            InterventionStage.FIVE_MINUTES -> planFiveMinutes(intensity)
            InterventionStage.SEVENTY_PERCENT -> planSeventyPercent(intensity)
            InterventionStage.TIME_UP -> planTimeUp(intensity, overlayAvailable)
        }
    }

    private fun planFiveMinutes(intensity: ReminderIntensity): InterventionAction {
        return when (intensity) {
            ReminderIntensity.LIGHT -> InterventionAction(
                type = InterventionType.NONE,
                reason = "Light mode skips midpoint interruption."
            )
            ReminderIntensity.NORMAL -> InterventionAction(
                type = InterventionType.NOTIFICATION,
                reason = "Normal mode uses notification at 5 minutes."
            )
            ReminderIntensity.STRICT -> InterventionAction(
                type = InterventionType.DIALOG,
                reason = "Strict mode uses dialog at 5 minutes."
            )
        }
    }

    private fun planSeventyPercent(intensity: ReminderIntensity): InterventionAction {
        return when (intensity) {
            ReminderIntensity.STRICT -> InterventionAction(
                type = InterventionType.DIALOG,
                reason = "Strict mode adds an extra 70% checkpoint."
            )
            else -> InterventionAction(
                type = InterventionType.NONE,
                reason = "No 70% checkpoint outside strict mode."
            )
        }
    }

    private fun planTimeUp(
        intensity: ReminderIntensity,
        overlayAvailable: Boolean
    ): InterventionAction {
        return when (intensity) {
            ReminderIntensity.STRICT -> {
                if (overlayAvailable) {
                    InterventionAction(
                        type = InterventionType.OVERLAY,
                        reason = "Strict mode prefers overlay on timeout."
                    )
                } else {
                    InterventionAction(
                        type = InterventionType.FULL_SCREEN,
                        reason = "Overlay unavailable, fall back to full screen."
                    )
                }
            }
            ReminderIntensity.NORMAL, ReminderIntensity.LIGHT -> InterventionAction(
                type = InterventionType.FULL_SCREEN,
                reason = "Timeout always escalates to full screen in non-strict modes."
            )
        }
    }
}
