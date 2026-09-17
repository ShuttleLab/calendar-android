package org.shuttlelab.calendar.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * 配色分两层,来源不同,刻意分开:
 *
 * 1. **应用外壳**(顶栏、卡片、按钮、设置页)用 secretary-android 的那套色板,原样搬来。
 *    它按 Material 3 的色调板规则配过:深色主题不复用品牌色,而取同一色相更亮的档位
 *    (primary 浅色 tone 40 / 深色 tone 80),每一档都量过对比度。重新配一套不会更好,
 *    只会多一组没验证过的颜色。
 *
 * 2. **日历语义色**(放假绿、补班红、周末黄、选中日的循环彩边)来自 Web 端 calendar-shuttle
 *    的 `app/globals.css`,把那边的 oklch 值转成 sRGB 后写死在 [CalendarPalette]。
 *    这一层**必须**照搬:绿=放假、红=补班是用户在网页上已经学会的读法,两端不一致就是给
 *    同一个人两套语义。
 *
 * 关键的一条:动态取色(Material You)只替换第 1 层,**不动**第 2 层。壁纸是紫色的手机上,
 * 「放假」也还是绿的 —— 那不是配色偏好,那是信息本身。
 *
 * EN: two layers of colour with different origins, deliberately separated.
 * (1) The app shell — top bars, cards, buttons, settings — uses secretary-android's palette
 * verbatim. It already follows Material 3's tonal rule (a dark theme takes a lighter tone of the
 * same hue rather than reusing the brand colour: primary at tone 40 light / tone 80 dark) with
 * every step contrast-checked. Picking fresh colours would not improve it, only add an unverified
 * set. (2) The calendar semantics — holiday green, make-up-workday red, weekend yellow, the
 * cycling ring on the selected day — come from the web app's app/globals.css, with its oklch
 * values converted to sRGB and pinned in [CalendarPalette]. That layer MUST be copied: green
 * means "day off" and red means "you are working" is a reading users already learned on the web,
 * and disagreeing would hand one person two vocabularies.
 * The load-bearing consequence: dynamic colour (Material You) replaces layer 1 only and leaves
 * layer 2 alone. On a phone with a purple wallpaper, a day off is still green — that is not a
 * colour preference, it is the information itself.
 */

// ---- 色阶 (Tailwind),与 secretary-android 同源 ----
private val Indigo100 = Color(0xFFE0E7FF)
private val Indigo400 = Color(0xFF818CF8)
private val Indigo600 = Color(0xFF4F46E5)
private val Indigo800 = Color(0xFF3730A3)
private val Indigo950 = Color(0xFF1E1B4B)

private val Violet100 = Color(0xFFEDE9FE)
private val Violet200 = Color(0xFFDDD6FE)
private val Violet400 = Color(0xFFA78BFA)
private val Violet700 = Color(0xFF6D28D9)
private val Violet800 = Color(0xFF5B21B6)
private val Violet950 = Color(0xFF2E1065)

private val Teal100 = Color(0xFFCCFBF1)
private val Teal300 = Color(0xFF5EEAD4)
private val Teal700 = Color(0xFF0F766E)
private val Teal800 = Color(0xFF115E59)
private val Teal900 = Color(0xFF134E4A)

private val Red100 = Color(0xFFFEE2E2)
private val Red400 = Color(0xFFF87171)
private val Red700 = Color(0xFFB91C1C)
private val Red800 = Color(0xFF991B1B)
private val Red950 = Color(0xFF450A0A)

// 中性色:略带暖调,与靛蓝相衬。
private val LightBg = Color(0xFFFDFBF7)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEEEAF3)
private val LightOn = Color(0xFF1C1B1A)
private val LightOnVariant = Color(0xFF524F57)

private val DarkBg = Color(0xFF111110)
private val DarkSurface = Color(0xFF1C1B1A)
private val DarkSurfaceVariant = Color(0xFF2C2A31)
private val DarkOn = Color(0xFFE6E1E5)
private val DarkOnVariant = Color(0xFFCAC4D0)

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo100,
    onPrimaryContainer = Indigo950,
    inversePrimary = Indigo400,

    secondary = Violet700,
    onSecondary = Color.White,
    // Violet200 而不是 Violet100:后者的选中 FilterChip 填充与 surfaceVariant 卡片实测仅
    // 1.01:1,而 M3 在选中时默认去掉描边,于是选中态一点线索都没有。见 SettingsScreen 里
    // ChoiceChip 为何同时保留描边和勾号。
    // EN: Violet200 not Violet100 — the latter's selected-chip fill measured 1.01:1 against the
    // surfaceVariant card while M3 drops the border when selected, leaving no affordance at all.
    secondaryContainer = Violet200,
    onSecondaryContainer = Violet950,

    tertiary = Teal700,
    onTertiary = Color.White,
    tertiaryContainer = Teal100,
    onTertiaryContainer = Teal900,

    background = LightBg,
    onBackground = LightOn,
    surface = LightSurface,
    onSurface = LightOn,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnVariant,
    surfaceTint = Indigo600,
    surfaceDim = Color(0xFFDED8E1),
    surfaceBright = LightSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F3FA),
    surfaceContainer = Color(0xFFF1EDF4),
    surfaceContainerHigh = Color(0xFFEBE7EE),
    surfaceContainerHighest = Color(0xFFE5E1E9),

    // 4.65:1 on the card / 5.52:1 on white. 更浅的描边(#7A7680)虽然不违规(3.74:1),但一条
    // 细线紧挨实心按钮时读起来像分隔线而不是控件。
    outline = Color(0xFF6B6771),
    outlineVariant = Color(0xFFCAC5D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),

    error = Red700,
    onError = Color.White,
    errorContainer = Red100,
    onErrorContainer = Red950,
)

private val DarkColors = darkColorScheme(
    // 400 档而不是 300 档:Indigo300 在深色下彩度只有 0.104 而亮度 78,就是"掺了白"的观感;
    // Indigo400 彩度 0.158、亮度 68,颜色是"实"的,而且仍远亮于底色(与卡片 4.75:1)。
    // EN: the 400 step, not 300 — Indigo300 measures chroma 0.104 at lightness 78 in OkLCh, which
    // is the definition of "mixed with white"; Indigo400 is chroma 0.158 at 68, still far lighter
    // than the surfaces (4.75:1 on the card).
    primary = Indigo400,
    // 跟着 primary 一起加深:Indigo900 叠在 Indigo400 上只有 3.83:1,低于正文门槛。
    onPrimary = Indigo950,
    primaryContainer = Indigo800,
    onPrimaryContainer = Indigo100,
    inversePrimary = Indigo600,

    secondary = Violet400,
    onSecondary = Violet950,
    secondaryContainer = Violet800,
    onSecondaryContainer = Violet100,

    // 青色留在 300 档:它只做小号信息性文字,需要的是阅读对比度(9.57:1)而不是色彩重量。
    tertiary = Teal300,
    onTertiary = Teal900,
    tertiaryContainer = Teal800,
    onTertiaryContainer = Teal100,

    background = DarkBg,
    onBackground = DarkOn,
    surface = DarkSurface,
    onSurface = DarkOn,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnVariant,
    surfaceTint = Indigo400,
    surfaceDim = DarkBg,
    surfaceBright = Color(0xFF383539),
    surfaceContainerLowest = Color(0xFF0C0B0C),
    surfaceContainerLow = Color(0xFF1A1919),
    surfaceContainer = Color(0xFF211F22),
    surfaceContainerHigh = Color(0xFF2B292D),
    surfaceContainerHighest = Color(0xFF363438),

    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    inverseSurface = DarkOn,
    inverseOnSurface = DarkSurface,

    error = Red400,
    onError = Red950,
    errorContainer = Red800,
    onErrorContainer = Red100,
)

/**
 * 日历语义色。每一个值都对应 Web 端 `app/globals.css` 里的一条 CSS 变量或 Tailwind 类,
 * 括号里写的就是那一条 —— 改这里之前先看 Web,两端不该各自漂移。
 *
 * 带 alpha 的值保留 alpha(而不是提前与底色混合):Web 用的就是 `bg-success/20` 这类半透明
 * 填充,叠在卡片上;Compose 这边同样把它画在卡片上,于是浅色/深色主题下的最终观感与 Web 一致。
 *
 * EN: the calendar's semantic colours. Every value names the CSS variable or Tailwind class it
 * mirrors from the web's app/globals.css — check the web before changing one; the two sides should
 * not drift independently. Alpha is kept rather than pre-blended because the web itself uses
 * translucent fills (bg-success/20) over the card, and painting them over the card here reproduces
 * the same result in both themes.
 */
data class CalendarPalette(
    /** 放假:--success。绿 = 休息。 */
    val holiday: Color,
    /** 放假格的填充:bg-success/20(深色 /25)。 */
    val holidayBg: Color,
    /** 放假格的描边:border-success/50。 */
    val holidayBorder: Color,
    /** 放假格的日期数字:text-green-800(深色 text-green-200)。 */
    val holidayText: Color,
    /** 放假格的次要文字(农历/节日):text-green-700(深色 text-green-300)。 */
    val holidaySubText: Color,
    /** 补班:--destructive。红 = 要上班。 */
    val work: Color,
    /** 补班格的填充:bg-destructive/15(深色 /20)。 */
    val workBg: Color,
    /** 补班格的描边:border-destructive/50。 */
    val workBorder: Color,
    /** 普通周末的填充:bg-yellow-100/80(深色 bg-yellow-900/20)。 */
    val weekendBg: Color,
    /** 普通周末的描边:border-yellow-300/60(深色 border-yellow-700/40)。 */
    val weekendBorder: Color,
    /** 选中日的循环彩边:--chart-1…4。 */
    val ring: List<Color>,
)

private val LightCalendar = CalendarPalette(
    holiday = Color(0xFF008E3E),          // oklch(0.55 0.18 155)
    holidayBg = Color(0x33008E3E),        // /20
    holidayBorder = Color(0x80008E3E),    // /50
    holidayText = Color(0xFF166534),      // green-800
    holidaySubText = Color(0xFF15803D),   // green-700
    work = Color(0xFFD40924),             // oklch(0.55 0.22 25)
    workBg = Color(0x26D40924),           // /15
    workBorder = Color(0x80D40924),       // /50
    weekendBg = Color(0xCCFEF9C3),        // yellow-100/80
    weekendBorder = Color(0x99FDE047),    // yellow-300/60
    ring = listOf(
        Color(0xFF2B62EF), // chart-1 oklch(0.55 0.22 264)
        Color(0xFF009A74), // chart-2 oklch(0.58 0.18 175)
        Color(0xFF11AD32), // chart-3 oklch(0.65 0.2 145)
        Color(0xFFE1A200), // chart-4 oklch(0.75 0.18 85)
    ),
)

private val DarkCalendar = CalendarPalette(
    holiday = Color(0xFF00AD5B),          // oklch(0.65 0.18 155)
    holidayBg = Color(0x4000AD5B),        // /25
    holidayBorder = Color(0x8000AD5B),    // /50
    holidayText = Color(0xFFBBF7D0),      // green-200
    holidaySubText = Color(0xFF86EFAC),   // green-300
    work = Color(0xFFF14D4C),             // oklch(0.65 0.2 25)
    workBg = Color(0x33F14D4C),           // /20
    workBorder = Color(0x80F14D4C),       // /50
    weekendBg = Color(0x33713F12),        // yellow-900/20
    weekendBorder = Color(0x66A16207),    // yellow-700/40
    ring = listOf(
        Color(0xFF4783FF), // chart-1 oklch(0.65 0.22 264)
        Color(0xFF00C098), // chart-2 oklch(0.7 0.18 175)
        Color(0xFF4CC157), // chart-3 oklch(0.72 0.18 145)
        Color(0xFFE6AD00), // chart-4 oklch(0.78 0.16 85)
    ),
)

/**
 * 取日历语义色。用 static local:这组值在一次组合里不变,而格子有 42 个 —— 走
 * `staticCompositionLocalOf` 时读取不会给每个格子登记无谓的订阅。
 * EN: the calendar palette. A static local because the values never change within a composition
 * while there are 42 cells reading them — nothing needs per-cell subscriptions.
 */
val LocalCalendarPalette = staticCompositionLocalOf { LightCalendar }

/** 文字/描边用的品牌色 —— 色板已按 M3 色调板规则给深色主题配了更亮的 primary,直接用即可。 */
@Composable
fun brandPrimary(): Color = MaterialTheme.colorScheme.primary

/** 文字用的错误色。 */
@Composable
fun brandError(): Color = MaterialTheme.colorScheme.error

/**
 * 主题:默认跟随系统,设置页可强制浅色/深色;[themePref] 由 ViewModel 持有,切换立即生效。
 * EN: theme follows the system by default and Settings can force light/dark; themePref lives in
 * the ViewModel so switching applies immediately.
 */
@Composable
fun CalendarTheme(
    themePref: String = "system",
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themePref) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    /*
     * Material You(动态取色)按壁纸生成配色。只在 Android 12+ 且用户显式打开时启用,**默认关闭**:
     * 它会整套替换上面那组逐档验证过对比度的颜色,而这是个观感偏好、不是修正 —— 默认开会让现有
     * 用户在一次升级后界面突然变色,而他们没要求过。
     *
     * 注意它**不**影响 [CalendarPalette]:放假绿/补班红是语义,不跟壁纸走(见文件头)。
     *
     * EN: Material You, wallpaper-derived. Android 12+ and only when explicitly enabled; off by
     * default because it replaces the whole contrast-checked palette above and is a taste
     * preference rather than a correction — defaulting it on would recolour every existing user's
     * app after an update they never asked for. It does NOT touch [CalendarPalette]: holiday green
     * and workday red are semantics and do not follow a wallpaper (see the header).
     */
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(ctx)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(ctx)
        darkTheme -> DarkColors
        else -> LightColors
    }

    /*
     * 系统栏图标的明暗必须在运行时按**实际生效的主题**设置。targetSdk 36 下 edge-to-edge 是强制的,
     * 状态栏/导航栏透明、直接压在我们的背景上;而图标黑白取自宿主 XML 主题(固定 Light,
     * windowLightStatusBar 默认 false = 白图标),浅色主题下白图标压在奶油白背景上就是看不见。
     * XML 也猜不到正确值:深浅由用户在 App 里的偏好决定。所以只能在这里、拿到 darkTheme 之后设。
     *
     * EN: system-bar icon appearance must be set at runtime from the theme actually in effect.
     * Edge-to-edge is mandatory at targetSdk 36, so the bars are transparent over our own
     * background, while icon lightness comes from the host XML theme (fixed Light,
     * windowLightStatusBar false = white icons) — invisible on a cream background. The XML cannot
     * know the right value either, since lightness is an in-app preference.
     */
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        if (window != null) {
            SideEffect {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalCalendarPalette provides if (darkTheme) DarkCalendar else LightCalendar,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
