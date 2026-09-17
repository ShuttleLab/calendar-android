package org.shuttlelab.calendar.data

import android.content.Context

/**
 * 本地持久化:**只有偏好**(语言、主题、壁纸取色、触感)。
 *
 * 这里没有任何账号或用户数据 —— 这个应用不登录、不联网上报、不收集任何东西;它唯一联网的动作
 * 是取一份公开的节假日安排(见 [HolidayRepo],缓存落在 filesDir,不在这里)。农历与节气是纯算法,
 * 连数据都不用取。
 *
 * EN: local persistence holds PREFERENCES ONLY (language, theme, wallpaper colours, haptics).
 * There is no account or user data: the app has no login, reports nothing, and collects nothing.
 * Its one network call fetches a public holiday schedule ([HolidayRepo], cached in filesDir, not
 * here), and the lunar calendar and solar terms are pure computation needing no data at all.
 */
object Prefs {
    private const val NAME = "calendar"

    const val KEY_THEME = "theme"                     // "system" | "light" | "dark"
    const val KEY_DYNAMIC_COLOR = "dynamic_color"     // "1" = Material You 动态取色

    /**
     * 触感反馈。**默认开启**,"未设置"即视为开启(所以键不存在时也是开)。
     * 系统的「触感反馈」总开关仍然优先 —— 关掉它,这里的调用就什么也不做。见 ui/Haptics.kt。
     * EN: haptics, ON by default with "unset" meaning on. The system-wide touch-feedback switch
     * still wins: with it off, these calls do nothing. See ui/Haptics.kt.
     */
    const val KEY_HAPTICS = "haptics"

    /**
     * 日历文字大小,存的是缩放系数("0.85" / "1.0" / "1.15" / "1.3"),默认 1.0。
     *
     * 为什么是应用内设置而不是跟随系统字体大小:系统那一档会**同时**放大所有文字,而日历格子
     * 的约束是七列固定宽度 —— 放大到某一档,格子里的三行就装不下了。这里缩放的是一组一起变的
     * 尺寸(格子高度 + 日期 + 农历/节日),所以放大后仍然装得下。系统字体大小对界面其余部分
     * (设置页、详情卡)照常生效,那些地方没有这个约束。
     *
     * EN: the calendar's text size, stored as a scale factor, default 1.0. An in-app setting rather
     * than the system font size because the system one enlarges everything at once, while a cell is
     * constrained by seven fixed-width columns: past some step, its three lines no longer fit. This
     * factor scales a SET of sizes together (cell height, date, lunar label) so the content keeps
     * fitting. The system font size still applies to the rest of the UI — settings, the detail card —
     * which carries no such constraint.
     */
    const val KEY_CALENDAR_SCALE = "calendar_scale"

    /** 可选的档位:小 / 标准 / 大 / 特大。 */
    val CALENDAR_SCALES = listOf(0.85f, 1.0f, 1.15f, 1.3f)

    private fun sp(ctx: Context) = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun get(ctx: Context, key: String, def: String = ""): String =
        sp(ctx).getString(key, def) ?: def

    fun put(ctx: Context, key: String, value: String) {
        sp(ctx).edit().putString(key, value).apply()
    }

    /** 主题偏好;未设置为「跟随系统」。 */
    fun theme(ctx: Context): String = get(ctx, KEY_THEME).ifBlank { "system" }

    /** 动态取色;未设置为关(见 ui/theme/Theme.kt 里为何默认关)。 */
    fun dynamicColor(ctx: Context): Boolean = get(ctx, KEY_DYNAMIC_COLOR) == "1"

    /** 触感反馈;未设置为开。 */
    fun haptics(ctx: Context): Boolean = get(ctx, KEY_HAPTICS) != "0"

    fun setHaptics(ctx: Context, on: Boolean) = put(ctx, KEY_HAPTICS, if (on) "1" else "0")

    /** 日历文字大小;未设置或存了脏值时回到 1.0。 */
    fun calendarScale(ctx: Context): Float =
        get(ctx, KEY_CALENDAR_SCALE).toFloatOrNull()?.takeIf { it in CALENDAR_SCALES } ?: 1.0f

    fun setCalendarScale(ctx: Context, scale: Float) =
        put(ctx, KEY_CALENDAR_SCALE, scale.toString())

    fun setDynamicColor(ctx: Context, on: Boolean) =
        put(ctx, KEY_DYNAMIC_COLOR, if (on) "1" else "0")
}
