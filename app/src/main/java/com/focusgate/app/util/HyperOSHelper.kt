package com.focusgate.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

/**
 * 小米 HyperOS / MIUI 保活与权限辅助工具
 */
object HyperOSHelper {

    /** 判断是否是小米/HyperOS 设备 */
    fun isXiaomiDevice(): Boolean {
        return android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
               android.os.Build.MANUFACTURER.equals("Redmi", ignoreCase = true) ||
               getSystemProperty("ro.miui.ui.version.name") != null
    }

    /**
     * HyperOS 各版本的后台弹出权限页面没有稳定公开入口；错误的私有 Activity
     * 在部分机型会跳到自启动页。改为可靠的应用详情页，让用户进入“其他权限”查看。
     */
    fun openBackgroundPopupSettings(context: Context) {
        Toast.makeText(
            context,
            "请在“其他权限”中查看后台弹出；主拦截使用无障碍覆盖层，不依赖此项",
            Toast.LENGTH_LONG
        ).show()
        openAppSettings(context)
    }

    /** 跳转小米自启动设置 */
    fun openAutoStartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent("miui.intent.action.OP_AUTO_START").apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                openAppSettings(context)
            }
        }
    }

    /** 跳转电池优化设置 */
    fun openBatteryOptimizationSettings(context: Context) {
        if (isIgnoringBatteryOptimizations(context)) {
            Toast.makeText(context, "电池优化已经设为不限制", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                openAppSettings(context)
            }
        }
    }

    /** 跳转到应用详情设置 */
    fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    /** 检测是否已忽略电池优化 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    private fun getSystemProperty(key: String): String? {
        return try {
            Class.forName("android.os.SystemProperties")
                .getMethod("get", String::class.java)
                .invoke(null, key) as? String
        } catch (e: Exception) {
            null
        }
    }
}
