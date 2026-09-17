package org.shuttlelab.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.coroutines.cancellation.CancellationException
import org.shuttlelab.calendar.data.Lang
import org.shuttlelab.calendar.ui.AboutScreen
import org.shuttlelab.calendar.ui.CalendarScreen
import org.shuttlelab.calendar.ui.CalendarViewModel
import org.shuttlelab.calendar.ui.Motion
import org.shuttlelab.calendar.ui.SettingsScreen
import org.shuttlelab.calendar.ui.rememberHaptics
import org.shuttlelab.calendar.ui.rememberReduceMotion
import org.shuttlelab.calendar.ui.theme.CalendarTheme
import org.shuttlelab.calendar.ui.theme.brandPrimary

/**
 * 唯一的 Activity。
 *
 * 这个应用不需要权限、不需要推送、不需要后台 —— 农历与节气在本机算,节假日是一次 GET。所以
 * 这里只剩三件事:声明 edge-to-edge、初始化语言、把 Compose 树挂上去。
 *
 * EN: the only activity. This app needs no permissions, no push and no background work — the lunar
 * calendar is computed locally and the holiday schedule is one GET — so three things remain here:
 * declaring edge-to-edge, initialising the language, and hosting the Compose tree.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 显式声明 edge-to-edge。targetSdk 36 下系统本来就强制开启(SDK 35 起默认,36 起 opt-out
        // 被移除),但显式调用有两个作用:在 Android 15 以下也得到一致观感,并且让"内容会画到
        // 系统栏底下、必须自己处理 insets"在代码里是**写明的**,而不是靠某个 SDK 版本的默认行为。
        // EN: declare edge-to-edge explicitly. The system enforces it at targetSdk 36 anyway
        // (default from 35, opt-out removed in 36), but calling it gives the same look below
        // Android 15 and makes "content draws under the system bars and we handle insets
        // ourselves" a stated fact rather than a silent consequence of one SDK level's defaults.
        enableEdgeToEdge()
        Lang.init(this)
        setContent {
            val vm: CalendarViewModel = viewModel()
            // 回到前台时重取"今天"。挂在 Compose 侧而不是 Activity.onResume:onResume 在
            // setContent 之前也会跑,那时 vm 还不存在;LifecycleEventEffect 只在有组合时触发。
            // EN: re-read "today" when returning to the foreground. On the Compose side rather
            // than Activity.onResume, which also runs before setContent when there is no vm yet;
            // LifecycleEventEffect only fires while composed.
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
            CalendarTheme(themePref = vm.themePref, dynamicColor = vm.useDynamicColor) {
                AppRoot(vm)
            }
        }
    }
}

@Composable
private fun AppRoot(vm: CalendarViewModel) {
    // 全应用一个 Snackbar 宿主,挂在所有页面之上 —— 放在根上,提示的存活就与"当前显示哪一页"
    // 无关(从设置页点刷新、随即返回日历页,提示仍在)。
    // EN: one app-wide snackbar host above every page. At the root, a message's lifetime is
    // independent of which page is showing — tap Refresh in Settings, go back, and it is still there.
    val hostState = remember { SnackbarHostState() }
    val snack = vm.snack
    // 清空必须在 showSnackbar **之后**。
    //
    // 直觉写法是先把 vm.snack 置空再去显示("取走这条消息"),但那样恰好把自己掐掉:snack 是
    // 这个 LaunchedEffect 的 key,置空会让 key 变化 → 旧协程被取消 → 还在挂起的 showSnackbar
    // 随之取消 → 提示条闪一下就没了。改成显示完再清空,key 在显示期间保持不变。
    //
    // 代价是"连着两次完全相同的消息"只显示一次(key 没变):第一条还挂在屏幕上,第二次点刷新
    // 得到同样的结果时不会重放。这个取舍是对的 —— 用户要的信息已经在屏幕上了。
    //
    // EN: clear AFTER showSnackbar. The intuitive order is to null the field first ("take the
    // message"), but that cuts the effect's own throat: snack is this LaunchedEffect's key, so
    // nulling it changes the key, cancels the coroutine and cancels the suspended showSnackbar
    // with it — the message flashes and disappears. Clearing afterwards keeps the key stable for
    // as long as it is displayed. The cost is that two identical messages in a row show once,
    // which is the right trade: the information is already on screen.
    LaunchedEffect(snack) {
        if (snack == null) return@LaunchedEffect
        hostState.showSnackbar(
            message = snack,
            withDismissAction = true,
            duration = SnackbarDuration.Short,
        )
        vm.snack = null
    }

    /*
     * 系统返回键 + 预测式返回。
     *
     * 没有它时,在设置页/关于页按手机的返回键会**直接退出应用** —— 两页都有自己的返回箭头,
     * 唯独系统返回键没接。栈空(在日历页)时不拦截,交回系统去退出。
     *
     * 用 `PredictiveBackHandler` 而不是 `BackHandler`:targetSdk 36 下预测式返回默认开启,但
     * 旧的 BackHandler 拦截事件后系统无从知道我们要返回,于是不画预览动画,手势拖到一半松手
     * 只是内容突然换掉。这里消费手势进度、不自己做变换 —— 页面转场由下面的 AnimatedContent
     * 负责,系统提供窗口层面的预览,两边各管一层。
     *
     * `completion` 正常走完才是真要返回;抛出 CancellationException 表示手势被取消,那时**什么
     * 都不能做** —— 在 finally 里执行返回是经典错误,会让取消手势也退了页。
     *
     * EN: system Back plus predictive back. Without it, pressing Back on Settings or About quits
     * the app outright: both pages have their own arrow but the phone's button was never wired.
     * With an empty stack (on the calendar) it stays disabled so the system exits as usual.
     * PredictiveBackHandler rather than BackHandler because predictive back is on by default at
     * targetSdk 36, but the old handler consumes the event and leaves the system no way to know we
     * intend to go back — so it draws no preview and a half-dragged gesture just swaps content.
     * The progress is consumed without being turned into our own transform: page transitions are
     * AnimatedContent's job below and the system supplies the window-level preview. Reaching
     * completion means the user really is going back; a CancellationException means the gesture was
     * cancelled and then nothing must happen — navigating in a finally block is the classic mistake
     * that makes a cancelled gesture leave the page anyway.
     */
    PredictiveBackHandler(enabled = vm.canGoBack) { progress ->
        try {
            progress.collect { /* 系统负责画预览 */ }
            vm.back()
        } catch (_: CancellationException) {
            // 手势被取消:停留在当前页。
        }
    }

    val reduceMotion = rememberReduceMotion()

    Box(Modifier.fillMaxSize()) {
        // 页面转场。方向来自导航栈,所以"往里走"和"退出来"看起来不一样 —— 那是一条免费的空间
        // 线索,少了它每次换页都只是内容突然被替换掉。位移只用四分之一屏宽:整屏平移看起来像
        // 在搬运界面,而 M3 的转场是让新内容在原地成形,位移只提示来向。
        // EN: page transitions. The direction comes from the navigation stack so descending and
        // backing out look different — a free spatial cue, without which changing pages is just
        // content being swapped. The offset is a quarter of the width: a full-width translation
        // looks like the UI is being carted around, whereas an M3 transition has the new content
        // form in place and uses displacement only to hint where it came from.
        AnimatedContent(
            targetState = vm.screen,
            transitionSpec = {
                when {
                    reduceMotion -> EnterTransition.None togetherWith ExitTransition.None
                    vm.navDirection == CalendarViewModel.Nav.Forward ->
                        (slideInHorizontally(Motion.pageSpec()) { it / 4 } +
                            fadeIn(Motion.pageSpec())) togetherWith
                            (slideOutHorizontally(Motion.exitSpec()) { -it / 8 } +
                                fadeOut(Motion.exitSpec()))
                    else ->
                        (slideInHorizontally(Motion.pageSpec()) { -it / 8 } +
                            fadeIn(Motion.pageSpec())) togetherWith
                            (slideOutHorizontally(Motion.exitSpec()) { it / 4 } +
                                fadeOut(Motion.exitSpec()))
                }
            },
            label = "screen",
        ) { screen ->
            when (screen) {
                CalendarViewModel.Screen.Settings -> SettingsScreen(vm)
                CalendarViewModel.Screen.About -> AboutScreen(vm)
                CalendarViewModel.Screen.Calendar -> CalendarHome(vm)
            }
        }
        // 让开系统导航栏:这个宿主刻意挂在所有 Scaffold **之外**(为了让提示活过翻页),代价是
        // 拿不到 Scaffold 的 innerPadding,不自己让开就会被压在手势条底下。
        // EN: clear the system navigation bar. This host deliberately sits OUTSIDE every Scaffold
        // so a message survives a page change, and the price is that no innerPadding reaches it —
        // without this it renders under the gesture bar.
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }
}

/**
 * 日历主页的外壳:顶栏(应用名 + 「今天」+ 设置)+ 日历页。
 *
 * 顶栏上只有两个动作,而这两个是**唯一**值得常驻的:
 *   - 「今天」:翻走几个月之后回来,这是最频繁的需求;没有它就得反着点一路点回来。
 *     它只在"当前不在今天所在的月"或"今天没被选中"时出现 —— 已经站在今天还摆一个
 *     「回到今天」是纯粹的噪音(见下面的 `visible` 条件)。
 *   - 设置:语言、主题、刷新数据都在里面。
 *
 * EN: the calendar home's shell — a top bar (app name, Today, Settings) and the page. Only two
 * actions live there because only two earn permanent space: Today, the commonest need after
 * paging months away (without it you tap back one month at a time), shown only when not already
 * on today; and Settings, which holds language, theme and the data refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarHome(vm: CalendarViewModel) {
    val haptics = rememberHaptics(vm)
    val onToday = vm.viewMonth == java.time.YearMonth.from(vm.today) && vm.selectedDate == vm.today

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        Lang.t("Calendar Shuttle", "日历穿梭机"),
                        fontWeight = FontWeight.Medium,
                        color = brandPrimary(),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                actions = {
                    // 已经在今天时隐藏:一个点了什么也不变的按钮比没有按钮更糟 —— 用户会以为
                    // 它坏了。用 AnimatedContent 的兄弟 AnimatedVisibility 会让设置图标左右跳动,
                    // 所以这里直接按条件出现/消失,而设置图标永远钉在最右。
                    // EN: hidden when already on today — a button that changes nothing when tapped
                    // is worse than no button, since it reads as broken. Animating it would shift
                    // the Settings icon sideways, so it simply appears or not, with Settings pinned
                    // to the right edge.
                    if (!onToday) {
                        IconButton(onClick = haptics.clicking { vm.goToday() }) {
                            Icon(
                                Icons.Filled.Today,
                                contentDescription = Lang.t("Today", "今天"),
                                tint = brandPrimary(),
                            )
                        }
                    }
                    IconButton(
                        onClick = haptics.clicking { vm.navigate(CalendarViewModel.Screen.Settings) },
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = Lang.t("Settings", "设置"),
                            tint = brandPrimary(),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            CalendarScreen(vm)
        }
    }
}
