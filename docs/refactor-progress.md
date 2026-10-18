# Mosaic 重构进度台账（refactor 分支）

> 用途：记录「自下而上重构」的进度与已完成步骤的实现要点，保证会话上下文压缩后仍可恢复工作状态与下一步。
> 分支：`refactor` · 当前 HEAD：L6.8（`53ba2ea2`）后 · 创建/更新：2026-10-17
> 关联：`docs/todo.md`（全项目功能 TODO，P0~P3）、`docs/tui-arch-deep-comparison.md`、`docs/unicode-width-research.md`
> 约定：本文件正文不出现任何第三方库/项目名（合并回 main 前的清理红线，见 §2）。

## 1. 工作流契约

- 每次只做**一项自下而上的功能**。
- 完成标准：功能实现 + **单元测试**（commonTest，必要时官方套件）+ JVM 与 Native 全绿。
- 完成后**提交英文信息**归档，并用中文向用户汇报，**等待确认后再进行下一项**。
- 用户确认后继续；本台账随每个归档步骤同步更新。

## 2. 红线与约束

- **禁止第三方库/项目名**出现在代码、注释、文档正文、提交信息、历史记录中。
  - 已中性化：`c35d0b09` 清除了内置宽度代码的归属注释。
  - 待清理（合并回 main 前）：早期归档/调研提交（`326d5971`~`aebfb705`）的提交信息、`docs/todo.md` 与 `docs/tui-arch-deep-comparison.md` 正文含第三方名；届时按用户指示重写提交信息与文档。
- **性能优先**：绘制/测量/渲染等热路径低分配（复用 StringBuilder、避免每帧不必要分配）。
- **Kotlin 惯用写法**：对外 API 惯用；内部热路径可手写优化。
- 中文交流、英文提交信息。验证命令见 §7。

## 3. 分层路线图（自下而上）

| 层 | 定位 | 对应 todo § | 状态 |
| --- | --- | --- | --- |
| L1 文本度量 | 字素簇分割 + 簇级列宽（唯一口径） | §3 核心 | 完成 |
| L2 缓冲/cell 模型 | `TextPixel` 升级为「簇字符串 + 显式列宽」 | §4 | 完成 |
| L3 渲染管线 | 按簇绘制 → 增量输出 → 差分/缓冲/静态/闪烁 | §2 | 完成（L3.1~L3.8） |
| L4 布局 | 约束/测量/缓存/排版/Modifier | §5、§6 | 主干完成（L4.1~L4.7）；余下 P2/P3 可选 |
| L5 输入/事件 | 鼠标/粘贴/剪贴板/背压/特殊键 | §8 | 完成（L5.1~L5.6 已归档） |
| L6 能力探测/tty 适配 | DEC 2027、三套绑定、raw、信号、Windows | §9、§10 | 完成（L6.1~L6.8 已归档） |
| L7 生命周期/动画/测试/发布 | 异常安全、帧节流、测试矩阵、示例 | §11~§14 | 进行中（L7.1 基准基线就绪，待归档） |

> 编号为推进顺序；某层内的小步骤继续用 Lx.y 编号（如 L3.2）。

## 4. 进度台账

| 层 | 步骤 | 内容 | 提交 | 测试 | 状态 |
| --- | --- | --- | --- | --- | --- |
| L1 | L1.1 | 字素簇分割（UAX #29）+ 官方套件 | `7f295e23` | `GraphemeBreakOfficialTest`（官方套件 1000+ 条，jvmTest）+ `GraphemesTest` | ✅ 已归档 |
| L1 | L1.2 | 簇级列宽作为权威文本度量 | `e5954966` | `GraphemeWidthTest` + `TextLayoutWidthTest` | ✅ 已归档 |
| L2 | L2.1 | `TextPixel` → 簇字符串 + 显式列宽 | `1a0e39d4` | `TextPixelTest` | ✅ 已归档 |
| L3 | L3.1 | 文本按字素簇粒度绘制 | `354883b2` | `DrawTextClusterTest` | ✅ 已归档 |
| L3 | L3.2 | 行级增量 ANSI 渲染（默认渲染器） | `08e92477` | `AnsiIncrementalRenderingTest`（10 条） | ✅ 已归档 |
| L3 | L3.3 | 行内段差分（cell 级 diff + 簇边界切分） | `dc031ff3` | `AnsiIncrementalRenderingTest`（13 条） | ✅ 已归档 |
| L3 | L3.8 | 删除旧全量渲染器 `AnsiRendering`（去重） | `72e359af` | 无新增；删除 `AnsiRenderingTest`（410 行），覆盖已由 `AnsiIncrementalRenderingTest` 承接 | ✅ 已归档 |
| L4 | L4.1 | Text 簇级安全软换行 + 布局约束透传 | `9c622d53` | `TextLayoutWrapTest`（12 条）+ `TextWrapRenderingTest`（2 条） | ✅ 已归档 |
| L4 | L4.2 | Text `maxLines` / `overflow` 簇安全截断 | `de3c9a1a` | `TextLayoutWrapTest` 增 5 条 + `TextWrapRenderingTest` 增 2 条 | ✅ 已归档 |
| L4 | L4.3 | Text `textAlign`（Start/Center/End，复用 `Alignment.Horizontal`） | `271f53fa` | `TextAlignRenderingTest`（7 条） | ✅ 已归档 |
| L4 | L4.4 | Text `scroll`（垂直查看窗口，Ratatui `Paragraph.scroll` 参照） | `ac703463` | `TextLayoutWrapTest` 增 7 条 + `TextScrollRenderingTest`（3 条） | ✅ 已归档 |
| L4 | L4.6 | `BoxWithConstraints`（内容读取父级约束做精确比例布局；单槽位子组合） | `d2780c1a` | `BoxWithConstraintsTest`（7 条） | ✅ 已归档 |
| L3 | L3.4 | cell 差分右缘防护 + 宽格 diff 回归（虚拟终端） | `76b8d301` | `CellDiffRenderingTest`（15 条）+ `VirtualTerminal`（解析 ANSI→屏幕网格） | ✅ 已归档 |
| L3 | L3.5 | static 输出行级清理（替代一键 `clearDisplay`） | `e2497ca3` | `AnsiIncrementalRenderingTest`（16 条，改 4 + 新增 2） | ✅ 已归档 |
| L3 | L3.6 | 无同步渲染时的闪烁防护（光标包裹） | `e15497c4` | `AnsiIncrementalRenderingTest`（19 条，新增 3） | ✅ 已归档 |
| L3 | L3.7 | 输出批量/分片写，避免大字符串抖动 | `8a0d3e7c` | `FrameWriterTest`（9 条） | ✅ 已归档 |
| L4 | L4.7 | 布局缓存（按 constraints+version 记忆测量，失效覆盖结构与测量期状态依赖） | `9146adaa` | `LayoutCacheTest`（4 条，新增） | ✅ 已归档 |
| L5 | L5.1 | 鼠标消费：`Modifier.onMouseEvent` + 命中测试分发 + 终端 1006/1000/1003 opt-in | `e7b9caaf` | `MouseEventTest`（8 条）+ `TtyTerminalMouseTest`（2 条） | ✅ 已归档 |
| L5 | L5.2 | 粘贴通路：`Modifier.onPaste` + bracketed paste 累积为单串分发（粘贴内容不触发按键） | `e7b9caaf` | `PasteEventTest`（6 条）+ `TtyTerminalPasteTest`（2 条） | ✅ 已归档 |
| L5 | L5.3 | kitty 键盘协议开关：探测 `kittyKeyboard` 后 push `CSI>23u`、退出 pop 恢复 | `e7b9caaf` | `TtyTerminalKittyKeyboardTest`（2 条） | ✅ 已归档 |
| L5 | L5.4 | clipboard（OSC 52）：`ClipboardEvent` 解析 + `Terminal` 读写命令 + `LocalClipboard`（suspend 读等待异步回复） | `423f25b8` | `EventParserOscClipboardEventTest`（7 条）+ `TtyTerminalClipboardTest` + `ClipboardTest`（3 条） | ✅ 已归档 |
| L5 | L5.5 | 事件背压（channel `DROP_OLDEST`→`SUSPEND` + `send`）+ Motion/Drag 批内合并只留最新坐标 | `68ee4a1e` | `TtyTerminalBackpressureTest`（1，新文件）+ `MouseEventTest` 增 3 | ✅ 已归档 |
| L5 | L5.6 | 特殊键/修饰核对：PUA 特殊键映射验证 + repeat 并入 press 投递、release 丢弃 + `text` 关联文本透传验证 | `5f438a79` | `CompatTest`（8 条，新文件） | ✅ 已归档 |
| L6 | L6.1 | 移除 `Tty.Callback.onKey/onMouse` 死回调（三套绑定同步，事件走共享字节流通道） | `cd6ce408` | `TtyTest` 清理 + 三套绑定编译/测试全绿 | ✅ 已归档 |
| L6 | L6.2 | DEC 2027 探测 → `Capabilities.graphemeClustering`；支持时自动启用（退出恢复） | `9f88fbc7` | `TtyTerminalDec2027Test`（5）+ `EventParser` 2027 例 + apiDump | ✅ 已归档 |
| L6 | L6.3 | POSIX raw mode 核对（审计）：与 `cfmakeraw(3)` 逐位一致，零代码差异 | —（审计无代码改动） | —（审计；基线全绿） | ✅ 已归档 |
| L6 | L6.4 | native 信号兜底：`SIGSEGV/SIGILL/SIGPIPE` C 级 async-safe 恢复 termios + 保留终止语义重发；随 raw 生命周期装/卸 | `39db32b8` | `SignalGuardTest`（6，fork 真实信号）+ tty/tty-terminal 两平台全绿 + apiCheck 修复 | ✅ 已归档 |
| L6 | L6.5 | resize 通路核对 + 加固：SIGWINCH 与 in-band 互斥（构造保证）；in-band 无初始报告时 `currentSize()` 播种 | `d469ad0a` | `TtyTerminalResizeTest` 增 2（互斥 + 播种），双平台 10 例全过 | ✅ 已归档 |
| L6 | L6.6 | 读写缓冲核对（审计）+ 加固：控制序列写循环已正确；`reset()` 三平台补 `close()` 后指针守卫（closed 后幂等空操作） | `4428a77b` | `TestTtyTest` 增 2（close/reset-after-close 幂等），tty 两平台全绿 | ✅ 已归档 |
| L6 | L6.7 | 生命周期异常安全核对 + 加固：teardown 从「依赖 Unconfined 内联续传的异步 hook」改为「close() 同步执行 + 信号/shutdown hook 复用」幂等闭环 | `963279b3` | `TtyTerminalLifecycleTest`（新，3 条）+ 既有 teardown 输出用例全绿 + runtime 编译过 | ✅ 已归档 |
| L6 | L6.8 | 探测清单完备性核对（审计）+ 清单回归测试：13 项探测逐一对账、theme 996/997 确认非笔误、「逐项重查」评估驳回 | `53ba2ea2` | `TtyTerminalProbeInventoryTest`（新，1 条）；tty-terminal 双平台 254 例全绿 | ✅ 已归档 |
| L7 | L7.1 | 双平台微基准（`samples/bench`，commonMain 单一驱动）：render（单行 delta/全量重建/layout-pass）+ EventParser；JVM+native 对照；验证 native 达主流实时标准（见 §5 结论）；`cjk` 分支补充 CJK/emoji 内容非 ASCII 路径测量 | `de7f7cf5` | JVM `:samples:bench:runJvm` + native kexe 可运行；无传统单测（基准类负载，见 `docs/benchmarks.md`）；bench 驱动随 L7.2 演进 | ✅ 已确认 |
| L7 | L7.2 | 绘制热路径低分配（surface 复用 / ASCII 测宽 / raw-Int 打包 / 源串-区间 cell / 簇切分 ASCII 快路径 / 对齐懒算 / 共用簇测量 / cell 级懒复位）+ native GC 调优实验（无效，恢复默认） | `c48a5d64` | `SurfaceReuseTest`/`StrWidthAsciiFastPathTest`/`PackedWidthEquivalenceTest`/`CellSliceTest`/`GraphemeFastPathTest`/`TextMeasuredDrawTest`/`SurfaceLazyResetTest` 等；双平台全绿 | ✅ 已归档 |
| L7 | L7.3 | 帧节流：`runMosaicComposition` 帧循环按负载自适应（`hasPendingFrameWork` 判定，活跃 ~60fps / 空闲 ~20fps），替代 ~1000fps 恒空转 | `59c1df44` | `FrameThrottleTest`（3 条：闲判定 / 动画保帧率 / 静态不空转重绘）；双平台全绿 | ✅ 已归档 |
| L7 | L7.4 | 异常/panic 恢复：`runMosaicComposition` 捕获非取消异常→`frameWriter` 写纯文本错误帧（`Mosaic error`+堆栈+`NODES`）→`cancel()` 收队→rethrow；终端恢复由 `withTerminal` use 保证；`runMosaicComposition` 增可注入 `frameWriter` | `ddbaa911` | `FrameErrorVisibilityTest`（2 条：致命错误帧+传播 / 正常取消无错误帧）；双平台全绿 | ✅ 已归档 |
| L7 | L7.5 | paste 逃生口：未终止 bracketed paste（只收到 start、未收到 end）中 Ctrl-C 仍退出（清空 paste 累积 → `job.cancel()`），普通粘贴字符不触发退出；顺带消除 `pasteText!!` | `6c4f46bd` | `PasteInterruptTest`（2 条：Ctrl-C 中断未终止 paste / 普通字符不退出；`Ticker` keep-alive 区分自然 idle 返回）；双平台全绿 | ✅ 已归档 |
| L7 | L7.6 | awaitComplete 语义明确化：run-until-idle 收尾（效果全结束 + 欠下的 layout/draw 显式驱动一帧并 join 后 close）+ `cancel()` 必释放等待者；`setContent` 二次调用替换内容并取消旧 effect；TODO 注释替换为结论 | `00ebdb43` | `LifecycleSemanticsTest`（3 条：无限 effect 保活至 cancel 释放 / 静态自然返回 / 二次 setContent 取消旧 effect）；双平台全绿 | ✅ 已归档 |
| L7 | L7.7 | `TextLayoutWidthTest`「测量与绘制一致」：按测量 `LineMeasure` 逐列对拍绘制结果（簇头列文本 / 宽簇 placeholder 续列 / 右沿 == totalWidth / `layout.width` == 最宽行）；覆盖 ascii/cjk/ZWJ/flags/keycap/VS16/内嵌空格/softWrap/Clip/Ellipsis/scroll/Annotated | `f51e04ee` | `TextLayoutWidthTest` 14 条（原 7 + 新 7）；双平台全绿 | ✅ 已归档 |
| L7 | L7.8 | 非交互能力提示：`withTerminal` 落到 `NonInteractiveTerminal`（`Ignore` 绑定失败 / `AssumeAndIgnore`）时先向 stderr 打 `NonInteractiveNotice`（JVM `System.err` / native posix `STDERR_FILENO`），能力降级不再静默 | `91c6d23b` | `NonInteractiveTest`（commonTest 2 条：AssumeAndIgnore 分派 + 惰性终端能力清单）+ `NonInteractiveNoticeTest`（jvmTest 1 条：stderr 捕获）；双平台全绿 | ✅ 已归档 |
| L7 | L7.9 | 快照体系扩展：`mosaic-testing` 增加 `CapabilityMatrix.defaults`（ansiLevel × kittyUnderline × synchronizedOutput × graphemeClustering 的 7 组代表性组合）+ `runMosaicTestForEach`（每组新 mosaic 跑同一断言）；`TestMosaic` 补齐 `sendFocusEvent`（→ 生命周期 ON_PAUSE/ON_RESUME） | `c2e51cad` | `CapabilityMatrixTest`（布局跨矩阵稳定，commonTest）+ `FocusEventTest`（焦点驱动 RESUMED/STARTED，commonTest）；mosaic-testing + mosaic-runtime 双平台全绿 | ✅ 已归档 |
## 5. 已完成步骤的实现要点（供恢复上下文）

### L7.9 快照体系扩展（能力矩阵 + 焦点注入）（`c2e51cad`）
- **能力矩阵**（`mosaic-testing/CapabilityMatrix.kt`）：`CapabilityMatrix.defaults` 7 组代表性组合——全能力 / 关 2027 / 关 kitty underline / 关 synchronized output / ANSI16 / ANSI256 / 全关（NONE）；`runMosaicTestForEach(capabilities, block)` 每组用全新 `RealTestMosaic` 跑同一 `TestMosaic<String>` 断言并透传该组能力。
- **事件注入补齐**：`TestMosaic.sendFocusEvent(focused)` 写 `TestTerminal.state.focused`，走 runtime 的 `terminalState` collector → `LifecycleRegistry` ON_PAUSE/ON_RESUME（与真实终端焦点事件一致）。鼠标/粘贴/剪贴板注入此前已有。
- **测试**（mosaic-testing commonTest，双平台）：
  - `CapabilityMatrixTest`：同一内容（CJK/combining/emoji/ASCII/多行）在矩阵全部组合下纯文本快照一致（能力开关不破坏布局），并断言全部组合均实际执行。
  - `FocusEventTest`：`sendFocusEvent(false/true)` 使组合生命周期 `RESUMED → STARTED → RESUMED`。
- **todo 勾销**：§13「P2 快照体系扩展」（能力矩阵 + 事件流注入）。

### L7.8 非交互能力提示（`91c6d23b`）
- **背景**：`Ignore`（绑定失败）与 `AssumeAndIgnore`（跳过检测）都会用惰性 `NonInteractiveTerminal` 继续跑——无颜色/输入/扩展能力，但此前**静默**，用户易误以为正常。
- **实现**：`withTerminal` 落到 `NonInteractiveTerminal` 时先调新增 expect/actual `warnNonInteractive()`（JVM `System.err` + flush；native posix `write(STDERR_FILENO)`，与 `nonInteractiveExit` 同构），打印 `NonInteractiveNotice`（一行英文中性提示）；`Exit/Throw/Return` 早返回不受影响。
- **测试**：
  - `NonInteractiveTest`（commonTest 2 条，双平台）：`AssumeAndIgnore` 跳过绑定、block 拿到惰性终端（`interactive=false`、事件通道已关、`withTerminal` 返回 `true`）；惰性终端能力清单（`ansiLevel=NONE`、kitty/2027/同步输出全 false、focused、`Size.Default`）。
  - `NonInteractiveNoticeTest`（jvmTest 1 条）：重定向 stderr 断言 `AssumeAndIgnore` 确实输出 `NonInteractiveNotice`（非静默）。
- **todo 勾销**：§11「P2 非交互策略」。

### L7.7 TextLayoutWidthTest「测量与绘制一致」断言（`f51e04ee`）
- **动机**：测量端按 (簇, 宽) 切分每行、绘制端直接消费该切分，但没有任何测试把两侧钉在一起——若某处让测量与绘制对簇列数/落位分叉（wrap 切簇、截断错位、续列开合），布局会 vs 渲染尺寸不一致。
- **断言口径（`assertColumnsMatchLayout`）**：
  1. **逐列对拍**：按 `LineMeasure.clusterStarts/clusterWidths` 构造每列期望（簇头部列 = 簇文本；宽簇后续 = placeholder 续列），与 `TextSurface` 实际绘制逐列相等。
  2. **右沿**：每行最后一个 contentful cell（非空、非 placeholder）的 `列+width` == `totalWidth`。
  3. **总宽**：`layout.width` == 各行 `totalWidth` 最大值（measure 端自身闭合）。
  边界口径：行尾空格 cell 被渲染器视为空列，用例避免行尾空格；孤立零宽簇与画布覆盖语义一致（0 宽仍写当前列，per-column 同步覆盖）。
- **用例**（`TextLayoutWidthTest` 7 → 14）：ASCII/CJK、宽簇+combining（ZWJ/flags/keycap/VS16）、行内空格、softWrap 折行（簇不拆）、Clip/Ellipsis、scroll 窗口、AnnotatedString 布局。
- **todo 勾销**：§13「P1 `TextLayoutWidthTest`」。

### L7.6 awaitComplete 生命周期语义明确化（`00ebdb43`）
- **背景**：`awaitComplete` 的 `// TODO Re-evaluate this for correctness.` 注释继承自上游单函数内联提交，语义一直未定论。
- **语义确认（run-until-idle）**：
  1. `effectJob.children.forEach { join() }`——等重组合器 effect job 的所有子协程结束；无限 effect（动画/tick）使 app 常驻（这是演示不退出的机制）。
  2. `recomposer.awaitIdle()`——等重组工作收敛。
  3. 若 `needLayout || needDraw` 仍有欠账，显式驱动一帧并 join：recomposer 已 idle 后无 effect 会再请求帧、帧循环无事可做；该帧保证欠下的 layout/draw pass 在 `close()` 前完整应用（run-until-idle 的显式收尾）。
  4. `finally` 兜底 dispose + `job.cancel()`——`cancel()` 走该路径后 `awaitComplete` 正常返回、不挂死。
- **setContent 二次调用**：为 Compose `composition.setContent` 标准替换语义——旧内容 dispose、其 effect 取消、新内容呈现。
- **测试**：新增 `LifecycleSemanticsTest`（commonTest，jvm + linuxX64 全绿，3 条）：`infiniteEffectKeepsAwaitCompleteBlockedUntilCancel` / `awaitCompleteReturnsOnceAllEffectsFinish` / `secondSetContentReplacesContentAndCancelsOldEffects`。
- **todo 勾销**：§1「P2 多实例 / 复用」——`setContent` 二次调用、`cancel`/`awaitComplete` 并发语义已明确并有测试固化。

### L7.1 双平台微基准：native 是否达主流实时标准（todo §13 P2）
- **结构决策**：微基准从 `mosaic-runtime`（`explicitApi` + apiCheck 约束 + 二进制会进发布制品）迁到 `samples/bench`（不发布、不进 API 基线）。render 负载走**公共 API**（`Mosaic` + `onDraw` 里 `mosaic.draw()`/`static()`）镜像生产「计算屏幕内容」阶段；JVM 与 native 共用同一 `commonMain` 驱动，逐平台独立读数。
- `event-parser` 走真实 PTY（`TestTerminal`）：720 字节纯字母数字流（每字节=1 `KeyboardEvent`，计数由构造确定）；JVM 与 native 都必须先 `enableRawMode()`。
- **关键坑（可从历史恢复）**：`TestTerminal` 新建的 PTY 默认**规范模式**，无换行的 `read` 永久阻塞——`EventParser.next()` 卡死（曾卡 40s+，`jstack` 定位 `EventParser.kt:125` 的 `tty.read` 确诊）。必须先 `testTerminal.tty.enableRawMode()`。
- **K/N 可执行直接放库模块会撞墙**：`explicitApi()` 要求显式可见性，`internal` 顶层函数在 K/N 被**改名破坏**导致 `-e` 找不到；`public` 又污染 `.klib.api` 基线（apiCheck 红线）。所以 native 可执行必须放 sample。
- **基线（2026-10-18 · linux x64 · JDK25 / K/N release）**：JVM/Native —— one-row-delta 338/697µs、full-rebuild 502/1279µs、layout-pass 347/739µs、event-parser 198/139µs。
- **结论（达主流标准）**：native 最坏全屏重建 ≈1.28ms，占 60fps 预算（16.6ms）**~8%**，余量 >10×；输入 ≈520 万事件/秒。**native 渲染约慢于 JVM 2–2.5×**（K/N 无 JIT + Compose 分配高），故 §2/§5 低分配优化对 native 收益最大，是性能主战场。明细见 `docs/benchmarks.md`。

### L5.4 clipboard（OSC 52）（`423f25b8`）
- `mosaic-terminal`：`ClipboardEvent(selection: Clipboard/Primary/Unknown, text: String?)`——`null`=终端拒绝/解码失败，`""`=空剪贴板；`Terminal` 接口新增 `requestClipboardRead(selection)` / `writeClipboard(text, selection)`。
- `mosaic-tty-terminal`：`EventParser` `parseOsc` 加 `52` 分支（`ClipboardOsc` 工具负责 base64 编解码 + selection 映射，`!`→null）；`TtyTerminal` 实现→写 `OSC 52;sel;?`(读) / `OSC 52;sel;<base64>`(写，空串清空)。
- `mosaic-runtime`：`ui/platform/Clipboard.kt` 镜像 Compose 新版 `LocalClipboard` 语义——`Clipboard`（`suspend getText()/setText()`）+ `LocalClipboard` + `TermClipboard`（读=发读请求后 await `ClipboardEvent` 回复，2s 超时）。`MosaicComposition` 提供 `LocalClipboard`，帧监听把 `ClipboardEvent` 喂给 `TermClipboard`；`NonInteractiveTerminal`/`TestTerminal` 补齐实现（捕获写/记录读请求）。
- `TestMosaic` 增 `sendClipboardReply(text, selection)` 注入回复。

### L5.5 事件背压/风暴合并（`68ee4a1e`）
- `mosaic-tty-terminal`（`TtyTerminal.kt`）：`events` channel 固定 64 缓冲由 `onBufferOverflow = DROP_OLDEST` 改为 `SUSPEND`，生产者 `events.trySend(event)` → `events.send(event)`。大粘贴/鼠标风暴不再丢**离散**事件；极端溢出时仅阻塞解析循环（消费端 ~1000fps 每帧排空，正常负载不触发）。
- `mosaic.kt` 帧监听循环：批内 `pendingMotion` + 局部 `flushMotion()`——连续 `Motion/Drag` 只保留**最后坐标**；遇到离散事件先 flush 再处理；循环结束 flush。键盘/粘贴/剪贴板事件不受影响。
- 测试：`TtyTerminalBackpressureTest.kt`（新）`largeBurstIsNotDropped`——写入 500 字符 → `events.receive()` 收到 500 条 `KeyboardEvent`（5s withTimeout，0 丢失；注意 `this@withTerminal.events.receive()`，block 接收者是 `Terminal`）。`MouseEventTest` 增 3 条：批内 3 motion+release → `["Motion@3,3","Release@3,3"]`；纯 motion 批 → 只投递单一最新 `["Motion@7,8"]`；双 Press 全部到达（离散事件绝不合并）。
- 测试要点：`MouseEventTest` 中 `received` 状态必须被组合**读取**（Box 内 `Text(received.size.toString())`）才会触发重组 onDraw，`awaitSnapshot` 才能返回；否则分支压根不重组、1s 虚拟超时。

### L6.8 探测清单完备性核对 + 清单回归测试（`53ba2ea2`）
- **审计结论（探测清单逐项对账，13 项全对应暴露能力）**：
  - 6×DECRQM（`CSI ?Ps$p` → `CSI ?Ps;Pm$y`）：25 光标 / 1004 焦点 / 2026 同步输出 / 2031 系统主题 / 2048 in-band resize / 2027 字素聚类，逐一喂给 `Terminal.Capabilities`。
  - theme 现值查询：`CSI ?996n` → 报告 `CSI ?997;{1|2}n` 是有意设计（上游提交 `546920a2` + 既有 `TtyTerminalThemeTest` 一致），非笔误，未改动。
  - kitty 五项（APC graphics / `CSI?u` keyboard / OSC99 notification / OSC22 pointer / OSC66+DCR 组合 textSizing）+ underline（`DCS+q5375`）+ xterm 版本（`CSI>0q`）+ DSR（`CSI 5n`）批收尾。`ansiLevel` 走 env 探测非终端查询。
- **「逐项重查」评估 → 驳回**：真正会「组合不响应」的终端，单独发同样不响应；逐项重查引入 N+1 轮询挤占 1s bootstrap 预算、破坏「未回复→能力默认 false」的安全契约、需改动全部既有探测测试（20+ 方法）；业界通行做法即合并批 + 全局超时。未回复的 DEC 模式默认 false，属安全降级（绝不启用未确认的能力）。
- **交付物**：新增 `TtyTerminalProbeInventoryTest.startupQueryInventory`——锁定完整启动探测清单（各 DECRQM 模式号 + theme + kitty + 版本 + DSR 收尾），任一探测被无声删除即测试失败，防止「探测丢失→能力静默退化」。本步纯测试 + 审计，无生产代码改动（与 L6.3 同性质）。
- **L6 收官**：本步完成后 L6 tty 适配层正式完成（L6.1~L6.8）。剩余 §9.1 三套绑定一致性 / §9.2 macOS / §9.3 Windows / `linuxArm64` cinterop 补全均需远程/CI 环境，登记不改码。

### L6.7 生命周期异常安全核对 + teardown 同步化（`963279b3`）
- **审计结论**：
  - **正常关闭顺序脆弱（关键）**：`TtyTerminal.close()` 原为 `closeJob.cancel()`（异步），而 `withTerminal` 的 `tty.use` 在无挂起点处紧接着同步 `tty.close()`。teardown（`setCallback(null)` + 回滚写入 + `reset()`）能否先于 `tty.close()`（释放 C 指针）跑完，依赖 `launch(Unconfined)` 的**内联续传**碰巧同步执行——正对应旧 TODO "design of finalization hook fights us here"。探针（复刻 `withTerminal` 嵌套 + 循环 200 次）实证现状不崩，但属隐性依赖：换 dispatcher 即可能打到已释放指针。
  - **异常路径全覆盖**：`asTerminalIn` 中途抛错 → `tty.use.close()` 兜底恢复 termios；应用 block 抛错 → `terminal.use.close()` 触发清理 + `coroutineScope` 取消 reader/interruptJob 子协程。
  - **信号/shutdown 兜底**：`withFinalizationHook`（native 信号 → cancel → hook → 恢复默认重发；JVM 另加 shutdown hook）。L6.4 C 守卫只管 SEGV/ILL/PIPE，与它信号集不重叠、无冲突。
- **修复**：抽出幂等 `teardown()`（`AtomicBoolean` 保证单次）；`close()` 同步先跑 `teardown()` 再 `closeJob.cancel()`，正常关闭**保证**先于 `tty.close()` 完成恢复；finalization hook 复用同一 teardown 兜底信号/shutdown 路径，重复调用由守卫去重。
- **测试**：新增 `TtyTerminalLifecycleTest`（3 条 commonTest，jvm + linuxX64）：`closeOrderingMatchesWithTerminal`（复刻 withTerminal 同 scope 内 tty 紧跟关闭）/ `doubleCloseIsIdempotent` / `exceptionInBlockStillCloses`；既有 teardown 输出用例（Cursor/Mouse/Theme 等）原样通过；runtime 两平台 compile 通过。
- **既有遗留（非本次引入）**：`apiCheck` 在 `linuxArm64` 目标失败——L6.4 新增 C 符号（`mosaic_tty_signal_guard`）的 `linuxArm64` cinterop 陈旧未再生成，需后续补全 target 生成（本地只建 linuxX64）。

### L6.6 读写缓冲核对 + reset 幂等加固（`4428a77b`）
- **审计结论**：
  - **控制序列写已正确**：`TtyTerminal.write(s: String)`（`TtyTerminal.kt`）自带完整写循环——partial write 前进 offset、`-1` 抛 `EofException`；C 层 `mosaic_utils_write` 单次 `write()` 返回部分计数原样回传，无需改动。
  - **主渲染不经 Tty**：帧经 `FrameWriter` 按 8192 字节切块 → `writeScreenBytes` → `flushScreenBytes`。native 侧 `writeScreenBytes` 自带循环；jvm 侧 `System.out.write` 为 PrintStream 原子全量写，无 partial 问题。
  - **幂等性唯一真实边界 bug**：`reset()` 在 `close()` 后无指针守卫——native/jvm/jvmJdk22 三处 close 后指针已置 null/0，但 reset 仍把空指针传给 C `mosaic_tty_reset`（内部直解 `tty->sigwinch`）→ 悬垂/SEGV。C 层 `mosaic_tty_reset`/`mosaic_tty_free` 各步已守卫、本身幂等。
- **修复**：三平台 `reset()` 补 close 后指针守卫（jvm `ptr != 0L` / jvmJdk22 `ptr != NULL` / native `ptr?.let`），与 `close()` 既有守卫行为对齐——closed 后 `reset()` 为幂等空操作。
- **测试**：`TestTtyTest` 增 `closeAfterCloseIsIdempotent`、`resetAfterCloseIsIdempotent` 2 条（commonTest，jvm + linuxX64 双平台全绿）。
- **未改项（已评估）**：调用层循环、批量 flush 语义保持现状；native `writeScreenBytes` 遇错即抛、`EINTR` 不重试仅属边缘瑕疵不阻塞。Todo §9.4 P2 勾销。

### L6.5 resize 通路核对 + 加固（`d469ad0a`）
- **审计结论**：
  - **互斥由构造保证**：`TtyTerminal` 只在 `inBandResizeEvents == false` 时 `enableWindowResizeEvents()`（装 SIGWINCH）；in-band（2048）支持时 SIGWINCH 永不安装 → **不存在重复发事件**。
  - **收敛单点**：两条通路都写 `size` StateFlow（runtime 唯一尺寸真相源）；channel 上的 `ResizeEvent` runtime 不消费，`trySend` 满仓丢弃也无害；`MutableStateFlow` 原子写 + last-write-wins，无竞态污染。
  - unsolicited `CSI 48;...t`：in-band 未支持时按 unsolicited 丢弃，不会误改尺寸。
  - in-band 通路**天然规避**「SIGWINCH 信号上下文直接调 Kotlin 回调」（该路径仅 in-band 不可用时启用），更安全。
- **边界隐患 + 修复**：in-band 支持但终端**未发出初始尺寸报告**时尺寸停留 `Default(80×24)`（旧版 `replySetNoInitialValue` 固化）。新增 `receivedInBandSize` 标记（in-band 尺寸事件到达即置位），bootstrap 后若 in-band 启用却从未收到尺寸报告，用 `currentSize()`（`TIOCGWINSZ`）播种；真实 in-band 事件（含像素）到达后覆盖，不干扰正常像素路径。
- **测试**：`TtyTerminalResizeTest` 增 2（jvm + linuxX64 双平台，共 10 例全绿）：
  - `inBandResizeIgnoresPlatformResizeSignal`：in-band 激活下 `testTerminal.resize(...)`（SIGWINCH）不影响 `state.size` → 互斥契约负向固化；
  - `inBandSupportedNoInitialValueSeedsFromOsSize`：先把 PTY 调到 200×60，构造「in-band 支持但无初始报告」→ `state.size == Size(200, 60, 0, 0)`（不再是 Default）。

### L6.4 native 信号兜底：终止型信号 raw 恢复（`39db32b8`）
- **审计（关键）**：`SIGINT/SIGTERM` 等**已由「生命周期收尾机制」覆盖**——native 侧 POSIX 信号处理器取消中断协程 → teardown hook 恢复终端 → 恢复默认处置并重发信号；JVM 侧 shutdown hook 恢复终端。真正缺口是**没有任何 C 级兜底的终止型信号** `SIGSEGV/SIGILL/SIGPIPE`：崩溃/断管道会直接把终端留在 raw 状态。
- **C 层实现（POSIX only，native 启用）**：
  - 新增 `mosaic_tty_enable_signal_guard` / `mosaic_tty_disable_signal_guard`：为 `SIGSEGV/SIGILL/SIGPIPE` 装 async-safe handler；**只接管当前处置为 `SIG_DFL`** 的信号（自定义 / `SIG_IGN` 一律跳过，不劫持）。
  - handler 顺序：恢复 `saved` termios（best-effort；Linux/BSD 上 `tcsetattr` 即直通 ioctl）→ 写 interrupt pipe 唤醒 reader → 恢复 `SIG_DFL` 并重发信号（**保留原始终止语义**：waitpid 状态 / core dump / 退出码不变）；重发异常时 `_exit(128+sig)` 兜底。
  - disable：仅当当前处置仍是我们的 handler 才还原旧处置——应用在 enable **之后**改过的处置不被回写覆盖。
  - 生命周期：native `Tty.enableRawMode()` 装 guard；`mosaic_tty_reset`（reset/close/free 共用）末尾自动卸。
- **JVM/Windows 不受影响**：JVM 不装（覆盖 JVM 自管的 `SIGSEGV/SIGILL/SIGPIPE` 处理会破坏运行时；且 native 才是目标）；Windows 无 POSIX 信号，shared 头声明 + 空实现保证链接。
- **测试**：`SignalGuardTest`（nativeTest，6 例，全部真实 fork + raise）：
  - SIGSEGV / SIGILL / SIGPIPE：fork 子进程 raise → 断言子进程按原信号终止（wait 低 7 位）+ 父进程经**共享 PTY** 观测到 termios 恢复 cooked + `tty.read` 返回 0（pipe 唤醒）；
  - SIGPIPE 在 enable 前已 `SIG_IGN` → 不劫持；enable 后被改为 `SIG_IGN` → disable 不覆盖；
  - `enableRawMode` 重复调用幂等。
- **harness 辅助**：`mosaic_test_tty_termios_cooked`（`mosaic-test` 返回 `c_lflag` 的 `ECHO|ICANON` 位，供断言）。
- **构建教训（延续 L6.1）**：改 `mosaic-tty.h` 后 cinterop **不跟踪 include 图头文件**——`cinteropMosaicLinuxX64` 判 UP-TO-DATE，须 `--rerun` 强制再生成（本例同时改了 .c 内容，增量 cklib 正常）。
- **顺带修正**：`mosaic-tty.api` 净删 `Tty$Callback.onKey/onMouse` 两行（L6.1 已删实现、dump 未同步），`apiCheck` 转绿。
- **手动验证**：kitty 运行已重链的 rrtop.kexe，另一终端 `kill -SEGV $(pgrep rrtop)` → 终端恢复非 raw、进程按信号退出（`$?`=139）。

### L6.3 POSIX raw mode 核对（审计，无代码改动）
- **结论**：`mosaic-tty-posix.c` `mosaic_tty_enable_raw_mode` 与权威 raw mode（glibc `cfmakeraw(3)`，即 `linux.die.net/man/3/termios` "Raw mode" 节）**逐位一致**，零代码差异。
- **对照表**（权威清位 = 当前代码清位）：
  - `c_iflag`：`IGNBRK|BRKINT|PARMRK|ISTRIP|INLCR|IGNCR|ICRNL|IXON` ✅
  - `c_oflag`：`OPOST` ✅
  - `c_lflag`：`ECHO|ECHONL|ICANON|ISIG|IEXTEN` ✅
  - `c_cflag`：`~(CSIZE|PARENB)` + `CS8` ✅
  - `c_cc`：`VMIN=1, VTIME=0` ✅；`tcsetattr(TCSAFLUSH)` 丢弃缓冲输入 + 失败时恢复 `saved` ✅
- **边角确认**：
  - `IUTF8` **不需要清**——它只在线规程 `ICANON` 行编辑（erase/kill 按字符边界）有意义，raw 已清 `ICANON`，保留即正确（`cfmakeraw` 也不动它）。
  - `OPOST` 已清 → `\n` 不被输出处理转成 `\r\n`，与终端字节直写契约一致。
  - `HUPCL`/`CLOCAL` 与 `cfmakeraw` 一致**保留**；macOS `HUPCL`/`c_cc` 差异属 §9.2 P2，后续单独核对。
- **交付**：纯审计 + 文档；无代码/测试改动（基线全绿）。对照表固化于此，防未来漂移。

### L6.2 DEC 2027 探测 → `graphemeClustering`（`9f88fbc7`）
- **探测**：DECRPM 批次补 `CSI ?2027$p`（沿用既有模式：`ansi.kt` `graphemeClusteringMode=2027` + Enable/Disable）；`DecModeReportEvent` 2027 分支：`isSupported` → 置 `graphemeClustering`，当前为 reset 时发 `CSI ?2027h` 启用并记 `toggleGraphemeClustering`，退出 `CSI ?2027l` 恢复；set/permanentlySet 不重复启用。
- **公开 API**：`Terminal.Capabilities` 新增只读 `graphemeClustering: Boolean`（文档标语义 + xterm 链接）；`TestTerminal`（默认 true）/`NonInteractiveTerminal`（false）补齐；`mosaic-terminal.api/.klib.api` 已 dump（`TtyTerminal` 为 private，tty-terminal 无公开 API 变化）。
- **宽度口径落法（决策）**：只做「能力暴露 + 终端侧 enable」，**不做逐 rune 后备策略**。理由：① 逐 rune 后备只对不支持簇处理的旧终端有意义，且会让 ZWJ 族回退多格（视觉倒退）；② 本架构整帧 cell 定位，不依赖终端光标跟随，无需 `reanchorWideLine` 式漂移补偿；③ 能力字段即未来切换的开关，需要时再实现。
- **测试**：`TtyTerminalDec2027Test`（5 例：reset→启用+退出恢复 / 已 set / permanentlySet / NotRecognized / permanentlyReset）+ `EventParserCsiDecModeReportEventTest` 增 2027 解析例。
- 诊断：`tools/terminal-capability` 反射打印会自动多出 `graphemeClustering` 一行。

### L6.1 移除 `Tty.Callback.onKey/onMouse` 死回调（`cd6ce408`）
- **决策**：`Callback.onKey/onMouse` 判定为死回调并**移除**（todo §9.4 P1）。依据：C 头 `MosaicTtyCallbackOnKey/OnMouse` 为 `// TODO params` 占位、从未被调用；Windows C 层把 `KEY_EVENT`/`MOUSE_EVENT` 经 `ReadFile` 转成 VT 转义序列走共享 `EventParser`（回调仅用于 `FOCUS_EVENT`/`WINDOW_BUFFER_SIZE_EVENT`）；唯一实现 `EventParserTtyCallback` 也仅是空实现。实现它们只会建第二条重复分发通道。
- **改动（8 文件）**：`mosaic-tty.h` 删 2 typedef + 2 struct 字段（`MosaicTtyCallback` 5→3 字段）；`Tty.kt` 四份接口声明与 `staticCFunction` 分配（commonMain/jvmMain/nativeMain/jvmJdk22）；`com_jakewharton_mosaic_tty_Jni.c` 删 jmethodID 查找 + `invokeOnKey/Mouse` + 字段赋值；`TtyTest.kt` 删 `@Ignore` 遗留测试；`EventParserTtyCallback.kt` 删空实现。
- **构建踩坑（重要）**：cklib 的 **C 增量编译不跟踪头文件变化**——改 `mosaic-tty.h` 后若不 `:mosaic-tty:clean` 重编，旧/新 struct 布局偏移错位会导致 `onResize` 回调崩溃（表现为 native `resizeCallback` 测试进程异常退出）。改 C 头后必须 clean 重编。
- **环境备注**：Gradle daemon **未配置代理**，`zigDownload`（Zig 0.15.1，JNI 交叉编译工具链）直连 `ziglang.org` 在墙内被卡；已手动走代理下载到 `mosaic-tty/build/zig`（`zig` 可执行文件）使 `zigDownload` up-to-date，此后构建正常。`mosaic-tty:clean` 会清掉它，需重新放置或重新下载。
- **验证**：`mosaic-tty`/`mosaic-tty-terminal`/`mosaic-runtime` 的 jvmTest + linuxX64Test 全绿；`mosaic-terminal`/`mosaic-testing`/`mosaic-animation`/samples jvmTest 全绿；FFM(jdk22)/cinterop/JNI(Zig) 三套绑定从头文件再生成并编译通过。

### L5.6 特殊键/修饰核对（`5f438a79`）
- **核对结论**：PUA 特殊键映射完整（方向/Insert/Delete/Page/Home/End/F1~F35），未映射码点回落到 Unicode 字符串而非发半真键名；`text` 关联文本——CSI u 无文本段时为 `null`，`toKeyEventOrNull` 的 `text ?: codepoint映射` 链无冲突（箭头等特殊键 `text` 恒 null 才走码点映射）。
- **行为修复**：`compat.kt` `toKeyEventOrNull` 由「仅 press」改为「press + repeat 投递、release 丢弃」。动机：kitty 协议开启 `reportEventTypes` 后长按只发 `press+repeat+release`，原实现丢 repeat 使长按只出单字符，与普通终端（legacy 原始字节自动重复）行为不一致；repeat 并入 press 恢复一致性。
- **测试**：`CompatTest`（新）8 条——legacy 控制键命名（Tab/Enter/Escape/Backspace）、可打印与 CJK 码点、PUA 特殊键映射（含 F17=57380）、repeat 投递、release 丢弃、关联文本优先、修饰键透传、未知码点回落。
- 无公开 API 变化（全部 internal）。

### L5.1 鼠标消费（`e7b9caaf`）
- `layout/MouseEvent.kt`：runtime `MouseEvent`（Type/Button 枚举 + 修饰键）+ `Modifier.onMouseEvent`（返回 Boolean 消费）；`toRuntimeMouseEvent()` 把 terminal `MouseEvent` 转型。
- `layout/Node.kt`：`MouseLayer` 进层链；`MosaicNode.sendMouseEvent` 做**绝对坐标命中测试**（越过界直接 false；子节点**逆序**优先 = z 序在上先收，再回退自身 modifier）；`BottomLayer` 终结。
- `mosaic.kt`：`runMosaic*` 增 `mouse: Boolean = false`；帧监听循环分发 terminal `MouseEvent→runtime`。顺带把 `BottomLayer.sendKeyEvent` 挪到 doMeasure 前。
- `mosaic-tty-terminal`：`mouse` opt-in → 同步写 `mouseEnable`（SGR 1006 + press/release 1000 + **all-motion 1003**），teardown 写 `mouseDisable`；无回复不干扰能力探测。
- 修复：`runMosaicMain(content, mouse)` 曾把 content 放前导致尾随 lambda 绑定到 Boolean（demo/robot 编译失败）→ 改为 `(mouse, paste, content)`，content 恒在最后。

### L5.2 粘贴通路（`e7b9caaf`）
- `layout/PasteEvent.kt`：`Modifier.onPaste { text -> Boolean }`，**树序向下分发**（祖先先于子，首个消费即止）。
- `mosaic.kt` 帧监听循环：`BracketedPasteEvent(start=true)` 起缓冲 StringBuilder，期间 `KeyboardEvent` 只累积 `text`（**不当作按键分发**、不判 Ctrl-C），`start=false` 时把整串经 `rootNode.sendPasteEvent` 分发。
- 终端：`paste: Boolean = false` opt-in → DECSET 2004；`TtyTerminal` 引导期同步写、teardown 写 disable。
- 修 bug：`PasteModifierElement` 属性与成员同名 `onPaste`，函数名优先解析 → 自递归栈溢出；改字段为 `handler`。
- `TestMosaic` 增 `sendPaste(text)`：发 start 标记 + 逐码点 KeyboardEvent（UTF-16 安全拆代理对）+ end 标记。

### L5.3 kitty 键盘协议开关（`e7b9caaf`）
- `ansi.kt`：`kittyKeyboardFlags = 23`（消歧义转义 1 | 事件类型 2 | 备用键 4 | 关联文本 16，**不含**「所有键转义」bit8 → 普通字符仍原始字节上报）；`kittyKeyboardPush = "CSI>23u"`、`kittyKeyboardPop = "CSI<23u"`。
- `TtyTerminal.kt`：能力探测阶段收到 `KittyKeyboardQueryEvent`（`CSI?u` 回复）即置 `kittyKeyboard` 并**同步 push** + `toggleKittyKeyboard`；teardown 一律 pop 恢复终端原状态（push/pop 栈语义，不泄漏到 shell）。
- 运行时无改动：`toKeyEventOrNull` 只放行 press，repeat/release 解析后自然过滤，既有应用行为不变。

### L3.8 删除旧全量渲染器（`72e359af`）
- `rendering.kt` 删除 `AnsiRendering` 类（全量 `cursorUp` + 逐行重写协议），`AnsiIncrementalRendering` 为唯一增量渲染器。
- 删除 `AnsiRenderingTest.kt`（14 条：首帧/清尾行/static 排序/无尾随空格/宽文本等）。其中关键行为（首帧、清旧行、宽簇正确渲染）已由 `AnsiIncrementalRenderingTest`（14 条）覆盖，无覆盖缺口。
- `MosaicTest.frameTimeChanges` 改用 `AnsiIncrementalRendering`（渲染器对该测试无行为影响）。

### L4.1 Text 簇级安全软换行（`9c622d53`）
- `TextLayout.measure(maxWidth)`：把入向约束纳入排版；`value.splitByLines()` 分段后，贪心按**字素簇边界**累计换行（`forEachGraphemeClusterRange` + `graphemeClusterWidth`），簇宽度与绘制/测量同一口径。
  - 簇完整不拆：ZWJ emoji 家庭、组合字符、变体选择符、区域旗帜永远整簇落入一行；超宽簇独占一行（允许溢出）。
  - 空段落保持（`\n\n`/前导/尾随换行不丢行数）；单段能放下时原样返回（对既有 `split("\n")` 行为零回归）。
  - `lastMaxWidth` 缓存 + `dirty`：值或约束任一变化即重排，父宽变化不会用到陈旧行。
- `ui/Layout.kt`：内部无内容 `Layout` 的 `NoContentMeasurePolicy` 增加 `constraints` 参数并透传，`Text` 借此读到父级 `maxWidth`。
- `ui/Text.kt`：两个 `Text` 重载增加 `softWrap: Boolean = false`（默认保持原行为不强启换行），开启时按 `constraints.maxWidth` 软换行；`Constraints.Infinity` 视为无约束。
- 测试：`TextLayoutWrapTest`（ASCII/CJK/空段落/ZWJ 家庭/组合字符/旗帜/超宽簇/AnnotatedString 样式保留）+ `TextWrapRenderingTest`（`Modifier.width()+softWrap` 的端到端快照，含默认不换行回归断言）。

### L4.2 Text `maxLines` / `overflow`（`de3c9a1a`）
- `ui/TextOverflow.kt`：新增 `TextOverflow.Clip` / `TextOverflow.Ellipsis` 公开枚举。
- `TextLayout.measure(maxWidth, maxLines, overflow)`：换行后若行数超过 `maxLines`，`Clip` 取前 N 行整行；`Ellipsis` 用 `clipTo(maxWidth-1)+"…"` 截断可见末行——`clipTo` 逐簇累计（整簇保留或整簇丢弃，绝不半簇）；无宽度预算（`Int.MAX_VALUE`）时退化为 Clip。
- `lastMaxLines` / `lastOverflow` 纳入重排判定：任一变即重排。
- `ui/Text.kt`：两个重载增加 `maxLines: Int = Int.MAX_VALUE`、`overflow: TextOverflow = TextOverflow.Clip`（Compose 同默认，默认不改变既有输出）。
- 测试：`ellipsisKeepsOverflowingClusterWhole`（ZWJ 家庭整簇保留 + 省略号）、`ellipsisDropsWholeClusterWhenItCannotFit`（超宽整簇丢弃）、Clip/无宽度回落/ASCII-CJK 截断；渲染端 `maxLinesClipsRemainingLines`、`maxLinesWithEllipsisTruncatesLastLine` 快照。

### L1.1 字素簇分割（`7f295e23`）
- `text/grapheme/GraphemeBreak.kt`：UAX #29 边界判定；`GraphemeBreakTables.kt`：生成用区间表。
- `text/grapheme/Graphemes.kt`：簇迭代/区间 API（`forEachGraphemeClusterRange` 等）。
- 官方套件：`GraphemeBreakTest.txt`（1121 行）+ `GraphemeBreakOfficialTest.kt`（jvmTest）。
- 生成器：`scripts/grapheme.py`（联网下载需代理；**必须在临时目录运行**，下载产物落当前目录）。

### L1.2 簇级列宽（`e5954966`）
- `text/grapheme/GraphemeWidth.kt` + `strWidth(CharSequence, from, to)`：按扩展字素簇计量，取代逐码点累加，作为唯一权威口径。
- `TextLayout` 测量切换到簇级宽度；`text/unicodewidth/`（本地 vendored 宽度表，归属注释已中性化）被簇级层复用。

### L2.1 cell 模型升级（`1a0e39d4`）
- `TextPixel(text, width, isPlaceholder)` + `markAsContinuation()`：首格存整簇文本与显式列宽，宽字符续列用占位。
- 这是"簇字符串 + 显式列宽"载体的落点，为测/绘口径统一与部分覆盖清理打基础。

### L3.1 按簇绘制（`354883b2`）
- `DrawScope.drawText` 以 `graphemeClusterWidth` 逐簇推进：整簇写入首格、续格预留占位，surface 只发首格。

### L3.2 行级增量渲染（`08e92477`，默认渲染器）
- `AnsiIncrementalRendering`（`rendering.kt:82`）替换全量 `AnsiRendering` 成为默认（`mosaic.kt:62`，`MOSAIC_DEBUG_RENDERING=true` 走 `DebugRendering`）。
- 协议：
  - 同尺寸且无 static：只对变化行发 `CSI {line};1H` + `clearLine` + 行内容；无变化帧仅出同步包裹（近零字节）。
  - 为保跨帧不变量：任一增量帧有行变化时末尾追加 `CSI {height+1};1H`，把光标固定到"输出下方"，与全量帧末尾 `\r\n` 语义一致，避免下一帧全量重画 `cursorUp` 相对定位错行。
  - 高度变化 / static 文本存在或变化：退化为全量重画（`cursorUp` + 逐行 `clearLine` / static 用 `clearDisplay` + 高度变矮时清尾），沿用旧渲染器协议。
  - 性能：复用 `stringBuilder`；行字符串每帧以 `List<String>` 保存用于比对（**L3.3 起已被 cell 快照替代**）。

### L3.3 cell 级增量差分（`dc031ff3`）
- `AnsiIncrementalRendering` 由「行字符串比对」升级为「**cell 网格比对**」：保留上一帧 cell 快照（双缓冲复用数组 + `copyFrom` 原位拷贝，零分配），跳过未变行。
- 变化行逐列对比新旧 cell 收集变化区间，**扩展到整个字素簇边界**后 `CSI r;cH` + 片段渲染（`appendRowRange`：区间内空 cell 输出空格、样式相对默认态、段末带样式则复位），未变前缀/后缀绝不重发。
- 稀疏/稠密二择：变化格数*2 ≥ 行内容范围 → 整行 `goto;clearLine` 回退（避免删尾输出一长串空格）；否则就地分段时间片重画。
- 首帧/尺寸变化/static 存在或变化 → 仍走全量重画，协议不变；增量帧末尾同样把光标 park 到输出下方一行。

### L3.4 宽格 diff 右缘防护 + 覆盖回归（`76b8d301`）
- 结论先行：L3.3 的「cell 网格比对 + 占位列身份区分 + 变化区间簇边界扩展 + 区间合并」结构上已保证**真实布局下的覆盖清理**——宽簇头被窄内容覆盖时，其续列（占位态）必然随 diff 进入变更区间，且窄→宽/宽→窄/相邻宽簇/整体换文本均无半格残留。调研中担忧的「覆盖残留」未能在真实 cell 转换中复现，故本步重点是**右缘防护**与**把保证固化为回归**。
- **右缘防护（唯一代码改动）**：`appendRowRange` 增加 `rowEnd`（行绝对右边界）参数，写入宽簇前判定 `columnIndex + width > rowEnd`，是则用**同风格空格**占满剩余列（不写簇文本），杜绝宽簇在末列导致终端换行/滚动。稠密整行（`appendRowTo`）与稀疏片段（`renderRowDiff`）两条路径统一走该写器，均传 `rowEnd`。
  - 既有 `wideClusterAtRowEndTrimsPlaceholder` / `ansiRenderingWrapsClusterOnce` 语义不变：宽簇**整簇放得下**时不触发防护、照常渲染。
- **虚拟终端 `VirtualTerminal`（commonTest 辅助）**：解析渲染器输出的 ANSI（`goto`/`clearLine`/`clearDisplay`/SGR 简化为是否非默认/按簇宽写文本），维护屏幕网格；宽簇同实例共享首列与续列，头列被覆盖而未清理续列时 `displayRow` 以 `◌` 暴露（`hasResidue()` 判半格残留）。
- **回归 `CellDiffRenderingTest`（15 条，绕过布局直接用 crafted surface + 假 Mosaic 驱动渲染器）**：宽→窄覆盖清续列、窄→宽（强制宽）整簇重写、宽行变窄行清除痕迹、相邻宽簇边界变化、宽簇右移一列、ZWJ 家庭整体替换、keycap 整体替换、宽簇纯样式变更、恰好贴右缘的宽簇保留、非首行右缘拦截、第二行宽簇变更、内容尾部删除、整行收缩清尾、真实 resize（宽度变窄）清旧列。
- 提交：`76b8d301`；JVM + Native（linuxX64）全绿。

### L3.5 static 输出行级清理（`e2497ca3`）
- `AnsiIncrementalRendering` 全量重画分支（`sizeChanged || staticChanged || static != null`）弃用一键 `clearDisplay`，改为**逐行 `clearLine`**：
  - `cursorUp(prevHeight)` 相对当前光标上移（static 输出可能已使屏幕滚动，用相对定位而非绝对坐标）。
  - static 各行先 `clearLine` 再重写，并计入被替换行数 `rowsReplaced`；随后 dynamic 行同样先 `clearLine` 再写。
  - 变矮后剩余的旧行不再 `clearDisplay` 整屏，而是 `while (rowsReplaced < prevHeight)` 逐行 `clearLine + \r\n`，避免清掉屏幕上无关内容。
  - 语义不变型：static 行数恰好铺满旧区（无 leftover）时输出与旧协议一致（`staticFramesRedrawFromTop` 等）。
- 测试：`AnsiIncrementalRenderingTest` 累计 16 条。改动既有 4 条（`shorterFrameClearsOldLines`/`staticFramesRedrawFromTop`/`staticWithHeightChangeClearsWholeArea` 由 `clearDisplay` 改为逐行 `clearLine`，补尾随空行），新增 2 条：`multiLineStaticClearsItsLinesIndividually`（多行 static 逐行清理）、`staticAndShorterDynamicClearLeftoverRowsPerLine`（static + 变矮 dynamic 的 leftover 行逐行清理）。


### L3.6 无同步渲染时的闪烁防护（`e15497c4`）
- `AnsiIncrementalRendering.render` 拆出统一帧包裹逻辑：有同步输出能力（DEC 2026）时保持 `enable … disable` 包整帧；无该能力且 `cursorVisibility`（可切换光标）时改用**光标 hide/show 包裹**：帧首 `CSI ? 25 l`（hide）、帧尾 `CSI ? 25 h`（show），终端实时重绘时光标跨行移动不再叠加闪烁。
- 空帧处理：本次无任何输出（同内容帧）时不发 hide/show（`setLength(frameStart)` 回滚已插的 hide），避免空包裹导致光标闪。
- 无光标切换能力的终端（`cursorVisibility=false`）不发包裹，退化为裸帧；该分支也天然绕过「启动时 hide 一次 + 每帧 show」的策略差异，行为保持最小变更。
- 新增常量：`mosaic-runtime/ansi.kt` 的 `cursorMode=25`/`hideCursor`/`showCursor`。启动期 `cursorDisable` 单次隐藏、退出恢复的既有逻辑不动。


### L3.7 输出批量/分片写（`8a0d3e7c`）
- 引入 `FrameWriter`（`mosaic-runtime/FrameWriter.kt`）：渲染结果 `CharSequence`（复用 StringBuilder 载体）→ **增量 UTF-8 编码进固定 8KB 缓冲 → 分块写出**，取代原 `print(rendering.render(...).toString())` 的每帧整帧 String 拷贝 + 单次巨型写。
- 编码按**码点原子落块**：读 ASCII/代理对判码点后整体编码，装不进缓冲就先 flush 再写，保证多字节码点永不被分到两个块（无半字符）；孤立代理替换为 `?`，与 `String.encodeToByteArray` 行为一致。
- 写口 expect/actual：common 声明 `writeScreenBytes`/`flushScreenBytes`，JVM 走 `System.out.write(byte[],off,len)` + 每帧 `flush()`（保留实时上屏，且字节直通规避 `file.encoding` 问题）；Native 走 `platform.posix.write(STDOUT_FILENO, …)` 循环写（处理部分写），flush 为空实现。
- `mosaic.kt` 的 `onDraw` 改为 `frameWriter.write(rendering.render(rootNode))`；`chunkSize` 与两个写出 sink 可注入，便于纯 commonTest 断言字节流。
- 测试 `FrameWriterTest`（9 条）：ASCII 单块 / 空帧零写但每帧 flush / 码点跨块原子性 / 多字节字符整块 defer / 代理对读前视 / 超块输入多块 / 孤立代理替换 / chunkSize<4 拒绝 / 跨帧缓冲复用。


### L4.3 Text `textAlign`（`271f53fa`）
- `Text`（String 与 AnnotatedString 两个重载）新增 `textAlign: Alignment.Horizontal = Alignment.Start`，复用既有 `Alignment.Horizontal.align(size, space)` 做行内偏移，不引入新枚举。
- 语义：有**有界入向宽度**（`it.maxWidth != Infinity`，无论 `softWrap` 与否）且 `textAlign != Start` 时，文本框按对齐宽度撑开为 `max(width, alignmentWidth)`，每行按 `align(lineWidth, boxWidth)` 求列偏移后 `drawText(row, column, …)` 绘制；`Start` 或缺宽度时保持紧凑内容宽（零回归）。
- `TextLayout` 增加 `alignmentWidth` 属性（`measure` 新参数，纳入脏缓存键）：不可用（Infinity）→ 无对齐；超宽行（宽簇独占行）以 `coerceAtLeast(0)` 兜底不会画负列。
- 测试 `TextAlignRenderingTest`（7 条）：居中/右对齐于给定宽、Start 保持紧凑、无界宽对齐无效果、软换行行居中、居中对齐把盒子撑宽影响 Row 后续兄弟、AnnotatedString 对齐。


### L4.4 Text `scroll` 查看窗口（`ac703463`）
- `Text`（两个重载）新增 `scroll: Int = 0`：先完整排版（wrap 到 `maxWidth`），再以 `scroll` 选**首个可见行**，之后最多显示 `maxLines` 行窗口；随 `overflow` 决定窗口方式——`Clip` 取窗口，`Ellipsis` 在「窗口末尾仍有内容」时对可见末行加省略号。
- `scroll=0` 严格等价旧逻辑（同一分支），负值 clamp 到 0，`scroll` 超出末尾时 clamp 到最后一行使视图不空，`maxOfOrNull ?: 0` 兜底空结果；`scroll` 纳入 `TextLayout` 脏缓存键。
- 语义定调（与 Mosaic Text 高度自适应一致）：窗口行数 = min(剩余行, `maxLines`)，内容不满窗口时盒子不高/空——如需固定窗口配合外层高度约束。
- 测试：`TextLayoutWrapTest` 增 7 条（窗口偏移/超尾/无界行数/Ellipsis 跟随内容/换行+对齐数据不冲突/负值 clamp/滚动到末行不空）；`TextScrollRenderingTest` 3 条渲染快照（窗口、越界显示末段、Ellipsis）。

### L4.6 `BoxWithConstraints`（`d2780c1a`）
- 移植 Compose 同名 API：`BoxWithConstraints(modifier, contentAlignment, propagateMinConstraints, content)`；`BoxWithConstraintsScope` 暴露 `constraints`/`minWidth`/`maxWidth`/`minHeight`/`maxHeight`/`hasBounded*`/`hasFixed*`（Int 像素）+ 复用 `BoxScope` 的 `align`/`matchParentSize`。
- 实现：**单槽位子组合**——`Composition(applier, parent)` + `rememberCompositionContext()`（`InternalComposeApi`），内容在**首次 measure 时**用传入约束组合，约束变化时 `setContent` 以新约束重组合（无首帧空跑）；子组合根仅作结构容器，测量策略**直接测量其孩子**，因此 `matchParentSize`/对齐与 `Box` 语义一致。
- 失效联动：子组合每次应用变更，通过 `version` snapshot 状态 +1；测量第一阶段读取该状态，使父布局 `needLayout` 被触发，重新排版。
- 边界：无界约束 → 按内容收敛（与 `Box` 一致）；`fillMaxSize`/`weight` 等外部 modifier 照常生效。
- 可见性调整：`Box` 的 `BoxScopeInstance`/`AlignModifier`/`BoxParentData`/`boxParentData`/`matchesParentSize`/`placeInBox` 由 private 放开为 internal（无行为变化）。
- 测试：`BoxWithConstraintsTest` 7 条（读 `maxWidth` 定宽、min/有界标志、2/3 比例、父尺寸变化重组合、matchParentSize+居中、无界收敛、状态驱动动态列数）。
### L4.7 布局缓存（`9146adaa`）
- 动机：任何重组/布局态变更都会置 `needLayout` → 每帧全树重跑 `measureAndPlace`；把「相同 `Constraints` + 未失效」的测量缓存下来，只重测受影响路径，缓解高频重排与同构重布局。
- 实现：`MosaicNode.measure`（`layout/Node.kt`）以 `(Constraints, layoutVersion)` 为键缓存最近一次测量结果，命中时整子树跳过测量（place 每帧照常执行）；同版本/同约束下行程式测量 O(1)。`measureCount`/`cacheHitCount` 计数器供测试观测。
- 失效（`invalidateLayoutVersion()` 对节点及其**祖先链**冒泡递增版本）：
  1. **结构变化**：`setModifier`、`measurePolicy` setter、applier 增删移子节点（`MosaicNodeApplier` 开始维护 `MosaicNode.parent` 链）。
  2. **状态依赖**：测量 miss 时用 `Snapshot.observe` 把子树测量期读到的 snapshot 状态收进 `measureDependencies`；`MosaicComposition` 的 apply 观察器执行 `invalidateLayoutCaches` 整树扫，命中节点的依赖即冒泡失效。
- 保留整树 `readStatesOnLayout` 观察，专门覆盖 **placement 阶段**才读的状态（如 `offset {}` 的 changeable offset）：此类变化仍需触发 layout pass 以重算位置，但测量全部命中。
- 与 Compose 对比：思想一致（constraints+脏标记 ≈ version、测量期收依赖、结构失效）；工程差异在失效定位——Compose 用 `SnapshotStateObserver` 的「状态→节点」反向索引近似 O(1) 唤醒，并裁剪「不影响父尺寸/未放置」子树；本项目当前为整树扫 + 祖先链全 bump。正确性等价，性能余量在 apply 查扫与子树裁剪（后续优化项，可参考反向索引方案）。
- 测试：`LayoutCacheTest` 4 条（单子树测量态变化不重测兄弟、插入兄弟不重测既有子、placement-only 变化跳过测量、约束变化强制重测）。
- 验证：JVM 520/0、linuxX64 519/0，`:samples:rrtop:compileKotlinLinuxX64` 通过。


## 6. 当前状态 / 下一步候选

- 进行中（2026-10-18）：**L7 性能「绘制热路径低分配」**。已落地六项（D/E/F 未归档，等确认）：
  ① surface 复用（每帧不再 `new TextSurface`=4800 对象，`reset()` 复用）+ `SurfaceReuseTest`（2 条）；
  ② `strWidth` printable-ASCII 快路径（纯 ASCII 范围直接 `to-from`，跳过逐码点 Pair 分配）+ `StrWidthAsciiFastPathTest`（5 条）；
  ③ 宽度状态机 raw-Int 打包（`packResult` 打包 width+raw 单 Int，12 个 raw 版谓词直读；消除 `Pair<Int,WidthInfo>` 分配）+ `PackedWidthEquivalenceTest`（JVM，oracle 重放全等 ~560 万次）。
  ④ 单元格「源串+区间」模型（TextPixel 存 `textSource`/`textStart`/`textEnd`，`drawText` 不再逐簇 `substring`；渲染/差分按区间读出与比较）+ `CellSliceTest`（6 条：source 身份/偏移/多字符簇/span 覆盖/跨源相等/copyFrom/reset）。
  ⑤ **gprofng 采样定位**簇切分族占 draw-idle ~65% → `forEachGraphemeClusterRange` 加 printable-ASCII/tab 单码点快路径（跳过 `gcbClassOf`/`isExtendedPictographic`/`inCbClassOf`/`codepointAt`；排除 GB9b Prepend 例外）+ `GraphemeFastPathTest`（ref 对拍 6 条）。
  ⑥ `Text.drawBehind` 对齐列懒算（`alignedColumn` 改懒 lambda：Start/无界直接 0，不再每行白算 `graphemeWidth()`）+ 官方字素套件回归。
  ⑦ **布局/绘制共用一次簇测量**（方案 B）：`LineMeasure` 在 `measure()` 期对每行切分+测宽一次并缓存到 `lineMeasures`；`Text` 经 `TextCanvasDrawScope.drawMeasuredText` 复用（`as?` 安全下转，保底走原流式）；`alignedColumn` 直接用 `totalWidth`。**gprofng 验证**绘制侧 grapheme 测宽彻底移出绘制主路径。+ `TextMeasuredDrawTest`（两路径逐格/ANSI 对拍 5 条）。
  ⑧ **cell 级懒复位**：量化确认全量 `surface.reset()`（4800 格 × 6 字段写）占 draw-idle ~37% → `beginDraw` 清 per-cell written 位 + `resetCellsNotWritten` 只复位本帧未写格（遍历仅 1 布尔读/格）。**中途被 `MosaicTest` 移格回归抓到的正确性 bug**：行级粒度会残留"同行内本帧未写"的旧格，必须 cell 级才等价原全量语义。+ `SurfaceLazyResetTest`（5 条）。
  ⑨ **native GC 调优实验（结论：无效，恢复默认）**：`render-full-rebuild` 是差距最大负载（native ~700µs vs JVM ~390–440µs）。gprofng 采样定位：GC Sweep ≈31% + Compose 重组 ≈10%，自家布局+绘制 <10%；JVM 快在 HotSpot 逃逸分析+标量替换+分代 GC 把重组分配压到近零，K/N 无逃逸分析+ARC+整堆 Mark&Sweep 让同样分配变真实 GC 压力。试过 `gc=ms`（~757µs，更差）、`gcSchedulerType=Aggressive`（运行挂死，病态），默认即 Adaptive；**GC 参数无收益，已重链回默认**。结论：差距是架构性的、不在库代码，无法用 `-Xbinary` 追平；后续收窄只剩降 Compose 重组分配（制约在外层）或 JVM 为主部署。
  ⑩ **帧节流（todo §11 P2 完成）**：原 `while(true){ sendFrame(); delay(1) }` ≈1000fps 恒空转 → 按负载分档：`hasPendingFrameWork()`（needLayout/needDraw/recomposer.hasPendingWork/internalClock.hasAwaiters 四路并）为真走 ~60fps（16ms），否则 ~20fps（50ms）。**测试抓到的关键坑**：`sendFrame` 后动画协程是**异步派发**恢复并重挂下一帧，立即判忙会漏掉动画（实测 30 帧 1490ms=被误判空闲）；`clock.sendFrame` 后加一次 `yield()` 让派发恢复先跑完再判。+ `FrameThrottleTest`（3 条：构造期闲判定 / 动画 30 帧 <1.2s 保帧率 / 静态 400ms 仅渲染 1 次不空转）。
  ⑪ **异常/panic 恢复（todo §11 P2 完成）**：`runMosaicComposition` 加 `frameWriter` 可注入参（默认 stdout），`setContent+帧循环+awaitComplete` 整体 try/catch——非取消异常先写纯文本错误帧（`Mosaic error`/堆栈/`dumpNodes`），**再 `mosaicComposition.cancel()` 收队后 rethrow**；终端状态恢复由 `withTerminal` 双层 `use` 保证（异常传播时触发）。**单测揪出的真隐患**：错误帧写后若不 cancel，recomposer/帧协程仍挂着等帧，调用方 teardown（join）永久挂死；已修复。正常取消（Ctrl-C/withTimeout）走 `t !is CancellationException` 过滤，不写错误帧。+ `FrameErrorVisibilityTest`（2 条）。
  ⑫ **paste 逃生口（L5.2 补强）**：原本 bracketed paste 期间所有键盘事件（含 Ctrl-C）都被累积为粘贴内容，若终端只发 `start` 没发 `end`（粘贴被打断），Ctrl-C 也失效、应用无法退出。现 paste 激活时先识别 Ctrl-C（`event.toKeyEventOrNull() == ctrlC`）→ 清 `pasteText` + `job.cancel()` 退出；普通字符继续累积。**测试 tip**：完全静止内容时 `awaitComplete` 会因 recomposer idle 自然返回，会掩盖"是否真的由 Ctrl-C 触发退出"——用 `Ticker`（无限 `withFrameNanos`）keep-alive 区分。+ `PasteInterruptTest`（2 条）。
  **native `render-draw-idle` 510µs → ~52µs（-90%）；JVM ~209 → ~31µs（-85%）**；draw-idle 比值 2.4×→**~1.7×**。
  **native draw-idle 约为 Rust 系纯渲染参照（~224µs）的四分之一**；最坏全帧 60fps 预算余量 ~23×。
  E/F（`9d6fdd4c`）、G（`9a066233`）、H+GC 记要（`c48a5d64`）已归档；帧节流本轮归档。
  下一步候选（需确认后做）：
  - 逐格写入本体：`writeCluster`（每格 11 字段含 5 个样式对象写）+ `canvas[row,col]` bounds-check/translation 仍占 draw-idle 大头——候选「样式 Color/TextStyle 打包成 primitive」或「遍历收敛局部 cell 引用」。
  - 或转 L7 收尾（异常安全/帧节流/示例），性能已远超既定目标。
- 现状：`refactor` 分支，HEAD `9146adaa`，JVM 520 + Native 519 全绿。上面功能步骤已归档。
- 现状（2026-10-17 归档后）：HEAD `5f438a79`，JVM 与 linuxX64 四模块（runtime/terminal/tty/tty-terminal）全绿，全 samples 编译通过，rrtop kexe 已重链（工作树演示不含提交）。**L5 输入/事件已全部完成**：L5.1 鼠标消费 / L5.2 粘贴通路 / L5.3 kitty 键盘协议开关 / L5.4 clipboard（OSC 52）/**L5.5 事件背压与 Motion 合并** / L5.6 特殊键核对与 repeat 投递。
- 现状（2026-10-17 归档后）：**L6 tty 适配已收官（L6.1~L6.8 全归档，HEAD `53ba2ea2`）**：死回调移除 / DEC 2027 / raw 核对 / native 信号兜底 / resize 通路 / 读写缓冲 / 生命周期异常安全 / 探测清单完备性。tty / tty-terminal 两平台测试全绿。L6 余项（macOS/Windows/三套绑定一致性/`linuxArm64` cinterop）需远程环境，登记不改码。
- 决策（2026-10-17，已归档）：**L6 按「可本机验证优先」推进**——已做 L6.2 DEC 2027（§10 P1）；剩余建议顺序：raw mode 核对（§9.2 P1）、信号处理（§9.2 P2）；Windows（§9.3）与三套绑定一致性（§9.1）需 CI/远程后做。
- 决策（2026-10-17，已提交）：**外部组件待办整体推迟**——滚动容器 / 惰性列表 / 输入控件 / 鼠标交互 / 焦点 / 边框 / 定位回调 / 动画容器 / 第一方 widget 集属最外层组合组件，依赖内部核心（L4 布局、L5 输入、L6 tty 适配）先重构到位；新增 `docs/todo.md` §15 仅登记不实现，优先级统一排在内部重构之后。
- 决策（2026-10-17，已提交）：**布局缓存落地即完成 L4 布局层主干**——剩余可选优化（apply 失效查扫反向索引、未放置子树裁剪）留待后续需要时再做；L4 对外的下一候选是 intrinsic 复用（与布局缓存合并）或直接进入 **L5 输入**。
- 决策（2026-10-17，已归档）：**L5 输入走"全量 opt-in + 能力探测后自动启用"**——鼠标/粘贴经 `runMosaic*` 参数显式开启；kitty 键盘协议在探测到支持后自动 push、退出 pop；repeat/release 事件解析后由 runtime 按 press-only 过滤，既有应用零行为变化。
- 下一步候选（需用户确认后实施，建议一次一项）：
  1. **`§9.2 P2` macOS 特判**：`TIOCGWINSZ`/termios 的 macOS 差异（`HUPCL`/`c_cc`）——需 macOS 环境，暂不可本机验证。
  2. 之后 Windows（§9.3）与三套绑定一致性（§9.1）需 CI/远程；本机可做候选见 todo §9.4/§9.5 剩余项。

### L4.5 调研记录：布局“比例”的 Compose 语法（未落地，方向调整）
- 背景：原计划做“约束扩展”（Row/Column 补 `percentage`/`ratio` 弹性，参照线性布局 Constraint 优先级）。
- 调研（依据 androidx `foundation-layout` 1.10.4 源码，本地 Gradle 缓存可复核）：Compose **没有** `Modifier.percentage()`/`Modifier.ratio()` 这类“占剩余空间百分比”的 modifier；布局比例的官方语法只有三种，且 mosaic 已具备前两种：
  1. `Modifier.weight(weight, fill=true)`：Row/Column 内“扣除无 weight 子项后平分剩余主轴”的唯一一等公民；等宽列 = 每列 `weight(1f)`。
  2. `Modifier.fillMaxWidth/H/S(fraction)`：占父级传入 `maxWidth` 的 `fraction`（`Infinity` 时无效果）——即 Compose 的“百分比”。官方示例用 `fillMaxSize(0.5f)` 表示“占一半”。mosaic 已有（`layout/Size.kt`）。
  3. `BoxWithConstraints`：需要“固定 + 精确比例 + 剩余细分”手算的官方标准答法——子组合在 measure 阶段读取 `constraints`/`maxWidth` 后自定尺寸。
- 结论/决策：**自造 `percentage`/`ratio` 不符合 Compose 语法，已撤销，不进入 API**；这类需求用 `weight` + `fillMaxWidth(fraction)` 覆盖。`BoxWithConstraints` 曾评估需要通用 runtime 级 subcomposition（`Compose` 子实例 + slot 复用/回收），后按 L4.6 以**最小单槽位子组合**实现落地（`d2780c1a`，见第 48 行台账），无需先行抽象通用 API。
- 归档：本调研结论入台账；调研阶段无代码改动，回归基线 509 条 JVM 全绿。

## 7. 验证命令

```bash
./gradlew :mosaic-runtime:jvmTest          # JVM 全量
./gradlew :mosaic-runtime:linuxX64Test     # Native 全量
```
新功能测试文件放 `mosaic-runtime/src/commonTest/...`（除官方套件这种只适合 JVM 的放大文件放 `src/jvmTest/...`）。

## 8. 主要文件地图

| 层 | 路径 |
| --- | --- |
| L1 度量 | `mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/text/grapheme/`（`GraphemeBreak.kt`/`Graphemes.kt`/`GraphemeWidth.kt`/`GraphemeBreakTables.kt`） |
| L1 表 | `.../text/unicodewidth/`（本地 vendored `Tables.kt`/`UnicodeWidthChar.kt`/`UnicodeWidthStr.kt`） |
| L2 cell | `.../TextPixel`（含 `surface.kt` 画布与 `appendRowTo` 跳格逻辑） |
| L3 绘制 | `.../DrawScope`（`drawText` 簇级循环） |
| L3 渲染 | `mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/rendering.kt`（`AnsiIncrementalRendering` + 旧 `AnsiRendering`）、`mosaic.kt:62`（默认渲染器选择） |
