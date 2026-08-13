package com.nfcgame.app.util

import android.util.Base64
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 端到端加密工具：AES-256-GCM。
 * - 密钥派生：SHA-256(密钥字符串) → 32 字节 AES-256 密钥。
 * - 密文格式（JSON 字符串）：{"v":1,"iv":"...","tag":"...","data":"..."}
 * - 解密失败（密钥不匹配/格式非法）返回 null。
 */
object CryptoUtils {

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    /** 密钥派生：SHA-256 */
    private fun deriveKey(key: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(key.toByteArray(Charsets.UTF_8))
    }

    /**
     * 加密明文，返回密文 JSON 字符串。
     */
    fun encrypt(plaintext: String, key: String): String {
        val secretKey = SecretKeySpec(deriveKey(key), "AES")
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val output = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // GCM 的 doFinal 返回 ciphertext + tag（末尾 16 字节为 tag）
        val tagBytes = GCM_TAG_BITS / 8
        val data = output.copyOfRange(0, output.size - tagBytes)
        val tag = output.copyOfRange(output.size - tagBytes, output.size)

        return JSONObject().apply {
            put("v", 1)
            put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            put("tag", Base64.encodeToString(tag, Base64.NO_WRAP))
            put("data", Base64.encodeToString(data, Base64.NO_WRAP))
        }.toString()
    }

    /**
     * 解密密文 JSON 字符串，密钥错误或格式非法时返回 null。
     */
    fun decrypt(ciphertextJson: String, key: String): String? {
        return try {
            val json = JSONObject(ciphertextJson)
            val iv = Base64.decode(json.getString("iv"), Base64.NO_WRAP)
            val tag = Base64.decode(json.getString("tag"), Base64.NO_WRAP)
            val data = Base64.decode(json.getString("data"), Base64.NO_WRAP)

            val secretKey = SecretKeySpec(deriveKey(key), "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))

            val combined = data + tag
            val plaintext = cipher.doFinal(combined)
            String(plaintext, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    /** 粗略判断字符串是否为加密格式（用于容错） */
    fun looksEncrypted(s: String): Boolean {
        return s.startsWith("{") && s.contains("\"v\"")
    }

    /**
     * 加密后的完整信息载荷（标题 + 内容 + 图片）。
     */
    data class Payload(
        val title: String,
        val content: String,
        val imageUrl: String,
    )

    /**
     * 将标题、内容、图片打包后整体加密，返回密文 JSON。
     * 这样三者在服务器上都是密文，无法明文查看。
     */
    fun encryptPayload(title: String, content: String, imageUrl: String, key: String): String {
        val payload = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("image_url", imageUrl)
        }.toString()
        return encrypt(payload, key)
    }

    /**
     * 解密并解析载荷，密钥错误或格式非法返回 null。
     */
    fun decryptPayload(ciphertext: String, key: String): Payload? {
        val plain = decrypt(ciphertext, key) ?: return null
        return try {
            val json = JSONObject(plain)
            Payload(
                title = json.optString("title"),
                content = json.optString("content"),
                imageUrl = json.optString("image_url"),
            )
        } catch (e: Exception) {
            null
        }
    }
}
