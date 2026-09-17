package org.shuttlelab.calendar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 日历页:一张卡片装月历,选中某天时在它下面摊开详情卡。
 *
 * 与 Web 端首页(`app/page.tsx`)相同的结构:一层 `bg-card rounded-xl border shadow-sm` 包住月历。
 * Web 那一页还有页眉、页脚、外链、SEO 文案 —— 按需求全部去掉,只留核心。
 *
 * EN: the calendar page — one card holding the month, with the detail card unfolding beneath it
 * when a day is selected. Same structure as the web's home page (a bg-card rounded-xl border
 * shadow-sm wrapper around the month), minus its header, footer, cross-links and SEO copy, which
 * were asked to go: the core only.
 */
@Composable
fun CalendarScreen(vm: CalendarViewModel) {
    val scroll = rememberScrollState()
    val reduceMotion = rememberReduceMotion()
    val selected = vm.selectedDate
    // 详情卡里显示哪一天:选中时跟着选中走,取消选中后**留在原处**直到收起动画结束。
    // EN: which day the detail shows — the selection while there is one, then STAYS there until
    // the exit animation has finished.
    val lastSelected = remember { mutableStateOf(vm.today) }
    LaunchedEffect(selected) { selected?.let { lastSelected.value = it } }

    /*
     * 选中某天后把详情滚进视野。
     *
     * 详情卡在月历**下方**(与 Web 一致),而六行格子在手机上已经占掉大半屏,于是"点了某天,
     * 详情出现在屏幕外"是这个布局的必然后果 —— 不处理的话点击看起来毫无反应,用户不会想到
     * 要往下滑。
     *
     * 同时依赖 `scroll.maxValue`:详情卡是展开动画出现的,选中那一刻它还没有高度,此时滚到
     * maxValue 等于没滚。maxValue 随展开变化,每次变化都重新滚一次,最后落在底部。
     *
     * 关掉系统动画时用 scrollTo 而不是 animateScrollTo —— 这条滚动也是动画。
     *
     * EN: scroll the detail into view. The card sits BELOW the month (as on the web) while six rows
     * of cells already fill most of a phone, so "tap a day and the detail appears off-screen" is an
     * inevitable consequence of that layout — left alone, the tap looks like it did nothing and
     * nobody thinks to scroll. It also keys on scroll.maxValue because the card arrives with an
     * expand animation and has no height at the moment of selection, so scrolling to maxValue then
     * would go nowhere; maxValue grows as it expands and each change re-scrolls, landing at the
     * bottom. With system animations off it jumps instead of animating — this scroll is animation too.
     */
    LaunchedEffect(selected, scroll.maxValue) {
        if (selected != null && scroll.maxValue > 0) {
            if (reduceMotion) scroll.scrollTo(scroll.maxValue) else scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                MonthCalendar(vm)
            }
        }

        // 详情卡:展开/收起而不是直接出现 —— 它出现在已有内容的下方,突然多出一块会把
        // 整页往上顶一下,展开则让这次变化是可被跟随的。
        // EN: the detail card expands rather than appearing outright: it arrives below existing
        // content, and a block popping in shoves the page, whereas expanding makes the change
        // followable.
        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn(Motion.elementSpec()) + expandVertically(Motion.elementSpec()),
            exit = fadeOut(Motion.elementSpec()) + shrinkVertically(Motion.elementSpec()),
        ) {
            // 收起动画期间 selected 已经是 null,所以留住最后一次非空值,否则收起时卡片会闪空。
            // EN: selected is already null while the exit animation runs, so the last non-null
            // value is held or the card would flash empty on the way out.
            DayDetail(vm, lastSelected.value)
        }
    }
}
