package org.shuttlelab.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 农历移植的**对账测试**:拿 Kotlin 的 [Lunar] 去比 JS 原库的输出。
 *
 * 为什么是这种形式,而不是手写几个"我知道的日子":农历算错从来不是均匀地错,而是**只在某些
 * 年份错一天** —— 闰月边界、节气跨日、小月除夕。手挑十个日子几乎必然全部落在正确的区间里,
 * 测试绿着,而用户在某个春节前一天发现 App 和网页对不上。
 *
 * 夹具 `src/test/resources/lunar_fixture.txt` 是用 Web 端**正在用的**那个包
 * (js-calendar-converter v0.0.7)跑出来的:1900-02 ~ 2100-12 每 23 天一个样本,外加一批钉死的
 * 边界与节日(1900-01-31 下限、历年春节/除夕、闰月、节气日),共三千余行。每行:
 *
 *   公历|农历年|农历月|农历日|是否闰月|农历月中文|农历日中文|节气|阳历节日|农历节日|生肖|星期
 *
 * 重新生成:`node scripts/gen_lunar_fixture.mjs > app/src/test/resources/lunar_fixture.txt`
 * (需要 Web 项目在本地,且 node_modules 已安装)。**改 [Lunar] 时不要重新生成夹具** —— 那等于
 * 把答案改成和新代码一致,测试就失去了意义;只有升级 js-calendar-converter、并确认 Web 端也
 * 一起升级时才重新生成。
 *
 * EN: a RECONCILIATION test — Kotlin's [Lunar] against the JS original's output.
 *
 * Why this shape rather than a handful of dates someone knows: lunar code does not fail uniformly,
 * it fails BY ONE DAY IN SOME YEARS — leap-month boundaries, terms either side of midnight, New
 * Year's Eve in a short month. Ten hand-picked dates almost certainly all sit inside correct
 * stretches, the test stays green, and a user finds the app disagreeing with the website the day
 * before some Spring Festival.
 *
 * The fixture is produced by the very package the web app uses (js-calendar-converter v0.0.7):
 * a sample every 23 days from 1900-02 to 2100-12 plus pinned boundaries and festivals (the
 * 1900-01-31 lower limit, Spring Festivals and eves, leap months, term days) — three thousand-odd
 * lines. Regenerate with the script above, and do NOT regenerate it while changing [Lunar]: that
 * would edit the answers to agree with the new code and the test would mean nothing. Regenerate
 * only when upgrading js-calendar-converter, and only together with the web side.
 */
class LunarParityTest {

    private data class Row(
        val date: LocalDate,
        val lYear: Int,
        val lMonth: Int,
        val lDay: Int,
        val isLeap: Boolean,
        val monthCn: String,
        val dayCn: String,
        val term: String,
        val festival: String,
        val lunarFestival: String,
        val animal: String,
        val nWeek: Int,
    )

    private fun fixture(): List<Row> {
        val stream = javaClass.getResourceAsStream("/lunar_fixture.txt")
            ?: error("lunar_fixture.txt 不在测试资源里 —— 见本文件头部的生成命令")
        return stream.bufferedReader().readLines()
            .filter { it.isNotBlank() }
            .map { line ->
                val f = line.split("|")
                Row(
                    date = LocalDate.parse(f[0]),
                    lYear = f[1].toInt(),
                    lMonth = f[2].toInt(),
                    lDay = f[3].toInt(),
                    isLeap = f[4] == "1",
                    monthCn = f[5],
                    dayCn = f[6],
                    term = f[7],
                    festival = f[8],
                    lunarFestival = f[9],
                    animal = f[10],
                    nWeek = f[11].toInt(),
                )
            }
    }

    @Test
    fun `每一个样本都与 JS 原库一致`() {
        val rows = fixture()
        // 夹具本身也要检查:一个空文件会让下面的循环"全部通过"。
        // EN: the fixture is checked too — an empty file would make the loop below pass vacuously.
        assertTrue("夹具样本太少,可能没生成对", rows.size > 3000)

        for (row in rows) {
            val got = Lunar.solar2Lunar(row.date.year, row.date.monthValue, row.date.dayOfMonth)
            assertNotNull("${row.date}: 返回了 null", got)
            got!!
            assertEquals("${row.date}: 农历年", row.lYear, got.lYear)
            assertEquals("${row.date}: 农历月", row.lMonth, got.lMonth)
            assertEquals("${row.date}: 农历日", row.lDay, got.lDay)
            assertEquals("${row.date}: 闰月标记", row.isLeap, got.isLeap)
            assertEquals("${row.date}: 农历月中文", row.monthCn, got.monthCn)
            assertEquals("${row.date}: 农历日中文", row.dayCn, got.dayCn)
            assertEquals("${row.date}: 节气", row.term, got.term ?: "")
            assertEquals("${row.date}: 阳历节日", row.festival, got.festival ?: "")
            assertEquals("${row.date}: 农历节日", row.lunarFestival, got.lunarFestival ?: "")
            assertEquals("${row.date}: 生肖", row.animal, got.animal)
            assertEquals("${row.date}: 星期", row.nWeek, got.nWeek)
        }
    }

    @Test
    fun `夹具里确实覆盖了闰月与节气`() {
        // 这条测试保护的是**上一条测试的价值**:夹具如果恰好一个闰月、一个节气都没采到,
        // 上面那个循环全绿也说明不了什么,而这正是最容易出错的两类日子。
        // EN: this guards the VALUE of the test above: if the fixture happened to contain no leap
        // month and no solar term, that loop could pass while saying nothing about the two kinds of
        // date most likely to be wrong.
        val rows = fixture()
        assertTrue("夹具里没有闰月样本", rows.count { it.isLeap } > 30)
        assertTrue("夹具里没有节气样本", rows.count { it.term.isNotEmpty() } > 100)
        assertTrue("夹具里没有农历节日样本", rows.count { it.lunarFestival.isNotEmpty() } > 5)
    }

    @Test
    fun `区间之外返回 null 而不是错误的日期`() {
        // 原库在越界时返回数字 -1;这里返回 null。会走到这条路径的是"用户一直往前翻月"。
        // EN: the original returns the number -1 out of range; this returns null. The way to get
        // here is a user paging months backwards far enough.
        assertNull(Lunar.solar2Lunar(1899, 12, 31))
        assertNull(Lunar.solar2Lunar(1900, 1, 30)) // 下限是 1900-01-31
        assertNull(Lunar.solar2Lunar(3001, 1, 1))
        assertNotNull(Lunar.solar2Lunar(1900, 1, 31))
    }

    @Test
    fun `格子里的农历标签 —— 节气优先,初一显示月份`() {
        // 这是唯一与 Web 不同的一处显示逻辑(见 lunarCellLabel 的注释),所以它需要自己的测试 ——
        // 夹具对不上它,因为 Web 端压根没有这个函数。
        // EN: the one display rule that differs from the web (see lunarCellLabel), so it needs its
        // own test — the fixture cannot cover it, the web having no such function.
        val springFestival = Lunar.solar2Lunar(2026, 2, 17) // 正月初一
        assertEquals("正月", lunarCellLabel(springFestival))

        val winterSolstice = Lunar.solar2Lunar(2026, 12, 22) // 冬至
        assertEquals("冬至", lunarCellLabel(winterSolstice))

        val ordinary = Lunar.solar2Lunar(2026, 9, 17)
        assertEquals(ordinary!!.dayCn, lunarCellLabel(ordinary))

        assertEquals("", lunarCellLabel(null))
    }

    @Test
    fun `详情页的完整农历串与 Web 的 lunarDisplay 同格式`() {
        // Web: `${IMonthCn}(${数字月})${IDayCn}`,有节气时只显示节气名。
        // 用字面量而不是拿同一组函数拼出期望值:后者等于用实现去验证实现,改坏了照样绿。
        // 「八月(八月)初七」看着别扭,那正是 Web 当前的行为,见 lunarDisplay 的说明 —— 这条断言
        // 失败就意味着有人只改了 App 这一边。
        // EN: literals rather than expectations assembled from the same functions, which would be
        // the implementation checking itself. 八月(八月)初七 does read oddly, and that is exactly
        // what the web renders today (see lunarDisplay) — this failing means someone changed only
        // the app side.
        assertEquals("八月(八月)初七", lunarDisplay(Lunar.solar2Lunar(2026, 9, 17)))
        assertEquals("腊月(十二月)廿九", lunarDisplay(Lunar.solar2Lunar(2025, 1, 28)))
        assertEquals("正月(一月)初一", lunarDisplay(Lunar.solar2Lunar(2026, 2, 17)))
        assertEquals("冬至", lunarDisplay(Lunar.solar2Lunar(2026, 12, 22)))
        assertEquals("", lunarDisplay(null))
    }
}
