package com.focusgate.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.focusgate.app.databinding.ActivityLegalBinding

class LegalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLegalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLegalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val document = intent.getStringExtra(EXTRA_DOCUMENT)
        if (document == DOCUMENT_TERMS) {
            binding.tvLegalTitle.text = "不玩了用户协议"
            binding.tvLegalBody.text = termsText()
        } else {
            binding.tvLegalTitle.text = "不玩了隐私政策"
            binding.tvLegalBody.text = privacyText()
        }
        binding.btnLegalBack.setOnClickListener { finish() }
    }

    private fun privacyText(): String = """
        更新日期：2026年9月14日
        生效日期：2026年9月14日

        一、社区版的数据处理
        不玩了社区版不申请联网权限，不包含广告、账号、云同步或第三方分析SDK。使用记录、打开目的、状态和计划时长仅保存在本机应用私有空间。

        二、无障碍服务
        经你主动授权后，本应用使用Android无障碍服务检测当前前台应用的包名和窗口切换，从而在你打开选定的短视频应用时显示停顿界面和到时提醒。本应用配置为不读取窗口内容、不读取文字、不记录输入、不执行手势，也不会截图。

        三、悬浮窗和通知
        悬浮窗仅作为部分厂商系统无法稳定显示无障碍覆盖层时的可选兼容方式。通知权限用于显示后台守护状态和到时提醒。拒绝可选权限不会影响历史复盘等不依赖该权限的功能。

        四、本地存储、导入和删除
        历史记录保存在本机Room数据库。加密导出文件由你自行选择保存位置，并使用你设置的口令加密；忘记口令后我们无法恢复。你可以在首页清空本地历史，卸载应用也会删除应用私有数据。

        五、商业版说明
        商业版启用手机号、试用或云同步前会另行展示联网版隐私政策并取得单独同意。社区版不会在后台启用这些能力。

        六、联系我们
        邮箱：support@buwanleapp.cn（域名启用后生效）
        微信：a1870621985
    """.trimIndent()

    private fun termsText(): String = """
        更新日期：2026年9月14日

        不玩了是一款帮助用户减少无意识短视频使用的自控工具，不是系统安全软件，也不保证在所有厂商系统、分屏、应用分身或系统限制下绝对无法绕过。

        你应只在自己拥有或获授权的设备上使用本应用，并自行决定是否开启无障碍、通知和悬浮窗权限。为避免影响紧急通信，本应用不会允许电话、短信、系统设置、桌面或安全中心成为受控目标。

        社区版按GPL-3.0许可证提供。软件按现状提供，但我们会持续修复已确认的稳定性和兼容性问题。

        联系方式：support@buwanleapp.cn（域名启用后生效）；微信 a1870621985。
    """.trimIndent()

    companion object {
        const val EXTRA_DOCUMENT = "document"
        const val DOCUMENT_PRIVACY = "privacy"
        const val DOCUMENT_TERMS = "terms"
    }
}
