# Mosaic 全项目 TODO / 改进清单

> 归档日期：2026-10-16，分支 `feature/unicode-wide-character-fixes`
> 定位：**全项目功能节点级** TODO（原专项「Unicode 宽度支持」并入 §3）。
> 覆盖：架构 / 渲染 / 缓冲 / 布局 / 文本 / 样式 / 颜色 / 输入 / 能力探测 / tty 适配 / 生命周期 / 动画 / 测试 / 发布。
> 优先级：**P0**=正确性/回归优先；**P1**=高价值优化；**P2**=增强/打磨；**P3**=长期/可选。
> 参考基准：`docs/tui-arch-deep-comparison.md`（逐功能深挖对照）、`docs/unicode-width-research.md`。

> **对账更新：2026-10-17（refactor 分支）**
> - 本清单是**总 backlog**；执行进度以 `docs/refactor-progress.md`（推进台账）为准，本文件只标记「已落地 ✅（提交）／仍待办」。
> - ✅ 项对应的重构提交见各条目，实现细节见台账 §5。
> - 第三方库/项目名清理（本文件部分条目含名称）仍按计划在合并回 main 前统一处理。

## 0. 功能域全景矩阵

| 功能域 | 现状 | 最短板 | 借鉴对象 | 优先级 |
| --- | --- | --- | --- | --- |
| 运行模型 | Compose 声明式 + Snapshot 脏标记 | 帧循环 `delay(1)` 空转，无 FPS 上限 | BubbleTea 60fps ticker | P2 |
| 渲染 | cell 级差分（L3.3）+ 宽格右缘防护与覆盖回归（L3.4） | static 输出行级清理（L3.5）未做 | — | **P1**（静态续） |
| 缓冲 | `TextPixel` = 簇字符串 + 显式列宽（L2.1 已落地） | 无部分覆盖清理 / 右缘裁剪 | Ratatui `Cell` / uv `Cell` | **P1**（余项） |
| 宽字符 | 簇级列宽唯一口径（L1.2），测/绘统一（L3.1） | DEC 2027 未探测；右缘裁剪 | uv `WidthMethod` 可插拔 | **P1** |
| 布局 | Row/Column weight + Box + Modifier 链 | 无 percentage/fill/ratio；无缓存 | Ratatui Constraint+LRU | P2 |
| 文本 | AnnotatedString + TextLayout | ellipsis/alignment/scroll 已落地（L4.2~L4.4） | Ratatui Paragraph | P2 |
| 颜色 | 16/256/24bit 输出 + env 探测 | 探测可提升（profile/OSC4） | colorprofile | P3 |
| 输入 | kitty 键盘协议全解析 | 鼠标/粘贴仅解析未消费；无 clipboard | BubbleTea View 声明式 | **P1/P2** |
| 能力探测 | DA1 + 批量 DECRPM/DCS/OSC + 1s 兜底 | 缺 DEC 2027（UnicodeCore） | BubbleTea DECRQM 2027 | **P1** |
| tty 适配 | JNI/FFM/cinterop 三绑定 + C(posix/windows) + Zig | 平台矩阵维护负担重；回调空挂 | — | **P1（大头）** |
| 生命周期 | withTerminal + finalization hook + NonInteractive | panic 恢复、信号处理薄 | Ratatui run / BubbleTea shutdown | P2/P3 |
| 动画 | mosaic-animation（Compose port） | 跟随 androidx 升级 | Compose upstream | P3 |
| 测试 | mosaic-testing 快照 + TestTerminal + 簇/渲染级断言 | 已补 ZWJ/keycap/宽格覆盖/右缘渲染级断言（L3.4）；无 benchmark | — | **P1** |

---

## 1. 架构 / 运行模型（mosaic-runtime `mosaic.kt`）

- [x] **P2 帧循环** ✅（L7.3）：`runMosaicComposition` 帧循环按负载自适应——活跃（重组/布局/绘制/动画等待中）~60fps、空闲 ~20fps，替代原 ~1000fps 恒发帧；`MosaicComposition.hasPendingFrameWork()` 判定 + `yield()` 消除动画重注册竞态；空闲帧不触发重绘（`FrameThrottleTest` 3 条）。
- [x] **P2 异常/panic 恢复** ✅（L7.4）：`runMosaicComposition` 捕获非取消异常→写纯文本错误帧（堆栈+节点树）→`cancel()` 收队→rethrow；终端状态恢复由 `withTerminal` 双 `use` 保证；正常取消不报错、不写错误帧（`FrameErrorVisibilityTest` 2 条）。
- [x] **P2 多实例 / 复用** ✅（L7.6，`00ebdb43`）：awaitComplete 语义明确化——静态无 effect 内容按 run-until-idle 自然返回；无限 effect 保活期间阻塞，仅 `cancel()` 释放；`setContent` 二次调用替换内容并取消旧 content 的 effect；原 "Re-evaluate" TODO 替换为结论化说明（`LifecycleSemanticsTest` 3 条）。
- [ ] **P3 运行时对齐**：`mosaic-animation/` 是手写 Compose animation port，需跟随 androidx 版本（`AnimateAsState`/`Animatable` 等）并评估是否上 `AnimationVector` 表维护成本。
- [ ] **P3 生命周期集成**：`LifecycleOwner`/`LocalTerminalState` 已接入；补齐前后台/焦点与动画节流的联动（如失焦时降帧）。

## 2. 渲染管线（`rendering.kt` / `surface.kt`）

- [x] **P1 行级增量输出** ✅（L3.2，`08e92477`）：`AnsiIncrementalRendering` 为默认渲染器，按行对比跳过未变行、绝对定位 + `clearLine` 重写变化行；高度变化 / static 存在退化为全量重画。
- [x] **P1 行内段差分** ✅（L3.3，`dc031ff3`）：cell 级 diff，变化区间按字素簇边界切分后 `goto` + 片段重写；稀疏段就地 patch、稠密/删尾整行 `goto;clearLine` 回退。
- [x] **P1 旧渲染器去重** ✅（L3.8，`72e359af`）：删除全量 `AnsiRendering` 与其专属测试（410 行），`AnsiIncrementalRendering` 为唯一增量渲染器；关键行为覆盖已由 `AnsiIncrementalRenderingTest` 承接。
- [x] **P2 宽格 diff 右缘防护 + 覆盖回归** ✅（L3.4，`76b8d301`）：`appendRowRange` 加 `rowEnd`，宽簇在末列放不下时以同风格空格占位（杜绝换行）；新增 `VirtualTerminal`（解析 ANSI→屏幕网格）与 `CellDiffRenderingTest`（15 条，覆盖宽→窄/窄→宽/ZWJ/keycap/相邻宽簇/右缘/resize）。调研中"覆盖残留"经 crafted-cell 全场景验证为**结构上已排除**（占位身份区分 + 簇边界扩展 + 区间合并），无需额外逻辑改动。
- [ ] **P2 静态输出优化**：`static()` 分支目前用 `clearDisplay` 兜底整屏（`rendering.kt`）；可解析 `static` 行数 / 逐行 `EL` 最小化清理（注释里已说明是"为省事"）。
- [x] **P2 闪烁防护** ✅（L3.6，`e15497c4`）：synchronized output（DEC 2026）按能力包裹整帧；无该能力且可切换光标时改为「光标 hide/show 包裹」（帧首 `hide` / 帧尾 `show`），空帧不包裹避免闪；`cursorVisibility=false` 时退化为裸帧。启动期 hide 一次/退出恢复逻辑不变。
- [x] **P2 输出缓冲** ✅（L3.7，`8a0d3e7c`）：`render` 结果不再 `.toString()` + 单次 `print`，改由 `FrameWriter` 增量 UTF-8 编码到固定 8KB 缓冲并分块写出（码点原子落块、代理对读前视、孤立代理替换 `?`）；JVM `System.out.write` + 每帧 flush，Native `posix.write(STDOUT)` 循环写。
- [ ] **P3 光标 API**：运行中无自定义光标位置/形状（Ratatui `Frame::set_cursor_position`、BubbleTea `View.Cursor`）；如需 IME/输入框类 widget，需补。

## 3. Unicode / 宽字符（原专项，含全部已知问题）

> 基准：引入改造 `7354a45f`（layout/render/input）、`326d5971`（unicode-width-kotlin）、`3ce27084`（vendor）+ `2e0d3b2f`（生成器）。
> 结论：`drawText`"每码点一格 + isWideContinuation + 跳格"对单码点宽字符正确，对多码点字素簇错误。
> 状态：核心问题已由簇级重写修复（L1.2 度量 / L2.1 cell / L3.1 绘制）；本节保留原描述作背景，✅ 为已落地、未 ✅ 为仍待办。

### Bug（渲染正确性）
- [x] **P0 P1 ZWJ 序列丢连接符** ✅（L3.1，`354883b2` 按簇绘制后不再逐码点覆盖）。背景：逐码点 `charWidth(ZWJ)=0` → 成员互相覆盖、`👨👩👧` 画成 3 个独立 emoji。
- [x] **P0 P1 keycap 丢 VS16** ✅（L3.1）：整簇写入首格，VS16/组合符不再丢。背景：原 `FE0F/20E3` w=0 被覆盖、`#️⃣` 与 `#⃣` 渲染相同。
- [x] **P1 P2 override 测/绘两层不一致** ✅（L1.2/L2.1 统一到簇级 `graphemeWidth` 单一口径）。背景：原 `CharWidth.kt` 带 `emojiOverrideRanges` 而 `TextLayout` 无 override，同块「布局 1 格、绘制 2 格」。

### 边界 / 健壮性
- [ ] 宽字符落在最后一列（右缘裁剪）：`drawText` 簇循环右缘直接跳过，不预留/不裁剪（参考 uv 右缘补同风格空格、Ratatui 放不下不写）。
- [x] **P1 `isWideContinuation` 生命周期** ✅（L2.1 起改为显式「簇字符串 + 列宽占位」cell，不再依赖该标志生命周期）。
- [ ] `drawSolidRect` clamp 是 node-local，叠加 `translationX/Y` 后右缘越界仍 `check` 崩（多数静默裁剪、右缘必崩）。

### 修复方向
- [x] **P0 方案 A（治本）** ✅（核心已落地）：L2.1 cell 升级为「簇字符串 + 显式列宽」、L3.1 `drawText` 按扩展字素簇迭代（整簇写首格、续格预留占位、surface 只发首格）、L1.2 测/绘统一簇级宽度。剩余子项见下方未勾条目。
  - [ ] 部分覆盖清理：占位语义用 `Width==0`（非布尔标志），做"部分覆盖时清理/修复整段宽格"生命周期（现状已用占位 cell，清理逻辑未做，见 §4 P1）。
  - [ ] 右缘裁剪：补同风格空格或放不下不写（见上「边界 / 健壮性」）。
- [ ] **P1 DEC 2027（UnicodeCore）探测**：`TtyTerminal` 未查询 `CSI ?2027$p`；无 2027 的终端上 ZWJ 族逐码点进格，Mosaic 无条件按簇折叠会错位。参照 BubbleTea（`tea.go` startup + `ModeReportMsg`→`setWidthMethod`）：探测并暴露宽度口径（簇折叠 `GraphemeWidth` / 逐 rune `WcWidth` 后备），必要时做光标漂移补偿（uv `reanchorWideLine`）。
- [x] **P3 方案 B（最小改动，治标）** ✅（已被方案 A 取代，不再实施）：原案 `w==0` 并入当前格，家庭 emoji 仍画 6 格，留档。
- [ ] **P3 输入侧组合字符**：IME/预组合输入（grapheme 重组）在 `EventParser` 层如何表示/透传（当前只有码点流）。

## 4. 缓冲 / cell 模型（`surface.kt`）
- [x] **P0 升级 `TextPixel`** ✅（L2.1，`1a0e39d4`）：`Int codePoint + Boolean isWideContinuation` → `String content + Int width`（含 `markAsContinuation()`；`appendRowTo` 跳格逻辑已同步）。
- [ ] **P1 部分覆盖清理**：宽格被 `drawRect`/后续文本覆盖头列或续列时，整段清空/修复，避免半格残留（cell 已是占位模型，清理逻辑未做）。
- [ ] **P2 画布复用/内存**：`TextSurface` 每帧重建；评估保留并差分（与 L3.4 双缓冲联动）。
- [ ] **P2 ISO 控制字符处理**：`drawText` 对 `\t`/`\r`/`\b` 的控制语义（`charWidth` 兜底 1）——是否需要处理制表符展开/退格（Ratatui `set_stringn` 是过滤控制符）。

## 5. 布局（`layout/` + `ui/`）
- [x] **P1 Text 内建排版（簇级安全换行）** ✅（L4.1，`9c622d53`）：`Text` 参数化 `softWrap`（默认 false 不回归），开启时按入向 `maxWidth` 约束在**字素簇边界**软换行；ZWJ 家庭/组合字符/变体/旗帜永不拆开，超宽簇独占行，空段落保留；约束变化即时重排。测试：`TextLayoutWrapTest` + `TextWrapRenderingTest`。
- [x] **P2 换行/截断/对齐/滚动参数** ✅（L4.2 `de3c9a1a` + L4.3 `271f53fa` + L4.4 `ac703463`）：`Text` 增加 `maxLines`/`overflow`（Clip/Ellipsis 簇安全截断）、`textAlign`（Start/Center/End，有界宽行偏移、Start/无界零回归）、`scroll`（垂直查看窗口，配合 `maxLines` 滚动切片，越界 clamp 不至于空窗）。
- [x] **P2 约束扩展（比例布局）** ✅（方向见台账 L4.5 调研、实现 L4.6 `d2780c1a`）：比例/百分比**不新增自造 modifier**，遵循 Compose 语法——等分/占剩余用 `weight`（已有）、占父级可用比例用 `fillMaxWidth/H/S(fraction)`（已有）、精确混合比例走 `BoxWithConstraints`（单槽位子组合实现）。剩余可选：gap/overlap（间距/负重叠）。
- [x] **P2 布局缓存** ✅（L4.7，`9146adaa`）：`needLayout` 时不再全树重跑测量——每个节点以（Constraints, layoutVersion）为键缓存最近一次测量，命中即整子树跳过；失效覆盖结构变化与测量期读取的 snapshot 状态。单槽位 memo（LRU-of-1）已够用，缓解高频重排与同构重布局。剩余优化：apply 失效查扫改反向索引、未放置子树裁剪（台账 L4.7）。
- [ ] **P2 Modifier 缺口**：现有 `padding/offset/aspectRatio/size/weight/drawBehind/onKeyEvent`；评估 `margin`、`fillMaxSize/widthIn…` 类便捷约束、`onGloballyPositioned` 类回调。
- [ ] **P2 intrinsic 复用**：`min/maxIntrinsicWidth/Height` 已透传，但测量重复计算；考虑缓存（与布局缓存合并）。
- [ ] **P3 焦点/键盘导航**：无焦点管理/方向键导航原语（Ratatui 生态靠 widget 状态机、BubbleTea 靠应用层）。

## 6. 文本 / 样式（`text/` + `ui/Text.kt`）
- [x] **P1 测/绘口径统一** ✅（L1.2/L2.1）：`TextLayout` 与 `drawText` 已共用簇级宽度；勿在 `TextLayout` 单独加 override（会和 `©=1/☀︎=1` 锁定语义冲突）。
- [ ] **P2 `AnnotatedString` 维护**：470 行手写富文本 + `SpanStyleRange`；核对与 androidx compose ui text 语义差异（合并/覆盖规则），或评估是否足够维持现状。
- [x] **P2 换行/截断/对齐/滚动 API** ✅（L4.1~L4.4）：`softWrap`/`maxLines`/`overflow`/`textAlign`/`scroll` 全部落地。
- [ ] **P3 widget 补齐**：Ratatui 有 Block/Border/Chart/Gauge/List/Table/Tabs/Scrollbar 等内建；Mosaic 目前靠 Box/Row/Column/Text 拼装——确定是否做第一方 widget 集（影响发布节奏）。

## 7. 颜色（`ansi.kt` / `Color.kt`）
- [ ] **P3 `toAnsi16Code/toAnsi256Code`**：从 colormath 移植的约简算法，核对与 colormath/终端行为一致性（ANSI 256 灰阶分段、16 色取整）。
- [ ] **P3 探测增强**：`detectAnsiLevel` 现为 env 启发式（`COLORTERM/TERM/WT_SESSION/ConEmuANSI`）；可加 colorprofile 式 `TERM_PROGRAM` 特判与 **OSC 4 调色板查询**（染主题色），并统一 16/256 判定。
- [ ] **P3 主题/调色板**：`SystemThemeEvent` 已解析；可提供 `LocalTerminalState.theme` 驱动的默认样式容器（另见 §10）。

## 8. 输入 / 事件（`mosaic-tty-terminal` + `mosaic-terminal`）
- [x] **P1 鼠标消费** ✅（L5.1，`e7b9caaf`）：`Modifier.onMouseEvent`（返回 Boolean 消费）+ 绝对坐标命中测试（z 序在上先收，回退祖先）；终端 1006 + 1000/1003 opt-in（`runMosaic*` `mouse=true`）。注：BubbleTea 式「CellMotion/AllMotion 声明式分档」未做（现固定 all-motion 1003），需要时再接。
- [x] **P1 粘贴通路** ✅（L5.2，`e7b9caaf`；+L7.5 未终止 paste 的 Ctrl-C 逃生口）：`Modifier.onPaste`（祖先先于子树）+ DECSET 2004 opt-in；帧循环将 paste 内容累积为单串，期间不触发按键。注：文本插入原语（光标/IME）归 §15 输入控件。
- [x] **P2 kitty 键盘协议开关** ✅（L5.3，`e7b9caaf`）：探测 `kittyKeyboard` 后自动 push `CSI>23u`、退出 pop 恢复；`toKeyEventOrNull` 只放行 press，repeat/release 过滤。注：无 `KeyboardEnhancementsMsg` 二次确认（探测期 `CSI?u` 回复已确认支持）。
- [x] **P2 clipboard（OSC 52）** ✅（L5.4，`423f25b8`）：`ClipboardEvent` 解析（base64/UTF-8；`!`→null）+ `Terminal.requestClipboardRead`/`writeClipboard` + runtime `LocalClipboard`（suspend `getText/setText`，读=请求→await 回复 2s 超时）。
- [x] **P2 事件背压/策略** ✅（L5.5，`68ee4a1e`）：`events` channel 64 改 `SUSPEND` + 生产者 `send`（零丢弃）；帧循环批内 `Motion/Drag` 合并只留最新坐标；`TtyTerminalBackpressureTest`（500 字符 0 丢失）+ `MouseEventTest` 增 3 条。
- [x] **P3 特殊键/修饰** ✅（L5.6，`5f438a79`）：PUA 特殊键映射验证（F1~F35 等）；`repeat` 并入 `press` 投递（恢复长按自动重复一致性）、`release` 丢弃；`text` 关联文本优先于码点（CSI u 无文本段时 null，无冲突）。`CompatTest` 8 条。

## 9. tty 适配（大头：三套绑定 + C 双平台 + Zig）
> 现状：`mosaic-tty` = Kotlin `expect/actual` + 底层 C（`src/commonMain/c/mosaic-{tty,streams,utils,test}-{posix,windows}.c`，约 2.4k 行）+ 三套宿主绑定
> （`jvmMain` **JNI**(`com_jakewharton_mosaic_tty_Jni.c`)、`jvmJdk22` **FFM/jextract**(`Libmosaic`)、`nativeMain` **cinterop**）+ Zig 0.15.1 交叉编译。

### 9.1 宿主绑定
- [ ] **P1 三套绑定一致性**：JNI / FFM / cinterop 行为必须一致（read 超时语义、回调线程、EINTR、内存生命周期）；为每个 native 函数建立统一契约测试（可用 `TerminalTester` + C 测试桩）。
- [ ] **P2 JNI 维护**：`Jni.c` 1140 行手写 JNI + 回调对象生命周期（`ttyCallbackInit/Free`）；核对全局引用泄漏/线程（`setCallback` 可在任意线程触发，文档已声明）。
- [ ] **P2 FFM (jextract) 同步**：JDK22+ 走 `Libmosaic`；`jextract.libraries.register('mosaic')` 头文件变化需重生成，构建里确认了生成物与源码同步（发布产物不漂移）。
- [ ] **P3 Zig 工具链**：Zig 0.15.1 仅用于交叉编译 C；评估升级锁定、缓存目录（`.zig-cache`）gitignore 状态与 CI 可重复性。

### 9.2 POSIX（linux / macos）
- [x] **P1 raw mode 核对** ✅（L6.3，审计无代码改动）：`mosaic-tty-posix.c` 清位与 glibc `cfmakeraw(3)` 权威定义**逐位一致**（8 iffags、OPOST、lflag ECHO/ECHONL/ICANON/ISIG/IEXTEN、CS8、VMIN=1/VTIME=0）；`IUTF8`/`HUPCL`/`CLOCAL` 保留正确（raw 已去 ICANON，该位无影响）；TCSAFLUSH + 失败恢复完备。对照表见 progress §5 L6.3。
- [x] **P1 resize** ✅（L6.5，`d469ad0a`）：`SIGWINCH` 与 in-band（2048）互斥由构造保证（in-band 支持则不装 SIGWINCH handler），收敛 `size` StateFlow 单点；补「in-band 无初始报告时用 `currentSize()` 播种」＋两平台互斥/播种回归测试。详见 progress §5 L6.5。
- [ ] **P2 macOS 特判**：`TIOCGWINSZ`/termios 在 macOS 的差异（`HUPCL`/`c_cc`）、终端类型差异与 Linux 不同的行为。
- [x] **P2 信号处理加强** ✅（L6.4，`39db32b8`）：SIGINT/SIGTERM 已由生命周期收尾机制覆盖（native POSIX 信号处理器→teardown→重发信号 / JVM shutdown hook）；补 **`SIGSEGV/SIGILL/SIGPIPE` 的 C 级兜底**（async-safe 恢复 termios + 唤醒 reader + 保留终止语义重发），随 native raw mode 生命周期装/卸。详见 progress §5 L6.4。
- [ ] **P3 非 UTF-8 locale**：`read`/`write` 假定 UTF-8；核对 locale 非 UTF-8 时字节流的解析与写回（或明确文档限制）。

### 9.3 Windows
- [ ] **P1 ConPTY / VT**：raw mode 用 `GetConsoleMode/SetConsoleMode` + `ENABLE_VIRTUAL_TERMINAL_INPUT/PROCESSING`（已有）；核对失败回退（非 VT 终端）、ConPTY 与真实 console 差异。
- [ ] **P1 输入多路复用**：Windows 需在 `ReadConsoleInput` 循环里分发 `KEY_EVENT`/`MOUSE_EVENT`/`WINDOW_BUFFER_SIZE_EVENT`/`FOCUS_EVENT`（已有分支）；核对中文字符（宽字符 `KEY_EVENT` 的 `WCHAR`）与 UTF-8 转换、Ime 持有状态。
- [ ] **P2 光标/颜色**：`ENABLE_VIRTUAL_TERMINAL_PROCESSING` 下的 ANSI 输出与 24bit；核对 Windows Terminal 与 conhost 差异。
- [ ] **P3 codepage**：`chcp 65001`/`CP_UTF8` 处理与恢复（若不做需文档化）。
- [ ] **P3 focus 回调**：仅 Windows 提供 `onFocus`；跨平台对等（POSIX 走 DECRPM focus 事件）已在 `TtyTerminal` 层补齐——保持两个通路语义一致。

### 9.4 抽象 / API
- [x] **P1 `Callback.onKey/onMouse` 空挂** ✅（L6.1，`cd6ce408`）：判定为死回调并**移除**——C 头 `ONKey/ONMouse` 为 `// TODO params` 占位从未调用；Windows C 层将 `KEY/MOUSE_EVENT` 转 VT 字节流走共享 `EventParser`（回调仅用于 Focus/Resize）；实现类只有空实现。三套绑定（JNI/FFM/cinterop）+ `mosaic-tty.h` 结构同步删除，测试同步清理。
- [ ] **P2 单例绑定约束**：`Tty.tryBind` 同时只能一个实例（文档已声明）；评估是否在错误路径上更友好。
- [x] **P2 读写缓冲** ✅（L6.6，`4428a77b`）：审计结论——控制序列写层（`TtyTerminal.write(s)`）已有完整循环、主渲染走 `FrameWriter`/`writeScreenBytes`（native 循环、jvm PrintStream 原子全量）；唯一边界 bug 为 `reset()` 在 `close()` 后无指针守卫（三平台悬垂），已补守卫使其 closed 后幂等空操作，并增 close/reset-after-close 幂等测试 2 条。
- [ ] **P3 测试桩对齐**：`mosaic-test-{posix,windows}.c`（800+ 行）真实度；`TerminalTester` 覆盖能力矩阵（见 §13）。

## 10. 能力探测（`TtyTerminal.kt`）
- [x] **P1 DEC 2027** ✅（L6.2，`9f88fbc7`）：补 `CSI ?2027$p` 探测 → `Capabilities.graphemeClustering`；支持时自动 `CSI ?2027h` 启用（退出恢复）、set/permanentlySet 不重复；`TtyTerminalDec2027Test`（5 态）+ `EventParser` 2027 例。宽度口径落法见 progress §6 决策：能力暴露 + 终端侧启用，逐 rune 后备未做（理由已记录）。
- [x] **P2 探测列表完备性** ✅（L6.8，`53ba2ea2`）：审计对账 13 项探测全对应暴露能力；theme 996/997 确认非笔误；「逐项重查」评估驳回（不响应终端逐项同样不响应 + N+1 轮询 + 破坏安全契约）；新增 `TtyTerminalProbeInventoryTest` 锁定完整探测清单防失。
- [ ] **P2 超时兜底**：`withTimeoutOrNull(1s)` 已有；无响应终端降级路径要可观测（debug 输出），避免静默错配。
- [ ] **P3 env 兜底**：`detectAnsiLevel` 扩展 `TERM_PROGRAM`（iTerm2/WezTerm…）与 kitty/OSC 12 兜底（BubbleTea colorprofile 思路）。

## 11. 生命周期 / 集成
- [x] **P1 异常安全** ✅（L6.7，`963279b3`）：teardown 提为幂等函数（`AtomicBoolean` 单次），`close()` 同步先跑再 `closeJob.cancel()`，保证先于 `tty.close()` 完成恢复；finalization hook 复用同一 teardown 兜底信号/shutdown 路径。异常/取消/信号三条路径核实全覆盖；消除对 `Unconfined` 内联续传的隐性依赖。
- [x] **P2 非交互策略** ✅（L7.8，`91c6d23b`）：`withTerminal` 落到 `NonInteractiveTerminal`（`Ignore` 绑定失败 / `AssumeAndIgnore`）时先向 stderr 打 `NonInteractiveNotice`（JVM `System.err` / native posix `STDERR_FILENO`），能力降级不再静默。
- [ ] **P3 静态日志**：`StaticEffect`/`static()` 与整帧渲染并发写 stdout 的原子性（`rendering.kt` 已考虑 `clearDisplay` 兜底）。

## 12. 动画（`mosaic-animation/`）
- [ ] **P3 帧时钟集成**：动画走 `BroadcastFrameClock`；核对与 `delay(1)` 帧循环的节流/低电量行为。
- [ ] **P3 API 完整性**：`Animatable/AnimateAsState/…` port 自 androidx；跟踪上游 API 与内部 `AnimationVector` 表维护；评估新增 `animateColorAsState`（线性 RGB）等。
- [ ] **P3 与渲染增量联动**：动画高频帧目前每帧整帧重建（§2 未做时），是行级增量改造的最大受益者。

## 13. 测试 / 质量
- [x] **P1 宽字符渲染级回归** ✅（L3.4，`76b8d301`）：`CellDiffRenderingTest` 补齐 ZWJ / keycap / 宽→窄覆盖清理 / 窄→宽强制宽 / 相邻宽簇 / 右缘 / resize 的**端到端屏幕态断言**（经 `VirtualTerminal` 重放）。剩余：不确定宽（VS16 结尾）在真实终端与预测口径分叉的探测与挂接（归 L6 DEC 2027）。
- [x] **P1 `TextLayoutWidthTest`** ✅（L7.7，`f51e04ee`）：增加「测量与绘制一致」逐列对拍断言——按测量 `LineMeasure` 逐列核对绘制（簇头列文本 / 宽簇 placeholder 续列 / 右沿 == totalWidth / `layout.width` == 最宽行），覆盖 ascii/cjk/ZWJ/flags/keycap/VS16/内嵌空格/softWrap/Clip/Ellipsis/scroll/Annotated。
- [ ] **P2 快照体系扩展**：`mosaic-testing` 增加能力矩阵（ansiLevel × kittyUnderline × synchronizedOutput × 2027）；`TestTerminal` 支持注入鼠标/粘贴/焦点事件流。
- [x] **P2 性能基准** ✅（L7.1）：`samples/bench`（commonMain 单一驱动）对 render 单行 delta / 全量重建 / layout-pass / EventParser 建模，JVM+native 对照（见 `docs/benchmarks.md`）。
- [ ] **P3 端到端 golden**：在真终端（script/pty）跑样例快照（Ratatui 有 golden/视觉测试体系，BubbleTea 有 examples 快照）。

## 14. 文档 / 发布
- [ ] **P2 README/CHANGELOG**：随上述功能落地更新能力矩阵与已知限制（非 UTF-8 locale、DEC 2027 依赖等）。
- [ ] **P3 API 稳定性**：0.x 阶段明确 breaking 窗口；§4/§3 方案 A 涉及 `TextPixel`（internal）不破坏 public API，但 `Text`/`Modifier` 新增入口需同步 `CHANGELOG`。
- [ ] **P3 示例补齐**：鼠标/粘贴/滚动/富文本/动画 样例与文档一一对应。
- [ ] **P2 台账同步**：`docs/todo.md` 勾选/状态与 `docs/refactor-progress.md` 台账保持一致（每次归档步骤后同步 ✅ 与提交号）。

---

## 15. 外部组件待办（只在内部核心重构完成后实现）

> 决策（2026-10-17，`refactor` 分支）：这些是**最外层的组合组件**，依赖内部核心（L4 布局、L5 输入、L6 tty 适配）先重构到位；当前阶段**只登记不实现**。优先级统一排在内部重构之后，具体顺序待内部核心完成后重排。实现原则：先实现 + 单元测试 + 用户确认，再归档。

- [ ] **滚动容器（最大布局缺口）**：`verticalScroll` / `horizontalScroll` + `rememberScrollState`，与 `Text.scroll`（L4.4）复用查看窗口/裁剪逻辑，但抽象为通用滚动容器供任意内容使用。
- [ ] **惰性列表**：`LazyColumn` / `LazyRow`（仅组合可视区子项）；依赖滚动容器与稳定的 item key，作为滚动容器之后的自然延伸。
- [ ] **输入控件**：可编辑单行输入（`BasicTextField` 等价物，含光标/IME），依赖 L5 输入（鼠标/粘贴/键盘编辑语义）落地。
- [ ] **鼠标交互**：`clickable` / `hoverable`（鼠标事件当前**已解析但未消费**，见 todo §8），依赖 L5 把事件接到平台回调。
- [ ] **焦点 / 键盘导航原语**：交叉引用 todo §5 已有 P3 条目。
- [ ] **边框 / 分隔绘制**：`border` / 分隔线（在 DrawScope 之上，无核心依赖，可随时插入）。
- [ ] **定位回调**：`onGloballyPositioned` 等价物（todo §5 已列出）。
- [ ] **动画容器**：`Crossfade` / `AnimatedVisibility` 类容器（`mosaic-animation` 已有核心值动画，仅需容器装配）。
- [ ] **第一方 widget 集**：charts / tables / tabs / scrollbars（todo §6 已有 P3 条目）。
- [ ] 备注：`margin` **不是**缺口——Compose 没有 `margin`，间距用 `padding` 或外层间隔表达。
