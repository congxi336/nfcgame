package com.nfcgame.app.ui.query

import android.animation.ValueAnimator
import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.nfcgame.app.MainActivity
import com.nfcgame.app.R
import com.nfcgame.app.databinding.FragmentQueryBinding
import com.nfcgame.app.network.HttpClient
import com.nfcgame.app.network.NfcRepository
import com.nfcgame.app.util.AnimUtils
import com.nfcgame.app.util.CryptoUtils
import com.nfcgame.app.util.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 查询模式：默认界面，读取卡片 UID 并查询显示隐藏信息。
 * 查询结果中的图片支持保存到系统相册。
 */
class QueryFragment : Fragment() {

    private var _binding: FragmentQueryBinding? = null
    private val binding get() = _binding!!

    private val apiService by lazy { HttpClient.getApiService(requireContext()) }

    /** 当前展示的图片 URL（用于保存到相册） */
    private var currentImageUrl: String? = null

    /** 当前卡片上存储的明文数据（NDEF），未加密时非空 */
    private var currentCardData: String? = null

    /** Android 9 及以下保存图片需要存储权限 */
    private val requestStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                doSaveImage()
            } else {
                Toast.makeText(requireContext(), "未授予存储权限，无法保存图片", Toast.LENGTH_SHORT).show()
            }
        }

    /** 扫描圆环的呼吸脉冲动画（onDestroyView 时取消） */
    private var pulseAnimator: ValueAnimator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentQueryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val helper = (requireActivity() as MainActivity).nfcHelper
        helper.onTagRead = { uid, cardData -> onTagRead(uid, cardData) }

        // 保存图片按钮
        binding.btnSaveImage.setOnClickListener { onSaveImageClick() }
        AnimUtils.pressScale(binding.btnSaveImage)

        // 扫描圆环：等待状态下的呼吸脉冲
        pulseAnimator = AnimUtils.pulse(binding.viewScanRing, requireContext())
    }

    override fun onResume() {
        super.onResume()
        val helper = (requireActivity() as MainActivity).nfcHelper

        when {
            !helper.isSupported() -> showStatus(getString(R.string.nfc_not_supported), isError = true)
            !helper.isEnabled() -> showStatus(getString(R.string.nfc_disabled), isError = true)
            else -> {
                showStatus(getString(R.string.query_hint))
                helper.startReading()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as MainActivity).nfcHelper.stopReading()
    }

    /** 读卡成功回调 */
    private fun onTagRead(uid: String, cardData: String?) {
        if (_binding == null) return

        // 触感反馈
        val vibrator = ContextCompat.getSystemService(requireContext(), Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(50)
        }

        // 扫描成功：圆环爆发一次，随后恢复呼吸脉冲
        pulseAnimator?.cancel()
        AnimUtils.scanBurst(binding.viewScanRing) {
            if (_binding != null) {
                pulseAnimator = AnimUtils.pulse(binding.viewScanRing, requireContext())
            }
        }

        currentCardData = cardData
        showStatus(getString(R.string.query_reading))
        queryAndShow(uid)
    }

    /** 请求服务器查询并展示结果 */
    private fun queryAndShow(uid: String) {
        lifecycleScope.launch {
            when (val result = NfcRepository.queryInfo(apiService, uid)) {
                is NfcRepository.Result.Success -> {
                    val info = result.data
                    if (info == null) {
                        showStatus(getString(R.string.query_not_found), isError = true)
                    } else {
                        showInfo(
                            uid,
                            info.title.orEmpty(),
                            info.content.orEmpty(),
                            info.imageUrl,
                            info.encrypted ?: 0,
                        )
                    }
                }
                is NfcRepository.Result.Error -> {
                    showStatus(result.message, isError = true)
                }
            }
        }
    }

    /** 展示隐藏信息（含解密处理） */
    private fun showInfo(
        uid: String,
        title: String,
        content: String,
        imageUrl: String?,
        encrypted: Int,
    ) {
        if (encrypted == 1) {
            val result = decryptContent(content)
            val payload = result.payload
            if (payload != null) {
                renderInfo(uid, payload.title, payload.content, payload.imageUrl, result.keyLabel)
            } else {
                // 解密失败：标题、内容、图片全部不显示明文
                binding.tvTitle.text = title // 服务器上的占位符「🔒 已加密」
                binding.tvContent.text = getString(R.string.query_encrypted)
                binding.tvStatus.text = getString(R.string.query_encrypted)
                binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.error_red))
                binding.ivImage.visibility = View.GONE
                binding.btnSaveImage.visibility = View.GONE
                currentImageUrl = null
                renderCardData()
                animateResultCardIn()
            }
        } else {
            renderInfo(uid, title, content, imageUrl.orEmpty(), null)
        }
    }

    /** 渲染明文信息（解密后，或本来未加密） */
    private fun renderInfo(uid: String, title: String, content: String, imageUrl: String, keyLabel: String?) {
        binding.tvTitle.text = title
        binding.tvContent.text = content
        binding.tvStatus.text = if (keyLabel != null) {
            getString(R.string.query_decrypted_by, keyLabel)
        } else {
            "UID: $uid"
        }
        binding.tvStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.neon_cyan))

        val hasImage = imageUrl.isNotBlank()
        currentImageUrl = imageUrl.takeIf { it.isNotBlank() }

        if (hasImage) {
            binding.ivImage.visibility = View.VISIBLE
            binding.btnSaveImage.visibility = View.VISIBLE
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.bg_neon_card)
                .error(R.drawable.bg_neon_card)
                .transition(DrawableTransitionOptions.withCrossFade(220))
                .into(binding.ivImage)
        } else {
            binding.ivImage.visibility = View.GONE
            binding.btnSaveImage.visibility = View.GONE
        }

        renderCardData()
        animateResultCardIn()
    }

    /** 结果卡片入场：整体上浮淡入，随后子元素逐个交错出现 */
    private fun animateResultCardIn() {
        binding.cardResult.visibility = View.VISIBLE
        binding.cardResult.alpha = 0f
        binding.cardResult.translationY = 48f
        binding.cardResult.scaleX = 0.96f
        binding.cardResult.scaleY = 0.96f
        binding.cardResult.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(360L)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                // 仅对当前可见的子元素做交错入场
                val children = listOfNotNull(
                    binding.tvTitle.takeIf { it.visibility == View.VISIBLE },
                    binding.tvContent.takeIf { it.visibility == View.VISIBLE },
                    binding.ivImage.takeIf { it.visibility == View.VISIBLE },
                    binding.btnSaveImage.takeIf { it.visibility == View.VISIBLE },
                    binding.tvCardDataLabel.takeIf { it.visibility == View.VISIBLE },
                    binding.tvCardData.takeIf { it.visibility == View.VISIBLE },
                )
                AnimUtils.staggerIn(children, duration = 240L, step = 55L)
            }
            .start()
    }

    /**
     * 渲染卡片上存储的明文数据（NDEF）。
     * 读到了明文（未加密）则展示；读不到/已加密则隐藏（跳过）。
     */
    private fun renderCardData() {
        val data = currentCardData
        if (data.isNullOrBlank()) {
            binding.tvCardDataLabel.visibility = View.GONE
            binding.tvCardData.visibility = View.GONE
        } else {
            binding.tvCardDataLabel.visibility = View.VISIBLE
            binding.tvCardData.visibility = View.VISIBLE
            binding.tvCardData.text = data
        }
    }

    /**
     * 解密结果。
     * @param payload 解密后的完整载荷（标题+内容+图片），null 表示解密失败
     * @param keyLabel 命中的密钥标识（备注）
     */
    private data class DecryptResult(val payload: CryptoUtils.Payload?, val keyLabel: String?)

    /**
     * 用设备上所有密钥依次尝试解密，全部失败返回 null 载荷。
     * 密钥只在本地，服务器不存密钥，因此解密完全依赖本机密钥列表。
     */
    private fun decryptContent(ciphertext: String): DecryptResult {
        val keys = KeyManager.getKeys(requireContext())
        for (entry in keys) {
            CryptoUtils.decryptPayload(ciphertext, entry.key)?.let {
                return DecryptResult(it, entry.remark.ifBlank { "密钥 ${entry.key.take(4)}…" })
            }
        }
        return DecryptResult(null, null)
    }

    /** 保存按钮点击：Android 9 及以下先请求权限 */
    private fun onSaveImageClick() {
        if (currentImageUrl.isNullOrBlank()) return

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            val granted = ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                return
            }
        }
        doSaveImage()
    }

    /** 执行保存 */
    private fun doSaveImage() {
        val url = currentImageUrl ?: return
        binding.tvStatus.text = getString(R.string.query_saving_image)

        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { saveImageToGallery(url) }
            if (ok) {
                binding.tvStatus.text = getString(R.string.query_image_saved)
                Toast.makeText(requireContext(), R.string.query_image_saved, Toast.LENGTH_SHORT).show()
            } else {
                binding.tvStatus.text = getString(R.string.query_image_save_failed)
                Toast.makeText(requireContext(), R.string.query_image_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 将图片保存到系统相册（在 IO 线程执行）。
     * Android 10+ 走 MediaStore（免权限），Android 9 及以下走公共 Pictures 目录。
     */
    private fun saveImageToGallery(url: String): Boolean {
        return try {
            // 用 Glide 下载图片到缓存文件（复用已加载的缓存）
            val file = Glide.with(this).asFile().load(url).submit().get()

            val mime = mimeFromUrl(url)
            val ext = extFromMime(mime)

            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "nfc_${System.currentTimeMillis()}.$ext")
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NFC")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = requireContext().contentResolver
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val uri = resolver.insert(collection, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            android.util.Log.w("QueryFragment", "保存图片失败", e)
            false
        }
    }

    /** 根据 URL 推断 MIME 类型 */
    private fun mimeFromUrl(url: String): String {
        val lower = url.lowercase()
        return when {
            lower.contains(".png") -> "image/png"
            lower.contains(".gif") -> "image/gif"
            lower.contains(".webp") -> "image/webp"
            else -> "image/jpeg"
        }
    }

    /** 根据 MIME 推断扩展名 */
    private fun extFromMime(mime: String): String = mime.substringAfter("/", "jpg")

    /** 显示状态提示（文字淡换；错误时轻抖一下） */
    private fun showStatus(text: String, isError: Boolean = false) {
        val color = ContextCompat.getColor(
            requireContext(),
            if (isError) R.color.error_red else R.color.neon_green,
        )
        // 先抖动再淡换，避免互相取消
        if (isError) {
            AnimUtils.shake(binding.tvStatus)
        }
        AnimUtils.swapText(binding.tvStatus) {
            if (_binding != null) {
                binding.tvStatus.text = text
                binding.tvStatus.setTextColor(color)
            }
        }

        if (binding.cardResult.visibility == View.VISIBLE) {
            AnimUtils.fadeOut(binding.cardResult, AnimUtils.FAST)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pulseAnimator?.cancel()
        pulseAnimator = null
        _binding = null
    }
}
