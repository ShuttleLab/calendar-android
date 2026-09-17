package org.shuttlelab.calendar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 节假日数据的取用:网络 → 本地缓存 → 随包快照,三级兜底。
 *
 * 三级不是过度设计,而是这份数据的性质决定的:
 *   - 它**一年只变几次**(国务院公布次年安排时),所以缓存可以放心地放很久,没必要每次打开都联网;
 *   - 它又是**日历的核心显示内容**:格子的绿(放假)与红(补班)全靠它。一个日历在飞机上、在
 *     地铁里打开却什么都标不出来,那就是坏了 —— 所以必须有随包快照,首装即离线可用;
 *   - Web 端可以直接依赖网络(打开网页本身就需要网),App 不能照抄这个假设。
 *
 * 与 Web 端的一处**实现**差异(不是显示差异):Web 走自己的 `/api/holiday`,因为浏览器直连
 * cdn.1htr.cn 容易 403/ORB。App 没有同源策略,直连即可,少一跳也少一个故障点。解析用的是同一组
 * 正则(见 [Holiday]),所以拿到的集合与 Web 完全一致。
 *
 * EN: getting holiday data — network, then local cache, then a snapshot shipped in the APK.
 * Three tiers is not over-engineering but what this data is like: it changes a few times a YEAR
 * (when next year's schedule is published), so a long cache is safe and going online at every
 * launch is pointless; and it is the calendar's core content — the green (day off) and red
 * (make-up workday) cells depend on it entirely, so a calendar that marks nothing on a plane or in
 * a tunnel is simply broken, hence the bundled snapshot making a fresh install useful offline. The
 * web can assume the network (opening a web page needs it); an app cannot copy that assumption.
 *
 * One IMPLEMENTATION difference from the web (not a display one): the web proxies through its own
 * /api/holiday because a browser hitting cdn.1htr.cn directly tends to get 403/ORB. An app has no
 * same-origin policy, so it fetches directly — one hop fewer, one failure point fewer. The parsing
 * regexes are the same ones ([Holiday]), so the resulting sets match the web exactly.
 */
object HolidayRepo {

    /** 缓存文件名(放 filesDir,不是 cacheDir —— 系统清缓存时不该丢掉离线可用性)。 */
    private const val CACHE_FILE = "holiday.js"

    /** 随包快照,首装离线可用的兜底。 */
    private const val ASSET_FILE = "holiday.js"

    /**
     * 缓存有效期:平时 30 天,**缺次年安排且已进入第四季度时缩到 1 天**。
     *
     * 为什么不是一个固定值:这份数据一年只变几次,所以绝大多数时候 30 天都嫌勤快 —— 固定一个
     * 很长的 TTL 看起来很合理。但它有一个明确的、每年都会到来的例外:国务院通常在 **11 月前后**
     * 公布次年的放假安排,而那恰恰是全年最需要立刻拿到新数据的时刻(大家正在订票、排假)。
     * 固定 30 天意味着最坏情况下要等到 12 月才看到明年元旦和春节的标记;固定 90 天则可能整个
     * 假期都错过。
     *
     * 所以 TTL 由**数据自己**决定:看缓存里有没有次年的日期。没有、且当前已是 10 月之后,就认为
     * "正等着那次公布",每天问一次;其余时候 30 天。这样既没有把 365 天的等待写死,也没有为了
     * 一年一次的更新让所有人天天联网 —— 而且它不需要任何人记得在某个月改一次常量。
     *
     * EN: a 30-day TTL, dropping to one day when the cache holds no dates for next year and the
     * fourth quarter has begun. Not a single constant, because this data has one predictable
     * exception each year: next year's schedule is published around November, exactly when it is
     * most wanted (people are booking travel). A flat 30 days can leave someone without next
     * January's marks until December, and a flat 90 days can miss the holiday entirely. So the TTL
     * is decided by the DATA: if the cache has nothing for next year and it is past October, assume
     * we are waiting for that announcement and ask daily; otherwise 30 days. No 365-day wait baked
     * in, no daily request for everyone all year, and nothing for anyone to remember to change.
     */
    private const val TTL_NORMAL = 30L * 24 * 60 * 60 * 1000
    private const val TTL_AWAITING_NEXT_YEAR = 24L * 60 * 60 * 1000

    /** 缓存里是否已经有次年的放假日期。 */
    private fun coversNextYear(data: Holiday.Data): Boolean {
        val nextYear = LocalDate.now().year + 1
        return data.holidays.any { it.startsWith("$nextYear-") }
    }

    private fun ttlFor(data: Holiday.Data): Long =
        if (!coversNextYear(data) && LocalDate.now().monthValue >= 10) {
            TTL_AWAITING_NEXT_YEAR
        } else {
            TTL_NORMAL
        }

    /** 数据来源,给界面提示用。 */
    enum class Source { NETWORK, CACHE, BUNDLED, NONE }

    data class Loaded(val data: Holiday.Data, val source: Source)

    /**
     * 取节假日数据。[force] 为 true 时无视缓存有效期直接联网(设置页的「刷新」用)。
     *
     * 任何一步失败都往下一级退,**不抛异常** —— 日历的其余部分(公历、农历、节气)完全不依赖
     * 这份数据,不能因为它取不到就让整页起不来。
     *
     * EN: load holiday data; [force] ignores the TTL and goes to the network (Settings → refresh).
     * Every failure falls through to the next tier and NOTHING is thrown: the rest of the calendar
     * (solar, lunar, terms) does not depend on this data at all, so failing to fetch it must never
     * take the page down with it.
     */
    suspend fun load(ctx: Context, force: Boolean = false): Loaded = withContext(Dispatchers.IO) {
        // 先解析缓存,再判断它是否还新鲜 —— 顺序不能反:有效期取决于**缓存里有什么**
        // (见 [ttlFor]),没解析之前无从判断。
        // EN: parse the cache before judging its freshness — the TTL depends on WHAT IS IN IT
        // ([ttlFor]), which cannot be known before parsing.
        val cached = readCache(ctx)
        val cachedData = cached?.let { Holiday.parse(it.first) }
        if (cachedData != null && !force &&
            System.currentTimeMillis() - cached.second < ttlFor(cachedData)
        ) {
            return@withContext Loaded(cachedData, Source.CACHE)
        }

        fetch()?.let { script ->
            Holiday.parse(script)?.let { parsed ->
                writeCache(ctx, script)
                return@withContext Loaded(parsed, Source.NETWORK)
            }
        }

        // 联网失败/解析失败:用缓存,哪怕过期 —— 去年的放假安排也远胜于一片空白。
        // EN: network or parse failed — use the cache even when stale; last year's schedule beats
        // a blank calendar.
        if (cachedData != null) return@withContext Loaded(cachedData, Source.CACHE)
        readAsset(ctx)?.let { Holiday.parse(it) }?.let {
            return@withContext Loaded(it, Source.BUNDLED)
        }
        Loaded(Holiday.Data(), Source.NONE)
    }

    /**
     * 拉取数据源。超时给得不长:这份数据取不到也只是少了颜色标记,让用户对着转圈等 30 秒
     * 换不来什么。
     * EN: fetch the source. The timeouts are deliberately short — failing here only costs the
     * colour marks, and making someone watch a spinner for 30 seconds buys nothing.
     */
    private fun fetch(): String? = try {
        val conn = (URL(Holiday.SCRIPT_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            // 与 Web 端的 Pages Function 用同一个 UA,便于数据源那边区分来源。
            setRequestProperty("User-Agent", "CalendarShuttle/1.0 (Android)")
        }
        try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    /** 读缓存:返回 (脚本全文, 写入时刻);没有缓存返回 null。 */
    private fun readCache(ctx: Context): Pair<String, Long>? {
        val f = File(ctx.filesDir, CACHE_FILE)
        if (!f.exists() || f.length() == 0L) return null
        return try {
            f.readText() to f.lastModified()
        } catch (_: Exception) {
            null
        }
    }

    /** 写缓存。写失败只影响下次要不要联网,不影响本次显示,所以静默。 */
    private fun writeCache(ctx: Context, script: String) {
        try {
            File(ctx.filesDir, CACHE_FILE).writeText(script)
        } catch (_: Exception) {
        }
    }

    /** 读随包快照。 */
    private fun readAsset(ctx: Context): String? = try {
        ctx.assets.open(ASSET_FILE).bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
