---
name: mosaic-terminal-ui
description: Build or modify Mosaic terminal-UI apps, widgets, samples, and tests in this repository. Use when writing @Composable screens with layout/text/scroll/lazy-list/input/focus/animation, wiring terminal state or the hardware cursor, or debugging Mosaic rendering, frame behavior, and snapshot tests.
---

# Mosaic 终端 UI 开发指南

本仓库是 **Compose 驱动的终端 UI 库**（不是 Android/Desktop UI）：状态→重组→测量/放置→按 cell 绘制→ANSI 增量输出。写应用、示例或测试时先读本文件，再按任务读 `references/`：

- [references/api.md](references/api.md) — 公共 API 全表与参数
- [references/pitfalls.md](references/pitfalls.md) — 踩坑清单（先看这个能省几轮）
- [references/testing.md](references/testing.md) — 快照/虚拟终端/能力矩阵测试
- [references/patterns.md](references/patterns.md) — 可直接抄的完整写法

## 0. 先记住 7 条硬约束

1. **组合根按无界约束测量**。`fillMaxWidth()`、`fillMaxHeight()`、`weight()`、比例布局在根上默认无效。全屏应用必须自己声明画布：
   `Column(Modifier.width(terminal.size.columns).height(terminal.size.rows))`，尺寸来自 `LocalTerminalState.current.size`。
2. **`Text` 报告的是内容宽，不是约束宽**。`Modifier.width(n)` / `requiredWidth(n)` 是首选尺寸，长文本会溢出而不是被裁；裁剪要靠 `softWrap` + `maxLines`/`overflow`，或父级 `clipToBounds()`。
3. **`Text` 默认 `softWrap = false`**。不显式打开时，长行不会因为父级有宽度就自动换行。
4. **`runMosaic` 是 run-until-idle**。没有常驻 effect 的静态界面会在首帧稳定后立即返回、进程退出。交互应用要保活：
   `LaunchedEffect(Unit) { awaitCancellation() }`，或由无限循环/事件驱动。
5. **必须真 TTY**。在 Gradle `run*`、IntelliJ 终端、CI 无 TTY 环境里，ANSI 会被吞或没有交互；先 `installDist`，再在真实终端运行二进制。
6. **鼠标/粘贴是 opt-in**：`runMosaic(mouse = true, paste = true)`（或对应 `runMosaicBlocking/Main`）。键盘始终可用；滚轮只在滚轮容器上生效。
7. **仓库红线**：代码/注释/文档/提交信息里不要出现外部参考库或项目名。改写或新增文档时也不要把这些名字带回来。

## 1. 最小完整应用

```kotlin
package example

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.runMosaicMain
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import kotlinx.coroutines.awaitCancellation

fun main() = runMosaicMain(mouse = true, paste = true) {
	App()
}

@Composable
fun App() {
	// 没有它，静态内容在首帧后就会结束；有它，进程等到 Ctrl-C / cancel。
	LaunchedEffect(Unit) { awaitCancellation() }

	val size = LocalTerminalState.current.size
	Column(Modifier.size(size.columns, size.rows)) {
		Text("Header")
		Text("Body", softWrap = true)
	}
}
```

要点：
- 根节点有界后，子节点里的 `fillMaxWidth()` / `weight()` 才按预期工作。
- `runMosaicMain` 是阻塞入口；suspend 场景用 `runMosaic`，`main()` 场景也可用 `runMosaicBlocking`。
- 无 TTY 时的行为由 `NonInteractivePolicy` 决定：`Exit`（默认）、`Throw`、`Return`、`Ignore`、`AssumeAndIgnore`。示例程序通常用 `Ignore` 便于 CI 构建。

## 2. 仓库地图

| 路径 | 作用 |
| --- | --- |
| `mosaic-runtime/` | 核心：`runMosaic*`、Compose 运行时接入、布局/绘制/渲染、Text/输入/焦点/滚动/光标 |
| `mosaic-terminal/` | `Terminal`/`TerminalState`/能力与事件模型 |
| `mosaic-tty/` | 原生 TTY 绑定（JNI / FFM / cinterop）与 `TestTerminal` PTY |
| `mosaic-tty-terminal/` | 终端能力探测、事件解析、`TtyTerminal` 实现 |
| `mosaic-animation/` | `animate*AsState`、`Animatable`、`Crossfade`、`AnimatedVisibility` |
| `mosaic-testing/` | `runMosaicTest`、`TestMosaic`、`TestTerminal`、`CapabilityMatrix` |
| `samples/showcase/` | 9 页能力演示（Unicode/Text/Layout/Input/Field/Animation/Scroll/Lazy/Border），**优先照抄** |
| `samples/counter/` | 最小状态/动画入口 |
| `mosaic-runtime/src/commonTest/` | 绝大多数行为测试；`jvmTest/` 放官方字素套件等只适合 JVM 的大文件 |

## 3. 构建、运行、测试

```bash
# JVM 单测（最常用）
./gradlew :mosaic-runtime:jvmTest
./gradlew :mosaic-tty:jvmTest
./gradlew :mosaic-tty-terminal:jvmTest
./gradlew :mosaic-animation:jvmTest

# Native（Linux x64）。native klib 的 configuration-cache 序列化会失败，带这个参数：
./gradlew :mosaic-runtime:linuxX64Test --no-configuration-cache
./gradlew :mosaic-tty:linuxX64Test --no-configuration-cache
./gradlew :mosaic-tty-terminal:linuxX64Test --no-configuration-cache
./gradlew :mosaic-animation:linuxX64Test --no-configuration-cache

# 公共 API 变更后
./gradlew :mosaic-runtime:apiDump    # 或对应模块
./gradlew :mosaic-runtime:apiCheck

# 示例二进制（不要用 runJvm 验证交互）
./gradlew :samples:showcase:installDist
./samples/showcase/build/install/showcase/bin/showcase
```

注意：`:mosaic-tty:spotlessCheck` 通过；但整个仓库的 `spotlessCheck` 当前在 runtime 的 7 处**既有**代码上失败（compose 规则），不是你新增代码的问题，别顺手改无关文件。

JVM 22+ 走 FFM（多版本 jar 的 `META-INF/versions/22`）。想在本地验证 FFM 路径：

```bash
./gradlew :mosaic-tty:installJvmTestDistribution
JAVA_HOME=<jdk22+> mosaic-tty/build/install/jvmTest/bin/mosaic-tty-test
```

## 4. API 速查

| 主题 | 关键 API | 详见 |
| --- | --- | --- |
| 入口 | `runMosaic`、`runMosaicMain`、`runMosaicBlocking`、`NonInteractivePolicy` | api.md `1 |
| 布局 | `Box`/`Row`/`Column`/`BoxWithConstraints`/`Spacer`/`Filler`/`Layout`；`weight`/`align` | api.md `2 |
| Modifier | `size` 族、`padding`、`offset`、`aspectRatio`、`fillMax*`、`border`、`background`、`clipToBounds`、`drawBehind` | api.md `2 |
| 文本 | `Text`、`TextStyle`、`Color`、`UnderlineStyle`、`TextOverflow`、`AnnotatedString`/`SpanStyle` | api.md `3 |
| 滚动/列表 | `ScrollState`、`rememberScrollState`、`verticalScroll`、`horizontalScroll`、`LazyColumn` | api.md `4 |
| 输入/焦点 | `onKeyEvent`/`onPreviewKeyEvent`、`clickable`/`hoverable`/`onMouseEvent`、`onPaste`、`focusable`/`onFocusChanged`/`FocusRequester`、`BasicTextField` | api.md `5 |
| 终端 | `LocalTerminalState`、`TerminalState`、`Terminal.Size/Theme/Capabilities`、`LocalClipboard` | api.md `6 |
| 动画 | `animate*AsState`、`Animatable`、`Crossfade`、`AnimatedVisibility` + Enter/Exit | api.md `7 |
| 光标/静态 | `Cursor`/`CursorShape`、`StaticEffect`/`LocalStaticLogger` | api.md `8 |
| 测试 | `runMosaicTest`、`TestMosaic`、`TestTerminal`、`CapabilityMatrix`、`SnapshotStrategy` | testing.md |

## 5. 常见任务从哪开始

- **新页面/全屏应用**：`patterns.md` `1/`2；先处理根尺寸与保活。
- **侧栏/三段布局/等分列**：`patterns.md` `3/`4；`weight` 只能用在 Row/Column scope。
- **滚动长内容**：`patterns.md` `5；容器必须有界。
- **大列表**：`patterns.md` `6；`LazyColumn` 的 `itemHeight` 是唯一映射，项内容不能超过槽高。
- **表单/多输入框**：`patterns.md` `7；`BasicTextField` 只在聚焦时消费输入，Tab/点击切焦点。
- **Tabs/悬停/点击**：`patterns.md` `8；用 `clickable`/`hoverable`，不要自己写命中测试。
- **自定义比例布局**：`patterns.md` `9；`BoxWithConstraints` 读 `maxWidth/maxHeight`。
- **硬件光标**：`patterns.md` `10；只在根范围内、无静态日志时可靠。
- **测试输出/回归**：`testing.md`；优先断言最终屏幕状态（`VirtualTerminal`），其次整帧字符串。

## 6. 提交前检查

1. 相关模块 JVM + native 测试全绿（至少 runtime；改动 tty 再加 tty/tty-terminal）。
2. 新增/修改 public API 跑 `apiDump` 并提交 `.api` / `.klib.api`。
3. 只对改动模块跑 `spotlessCheck`；不要被 runtime 的既有 7 处失败带偏。
4. 不引入外部参考名；不把已清理的调研文档/名称加回来。
5. 示例改动要在真实终端手动看一次（IDE/Gradle 里不算）。

## 7. 参考文件

- [references/api.md](references/api.md)
- [references/pitfalls.md](references/pitfalls.md)
- [references/testing.md](references/testing.md)
- [references/patterns.md](references/patterns.md)
