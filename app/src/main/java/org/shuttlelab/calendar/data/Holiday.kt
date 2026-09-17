package org.shuttlelab.calendar.data

/**
 * 节假日数据:解析与查询。**纯逻辑,不碰 Android** —— 网络与缓存在 [HolidayRepo],
 * 这样解析本身能被单元测试盖住(见 `HolidayParseTest`),而那正是最容易悄悄失效的一环。
 *
 * 数据源与 Web 端(calendar-shuttle)完全相同:`https://cdn.1htr.cn/static/module/holiday.js`。
 * 它是一个 **JS 文件**而不是 JSON 接口 —— 内容是 `window.syncModule.holiday = { … }`。Web 端在
 * Cloudflare Function 里用正则把数组抠出来(Workers 禁用 eval/new Function),这里沿用**同一组
 * 正则**:不是图省事,而是两端解析出的集合必须一致,同一份正则是最直接的保证。而且它天然
 * 容错 —— 数据源多加一个我们不认识的字段,正则只会忽略它,不会像严格解析那样整体失败。
 *
 * EN: holiday data — parsing and lookup. PURE LOGIC, no Android: networking and caching live in
 * [HolidayRepo], which keeps the parser unit-testable (`HolidayParseTest`), the one part most
 * likely to break silently.
 *
 * The source is exactly the web app's: https://cdn.1htr.cn/static/module/holiday.js — a JS FILE,
 * not a JSON endpoint, whose body is `window.syncModule.holiday = { … }`. The web extracts the
 * arrays with regexes inside a Cloudflare Function (Workers forbid eval/new Function) and the SAME
 * regexes are used here: not for convenience, but because both sides must end up with identical
 * sets, and one shared set of patterns is the most direct guarantee of that. It also degrades
 * well — an unknown new field in the source is ignored rather than failing the whole parse.
 */
object Holiday {

    /** 节假日数据源(唯一配置处),与 Web 端同一个 URL 同一个版本参数。 */
    const val SCRIPT_URL = "https://cdn.1htr.cn/static/module/holiday.js?v=1.0.2"

    /** 某日的类型:放假 / 补班 / 仅节日 / 普通。 */
    enum class Kind { HOLIDAY, WORK, DAY, NONE }

    /**
     * 解析结果:按日期查询用的集合 + 按"月-日"查询的节日表。
     *
     * `holiday` / `work` 用 Set 而不是 List:一个月要查 42 个格子,每个格子都要问"这天放假吗",
     * 而数据里有上千个日期 —— List 的话每次翻月都是四万次字符串比较。
     *
     * EN: the parse result — sets for date lookups plus a "M-D" festival table. Sets rather than
     * lists because a month asks 42 cells × "is this a day off?" against a thousand-odd dates;
     * with lists every month change costs tens of thousands of string comparisons.
     */
    data class Data(
        val holidays: Set<String> = emptySet(),
        val workdays: Set<String> = emptySet(),
        val festivals: Map<String, List<String>> = emptyMap(),
    ) {
        val isEmpty: Boolean get() = holidays.isEmpty() && workdays.isEmpty() && festivals.isEmpty()
    }

    /** 查询结果:类型 + 节日名。 */
    data class DayInfo(val kind: Kind, val names: List<String>)

    /** 把 'YYYY-M-D' 规范为 'YYYY-MM-DD' 便于比对(数据源里两种写法都有)。 */
    fun normalizeDateKey(raw: String): String {
        val parts = raw.split("-")
        if (parts.size != 3) return raw
        val y = parts[0].toIntOrNull() ?: return raw
        val m = parts[1].toIntOrNull() ?: return raw
        val d = parts[2].toIntOrNull() ?: return raw
        return "%d-%02d-%02d".format(y, m, d)
    }

    /** 日期键:'YYYY-MM-DD'。 */
    fun dateKey(year: Int, month: Int, day: Int): String = "%d-%02d-%02d".format(year, month, day)

    /** 节日表的键:'M-D'(不补零,与数据源一致)。 */
    fun monthDayKey(month: Int, day: Int): String = "$month-$day"

    // ---- 正则:与 Web 端 functions/api/holiday.ts 逐字相同 ----
    private val holidayArrRe = Regex("""holiday:\s*\[([\s\S]*?)]\s*,\s*work:\s*\[""")
    private val workArrRe = Regex("""work:\s*\[([\s\S]*?)]\s*,\s*day:\s*\{""")
    private val commonBlockRe = Regex("""day:\s*\{\s*common:\s*\{([\s\S]*?)}\s*}\s*}""")
    private val keyValRe = Regex("""'(\d+-\d+(?:w\d+)?)':\s*\[([\s\S]*?)]""")
    // 单双引号两种字面量都收。这一条不用三引号原始串:它以 " 结尾,紧接 """ 会形成
    // 歧义的引号串,普通转义串更清楚。
    // EN: accepts both quote styles. Not a raw string: this pattern ends in a quote, which would
    // run into the closing triple quote ambiguously — an escaped literal is clearer.
    private val stringItemRe = Regex("'([^']*)'|\"([^\"]*)\"")

    /** 从 JS 数组字面量文本里取出字符串项:" 'a', 'b' " → [a, b]。 */
    private fun parseJsStringArray(body: String): List<String> =
        stringItemRe.findAll(body).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

    /**
     * 解析 holiday.js 全文;结构不符(拿不到 holiday/work 数组)时返回 null,
     * 而不是返回一个空的 [Data] —— 调用方要能分清"解析失败"和"数据里确实没有",
     * 前者该保留上一次的缓存,后者不该。
     *
     * EN: parse the whole holiday.js; returns null when the structure does not match (no
     * holiday/work arrays) rather than an empty [Data], so a caller can tell "parse failed" from
     * "the data really is empty" — the former should keep the previous cache, the latter should not.
     */
    fun parse(script: String): Data? {
        val holidayMatch = holidayArrRe.find(script) ?: return null
        val workMatch = workArrRe.find(script) ?: return null

        val holidays = parseJsStringArray(holidayMatch.groupValues[1]).map(::normalizeDateKey).toSet()
        val workdays = parseJsStringArray(workMatch.groupValues[1]).map(::normalizeDateKey).toSet()

        val festivals = LinkedHashMap<String, List<String>>()
        commonBlockRe.find(script)?.let { block ->
            for (kv in keyValRe.findAll(block.groupValues[1])) {
                val names = parseJsStringArray(kv.groupValues[2])
                if (names.isNotEmpty()) festivals[kv.groupValues[1]] = names
            }
        }

        if (holidays.isEmpty()) return null
        return Data(holidays, workdays, festivals)
    }

    /**
     * 查一天:先看放假,再看补班,最后看是否只是个节日。
     *
     * 顺序不能反:调休补班日在数据里可能同时落在某个节日的月日上(如 10-10),先判节日
     * 就会把"要上班"这条更要紧的信息盖掉。与 Web 端 `getDayInfo` 同序。
     *
     * 关于 `'9-3w6'` 这类键(九月第三个周六):数据源里有,但 Web 端只用 'M-D' 查表,所以它
     * 从不命中、界面上也从不显示。这里**照旧不处理** —— 支持它会让 App 在某个周六多出一个
     * Web 上没有的节日名,而"两端一致"比"多显示一条"重要。要加的话两端一起加。
     *
     * EN: look a day up — day off first, make-up workday next, festival-only last. The order
     * cannot be reversed: a make-up workday can share its month-day with a festival (10-10), and
     * checking festivals first would bury the more consequential "you are working today". Same
     * order as the web's getDayInfo.
     *
     * On keys like '9-3w6' (the third Saturday of September): present in the source, but the web
     * only ever looks up 'M-D', so it never matches and never shows. Left unhandled here for the
     * same reason — supporting it would put a festival name on some Saturday that the web does not
     * have, and agreeing with the web matters more than one extra label. Add it on both sides or
     * neither.
     */
    fun dayInfo(dateKey: String, monthDay: String, data: Data): DayInfo {
        val names = data.festivals[monthDay] ?: emptyList()
        return when {
            data.holidays.contains(dateKey) -> DayInfo(Kind.HOLIDAY, names)
            data.workdays.contains(dateKey) -> DayInfo(Kind.WORK, names)
            names.isNotEmpty() -> DayInfo(Kind.DAY, names)
            else -> DayInfo(Kind.NONE, emptyList())
        }
    }
}
