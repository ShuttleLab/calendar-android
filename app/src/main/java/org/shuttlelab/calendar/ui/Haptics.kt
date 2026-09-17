package org.shuttlelab.calendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * 触感反馈的统一入口(与 secretary-android 同一套分级)。
 *
 * 值得单独一层的理由:震动的**语义**必须一致,否则它比没有更糟。同一种震动既表示"翻了一页"
 * 又表示"选中了某天",用户就学不到任何东西,只感觉手机在乱抖。这里按平台语义最贴的档位分:
 *
 *   [tap]     普通点击 —— VirtualKey,输入法按键那一档。
 *   [select]  在互斥选项间移动 —— 切语言/主题 chip、**翻月**。SegmentTick,比 tap 更轻。
 *   [confirm] 事情成了 —— 回到今天、刷新到了新数据。Confirm。
 *   [toggle]  开关翻转 —— 按状态给 ToggleOn / ToggleOff。
 *
 * 翻月用最轻的 [select] 而不是 [tap]:它是这个应用里按得最频繁的动作(连点找月份),重一点点
 * 就会变成噪音;而"移到了另一格"本来也正是 SegmentTick 表达的意思。
 *
 * 两层开关都真实生效:系统的「触感反馈」总开关由 `performHapticFeedback` 自己遵守(关掉它,
 * 这里的调用什么也不做);App 内这个开关的意义在于,你可能想留着输入法的打字震动,却觉得
 * App 里的太多 —— 系统开关做不到这种区分。
 *
 * 用的是 Compose UI 1.8 起的语义化档位;映射集中在此,将来要调整只改这一个文件。
 *
 * EN: one place for haptics, the same grading as secretary-android. It deserves a layer because
 * the MEANING of a buzz must be consistent, or it is worse than silence: if one vibration says
 * both "page turned" and "day selected", the user learns nothing and just feels a phone twitching.
 * Month changes take the lightest [select] level rather than [tap] — it is the most frequently
 * repeated action in this app (tapping through months), where anything heavier becomes noise, and
 * "moved to another segment" is exactly what SegmentTick means.
 *
 * Two switches, both real: the system's own touch-feedback toggle is honoured by
 * performHapticFeedback itself, while the in-app switch exists because you may want to keep the
 * keyboard's typing buzz yet find an app's action haptics excessive — a distinction the system
 * switch cannot express. These semantic levels exist from Compose UI 1.8.
 */
class Haptics(private val feedback: HapticFeedback, private val enabled: Boolean) {
    /** 一次普通点击 —— 输入法按键那一档。 */
    fun tap() = perform(HapticFeedbackType.VirtualKey)

    /** 在一组互斥选项之间移动:切 chip、翻月。比 tap 更轻。 */
    fun select() = perform(HapticFeedbackType.SegmentTick)

    /** 事情成了:回到今天、刷新拿到新数据。 */
    fun confirm() = perform(HapticFeedbackType.Confirm)

    fun toggle(on: Boolean) =
        perform(if (on) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)

    /**
     * 把 onClick 包成"先轻震一下再执行"。存在的理由是调用点的数量:每处写两行既啰嗦,
     * 也让"忘记加"变成常态;而它读起来仍是 `onClick = haptics.clicking { … }`,一眼看得出有反馈。
     * EN: wraps an onClick so it buzzes first. The reason is the number of call sites: two lines at
     * each is noisy and makes forgetting the norm, while this still reads as
     * `onClick = haptics.clicking { … }`, so the feedback is visible at a glance.
     */
    fun clicking(action: () -> Unit): () -> Unit = { tap(); action() }

    /** 同 [clicking],但用 [select] 的轻档 —— 给 chip 与翻月。 */
    fun selecting(action: () -> Unit): () -> Unit = { select(); action() }

    private fun perform(type: HapticFeedbackType) {
        if (enabled) feedback.performHapticFeedback(type)
    }
}

/**
 * 取当前的触感反馈句柄。开关关闭时返回的句柄所有方法都是空操作 —— 让"开关生效"只发生在**一处**,
 * 而不是在每个调用点写 `if (hapticsEnabled)`:那种写法漏掉一处就是一个关不掉的震动,而且是静默的。
 * EN: the current handle. With the switch off every method is a no-op, so honouring the setting
 * happens in ONE place rather than as an `if` at each call site, where a single omission is a buzz
 * that cannot be turned off — and a silent one at that.
 */
@Composable
fun rememberHaptics(vm: CalendarViewModel): Haptics {
    val feedback = LocalHapticFeedback.current
    val enabled = vm.hapticsEnabled
    return remember(feedback, enabled) { Haptics(feedback, enabled) }
}
