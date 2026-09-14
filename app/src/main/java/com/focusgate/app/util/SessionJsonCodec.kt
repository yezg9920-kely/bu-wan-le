package com.focusgate.app.util

import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.rule.ReminderIntensity
import org.json.JSONArray
import org.json.JSONObject

/** Version-tolerant codec shared by the one-time legacy migration and user archives. */
object SessionJsonCodec {
    fun encode(sessions: List<AppSession>): String = JSONArray().apply {
        sessions.forEach { session ->
            put(JSONObject().apply {
                put("pkg", session.packageName)
                put("start", session.startTime)
                put("end", session.endTime ?: -1L)
                put("intent", session.intentType.name)
                put("plan", session.plannedDurationMinutes)
                put("enforced", session.enforcedDurationMinutes ?: -1)
                put("intensity", session.reminderIntensity.name)
                put("double", session.doubleConfirmed)
                put("subTarget", session.subTarget.orEmpty())
                put("blocked", session.blockedByRule.orEmpty())
                put("mood", session.mood?.name.orEmpty())
            })
        }
    }.toString()

    fun decode(raw: String): List<AppSession> {
        val list = mutableListOf<AppSession>()
        val array = JSONArray(raw)
        for (index in 0 until array.length()) {
            try {
                val item = array.getJSONObject(index)
                val end = item.optLong("end", -1L)
                list += AppSession(
                    packageName = item.getString("pkg"),
                    startTime = item.getLong("start"),
                    endTime = end.takeIf { it >= 0L },
                    subTarget = item.optString("subTarget", "").takeIf(String::isNotEmpty),
                    intentType = enumOrDefault(item.optString("intent"), IntentType.UNKNOWN),
                    plannedDurationMinutes = item.optInt("plan", 10).coerceAtLeast(0),
                    enforcedDurationMinutes = item.optInt("enforced", -1).takeIf { it >= 0 },
                    reminderIntensity = enumOrDefault(
                        item.optString("intensity"),
                        ReminderIntensity.NORMAL
                    ),
                    doubleConfirmed = item.optBoolean("double", false),
                    blockedByRule = item.optString("blocked", "").takeIf(String::isNotEmpty),
                    mood = enumOrNull<MoodType>(item.optString("mood", ""))
                )
            } catch (_: Exception) {
                // A damaged record must not hide the rest of the user's history.
            }
        }
        return list
    }

    private inline fun <reified T : Enum<T>> enumOrNull(value: String): T? =
        enumValues<T>().firstOrNull { it.name == value }

    private inline fun <reified T : Enum<T>> enumOrDefault(value: String, fallback: T): T =
        enumOrNull<T>(value) ?: fallback
}
