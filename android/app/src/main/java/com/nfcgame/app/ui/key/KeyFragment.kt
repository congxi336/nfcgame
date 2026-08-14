package com.nfcgame.app.ui.key

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nfcgame.app.R
import com.nfcgame.app.databinding.DialogAddKeyBinding
import com.nfcgame.app.databinding.FragmentKeyBinding
import com.nfcgame.app.databinding.ItemKeyBinding
import com.nfcgame.app.util.KeyEntry
import com.nfcgame.app.util.KeyManager

/**
 * 密钥页：管理本地密钥（添加/备注/分发/删除/刷新）。
 * 进入时需通过生物识别（指纹/面容/锁屏 PIN）验证，验证通过后才显示密钥。
 */
class KeyFragment : Fragment() {

    private var _binding: FragmentKeyBinding? = null
    private val binding get() = _binding!!

    /** 本次进入是否已通过身份验证 */
    private var verified = false

    private val executor by lazy { ContextCompat.getMainExecutor(requireContext()) }

    private var biometricPrompt: BiometricPrompt? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentKeyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnAddKey.setOnClickListener { showAddDialog() }
        binding.btnRefresh.setOnClickListener { refresh() }
        binding.btnUnlock.setOnClickListener { authenticate() }
    }

    override fun onResume() {
        super.onResume()
        if (verified) {
            showKeys()
        } else {
            authenticate()
        }
    }

    /** 生物识别验证 */
    private fun authenticate() {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

        val status = BiometricManager.from(requireContext()).canAuthenticate(authenticators)
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            // 设备不支持 / 未录入任何凭证：降级直接显示，并提示
            verified = true
            showKeys()
            if (status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
                status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
            ) {
                Toast.makeText(requireContext(), R.string.key_auth_unsupported, Toast.LENGTH_LONG).show()
            }
            return
        }

        showLocked()

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.key_auth_title))
            .setSubtitle(getString(R.string.key_auth_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()

        val prompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                verified = true
                showKeys()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                showLocked()
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    Toast.makeText(requireContext(), R.string.key_auth_cancel, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationFailed() {
                // 单次识别失败，可重试，不额外处理
            }
        })
        biometricPrompt = prompt
        prompt.authenticate(promptInfo)
    }

    /** 显示锁定态 */
    private fun showLocked() {
        binding.llLock.visibility = View.VISIBLE
        binding.scrollKeys.visibility = View.GONE
        binding.tvEmpty.visibility = View.GONE
    }

    /** 显示密钥（列表或空提示） */
    private fun showKeys() {
        binding.llLock.visibility = View.GONE
        refresh()
    }

    /** 重新加载密钥列表 */
    private fun refresh() {
        val keys = KeyManager.getKeys(requireContext())
        binding.keyContainer.removeAllViews()
        if (keys.isEmpty()) {
            binding.scrollKeys.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.scrollKeys.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            keys.forEach { entry -> binding.keyContainer.addView(createKeyView(entry)) }
        }
    }

    /** 构建单个密钥条目视图 */
    private fun createKeyView(entry: KeyEntry): View {
        val item = ItemKeyBinding.inflate(layoutInflater, binding.keyContainer, false)
        item.tvRemark.text = entry.remark.ifBlank { getString(R.string.key_unnamed) }
        item.tvKeyMask.text = maskKey(entry.key)
        item.btnExport.setOnClickListener { export(entry) }
        item.btnDelete.setOnClickListener { delete(entry) }
        return item.root
    }

    /** 脱敏显示密钥（只显示首尾） */
    private fun maskKey(key: String): String {
        if (key.length <= 8) return "****"
        return key.take(4) + "…" + key.takeLast(4)
    }

    /** 分发：复制到剪贴板 + 弹出系统分享 */
    private fun export(entry: KeyEntry) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("key", entry.key))
        Toast.makeText(requireContext(), R.string.key_copied, Toast.LENGTH_SHORT).show()

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, entry.key)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.key_share_title)))
    }

    /** 删除密钥 */
    private fun delete(entry: KeyEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.key_delete)
            .setMessage("确定删除密钥「${entry.remark.ifBlank { getString(R.string.key_unnamed) }}」吗？")
            .setPositiveButton(R.string.key_delete) { _, _ ->
                KeyManager.deleteKey(requireContext(), entry.id)
                Toast.makeText(requireContext(), R.string.key_deleted, Toast.LENGTH_SHORT).show()
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** 添加密钥对话框 */
    private fun showAddDialog() {
        val dialogBinding = DialogAddKeyBinding.inflate(layoutInflater)
        dialogBinding.btnRandom.setOnClickListener {
            dialogBinding.etKey.setText(KeyManager.generateKey())
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.key_add_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.key_add) { _, _ ->
                val key = dialogBinding.etKey.text?.toString()?.trim().orEmpty()
                val remark = dialogBinding.etRemark.text?.toString()?.trim().orEmpty()
                if (key.isEmpty()) {
                    Toast.makeText(requireContext(), R.string.key_need_input, Toast.LENGTH_SHORT).show()
                } else {
                    KeyManager.addKey(requireContext(), key, remark)
                    Toast.makeText(requireContext(), R.string.key_added, Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        biometricPrompt?.cancelAuthentication()
        _binding = null
    }
}
