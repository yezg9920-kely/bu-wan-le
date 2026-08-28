package com.focusgate.app.util

import android.content.Context
import android.content.SharedPreferences
import com.focusgate.app.Constants
import com.focusgate.app.data.AppSession
import com.focusgate.app.data.IntentType
import com.focusgate.app.data.MoodType
import com.focusgate.app.rule.ReminderIntensity
import com.focusgate.app.service.SessionRecoveryPolicy
import org.json.JSONArray
import org.json.JSONObject

class SessionStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SESSIONS = "sessions"
        private const val ACTIVE_SESSION_BUFFER_MINUTES = 5
        private val STORAGE_LOCK = Any()
    }

    init {
        repairStaleOpenSessions()
    }

    /** 保存一个会话 */
    fun saveSession(session: AppSession) {
        synchronized(STORAGE_LOCK) {
            val list = readSessions().toMutableList()
            list.add(session.copy())
            prefs.edit().putString(KEY_SESSIONS, serialize(list)).commit()
        }
    }

    /** 更新最后一个未结束的会话 */
    fun closeLastSession(packageName: String, subTarget: String?, endTime: Long) {
        synchronized(STORAGE_LOCK) {
            val list = readSessions().toMutableList()
            val idx = list.indexOfLast {
                it.packageName == packageName && it.subTarget == subTarget && it.endTime == null
            }
            if (idx >= 0) {
                list[idx] = list[idx].copy(endTime = endTime)
                prefs.edit().putString(KEY_SESSIONS, serialize(list)).commit()
            }
        }
    }

    /** 将延长后的时长等字段同步回最后一个未结束会话。 */
    fun updateLastOpenSession(session: AppSession) {
        synchronized(STORAGE_LOCK) {
            val list = readSessions().toMutableList()
            val idx = list.indexOfLast {
                it.packageName == session.packageName &&
                    it.subTarget == session.subTarget &&
                    it.endTime == null
            }
            if (idx >= 0) {
                list[idx] = session.copy()
                prefs.edit().putString(KEY_SESSIONS, serialize(list)).commit()
            }
        }
    }

    /** 获取所有会话 */
    fun getAllSessions(): List<AppSession> {
        return synchronized(STORAGE_LOCK) { readSessions() }
    }

    /**
     * 修复进程崩溃或服务被系统终止后遗留的 end=-1 记录。
     * 当前仍在有效封锁期内的那一条活跃记录会保留，其余记录最多按用户选择时长结算。
     */
    fun repairStaleOpenSessions(nowMs: Long = System.currentTimeMillis()): Int {
        synchronized(STORAGE_LOCK) {
            val list = readSessions().toMutableList()
            if (list.none { it.endTime == null }) return 0

            val active = readActiveSession()
            val activeIsTemporallyValid = active != null && SessionRecoveryPolicy.isRestorable(
                savedTargetId = active.targetId,
                currentTargetId = active.targetId,
                startTimeMs = active.startTime,
                effectiveMinutes = active.effectiveMinutes,
                nowMs = nowMs,
                bufferMinutes = ACTIVE_SESSION_BUFFER_MINUTES
            )
            var repaired = 0
            list.indices.forEach { index ->
                val session = list[index]
                if (session.endTime != null) return@forEach
                val targetId = session.subTarget?.let { "${session.packageName}#$it" }
                    ?: session.packageName
                val isCurrentActive = activeIsTemporallyValid && active != null &&
                    active.targetId == targetId &&
                    active.startTime == session.startTime
                if (!isCurrentActive) {
                    list[index] = session.copy(
                        endTime = SessionRecoveryPolicy.repairedEndTime(
                            startTimeMs = session.startTime,
                            effectiveMinutes = session.effectiveDurationMinutes,
                            nowMs = nowMs
                        )
                    )
                    repaired++
                }
            }
            if (repaired > 0) {
                prefs.edit().putString(KEY_SESSIONS, serialize(list)).commit()
                FocusGateLogger.log("SESSION_REPAIR", "已修复 $repaired 条异常未结束会话")
            }
            return repaired
        }
    }

    /** 获取今日会话 */
    fun getTodaySessions(): List<AppSession> {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        return getAllSessions().filter { it.startTime >= startOfDay }
    }

    /** 获取今日某 App 的会话数 */
    fun getTodaySessionCount(packageName: String): Int {
        return getTodaySessions().count { it.packageName == packageName }
    }

    /** 获取今日总打开次数 */
    fun getTodayTotalOpens(): Int = getTodaySessions().size

    /** 获取指定时间范围内的会话 */
    fun getSessionsBetween(startMs: Long, endMs: Long): List<AppSession> {
        return getAllSessions().filter { it.startTime in startMs..endMs }
    }

    /** 获取某天的0点毫秒时间 */
    private fun startOfDay(calendar: java.util.Calendar): Long {
        return calendar.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** 获取近N天的会话，按天分组（key: yyyy-MM-dd） */
    fun getLastNDaysSessions(n: Int): Map<String, List<AppSession>> {
        val cal = java.util.Calendar.getInstance()
        val endOfToday = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, -(n - 1))
        val startOfFirstDay = startOfDay(cal)
        val sessions = getAllSessions().filter { it.startTime in startOfFirstDay..endOfToday }
        return sessions.groupBy { session ->
            java.util.Calendar.getInstance().apply { timeInMillis = session.startTime }
                .let {
                    val y = it.get(java.util.Calendar.YEAR)
                    val m = it.get(java.util.Calendar.MONTH) + 1
                    val d = it.get(java.util.Calendar.DAY_OF_MONTH)
                    String.format("%04d-%02d-%02d", y, m, d)
                }
        }
    }

    /** 获取指定日期的24小时分布（每小时使用分钟数） */
    fun getHourlyDistributionForDate(dateMs: Long): IntArray {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
        val start = startOfDay(cal)
        val end = start + 24 * 60 * 60 * 1000L
        val sessions = getAllSessions().filter { it.startTime in start..<end && it.endTime != null }
        val result = IntArray(24) { 0 }
        sessions.forEach { s ->
            val h = java.util.Calendar.getInstance().apply { timeInMillis = s.startTime }
                .get(java.util.Calendar.HOUR_OF_DAY)
            val mins = (s.durationMs / 1000 / 60).toInt()
            result[h] += mins
        }
        return result
    }

    /** 清空数据（调试用） */
    fun clear() {
        synchronized(STORAGE_LOCK) {
            prefs.edit().remove(KEY_SESSIONS).commit()
        }
    }

    private fun serialize(sessions: List<AppSession>): String {
        val arr = JSONArray()
        sessions.forEach { s ->
            arr.put(JSONObject().apply {
                put("pkg", s.packageName)
                put("start", s.startTime)
                put("end", s.endTime ?: -1)
                put("intent", s.intentType.name)
                put("plan", s.plannedDurationMinutes)
                put("enforced", s.enforcedDurationMinutes ?: -1)
                put("intensity", s.reminderIntensity.name)
                put("double", s.doubleConfirmed)
                put("subTarget", s.subTarget ?: "")
                put("blocked", s.blockedByRule ?: "")
                put("mood", s.mood?.name ?: "")
            })
        }
        return arr.toString()
    }

    private fun readSessions(): List<AppSession> {
        val raw = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            deserialize(raw)
        } catch (_: Exception) {
            // 单条旧数据或意外中断不应让闸门、首页和复盘页一起崩溃。
            emptyList()
        }
    }

    private data class ActiveSessionState(
        val targetId: String,
        val startTime: Long,
        val effectiveMinutes: Int
    )

    private fun readActiveSession(): ActiveSessionState? {
        val raw = prefs.getString(Constants.KEY_ACTIVE_SESSION, null) ?: return null
        return try {
            val json = JSONObject(raw)
            val legacyPackage = json.optString("pkg", "")
            val targetId = json.optString("target_id", "").ifBlank {
                val subTarget = json.optString("sub_target", "")
                if (legacyPackage.contains("#") || subTarget.isBlank()) {
                    legacyPackage
                } else {
                    "$legacyPackage#$subTarget"
                }
            }
            ActiveSessionState(
                targetId = targetId,
                startTime = json.optLong("start_time", 0L),
                effectiveMinutes = json.optInt("effective_minutes", 0)
            ).takeIf { it.targetId.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun deserialize(raw: String): List<AppSession> {
        val list = mutableListOf<AppSession>()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            try {
                val obj = arr.getJSONObject(i)
                val enforced = obj.optInt("enforced", -1)
                val intensity = try {
                    ReminderIntensity.valueOf(obj.optString("intensity", ReminderIntensity.NORMAL.name))
                } catch (_: Exception) {
                    ReminderIntensity.NORMAL
                }
                val intent = try {
                    IntentType.valueOf(obj.optString("intent", IntentType.UNKNOWN.name))
                } catch (_: Exception) {
                    IntentType.UNKNOWN
                }
                val moodStr = obj.optString("mood", "")
                val mood = if (moodStr.isNotEmpty()) {
                    try { MoodType.valueOf(moodStr) } catch (_: Exception) { null }
                } else null
                val end = obj.optLong("end", -1L)
                list.add(
                    AppSession(
                        packageName = obj.getString("pkg"),
                        startTime = obj.getLong("start"),
                        endTime = if (end < 0L) null else end,
                        subTarget = obj.optString("subTarget", "").takeIf { it.isNotEmpty() },
                        intentType = intent,
                        plannedDurationMinutes = obj.optInt("plan", 10).coerceAtLeast(0),
                        enforcedDurationMinutes = if (enforced >= 0) enforced else null,
                        reminderIntensity = intensity,
                        doubleConfirmed = obj.optBoolean("double", false),
                        blockedByRule = obj.optString("blocked", "").takeIf { it.isNotEmpty() },
                        mood = mood
                    )
                )
            } catch (_: Exception) {
                // 跳过损坏的单条记录，保留其余历史。
            }
        }
        return list
    }
}
