package com.nfcgame.app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nfcgame.app.databinding.ActivityMainBinding
import com.nfcgame.app.nfc.NfcHelper
import com.nfcgame.app.ui.enroll.EnrollFragment
import com.nfcgame.app.ui.key.KeyFragment
import com.nfcgame.app.ui.query.QueryFragment

/**
 * 主 Activity：承载查询、录入、密钥三个 Fragment，底部导航切换。
 * 切换时按导航方向播放滑入 / 滑出转场动画。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    /** 全局 NFC 助手，供两个 Fragment 共用 */
    lateinit var nfcHelper: NfcHelper
        private set

    /** 当前底部导航选中项（决定切换动画方向） */
    private var currentTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        nfcHelper = NfcHelper(this)

        // 首次进入弹出隐私提示
        showPrivacyDialogOnce()

        // 默认进入查询模式，并播放启动入场动画
        if (savedInstanceState == null) {
            switchTo(QueryFragment(), 0)
            animateEntrance()
        }

        // 底部导航切换
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_query -> switchTo(QueryFragment(), 0)
                R.id.nav_enroll -> switchTo(EnrollFragment(), 1)
                R.id.nav_key -> switchTo(KeyFragment(), 2)
            }
            true
        }
    }

    /**
     * 切换 Fragment：根据目标位置与当前位置的先后关系决定滑入方向，
     * 让底部导航的切换有「前进 / 后退」的空间方向感。
     */
    private fun switchTo(fragment: Fragment, targetIndex: Int) {
        val forward = targetIndex >= currentTabIndex
        currentTabIndex = targetIndex

        val enterAnim = if (forward) R.anim.slide_in_right else R.anim.slide_in_left
        val exitAnim = if (forward) R.anim.slide_out_left else R.anim.slide_out_right

        supportFragmentManager.beginTransaction()
            .setCustomAnimations(enterAnim, exitAnim)
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    /** 启动入场：内容区淡入上浮，底部导航自下而上滑入 */
    private fun animateEntrance() {
        val interpolator = DecelerateInterpolator()

        binding.fragmentContainer.alpha = 0f
        binding.fragmentContainer.translationY = 24f
        binding.fragmentContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(380L)
            .setStartDelay(60L)
            .setInterpolator(interpolator)
            .start()

        binding.bottomNav.alpha = 0f
        binding.bottomNav.translationY = 64f
        binding.bottomNav.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(380L)
            .setStartDelay(140L)
            .setInterpolator(interpolator)
            .start()
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
