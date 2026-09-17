# CLAUDE.md

本仓库的开发约定写在 [AGENTS.md](AGENTS.md) —— 请先读那一份,它是权威版本。

重点先说三条(细节见 AGENTS.md):

1. **本地通常没有编译环境,CI 是代码第一次被真正编译的地方。** 提交前逐行检查导入与类型,
   不要"先推上去让 CI 说"。
2. **这是 Web 项目 `calendar-shuttle` 的同口径移植。** 日历本身的显示规则(农历、节气、节日、
   放假/补班配色)不要单方面改动 —— 改一边就是让两端不一致。
3. **不要重新生成 `lunar_fixture.txt` 来让测试变绿。** 那份夹具是 Web 正在用的 JS 库的输出,
   是"答案"而不是"快照"。

EN: the development guide lives in [AGENTS.md](AGENTS.md) — read that one, it is authoritative.
Three points up front: CI is where this code is first genuinely compiled, so check imports and
types before pushing; this app is a same-semantics port of the calendar-shuttle web project and
its calendar display rules must not drift on one side only; and never regenerate the lunar fixture
to make a test pass — it holds the answers, not a snapshot.
