package org.shuttlelab.calendar.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.shuttlelab.calendar.data.Lang
import java.time.YearMonth

/**
 * 月份选择器:一条**跨年连续**的月份列表,点哪个月就跳到哪个月。
 *
 * 为什么是连续列表而不是"一年 12 格 + 左右切年":用户要去的月份往往在附近几个月到一两年内,
 * 而"切年"会把这种最常见的移动拆成两步(先换年、再选月),还得先想清楚目标在哪一年。连续列表
 * 里"往回三个月"和"往回十四个月"是同一个动作,只是手指多滑一点 —— 跨年这件事不需要被专门操作。
 *
 * 年份是**吸顶**的分组标题:滑到哪一年,顶上就写着哪一年。没有它,一列「1月…12月」滑起来会
 * 迅速失去坐标 —— 这正是连续列表唯一的代价,而吸顶标题把这个代价抵消掉。
 *
 * 范围取 1900–2100:不是随手定的,而是农历数据的覆盖范围(见 data/Lunar.kt)。让选择器能跳到
 * 一个农历为空的月份,等于允许用户走到一个半残的界面里去。真要更远,月份箭头不受此限。
 *
 * EN: a month picker — one continuous, cross-year list; tapping a month jumps to it. Continuous
 * rather than "twelve cells with a year switcher" because the month someone wants is usually within
 * a few months to a year or two, and a year switcher splits that commonest move into two steps
 * (change year, then pick) while requiring them to work out which year the target is in. In a
 * continuous list, "back three months" and "back fourteen months" are the same gesture with a
 * longer swipe; crossing a year needs no separate act.
 *
 * Years are STICKY group headers: whichever year you have scrolled into is written at the top.
 * Without them a column of "January…December" loses its coordinates almost immediately — the one
 * cost of a continuous list, which the sticky header cancels out.
 *
 * The 1900–2100 range is not arbitrary: it is what the lunar tables cover (data/Lunar.kt). Letting
 * the picker jump to a month with no lunar data would be letting someone walk into a half-blank
 * screen. The month arrows are not bounded by this.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MonthPickerSheet(vm: CalendarViewModel) {
    if (!vm.monthPickerOpen) return

    val haptics = rememberHaptics(vm)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scheme = MaterialTheme.colorScheme

    // 扁平化成一串条目,这样 LazyColumn 的下标可以直接算出来(定位到当前月要用)。
    // 2413 个条目一次性建好:一个 YearMonth 列表的构造成本远低于每次滚动去推算。
    // EN: flattened into one list so a LazyColumn index can be computed directly, which is what
    // positioning on the current month needs. Building all 2413 entries once costs far less than
    // deriving them during scrolling.
    val entries = remember {
        buildList {
            for (year in FIRST_YEAR..LAST_YEAR) {
                add(Entry.YearHeader(year))
                for (month in 1..12) add(Entry.Month(YearMonth.of(year, month)))
            }
        }
    }

    // 打开时直接停在当前显示的月份上,而不是列表顶端(1900 年 1 月)。选择器的起点应该是
    // "你现在在哪儿",往前往后都只需要少量滑动。减 1 是为了让那一年的标题也露出来。
    // EN: open positioned on the month currently shown rather than at the top of the list (January
    // 1900). A picker should start from where you are, so both directions are a short swipe. The
    // minus one keeps that year's header in view.
    val initialIndex = remember(vm.viewMonth) {
        entries.indexOfFirst { it is Entry.Month && it.value == vm.viewMonth }
            .let { if (it > 0) it - 1 else 0 }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    ModalBottomSheet(
        onDismissRequest = { vm.closeMonthPicker() },
        sheetState = sheetState,
    ) {
        Text(
            Lang.t("Jump to month", "选择月份"),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight(0.72f),
        ) {
            entries.forEach { entry ->
                when (entry) {
                    is Entry.YearHeader -> stickyHeader(key = "y${entry.year}") {
                        Text(
                            if (Lang.isZh) "${entry.year}年" else "${entry.year}",
                            style = MaterialTheme.typography.labelLarge,
                            color = scheme.primary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(scheme.surfaceContainerLow)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        )
                    }

                    is Entry.Month -> item(key = "m${entry.value}") {
                        MonthRow(
                            month = entry.value,
                            isCurrentView = entry.value == vm.viewMonth,
                            isThisMonth = entry.value == YearMonth.from(vm.today),
                            onClick = haptics.selecting { vm.jumpToMonth(entry.value) },
                        )
                    }
                }
            }
        }
    }
}

private const val FIRST_YEAR = 1900
private const val LAST_YEAR = 2100

private sealed interface Entry {
    data class YearHeader(val year: Int) : Entry
    data class Month(val value: YearMonth) : Entry
}

/**
 * 列表里的一行。两种标记各说一件事,不要合并:
 *   - 实心底色 = **正在看的那个月**(点进来之前你在哪儿)
 *   - 小圆点   = **今天所在的月**(真实世界的此刻)
 * 翻到明年三月时这两者不是同一个月,而用户恰恰需要同时知道这两件事。
 *
 * EN: two marks saying two different things, not to be merged — the filled background is the month
 * currently being viewed, the dot is the month containing today. Having paged to next March they
 * are different months, and both are worth knowing at once.
 */
@Composable
private fun MonthRow(
    month: YearMonth,
    isCurrentView: Boolean,
    isThisMonth: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCurrentView) scheme.primaryContainer else scheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            monthLabel(month),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isCurrentView) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isCurrentView) scheme.onPrimaryContainer else scheme.onSurface,
        )
        if (isThisMonth) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
            Text(
                Lang.t("this month", "本月"),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.primary,
            )
        }
    }
}

private val EN_MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * 月份名按界面语言给,不走 `DateTimeFormatter` —— 那个跟随**系统** locale,而界面语言是应用内
 * 偏好,中文界面在英文系统上会冒出 "September"。同 DayDetail 里的星期。
 * EN: month names follow the in-app language rather than DateTimeFormatter, which follows the
 * SYSTEM locale — a Chinese UI on an English system would read "September". Same as the weekday in
 * DayDetail.
 */
private fun monthLabel(month: YearMonth): String =
    if (Lang.isZh) "${month.monthValue}月" else EN_MONTHS[month.monthValue - 1]
