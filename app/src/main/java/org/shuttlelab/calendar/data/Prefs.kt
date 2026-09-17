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

    fun setDynamicColor(ctx: Context, on: Boolean) =
        put(ctx, KEY_DYNAMIC_COLOR, if (on) "1" else "0")
}
