package com.focusgate.app.rule

import com.focusgate.app.Constants
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import java.util.Calendar

data class RuleContext(
    val packageName: String,
    val currentIntent: IntentType? = null,
    val userSelectedDuration: Int = 10,
    val currentTimeMillis: Long = System.currentTimeMillis(),
    val todaySessions: List<AppSession> = emptyList(),
    val allSessions: List<AppSession> = emptyList(),
    val targetPackages: Set<String> = Constants.TARGET_PACKAGES
) {
    val currentHour: Int
        get() = Calendar.getInstance().apply { timeInMillis = currentTimeMillis }
            .get(Calendar.HOUR_OF_DAY)

    val isNightTime: Boolean
        get() = currentHour >= Constants.NIGHT_MODE_START_HOUR ||
            currentHour < Constants.NIGHT_MODE_END_HOUR

    /** 真实包名（不含子目标后缀） */
    val realPackageName: String
        get() = if (packageName.contains("#")) packageName.substringBefore("#") else packageName

    val isTargetPackage: Boolean
        get() = realPackageName in targetPackages ||
            packageName in targetPackages ||
            (packageName.contains("#") && realPackageName in Constants.SUB_TARGET_ACTIVITIES.keys)

    val todayAppSessions: List<AppSession>
        get() = todaySessions.filter { it.packageName == realPackageName }

    val todayUnconsciousCount: Int
        get() = todayAppSessions.count { it.intentType == IntentType.UNCONSCIOUS }

    val unconsciousStreak: Int
        get() {
            val appSessions = allSessions.filter { it.packageName == realPackageName }
            var streak = 0
            for (i in appSessions.indices.reversed()) {
                if (appSessions[i].intentType == IntentType.UNCONSCIOUS) {
                    streak++
                } else {
                    break
                }
            }
            return streak
        }
}
