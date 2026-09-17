package org.shuttlelab.calendar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.shuttlelab.calendar.data.Holiday
import org.shuttlelab.calendar.data.HolidayRepo
import org.shuttlelab.calendar.data.Lang
import org.shuttlelab.calendar.data.Prefs
import java.time.LocalDate
import java.time.YearMonth

/**
 * 全应用的状态与动作。状态用 `var … by mutableStateOf(…)` 而不是 StateFlow,页面直接拿 `vm` ——
 * 与 secretary-android 同一套做法:这个应用没有并发的数据流要合并,一层 Flow 只是多一层样板。
 *
 * EN: app-wide state and actions. State is `var … by mutableStateOf(…)` rather than StateFlow and
 * screens take `vm` directly — the same approach as secretary-android: there are no concurrent
 * streams to combine here, so a Flow layer would only add boilerplate.
 */
class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    // ---- 偏好 ----
    var themePref by mutableStateOf(Prefs.theme(ctx))
        private set
    var useDynamicColor by mutableStateOf(Prefs.dynamicColor(ctx))
        private set
    var hapticsEnabled by mutableStateOf(Prefs.haptics(ctx))
        private set

    fun setTheme(t: String) {
        themePref = t
        Prefs.put(ctx, Prefs.KEY_THEME, t)
    }

    fun setDynamicColor(on: Boolean) {
        useDynamicColor = on
        Prefs.setDynamicColor(ctx, on)
    }

    fun setHaptics(on: Boolean) {
        hapticsEnabled = on
        Prefs.setHaptics(ctx, on)
    }

    fun setLang(lang: String) = Lang.set(ctx, lang)

    // ---- 导航 ----
    /**
     * 全部页面。[Calendar] 是唯一的根 —— 这个应用只有一件事可做,没有并列的 tab,所以不设底栏。
     * secretary 有三个互为根的 tab 才需要 NavigationBar;这里加一条只有一个图标的底栏纯属摆设,
     * 还要吃掉一行屏幕高度,而日历格子恰恰最缺高度。
     *
     * EN: every page. [Calendar] is the only root — the app does one thing, so there are no peer
     * tabs and no bottom bar. secretary needs a NavigationBar because it has three tabs that are
     * roots of their own; a one-item bar here would be decoration that costs a row of screen
     * height, and the day grid is precisely what wants that height.
     */
    enum class Screen { Calendar, Settings, About }

    var screen by mutableStateOf(Screen.Calendar)
        private set

    /**
     * 最近一次导航的方向。转场动画靠它决定往哪边滑 —— 没有方向,前进和返回的动效一模一样,
     * 用户就失去了"我在往里走还是在退出来"这条免费的空间线索。
     * EN: the direction of the last navigation; transitions use it to pick which way to slide.
     * Without it, descending and backing out animate identically and that free spatial cue is lost.
     */
    enum class Nav { Forward, Back }

    var navDirection by mutableStateOf(Nav.Forward)
        private set

    private val backStack = ArrayDeque<Screen>()
    private val maxBackDepth = 8

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun navigate(to: Screen) {
        if (to == screen) return
        navDirection = Nav.Forward
        backStack.addLast(screen)
        while (backStack.size > maxBackDepth) backStack.removeFirst()
        screen = to
    }

    /** 返回上一页;栈空时返回 false,交给系统(退出 App)。 */
    fun back(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        navDirection = Nav.Back
        screen = prev
        return true
    }

    // ---- 日历 ----
    /**
     * "今天"。**不是**常量:App 可以在后台挂一整夜,第二天回到前台时,今日高亮必须落在新的
     * 一天上,而不是昨天。所以它是状态,由 [onResume] 重新取(Web 端同理,在 `useEffect` 里
     * 取而不是在构建期取 —— 那边的注释写的是"避免静态构建时用构建机日期导致高亮错位")。
     *
     * EN: "today" is NOT a constant. The app can sit in the background overnight, and on returning
     * to the foreground the today-highlight has to land on the new day rather than yesterday — so
     * it is state, re-read by [onResume]. The web does the same thing for its own reason (reading
     * it in an effect rather than at build time, or a static build would bake in the build
     * machine's date).
     */
    var today by mutableStateOf(LocalDate.now())
        private set

    /** 当前显示的月份。 */
    var viewMonth by mutableStateOf(YearMonth.now())
        private set

    /** 选中的日期(点格子);null 表示没有选中,详情卡片收起。 */
    var selectedDate by mutableStateOf<LocalDate?>(null)
        private set

    /** 翻月方向,给月份网格的转场用:+1 下一月,-1 上一月,0 跳转(回到今天)。 */
    var monthDelta by mutableStateOf(0)
        private set

    fun prevMonth() {
        monthDelta = -1
        viewMonth = viewMonth.minusMonths(1)
    }

    fun nextMonth() {
        monthDelta = 1
        viewMonth = viewMonth.plusMonths(1)
    }

    /**
     * 回到今天:既跳月,也把今天选中。
     *
     * 两件事一起做而不是只跳月:用户点「今天」通常是想**看今天怎么样**(放假吗?农历几号?),
     * 顺手选中就把详情摊开了,省掉紧接着的那一次点击。
     *
     * 方向取决于往哪边跳,这样从半年后回到今天时,网格是往回滑的 —— 与翻月的方向语义一致。
     *
     * EN: back to today — jump the month AND select today. Both, because tapping "Today" usually
     * means wanting to know about today (a day off? which lunar date?), and selecting it opens the
     * detail, saving the tap that would follow. The direction follows which way the jump goes, so
     * returning from six months ahead slides backwards, consistent with what a month change means.
     */
    fun goToday() {
        val target = YearMonth.from(today)
        monthDelta = target.compareTo(viewMonth).coerceIn(-1, 1)
        viewMonth = target
        selectedDate = today
    }

    fun select(date: LocalDate) {
        // 再点一次已选中的日期 = 收起详情。点开点关是同一个动作,不该逼用户去找关闭按钮
        // (关闭按钮仍然保留 —— 与 Web 一致)。
        // EN: tapping the selected day again closes the detail. Opening and closing is the same
        // gesture; nobody should have to hunt for the close button (which is still there, as on
        // the web).
        selectedDate = if (selectedDate == date) null else date
    }

    fun clearSelection() {
        selectedDate = null
    }

    /**
     * 回到前台:重取"今天"。跨过午夜再回来时,高亮必须跟着走 —— 这是日历应用最容易被发现的
     * 错误之一,而它只在"应用开着过了一夜"时出现,平时测不到。
     * EN: on returning to the foreground, re-read today. Crossing midnight with the app alive must
     * move the highlight — one of the most visible bugs a calendar can have, and one that only
     * appears after the app has been left open overnight.
     */
    fun onResume() {
        val now = LocalDate.now()
        if (now != today) today = now
    }

    // ---- 节假日数据 ----
    var holiday by mutableStateOf(Holiday.Data())
        private set
    var holidayLoading by mutableStateOf(true)
        private set
    var holidaySource by mutableStateOf(HolidayRepo.Source.NONE)
        private set

    /** 一次性提示(Snackbar)。 */
    var snack by mutableStateOf<String?>(null)

    init {
        loadHolidays(force = false)
    }

    /**
     * 载入节假日数据。[force] 为 true 时无视缓存有效期联网(设置页的「刷新」)。
     *
     * 失败时**保留**已有数据、只把来源标成兜底 —— 刷新失败不该把屏幕上已经正确显示的绿红格子
     * 清空。数据为空时界面上会说明(见 MonthCalendar 的提示行)。
     *
     * EN: load the holiday data; [force] ignores the TTL. On failure the existing data is KEPT and
     * only the source label changes: a failed refresh must not wipe correct green/red marks
     * already on screen. When there is no data at all the UI says so (see MonthCalendar's hint).
     */
    fun loadHolidays(force: Boolean) {
        holidayLoading = true
        viewModelScope.launch {
            val loaded = HolidayRepo.load(ctx, force)
            holidayLoading = false
            holidaySource = loaded.source
            if (!loaded.data.isEmpty) holiday = loaded.data
            if (force) {
                snack = when (loaded.source) {
                    HolidayRepo.Source.NETWORK -> Lang.t("Holiday data updated", "节假日数据已更新")
                    else -> Lang.t(
                        "Could not reach the source — showing the data already on the device.",
                        "数据源没连上 —— 显示的是设备上已有的数据。",
                    )
                }
            }
        }
    }

    /** 某一天的节假日信息(格子与详情共用同一条查询,免得两处判断不一致)。 */
    fun dayInfo(date: LocalDate): Holiday.DayInfo = Holiday.dayInfo(
        Holiday.dateKey(date.year, date.monthValue, date.dayOfMonth),
        Holiday.monthDayKey(date.monthValue, date.dayOfMonth),
        holiday,
    )
}
