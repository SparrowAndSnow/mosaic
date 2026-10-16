# Mosaic 全项目 TODO / 改进清单

> 归档日期：2026-10-16，分支 `feature/unicode-wide-character-fixes`
> 定位：**全项目功能节点级** TODO（原专项「Unicode 宽度支持」并入 §3）。
> 覆盖：架构 / 渲染 / 缓冲 / 布局 / 文本 / 样式 / 颜色 / 输入 / 能力探测 / tty 适配 / 生命周期 / 动画 / 测试 / 发布。
> 优先级：**P0**=正确性/回归优先；**P1**=高价值优化；**P2**=增强/打磨；**P3**=长期/可选。
> 参考基准：`docs/tui-arch-deep-comparison.md`（Ratatui / BubbleTea v2 逐功能深挖对照）、`docs/unicode-width-research.md`。

## 0. 功能域全景矩阵

| 功能域 | 现状 | 最短板 | 借鉴对象 | 优先级 |
| --- | --- | --- | --- | --- |
| 运行模型 | Compose 声明式 + Snapshot 脏标记 | 帧循环 `delay(1)` 空转，无 FPS 上限 | BubbleTea 60fps ticker | P2 |
| 渲染 | 无 diff，整帧重建 + 每行 `clearLine` | 无增量输出、带宽/闪烁 | uv `transformLine` / Ratatui `BufferDiff` | **P1** |
| 缓冲 | `TextPixel` 单 Int + 布尔续列 | 多码点字素簇丢失（ZWJ/keycap/VS16） | Ratatui `Cell` / uv `Cell` | **P0** |
| 宽字符 | vendor unicode-width 表 + emoji override | 测/绘两套口径；DEC 2027 未探测 | uv `WidthMethod` 可插拔 | **P0/P1** |
| 布局 | Row/Column weight + Box + Modifier 链 | 无 percentage/fill/ratio；无缓存 | Ratatui Constraint+LRU | P2 |
| 文本 | AnnotatedString + TextLayout | 无 wrap/ellipsis/scroll/alignment | Ratatui Paragraph | P2 |
| 颜色 | 16/256/24bit 输出 + env 探测 | 探测可提升（profile/OSC4） | colorprofile | P3 |
| 输入 | kitty 键盘协议全解析 | 鼠标/粘贴仅解析未消费；无 clipboard | BubbleTea View 声明式 | **P1/P2** |
| 能力探测 | DA1 + 批量 DECRPM/DCS/OSC + 1s 兜底 | 缺 DEC 2027（UnicodeCore） | BubbleTea DECRQM 2027 | **P1** |
| tty 适配 | JNI/FFM/cinterop 三绑定 + C(posix/windows) + Zig | 平台矩阵维护负担重；回调空挂 | — | **P1（大头）** |
| 生命周期 | withTerminal + finalization hook + NonInteractive | panic 恢复、信号处理薄 | Ratatui run / BubbleTea shutdown | P2/P3 |
| 动画 | mosaic-animation（Compose port） | 跟随 androidx 升级 | Compose upstream | P3 |
| 测试 | mosaic-testing 快照 + TestTerminal | 无宽字符渲染级断言；无 benchmark | uv widecell 回归 / Ratatui TestBackend | **P1** |

---

## 1. 架构 / 运行模型（mosaic-runtime `mosaic.kt`）

- [ ] **P2 帧循环**：`runMosaicComposition` 的 `while(true){ clock.sendFrame(); delay(1) }`（≈1000fps）持续空转；提供 FPS 上限/自适应（参考 BubbleTea `defaultFPS=60`）。
- [ ] **P2 异常/panic 恢复**：绘制或布局抛异常时应确保终端状态恢复、错误可见（现有 `DebugRendering` 会吞并抛错；`AnsiRendering` 无兜底）。
- [ ] **P2 多实例 / 复用**：`Mosaic(…)` 已可独立构造；明确 `setContent` 二次调用、`cancel`/`awaitComplete` 的并发语义（`mosaic.kt` 已有 "Re-evaluate this for correctness" TODO）。
- [ ] **P3 运行时对齐**：`mosaic-animation/` 是手写 Compose animation port，需跟随 androidx 版本（`AnimateAsState`/`Animatable` 等）并评估是否上 `AnimationVector` 表维护成本。
- [ ] **P3 生命周期集成**：`LifecycleOwner`/`LocalTerminalState` 已接入；补齐前后台/焦点与动画节流的联动（如失焦时降帧）。

## 2. 渲染管线（`rendering.kt` / `surface.kt`）

- [ ] **P1 行级增量输出**：`AnsiRendering` 目前整帧重建 + 每行 `clearLine(CSI K)`；改为「上一帧 `TextSurface` 对比 → 只重绘脏行 / 行内变化段 + `EL` 清尾」，大幅降带宽与闪烁（参考 uv `transformLine` 的 `putRange`/`EL` 成本择优）。
- [ ] **P2 全屏双缓冲 diff**：在行级增量之上做 `Buffer` 双缓冲（Ratatui `buffers: [Buffer;2]` + `BufferDiff`），吸收其 `skip`/`ForcedWidth`/不确定宽三件套语义（滑动到 §3 方案 A 之后实施）。
- [ ] **P2 静态输出优化**：`static()` 分支目前用 `clearDisplay` 兜底整屏（`rendering.kt`）；可解析 `static` 行数 / 逐行 `EL` 最小化清理（注释里已说明是"为省事"）。
- [ ] **P2 闪烁防护**：synchronized output（DEC 2026）已按能力包裹整帧；无该能力时可选加「光标 hide/show 包裹」（BubbleTea 的处理），当前只在启动时 `cursorDisable` 一次。
- [ ] **P2 输出缓冲**：`render` 结果一次 `print()`；评估批量/分片写、避免大字符串抖动。
- [ ] **P3 光标 API**：运行中无自定义光标位置/形状（Ratatui `Frame::set_cursor_position`、BubbleTea `View.Cursor`）；如需 IME/输入框类 widget，需补。

## 3. Unicode / 宽字符（原专项，含全部已知问题）

> 基准：引入改造 `7354a45f`（layout/render/input）、`326d5971`（unicode-width-kotlin）、`3ce27084`（vendor）+ `2e0d3b2f`（生成器）。
> 结论：`drawText`"每码点一格 + isWideContinuation + 跳格"对单码点宽字符正确，对多码点字素簇错误。

### Bug（渲染正确性）
- [ ] **P0 P1 ZWJ 序列丢连接符**：逐码点 `charWidth(ZWJ)=0` → 不推进列，成员互相覆盖（`drawText`，`DrawScope.kt:278/295`）。`👨👩👧` 画成 3 个独立 emoji，布局测 2 格 → 重叠/越界。
- [ ] **P0 P1 keycap 丢 VS16**：`# U+FE0F U+20E3` 中 `FE0F/20E3` w=0 挤进同格被覆盖；`#️⃣` 与 `#⃣` 渲染相同（同 `DrawScope.kt`）。
- [ ] **P1 P2 override 测/绘两层不一致**：`CharWidth.kt` 带 `emojiOverrideRanges`（≈91 个码点）而 `TextLayout` 用串级 `unicodeWidth()` 无 override；同块「布局 1 格、绘制 2 格」。（`CharWidth.kt:3`、`TextLayout.kt:58`）

### 边界 / 健壮性
- [ ] 宽字符落在最后一列：`DrawScope.kt:297` 直接跳过，不预留/不裁剪（参考 uv 右缘补同风格空格、Ratatui 放不下不写）。
- [ ] `isWideContinuation` 生命周期：仅"同格重写指定码点"复位（`DrawScope.kt:331`）；`drawRect` 用 `UnspecifiedCodePoint` 填充背景不复位；被 `render` skip + `AnsiRendering` 每行 clearLine 掩盖（隐藏耦合）。
- [ ] `drawSolidRect` clamp 是 node-local，叠加 `translationX/Y` 后右缘越界仍 `check` 崩（多数静默裁剪、右缘必崩）。

### 修复方向
- [ ] **P0 方案 A（治本，推荐）**：cell 升级为「簇字符串 + 显式列宽」；`drawText` 按扩展字素簇迭代、整簇写首格、后续 w-1 格预留占位；surface 只发首格。同时修 P1/P2 并使测/绘统一簇级 `unicodeWidth`。
  - 占位语义用 `Width==0` 而非布尔标志（uv），做"部分覆盖时清理/修复整段宽格"生命周期（防两类检测口径分歧，参照 `widecell_placeholder_test`）。
  - 右缘裁剪补同风格空格（uv）；或者放不下不写（Ratatui）。
- [ ] **P1 DEC 2027（UnicodeCore）探测**：`TtyTerminal` 未查询 `CSI ?2027$p`；无 2027 的终端上 ZWJ 族逐码点进格，Mosaic 无条件按簇折叠会错位。参照 BubbleTea（`tea.go` startup + `ModeReportMsg`→`setWidthMethod`）：探测并暴露宽度口径（簇折叠 `GraphemeWidth` / 逐 rune `WcWidth` 后备），必要时做光标漂移补偿（uv `reanchorWideLine`）。
- [ ] **P3 方案 B（最小改动，治标）**：`w==0` 并入当前格而非覆盖，保住 ZWJ/VS16；但家庭 emoji 仍画 6 格。
- [ ] **P3 输入侧组合字符**：IME/预组合输入（grapheme 重组）在 `EventParser` 层如何表示/透传（当前只有码点流）。

## 4. 缓冲 / cell 模型（`surface.kt`）
- [ ] **P0 升级 `TextPixel`**：`Int codePoint + Boolean isWideContinuation` → `String content + Int width`（连带 `appendRowTo` 跳格逻辑、`isEmpty` 判定）——即 §3 方案 A 的载体。
- [ ] **P1 部分覆盖清理**：宽格被 `drawRect`/后续文本覆盖头列或续列时，整段清空/修复（uv `Line.Set`），避免半格残留。
- [ ] **P2 画布复用/内存**：`TextSurface` 每帧重建；评估保留并差分（与 §2 行级增量联动）。
- [ ] **P2 ISO 控制字符处理**：`drawText` 对 `\t`/`\r`/`\b` 的控制语义（`charWidth` 兜底 1）——是否需要处理制表符展开/退格（Ratatui `set_stringn` 是过滤控制符）。

## 5. 布局（`layout/` + `ui/`）
- [ ] **P1 Text 内建排版**：`Text` 目前无 wrap / ellipsis / alignment / scroll；多行仅靠 `\n`。以 Ratatui `Paragraph.wrap(trim)/alignment/scroll` 为参照做簇级安全换行。
- [ ] **P2 约束扩展**：Row/Column 只有 `weight`；补 `percentage`/`fill`/`ratio` 式弹性（Ratatui Constraint 优先级 Min>Max>Length>Percentage>Ratio>Fill），并考虑对 gap/overlap（Ratatui `Spacing`）支持。
- [ ] **P2 布局缓存**：`needLayout` 时全树重跑 `measureAndPlace`；对相同 constraints+size 结果做 LRU（Ratatui `layout-cache` feature），缓解高频 resize 与同构重排。
- [ ] **P2 Modifier 缺口**：现有 `padding/offset/aspectRatio/size/weight/drawBehind/onKeyEvent`；评估 `margin`、`fillMaxSize/widthIn…` 类便捷约束、`onGloballyPositioned` 类回调。
- [ ] **P2 intrinsic 复用**：`min/maxIntrinsicWidth/Height` 已透传，但测量重复计算；考虑缓存（与布局缓存合并）。
- [ ] **P3 焦点/键盘导航**：无焦点管理/方向键导航原语（Ratatui 生态靠 widget 状态机、BubbleTea 靠应用层）。

## 6. 文本 / 样式（`text/` + `ui/Text.kt`）
- [ ] **P1 测/绘口径统一**：走 §3 方案 A 后 `TextLayout` 与 `drawText` 共用簇级 `unicodeWidth`（消除 P2；不要在 `TextLayout` 单独加 override，会和 `©=1/☀︎=1` 锁定语义冲突）。
- [ ] **P2 `AnnotatedString` 维护**：470 行手写富文本 + `SpanStyleRange`；核对与 androidx compose ui text 语义差异（合并/覆盖规则），或评估是否足够维持现状。
- [ ] **P2 换行/截断 API**：见 §5 P1；另加 `maxLines`、`overflow=Ellipsis/Clip` 此类参数化入口。
- [ ] **P3 widget 补齐**：Ratatui 有 Block/Border/Chart/Gauge/List/Table/Tabs/Scrollbar 等内建；Mosaic 目前靠 Box/Row/Column/Text 拼装——确定是否做第一方 widget 集（影响发布节奏）。

## 7. 颜色（`ansi.kt` / `Color.kt`）
- [ ] **P3 `toAnsi16Code/toAnsi256Code`**：从 colormath 移植的约简算法，核对与 colormath/终端行为一致性（ANSI 256 灰阶分段、16 色取整）。
- [ ] **P3 探测增强**：`detectAnsiLevel` 现为 env 启发式（`COLORTERM/TERM/WT_SESSION/ConEmuANSI`）；可加 colorprofile 式 `TERM_PROGRAM` 特判与 **OSC 4 调色板查询**（染主题色），并统一 16/256 判定。
- [ ] **P3 主题/调色板**：`SystemThemeEvent` 已解析；可提供 `LocalTerminalState.theme` 驱动的默认样式容器（另见 §10）。

## 8. 输入 / 事件（`mosaic-tty-terminal` + `mosaic-terminal`）
- [ ] **P1 鼠标消费**：`EventParser` 已解析 SGR 鼠标（含 Drag/Motion/Release/Wheel、按钮 1..11），但 runtime 无任何 `MouseEvent` 消费者（`rg` 确认 `mosaic-runtime` 无引用）。补 `Modifier.onMouseEvent` + 对应 `PointerInputScope` 类 API；参考 BubbleTea `View.MouseMode` 声明式切换（CellMotion/AllMotion + SGR 回退 X10）。
- [ ] **P1 粘贴通路**：`BracketedPasteEvent` 已解析但无上层 API；补 `onPaste` / 文本插入原语（含大文本、性能）。
- [ ] **P2 kitty 键盘协议开关**：现在解析齐全但「启用时机」隐式；参考 BubbleTea 在 flush 时按需 `PushKittyKeyboard`/`RequestKittyKeyboard`，并处理 `KeyboardEnhancementsMsg` 回报。
- [ ] **P2 clipboard（OSC 52）**：目前完全无读写；补 `ClipboardEvent` 解析 + 请求 command（BubbleTea `RequestSystemClipboard` 模式）。
- [ ] **P2 事件背压/策略**：`events` channel 固定 64、`DROP_OLDEST`；高频粘贴/鼠标风暴可能丢关键事件，评估批量合并或扩容。
- [ ] **P3 特殊键/修饰**：kitty PUA 特殊键码已定义；核对 `EventTypeRepeat/Release` 与关联文本（`text`）的透传与文档。

## 9. tty 适配（大头：三套绑定 + C 双平台 + Zig）
> 现状：`mosaic-tty` = Kotlin `expect/actual` + 底层 C（`src/commonMain/c/mosaic-{tty,streams,utils,test}-{posix,windows}.c`，约 2.4k 行）+ 三套宿主绑定
> （`jvmMain` **JNI**(`com_jakewharton_mosaic_tty_Jni.c`)、`jvmJdk22` **FFM/jextract**(`Libmosaic`)、`nativeMain` **cinterop**）+ Zig 0.15.1 交叉编译。

### 9.1 宿主绑定
- [ ] **P1 三套绑定一致性**：JNI / FFM / cinterop 行为必须一致（read 超时语义、回调线程、EINTR、内存生命周期）；为每个 native 函数建立统一契约测试（可用 `TerminalTester` + C 测试桩）。
- [ ] **P2 JNI 维护**：`Jni.c` 1140 行手写 JNI + 回调对象生命周期（`ttyCallbackInit/Free`）；核对全局引用泄漏/线程（`setCallback` 可在任意线程触发，文档已声明）。
- [ ] **P2 FFM (jextract) 同步**：JDK22+ 走 `Libmosaic`；`jextract.libraries.register('mosaic')` 头文件变化需重生成，构建里确认了生成物与源码同步（发布产物不漂移）。
- [ ] **P3 Zig 工具链**：Zig 0.15.1 仅用于交叉编译 C；评估升级锁定、缓存目录（`.zig-cache`）gitignore 状态与 CI 可重复性。

### 9.2 POSIX（linux / macos）
- [ ] **P1 raw mode 核对**：`mosaic-tty-posix.c` `c_iflag/c_lflag` 清位（BRKINT/ICRNL/IXON/ECHO/ICANON/IEXTEN/ISIG…）+ `VMIN=1, VTIME=0`；与 termios 权威 raw 模式逐 flag 核对（含 `IUTF8`、`OPOST`、`CS8`，VTIME 是否需要）。
- [ ] **P1 resize**：`SIGWINCH` + `TIOCGWINSZ` 已有；核对与 in-band resize（2048）并存时的竞态/重复发事件。
- [ ] **P2 macOS 特判**：`TIOCGWINSZ`/termios 在 macOS 的差异（`HUPCL`/`c_cc`）、终端类型差异与 Linux 不同的行为。
- [ ] **P2 信号处理加强**：当前 SIGINT/SIGTERM 无专用处理（仅靠 Ctrl+C 按键取消）；补优雅退出与 raw 恢复的兜底（BubbleTea `handleSignals`）。
- [ ] **P3 非 UTF-8 locale**：`read`/`write` 假定 UTF-8；核对 locale 非 UTF-8 时字节流的解析与写回（或明确文档限制）。

### 9.3 Windows
- [ ] **P1 ConPTY / VT**：raw mode 用 `GetConsoleMode/SetConsoleMode` + `ENABLE_VIRTUAL_TERMINAL_INPUT/PROCESSING`（已有）；核对失败回退（非 VT 终端）、ConPTY 与真实 console 差异。
- [ ] **P1 输入多路复用**：Windows 需在 `ReadConsoleInput` 循环里分发 `KEY_EVENT`/`MOUSE_EVENT`/`WINDOW_BUFFER_SIZE_EVENT`/`FOCUS_EVENT`（已有分支）；核对中文字符（宽字符 `KEY_EVENT` 的 `WCHAR`）与 UTF-8 转换、Ime 持有状态。
- [ ] **P2 光标/颜色**：`ENABLE_VIRTUAL_TERMINAL_PROCESSING` 下的 ANSI 输出与 24bit；核对 Windows Terminal 与 conhost 差异。
- [ ] **P3 codepage**：`chcp 65001`/`CP_UTF8` 处理与恢复（若不做需文档化）。
- [ ] **P3 focus 回调**：仅 Windows 提供 `onFocus`；跨平台对等（POSIX 走 DECRPM focus 事件）已在 `TtyTerminal` 层补齐——保持两个通路语义一致。

### 9.4 抽象 / API
- [ ] **P1 `Callback.onKey/onMouse` 空挂**：接口注释 `Currently unused`；决定实现（事件直接平台回调）或移除，避免误导。
- [ ] **P2 单例绑定约束**：`Tty.tryBind` 同时只能一个实例（文档已声明）；评估是否在错误路径上更友好。
- [ ] **P2 读写缓冲**：`Tty.write` 循环写 + `reset`/`close` 幂等性；评估批量 flush 与 interrupt 语义。
- [ ] **P3 测试桩对齐**：`mosaic-test-{posix,windows}.c`（800+ 行）真实度；`TerminalTester` 覆盖能力矩阵（见 §13）。

## 10. 能力探测（`TtyTerminal.kt`）
- [ ] **P1 DEC 2027**：补 `CSI ?2027$p` 探测并接到宽度口径（见 §3）。
- [ ] **P2 探测列表完备性**：现有 DA1 → batch DECRPM（cursor/focus/2026/theme/2048）+ kitty graphics/keyboard/notification/pointer/textSizing/underline + xterm version；评估补 `DECRQM` 全量/逐项（部分终端不响应组合查询）。
- [ ] **P2 超时兜底**：`withTimeoutOrNull(1s)` 已有；无响应终端降级路径要可观测（debug 输出），避免静默错配。
- [ ] **P3 env 兜底**：`detectAnsiLevel` 扩展 `TERM_PROGRAM`（iTerm2/WezTerm…）与 kitty/OSC 12 兜底（BubbleTea colorprofile 思路）。

## 11. 生命周期 / 集成
- [ ] **P1 异常安全**：`runMosaic{...}` 内抛错应先恢复终端；核实 `withTerminal` 的 `use` + `finalizationHook` 在协程取消/异常路径全覆盖（`TtyTerminal.kt` 的 "design of finalization hook fights us here" TODO）。
- [ ] **P2 非交互策略**：`NonInteractivePolicy` 五态已有；补充在 `AssumeAndIgnore` 时 `NonInteractiveTerminal` 的能力提示（避免静默画错）。
- [ ] **P3 静态日志**：`StaticEffect`/`static()` 与整帧渲染并发写 stdout 的原子性（`rendering.kt` 已考虑 `clearDisplay` 兜底）。

## 12. 动画（`mosaic-animation/`）
- [ ] **P3 帧时钟集成**：动画走 `BroadcastFrameClock`；核对与 `delay(1)` 帧循环的节流/低电量行为。
- [ ] **P3 API 完整性**：`Animatable/AnimateAsState/…` port 自 androidx；跟踪上游 API 与内部 `AnimationVector` 表维护；评估新增 `animateColorAsState`（线性 RGB）等。
- [ ] **P3 与渲染增量联动**：动画高频帧目前每帧整帧重建（§2 未做时），是行级增量改造的最大受益者。

## 13. 测试 / 质量
- [ ] **P1 宽字符渲染级回归**：`AnsiRenderingTest` 增加 ZWJ / keycap / emoji override / 宽格覆盖清理 / 右缘裁剪 断言（现状只锁 `A中B`；修 §3 前先补测试复现）。
- [ ] **P1 `TextLayoutWidthTest`**：增加「测量与绘制一致」断言（防 P2 回归）。
- [ ] **P2 快照体系扩展**：`mosaic-testing` 增加能力矩阵（ansiLevel × kittyUnderline × synchronizedOutput × 2027）；`TestTerminal` 支持注入鼠标/粘贴/焦点事件流。
- [ ] **P2 性能基准**：对 `render`（整帧 vs 行级增量）、`measureAndPlace`、`EventParser` 建 benchmark，作为 §2/§5 改造前后对照。
- [ ] **P3 端到端 golden**：在真终端（script/pty）跑样例快照（Ratatui 有 golden/视觉测试体系，BubbleTea 有 examples 快照）。

## 14. 文档 / 发布
- [ ] **P2 README/CHANGELOG**：随上述功能落地更新能力矩阵与已知限制（非 UTF-8 locale、DEC 2027 依赖等）。
- [ ] **P3 API 稳定性**：0.x 阶段明确 breaking 窗口；§4/§3 方案 A 涉及 `TextPixel`（internal）不破坏 public API，但 `Text`/`Modifier` 新增入口需同步 `CHANGELOG`。
- [ ] **P3 示例补齐**：鼠标/粘贴/滚动/富文本/动画 样例与文档一一对应。
