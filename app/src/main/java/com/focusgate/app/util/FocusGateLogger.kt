package com.focusgate.app.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 运行时日志收集器：把所有关键决策和事件写入内存环状缓冲区，
 * 方便用户一键复制发给我们诊断。
 */
object FocusGateLogger {

    private const val TAG = "FocusGateLog"
    private const val MAX_LINES = 600
    private val buffer = ArrayDeque<String>(MAX_LINES)
    private val timeFmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    @JvmStatic
    @Synchronized
    fun log(tag: String, message: String) {
        val ts = timeFmt.format(Date())
        val line = "[$ts] [$tag] $message"
        buffer.addLast(line)
        if (buffer.size > MAX_LINES) buffer.removeFirst()
        Log.d(TAG, line)
    }

    @JvmStatic
    @Synchronized
    fun getLogs(): String = buffer.joinToString("\n")

    @JvmStatic
    @Synchronized
    fun clear() = buffer.clear()
}
