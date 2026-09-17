package org.shuttlelab.calendar.ui

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 动效的共同参数(与 secretary-android 同一套),以及"用户要求关闭动画"时的退让。
 *
 * 集中在一处的理由:动效有说服力靠的是**一致**。时长和曲线散落在各页面里,每处随手写一个
 * 300、一个 easeInOut,合起来就是一堆彼此不搭的运动 —— 那比没有动效更显得廉价。
 *
 * EN: shared motion parameters (the same set as secretary-android) plus the concession for "the
 * user asked for no animations". One place, because what makes motion convincing is CONSISTENCY:
 * durations and curves scattered across screens add up to movements that do not match, which
 * reads cheaper than having no motion at all.
 */
object Motion {
    /** 页面切换时长。M3 建议 200–500ms,导航取偏短的一端:用户在页面之间是赶路,不是欣赏转场。 */
    const val PAGE = 260

    /** 元素进出、淡入淡出等小尺度动效。 */
    const val ELEMENT = 180

    /**
     * 翻月时长。比 [ELEMENT] 再短一点:翻月是**连续动作** —— 用户常常连点三四下往前找某个月,
     * 每一下都等 180ms 就会拖手。这一档只需要交代"内容往哪个方向走了"。
     * EN: month-change duration, shorter still than [ELEMENT] because changing month is a
     * REPEATED action — people tap forward three or four times looking for a month, and 180ms
     * each time drags. This step only has to say which way the content went.
     */
    const val MONTH = 140

    /** 标准减速曲线:进场快、收尾缓,M3 里"某物到位"的默认表达。 */
    val Emphasized: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)

    /** 退场曲线:起步就快,因为离开的东西不需要被看清。 */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

    fun <T> pageSpec() = tween<T>(durationMillis = PAGE, easing = Emphasized)
    fun <T> exitSpec() = tween<T>(durationMillis = PAGE, easing = Accelerate)
    fun <T> elementSpec() = tween<T>(durationMillis = ELEMENT, easing = Emphasized)
    fun <T> monthSpec() = tween<T>(durationMillis = MONTH, easing = Emphasized)
}

/**
 * 系统里是否关掉了动画。
 *
 * 这不是可选的讲究:开发者选项的「动画时长缩放 = 关闭」与无障碍设置的「移除动画」都会把
 * `ANIMATOR_DURATION_SCALE` 置 0 —— 有前庭功能障碍的人靠这个开关避免头晕,而 Compose **不会**
 * 替我们遵守它,自己写的 tween 照样跑完写死的时长。所以读一次系统值,接回我们的动效。
 *
 * 只在组合时读一次:改这个设置要去系统设置页,回来时 Activity 已经重建。
 *
 * EN: whether the system has animations off. Not an optional nicety — "animation duration scale:
 * off" and accessibility's "remove animations" both set ANIMATOR_DURATION_SCALE to 0, which people
 * with vestibular disorders rely on, and Compose does NOT honour it for us: a hand-written tween
 * still runs its hard-coded duration. Read once per composition is enough, since changing it means
 * a trip to system settings and the activity is recreated on the way back.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val ctx = LocalContext.current
    return remember {
        Settings.Global.getFloat(
            ctx.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
