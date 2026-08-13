package com.nfcgame.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nfcgame.app.databinding.ActivityMainBinding
import com.nfcgame.app.nfc.NfcHelper
import com.nfcgame.app.ui.enroll.EnrollFragment
import com.nfcgame.app.ui.query.QueryFragment

/**
 * 主 Activity：承载查询模式与录入模式两个 Fragment，底部导航切换。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 全局 NFC 助手，供两个 Fragment 共用 */
    lateinit var nfcHelper: NfcHelper
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nfcHelper = NfcHelper(this)

        // 首次进入弹出隐私提示
        showPrivacyDialogOnce()

        // 默认进入查询模式
        if (savedInstanceState == null) {
            switchTo(QueryFragment())
        }

        // 底部导航切换
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_query -> switchTo(QueryFragment())
                R.id.nav_enroll -> switchTo(EnrollFragment())
            }
            true
        }
    }

    /** 切换 Fragment */
    private fun switchTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /**
     * 隐私提示：首次进入弹一次，之后不再重复。
     */
    private fun showPrivacyDialogOnce() {
        val prefs: SharedPreferences =
            getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("privacy_agreed", false)) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.privacy_title)
            .setMessage(R.string.privacy_message)
            .setPositiveButton(R.string.privacy_agree) { _, _ ->
                prefs.edit().putBoolean("privacy_agreed", true).apply()
            }
            .setCancelable(false)
            .show()
    }
}
