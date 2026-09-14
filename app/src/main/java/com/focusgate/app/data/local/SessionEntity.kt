package com.focusgate.app.data.local

import androidx.room.Entity
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.rule.ReminderIntensity

@Entity(
    tableName = "sessions",
    primaryKeys = ["packageName", "subTargetKey", "startTime"]
)
data class SessionEntity(
    val packageName: String,
    val subTargetKey: String,
    val startTime: Long,
    val endTime: Long?,
    val intentType: String,
    val plannedDurationMinutes: Int,
    val enforcedDurationMinutes: Int?,
    val reminderIntensity: String,
    val doubleConfirmed: Boolean,
    val blockedByRule: String?,
    val mood: String?
) {
    fun toDomain(): AppSession = AppSession(
        packageName = packageName,
        startTime = startTime,
        endTime = endTime,
        subTarget = subTargetKey.ifBlank { null },
        intentType = enumValueOrDefault(intentType, IntentType.UNKNOWN),
        plannedDurationMinutes = plannedDurationMinutes,
        enforcedDurationMinutes = enforcedDurationMinutes,
        reminderIntensity = enumValueOrDefault(reminderIntensity, ReminderIntensity.NORMAL),
        doubleConfirmed = doubleConfirmed,
        blockedByRule = blockedByRule,
        mood = mood?.let { enumValueOrNull<MoodType>(it) }
    )

    companion object {
        fun fromDomain(session: AppSession): SessionEntity = SessionEntity(
            packageName = session.packageName,
            subTargetKey = session.subTarget.orEmpty(),
            startTime = session.startTime,
            endTime = session.endTime,
            intentType = session.intentType.name,
            plannedDurationMinutes = session.plannedDurationMinutes,
            enforcedDurationMinutes = session.enforcedDurationMinutes,
            reminderIntensity = session.reminderIntensity.name,
            doubleConfirmed = session.doubleConfirmed,
            blockedByRule = session.blockedByRule,
            mood = session.mood?.name
        )
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    enumValueOrNull<T>(value) ?: fallback
