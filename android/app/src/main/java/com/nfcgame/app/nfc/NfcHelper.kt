package com.nfcgame.app.nfc

import android.app.Activity
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Handler
import android.os.Looper
import com.nfcgame.app.util.UidUtils
import java.util.Arrays

/**
 * NFC 读取辅助类：封装 enableReaderMode，读取卡片 UID 与卡片上存储的数据（NDEF）。
 *
 * - 使用 Reader Mode 而非 foreground-dispatch：读取更稳定、可直接拿 Tag、可屏蔽提示音。
 * - 回调运行在 binder 后台线程，这里统一切回主线程。
 * - 除 UID 外，还会尝试读取卡片上未加密的 NDEF 数据（明文文本/URI）；
 *   若卡片数据已加密或无法读取，cardData 为 null（调用方据此「跳过」）。
 */
class NfcHelper(private val activity: Activity) {

    /**
     * 读卡成功回调（始终在主线程调用）。
     * @param uid 规范化后的大写十六进制 UID
     * @param cardData 卡片上存储的明文 NDEF 数据（文本/URI）；读不到或已加密时为 null
     */
    var onTagRead: ((uid: String, cardData: String?) -> Unit)? = null

    private var adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    /** 主线程 Handler：把读卡回调切回主线程 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 去重：上次读到的 UID 与时间，防 reader mode 对同一张卡短时间重复回调 */
    private var lastUid: String? = null
    private var lastReadAt: Long = 0L

    /** 设备是否支持 NFC */
    fun isSupported(): Boolean = adapter != null

    /** NFC 是否已开启 */
    fun isEnabled(): Boolean = adapter?.isEnabled == true

    /**
     * 启用前台读取（应在 onResume 调用）。
     */
    fun startReading() {
        val a = adapter ?: return
        a.enableReaderMode(
            activity,
            { tag -> handleTag(tag) },
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    /**
     * 停止前台读取（应在 onPause 调用）。
     */
    fun stopReading() {
        adapter?.disableReaderMode(activity)
    }

    /**
     * 处理读取到的 Tag：解析 UID + 读取卡片 NDEF 数据，然后切主线程回调。
     */
    private fun handleTag(tag: Tag) {
        val uid = UidUtils.normalize(UidUtils.toHexString(tag.id))
        if (!UidUtils.isValidUid(uid)) return

        val now = System.currentTimeMillis()
        if (uid == lastUid && now - lastReadAt < DEBOUNCE_MS) return
        lastUid = uid
        lastReadAt = now

        // 在 binder 线程读取卡片数据（可能阻塞，不占用主线程）
        val cardData = readCardData(tag)

        mainHandler.post { onTagRead?.invoke(uid, cardData) }
    }

    /**
     * 读取卡片上未加密的 NDEF 数据（文本 / URI）。
     * 加密卡或非 NDEF 卡会返回 null（读取失败、无 NDEF 记录）。
     */
    private fun readCardData(tag: Tag): String? {
        return try {
            val ndef = Ndef.get(tag) ?: return null
            ndef.connect()
            val msg = ndef.ndefMessage ?: ndef.cachedNdefMessage
            try { ndef.close() } catch (_: Exception) {}
            if (msg == null) return null

            val texts = msg.records.mapNotNull { parseRecord(it) }
            if (texts.isEmpty()) null else texts.joinToString("\n")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 解析单条 NDEF 记录：支持文本（RTD_TEXT）与 URI（RTD_URI）。
     */
    private fun parseRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null

        return try {
            when {
                Arrays.equals(record.type, NdefRecord.RTD_TEXT) -> parseText(payload)
                Arrays.equals(record.type, NdefRecord.RTD_URI) -> parseUri(payload)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 解析 RTD_TEXT：首字节为状态（UTF-8/UTF-16 + 语言码长度） */
    private fun parseText(payload: ByteArray): String {
        val status = payload[0].toInt() and 0xFF
        val langLen = status and 0x3F
        val textBytes = payload.copyOfRange(1 + langLen, payload.size)
        val charset = if ((status and 0x80) != 0) Charsets.UTF_16 else Charsets.UTF_8
        return String(textBytes, charset)
    }

    /** 解析 RTD_URI：首字节为前缀码 */
    private fun parseUri(payload: ByteArray): String {
        val prefix = URI_PREFIXES[payload[0].toInt() and 0xFF]
        val rest = String(payload.copyOfRange(1, payload.size), Charsets.UTF_8)
        return prefix + rest
    }

    companion object {
        /** 同一张卡的防抖时间窗（毫秒） */
        const val DEBOUNCE_MS = 1000L

        /** NFC Forum URI 前缀表（RTD_URI 首字节 → 前缀） */
        private val URI_PREFIXES = arrayOf(
            "", "http://www.", "https://www.", "http://", "https://", "tel:", "mailto:",
            "ftp://anonymous:anonymous@", "ftp://ftp.", "ftps://", "sftp://", "smb://",
            "nfs://", "ftp://", "dav://", "news:", "telnet://", "imap:", "rtsp://", "urn:",
            "pop:", "sip:", "sips:", "tftp:", "btspp://", "btl2cap://", "btgoep://",
            "tcpobex://", "irdaobex://", "file://", "urn:epc:id:", "urn:epc:tag:",
            "urn:epc:pat:", "urn:epc:raw:", "urn:epc:", "urn:nfc:",
        )

        /** 判断 Tag 是否为受支持的常见卡片类型 */
        @Suppress("unused")
        fun describeTag(tag: Tag): String {
            val techs = tag.techList
            return when {
                techs.any { it == NfcA::class.java.name } -> "MIFARE / NfcA"
                techs.any { it == NfcB::class.java.name } -> "NfcB"
                techs.any { it == NfcF::class.java.name } -> "NfcF"
                techs.any { it == NfcV::class.java.name } -> "NfcV"
                else -> "未知卡片"
            }
        }
    }
}
