package com.focusgate.app.backup

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.focusgate.app.Constants
import com.focusgate.app.util.SessionJsonCodec
import com.focusgate.app.util.SessionStorage
import org.json.JSONObject
import java.security.MessageDigest

class DataArchiveManager(private val context: Context) {
    data class ImportResult(val importedSessions: Int, val importedManagedApps: Int)

    fun exportTo(uri: Uri, passphrase: CharArray): Result<Int> = runCatching {
        val sessions = SessionStorage(context).getAllSessions().filter { it.endTime != null }
        val data = JSONObject().apply {
            put("sessions", SessionJsonCodec.encode(sessions))
            put(
                "managedApps",
                JSONObject().put(
                    "values",
                    context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                        .getStringSet(Constants.KEY_MANAGED_APPS, emptySet())
                        .orEmpty()
                        .toList()
                )
            )
        }.toString().toByteArray(Charsets.UTF_8)
        val envelope = JSONObject().apply {
            put("schemaVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("sha256", sha256(data))
            put("payload", Base64.encodeToString(data, Base64.NO_WRAP))
        }.toString().toByteArray(Charsets.UTF_8)
        val encrypted = ArchiveCrypto.encrypt(envelope, passphrase)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
            ?: error("无法写入所选文件")
        sessions.size
    }.also { passphrase.fill('\u0000') }

    fun importFrom(uri: Uri, passphrase: CharArray): Result<ImportResult> = runCatching {
        val encrypted = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选文件")
        val envelope = JSONObject(ArchiveCrypto.decrypt(encrypted, passphrase).toString(Charsets.UTF_8))
        require(envelope.optInt("schemaVersion", -1) == 1) { "暂不支持该备份版本" }
        val data = Base64.decode(envelope.getString("payload"), Base64.NO_WRAP)
        require(sha256(data) == envelope.getString("sha256")) { "备份校验失败" }
        val payload = JSONObject(data.toString(Charsets.UTF_8))
        val sessions = SessionJsonCodec.decode(payload.getString("sessions"))
        val imported = SessionStorage(context).importSessions(sessions)
        val managedAppsJson = payload.optJSONObject("managedApps")?.optJSONArray("values")
        val managedApps = buildSet {
            if (managedAppsJson != null) {
                for (index in 0 until managedAppsJson.length()) {
                    managedAppsJson.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }
        }
        if (managedApps.isNotEmpty()) {
            context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putStringSet(Constants.KEY_MANAGED_APPS, managedApps)
                .commit()
        }
        ImportResult(imported, managedApps.size)
    }.also { passphrase.fill('\u0000') }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
