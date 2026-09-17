# AGENTS.md

日历穿梭机的 Android 客户端(Calendar Shuttle)。单模块 `:app`,Kotlin 2.0 + Compose M3,
minSdk 26 / targetSdk 36。这份文件只写**你猜不到、猜错了会付出代价**的那些事。

EN: the Android client for Calendar Shuttle. One Gradle module `:app`, Kotlin 2.0 + Compose M3,
minSdk 26 / targetSdk 36. This file covers only what you would otherwise guess wrong.

## 一句话定位

这个应用是 Web 项目 `calendar-shuttle` 的**同口径移植**,不是一个"另做一个日历"。
Web 仓库在本机 `/Users/atlas/Data/shuttlelab/calendar-shuttle`;界面与配色约定参照
`/Users/atlas/Data/shuttlelab/secretary/android/Secretary`(同组织的另一个 Android 应用)。

## 构建与测试

本机(atlas 的 mac)已具备完整工具链,**不要再假设"只能靠 CI 编译"**:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17      # brew 装的 JDK 17
export ANDROID_HOME="$HOME/Library/Android/sdk"    # sdkmanager 装在 platform-tools/platforms/build-tools
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"

./gradlew testDebugUnitTest    # 纯 JVM,不需要设备
./gradlew assembleDebug        # 15MB 左右
./gradlew assembleRelease      # 本地无密钥时回退 debug 签名,仅用于验证 lintVitalRelease
./gradlew lintDebug            # 报告在 app/build/reports/lint-results-debug.html
```

`local.properties`(内含 `sdk.dir`)是 gitignore 的,换机器要自己写一份。

**装到设备/模拟器上跑,是本仓库唯一能发现一整类问题的办法** —— 单元测试跑在 JVM 上,用的是
桌面 JDK 的实现;设备上是 ART 加 ICU,正则、`java.time`、字体度量都可能表现不同。UI 更是
只有跑起来才知道:格子放不放得下三行、深色下对比够不够、翻月会不会跳。所以"改完 UI"
的验收标准是**在设备上看过**,不是"CI 绿了"。

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c && adb shell am start -n org.shuttlelab.calendar/.MainActivity
adb logcat -d -s AndroidRuntime:E   # 崩溃栈
```

- CI 会先跑单元测试再打包,测试失败就不出包;另有一步断言"用例数 ≥ 10",防止测试没被发现
  却依然绿灯。
- 不需要任何密钥或配置文件即可构建 debug 包(与 secretary 不同,这里没有 Firebase)。
- release 包由 tag 触发,必须有 `KEYSTORE_BASE64` 等 Secrets;缺了就失败,**不回退 debug 签名**
  (回退会产出用户装不上的包,见 `release.yml` 里的说明)。

## 与 Web 端的一致性——本仓库最重要的约束

界面上任何"日历本身"的显示规则,改一边就等于让两端不一致,而用户会把网页和 App 摆在一起看。
以下几处都是**故意**照抄的,看着可以顺手改好,但不要单方面改:

- `data/Lunar.kt` 是 js-calendar-converter v0.0.7 `solar2lunar` 的逐行移植,连它的循环写法、
  2101 上界、除夕修正都保留。`data/LunarTables.kt` 由脚本生成,**不要手工编辑**。
- `data/Holiday.kt` 的四条正则与 Web 的 `functions/api/holiday.ts` 逐字相同。
- `lunarDisplay()` 会渲染出 `八月(八月)初七` 这种重复 —— 那是 Web 当前的行为,`LunarParityTest`
  用字面量钉住了它。
- 周一起始、固定 42 格、本月之外留空、绿/红/黄的优先级,都与 Web 一致。
- **唯一刻意不同**的是格子里那一行农历:Web 放完整串靠 CSS 截断,手机上会把日期截没,所以
  用 `lunarCellLabel()`(节气 > 初一显示月份 > 农历日)。详情页仍是完整串。

真要改显示,请两端一起改,并同步重新生成对账夹具。

## 测试的意义,别把它改绿

- `LunarParityTest` 比对的是 `src/test/resources/lunar_fixture.txt` —— 由 **Web 正在用的那个 JS 包**
  跑出来的三千余行样本(1900–2100,含闰月、节气、春节/除夕)。**改 `Lunar.kt` 时不要重新生成
  夹具**:那等于把答案改成和新代码一致。只有升级 js-calendar-converter(且 Web 端同步升级)
  才重新生成:`node scripts/gen_lunar_fixture.mjs > app/src/test/resources/lunar_fixture.txt`。
- `HolidayParseTest` 喂的是**随包的真数据** `app/src/main/assets/holiday.js`,不是手写样本 ——
  正则最典型的失效方式是"只对我造的那个例子有效"。

## 架构(只写高信息量的)

- `data/` 是纯逻辑,不碰 Android(所以能被 JVM 测试覆盖);`HolidayRepo` 是唯一的例外,
  它要 Context 读文件与 assets。
- 状态全在 `ui/CalendarViewModel.kt`,用 `var … by mutableStateOf(…)`(不是 StateFlow),
  页面直接拿 `vm`。导航是 `Screen` 枚举 + 手写回退栈:**不要直接给 `screen` 赋值**,
  用 `navigate()` / `back()`,否则系统返回键和预测式返回都会失灵。
- 节假日数据三级兜底:网络 → filesDir 缓存(TTL 7 天)→ 随包快照。刷新失败时**保留**已有数据,
  不清空。
- 配色分两层:应用外壳用 secretary 那套 M3 色板,日历语义色(放假绿/补班红/周末黄/选中彩边)
  来自 Web 的 `globals.css`。**动态取色只替换外壳,不动语义色** —— 放假永远是绿的。
- 没有权限申请、没有通知、没有后台任务。`INTERNET` 是唯一实质权限。加任何一条之前先问:
  这个日历真的需要吗?

## 风格约定

- 注释与 KDoc **中文在前,`EN:` 段落在后**;用户可见文案用 `Lang.t("English", "中文")` 写在
  调用点,`strings.xml` 只有 `app_name`。
- 解释**为什么**,不要复述代码在做什么。一条"这里看起来多余但不能删"的注释比十条
  "// 设置标题"有用。
- 触感反馈走 `rememberHaptics(vm)`,**分级不要collapse成一种震动**;动效走 `ui/Motion.kt`,
  必须尊重 `rememberReduceMotion()`。
- 不要为了一件小事引依赖:一个 GET 用 `HttpURLConnection`,四十行手写代码胜过一个库。
  真的需要时,先说明理由,而不是默默加上。
- **提交信息一律英文**(仓库规定,不分人机):`feat:` / `fix:` / `docs:` / `chore:` / `ci:` +
  简短祈使句。代码注释与本文件仍是中文在前 —— 两者面向的读者不同:提交历史是给
  GitHub 上任何路过的人看的,注释是给维护这份代码的人看的。
- 作者身份用仓库本地配置的 `ShuttleLab <support@shuttlelab.org>`,别用个人账号覆盖。

## 脚本

`scripts/` 下两个生成脚本都依赖 Web 项目在本机且装好 node_modules:

```sh
node scripts/gen_lunar_tables.js  > app/src/main/java/org/shuttlelab/calendar/data/LunarTables.kt
node scripts/gen_lunar_fixture.mjs > app/src/test/resources/lunar_fixture.txt
```
