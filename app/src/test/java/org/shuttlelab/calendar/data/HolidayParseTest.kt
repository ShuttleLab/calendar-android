package org.shuttlelab.calendar.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 节假日解析测试,喂给它的是**随包的那份真数据**(`app/src/main/assets/holiday.js`),不是
 * 手写的小样本。
 *
 * 理由:这里的正则是照 Web 端的 Cloudflare Function 抄来的,而正则最典型的失效方式不是"写错了
 * 语法",是**只对我构造的那个例子有效** —— 真实文件里有尾逗号、单双引号混用、跨行数组、一个
 * `'9-3w6'` 这样的异类键。拿真文件测,这些全都自动覆盖到。
 *
 * 顺带:随包快照是首装离线时唯一的数据来源,所以"它能被解析"本身就是一条必须成立的事实 ——
 * 快照哪天换了一份格式不同的,这个测试会在打包之前就红。
 *
 * EN: holiday-parsing tests fed with the REAL bundled data (app/src/main/assets/holiday.js) rather
 * than a hand-written sample. The regexes here are copied from the web's Cloudflare Function, and
 * the classic way a regex fails is not bad syntax but working ONLY on the example it was written
 * against — while the real file has trailing commas, mixed quote styles, arrays spanning lines and
 * an odd key like '9-3w6'. Testing against the real file covers all of that for free.
 *
 * It also pins a fact that must hold: the bundled snapshot is the only data a fresh offline install
 * has, so "it parses" is load-bearing, and replacing it with a differently shaped file turns this
 * red before a build ships.
 */
class HolidayParseTest {

    /**
     * 找到随包快照。JVM 单元测试的工作目录是模块目录(`app/`),但用 Gradle 之外的方式跑
     * (IDE 的某些配置)可能是仓库根,所以两条路径都试一下 —— 这点小事不值得让测试因环境而红。
     * EN: locate the bundled snapshot. A JVM unit test's working directory is the module dir
     * (app/), but running it another way (some IDE configurations) can put it at the repo root, so
     * both are tried — not worth a red test over.
     */
    private fun snapshot(): String {
        val candidates = listOf(
            File("src/main/assets/holiday.js"),
            File("app/src/main/assets/holiday.js"),
        )
        val f = candidates.firstOrNull { it.exists() }
            ?: error("找不到 holiday.js 快照,试过:" + candidates.joinToString { it.absolutePath })
        return f.readText()
    }

    @Test
    fun `随包快照能被解析,且三部分都不为空`() {
        val data = Holiday.parse(snapshot())
        assertNotNull("随包快照解析失败 —— 首装离线时将没有任何节假日标记", data)
        data!!
        assertTrue("放假日期为空", data.holidays.size > 50)
        assertTrue("补班日期为空", data.workdays.size > 10)
        assertTrue("节日表为空", data.festivals.size > 50)
    }

    @Test
    fun `日期一律规范成补零形式`() {
        // 数据源里写的是 '2026-1-1',查询用的是 '2026-01-01'。这一步漏掉的话,一整月的格子
        // 都查不到自己 —— 而界面看起来完全正常,只是什么都不标。
        // EN: the source writes '2026-1-1' while lookups use '2026-01-01'. Skip this and a whole
        // month of cells fails to find itself, with the UI looking perfectly fine and marking nothing.
        assertEquals("2026-01-01", Holiday.normalizeDateKey("2026-1-1"))
        assertEquals("2026-01-01", Holiday.normalizeDateKey("2026-01-01"))
        assertEquals("2026-10-10", Holiday.normalizeDateKey("2026-10-10"))
        // 解析不了的原样返回,不抛异常:数据源多一个奇怪的条目不该让整个日历起不来。
        assertEquals("garbage", Holiday.normalizeDateKey("garbage"))

        val data = Holiday.parse(snapshot())!!
        assertTrue("元旦没被认成放假", data.holidays.contains("2026-01-01"))
        assertTrue("国庆没被认成放假", data.holidays.contains("2026-10-01"))
        assertTrue("2026-01-04 是补班日", data.workdays.contains("2026-01-04"))
    }

    @Test
    fun `同一天既是补班又有节日时,先说补班`() {
        // 2026-10-10 是调休补班日,而 '10-10' 在节日表里有三条纪念日。顺序反了的话,用户在
        // 这一天看到的是"世界精神卫生日",而不是"今天要上班"——后者才是他当天真正要知道的。
        // EN: 2026-10-10 is a make-up workday while '10-10' carries three observances. Reversed,
        // the user sees "World Mental Health Day" instead of "you are working today", and the
        // latter is what actually matters that morning.
        val data = Holiday.parse(snapshot())!!
        val info = Holiday.dayInfo("2026-10-10", "10-10", data)
        assertEquals(Holiday.Kind.WORK, info.kind)
        assertTrue("补班日也应带上当天的节日名", info.names.isNotEmpty())
    }

    @Test
    fun `只有节日的普通日子是 DAY,什么都没有的是 NONE`() {
        val data = Holiday.parse(snapshot())!!
        val valentines = Holiday.dayInfo("2027-02-14", "2-14", data)
        assertEquals(Holiday.Kind.DAY, valentines.kind)
        assertEquals(listOf("情人节"), valentines.names)

        val nothing = Holiday.dayInfo("2027-03-19", "3-19", data)
        assertEquals(Holiday.Kind.NONE, nothing.kind)
        assertTrue(nothing.names.isEmpty())
    }

    @Test
    fun `解析不出结构时返回 null,而不是一份空数据`() {
        // 调用方靠这个区别决定"保留旧缓存还是覆盖":返回空数据会让一次失败的刷新把屏幕上
        // 原本正确的绿红标记全部抹掉。
        // EN: callers use this distinction to decide whether to keep the previous cache; returning
        // empty data would let one failed refresh wipe correct marks off the screen.
        assertNull(Holiday.parse(""))
        assertNull(Holiday.parse("window.syncModule = {}"))
        assertNull(Holiday.parse("<html>404 Not Found</html>"))
    }

    @Test
    fun `按周计的键不参与查询,与 Web 保持一致`() {
        // '9-3w6'(九月第三个周六)在数据源里存在,Web 端只用 'M-D' 查表所以从不命中。这里
        // 同样不命中 —— 这条测试钉住的是"两端一致",而不是"这个功能做好了"。
        // EN: '9-3w6' (the third Saturday of September) exists in the source but the web only looks
        // up 'M-D', so it never matches there. It must not match here either — this test pins
        // AGREEMENT WITH THE WEB, not a feature.
        val data = Holiday.parse(snapshot())!!
        assertTrue("快照里应当有 9-3w6 这个键", data.festivals.containsKey("9-3w6"))
        val thirdSaturday = Holiday.dayInfo("2026-09-19", "9-19", data)
        assertTrue("按周计的节日不该出现在某个 M-D 上", thirdSaturday.names.none { it.contains("国防") })
    }
}
