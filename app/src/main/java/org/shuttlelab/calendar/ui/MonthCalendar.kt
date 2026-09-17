package org.shuttlelab.calendar.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.shuttlelab.calendar.data.Holiday
import org.shuttlelab.calendar.data.Lang
import org.shuttlelab.calendar.data.Lunar
import org.shuttlelab.calendar.data.lunarCellLabel
import org.shuttlelab.calendar.ui.theme.LocalCalendarPalette
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历:月份导航 + 星期表头 + 6×7 格子 + 图例。
 *
 * 结构与 Web 端 `components/calendar/month-calendar.tsx` 一一对应,包括那些看起来可以"顺手改好"
 * 的地方,因为它们都是有意的:
 *
 *   - **周一起始**。Web 的表头是「一二三四五六日」,`leadingBlanks = (firstWeekday + 6) % 7`。
 *     Android 的系统惯例是周日起始,但换过来就与网页错开一列,同一个月在两端长得不一样。
 *   - **固定 42 格**(6 行)。不按当月实际行数收缩,所以翻月时表格高度不变、按钮不跳动 ——
 *     Web 写死 `totalCells = 42` 也是这个原因。
 *   - **本月之外的格子留空**,不显示上下月的日期数字。
 *
 * EN: the month view — navigation, weekday header, a 6×7 grid and the legend. The structure mirrors
 * the web's month-calendar.tsx including the parts that look casually improvable, because each is
 * deliberate: MONDAY FIRST (the web's header is 一二三四五六日; Android's convention is Sunday-first,
 * but switching would offset the grid by a column and make the same month look different on the two
 * sides); a FIXED 42 CELLS so the grid's height never changes and the buttons never jump as months
 * change (the web hard-codes totalCells = 42 for the same reason); and cells OUTSIDE THE MONTH LEFT
 * BLANK rather than filled with the neighbouring months' numbers.
 */
@Composable
fun MonthCalendar(vm: CalendarViewModel) {
    val haptics = rememberHaptics(vm)
    val reduceMotion = rememberReduceMotion()
    val month = vm.viewMonth

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- 月份导航 ----
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedIconButton(onClick = haptics.selecting { vm.prevMonth() }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = Lang.t("Previous month", "上月"),
                )
            }
            Text(
                monthTitle(month),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedIconButton(onClick = haptics.selecting { vm.nextMonth() }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = Lang.t("Next month", "下月"),
                )
            }
        }

        // ---- 星期表头 ----
        WeekdayHeader()

        // ---- 格子 ----
        // 翻月时整块网格横向滑动:方向来自 [CalendarViewModel.monthDelta],所以"往后翻"和
        // "往前翻"看起来不一样。位移只用八分之一宽度 —— 整屏平移看起来是在搬运界面,而这里
        // 要的只是"内容往哪边走了"这一条提示。
        // EN: the whole grid slides sideways on a month change, with the direction coming from
        // monthDelta so forward and back differ. The offset is an eighth of the width: a
        // full-width translation looks like the UI is being carted around, while all that is
        // needed is a hint of which way the content went.
        // 左右滑动翻月。
        //
        // Web 上没有这个手势,也不需要:鼠标用户点箭头。但在手机上"翻页靠滑"是肌肉记忆,而这
        // 屏最主要的动作就是翻月 —— 只给两个角落里的箭头,等于让每次翻月都要先瞄准。它不改变
        // 任何显示内容,只是多一条到达同一个动作的路径,所以不算与 Web 的显示不一致。
        //
        // 阈值 56dp:低于这个值,纵向滚动页面时手指的横向抖动会误触发翻月 —— 那种"我在滚动,
        // 月份自己跳了"比没有手势糟得多。方向按内容走:手指向右拖 = 把上一个月拉进来。
        //
        // EN: swipe to change month. The web has no such gesture and needs none — a mouse user
        // clicks the arrows — but on a phone "swipe to turn the page" is muscle memory, and
        // changing month is this screen's main action: offering only two arrows in the corners
        // makes every month change an act of aiming. It changes nothing about what is displayed,
        // it only adds a second route to the same action.
        // The 56dp threshold: below it, the sideways wobble of a finger scrolling the page
        // vertically triggers a month change — and "I was scrolling and the month jumped" is far
        // worse than having no gesture. The direction follows the content: dragging right pulls
        // the previous month in.
        val dragX = remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.pointerInput(Unit) {
                val threshold = 56.dp.toPx()
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragX.floatValue > threshold -> {
                                haptics.select()
                                vm.prevMonth()
                            }
                            dragX.floatValue < -threshold -> {
                                haptics.select()
                                vm.nextMonth()
                            }
                        }
                        dragX.floatValue = 0f
                    },
                    onDragCancel = { dragX.floatValue = 0f },
                ) { change, dragAmount ->
                    dragX.floatValue += dragAmount
                    change.consume()
                }
            },
        ) {
            AnimatedContent(
                targetState = month,
                transitionSpec = {
                    if (reduceMotion) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        val dir = if (vm.monthDelta >= 0) 1 else -1
                        (slideInHorizontally(Motion.monthSpec()) { dir * it / 8 } +
                            fadeIn(Motion.monthSpec())) togetherWith
                            (slideOutHorizontally(Motion.monthSpec()) { -dir * it / 8 } +
                                fadeOut(Motion.monthSpec()))
                    }
                },
                label = "month-grid",
            ) { shown ->
                MonthGrid(vm, shown)
            }
        }

        // ---- 数据状态提示 ----
        // 只在"确实没有数据"时说话。加载中不提示:随包快照让首帧就有内容,再挂一行"加载中"
        // 只会让一个已经正确的界面看起来像没准备好。
        // EN: speak up only when there really is no data. No "loading" line: the bundled snapshot
        // means the first frame already has content, and a spinner over a correct screen only makes
        // it look unready.
        if (vm.holiday.isEmpty && !vm.holidayLoading) {
            Text(
                Lang.t(
                    "No holiday data — days off and make-up workdays are not marked. Try Refresh in Settings.",
                    "没有节假日数据 —— 放假与补班暂不标记。可到设置里刷新。",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Legend()
    }
}

/** 标题:中文 `2026年9月`,英文 `9/2026` —— 与 Web 端同一种写法。 */
private fun monthTitle(month: YearMonth): String =
    if (Lang.isZh) "${month.year}年${month.monthValue}月" else "${month.monthValue}/${month.year}"

/**
 * 星期表头。周末两列用 primary 着色 —— 这是格子里"周末是黄底"那条信息在表头的呼应:
 * 表头本身不着色的话,一片黄底格子看起来像是被随机标记的。
 * EN: the weekday header. The two weekend columns are tinted, echoing the yellow weekend cells:
 * without it, a block of yellow cells reads as randomly marked.
 */
@Composable
private fun WeekdayHeader() {
    val labels = if (Lang.isZh) {
        listOf("一", "二", "三", "四", "五", "六", "日")
    } else {
        listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    }
    Row(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            val weekend = i >= 5
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = if (weekend) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** 6×7 的格子。 */
@Composable
private fun MonthGrid(vm: CalendarViewModel, month: YearMonth) {
    val first = month.atDay(1)
    // 周一起始:java 的 DayOfWeek 周一=1,所以前导空格恰好是 value-1。
    // Web 端写成 (getDay() + 6) % 7(那边周日=0),两者等价。
    val leadingBlanks = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val daysInMonth = month.lengthOfMonth()

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        for (row in 0 until 6) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                for (col in 0 until 7) {
                    val index = row * 7 + col
                    val day = index - leadingBlanks + 1
                    if (day in 1..daysInMonth) {
                        DayCell(vm, month.atDay(day), Modifier.weight(1f))
                    } else {
                        // 本月之外:空格子(与 Web 的 `bg-muted/30` 同义),保持网格对齐。
                        Box(
                            Modifier
                                .weight(1f)
                                .height(CELL_HEIGHT)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 一个格子的高度。
 *
 * 手机上七列每列不到 50dp 宽,而格子要放三行:日期数字、农历、节日。58dp 是能容下三行
 * (13sp + 9sp + 9sp)又不让六行网格把屏幕撑破的那一档。Web 用 `min-h-[80px]`,那是桌面的
 * 尺度 —— 照搬 80dp 会让六行占掉 480dp,在多数手机上把图例和详情卡片都挤出屏幕。
 *
 * EN: a cell's height. Seven columns leave under 50dp each on a phone while a cell carries three
 * lines — the date, the lunar label and a festival. 58dp fits those (13sp + 9sp + 9sp) without the
 * six-row grid overflowing the screen. The web's min-h-[80px] is a desktop measure; copying 80dp
 * would spend 480dp on the grid and push the legend and the day detail off most phones.
 */
private val CELL_HEIGHT = 58.dp

/**
 * 一天的格子:日期数字 + 农历(或节气)+ 节日。
 *
 * 配色语义与 Web 端 `day-cell.tsx` 完全一致,包括优先级:
 *   放假(绿) > 补班(红) > 普通周末(黄) > 平日。
 * 顺序有意义 —— 十月一日是周末也是放假,它必须是绿的;补班日通常正好落在周末,它必须是红的。
 * 把周末排在前面的话,这两种最需要被看见的日子反而被黄色盖掉了。
 *
 * 「今天」是一圈 primary 描边,「选中」是一圈循环变色描边,两者可以同时存在(选中今天时
 * 彩边在外、蓝边在内)—— 与 Web 的 `ring-2` + `ring-rainbow-today` 同一个安排。
 *
 * EN: one day — the date, the lunar label (or the solar term) and a festival. The colour semantics
 * match the web's day-cell.tsx exactly, priority included: day off (green) > make-up workday (red)
 * > ordinary weekend (yellow) > plain day. The order carries meaning — 1 October is both a weekend
 * and a day off and must read green; a make-up workday usually falls on a weekend and must read
 * red. Putting weekends first would bury precisely the two kinds of day worth seeing.
 *
 * Today is a primary ring and the selected day a colour-cycling ring; both can show at once (on
 * today, the cycling ring outside and the primary one inside) — the same arrangement as the web's
 * ring-2 plus ring-rainbow-today.
 */
@Composable
private fun DayCell(vm: CalendarViewModel, date: LocalDate, modifier: Modifier) {
    val palette = LocalCalendarPalette.current
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics(vm)

    val info = vm.dayInfo(date)
    val lunar = remember(date) { Lunar.solar2Lunar(date.year, date.monthValue, date.dayOfMonth) }
    val isToday = date == vm.today
    val isSelected = date == vm.selectedDate
    val isHoliday = info.kind == Holiday.Kind.HOLIDAY
    val isWork = info.kind == Holiday.Kind.WORK
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val isPlainWeekend = isWeekend && !isHoliday && !isWork

    val bg = when {
        isHoliday -> palette.holidayBg
        isWork -> palette.workBg
        isPlainWeekend -> palette.weekendBg
        else -> scheme.surface
    }
    val borderColor = when {
        isHoliday -> palette.holidayBorder
        isWork -> palette.workBorder
        isPlainWeekend -> palette.weekendBorder
        else -> scheme.outlineVariant
    }
    val dayColor = when {
        isHoliday -> palette.holidayText
        isWork -> palette.work
        else -> scheme.onSurface
    }
    val subColor = when {
        isHoliday -> palette.holidaySubText
        isWork -> palette.work.copy(alpha = 0.85f)
        else -> scheme.onSurfaceVariant
    }

    // 节日取名与 Web 同序:农历节日 > 阳历节日 > 数据源里的纪念日(取第一条)。
    // EN: same order as the web — lunar festival, then solar festival, then the first observance.
    val observance = lunar?.lunarFestival
        ?: lunar?.festival
        ?: info.names.firstOrNull()

    val shape = RoundedCornerShape(8.dp)
    // 每个格子都预留 2dp 的"描边留空",不论今天/选中与否 —— 只在需要时才画那圈描边。
    // 不预留的话,今天那一格会比邻格小一圈,整行看起来是歪的。
    // EN: every cell reserves a 2dp ring gap whether or not it draws one; without it today's cell
    // would be a ring's width smaller than its neighbours and the row would look crooked.
    Box(
        modifier
            .height(CELL_HEIGHT)
            .then(if (isSelected) Modifier.selectionRing(shape) else Modifier)
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(bg)
                .border(
                    width = if (isToday) 2.dp else 1.dp,
                    color = if (isToday) scheme.primary else borderColor,
                    shape = shape,
                )
                .clickable(onClick = haptics.selecting { vm.select(date) })
                .padding(horizontal = 2.dp, vertical = 3.dp)
                // 无障碍:三行文字分别播报没有意义(「14」「初九」「中秋节」),合成一句
                // 「9月14日 初九 中秋节 放假」才是用户要的。
                // EN: announcing the three lines separately ("14", "初九", "中秋节") is useless;
                // one sentence is what a screen reader user wants.
                .clearAndSetSemantics {
                    contentDescription = cellDescription(date, lunar?.let { lunarCellLabel(it) }, observance, info.kind)
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                    color = dayColor,
                )
                val lunarLabel = lunarCellLabel(lunar)
                if (lunarLabel.isNotEmpty()) {
                    Text(
                        lunarLabel,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 节气用品牌色:它是格子里唯一"天文事件"类的信息,与节日(纪念日)
                        // 不是一类。放假格里不改色 —— 那里绿色本身就是最强的信号。
                        // EN: solar terms take the brand colour, being the one astronomical item
                        // in a cell as opposed to a festival. Not recoloured on a day off, where
                        // the green is already the strongest signal.
                        color = if (lunar?.isTerm == true && !isHoliday && !isWork) {
                            scheme.primary
                        } else {
                            subColor
                        },
                    )
                }
                if (!observance.isNullOrEmpty()) {
                    Text(
                        observance,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = subColor,
                    )
                }
            }
        }
    }
}

/**
 * 选中日的循环彩边。
 *
 * Web 用 CSS `@keyframes ring-rainbow` 在 1.1s 内把 box-shadow 的颜色在 chart-1…4 之间循环,
 * 这里用同样的时长、同样四个颜色、同样的线性插值。为什么值得动:一个静态的高亮框在一片
 * 已经有绿红黄底色的格子里很难被一眼找到,而"在动"是唯一不与任何底色抢注意力的区别方式。
 *
 * 关掉系统动画时退化为第一个颜色的静态描边(见 [rememberReduceMotion])—— 选中态仍然看得见,
 * 只是不动;把"选中"整个去掉才是错的。
 *
 * EN: the colour-cycling ring on the selected day. The web cycles a box-shadow through chart-1…4
 * over 1.1s with CSS keyframes; this uses the same duration, the same four colours and the same
 * linear interpolation. Motion earns its place here: a static highlight is hard to spot among
 * cells that already carry green, red and yellow fills, and moving is the one distinction that
 * competes with none of them. With system animations off it degrades to a static ring in the first
 * colour — the selection stays visible, it just does not move; dropping the selection entirely
 * would be the wrong answer.
 */
@Composable
private fun Modifier.selectionRing(shape: RoundedCornerShape): Modifier {
    val colors = LocalCalendarPalette.current.ring
    if (rememberReduceMotion()) return this.border(2.dp, colors.first(), shape)
    val transition = rememberInfiniteTransition(label = "ring")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = colors.size.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring-phase",
    )
    val index = phase.toInt().coerceIn(0, colors.size - 1)
    val color = lerp(
        colors[index],
        colors[(index + 1) % colors.size],
        phase - index,
    )
    return this.border(2.dp, color, shape)
}

/** 给读屏软件的一句话描述。 */
private fun cellDescription(
    date: LocalDate,
    lunarLabel: String?,
    observance: String?,
    kind: Holiday.Kind,
): String = buildString {
    if (Lang.isZh) append("${date.monthValue}月${date.dayOfMonth}日") else append("${date.monthValue}/${date.dayOfMonth}")
    if (!lunarLabel.isNullOrEmpty()) append(" $lunarLabel")
    if (!observance.isNullOrEmpty()) append(" $observance")
    when (kind) {
        Holiday.Kind.HOLIDAY -> append(" " + Lang.t("day off", "放假"))
        Holiday.Kind.WORK -> append(" " + Lang.t("make-up workday", "补班"))
        else -> Unit
    }
}

/**
 * 图例:绿=放假、红=补班。与 Web 端同样只有这两条 —— 周末的黄底不进图例,因为"周六周日是
 * 周末"不需要解释,而绿红是这个应用**独有**的约定,不说清就只是两种颜色。
 * EN: the legend — green is a day off, red a make-up workday; the same two entries as the web. The
 * yellow weekend is left out because "Saturday and Sunday are the weekend" needs no explanation,
 * while green and red are this app's own convention and mean nothing until stated.
 */
@Composable
private fun Legend() {
    val palette = LocalCalendarPalette.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(palette.holidayBg, palette.holidayBorder, Lang.t("Day off", "放假"))
        Spacer(Modifier.size(16.dp))
        LegendItem(palette.workBg, palette.workBorder, Lang.t("Make-up workday", "补班"))
    }
}

@Composable
private fun LegendItem(fill: Color, border: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(fill)
                .border(1.dp, border, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.size(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
