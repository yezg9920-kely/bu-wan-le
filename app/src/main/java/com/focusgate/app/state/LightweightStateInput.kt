package com.focusgate.app.state

import com.focusgate.app.data.MoodType

data class StateOption(val mood: MoodType, val label: String)

object LightweightStateInput {
    val options: List<StateOption> = listOf(
        StateOption(MoodType.TIRED, "累"),
        StateOption(MoodType.ANNOYED, "烦"),
        StateOption(MoodType.ESCAPE, "想放空"),
        StateOption(MoodType.RESEARCH, "找资料"),
        StateOption(MoodType.ITCHY, "手痒")
    )
}
