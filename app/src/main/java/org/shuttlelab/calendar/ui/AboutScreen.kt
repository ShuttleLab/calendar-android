package org.shuttlelab.calendar.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.shuttlelab.calendar.BuildConfig
import org.shuttlelab.calendar.data.Holiday
import org.shuttlelab.calendar.data.Lang
import org.shuttlelab.calendar.ui.theme.brandPrimary

/**
 * 关于页:这个应用做什么、数据从哪来、隐私、许可、联系方式。
 *
 * 内容取自 Web 端的关于页,但**只保留事实**:功能说明、数据来源、隐私、许可、联系。网页那一页
 * 还有一批面向搜索引擎的段落(使用场景、"与君初相识"、旗下其他产品、FAQ),那些在应用里没有
 * 读者 —— 装了应用的人已经不需要被说服。
 *
 * 数据来源这一节是这页存在的主要理由:一个日历凭什么说某天放假、某天补班,用户有权知道;而
 * 农历与节气用的是哪套算法,也决定了它和别的日历对不上时该信谁。
 *
 * EN: About — what the app does, where its data comes from, privacy, licence, contact. Taken from
 * the web's About page but keeping the FACTS only; that page also carries search-facing sections
 * (use cases, a poetic support note, the other products, an FAQ) which have no reader inside an
 * app — someone who installed it no longer needs persuading. The data-sources section is the main
 * reason this page exists: a user is entitled to know on what authority a calendar calls a day a
 * holiday, and which algorithm produces its lunar dates decides who to believe when two calendars
 * disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(vm: CalendarViewModel) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics(vm)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Lang.t("About", "关于")) },
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
            AboutCard(Lang.t("Calendar Shuttle", "日历穿梭机") + " v${BuildConfig.VERSION_NAME}") {
                Body(
                    Lang.t(
                        "Gregorian and lunar dates side by side, with China's statutory holidays and make-up workdays marked — green for a day off, red for a day you work — plus solar terms and festivals.",
                        "公历与农历对照,标出中国法定节假日与调休补班日 —— 绿色是放假,红色是要上班 —— 并显示节气与节日。",
                    ),
                )
            }

            AboutCard(Lang.t("Data sources", "数据来源")) {
                // 把数据源地址原样写出来。一个日历凭什么说某天放假,用户有权核对 —— 含糊地写
                // 「来自权威来源」是最没有信息量的一种说法。
                // EN: state the source outright. A user is entitled to check on what authority a
                // calendar calls a day a holiday; "from an authoritative source" says nothing.
                Field(
                    Lang.t("Holidays (days off / make-up workdays)", "节假日(放假/补班)"),
                    Lang.t(
                        "Follows the State Council's published arrangements, fetched from the same source the website uses and updated as new announcements appear:",
                        "依据国务院公布的安排,与网页版取自同一数据源,随官方公布更新:",
                    ),
                )
                TextButton(onClick = haptics.clicking { openUri(ctx, Holiday.SCRIPT_URL) }) {
                    Text("cdn.1htr.cn/static/module/holiday.js", style = MaterialTheme.typography.bodySmall)
                }
                Field(
                    Lang.t("Lunar calendar, solar terms & festivals", "农历、节气与节日"),
                    Lang.t(
                        "Computed on the device with the jjonline/calendar.js algorithm and tables — the very same ones the website uses, ported to Kotlin so both give identical dates. Nothing is sent anywhere to work this out.",
                        "在本机计算,用的是 jjonline/calendar.js 的算法与数据表 —— 与网页版完全相同,移植到 Kotlin,因此两端结果一致。这一步不向任何地方发送数据。",
                    ),
                )
            }

            AboutCard(Lang.t("Privacy", "隐私")) {
                Body(
                    Lang.t(
                        "No account, no analytics, no advertising identifiers, and nothing about you leaves the device. The app makes exactly one kind of network request: fetching the public holiday schedule. Your settings stay on the device and are excluded from cloud backup.",
                        "不需要账号,没有统计分析,没有广告标识,关于你的任何信息都不会离开设备。应用只发起一种网络请求:获取公开的节假日安排。你的设置只存在本机,并且已排除在云备份之外。",
                    ),
                )
            }

            AboutCard(Lang.t("Licence", "许可")) {
                Body(
                    Lang.t(
                        "AGPL-3.0-only. Free to use, study, modify and redistribute under its terms; derivative works stay under the same licence. For commercial licensing without copyleft obligations, get in touch.",
                        "AGPL-3.0-only。可自由使用、研究、修改与再分发,衍生作品须沿用同一许可。需要不带 copyleft 义务的商业授权请联系我们。",
                    ),
                )
            }

            AboutCard(Lang.t("Contact", "联系")) {
                Body(Lang.t("Questions or suggestions are welcome.", "有问题或建议欢迎联系。"))
                TextButton(onClick = haptics.clicking { openUri(ctx, "mailto:support@shuttlelab.org") }) {
                    Text("support@shuttlelab.org")
                }
                TextButton(onClick = haptics.clicking { openUri(ctx, "https://calendar.shuttlelab.org") }) {
                    Text("calendar.shuttlelab.org")
                }
            }
        }
    }
}

@Composable
private fun AboutCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = brandPrimary())
            content()
        }
    }
}

@Composable
private fun Body(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Field(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Body(value)
    }
}

/**
 * 打开外部链接。失败就静默 —— 设备上可能没有浏览器或邮件客户端,而"关于页点了一下没反应"
 * 远好过在关于页上崩一次。
 * EN: open an external link, silently ignoring failure — a device may have no browser or mail
 * client, and a tap that does nothing on the About page is far better than a crash on it.
 */
private fun openUri(ctx: Context, uri: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
    } catch (_: Exception) {
    }
}
