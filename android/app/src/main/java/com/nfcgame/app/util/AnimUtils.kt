package com.nfcgame.app.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

/**
 * 动画工具：为全应用提供统一、流畅、克制的动效。
 *
 * 全部基于系统 Animator / ViewPropertyAnimator，无额外依赖；
 * 系统「移除动画」设置（ANIMATOR_DURATION_SCALE = 0）会自动被尊重——
 * 无限循环的动画会直接跳过，一次性动画立即完成。
 */
object AnimUtils {

    /** 快捷时长 */
    const val FAST = 160L
    const val NORMAL = 280L
    const val SLOW = 420L

    private val decelerate = DecelerateInterpolator()
    private val easeInOut = AccelerateDecelerateInterpolator()

    /** 系统是否允许动画（用户可能在无障碍中关闭动画） */
    fun animationsEnabled(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f

    /** 淡入 + 轻微上浮 */
    fun fadeInUp(view: View, duration: Long = NORMAL, delay: Long = 0L, distance: Float = 24f) {
        view.alpha = 0f
        view.translationY = distance
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(decelerate)
            .start()
    }

    /** 淡入（无位移） */
    fun fadeIn(view: View, duration: Long = NORMAL, delay: Long = 0L) {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(decelerate)
            .start()
    }

    /** 淡出（默认结束后隐藏） */
    fun fadeOut(
        view: View,
        duration: Long = FAST,
        endGone: Boolean = true,
        onEnd: (() -> Unit)? = null,
    ) {
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(decelerate)
            .withEndAction {
                if (endGone) view.visibility = View.GONE
                onEnd?.invoke()
            }
            .start()
    }

    /** 弹性弹出（轻微 overshoot，适合图标 / 卡片） */
    fun popIn(view: View, duration: Long = NORMAL, delay: Long = 0L, from: Float = 0.72f) {
        view.alpha = 0f
        view.scaleX = from
        view.scaleY = from
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(duration)
            .setStartDelay(delay)
            .setInterpolator(OvershootInterpolator(2.2f))
            .start()
    }

    /** 列表交错入场（上浮 + 淡入，逐个依次出现） */
    fun staggerIn(
        views: List<View>,
        duration: Long = NORMAL,
        step: Long = 70L,
        delay: Long = 0L,
    ) {
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 28f
            view.visibility = View.VISIBLE
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(duration)
                .setStartDelay(delay + index * step)
                .setInterpolator(decelerate)
                .start()
        }
    }

    /**
     * 无限呼吸脉冲（扫描圆环等待态）。
     * @return 动画句柄（可在 onDestroyView 中取消）；系统禁用动画时返回 null。
     */
    fun pulse(
        view: View,
        context: Context,
        scale: Float = 1.15f,
        duration: Long = 950L,
    ): ValueAnimator? {
        view.scaleX = 1f
        view.scaleY = 1f
        if (!animationsEnabled(context)) return null

        val animator = ValueAnimator.ofFloat(1f, scale, 1f)
        animator.duration = duration
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = easeInOut
        animator.addUpdateListener {
            val s = it.animatedValue as Float
            view.scaleX = s
            view.scaleY = s
        }
        animator.start()
        return animator
    }

    /** 扫描成功爆发：放大 + 变淡，结束后复位（可接续呼吸脉冲） */
    fun scanBurst(view: View, onEnd: (() -> Unit)? = null) {
        view.animate().cancel()
        view.animate()
            .scaleX(1.45f)
            .scaleY(1.45f)
            .alpha(0.35f)
            .setDuration(360L)
            .setInterpolator(decelerate)
            .withEndAction {
                view.scaleX = 1f
                view.scaleY = 1f
                view.alpha = 1f
                onEnd?.invoke()
            }
            .start()
    }

    /** 错误抖动（轻微水平晃动，提示失败） */
    fun shake(view: View) {
        view.animate().cancel()
        view.translationX = 0f
        ObjectAnimator.ofFloat(view, "translationX", 0f, -10f, 8f, -6f, 4f, -2f, 0f)
            .apply { duration = SLOW }
            .start()
    }

    /** 吸引注意：轻微缩小 + 闪亮回弹（如读到卡片时的输入框） */
    fun attention(view: View) {
        view.animate().cancel()
        view.alpha = 0.45f
        view.scaleX = 0.98f
        view.scaleY = 0.98f
        view.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(OvershootInterpolator(1.6f))
            .start()
    }

    /** 纵向展开（height 0 → wrap，配合淡入，用于折叠区域） */
    fun expandVertically(view: View, duration: Long = NORMAL) {
        val parent = view.parent as? View ?: return
        val lp = view.layoutParams

        // 先按 wrap 测量出完整高度
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
        view.layoutParams = lp
        view.measure(
            View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val targetHeight = view.measuredHeight
        if (targetHeight <= 0) {
            view.visibility = View.VISIBLE
            view.alpha = 1f
            return
        }

        lp.height = 0
        view.layoutParams = lp
        view.visibility = View.VISIBLE
        view.alpha = 0f

        val animator = ValueAnimator.ofInt(0, targetHeight)
        animator.duration = duration
        animator.interpolator = decelerate
        animator.addUpdateListener {
            lp.height = it.animatedValue as Int
            view.layoutParams = lp
            view.alpha = it.animatedFraction
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = lp
                view.alpha = 1f
            }
        })
        animator.start()
    }

    /** 纵向收起（height → 0，结束后隐藏） */
    fun collapseVertically(view: View, duration: Long = NORMAL, onEnd: (() -> Unit)? = null) {
        val lp = view.layoutParams
        val startHeight = view.height
        if (startHeight <= 0) {
            view.visibility = View.GONE
            onEnd?.invoke()
            return
        }

        val animator = ValueAnimator.ofInt(startHeight, 0)
        animator.duration = duration
        animator.interpolator = decelerate
        animator.addUpdateListener {
            lp.height = it.animatedValue as Int
            view.layoutParams = lp
            view.alpha = 1f - it.animatedFraction
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                view.layoutParams = lp
                view.alpha = 1f
                view.visibility = View.GONE
                onEnd?.invoke()
            }
        })
        animator.start()
    }

    /** 条目移除（向下收起 + 淡出，结束后回调） */
    fun removeItem(view: View, duration: Long = 240L, onEnd: (() -> Unit)? = null) {
        view.animate().cancel()
        view.pivotY = 0f
        view.animate()
            .scaleY(0.08f)
            .alpha(0f)
            .translationY(-10f)
            .setDuration(duration)
            .setInterpolator(decelerate)
            .withEndAction {
                view.scaleY = 1f
                view.alpha = 1f
                view.translationY = 0f
                onEnd?.invoke()
            }
            .start()
    }

    /** 按压反馈：按下轻微缩小、抬起弹性回弹（不拦截点击事件） */
    fun pressScale(view: View, downScale: Float = 0.96f) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(downScale).scaleY(downScale).setDuration(90L).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(150L)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .start()
                }
            }
            false
        }
    }

    /** 文字淡换：先淡出，替换内容后淡入 */
    fun swapText(view: View, update: () -> Unit) {
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(90L)
            .withEndAction {
                update()
                view.animate()
                    .alpha(1f)
                    .setDuration(180L)
                    .setInterpolator(decelerate)
                    .start()
            }
            .start()
    }
}
