package org.shuttlelab.calendar.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import org.shuttlelab.calendar.BuildConfig
import org.shuttlelab.calendar.data.HolidayRepo
import org.shuttlelab.calendar.data.Prefs
import org.shuttlelab.calendar.data.Lang
import org.shuttlelab.calendar.ui.theme.brandPrimary

/**
 * 设置页:外观与反馈、节假日数据、关于。
 *
 * 每一组一张 [SettingsCard],与 secretary-android 同一个版式 —— 一长串按钮加分隔线的排法会让
 * 人分不清哪个开关属于哪件事。
 *
 * EN: Settings — appearance & feedback, holiday data, about. One [SettingsCard] per group, the
 * same layout as secretary-android: a flat run of buttons and dividers makes it impossible to see
 * which toggle belongs to what.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: CalendarViewModel) {
    val haptics = rememberHaptics(vm)
    // 原始句柄,只给"打开触感反馈"那一下用:rememberHaptics 捕获的是**当前**开关值,开关刚被
    // 打开的那一帧它还是禁用状态,用它震不出来 —— 而那一下恰恰最该震,它让开关自己演示它控制的东西。
    // EN: the raw handle, only for the moment haptics are switched ON: rememberHaptics captures the
    // CURRENT value, so on that frame it is still the disabled handle and would stay silent — yet
    // that is the one buzz worth having, since it lets the control demonstrate what it controls.
    val rawHaptics = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Lang.t("Settings", "设置")) },
                navigationIcon = {
                    IconButton(onClick = haptics.clicking { vm.back() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = Lang.t("Back", "返回"),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- 外观与反馈 ----
            SettingsCard(Lang.t("Appearance & feedback", "外观与反馈")) {
                SettingRow(Lang.t("Language", "语言")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ChoiceChip(Lang.current == "zh", haptics.selecting { vm.setLang("zh") }, "中文")
                        ChoiceChip(Lang.current == "en", haptics.selecting { vm.setLang("en") }, "English")
                    }
                }
                // 日历文字大小。**只作用于日历格子**,不是全局字号 —— 格子受七列宽度约束,
                // 而系统字体大小是无差别放大的,那一档下三行文字会被裁掉。见 Prefs.KEY_CALENDAR_SCALE。
                // EN: calendar text size — the day grid only, not a global scale. Cells are bound by
                // seven fixed-width columns, whereas the system font size enlarges indiscriminately
                // and would clip their three lines. See Prefs.KEY_CALENDAR_SCALE.
                SettingRow(Lang.t("Calendar text size", "日历文字大小")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val labels = listOf(
                            Lang.t("S", "小"),
                            Lang.t("M", "标准"),
                            Lang.t("L", "大"),
                            Lang.t("XL", "特大"),
                        )
                        Prefs.CALENDAR_SCALES.forEachIndexed { i, scale ->
                            ChoiceChip(
                                vm.calendarScale == scale,
                                haptics.selecting { vm.setCalendarTextSize(scale) },
                                labels[i],
                            )
                        }
                    }
                }
                SettingRow(Lang.t("Theme", "主题")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "system" to Lang.t("System", "跟随系统"),
                            "light" to Lang.t("Light", "浅色"),
                            "dark" to Lang.t("Dark", "深色"),
                        ).forEach { (key, label) ->
                            ChoiceChip(vm.themePref == key, haptics.selecting { vm.setTheme(key) }, label)
                        }
                    }
                }
                // 动态取色只在 Android 12+ 可用。低版本上这一行**不显示**,而不是显示一个点了
                // 没反应的开关。
                // EN: dynamic colour exists only on Android 12+; below that the row is absent
                // rather than present-but-inert.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    ToggleRow(
                        title = Lang.t("Wallpaper colours", "壁纸取色"),
                        subtitle = Lang.t(
                            "Material You: derive the app's palette from your wallpaper. The calendar's own colours never change — a day off stays green and a make-up workday red, because those are meanings rather than decoration.",
                            "Material You:按你的壁纸生成应用配色。日历本身的颜色不受影响 —— 放假仍是绿、补班仍是红,那是语义而不是装饰。",
                        ),
                        checked = vm.useDynamicColor,
                        onCheckedChange = {
                            haptics.toggle(it)
                            vm.setDynamicColor(it)
                        },
                    )
                }
                ToggleRow(
                    title = Lang.t("Haptic feedback", "触感反馈"),
                    subtitle = Lang.t(
                        "A short vibration on taps and on changing month, graded by weight: lighter for moving between months, a touch heavier for a plain tap. Your system-wide touch-feedback setting still overrides this.",
                        "点击与翻月时轻震一下,轻重分级:翻月更轻,普通点击稍重。系统的「触感反馈」总开关仍然优先于此。",
                    ),
                    checked = vm.hapticsEnabled,
                    onCheckedChange = {
                        // 先设置再反馈:打开的那一下立刻震,关闭的那一下不震。
                        // EN: set first, then buzz — switching on vibrates immediately, switching
                        // off does not.
                        vm.setHaptics(it)
                        if (it) rawHaptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    },
                )
            }

            // ---- 节假日数据 ----
            SettingsCard(Lang.t("Holiday data", "节假日数据")) {
                Text(
                    Lang.t(
                        "Days off and make-up workdays follow the State Council's published schedule, from the same source the website uses. It changes a few times a year, so it is cached for 30 days — switching to a daily check once October arrives if next year's schedule is still missing, which is when it is normally announced. A snapshot ships with the app, so a fresh install works offline.",
                        "放假与补班依据国务院公布的安排,数据源与网页版相同。它一年只变几次,所以缓存 30 天;次年安排通常在 11 月前后公布,若进入 10 月后数据里仍没有次年,会自动改成每天检查一次。应用内置了一份快照,首次安装离线也能用。",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Lang.t("Current source", "当前来源"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            sourceLabel(vm.holidaySource),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (vm.holidayLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp).size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
                Button(
                    onClick = haptics.clicking { vm.loadHolidays(force = true) },
                    enabled = !vm.holidayLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(Lang.t("Refresh now", "立即刷新")) }
            }

            // ---- 关于 ----
            SettingsCard(Lang.t("About", "关于")) {
                Text(
                    Lang.t("Calendar Shuttle", "日历穿梭机") + " v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = haptics.clicking { vm.navigate(CalendarViewModel.Screen.About) })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            Lang.t("About this app", "关于本应用"),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            Lang.t("Data sources, privacy, licence", "数据来源、隐私、许可"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 数据来源的人话说明。"用的是哪一级兜底"直接决定了数据有多新,所以它值得说清楚。 */
private fun sourceLabel(source: HolidayRepo.Source): String = when (source) {
    HolidayRepo.Source.NETWORK -> Lang.t("Fetched from the network", "刚从网络获取")
    HolidayRepo.Source.CACHE -> Lang.t("Cached on this device", "设备上的缓存")
    HolidayRepo.Source.BUNDLED -> Lang.t(
        "Snapshot shipped with the app — refresh to get the latest",
        "应用内置的快照 —— 可刷新获取最新",
    )
    HolidayRepo.Source.NONE -> Lang.t("Not loaded yet", "尚未载入")
}

/**
 * 选择型 chip。
 *
 * 选中时带一个勾号 —— 这是 M3 对 FilterChip 的既定表达,而在这里它不是装饰,是**唯一可靠的**
 * 信号:chip 坐在 surfaceVariant 的淡紫卡片上,任何浅紫填充与那张卡片都到不了 3:1。填充和描边
 * 只能表达"层次",勾号表达"状态",而且与是否开了壁纸取色无关。描边在选中态也保留 —— M3 默认
 * 在选中时去掉它,那个默认假设填充与背景有足够反差,在这张卡片上不成立。
 *
 * EN: a selection chip. The check mark on selection is M3's established treatment, and here it is
 * not decoration but the only dependable signal: these chips sit on a light-violet surfaceVariant
 * card and no light violet fill reaches 3:1 against it. Fill and border express a step; the check
 * expresses state, regardless of whether wallpaper colours are on. The border is kept when
 * selected too — M3 drops it by default, assuming the fill contrasts with the background, which is
 * untrue on this card.
 */
@Composable
private fun ChoiceChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            selectedBorderWidth = 1.dp,
        ),
    )
}

/** 分组卡片:标题 + 内容,统一圆角与内距。 */
@Composable
private fun SettingsCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = brandPrimary(),
            )
            content()
        }
    }
}

/** 一行"标签 + 控件"。 */
@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        control()
    }
}

/** 带说明文字的开关行。 */
@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
