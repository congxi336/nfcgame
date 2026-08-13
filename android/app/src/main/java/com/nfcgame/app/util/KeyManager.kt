package com.nfcgame.app.util

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

/**
 * 密钥条目。
 * @param key 密钥内容（分发时分享的字符串）
 * @param remark 备注/标识，用于解密后标明使用了哪个密钥
 */
data class KeyEntry(
    val id: String,
    val key: String,
    val remark: String,
    val createdAt: Long,
)

/**
 * 密钥本地管理：使用 SharedPreferences 存储密钥列表（JSON）。
 * 密钥仅存本机，不上传服务器。
 */
object KeyManager {

    private const val PREFS = "nfc_keys"
    private const val KEY_LIST = "key_list"

    /** 读取所有密钥 */
    fun getKeys(context: Context): List<KeyEntry> {
        val json = prefs(context).getString(KEY_LIST, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                KeyEntry(
                    id = o.getString("id"),
                    key = o.getString("key"),
                    remark = o.optString("remark"),
                    createdAt = o.optLong("createdAt"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 添加密钥 */
    fun addKey(context: Context, key: String, remark: String): KeyEntry {
        val entry = KeyEntry(
            id = UUID.randomUUID().toString(),
            key = key.trim(),
            remark = remark.trim(),
            createdAt = System.currentTimeMillis(),
        )
        val keys = getKeys(context).toMutableList()
        keys.add(entry)
        save(context, keys)
        return entry
    }

    /** 删除密钥 */
    fun deleteKey(context: Context, id: String) {
        save(context, getKeys(context).filter { it.id != id })
    }

    /** 生成随机密钥（64 位十六进制字符串） */
    fun generateKey(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(context: Context, keys: List<KeyEntry>) {
        val arr = JSONArray()
        keys.forEach { k ->
            arr.put(JSONObject().apply {
                put("id", k.id)
                put("key", k.key)
                put("remark", k.remark)
                put("createdAt", k.createdAt)
            })
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }
}
