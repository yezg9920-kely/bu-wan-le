package com.focusgate.app.modulea

data class UsageEventRecord(
    val packageName: String,
    val timestamp: Long,
    val kind: Kind
) {
    enum class Kind {
        FOREGROUND,
        RESUMED,
        OTHER
    }
}

object UsageEventAnalyzer {
    fun latestForeground(events: List<UsageEventRecord>): UsageEventRecord? {
        return events.asSequence()
            .filter { it.kind == UsageEventRecord.Kind.FOREGROUND || it.kind == UsageEventRecord.Kind.RESUMED }
            .maxByOrNull { it.timestamp }
    }

    fun countLaunches(
        events: List<UsageEventRecord>,
        packageName: String,
        dedupeWindowMs: Long
    ): Int {
        var count = 0
        var lastCountedAt: Long? = null
        events.asSequence()
            .filter { it.packageName == packageName }
            .filter { it.kind == UsageEventRecord.Kind.FOREGROUND || it.kind == UsageEventRecord.Kind.RESUMED }
            .sortedBy { it.timestamp }
            .forEach { event ->
                val last = lastCountedAt
                if (last == null || event.timestamp - last > dedupeWindowMs) {
                    count++
                    lastCountedAt = event.timestamp
                }
            }
        return count
    }
}
