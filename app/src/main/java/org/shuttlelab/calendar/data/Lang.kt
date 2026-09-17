package org.shuttlelab.calendar.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 轻量 i18n:`t(en, zh)` 按当前语言返回其一。与 secretary-android 同一套做法,
 * 用户可见文案就写在调用点,`strings.xml` 只留 `app_name`。
 *
 * 为什么不用 `values/` + `values-zh/` 的标准资源:语言是**应用内**的偏好(设置页可切),
 * 而资源方案跟随的是系统 locale。用资源就得在切换语言时重建 Activity 或改 per-app locale,
 * 而这里语言是 Compose 状态,拨一下开关整个界面立刻重绘 —— 对一个只有两种语言、几十条文案的
 * 应用,这是更简单也更直接的一端。
 *
 * EN: minimal i18n — t(en, zh) returns one side per the current language, the same approach as
 * secretary-android: user-facing strings sit at the call site and strings.xml holds only app_name.
 * Not standard values/ + values-zh/ resources because language here is an IN-APP preference
 * (switchable in Settings) while resources follow the system locale: that route needs an activity
 * recreation or a per-app locale to switch, whereas this language is Compose state and flipping it
 * recomposes everything at once — the simpler end for an app with two languages and a few dozen
 * strings.
 */
object Lang {
    const val KEY_LANG = "lang" // "en" | "zh"

    var current: String by mutableStateOf("zh")
        private set

    /**
     * 初始化:用户设置过就用他的选择,没设置过**跟随系统语言**。
     *
     * 与 secretary 的一处不同:那边没设置时默认英文(系统中文才用中文),这里默认中文。
     * 理由是内容本身 —— 农历、节气、法定节假日与调休是中国日历的概念,格子里显示的
     * 「腊月初八」「补班」也只有中文表述。系统语言不是中文的用户仍然能一键切到英文。
     *
     * EN: use the user's choice if any, otherwise FOLLOW THE SYSTEM LANGUAGE. Unlike secretary,
     * the fallback here is Chinese rather than English, because of what the content is: the lunar
     * calendar, solar terms, statutory holidays and make-up workdays are Chinese-calendar
     * concepts, and what a cell shows (腊月初八, 补班) exists only in Chinese. A user on a
     * non-Chinese system can still switch to English in one tap.
     */
    fun init(ctx: Context) {
        val saved = Prefs.get(ctx, KEY_LANG)
        current = when {
            saved == "zh" || saved == "en" -> saved
            else -> if (systemIsChinese()) "zh" else "en"
        }
    }

    /** 设备语言是否为中文(含简繁及各地区变体)。 */
    private fun systemIsChinese(): Boolean =
        java.util.Locale.getDefault().language.equals("zh", ignoreCase = true)

    fun set(ctx: Context, lang: String) {
        current = if (lang == "en") "en" else "zh"
        Prefs.put(ctx, KEY_LANG, current)
    }

    /** 按当前语言返回:英文在前,中文在后。 */
    fun t(en: String, zh: String): String = if (current == "zh") zh else en

    /** 当前是否中文界面 —— 格子里的农历文案只有中文,少数地方要按它分支。 */
    val isZh: Boolean get() = current == "zh"
}
