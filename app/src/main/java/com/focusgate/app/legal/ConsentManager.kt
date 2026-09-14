package com.focusgate.app.legal

import android.content.Context

class ConsentManager(context: Context) {
    private val prefs = context.getSharedPreferences("buwanle_legal_consent", Context.MODE_PRIVATE)

    fun hasAcceptedPrivacy(): Boolean =
        prefs.getInt(KEY_PRIVACY_VERSION, 0) >= CURRENT_PRIVACY_VERSION

    fun acceptPrivacy() {
        prefs.edit().putInt(KEY_PRIVACY_VERSION, CURRENT_PRIVACY_VERSION).commit()
    }

    fun hasAcceptedAccessibilityDisclosure(): Boolean =
        prefs.getInt(KEY_ACCESSIBILITY_VERSION, 0) >= CURRENT_ACCESSIBILITY_VERSION

    fun acceptAccessibilityDisclosure() {
        prefs.edit().putInt(KEY_ACCESSIBILITY_VERSION, CURRENT_ACCESSIBILITY_VERSION).commit()
    }

    companion object {
        const val CURRENT_PRIVACY_VERSION = 1
        const val CURRENT_ACCESSIBILITY_VERSION = 1
        private const val KEY_PRIVACY_VERSION = "privacy_version"
        private const val KEY_ACCESSIBILITY_VERSION = "accessibility_disclosure_version"
    }
}
