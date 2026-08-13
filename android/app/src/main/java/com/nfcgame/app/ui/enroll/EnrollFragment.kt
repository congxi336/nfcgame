package com.nfcgame.app.ui.enroll

import android.net.Uri
import android.os.Bundle
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nfcgame.app.BuildConfig
import com.nfcgame.app.MainActivity
import com.nfcgame.app.R
import com.nfcgame.app.databinding.FragmentEnrollBinding
import com.nfcgame.app.network.HttpClient
import com.nfcgame.app.network.NfcRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 录入模式：读取卡片 UID + 手动输入标题/内容/图片，保存到服务器。
 * 图片支持两种方式：手动填 URL，或从相册选择后自动上传。
 */
class EnrollFragment : Fragment() {

    private var _binding: FragmentEnrollBinding? = null
    private val binding get() = _binding!!

    private val apiService by lazy { HttpClient.getApiService(requireContext()) }

    /** 当前读取到的 UID（触碰后填入） */
    private var currentUid: String? = null

    /** 从相册选择图片（返回 URI） */
    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { onImagePicked(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentEnrollBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val helper = (requireActivity() as MainActivity).nfcHelper
        helper.onUidRead = { uid ->
            currentUid = uid
            binding.etUid.setText(uid)
            Toast.makeText(requireContext(), "已读取卡片: $uid", Toast.LENGTH_SHORT).show()
        }

        // 触碰读卡按钮
        binding.btnTapCard.setOnClickListener {
            if (!helper.isSupported()) {
                Toast.makeText(requireContext(), R.string.nfc_not_supported, Toast.LENGTH_SHORT).show()
            } else if (!helper.isEnabled()) {
                Toast.makeText(requireContext(), R.string.nfc_disabled, Toast.LENGTH_SHORT).show()
            } else {
                binding.tvEnrollStatus.text = "请将卡片贴近手机背面…"
            }
        }

        // 从相册选择图片
        binding.btnPickImage.setOnClickListener { imagePicker.launch("image/*") }

        // 保存按钮
        binding.btnSave.setOnClickListener { save() }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).nfcHelper.startReading()
    }

    override fun onPause() {
        super.onPause()
        (requireActivity() as MainActivity).nfcHelper.stopReading()
    }

    /** 相册选图回调：预览 + 上传 */
    private fun onImagePicked(uri: Uri) {
        // 显示本地预览
        binding.ivPreview.setImageURI(uri)
        binding.ivPreview.visibility = View.VISIBLE
        binding.tvEnrollStatus.text = "正在上传图片…"
        uploadImage(uri)
    }

    /** 上传图片到服务器，成功后把完整 URL 填入输入框 */
    private fun uploadImage(uri: Uri) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { doUpload(uri) }
            when (result) {
                is NfcRepository.Result.Success -> {
                    val fullUrl = BuildConfig.SERVER_URL.trimEnd('/') + result.data
                    binding.etImageUrl.setText(fullUrl)
                    binding.tvEnrollStatus.text = "图片上传成功"
                    Toast.makeText(requireContext(), R.string.enroll_image_uploaded, Toast.LENGTH_SHORT).show()
                }
                is NfcRepository.Result.Error -> {
                    binding.tvEnrollStatus.text = "图片上传失败：${result.message}"
                    Toast.makeText(requireContext(), "上传失败：${result.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /** 在 IO 线程读取 URI 字节并上传 */
    private suspend fun doUpload(uri: Uri): NfcRepository.Result<String> {
        return try {
            val resolver = requireContext().contentResolver
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return NfcRepository.Result.Error("无法读取所选图片")
            val mimeType = resolver.getType(uri) ?: "image/jpeg"
            val ext = mimeType.substringAfter("/", "jpg")
            val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
            val part = MultipartBody.Part.createFormData("file", "image.$ext", requestBody)
            NfcRepository.uploadImage(apiService, part)
        } catch (e: Exception) {
            NfcRepository.Result.Error(e.message ?: "上传失败")
        }
    }

    /** 保存到服务器 */
    private fun save() {
        val uid = currentUid ?: binding.etUid.text?.toString()?.trim().orEmpty()
        val title = binding.etTitle.text?.toString()?.trim().orEmpty()
        val content = binding.etContent.text?.toString()?.trim().orEmpty()
        val imageUrl = binding.etImageUrl.text?.toString()?.trim().orEmpty()

        if (uid.isEmpty()) {
            Toast.makeText(requireContext(), R.string.enroll_need_card, Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(requireContext(), R.string.enroll_need_fields, Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSave.isEnabled = false
        binding.tvEnrollStatus.text = "正在保存…"

        lifecycleScope.launch {
            val result = NfcRepository.saveInfo(
                api = apiService,
                uid = uid,
                title = title,
                content = content,
                imageUrl = imageUrl,
            )
            when (result) {
                is NfcRepository.Result.Success -> {
                    Toast.makeText(requireContext(), R.string.enroll_success, Toast.LENGTH_SHORT).show()
                    binding.tvEnrollStatus.text = "保存成功"
                    resetForm()
                }
                is NfcRepository.Result.Error -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    binding.tvEnrollStatus.text = result.message
                }
            }
            binding.btnSave.isEnabled = true
        }
    }

    /** 清空表单 */
    private fun resetForm() {
        currentUid = null
        binding.etUid.text?.clear()
        binding.etTitle.text?.clear()
        binding.etContent.text?.clear()
        binding.etImageUrl.text?.clear()
        binding.ivPreview.setImageDrawable(null)
        binding.ivPreview.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
