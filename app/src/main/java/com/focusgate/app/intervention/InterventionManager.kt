package com.focusgate.app.intervention

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.ContextThemeWrapper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.focusgate.app.MainActivity
import com.focusgate.app.R
import com.focusgate.app.dialog.ReminderDialogActivity
import com.focusgate.app.util.BuwanleLogger

/**
 * 干预层管理器：统一调度三档干预手段
 */
class InterventionManager(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "focus_gate_intervention"
        private const val NOTIF_ID_BASE = 2000

        /** 检查是否有悬浮窗权限 */
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }

        /** 跳转到悬浮窗权限设置 */
        fun openOverlaySettings(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null

    init {
        createChannel()
    }

    /** 发送通知提醒（最轻） */
    fun notify(title: String, message: String, packageName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            BuwanleLogger.log("NOTIFY", "通知权限未授予，跳过中途通知")
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(NOTIF_ID_BASE + packageName.hashCode(), notification)
    }

    /** 弹出半屏 DialogActivity（中等） */
    fun showDialog(packageName: String, title: String, message: String, action: DialogAction) {
        val intent = Intent(context, ReminderDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ReminderDialogActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(ReminderDialogActivity.EXTRA_TITLE, title)
            putExtra(ReminderDialogActivity.EXTRA_MESSAGE, message)
            putExtra(ReminderDialogActivity.EXTRA_ACTION, action.name)
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            BuwanleLogger.log("DIALOG", "提醒页面启动失败: ${e.message}")
        }
    }

    /** 无障碍服务可直接使用专用高优先级窗口，无需额外悬浮窗权限。 */
    fun canShowPriorityOverlay(): Boolean {
        return context is AccessibilityService || canDrawOverlays(context)
    }

    /** 显示全屏覆盖层（最重），返回窗口是否真正添加成功。 */
    fun showOverlay(@Suppress("UNUSED_PARAMETER") packageName: String, message: String): Boolean {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            BuwanleLogger.log("OVERLAY", "覆盖层必须在主线程创建，已交由调用方回退")
            return false
        }
        if (!canShowPriorityOverlay()) return false
        dismissOverlay()

        val type = if (context is AccessibilityService) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var candidateView: View? = null
        var candidateWindowManager: WindowManager? = null
        try {
            // AccessibilityService 使用系统 DeviceDefault 主题，无法解析应用的 colorPrimary。
            // 必须显式套用应用主题后再 inflate，否则所选时间到点时会直接崩溃。
            val themedContext = ContextThemeWrapper(context, R.style.Theme_Buwanle_Gate)
            val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_reminder, null)
            candidateView = view
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            candidateWindowManager = wm

            view.findViewById<android.widget.TextView>(R.id.tvOverlayMessage).text = message
            view.findViewById<android.widget.Button>(R.id.btnOverlayDismiss).setOnClickListener {
                dismissOverlay()
                context.sendBroadcast(Intent("com.focusgate.app.EXTEND_SESSION").apply {
                    setPackage(context.packageName)
                    putExtra("extend_minutes", 5)
                })
            }
            view.findViewById<android.widget.Button>(R.id.btnOverlayGoHome).setOnClickListener {
                dismissOverlay()
                val home = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(home)
            }
            view.isFocusableInTouchMode = true
            view.setOnKeyListener { _, keyCode, _ -> keyCode == android.view.KeyEvent.KEYCODE_BACK }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.OPAQUE
            ).apply {
                gravity = Gravity.FILL
                setTitle("不玩了·全屏超时提醒")
            }

            wm.addView(view, params)
            windowManager = wm
            overlayView = view
            view.requestFocus()
            BuwanleLogger.log("OVERLAY", "全屏优先覆盖层显示成功 type=$type")
            return true
        } catch (e: Exception) {
            candidateView?.takeIf { it.isAttachedToWindow }?.let {
                try { candidateWindowManager?.removeViewImmediate(it) } catch (_: Exception) {}
            }
            overlayView = null
            windowManager = null
            BuwanleLogger.log(
                "OVERLAY",
                "悬浮提醒显示失败: ${e.javaClass.simpleName}: ${e.message}"
            )
            return false
        }
    }

    /** 移除悬浮覆盖层 */
    fun dismissOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { dismissOverlay() }
            return
        }
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    /** 取消所有通知 */
    fun dismissNotifications() {
        notificationManager.cancelAll()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "不玩了提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "刷中节奏打断提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    enum class DialogAction {
        MIDWAY,   // 中途提醒：可继续
        TIMEUP    // 超时：需要选择
    }
}
