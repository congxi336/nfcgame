package com.nfcgame.app.util

/**
 * UID 工具：将 NFC 卡片的字节 UID 转换为大写十六进制字符串。
 * 兼容 4 字节（MIFARE Classic）、7 字节（Ultralight / DESFire）等不同长度。
 */
object UidUtils {

    private val HEX_CHARS = "0123456789ABCDEF".toCharArray()

    /**
     * 将字节数组转为大写十六进制字符串，无分隔符。
     * 例：byte[]{0x04,0xA1,0xB2} -> "04A1B2"
     */
    fun toHexString(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val hex = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hex[i * 2] = HEX_CHARS[v ushr 4]
            hex[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(hex)
    }

    /**
     * 校验 UID 是否为合法十六进制字符串（4~16 位）。
     * 与服务器端规则保持一致。
     */
    fun isValidUid(uid: String): Boolean {
        return uid.isNotEmpty() && Regex("^[0-9A-Fa-f]{4,16}$").matches(uid)
    }

    /**
     * 规范化 UID：去除首尾空白并转大写。
     */
    fun normalize(uid: String): String = uid.trim().uppercase()
}
