package org.shuttlelab.calendar.data

import java.time.LocalDate

/**
 * 公历 → 农历、节气、节日。
 *
 * 这是 `js-calendar-converter`(jjonline/calendar.js v0.0.7)`solar2lunar` 的**逐行移植**,
 * 而不是另写一套农历算法 —— Web 端(calendar-shuttle)用的就是这个包,两端要显示完全相同的
 * 农历日、节气和节日,唯一靠得住的办法是同一份表(见 [LunarTables])配同一套算法。
 *
 * 为什么坚持"逐行":农历的实现差异不会均匀地错,而是**只在某些年份错一天** —— 闰月边界、
 * 节气跨日、除夕在小月等等。自己写一套再"抽查几个日期"根本测不出来,差异会留给用户去发现,
 * 而那时 Web 和 App 摆在一起对不上,没人能说清哪边是对的。所以这里刻意连原实现的取整与
 * 循环写法都保持一致,连它的已知怪癖也一并保留(见下面 [solar2Lunar] 里的两处注释)。
 *
 * EN: solar → lunar, solar terms and festivals. This is a LINE-BY-LINE port of
 * js-calendar-converter's (jjonline/calendar.js v0.0.7) solar2lunar rather than another lunar
 * implementation: the web app uses that package, and showing identical lunar dates, terms and
 * festivals on both sides is only dependable with the same tables ([LunarTables]) and the same
 * algorithm.
 *
 * Why line-by-line matters: lunar implementations do not disagree uniformly, they disagree by ONE
 * DAY IN SOME YEARS — leap-month boundaries, a term falling either side of midnight, New Year's
 * Eve in a short twelfth month. Writing a fresh one and spot-checking a few dates cannot find
 * that; the difference is left for a user to find, with the web and the app side by side and
 * nobody able to say which is right. So even the original's loop shape is preserved, including
 * its known quirks (see the two comments inside [solar2Lunar]).
 */
object Lunar {

    /** 农历某年的总天数。 */
    private fun lYearDays(y: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            if (LunarTables.lunarInfo[y - 1900] and i != 0) sum += 1
            i = i shr 1
        }
        return sum + leapDays(y)
    }

    /** 农历某年闰几月;没有闰月返回 0。 */
    private fun leapMonth(y: Int): Int = LunarTables.lunarInfo[y - 1900] and 0xf

    /** 农历某年闰月的天数(0 / 29 / 30)。 */
    private fun leapDays(y: Int): Int {
        if (leapMonth(y) != 0) {
            return if (LunarTables.lunarInfo[y - 1900] and 0x10000 != 0) 30 else 29
        }
        return 0
    }

    /** 农历某年某月(非闰月)的天数(29 / 30);月份越界返回 -1。 */
    private fun monthDays(y: Int, m: Int): Int {
        if (m > 12 || m < 1) return -1
        return if (LunarTables.lunarInfo[y - 1900] and (0x10000 shr m) != 0) 30 else 29
    }

    /** 农历年 → 干支年。 */
    private fun toGanZhiYear(lYear: Int): String {
        var ganKey = (lYear - 3) % 10
        var zhiKey = (lYear - 3) % 12
        if (ganKey == 0) ganKey = 10
        if (zhiKey == 0) zhiKey = 12
        return LunarTables.gan[ganKey - 1] + LunarTables.zhi[zhiKey - 1]
    }

    /** 年 → 生肖(粗略:精确分界是立春,原实现也只做到这一步)。 */
    private fun animal(y: Int): String = LunarTables.animals[(y - 4) % 12]

    /**
     * 公历 y 年第 n 个节气(n=1 为小寒)的**日**。
     *
     * 表的编码:每年 30 个十六进制字符,按 5 字符一段共 6 段;每段转成十进制后是 6 位数字,
     * 再切成 1/2/1/2 位 → 4 个日期,6 段共 24 个。这套切法看着奇怪,但它是原实现的编码方式,
     * 换个写法就对不上表了,所以照搬。
     *
     * EN: the day-of-month of the n-th solar term (n=1 is 小寒) in solar year y. The table encodes
     * each year as 30 hex chars in six 5-char chunks; each chunk parsed as decimal yields six
     * digits, sliced 1/2/1/2 into four day numbers — 24 per year. The slicing looks odd but it is
     * the table's own encoding, so it is reproduced as-is.
     */
    private fun getTerm(y: Int, n: Int): Int {
        if (y < 1900 || y > 3000 || n < 1 || n > 24) return -1
        val table = LunarTables.sTermInfo[y - 1900]
        val days = ArrayList<String>(24)
        var index = 0
        while (index < table.length) {
            val chunk = table.substring(index, index + 5).toLong(16).toString()
            days.add(chunk.substring(0, 1))
            days.add(chunk.substring(1, 3))
            days.add(chunk.substring(3, 4))
            days.add(chunk.substring(4, 6))
            index += 5
        }
        return days[n - 1].toInt()
    }

    /** 农历月 → 中文(正月…腊月)。 */
    private fun toChinaMonth(m: Int): String {
        if (m > 12 || m < 1) return ""
        return LunarTables.nStr3[m - 1] + "月"
    }

    /** 农历日 → 中文(初一…三十)。 */
    private fun toChinaDay(d: Int): String = when (d) {
        10 -> "初十"
        20 -> "二十"
        30 -> "三十"
        else -> LunarTables.nStr2[d / 10] + LunarTables.nStr1[d % 10]
    }

    /**
     * 公历 → 农历详情;超出 1900-01-31 ~ 3000-12-31 返回 null。
     *
     * 原实现在越界时返回数字 -1(JS 弱类型下的惯用法),这里返回 null —— 调用方在 Kotlin 里
     * 必须显式处理,不会像 JS 那样把 -1 当对象用下去。
     *
     * EN: returns null outside 1900-01-31 … 3000-12-31. The original returns the number -1 (a JS
     * idiom); null forces the caller to handle it instead of letting a -1 flow on as an object.
     */
    fun solar2Lunar(year: Int, month: Int, day: Int): LunarInfo? {
        if (year < 1900 || year > 3000) return null
        if (year == 1900 && month == 1 && day < 31) return null

        val date = try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            return null
        }
        val y = date.year
        val m = date.monthValue
        val d = date.dayOfMonth

        var offset = (date.toEpochDay() - LocalDate.of(1900, 1, 31).toEpochDay()).toInt()
        var temp = 0
        var i = 1900
        // 上界 2101 是原实现写死的(它比表的覆盖范围小得多)。保留 —— 改大会让 2101 年以后的
        // 结果与 Web 不一致,而"与 Web 一致"是这个文件存在的全部理由。
        // EN: the 2101 bound is the original's, far below what the tables cover. Kept: raising it
        // would disagree with the web after 2101, and agreeing with the web is this file's reason
        // to exist.
        while (i < 2101 && offset > 0) {
            temp = lYearDays(i)
            offset -= temp
            i++
        }
        if (offset < 0) {
            offset += temp
            i--
        }

        val lunarYear = i
        val leap = leapMonth(i)
        var isLeap = false

        i = 1
        while (i < 13 && offset > 0) {
            if (leap > 0 && i == leap + 1 && !isLeap) {
                --i
                isLeap = true
                temp = leapDays(lunarYear)
            } else {
                temp = monthDays(lunarYear, i)
            }
            if (isLeap && i == leap + 1) isLeap = false
            offset -= temp
            i++
        }
        // 闰月导致下标重叠时取反(原注释:"闰月导致数组下标重叠取反")。
        if (offset == 0 && leap > 0 && i == leap + 1) {
            if (isLeap) {
                isLeap = false
            } else {
                isLeap = true
                --i
            }
        }
        if (offset < 0) {
            offset += temp
            --i
        }

        val lunarMonth = i
        val lunarDay = offset + 1

        var nWeek = date.dayOfWeek.value % 7 // java: 周一=1…周日=7;转成 JS 的 0=周日
        val cWeek = LunarTables.nStr1[nWeek]
        if (nWeek == 0) nWeek = 7 // 顺应"周一开始"的惯例,与原实现一致

        // 当月的两个节气(用**公历**年 y,不是农历年 —— 原实现 2017-07-24 修过这个 bug)
        // EN: the month's two terms are looked up with the SOLAR year; the original fixed a bug
        // here in 2017 where it used the lunar year.
        val firstNode = getTerm(y, m * 2 - 1)
        val secondNode = getTerm(y, m * 2)
        var isTerm = false
        var term: String? = null
        if (firstNode == d) {
            isTerm = true
            term = LunarTables.solarTerm[m * 2 - 2]
        }
        if (secondNode == d) {
            isTerm = true
            term = LunarTables.solarTerm[m * 2 - 1]
        }

        val festivalKey = "$m-$d"
        // 除夕修正(原实现对 issue #29 的修法):农历十二月是小月(29 天)时,廿九即除夕。
        // 农历节日"遇闰不过后",所以这里取十二月天数时不考虑闰月 —— 本工具支持的区间内
        // 闰十二月仅 1574 年出现过一次。
        // EN: New Year's Eve fix (the original's answer to issue #29): when the twelfth lunar
        // month is short (29 days), the 29th IS the eve. Lunar festivals are observed in the
        // non-leap month, so the leap case is ignored — a leap twelfth month occurs once (1574)
        // in the supported range.
        var lunarFestivalKey = "$lunarMonth-$lunarDay"
        if (lunarMonth == 12 && lunarDay == 29 && monthDays(lunarYear, lunarMonth) == 29) {
            lunarFestivalKey = "12-30"
        }

        return LunarInfo(
            cYear = y,
            cMonth = m,
            cDay = d,
            lYear = lunarYear,
            lMonth = lunarMonth,
            lDay = lunarDay,
            isLeap = isLeap,
            monthCn = (if (isLeap) "闰" else "") + toChinaMonth(lunarMonth),
            dayCn = toChinaDay(lunarDay),
            animal = animal(lunarYear),
            ganZhiYear = toGanZhiYear(lunarYear),
            isTerm = isTerm,
            term = term,
            nWeek = nWeek,
            weekCn = "星期$cWeek",
            festival = LunarTables.festival[festivalKey],
            lunarFestival = LunarTables.lunarFestival[lunarFestivalKey],
        )
    }
}

/**
 * 一天的农历信息。字段与 Web 端 `lib/lunar.ts` 的 `LunarInfo` 接口一一对应(名字按 Kotlin
 * 习惯改成小驼峰),这样两端要核对显示内容时是同一组字段。
 *
 * 原实现还返回 `gzMonth` / `gzDay` / `astro` / `isToday`,Web 的类型里没有、界面也不用,
 * 所以没有移植 —— 需要时照原实现补上即可,算法不依赖它们。
 *
 * EN: one day's lunar information, field-for-field with the web's LunarInfo interface (renamed to
 * Kotlin camelCase), so comparing what the two sides display is a comparison of the same fields.
 * The original also returns gzMonth / gzDay / astro / isToday, which the web's type omits and no
 * screen uses, so they are not ported; the algorithm does not depend on them.
 */
data class LunarInfo(
    val cYear: Int,
    val cMonth: Int,
    val cDay: Int,
    val lYear: Int,
    val lMonth: Int,
    val lDay: Int,
    val isLeap: Boolean,
    /** 农历月中文,如 腊月 / 闰六月。 */
    val monthCn: String,
    /** 农历日中文,如 初十。 */
    val dayCn: String,
    val animal: String,
    val ganZhiYear: String,
    val isTerm: Boolean,
    /** 节气名,当天不是节气则为 null。 */
    val term: String?,
    val nWeek: Int,
    val weekCn: String,
    /** 公历节日,如 元旦节。 */
    val festival: String?,
    /** 农历节日,如 春节。 */
    val lunarFestival: String?,
)

/**
 * 农历月数字 → 中文(一月…十二月),用于括号里那一段。
 * EN: lunar month number → Chinese, for the parenthesised part.
 */
private val lunarMonthNum = arrayOf(
    "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二",
)

fun lunarMonthNumStr(lMonth: Int): String =
    if (lMonth in 1..12) "${lunarMonthNum[lMonth - 1]}月" else ""

/**
 * 完整农历显示:`腊月(十二月)十四`;当天是节气则**只**显示节气名。
 *
 * 与 Web 的 `lunarDisplay()` 完全一致,包括"节气优先"这一条 —— 详情页两端看起来必须一样。
 * 括号里重复一遍数字月份是 Web 的选择:腊月、冬月、正月这类称呼不是人人都能立刻对上月份。
 *
 * **已知的别扭之处**(照搬,不在这边单方面改):二月到十月的农历月名和数字月名本来就是同一个词,
 * 于是这个格式会渲染成 `八月(八月)初七` —— 括号里重复了它要解释的东西。只有正月、冬月、腊月和
 * 闰月那几个月份才真正需要这个括号。改法是显而易见的(只在两者不同时加括号),但这是**显示**
 * 上的改动,改了两端就不一致了 —— 要改请两端一起改。`LunarParityTest` 里用字面量把当前行为
 * 钉住了,那条断言失败就说明有人单方面动过这里。
 *
 * EN: the full lunar string, identical to the web's lunarDisplay(), including term-takes-priority.
 * Repeating the month as a number in brackets is the web's choice: 腊月, 冬月 and 正月 are not names
 * everyone maps to a month number on sight.
 *
 * KNOWN AWKWARDNESS, reproduced rather than fixed unilaterally: from the second to the tenth month
 * the lunar month name and its numeric name are the same word, so this format renders 八月(八月)初七
 * — the bracket repeating what it exists to explain. Only 正月, 冬月, 腊月 and leap months actually
 * need it. The fix is obvious (bracket it only when the two differ) but it is a DISPLAY change, and
 * making it here alone would put the two sides out of step — change both or neither.
 * LunarParityTest pins the current behaviour with a literal, so that assertion failing means
 * someone changed this on one side only.
 */
fun lunarDisplay(info: LunarInfo?): String {
    if (info == null) return ""
    info.term?.let { return it }
    val num = lunarMonthNumStr(info.lMonth)
    return if (num.isNotEmpty()) "${info.monthCn}($num)${info.dayCn}" else info.monthCn + info.dayCn
}

/**
 * 格子里那一行农历。
 *
 * 这是与 Web 端**唯一刻意不同**的一处:Web 在格子里也放完整的 `lunarDisplay()`,靠 CSS
 * `truncate` 截断。桌面浏览器上一个格子有一百多像素宽,截断很少发生;手机上七列格子每列
 * 不到 50dp,`腊月(十二月)十四` 九个字必然被砍成 `腊月(十二…`,于是最该看的那个日子(十四)
 * 恰好是被砍掉的部分 —— 照搬会得到一个"像素级一致但读不出日期"的格子。
 *
 * 所以这里按手机日历的通行做法给出同样的信息、换一种取舍:节气 > 农历月(初一那天)> 农历日。
 * 完整的 `腊月(十二月)十四` 仍然在详情页原样出现(见 [lunarDisplay]),信息没有丢。
 *
 * EN: the lunar line inside a day cell — the one place this app deliberately differs from the web.
 * The web puts the full lunarDisplay() in the cell and lets CSS `truncate` cut it; on a desktop a
 * cell is 100+ px wide so that rarely bites, but on a phone seven columns leave under 50dp each and
 * 腊月(十二月)十四 is certainly cut to 腊月(十二… — clipping away the day, the one part worth
 * reading. Copying it literally would give a pixel-faithful cell you cannot read a date from. So the
 * cell follows what phone calendars do, with the same information ranked differently: term > lunar
 * month (on its first day) > lunar day. The full string still appears verbatim in the day detail.
 */
fun lunarCellLabel(info: LunarInfo?): String {
    if (info == null) return ""
    info.term?.let { return it }
    return if (info.lDay == 1) info.monthCn else info.dayCn
}
