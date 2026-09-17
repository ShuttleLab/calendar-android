package org.shuttlelab.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.shuttlelab.calendar.data.Holiday
import org.shuttlelab.calendar.data.Lang
import org.shuttlelab.calendar.data.Lunar
import org.shuttlelab.calendar.data.lunarDisplay
import org.shuttlelab.calendar.ui.theme.LocalCalendarPalette
import java.time.LocalDate

/**
 * 日详情卡片:点某一天后摊开的那张卡。
 *
 * 内容与顺序完全照 Web 端 `components/calendar/day-detail.tsx`:日期 → 公历星期/农历 → 节气 →
 * 阳历节日 → 农历节日 → 放假/补班徽标 → 纪念日列表。字段一个不多一个不少 —— 详情页是两端
 * 最容易"各自加一点"的地方,而那正是用户会发现两边不一样的地方。
 *
 * 位置也照 Web:摊在月历**下方**,而不是做成底部弹窗。弹窗会盖住格子,而这张卡最常见的用法
 * 恰恰是"对照着看":点一天、看它是什么、再点旁边一天。Android 的原生做法在这里反而更差。
 *
 * EN: the day detail card. Content and order follow the web's day-detail.tsx exactly — date,
 * weekday and lunar date, solar term, solar festival, lunar festival, the day-off/workday badge,
 * then observances — with no field added or dropped: a detail view is where two ports most easily
 * each "add a little", and that is exactly where a user notices they disagree.
 *
 * Its position follows the web too: laid out BELOW the month rather than as a bottom sheet. A sheet
 * would cover the grid, while the commonest use of this card is comparison — tap a day, read it,
 * tap the one next to it. The more native Android answer is the worse one here.
 */
@Composable
fun DayDetail(vm: CalendarViewModel, date: LocalDate, modifier: Modifier = Modifier) {
    val palette = LocalCalendarPalette.current
    val scheme = MaterialTheme.colorScheme
    val haptics = rememberHaptics(vm)

    val info = vm.dayInfo(date)
    val lunar = remember(date) { Lunar.solar2Lunar(date.year, date.monthValue, date.dayOfMonth) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp, bottom = 16.dp)) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "%d-%02d-%02d".format(date.year, date.monthValue, date.dayOfMonth),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )

                // 公历(星期)与农历并排一行,与 Web 的同一行排布一致。
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Label(Lang.t("Solar", "公历"))
                    Text(weekdayText(date), style = MaterialTheme.typography.bodyMedium)
                    val lunarStr = lunarDisplay(lunar)
                    if (lunarStr.isNotEmpty()) {
                        Label(Lang.t("Lunar", "农历"))
                        Text(lunarStr, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                // 节气 / 阳历节日 / 农历节日:有才显示,顺序与 Web 相同。
                lunar?.term?.let { Field(Lang.t("Solar term", "节气"), it) }
                lunar?.festival?.let { Field(Lang.t("Solar festival", "阳历节日"), it) }
                lunar?.lunarFestival?.let { Field(Lang.t("Lunar festival", "农历节日"), it) }

                // 放假 / 补班徽标。颜色与格子里用的是同一组语义色,所以"卡片上的绿"和
                // "格子里的绿"是同一个意思,而不是两处各挑了一个绿。
                // EN: the badge uses the same semantic colours as the cells, so the green on the
                // card and the green in the grid mean one thing rather than being two greens
                // picked independently.
                if (info.kind == Holiday.Kind.HOLIDAY || info.kind == Holiday.Kind.WORK) {
                    val holiday = info.kind == Holiday.Kind.HOLIDAY
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (holiday) palette.holidayBg else palette.workBg)
                            .border(
                                1.dp,
                                if (holiday) palette.holidayBorder else palette.workBorder,
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (holiday) Lang.t("Day off", "放假") else Lang.t("Make-up workday", "补班"),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (holiday) palette.holidayText else palette.work,
                        )
                    }
                }

                // 纪念日:数据源里同一天可能有好几条(如 4-7「世界卫生日」),与 Web 一样
                // 用「、」连成一段,而不是列成表 —— 它们是并列的注记,不是待办。
                // EN: observances — a date can carry several, joined with 、 as on the web rather
                // than listed: they are parallel annotations, not items to work through.
                if (info.names.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Label(Lang.t("Observances", "节日"))
                        Text(
                            info.names.joinToString("、"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            // 关闭按钮。再点一次那一天同样收起(见 [CalendarViewModel.select]),但按钮必须在:
            // 它是唯一**看得见**的关闭方式。
            // EN: the close button. Tapping the day again also closes it, but the button has to be
            // here — it is the only VISIBLE way to close.
            IconButton(onClick = haptics.clicking { vm.clearSelection() }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = Lang.t("Close", "关闭"),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 字段名:小一号、弱一档,让值成为读者先看到的东西。 */
@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Field(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        Label("$label:")
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * 星期:中文「星期三」,英文「Wed」。
 *
 * 不用 `DateTimeFormatter` 的本地化星期名:那会跟随**系统** locale,而界面语言是应用内偏好 ——
 * 中文界面在英文系统上就会显示 "Wednesday"。这里直接按 [Lang] 给字。
 *
 * EN: the weekday. Not DateTimeFormatter's localised names, which follow the SYSTEM locale while
 * the UI language is an in-app preference — a Chinese UI on an English system would read
 * "Wednesday". The text is chosen from [Lang] instead.
 */
private fun weekdayText(date: LocalDate): String {
    val zh = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
    val en = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val i = date.dayOfWeek.value - 1
    return if (Lang.isZh) zh[i] else en[i]
}
