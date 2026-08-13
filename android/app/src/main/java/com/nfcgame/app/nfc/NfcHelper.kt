package com.nfcgame.app.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import android.os.Handler
import android.os.Looper
import com.nfcgame.app.util.UidUtils

/**
 * NFC 读取辅助类：封装 enableReaderMode，读取卡片 UID 并回调。
 *
 * 使用 Reader Mode 而非 foreground-dispatch 的原因：
 *  - 读取更稳定，可直接拿到 Tag 对象；
 *  - 可屏蔽系统 NFC 提示音（FLAG_READER_NO_PLATFORM_SOUNDS）。
 *
 * 注意：enableReaderMode 的 onTagDiscovered 回调运行在 binder 后台线程，
 * 因此这里统一通过主线程 Handler 把 UID 回调切回主线程，避免 UI 层在
 * 后台线程操作控件导致 CalledFromWrongThreadException 或界面卡死。
 */
class NfcHelper(private val activity: Activity) {

    /** UID 读取成功回调（参数为规范化后的大写十六进制 UID）。始终在主线程调用。 */
    var onUidRead: ((String) -> Unit)? = null

    private var adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    /** 主线程 Handler：用于把读卡回调切回主线程 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 去重：上次读到的 UID 与时间，防止 reader mode 对同一张卡在短时间内重复回调 */
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
     * 处理读取到的 Tag：解析 UID 并回调。
     *
     * 本方法运行在 NFC binder 后台线程：
     *  1. 先做去重，避免同一张卡被反复回调导致界面反复刷新；
     *  2. 再 post 到主线程，保证 onUidRead 在主线程执行。
     */
    private fun handleTag(tag: Tag) {
        val uid = UidUtils.normalize(UidUtils.toHexString(tag.id))
        if (!UidUtils.isValidUid(uid)) return

        val now = System.currentTimeMillis()
        // 同一张卡在 DEBOUNCE_MS 内的重复回调直接忽略（reader mode 可能会对同一张卡多次回调）
        if (uid == lastUid && now - lastReadAt < DEBOUNCE_MS) return
        lastUid = uid
        lastReadAt = now

        // 切回主线程再回调 UI
        mainHandler.post { onUidRead?.invoke(uid) }
    }

    companion object {
        /** 同一张卡的防抖时间窗（毫秒） */
        const val DEBOUNCE_MS = 1000L

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
