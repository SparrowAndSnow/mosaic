# Mosaic 重构进度台账（refactor 分支）

> 用途：记录「自下而上重构」的进度与已完成步骤的实现要点，保证会话上下文压缩后仍可恢复工作状态与下一步。
> 分支：`main`（`refactor` 同步） · 当前 HEAD：L7.46（代码 `e236784d`） · 创建：2026-10-17 · 更新：2026-10-23
> 关联：`docs/todo.md`（全项目功能 TODO，P0~P3）、`docs/benchmarks.md`（调研文档已在合并前移出仓库）
> 约定：本文件正文不出现任何第三方库/项目名（合并回 main 前的清理红线，见 §2）。

## 1. 工作流契约

- 每次只做**一项自下而上的功能**。
- 完成标准：功能实现 + **单元测试**（commonTest，必要时官方套件）+ JVM 与 Native 全绿。
- 完成后**提交英文信息**归档，并用中文向用户汇报，**等待确认后再进行下一项**。
- 用户确认后继续；本台账随每个归档步骤同步更新。

## 2. 红线与约束

- **禁止第三方库/项目名**出现在代码、注释、文档正文、提交信息、历史记录中。
  - 已中性化：`d1398ccd` 清除了内置宽度代码的归属注释。
  - 已清理（2026-10-21）：早期归档/调研提交（`cef427b8`~`b4ee1af4`）的提交信息已重写；`docs/todo.md` 与 `docs/refactor-progress.md` 正文已中性化；三份调研文档已移出仓库（存档于仓库外）；README/CHANGELOG 的归属引用已中性化。
  - 已处理（L7.46，`e236784d`）：`spotlessCheck` 全绿。实际范围是 10 处 compose lint（runtime 8 + animation 1 + samples/bench 1）+ 全仓库格式（62 文件，均为换行/缩进/尾随逗号/import 顺序等格式变更）；旧文档写「7 处」是低估，见 §5 L7.46。
  - 未处理：上游示例目录标识重命名（见 §6.2）。
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
| L7 生命周期/动画/测试/发布 | 异常安全、帧节流、测试矩阵、示例 | §11~§14 | 完成（L7.1~L7.46 已归档；余下 P3 可选） |

> 编号为推进顺序；某层内的小步骤继续用 Lx.y 编号（如 L3.2）。

## 4. 进度台账

| 层 | 步骤 | 内容 | 提交 | 测试 | 状态 |
| --- | --- | --- | --- | --- | --- |
| L1 | L1.1 | 字素簇分割（UAX #29）+ 官方套件 | `9c0e0724` | `GraphemeBreakOfficialTest`（官方套件 1000+ 条，jvmTest）+ `GraphemesTest` | ✅ 已归档 |
| L1 | L1.2 | 簇级列宽作为权威文本度量 | `cfa64a46` | `GraphemeWidthTest` + `TextLayoutWidthTest` | ✅ 已归档 |
| L2 | L2.1 | `TextPixel` → 簇字符串 + 显式列宽 | `a99e5100` | `TextPixelTest` | ✅ 已归档 |
| L3 | L3.1 | 文本按字素簇粒度绘制 | `4c368769` | `DrawTextClusterTest` | ✅ 已归档 |
| L3 | L3.2 | 行级增量 ANSI 渲染（默认渲染器） | `ef1fe8e7` | `AnsiIncrementalRenderingTest`（10 条） | ✅ 已归档 |
| L3 | L3.3 | 行内段差分（cell 级 diff + 簇边界切分） | `264f637d` | `AnsiIncrementalRenderingTest`（13 条） | ✅ 已归档 |
| L3 | L3.8 | 删除旧全量渲染器 `AnsiRendering`（去重） | `282e865b` | 无新增；删除 `AnsiRenderingTest`（410 行），覆盖已由 `AnsiIncrementalRenderingTest` 承接 | ✅ 已归档 |
| L4 | L4.1 | Text 簇级安全软换行 + 布局约束透传 | `18428264` | `TextLayoutWrapTest`（12 条）+ `TextWrapRenderingTest`（2 条） | ✅ 已归档 |
| L4 | L4.2 | Text `maxLines` / `overflow` 簇安全截断 | `68d19e72` | `TextLayoutWrapTest` 增 5 条 + `TextWrapRenderingTest` 增 2 条 | ✅ 已归档 |
| L4 | L4.3 | Text `textAlign`（Start/Center/End，复用 `Alignment.Horizontal`） | `94c37ff7` | `TextAlignRenderingTest`（7 条） | ✅ 已归档 |
| L4 | L4.4 | Text `scroll`（垂直查看窗口，主流终端 UI 的段落滚动参照） | `1e99eb40` | `TextLayoutWrapTest` 增 7 条 + `TextScrollRenderingTest`（3 条） | ✅ 已归档 |
| L4 | L4.6 | `BoxWithConstraints`（内容读取父级约束做精确比例布局；单槽位子组合） | `92297096` | `BoxWithConstraintsTest`（7 条） | ✅ 已归档 |
| L3 | L3.4 | cell 差分右缘防护 + 宽格 diff 回归（虚拟终端） | `55dfaae1` | `CellDiffRenderingTest`（15 条）+ `VirtualTerminal`（解析 ANSI→屏幕网格） | ✅ 已归档 |
| L3 | L3.5 | static 输出行级清理（替代一键 `clearDisplay`） | `736dd9a9` | `AnsiIncrementalRenderingTest`（16 条，改 4 + 新增 2） | ✅ 已归档 |
| L3 | L3.6 | 无同步渲染时的闪烁防护（光标包裹） | `e1e17349` | `AnsiIncrementalRenderingTest`（19 条，新增 3） | ✅ 已归档 |
| L3 | L3.7 | 输出批量/分片写，避免大字符串抖动 | `70b4067b` | `FrameWriterTest`（9 条） | ✅ 已归档 |
| L4 | L4.7 | 布局缓存（按 constraints+version 记忆测量，失效覆盖结构与测量期状态依赖） | `4af27661` | `LayoutCacheTest`（4 条，新增） | ✅ 已归档 |
| L5 | L5.1 | 鼠标消费：`Modifier.onMouseEvent` + 命中测试分发 + 终端 1006/1000/1003 opt-in | `bccb50d9` | `MouseEventTest`（8 条）+ `TtyTerminalMouseTest`（2 条） | ✅ 已归档 |
| L5 | L5.2 | 粘贴通路：`Modifier.onPaste` + bracketed paste 累积为单串分发（粘贴内容不触发按键） | `bccb50d9` | `PasteEventTest`（6 条）+ `TtyTerminalPasteTest`（2 条） | ✅ 已归档 |
| L5 | L5.3 | kitty 键盘协议开关：探测 `kittyKeyboard` 后 push `CSI>23u`、退出 pop 恢复 | `bccb50d9` | `TtyTerminalKittyKeyboardTest`（2 条） | ✅ 已归档 |
| L5 | L5.4 | clipboard（OSC 52）：`ClipboardEvent` 解析 + `Terminal` 读写命令 + `LocalClipboard`（suspend 读等待异步回复） | `9af01ce1` | `EventParserOscClipboardEventTest`（7 条）+ `TtyTerminalClipboardTest` + `ClipboardTest`（3 条） | ✅ 已归档 |
| L5 | L5.5 | 事件背压（channel `DROP_OLDEST`→`SUSPEND` + `send`）+ Motion/Drag 批内合并只留最新坐标 | `bf564374` | `TtyTerminalBackpressureTest`（1，新文件）+ `MouseEventTest` 增 3 | ✅ 已归档 |
| L5 | L5.6 | 特殊键/修饰核对：PUA 特殊键映射验证 + repeat 并入 press 投递、release 丢弃 + `text` 关联文本透传验证 | `56a55c3e` | `CompatTest`（8 条，新文件） | ✅ 已归档 |
| L6 | L6.1 | 移除 `Tty.Callback.onKey/onMouse` 死回调（三套绑定同步，事件走共享字节流通道） | `8c489971` | `TtyTest` 清理 + 三套绑定编译/测试全绿 | ✅ 已归档 |
| L6 | L6.2 | DEC 2027 探测 → `Capabilities.graphemeClustering`；支持时自动启用（退出恢复） | `b84c0dd9` | `TtyTerminalDec2027Test`（5）+ `EventParser` 2027 例 + apiDump | ✅ 已归档 |
| L6 | L6.3 | POSIX raw mode 核对（审计）：与 `cfmakeraw(3)` 逐位一致，零代码差异 | —（审计无代码改动） | —（审计；基线全绿） | ✅ 已归档 |
| L6 | L6.4 | native 信号兜底：`SIGSEGV/SIGILL/SIGPIPE` C 级 async-safe 恢复 termios + 保留终止语义重发；随 raw 生命周期装/卸 | `66c4f2d6` | `SignalGuardTest`（6，fork 真实信号）+ tty/tty-terminal 两平台全绿 + apiCheck 修复 | ✅ 已归档 |
| L6 | L6.5 | resize 通路核对 + 加固：SIGWINCH 与 in-band 互斥（构造保证）；in-band 无初始报告时 `currentSize()` 播种 | `59284788` | `TtyTerminalResizeTest` 增 2（互斥 + 播种），双平台 10 例全过 | ✅ 已归档 |
| L6 | L6.6 | 读写缓冲核对（审计）+ 加固：控制序列写循环已正确；`reset()` 三平台补 `close()` 后指针守卫（closed 后幂等空操作） | `164d1c70` | `TestTtyTest` 增 2（close/reset-after-close 幂等），tty 两平台全绿 | ✅ 已归档 |
| L6 | L6.7 | 生命周期异常安全核对 + 加固：teardown 从「依赖 Unconfined 内联续传的异步 hook」改为「close() 同步执行 + 信号/shutdown hook 复用」幂等闭环 | `d6926d77` | `TtyTerminalLifecycleTest`（新，3 条）+ 既有 teardown 输出用例全绿 + runtime 编译过 | ✅ 已归档 |
| L6 | L6.8 | 探测清单完备性核对（审计）+ 清单回归测试：13 项探测逐一对账、theme 996/997 确认非笔误、「逐项重查」评估驳回 | `7ba3a7ef` | `TtyTerminalProbeInventoryTest`（新，1 条）；tty-terminal 双平台 254 例全绿 | ✅ 已归档 |
| L7 | L7.1 | 双平台微基准（`samples/bench`，commonMain 单一驱动）：render（单行 delta/全量重建/layout-pass）+ EventParser；JVM+native 对照；验证 native 达主流实时标准（见 §5 结论）；`cjk` 分支补充 CJK/emoji 内容非 ASCII 路径测量 | `bb36834b` | JVM `:samples:bench:runJvm` + native kexe 可运行；无传统单测（基准类负载，见 `docs/benchmarks.md`）；bench 驱动随 L7.2 演进 | ✅ 已确认 |
| L7 | L7.2 | 绘制热路径低分配（surface 复用 / ASCII 测宽 / raw-Int 打包 / 源串-区间 cell / 簇切分 ASCII 快路径 / 对齐懒算 / 共用簇测量 / cell 级懒复位）+ native GC 调优实验（无效，恢复默认） | `9c2761ce` | `SurfaceReuseTest`/`StrWidthAsciiFastPathTest`/`PackedWidthEquivalenceTest`/`CellSliceTest`/`GraphemeFastPathTest`/`TextMeasuredDrawTest`/`SurfaceLazyResetTest` 等；双平台全绿 | ✅ 已归档 |
| L7 | L7.3 | 帧节流：`runMosaicComposition` 帧循环按负载自适应（`hasPendingFrameWork` 判定，活跃 ~60fps / 空闲 ~20fps），替代 ~1000fps 恒空转 | `13f3c7de` | `FrameThrottleTest`（3 条：闲判定 / 动画保帧率 / 静态不空转重绘）；双平台全绿 | ✅ 已归档 |
| L7 | L7.4 | 异常/panic 恢复：`runMosaicComposition` 捕获非取消异常→`frameWriter` 写纯文本错误帧（`Mosaic error`+堆栈+`NODES`）→`cancel()` 收队→rethrow；终端恢复由 `withTerminal` use 保证；`runMosaicComposition` 增可注入 `frameWriter` | `92e4d04a` | `FrameErrorVisibilityTest`（2 条：致命错误帧+传播 / 正常取消无错误帧）；双平台全绿 | ✅ 已归档 |
| L7 | L7.5 | paste 逃生口：未终止 bracketed paste（只收到 start、未收到 end）中 Ctrl-C 仍退出（清空 paste 累积 → `job.cancel()`），普通粘贴字符不触发退出；顺带消除 `pasteText!!` | `0d4f3d94` | `PasteInterruptTest`（2 条：Ctrl-C 中断未终止 paste / 普通字符不退出；`Ticker` keep-alive 区分自然 idle 返回）；双平台全绿 | ✅ 已归档 |
| L7 | L7.6 | awaitComplete 语义明确化：run-until-idle 收尾（效果全结束 + 欠下的 layout/draw 显式驱动一帧并 join 后 close）+ `cancel()` 必释放等待者；`setContent` 二次调用替换内容并取消旧 effect；TODO 注释替换为结论 | `f679993c` | `LifecycleSemanticsTest`（3 条：无限 effect 保活至 cancel 释放 / 静态自然返回 / 二次 setContent 取消旧 effect）；双平台全绿 | ✅ 已归档 |
| L7 | L7.7 | `TextLayoutWidthTest`「测量与绘制一致」：按测量 `LineMeasure` 逐列对拍绘制结果（簇头列文本 / 宽簇 placeholder 续列 / 右沿 == totalWidth / `layout.width` == 最宽行）；覆盖 ascii/cjk/ZWJ/flags/keycap/VS16/内嵌空格/softWrap/Clip/Ellipsis/scroll/Annotated | `01d5d04d` | `TextLayoutWidthTest` 14 条（原 7 + 新 7）；双平台全绿 | ✅ 已归档 |
| L7 | L7.8 | 非交互能力提示：`withTerminal` 落到 `NonInteractiveTerminal`（`Ignore` 绑定失败 / `AssumeAndIgnore`）时先向 stderr 打 `NonInteractiveNotice`（JVM `System.err` / native posix `STDERR_FILENO`），能力降级不再静默 | `b60dd5b8` | `NonInteractiveTest`（commonTest 2 条：AssumeAndIgnore 分派 + 惰性终端能力清单）+ `NonInteractiveNoticeTest`（jvmTest 1 条：stderr 捕获）；双平台全绿 | ✅ 已归档 |
| L7 | L7.9 | 快照体系扩展：`mosaic-testing` 增加 `CapabilityMatrix.defaults`（ansiLevel × kittyUnderline × synchronizedOutput × graphemeClustering 的 7 组代表性组合）+ `runMosaicTestForEach`（每组新 mosaic 跑同一断言）；`TestMosaic` 补齐 `sendFocusEvent`（→ 生命周期 ON_PAUSE/ON_RESUME） | `a9467f04` | `CapabilityMatrixTest`（布局跨矩阵稳定，commonTest）+ `FocusEventTest`（焦点驱动 RESUMED/STARTED，commonTest）；mosaic-testing + mosaic-runtime 双平台全绿 | ✅ 已归档 |
| L7 | L7.10 | 真实终端渲染回归：全量重绘帧末尾不再带尾随 `\r\n`（真实终端末行收到换行=整屏上滚一行 → 后续所有绝对坐标 diff 错位 → 双 tabBar / 上一页残留；内存 `VirtualTerminal` 不模拟滚动故测不出）→ 行间 CRLF、末行不终止；`VirtualTerminal` 补 `CSI F` 回列 0；`BoxWithConstraints` 子组合根 0×0 容器被绘制遍历跳过（Layout 页第 4 节整段空白）→ 补一次真实 measure（结果丢弃，matchParentSize/align 语义不变）；顺带归档早期 paint 越界裁剪（越出屏幕改为裁剪而非抛错） | `27d038a0` | `FullRedrawNoScrollTest`（2：帧永不带行终止 / Unicode→Input 切页无残留且 tabBar 单行）+ `BoxWithConstraintsRenderingTest`（1/2 1/3 余量渲染）+ `UnicodeScreenReproTest` + `OffsetTest`（越界改裁剪）+ `TextMeasuredDrawTest` +2；runtime jvmTest 607 全绿 | ✅ 已归档 |
| L7 | L7.11 | showcase 演示样本（仅公共 API，`samples/showcase`）：Unicode/Text/Layout/Input/Animation 五页 + 页头/可点击 tab 外壳；Text 页新增第 5 节字符串截断示例（`maxLines=2` + `softWrap` 定宽，`Clip` 硬切 vs `Ellipsis` 省略号、两种宽度对比，中英混排不拆字素簇） | `7aa7f3ec` | `ShowcaseTest` 3 条（Unicode 宽字/emoji 内容 / Text 对齐+滚动 / 截断省略号渲染）；showcase jvmTest 全绿 | ✅ 已归档 |
| L7 | L7.12 | ISO 控制字符净化为「非打印」：C0（0x00-0x1F）/DEL（0x7F）/C1（0x80-0x9F）一律 0 列、不绘图、不进 ANSI 流（原为 1 列 + 原样发出，真实终端把 CR/BS/TAB/ESC 当控制序列打乱整帧）；`charWidth`/`graphemeClusterWidth` 返回 0（测/绘唯一口径），ASCII 快路径排除 tab，簇写入器跳过 0 宽簇；`\n` 仍是行分隔符 | `ab05f570` | `IsoControlRenderingTest`（7：TAB/CR/BS/ESC/C1-NEL 净化为纯内容 / CRLF 与 `\n` 分行保持 / 纯控制行空）+ `GraphemeWidthTest` 增 controlCharactersOccupyZeroColumns（1 条）；runtime jvmTest 615 / linuxX64Test 613 全绿 | ✅ 已归档 |
| L7 | L7.13 | `Modifier.onGloballyPositioned { position, size -> }`（放置回调）：节点放置完成后报告根坐标（屏幕）左上角 + 尺寸——父级不参与、透传 LayoutModifier 不占空间不改测量，仅位置/尺寸变化时触发（未移动不以帧频重复回调） | `118c1a2e` | `OnGloballyPositionedTest`（5：嵌套 padding 报 (3,5)+尺寸 / Row 兄弟累加报 (7,0) / offset 移动二次触发 / 宽度变化二次触发 / 20 动画帧未移动仅报 1 次）；runtime jvmTest 620 / linuxX64Test 618 全绿 | ✅ 已归档 |
| L7 | L7.14 | 能力探测 1s 超时降级可观测：无法响应终端不再静默——`withTimeoutOrNull` 捕获结果，超时即向 stderr 打英文提示（含卡住阶段 DA1/能力/默认查询），开 `MOSAIC_TTY_TERMINAL_DEBUG=true` 追加已确认能力清单；指标文本抽成 internal 纯函数；新增 expect/actual `stderrPrint`（JVM `System.err` flush / native posix `STDERR_FILENO`，与 L7.8 非交互提示同构） | `7b5aee1f` | `TtyTerminalBootstrapTimeoutNoticeTest`（4：默认文案/调试清单/空清单/未知阶段）+ `TtyTerminalTimeoutNoticeJvmTest`（1：真实无回复终端 1s 超时 + stderr 重定向断言）；tty-terminal jvmTest 259 / linuxX64Test 258 全绿，apiCheck 通过 | ✅ 已归档 |
| L7 | L7.15 | 通用滚动容器（最大布局缺口）：`ScrollState`（`value`/`maxValue` 快照态、`scrollBy`/`scrollTo` clamp、`rememberScrollState`）、`verticalScroll`/`horizontalScroll`（滚动轴无界测量 + 视口=约束 + 内容位移取负，clip 在外 offset 在内）、`clipToBounds()` + 画布裁剪矩形（逐层与外层 clip 求交）；顺带修复三处绘制问题（簇被裁后列游标不前进→整行空白 / `maxValue` 快照可观察 / 格子帧内首写重置→跨页背景残留）；滚轮内建（一格 3 行，方向镜像方向键）；showcase 新增第 6 页演示三类容器（纵向列表/横向长行/混合色块）+ 实时 `value/maxValue` + 滚动条 | `cc62a232` | `ScrollStateTest`（4）+ `ScrollRenderingTest`（6 含滚轮）+ `ClipToBoundsTest`（4）+ `CellStyleResetTest`（1）；runtime jvmTest 635 / linuxX64Test 633，showcase jvm/native 各 4，apiCheck 通过 | ✅ 已归档 |
| L7 | L7.16 | 惰性列表容器：`LazyColumn`（仅组合可视窗口子项，代价 O(视口) 与总数无关）+ `LazyListScope` DSL（`items(count)`/`items(List)`，多段共享全局 index）+ 窗口算法（firstVisible/lastExclusive 含 lookahead）+ 固定 `itemHeight` 槽位测量定位；复用 L7.15 裁剪/滚轮；`KeyEvent` 增 `repeat` 标志 + Page 键同键 600ms 时间窗防长按堆页（笔记本 Fn+方向键无独立 PgUp/PgDn）；showcase 新增第 7 页（500 行演示，Input 页回显补 `(repeat)` 标签） | `f4b6f9a1` | `LazyColumnTest`（7）+ `CompatTest` 增 repeat 断言 + `ShowcaseTest` 增 Lazy 2 条；runtime jvmTest 642 / linuxX64Test 640，showcase jvm/native 各 6，apiCheck 通过 | ✅ 已归档 |
| L7 | L7.16.1 | `<key>~` 键事件补解析调制键与事件类型（根因修复）：`CSI <key>;<modifiers>[:<event-type>]~` 此前只取数字码，`:3~`（松开）被当成第二次按下——`Fn+PageDown` 一次按压翻两页；按游标键分支解析 payload，松开标记为 release、被 runtime（`compat.kt`）丢弃。顺带撤销 L7.16 的 Page 键 600ms 时间窗「敷衍式」兜底——改 Page 键只响应全新 press、吞 keyboard-protocol repeat（长按只翻一页）；`bin/` 构建产物入 gitignore | `e9ca8239` | `EventParserCsiTildeKeyboardEventTest`（9：PgUp/PgDn/Insert/Delete/Home/End 纯形 + `6;129:1~` press / `:3~` release / `:2~` repeat / `11;2~` Shift+F1）+ `ShowcaseTest` 改 press/repeat/release/反向 4 事件；tty-terminal jvm/native 各 +9，runtime 642/640，showcase 6/6，apiCheck 通过 | ✅ 已归档 |
| L7 | L7.17 | 单行可编辑文本输入框：`BasicTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, color, background, onDone)`——Compose 同款受控语义；光标=终端原生反显块（SGR 7，字符上反显该字素、末尾反显空格）；编辑按 UAX#29 字素移动/删除（CJK 与组合序列），IME/粘贴插光标处，Ctrl 组合不吞；`enabled=false` 惰性无光标、`readOnly` 只导航不编辑；**内部文本镜像**解决同帧连击丢字（编辑基于镜像而非旧闭包 value）；编辑器抽成纯类可单测 | `7d07e645` | `BasicTextFieldTest`（18：引擎 9 条——插入/退格/删除/导航含宽字符列宽与组合簇边界；组合层 9 条——渲染+反显光标/disabled/typing/ctrl 不插/退格/中插/粘贴，走真实事件通路）；runtime jvm 660 / native 658，showcase 各 7，apiCheck（.api/.klib.api）通过 | ✅ 已归档 |

| L7 | L7.18 | 鼠标交互原语：`Modifier.hoverable(onHoverChanged)`（enter/leave）+ `Modifier.clickable(enabled, onClickLabel, onClick)`（主键 Press→Release 同界内触发，越界/换节点取消）；每元素 state 用 `.composed`+`remember` 跨重组稳定；根节点统一 PointerState 预检——收集命中路径上所有 pointer 输入 → hover enter/exit 会话级对账 + click 按下捕获/释放判定（取叶子优先，释放位置回查原节点当前边界与树上存活）→ 再走原有命中分发。showcase TabBar 改由 clickable/hoverable 驱动（顺带修复 hover 粘滞） | 待提交 | `PointerInputTest`（12 条：hover enter/exit/跨兄弟/disabled；click 同界触发/越界取消/界外按下/disabled/叠放兄弟取顶/嵌套取内/wheel 忽略/hover+click 共存/拖拽后仍在界内点击）；runtime jvm 672 / native 670，showcase jvm/native 各 8，apiCheck 通过 | ⏳ 待确认 |

| L7 | L7.19 | 焦点 / 键盘导航原语：`Modifier.focusable(enabled)`（Tab/Shift+Tab 环形遍历 + 主键点击聚焦）+ `Modifier.onFocusChanged { FocusState }`（`hasFocus`=自或后代、`isFocused`=自身、`isCaptured` 恒 false）+ `FocusRequester`（`Modifier.focusRequester` 程序化 requestFocus，配 focusable 才会生效）；每节点单个 `FocusTarget`（跨重组复用，观察者每次重建拾取新 lambda），组合根 `FocusManager` 负责焦点事务——释放旧分支（自身+非共享祖先 down）→点亮新分支（至首个已亮祖先停），每节点**一次原子通知**；Tab 仅在存在 focusable 时消耗（否则保持旧行为）、Shift+Tab 反向、`repeat` 吞掉不连滚；BasicTextField 接入：仅聚焦字段响应输入/画光标，多字段同屏；祖先 `onFocusChanged` 观察后代（hasFocus=true/isFocused=false）。showcase Field 页双字段 + 聚焦状态 + 自动聚焦首字段 | `e7806082` | `FocusTest`（11 条：Tab 前进/环绕、Shift+Tab 反向、单 focusable 消耗、无 focusable 不拦截、点击聚焦、点击非 focusable 不动焦点、disabled 跳过、FocusRequester 生效/对非 focusable 无效、祖先观察、Ctrl+Tab 不遍历）；`BasicTextFieldTest` 改聚焦前置；runtime jvm 683 / native 681，showcase 各 8，apiCheck 通过 | ✅ 已归档 |
| L7 | L7.20 | 边框 / 分隔原语：`Modifier.border(width=1, color=Unspecified)`（`LayoutModifier` 按 width 四边内凹 + `DrawModifier` 画 `┌┐└┘` 角 + `─│` 边条——width>1 时边条跨整行/列实心，width=0 不画、尺寸不变；color 直通前景）/ `HorizontalDivider`（thickness=1、color、startIndent/endIndent，规则占满约束主向、次向 thickness）/ `VerticalDivider`（对称，占满高度、宽度 thickness）；showcase 第 9 页 Border 演示边框变体/内边距/嵌套/横竖分隔线 | `392b7efa` | `BorderTest`（16 条：border 内容内凹+环线、单字符、border0 不画且尺寸不变、border2 边条实心、fillMaxWidth 整行、与 padding 组合、彩色 ANSI 前景、Unspecified 无前景、边框内 clickable 回归、横/竖分隔线含厚度与缩进、负参数 require）；runtime jvm 699 / native 697，showcase 各 9，apiCheck 通过 | ✅ 已归档 |

| L7 | L7.21 | 动画容器（`mosaic-animation`）：`Crossfade`（水平推进语义——容器叠层裁剪，旧页向左滑出、新页从右滑入，`Transition.Crossfade` 用 `DeferredAnimation` 偏移驱动，`contentKey` 去重）+ `AnimatedVisibility`（`EnterTransition`/`ExitTransition` sealed 类型 + `EnterExitState`(PreEnter/Visible/PostExit) + `EnterTransition.None`/`+` 组合；expand/shrink 以 `IntSize` 驱动 **布局尺寸含兄弟挤压**、slide 以 `IntOffset` 驱动位移；fade/scale 因终端无 alpha/graphicsLayer 省略；退出动画结束前内容保留、结束后才从树中移除——`produceState`+`snapshotFlow{exitFinished}` 观察；`MutableTransitionState` 变体入场即动画）；showcase Animation 页增补 Crossfade 页面推进 + AnimatedVisibility 展开/收起演示 | `a35bfe01` | `CrossfadeTest`（5：初始目标/中途混合帧/切换后只剩目标/多帧动画/切回同 target 单层）+ `AnimatedVisibilityTest`（6：隐藏不渲染/即刻显示/入场多帧并最终全显/退出期间保留后移除/退出多帧/MutableTransitionState 入场动画）；mosaic-animation jvmTest / linuxX64Test 13 各全绿，showcase jvm/native 各 +1（动画冒烟），apiCheck 通过，kexe 已重链 | ✅ 已归档 |

| L7 | L7.22 | `TtyTerminal` 关停竞态修复：`close()` 在 `teardown()+closeJob.cancel()` 后**阻塞等待读取协程 `readerJob` 完全退出**（`while (!isCompleted) { runCatching{ interruptRead() }; delay(1) }`，1s 超时后 `cancel()` + 最后一次 interruptRead 兜底）才返回，保证调用方随后的 `tty.close()` 释放 fd 时读取线程已彻底离开描述符；杜绝「fd 释放当口 read 仍在阻塞」→ EBADF(9) `IOException`（读取循环无捕获 → 未捕获 → SIGABRT）或 fd 号复用后 read 永久挂起；读取循环对 `IOException` 按输入终结处理；interrupt job finally 的 `interruptRead` 加 `runCatching` | `21291c1c` | `shutdownNeverRacesFdFree`（200 次精确复刻退出时序，修复前必现崩溃/挂起、修复后稳定 ~10s）；mosaic-tty / tty-terminal linuxX64Test 与 jvmTest 全绿，mosaic-runtime native 编译通过，showcase kexe 重链后 PTY 多次 Ctrl-C 退出干净 | ✅ 已归档 |

| L7 | L7.23 | 首帧全屏清屏接管：`ansi.kt` `clearDisplay` 由 `CSI J`（ED 0，只清光标下方）改为 `CSI 2 J`（ED 2 全清）；`rendering.kt` 首帧绘制前追加 `clearDisplay + CSI H`（清屏归位再画首帧），后续帧仍走 cell 级差分 | `5aaadb12` | 6 个渲染测试首帧断言补 `|${clearDisplay}${CSI}H` 前缀（AnsiIncremental 19 / TextScroll 3 / TextWrap 4 / TextAlign 7 / SurfaceReuse 1 / IsoControl 1，`visible()` helper 剥离前缀）；mosaic-runtime jvmTest（4s 全绿）与 linuxX64Test 全绿；counter kexe 重链后首帧带 `[2J[H`、退出后 icanon/echo 恢复 | ✅ 已归档 |
| L7 | L7.24 | `weight(fill)` 槽位记账（`ui/RowColumnMeasurementHelper.kt`）：加权重排时按子项**参与行/列尺寸与排布的尺寸**记账与放置——加权且 `fill=true` 用 granted slot，`fill=false` 与非加权用实测内容尺寸；`mainAxisPositions` 改吃槽位尺寸 → 兄弟背靠背摆放、Row/Column 总尺寸=槽位总和（`fillMaxWidth` 子项下撑满）；子节点自身宽度保持内容宽、不拉伸标签。替代被否决的框架级方案（按约束收敛报告宽会破坏溢出/越界/intrinsic 回归），定点只改 Row/Column 记账 | `3edfd316` | `WeightFillSemanticsTest`（5 条：无 weight 按内容包裹 / weight+fillMaxWidth 等分槽位行宽 100、子 x=0/34/67、子宽保持内容、间隔>内容宽 / fill=false 保持内容宽、按内容堆 / 固定 6+权重 1:2 行宽 80、x=0/6/31 / Column 等分 y=0/30）；mosaic-runtime jvmTest 全绿 | ✅ 已归档 |
| L7 | L7.25 | intrinsic 复用（`layout/Node.kt`）：`MosaicNode` 增单槽 intrinsic memo——以（kind, height/width, `layoutVersion`）为键缓存最近一次 `min/maxIntrinsicWidth/Height`，命中直接返回不再重走子树；重算时经 `Snapshot.observe` 把读取的 snapshot 状态记入该节点 `measureDependencies`（与 measure 同一失效口径），并在节点**实际 measure**（缓存未命中）时丢弃 memo（防 measure 清空依赖后 memo 陈旧）；`intrinsicRecomputeCount`/`intrinsicCacheHitCount` 计数 | `d51f3ae4` | `IntrinsicCacheTest`（3 条：同参 3 连查只重算 1 次 / 不同参数各自重算 / 状态变更后 memo 不返回陈旧值，仅 intrinsic 读状态、measure 不读以隔离验证依赖记录）；既有 `IntrinsicTest` 回归；mosaic-runtime jvmTest 与 linuxX64Test 全绿 | ✅ 已归档 |
| L7 | L7.26 | ANSI 16/256 颜色约简核对（`ansi.kt` / `AnsiColorReductionTest.kt`）：`toAnsi16Code`（最高通道近似 100% → 亮色 +60；每通道过半翻转基索引位）/ `toAnsi256Code`（等通道走 232..255 灰阶、两端钳到 16/231；彩色走 6x6x6 cube，每通道 `/255*5` 取整）与参照行为逐点核对一致；清理归属 URL 注释改中性说明 | `27849ee1` | `AnsiColorReductionTest`（4 条，金表 ~60 向量：16 色黑白/亮暗/半强度翻转（127/128）/最高通道 30% 特判；256 灰阶端点 8/247/248 与内部采样；cube 纯色/二级色/取整临界（76/77、127/128）/跨通道组合）；mosaic-runtime jvmTest 与 linuxX64Test 全绿 | ✅ 已归档 |
| L7 | L7.27 | 端到端 golden（`EndToEndGoldenTest.kt`）：组合→测量→绘制→ANSI 帧经内存 `VirtualTerminal` 回放，断言**屏幕最终状态**（用户所见）与是否残留半格 | `0754a318` | `EndToEndGoldenTest`（4 条：宽字符混排 / weight 槽位列（80 列 1:2:1 槽 20/40/20 @ x=0/20/60）/ `maxLines` 裁剪窗口 / 两帧增量 diff 后屏幕）；mosaic-runtime jvmTest 与 linuxX64Test 全绿 | ✅ 已归档 |
| L7 | L7.28 | README/S 能力矩阵（`README.md` / `CHANGELOG.md`）：README 增「Capabilities & Known limitations」——文本与 Unicode（字素簇、DEC 2027 后备）/渲染（cell 级增量、同步输出、快照）/布局（约束族、weight、fill 分数、BoxWithConstraints、布局缓存、Text 重载）/交互（键盘+kitty 协议、鼠标、paste、OSC 52、焦点）/动画（值动画+容器）；限制：非 UTF-8 locale、须真 TTY、Windows/macOS 靠 CI、**组合根无界测量→fillMaxWidth 需显式容器**。CHANGELOG [Unreleased] 补齐重构的用户可见能力。措辞中性零第三方名 | `6070c929` | 仅文档（无代码/测试改动）；README/CHANGELOG 均无第三方库名（rg 校验） | ✅ 已归档 |
| L7 | L7.29 | 值动画/转换器族核对 + 单元测试（`mosaic-animation`）：`animate*AsState` 家族齐全（Float/Int/IntOffset/IntSize/Color，终端无 Dp/Size 需求）；`Color.VectorConverter` 走 **Oklab** 感知空间插值（黑→白向量中点 Oklab L=0.5 → sRGB≈99，对比朴素 sRGB 128 / 线性 RGB 188）——比 todo 原设想「线性 RGB」更优的动画颜色空间，维持现状；补 10 条单测覆盖 5 转换器往返 + IntSize 负值钳制 + color 端点精确收敛/finishedListener 恰一次/中途重定向 | `10bd9d22` | `VectorConvertersTest`（6）+ `AnimateColorAsStateTest`（4）；mosaic-animation jvmTest 与 linuxX64Test 各 23 全绿 + apiCheck | ✅ 已归档 |
| L7 | L7.30 | 帧时钟集成核对（审计 + 补测）：动画经 `MosaicComposition` 内部 `BroadcastFrameClock` 驱动（`startFrameListener` 逐外帧 `internalClock.sendFrame(nanos)`，时间=真实单调钟 `nanoTime()`）；`delay(1)` ~1000fps 恒发已被 L7.3 自适应两级节流取代（动画忙 `hasAwaiters`→60fps / 空闲 20fps，空闲帧不重绘）；低电量=空闲 20fps 有界轮询（输入延迟 ≤50ms 取舍，纯唤醒不渲染）；覆盖缺口「空闲期启动动画 → 切回 60fps」补 1 条测试 | `fb5d2b2b` | `FrameThrottleTest` 增 `animationStartingAfterIdleKeepsHighFrameRate`（空闲 200ms 后起 30 帧 <1.4s，与 20fps 卡死 ~1.55s 区分）；mosaic-runtime jvmTest 与 linuxX64Test 全绿 | ✅ 已归档 |
| L7 | L7.31 | 硬件光标 API（`ui/Cursor.kt` / `rendering.kt` / `ansi.kt`）：`Cursor(modifier, shape, blink)` 零尺寸标记按布局位置（`onGloballyPositioned` 根坐标）锚定真实终端光标，`CursorShape`（Block/Underline/Bar × blink）映射 DECSCUSR；`MosaicComposition.draw()` 经 `TextSurface` 携带请求（不改 `Mosaic`/`Rendering` 公共接口）；渲染器帧尾按需 CUP + 形状 + show，越界不显示、宽簇续列吸附簇头、请求消失即 hide + 复位形状、进程退出复位默认形状；仅 `cursorVisibility` 能力下生效 | `b96a8c64` | `CursorRenderingTest`（10 条：布局位置 / 祖先 padding / 形状编码 / 宽簇吸附 / 移除隐藏并复位 / 越界不显示 / 跨帧移动 / 同步输出 / 无光标保持旧 hide-show 框架 / 退出复位）；JVM 726 / linuxX64 724 全绿；apiDump 更新 JVM + klib | ✅ 已归档 |
| L7 | L7.32 | TTY 三绑定一致性（`mosaic-tty`）：新增共享参数校验 `TtyArguments.checkReadWriteArguments` + `TtyContractTest`；JNI/FFM/native 统一「越界 → `IndexOutOfBoundsException`」「关闭后除 `reset`/`close` 外 → `IllegalStateException`」、offset/count、超时/中断、字节往返；JNI 回调初始化失败清理全局引用、三绑定回调「先摘除后释放」、FFM 关闭先释放原生再关回调 arena、native `reset` 错误传播、JNI/FFM 进入 raw mode 补装 POSIX 信号兜底（与 native 对齐）；修正 `TestTtyTest` stderr 假阳性 | `65631043` | `TtyContractTest`（11 条）+ 既有套件；mosaic-tty JVM 116 / linuxX64 122、FFM 分发（JDK 25）116 全绿；tty-terminal JVM 269 / linuxX64 268 回归；`:mosaic-tty:spotlessCheck` 通过 | ✅ 已归档 |
| L7 | L7.33 | `onGloballyPositioned` 内容未放置修复（`layout/OnGloballyPositioned.kt`）：measure 自建的 placement 块只回报坐标、未 `placeable.place(0,0)`，子内容与整棵子树停在 canvas 原点 → LazyColumn 各行/带该 modifier 的内容全部画到左上角重叠；补放置调用（坐标语义不变） | `b8cdc33e` | `OnGloballyPositionedTest` 7 条（原 5 + `contentRemainsAtItsPlacedPosition`（修复前 `X---` → 修复后 `----X`）+ `lazyColumnRowsStayOnTheirRows`）；mosaic-runtime JVM 728 / linuxX64 726 全绿 | ✅ 已归档 |
| L7 | L7.34 | 备用屏生命周期：`runMosaic` / `runMosaicBlocking` / `runMosaicMain` 增 `alternateScreen: Boolean = false`（默认保持内联行为），经 `withTerminal` → `Tty.asTerminalIn` 透传；真实绑定 TTY 且开启时在 `enableRawMode()` 后、能力探测前写 `CSI ?1049h`，幂等 teardown 先恢复所有被 toggle 的模式再写 `CSI ?1049l`（`reset()` 仅恢复 termios/信号、不写终端，置于 leave 后并注释）；`ansi.kt` 集中序列常量 | `43c85bb1` | `TtyTerminalAlternateScreenTest`（3：进入早于首次探测 + 退出 / 恢复模式后 leave 且为最后输出 / 关闭时不出现 1049）+ `TtyTerminalLifecycleTest` 增 2（close 幂等只 leave 一次 / 作用域取消经 finalization hook 也 leave）；tty-terminal JVM 274 / linuxX64 273，runtime JVM 728，apiDump/apiCheck JVM+klib | ✅ 已归档 |
| L7 | L7.35 | 惯性滚动状态（`layout/MomentumScrollState.kt`）：只提供物理状态与 `advance(dt, maxPosition)`，不启动协程/帧循环；`impulse(rows)` 把滚轮格数转成 `rows × friction` 的初速度，默认参数一格 3 行约走 3 行；应用在自己的 frame loop 调 `advance` 并把 `position` 映射到布局。补齐 17 条单测，并修 `advance(dt=0)` 返回 false 但 velocity 未清的语义 bug（现返回是否仍在运动） | `8532ef1e` | `MomentumScrollStateTest`（17：默认 3 行/上限/累积/边界/越界 clamp/非法参数/快照状态/应用帧循环映射等）；mosaic-runtime JVM 745 / linuxX64 743 全绿；apiDump + apiCheck 通过 | ✅ 已归档 |
| L7 | L7.36 | 双击检测工具（`layout/ClickTracker.kt`）：纯 Kotlin、无框架依赖；`press(index, nowMillis)` 在同一下标且与上次按下的间隔在 `0..doubleClickMillis`（默认 400ms，含边界）内返回 true，每次按下都更新基准；时钟回拨不匹配且该次按下成为新基准；`reset()` 清空。面向直接处理 `onMouseEvent` 的行选中场景，当时未接入 `Modifier.clickable`（L7.37 已接入） | `284565b6` | `ClickTrackerTest`（6：同下标窗口内/不同下标/超窗/时钟回拨/reset/负窗口拒绝）；定向 JVM 测试 6/6 通过；apiDump + apiCheck 通过；仅 `:mosaic-runtime:compileKotlinLinuxX64`（按时间盒跳过 linuxX64Test） | ✅ 已归档 |
| L7 | L7.37 | `Modifier.clickable` 跨重组按压捕获加固 + 可选双击：按下时捕获元素 state 与**按下瞬间边界**，释放不再依赖节点实例/父链存活（消除 `isAttachedTo(root)` 在子组合中的误判）；元素移除/禁用/越界均不触发并清空双击配对。新增 `onDoubleClick`（默认 null；首个释放 `onClick`，同元素 400ms 内第二个释放仅 `onDoubleClick`，第三个重新起配对；每元素独立 `ClickTracker`；未提供时每次释放都是 `onClick`） | `a935b802` | `PointerInputTest` 23 条（原 12 + 新增 11：节点替换 ×2 / `BoxWithConstraints` / 移除 / 禁用 / 双击窗口内 / 超窗 / 三连击 / 不同元素 / 集成双击）；定向 JVM 23/23；apiDump + apiCheck 通过；仅 `:mosaic-runtime:compileKotlinLinuxX64`（按时间盒跳过 linuxX64Test） | ✅ 已归档 |
| L7 | L7.38 | 标签栏组件（`ui/TabRow.kt`）：`TabRow(tabs, selectedIndex, onSelect, modifier, colors = TabRowColors(), divider = "│", trailing)`——每个标签为 `" label "`（左右各 1 列内边距），由 `clickable`/`hoverable` 驱动；背景优先级选中 > 悬停 > 容器，内容色选中/未选中分离，标签间可选 1 格分隔符；`trailing` 为 `RowScope` 槽位（可用 `weight` 右对齐提示）；六项配色默认 `Color.Unspecified`，不绑定应用调色板；不含选中/滚动/键盘逻辑 | `d5bf3b19` | `TabRowTest`（7 条：标签/分隔/尾部权重、选中与非选中配色、点击第二个/已选中标签、悬停背景与离开恢复、`divider=null`、空标签） | ✅ 已归档 |
| L7 | L7.39 | 非模态浮层第一阶段（`ui/OverlayHost.kt` / `ui/Popup.kt`）：`OverlayHost(overlay, modifier, content)` 以 base 先测先画、overlay 槽 `matchParentSize` 跟随 base，直接子节点即 layer（后声明在上）；`Popup(alignment, offset, modifier, content)` 按内容尺寸测量并由槽按 base 相对 alignment + cell offset 放置，鼠标命中自顶向下、popup 内未消费事件由层内 shield 吞掉、popup 外回落 base；单组合仅允许一个 host（第二个/嵌套抛 `IllegalStateException`）；键盘/粘贴仍按树序（P2 门控）、无 modal/backdrop/焦点域/Esc（P2） | `19c291a1` | `OverlayHostTest` 6 条（null 等价 / 槽尺寸 / 0×0 base / 嵌套与第二 host 抛错 / 键盘行为锁定）+ `PopupTest` 5 条（绘制顺序 + 对齐偏移 / 双层命中 / 点内屏蔽 / 点外回落 / popup 内容优先）；定向 JVM 11/11；apiDump + apiCheck；仅 `compileKotlinLinuxX64`（时间盒跳过 linuxX64Test/全量） | ✅ 已归档 |
| L7 | L7.40 | 弹窗/浮层 P2（模态输入门控）：`Dialog`（居中、默认 `OverlayBackdrop.Solid`、Esc 后置、backdrop 点击 + 内容 shield、焦点保存/恢复）与 `Popup(modal = true, backdrop, onDismissRequest)`；`OverlayBackdrop.None/Solid`（`Solid` 经 `TextSurface.clearRect` 清字符/前景/背景/下划线，可铺背景色）；根入口 key/mouse/paste 按层反序、modal 屏障；`FocusManager` 活动域/保存恢复/Tab 空域消费/域外 requestFocus 忽略；`PointerState` 候选 roots/按压取消/hover 清理；`OverlayHostState` 作为内部 LayoutModifier 挂到组合根，applier 变更置 dirty，layout/input 前同步 | `1149eb06` | `DialogTest` 13 + `PopupTest` 6 + `OverlayHostTest` 6 = 25 条（modal key/paste 屏障、Esc 内容优先、backdrop 点击与 press/release 跨区、Solid 清样式与背景色、None 保 base、焦点保存恢复/目标消失、Tab 域/空域、按压取消与 hover 清理、双层 LIFO、modal Popup 屏障）；全量 JVM 794/794、linuxX64 792/792；apiDump + apiCheck；compileKotlinLinuxX64 | ✅ 已归档 |
| L7 | L7.41 | 浮层 P3 验收：`EndToEndGoldenTest` 增 3 条端到端 golden（Dialog 默认 Solid 擦除 base 且内容居中、Popup 叠加但 base 其余可见、关闭 Dialog 后增量恢复）；showcase 增 Overlay 页（shell 整体包 `OverlayHost`，非模态 Popup + 模态 Dialog + base 点击计数；页面经编号键/标签进入），页面 smoke 测试 | `164872d1` | 全量 JVM 797/797、linuxX64 795/795（含 3 条 golden）；`ShowcaseTest.overlayPageShowsPopupAndDialogControls` 通过；`:samples:showcase:compileKotlinLinuxX64` 通过；API 未变（无需 dump）；showcase 既有 Unicode 测试失败已由 L7.42 修复 | ✅ 已归档 |
| L7 | L7.42 | showcase Unicode 测试夹具修复：`UnicodeScreen` 的 `Line` 用 `Row(fillMaxWidth) + weight + 尾部│`，测试直接在无界根上渲染时尾部│落在第 0 列并覆盖首字；测试改为按 shell 运行时宽度（`Terminal.Size.Default.columns`）包一层 `Box(width)` | `14126970` | `:samples:showcase:jvmTest` 11/11；`:samples:showcase:compileKotlinLinuxX64` 通过 | ✅ 已归档 |
| L7 | L7.43 | 浮层 P3 打磨三项：①层变化后按最后鼠标坐标重算 hover（`PointerState.rehover`，不派发 click/focus）；②`BoxWithConstraints` 子组合根 `parent` 链接主树，`OverlayHost` 沿 parent 注册到组合根，applier 变更标记顶层 host；③`Cursor` 通过内部 node attach + `CursorState.isVisible` 按活动焦点域 gate，modal 打开时 base 光标隐藏、关闭恢复（modal 内光标可见） | `bb61db22` | `DialogTest` 增 opening/closing hover + BWC 3 条、`CursorActiveDomainTest` 3 条；全量 JVM 803/803、linuxX64 801/801；apiDump/apiCheck（API 无变化）；compileKotlinLinuxX64 | ✅ 已归档 |
| L7 | L7.44 | 浮层剩余正确性：①`TextSurface.clearRect` 按行把矩形扩到宽簇边界（起点在续列时左移簇头，右缘覆盖簇头时扩到簇尾），部分 backdrop 不再留半格；②`FocusManager` 焦点栈按 modal layer node 记录 owner+saved，非 LIFO 关闭只删对应层、保留上层焦点；全部关闭时回退最近仍有效目标（含被丢弃层的 saved），不恢复已移除/禁用节点，并用 `check` 保证栈与层对齐 | `fed2ecda` | `OverlayBackdropTest` 6 条（CJK/emoji 头/续列/相邻/全屏）+ `DialogTest.nonLifo...` 1 条；全量 JVM 810/810、linuxX64 808/808、showcase 11/11；apiCheck（API 无变化）；compileKotlinLinuxX64 | ✅ 已归档 |
| L7 | L7.45 | 修复 `OverlayBackdrop.Solid` 在非原点 host 的清理偏移：`TextCanvasDrawScope.clear` 用 `canvas.translationX/Y` 作为局部 (0,0) 的绝对原点（此前取负值，host 在 y=1 时会从 row0 开始清，擦掉 Header）；现在只清 host/layer 实际矩形，clip 仍生效。回归：header + host + footer 的 golden（header/footer 保留、host 中间清空、Dialog 居中）；`OverlayBackdropTest` 直测 translation (2,1) 的 2×1 清除只影响目标格。既有全屏 golden 不变 | `b1256722` | 全量 JVM 812/812、linuxX64 810/810、showcase 11/11；apiCheck（API 无变化）；compileKotlinLinuxX64 | ✅ 已归档 |
| L7 | L7.46 | 合并前格式清理：`spotlessApply` 全仓库格式化（62 文件，11.8k 行，均为换行/缩进/尾随逗号/import 顺序/分支空行等格式变更）+ 10 处 compose lint 逐条抑制或私有参数重排（Border param-order、PointerInput composed、BoxWithConstraintsNode、BWC/Intrinsic/PointerInput/BWC 测试、AnimatedVisibility、samples/bench Main）；无行为/公共 API 变化 | `e236784d` | `:spotlessCheck`、`:mosaic-runtime:spotlessKotlinCheck`、`:mosaic-animation:spotlessKotlinCheck`、`:samples:bench:spotlessKotlinCheck` 全绿；`:mosaic-runtime:jvmTest` 812/812、`:mosaic-animation:jvmTest` 23/23、`:samples:showcase:jvmTest` 11/11；`:mosaic-runtime:compileKotlinLinuxX64` 通过 | ✅ 已归档 |

## 5. 已完成步骤的实现要点（供恢复上下文）

### L7.19 焦点 / 键盘导航原语（`layout/Focus.kt`，`e7806082`）
- **定位**：落地 todo §15「焦点 / 键盘导航原语」——`BasicTextField`（L7.17）的「多字段 / 点击聚焦」前置；依赖 L5 输入 + L7.18 鼠标通路。
- **API**：
  - `Modifier.focusable(enabled = true)`：进入焦点遍历（Tab / Shift+Tab 在树序上环形移动）+ 主键点击聚焦；disabled 既不遍历也不可点击聚焦。
  - `Modifier.onFocusChanged { FocusState -> }`：`hasFocus` = 自身或后代聚焦、`isFocused` = 自身即聚焦目标、`isCaptured` 恒 false（无 capture 概念）。放在祖先可观察后代聚焦。
  - `FocusRequester` + `Modifier.focusRequester(r)`：程序化 `requestFocus()`，与 `focusable` 配套（对非 focusable 目标无效）；典型用法 `LaunchedEffect { r.requestFocus() }` 首帧聚焦。
- **每节点单 `FocusTarget`（`Focus.kt`）**：`setModifier` 折叠时，同一节点的 `focusable`/`onFocusChanged` 共享**一个** target（首个创建、跨重组复用，`hasFocus` 态不丢）；倒序清理观察者、按新 lambda 重建。无 focus 元素的节点 finalize（置 false）后置空 target。
- **组合级 `FocusManager`（根节点惰性创建）**：`focus(node)` 为一个「焦点事务」——释放旧分支（自身 self↓ + 「非新路径」祖先 hasFocus↓）→ 点亮新分支（self↑ + 沿祖先 hasFocus↑ 至首个已亮祖先停）→ 每节点经 `setFocus(down, self)` **一次原子通知**（避免 Compose 式冗余回调）。`preKeyEvent`：Tab/Shift+Tab 且有 focusable → 移动并**消耗**（`repeat` 吞掉不连滚）；无 focusable → 放行（旧 Tab 应用行为不变）；Ctrl+Tab 不拦截。焦点节点失效（脱离树 / 不再 focusable）在下一事件 reconcile 清除。
- **点击聚焦**：`PointerInput.preDispatch` 的 Press 阶段收集命中路径，取最具体（叶子优先）的可点 focusable 聚焦；点击非 focusable 不动焦点（与 Compose 语义一致）。
- **BasicTextField 接入**：内部挂 `.focusable(enabled)` + `.onFocusChanged { focused = ... }`——只有聚焦才响应按键/IME/粘贴、画反显光标；未聚焦字段惰性。宽度恒 `strWidth+1`（enabled），避免空文本 0 宽不可点击、聚焦时宽度跳变。
- **showcase Field 页**：双可编辑字段（姓名 auto-focus + 备注）+ 各自聚焦状态行 + readOnly/disabled 展示；页 8 中 Tab 现在走「字段间焦点」而非切页（其他页无 focusable，Tab 仍切页）。
- **测试**：`FocusTest`（11 条）+ `BasicTextFieldTest` 改聚焦前置（字段惰性致使原直接打字用例全部先点击聚焦、空字段图 0 宽问题改为恒留 1 列）。
- **验证**：runtime jvm 683 / native 681，showcase jvm/native 各 8，apiCheck 通过。 鼠标交互原语 `hoverable` / `clickable`（L7.18 工作树）
- **定位**：落地 todo §15「鼠标交互」——把已解析但未消费的鼠标事件提升为声明式原语；依赖 L5 鼠标通路 + L4 布局已就绪。
- **API（`layout/PointerInput.kt`）**：
  - `Modifier.hoverable(enabled = true, onHoverChanged: (Boolean) -> Unit)`：指针进入/离开元素边界回调；leave 在**下次鼠标事件**上报（静止指针无事件，接受该限制）。
  - `Modifier.clickable(enabled = true, onClickLabel: String? = null, onClick: () -> Unit)`：主键（左键）在边界内按下→边界内松开才触发；按下后拖走 / 到边界外松开 / 松开落在其他组件都取消。wheel 等非左键不响应。
- **稳定性设计**：`composed`+`remember` 让每个元素的 `PointerInputState` 跨重组复用（重组仅刷新 `enabled`/回调）；`Node.setModifier` 折叠时把元素 state 收集进节点 `pointerInputs`（每次重建、天然去陈旧）。删除元素后其 state 不再在路径上 → 下次事件触发 leave。
- **会话级预检（`PointerState`，仅在组合根一次）**：`sendMouseEvent` 在根节点先跑 `preDispatch` 再走原命中分发——① 沿命中路径收集 pointer 输入（递归先子后己，列表首项=最具体叶子）② hover enter/exit 与「上次 hovered 集合」对账 ③ click 按下捕获（路径上第一个 enabled clickable）+ 松开判定（回查**原节点当前边界** + `isAttachedTo(root)` 存活校验，防节点已移除仍触发）。不消费事件，`onMouseEvent` 低层通路照常。
- **showcase**：`TabBar`/`Tab` 改由 `.clickable { onSelect(index) }`+`.hoverable { hovering = it }` 驱动——移除旧的 `hoveredPage` 状态；顺带修复原 hover 粘滞（旧实现只置不清，指针移开后高亮残留；新实现 leave 正确清除）。页脚提示改「悬停/点击标签」。
- **测试**：`PointerInputTest`（12 条，双平台）——hover：enter/exit、兄弟间迁移、disabled 忽略；click：同界触发、越界取消、界外按下不触发、disabled 忽略、叠放兄弟取顶序、嵌套取内层、wheel 忽略、hover+click 共存、按下后拖动仍在原界内释放触发。`ShowcaseTest` 增 `tabBarClickSwitchesPage`（端到端：算实际标签宽定位 Text 页签→模拟 Press/Release→断言切页；顺带暴露 `weight(fill)` 槽位=内容宽的既有布局行为）。
- **验证**：runtime jvm 672 / native 670，showcase jvm/native 各 8，apiCheck（.api/.klib.api）通过。 通用滚动容器 + 裁剪（`cc62a232`）
- **定位**：`Text.scroll`（L4.4）只动 Text 的查看窗口；本次把「滚动 + 裁剪 + 状态」抽象成通用 `LayoutModifier`，任意内容（列表 / 单行 / 带背景块）都能放进有界视口滚动。
- **状态（`layout/Scroll.kt`）**：`ScrollState.value` 与 `maxValue` 均为 `mutableIntStateOf` 快照态（setter internal）；`maxValue` 每次 measure 由滚动 modifier 计算写回（内容尺寸 − 视口，clamp≥0），UI（状态行 / 滚动条）上帧即可刷新，无需等 value 变化；`scrollBy`/`scrollTo` 双端 clamp，`scrollBy` 返回实际消费量（到边界时小于 delta）。
- **滚动布局**：`verticalScroll`/`horizontalScroll` 链上 `ClipToBoundsElement`（外）+ `ScrollingLayoutModifier`（内）；滚动轴方向用 `Infinity` 测内容取自然尺寸，视口取父约束有界值（父无界则视口=内容、无可滚）；返回 `layout(视口){ content.place(-scroll, 0) }`。
- **裁剪**：`clipToBounds()` 是纯 DrawModifier；`Node.kt` `DrawLayer` 对 ClipToBoundsElement 安装画布裁剪矩形（用本层 x/y/width/height 与外层 clip 求交）并在绘制尾部恢复；`surface.kt` 增加 `clipActive/clipLeft/clipTop/clipRight/clipBottom`（非 clip 单布尔分支）；`DrawScope` 簇写入 / 实心矩形前加 clip 守卫（整簇可见才写，续列一并）。
- **绘制修复（新路径逼出的三个真 bug）**：① 簇写入器只在「实际绘制」时推进列游标——一旦某簇被裁剪跳过，后续簇全部卡同一列、整行空白；改判可见性、始终 `characterColumn += w`，整簇落入 canvas+clip 才写。② `maxValue` 由普通字段改快照态（见上）。③ `TextSurface` 格子跨帧复用：`updateTextPixel` 仅覆盖「已指定」字段 → 无背景文本继承上一帧背景色（切页残留）；改为帧内**首次写入重置空白**（同帧后续绘制仍叠加，保住「父填充 + 子文字」层叠语义）。
- **鼠标**：`ScrollWheelMouseModifier` 挂在滚动容器上，`Press` 型 `WheelUp/WheelDown` → `scrollBy(±3)`，方向镜像方向键；命中走既有「先子后父」分发，子项不消费时容器滚；到边界仍消费（与常规做法一致）。
- **showcase**：第 6 页 Scroll——三类容器 + 三种快捷键分工（第 1 段 `↑/↓`、第 2 段 `[`/`]`、第 3 段 `PageUp/PageDown`）+ 滚轮；每段状态行实时 `value/maxValue` + `█·` 滚动条。注意页高：滚动页按 ~16 行排布，过矮终端第 3 段会溢出，属 showcase 页面的已知布局约束。
- **todo 勾销**：§15「滚动容器（最大布局缺口）」；惰性列表已由 L7.16 落地（见台账 L7.16 行与 §5 同节）。

### L7.16 惰性列表容器（`f4b6f9a1`）
- **定位**：列表场景只组合可视窗口内的子项，代价 O(viewport/itemHeight + lookahead) 与总数无关；落地 §15「惰性列表」。
- **API（`ui/LazyColumn.kt`）**：`LazyColumn(modifier, state, itemHeight=1, beforeLookahead=2, afterLookahead=2, content)`；`LazyListScope` DSL `items(count) { index -> }` / `items(List) { item -> }`，多段共享全局 index（`items(List)` 用 `items[index - start]` 映射，修过越界 bug）；注意 `LazyListScope.()` 参必须标 `@Composable`。
- **窗口算法**：`firstVisible = scroll/itemHeight - beforeLookahead`，`lastExclusive = firstVisible + viewport/itemHeight + before + after`，只组合该窗口内条目；首帧视图高用终端 rows 猜、measure 期写回快照态，父无界时整列表当视口。
- **measure**：固定 `itemHeight` 槽位锁高测宽、取最宽子项为列宽；`LazyIndexModifier`（ParentDataModifier）把全局 index 传回 policy，`place(0, index*itemHeight - scroll)` 并 clamp；`maxValue = max(0, totalHeight - viewport)`。
- **复用 L7.15**：`.then(ClipToBoundsElement).then(ScrollWheelMouseModifier)`；`ScrollWheelMouseModifier`/`MouseWheelLines` 由 private 改 internal。
- **键盘 auto-repeat（笔记本无独立 PgUp/PgDn 的问题）**：`KeyEvent` 增 `repeat` 标志，`compat.kt` 按 `KeyboardEvent.eventType==Repeat` 映射（press 与 repeat 都投递、release 丢弃）；Page 键在 showcase 先用「`repeat` 标志 + 同键 600ms 时间窗」双重抑制（该时间窗是铺垫，L7.16.1 根因修复后已撤销，见下节）；Input 页回显补 `(repeat)` 标签便于诊断终端是否带 repeat 标志。
- **showcase**：新增第 7 页 Lazy——500 行、↑/↓ 逐行、PageUp/PageDown ±(视口−1) 行、滚轮、实时 `offset/500` + 滚动条；与第 6 页 Scroll 的快捷键不冲突（页面自身 Box 先于外壳消费）。
- **todo 勾销**：§15「惰性列表」；`LazyRow` 仍待办。

### L7.16.1 legacy `<key>~` 键事件补解析调制键与事件类型（`e9ca8239`）
- **根因（字节级定位）**：kitty 对一次 `Fn+↓`（PgDn）实际发送 `CSI 6;129~`（按下）+ `CSI 6;129:3~`（松开）。`EventParser` 的 `~` 分支此前 `// TODO parse rest of CSI ... ~` + `return KeyboardEvent(codepoint)`，**根本没解析 `;modifiers[:event-type]`** —— `:3~` 的松开事件被当成第二次按下 → 一次物理按压翻两页。Home/End 走 `parseCsiLegacyKeyboard` 分支正确解析并丢弃 release，所以只有 PgUp/PgDn 异常（辅助证据）。
- **修正（`tty-terminal/EventParser.kt`）**：`~` 分支在 `delimiter != finalIndex` 时解析 `;modifiers[:event-type]`（缺省 press），逻辑与游标键分支对齐；`;129:1~`→press、`:2~`→repeat、`:3~`→release。release 进 runtime 后被 `compat.kt`（`eventType == Press/Repeat` 才投递）丢弃，Page 键不再重复翻页。
- **撤销 L7.16 的 600ms 时间窗**：时间窗只是掩盖解析错误的「敷衍式」兜底，根因修复后不再需要。PageUp/PageDown 改回——只响应全新 press（翻一页）、吞 `event.repeat`（键盘协议自动重复，长按只翻一页）；release 在 compat 层即消失，不进入 `onKeyEvent`。`LazyScreen` 移除 `PageKeyRepeatWindow`/`lastPageKey` 及 `TimeSource`/`TimeMark`/`milliseconds` import。
- **测试**：新增 `EventParserCsiTildeKeyboardEventTest`（9 条，commonTest 双平台）——PgUp/PgDn/Insert/Delete/Home/End 纯形、`6;129:1~` press 带 `ModifierNumLock`、`:3~` release、`:2~` repeat、`11;2~` Shift+F1；`ShowcaseTest.lazyPagePageKeysIgnoreAutoRepeat` 改为 press→9 / repeat→仍 9 / release→仍 9 / PageUp 反向立即 0。回归：tty-terminal jvm/native 各 +9、runtime 642/640、showcase 6/6、apiCheck 全绿。
- **杂项**：`bin/`（本地 native 构建产物目录）入 `.gitignore`，防误提交 kexe。

### L7.17 单行可编辑文本输入框 `BasicTextField`（`7d07e645`）
- **定位**：落地 todo §15「输入控件」——`BasicTextField` 等价物（单行、光标、IME、粘贴、基础编辑语义）；依赖 L5 输入 + L7.16 `KeyEvent` 已就绪。
- **API（`ui/BasicTextField.kt`）**：`BasicTextField(value, onValueChange, modifier, enabled, readOnly, textStyle, color, background, onDone)` 受控组件（值由调用方持有，与 Compose 语义一致）。`onDone` 是末尾函数参，调用需具名 `onValueChange = { ... }`（尾随 lambda 绑定到 onDone，与 Compose 用法一致）。
- **光标渲染**：终端原生**反显块**——在光标所在字素上以 `TextStyle.Invert` 重绘该字素、光标在末尾则反显空格；不用颜色、任何终端可见（SGR 7/27）；已启用才画光标。
- **编辑模型（`TextEditing` 内部纯对象 + `TextEdit`）**：光标恒在字素簇边界；`clusterStartBefore`/`clusterEndAfter` 复用 `forEachGraphemeClusterRange`（完整 UAX#29）；`←/→` 一次跳一个簇（CJK/组合序列成簇移动）、`Home/End`、`Backspace/Delete` 删整个簇、IME/粘贴在光标处插入；`isEditableText` 过滤控制符、Ctrl 组合不消费（保应用快捷键）。
- **同帧连击丢字（开发中揪出的真 bug）**：键盘事件可在同一帧预算内成批到达、重组未发生时若编辑基于闭包旧 `value`，同一批每个事件都命中旧文本 → 只剩最后字符。修复：字段持**内部文本镜像** `editableText`（`remember mutableStateOf`，快照写对后续读即时可见）+ 每事件入口**现算** `caret = cursor.coerceIn(0, editableText.length)`；父级 value 变化时对账采纳并钳制光标。这同时解决受控组件"父级忽略 onValueChange"（过滤输入）时的收敛。
- **焦点前置说明**：键盘事件目前广播给所有节点，单屏只能放一个可编辑字段；多字段/点击聚焦依赖 §15「焦点/键盘导航原语」，另行实现。
- **测试**：`BasicTextFieldTest`（18 条，双平台）——引擎 9 条（插入/空插/退格/删除含组合簇、导航钳制、宽字符列宽 `strWidth` 对拍）+ 组合层 9 条（渲染文本+反显光标、disabled 无光标、typing 更新受控值、ctrl 不插入、退格删除、光标中插、粘贴插入），走真实事件通路。**harness 要点**：外部受控 state 写入需先打一帧基线才重组；无变化场景用 `setContentAndSnapshot`+`draw()` 对拍而非 `awaitSnapshot`（后者等变更会超时）。
- **showcase**：新增第 8 页 Field——可编辑字段初始 `你好，mosaic` + 实时「当前值」回显 + readOnly/disabled 展示；`1`-`8` 快捷键；pty 端到端验证 `h/i/中` 正常进字段。

### L7.14 能力探测超时降级可观测（`7b5aee1f`）
- **背景**：1s bootstrap 预算（`withTimeoutOrNull`）早已有，但无响应终端降级**全程静默**——扩展能力静默关闭、界面照常运行，用户误以为一切正常或把它当 bug 排查。
- **实现（`TtyTerminal.kt`）**：`withTimeoutOrNull { bootstrapDone.await(); true } ?: false` 捕获是否按时完成；超时先记录卡住阶段（`StageDeviceAttributes`/`StageCapabilityQueries`/`StageDefaultQueries`）再重置 `StageNormalOperation`，随后向 stderr 打诊断。默认一行英文提示（预算 + 卡住阶段 + 指向调试开关）；`MOSAIC_TTY_TERMINAL_DEBUG=true` 时追加「已确认能力清单」（bootstrap 各布尔局部汇总）或「无已确认能力」。
- **平台层**：新增 expect/actual `stderrPrint(message)`（`platform.kt`）——JVM `System.err.println` + flush；native posix `write(STDERR_FILENO, …)`，与 runtime `warnNonInteractive`/`nonInteractiveExit` 同构，走 stderr 而非渲染面 stdout。
- **测试**：`TtyTerminalBootstrapTimeoutNoticeTest`（commonTest 4 条，双平台）——默认文案含预算/DA1 阶段/调试开关且不列能力、debug 列出已确认能力、空清单文案、未知阶段兜底文案。`TtyTerminalTimeoutNoticeJvmTest`（jvmTest 1 条）——真实无回复终端跑完 1s 超时，重定向 `System.err` 断言确实输出提示（非静默）。阶段常量 `private→internal` 供单测引用。
- **备注**：超时后行为不变（晚到回复仍丢弃、未确认能力默认 false，安全降级）；仅增加可观测性。`ExperimentalForeignApi` 未解析警告为改动前既有配置噪音。
- **todo 勾销**：§10「P2 超时兜底」。

### L7.13 `Modifier.onGloballyPositioned` 放置回调（`118c1a2e`）
- **定位**：把布局"反向读取结果"——普通节点只被父级摆放、不知道自己的绝对坐标；该 modifier 在放置完成后回调**根坐标系（屏幕）左上角 + 尺寸**，用于锚定弹出层/菜单、定位光标、判断越界与裁剪决策等，不参与也不改变布局。
- **实现（`layout/OnGloballyPositioned.kt`）**：透传 `LayoutModifier`（`measurable.measure(constraints)` 原样 + `layout(w,h){...}`），placementBlock 里 `x/y` 即全局坐标（放置级联自顶向下，结束时已累加全部祖先偏移）；用字段比较上次 (x,y,w,h)，**仅变化时触发**，未移动布局不会每帧重复回调。
- **closure of §5「P2 Modifier 缺口」**：`widthIn/heightIn/sizeIn`、`requiredWidth/Height/Size(+In)`、`fillMax*`、`wrapContent*`、`defaultMinSize` 等便捷约束早已齐备（`layout/Size.kt`），真实缺口仅 `onGloballyPositioned`——本次补上后该条勾销；§15「定位回调」亦随之闭环。
- **测试**：`OnGloballyPositionedTest`（5 条）——嵌套 padding 报 (3,5)+(4,6)、Row 7 宽兄弟后报 (7,0)、`offset` 移动二次触发、宽度变化二次触发、真实组合循环 20 动画帧持续重绘但未移动节点仅报 1 次。`apiDump` 同步 `.api` 声明，`apiCheck` 通过。
- **todo 勾销**：§5「P2 Modifier 缺口」、§15「定位回调」。

### L7.12 ISO 控制字符净化（`ab05f570`）
- **背景**：`Text("A\tB")` 等含控制符的字符串此前被 `charWidth`/快路径量成 1 列并**原样发出**——真实终端把 `\r` 当回车回行首、`\b` 当退格、`\t` 当跳格、`\u001B` 当转义等，会直接打乱当前行的游标/布局（与测量宽度不一致）。
- **方案（决策）**：控制符统一定义为**非打印**——0 列、不绘制、不进 cell / ANSI 流（对齐台账参考：过滤控制符而非展开 tab）。**不做制表符展开**：展开需要"当前列"做上下文，会破坏本项目"簇宽度上下文无关、测/绘唯一口径"（`graphemeClusterWidth` 两端共用）的核心不变量。
- **实现**：`charWidth` 前置 `isControl(cp)=0`（C0 0x00-0x1F / DEL 0x7F / C1 0x80-0x9F）；`graphemeClusterWidth` 对控制簇（GCB_CONTROL 必为单码点簇）直接返回 0，ASCII 快路径排除 tab 使含控制串走逐簇口径；`DrawScope.writeCluster` 对 `w<=0` 簇跳过（控制与 ZWSP/BOM/孤立组合符都不落 cell）。`\n` 仍是 Text 行分隔符，CRLF 中的 `\r` 被滤掉、`\n` 正常分行。
- **测试**：`IsoControlRenderingTest`（7 条）——`A\tB`/`A\rB`/`A\bB`/ESC/NEL 全部净化为 `AB` 且帧内无原始控制字节；纯控制行保持空；`A\r\nB` 仍两行；`\n` 行分隔不受影响。`GraphemeWidthTest` 增 `controlCharactersOccupyZeroColumns`（tab/CR/BS/ESC/DEL/NEL=0 列，`A\tB` 测宽 2）。
- **todo 勾销**：§4「P2 ISO 控制字符处理」。

### L7.10 真实终端渲染回归修复（`27d038a0`）
- **尾随 CRLF 上滚（切页残留根因）**：首帧 / resize / static 全量重绘帧在最后一行后带 `\r\n`——真实终端在末行收到换行会把整屏上滚一行；此后所有绝对坐标 diff 全部错位一行 → 双 tabBar、上一页标题/CJK 行残留。内存 `VirtualTerminal` 不模拟滚动，单元测试测不出。修复（`rendering.kt`）：全量重绘路径改为**行与行之间 CRLF 分隔、最后一行不终止**；分隔符需在 `clearLine` 之前（清的是下一行）。`VirtualTerminal` 的 `CSI F`（CPL）补 `cursorColumn = 0`（原漏回列 0，被 resize 测试暴露）。
- **BoxWithConstraints 0×0 容器不绘制（Layout 页第 4 节空白根因）**：`BoxWithConstraintsMeasurePolicy.measure` 只逐个测量子节点并 place，子组合根容器永远是 0×0；绘制遍历（`Node.BottomLayer.drawTo`）跳过宽或高为 0 的节点 → 整棵比例子树一笔不画（节点树完整、尺寸全对）。修复：对 `subRoot` 额外 measure 一次（结果丢弃）让容器获得非零尺寸；子节点仍按原语义单独测量/place，`matchParentSize` / `align` 不受影响。
- **paint 越界裁剪（早期遗留一并归档）**：越出屏幕画布的绘制从抛错改为裁剪到屏幕边缘（`DrawScope` 簇写入器 + fillRect 边界，在节点 placement 平移后的 canvas 局部系换算）。
- **回归**：`FullRedrawNoScrollTest`（2：帧永不带行终止 / Unicode→Input 切页无残留、tabBar 单行）、`BoxWithConstraintsRenderingTest`（1/2 1/3 余量渲染）、`UnicodeScreenReproTest`、`OffsetTest`（越界改裁剪）、`TextMeasuredDrawTest` +2；runtime jvmTest 607 全绿。

### L7.11 showcase 演示样本 + 字符串截断示例（`7aa7f3ec`）
- **定位**：仅用公共 API 的自包含演示（`samples/showcase`），把重构后的能力集落到可运行样例：页头 + 可点击 tab 外壳；Unicode / Text / Layout / Input / Animation 五页；`←`/`→`/`Tab`/`1`-`5` 切页，Ctrl-C 退出。
- **字符串截断示例**（Text 页第 5 节）：`maxLines=2` + `softWrap` 固定宽度，并排展示 `TextOverflow.Clip`（硬切断、无标记）vs `TextOverflow.Ellipsis`（末可见行带 `…`），以及更宽宽度能看到更多内容的对比；中英混排文本，超宽字不拆字素簇。
- **测试**：`ShowcaseTest` 3 条（Unicode 宽字/emoji 内容、Text 对齐+滚动、截断省略号渲染）；showcase jvmTest 全绿。

### L7.9 快照体系扩展（能力矩阵 + 焦点注入）（`a9467f04`）
- **能力矩阵**（`mosaic-testing/CapabilityMatrix.kt`）：`CapabilityMatrix.defaults` 7 组代表性组合——全能力 / 关 2027 / 关 kitty underline / 关 synchronized output / ANSI16 / ANSI256 / 全关（NONE）；`runMosaicTestForEach(capabilities, block)` 每组用全新 `RealTestMosaic` 跑同一 `TestMosaic<String>` 断言并透传该组能力。
- **事件注入补齐**：`TestMosaic.sendFocusEvent(focused)` 写 `TestTerminal.state.focused`，走 runtime 的 `terminalState` collector → `LifecycleRegistry` ON_PAUSE/ON_RESUME（与真实终端焦点事件一致）。鼠标/粘贴/剪贴板注入此前已有。
- **测试**（mosaic-testing commonTest，双平台）：
  - `CapabilityMatrixTest`：同一内容（CJK/combining/emoji/ASCII/多行）在矩阵全部组合下纯文本快照一致（能力开关不破坏布局），并断言全部组合均实际执行。
  - `FocusEventTest`：`sendFocusEvent(false/true)` 使组合生命周期 `RESUMED → STARTED → RESUMED`。
- **todo 勾销**：§13「P2 快照体系扩展」（能力矩阵 + 事件流注入）。

### L7.8 非交互能力提示（`b60dd5b8`）
- **背景**：`Ignore`（绑定失败）与 `AssumeAndIgnore`（跳过检测）都会用惰性 `NonInteractiveTerminal` 继续跑——无颜色/输入/扩展能力，但此前**静默**，用户易误以为正常。
- **实现**：`withTerminal` 落到 `NonInteractiveTerminal` 时先调新增 expect/actual `warnNonInteractive()`（JVM `System.err` + flush；native posix `write(STDERR_FILENO)`，与 `nonInteractiveExit` 同构），打印 `NonInteractiveNotice`（一行英文中性提示）；`Exit/Throw/Return` 早返回不受影响。
- **测试**：
  - `NonInteractiveTest`（commonTest 2 条，双平台）：`AssumeAndIgnore` 跳过绑定、block 拿到惰性终端（`interactive=false`、事件通道已关、`withTerminal` 返回 `true`）；惰性终端能力清单（`ansiLevel=NONE`、kitty/2027/同步输出全 false、focused、`Size.Default`）。
  - `NonInteractiveNoticeTest`（jvmTest 1 条）：重定向 stderr 断言 `AssumeAndIgnore` 确实输出 `NonInteractiveNotice`（非静默）。
- **todo 勾销**：§11「P2 非交互策略」。

### L7.7 TextLayoutWidthTest「测量与绘制一致」断言（`01d5d04d`）
- **动机**：测量端按 (簇, 宽) 切分每行、绘制端直接消费该切分，但没有任何测试把两侧钉在一起——若某处让测量与绘制对簇列数/落位分叉（wrap 切簇、截断错位、续列开合），布局会 vs 渲染尺寸不一致。
- **断言口径（`assertColumnsMatchLayout`）**：
  1. **逐列对拍**：按 `LineMeasure.clusterStarts/clusterWidths` 构造每列期望（簇头部列 = 簇文本；宽簇后续 = placeholder 续列），与 `TextSurface` 实际绘制逐列相等。
  2. **右沿**：每行最后一个 contentful cell（非空、非 placeholder）的 `列+width` == `totalWidth`。
  3. **总宽**：`layout.width` == 各行 `totalWidth` 最大值（measure 端自身闭合）。
  边界口径：行尾空格 cell 被渲染器视为空列，用例避免行尾空格；孤立零宽簇与画布覆盖语义一致（0 宽仍写当前列，per-column 同步覆盖）。
- **用例**（`TextLayoutWidthTest` 7 → 14）：ASCII/CJK、宽簇+combining（ZWJ/flags/keycap/VS16）、行内空格、softWrap 折行（簇不拆）、Clip/Ellipsis、scroll 窗口、AnnotatedString 布局。
- **todo 勾销**：§13「P1 `TextLayoutWidthTest`」。

### L7.6 awaitComplete 生命周期语义明确化（`f679993c`）
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

### L5.4 clipboard（OSC 52）（`9af01ce1`）
- `mosaic-terminal`：`ClipboardEvent(selection: Clipboard/Primary/Unknown, text: String?)`——`null`=终端拒绝/解码失败，`""`=空剪贴板；`Terminal` 接口新增 `requestClipboardRead(selection)` / `writeClipboard(text, selection)`。
- `mosaic-tty-terminal`：`EventParser` `parseOsc` 加 `52` 分支（`ClipboardOsc` 工具负责 base64 编解码 + selection 映射，`!`→null）；`TtyTerminal` 实现→写 `OSC 52;sel;?`(读) / `OSC 52;sel;<base64>`(写，空串清空)。
- `mosaic-runtime`：`ui/platform/Clipboard.kt` 镜像 Compose 新版 `LocalClipboard` 语义——`Clipboard`（`suspend getText()/setText()`）+ `LocalClipboard` + `TermClipboard`（读=发读请求后 await `ClipboardEvent` 回复，2s 超时）。`MosaicComposition` 提供 `LocalClipboard`，帧监听把 `ClipboardEvent` 喂给 `TermClipboard`；`NonInteractiveTerminal`/`TestTerminal` 补齐实现（捕获写/记录读请求）。
- `TestMosaic` 增 `sendClipboardReply(text, selection)` 注入回复。

### L5.5 事件背压/风暴合并（`bf564374`）
- `mosaic-tty-terminal`（`TtyTerminal.kt`）：`events` channel 固定 64 缓冲由 `onBufferOverflow = DROP_OLDEST` 改为 `SUSPEND`，生产者 `events.trySend(event)` → `events.send(event)`。大粘贴/鼠标风暴不再丢**离散**事件；极端溢出时仅阻塞解析循环（消费端 ~1000fps 每帧排空，正常负载不触发）。
- `mosaic.kt` 帧监听循环：批内 `pendingMotion` + 局部 `flushMotion()`——连续 `Motion/Drag` 只保留**最后坐标**；遇到离散事件先 flush 再处理；循环结束 flush。键盘/粘贴/剪贴板事件不受影响。
- 测试：`TtyTerminalBackpressureTest.kt`（新）`largeBurstIsNotDropped`——写入 500 字符 → `events.receive()` 收到 500 条 `KeyboardEvent`（5s withTimeout，0 丢失；注意 `this@withTerminal.events.receive()`，block 接收者是 `Terminal`）。`MouseEventTest` 增 3 条：批内 3 motion+release → `["Motion@3,3","Release@3,3"]`；纯 motion 批 → 只投递单一最新 `["Motion@7,8"]`；双 Press 全部到达（离散事件绝不合并）。
- 测试要点：`MouseEventTest` 中 `received` 状态必须被组合**读取**（Box 内 `Text(received.size.toString())`）才会触发重组 onDraw，`awaitSnapshot` 才能返回；否则分支压根不重组、1s 虚拟超时。

### L6.8 探测清单完备性核对 + 清单回归测试（`7ba3a7ef`）
- **审计结论（探测清单逐项对账，13 项全对应暴露能力）**：
  - 6×DECRQM（`CSI ?Ps$p` → `CSI ?Ps;Pm$y`）：25 光标 / 1004 焦点 / 2026 同步输出 / 2031 系统主题 / 2048 in-band resize / 2027 字素聚类，逐一喂给 `Terminal.Capabilities`。
  - theme 现值查询：`CSI ?996n` → 报告 `CSI ?997;{1|2}n` 是有意设计（上游提交 `546920a2` + 既有 `TtyTerminalThemeTest` 一致），非笔误，未改动。
  - kitty 五项（APC graphics / `CSI?u` keyboard / OSC99 notification / OSC22 pointer / OSC66+DCR 组合 textSizing）+ underline（`DCS+q5375`）+ xterm 版本（`CSI>0q`）+ DSR（`CSI 5n`）批收尾。`ansiLevel` 走 env 探测非终端查询。
- **「逐项重查」评估 → 驳回**：真正会「组合不响应」的终端，单独发同样不响应；逐项重查引入 N+1 轮询挤占 1s bootstrap 预算、破坏「未回复→能力默认 false」的安全契约、需改动全部既有探测测试（20+ 方法）；业界通行做法即合并批 + 全局超时。未回复的 DEC 模式默认 false，属安全降级（绝不启用未确认的能力）。
- **交付物**：新增 `TtyTerminalProbeInventoryTest.startupQueryInventory`——锁定完整启动探测清单（各 DECRQM 模式号 + theme + kitty + 版本 + DSR 收尾），任一探测被无声删除即测试失败，防止「探测丢失→能力静默退化」。本步纯测试 + 审计，无生产代码改动（与 L6.3 同性质）。
- **L6 收官**：本步完成后 L6 tty 适配层正式完成（L6.1~L6.8）。剩余 §9.1 三套绑定一致性 / §9.2 macOS / §9.3 Windows / `linuxArm64` cinterop 补全均需远程/CI 环境，登记不改码。

### L6.7 生命周期异常安全核对 + teardown 同步化（`d6926d77`）
- **审计结论**：
  - **正常关闭顺序脆弱（关键）**：`TtyTerminal.close()` 原为 `closeJob.cancel()`（异步），而 `withTerminal` 的 `tty.use` 在无挂起点处紧接着同步 `tty.close()`。teardown（`setCallback(null)` + 回滚写入 + `reset()`）能否先于 `tty.close()`（释放 C 指针）跑完，依赖 `launch(Unconfined)` 的**内联续传**碰巧同步执行——正对应旧 TODO "design of finalization hook fights us here"。探针（复刻 `withTerminal` 嵌套 + 循环 200 次）实证现状不崩，但属隐性依赖：换 dispatcher 即可能打到已释放指针。
  - **异常路径全覆盖**：`asTerminalIn` 中途抛错 → `tty.use.close()` 兜底恢复 termios；应用 block 抛错 → `terminal.use.close()` 触发清理 + `coroutineScope` 取消 reader/interruptJob 子协程。
  - **信号/shutdown 兜底**：`withFinalizationHook`（native 信号 → cancel → hook → 恢复默认重发；JVM 另加 shutdown hook）。L6.4 C 守卫只管 SEGV/ILL/PIPE，与它信号集不重叠、无冲突。
- **修复**：抽出幂等 `teardown()`（`AtomicBoolean` 保证单次）；`close()` 同步先跑 `teardown()` 再 `closeJob.cancel()`，正常关闭**保证**先于 `tty.close()` 完成恢复；finalization hook 复用同一 teardown 兜底信号/shutdown 路径，重复调用由守卫去重。
- **测试**：新增 `TtyTerminalLifecycleTest`（3 条 commonTest，jvm + linuxX64）：`closeOrderingMatchesWithTerminal`（复刻 withTerminal 同 scope 内 tty 紧跟关闭）/ `doubleCloseIsIdempotent` / `exceptionInBlockStillCloses`；既有 teardown 输出用例（Cursor/Mouse/Theme 等）原样通过；runtime 两平台 compile 通过。
- **既有遗留（非本次引入）**：`apiCheck` 在 `linuxArm64` 目标失败——L6.4 新增 C 符号（`mosaic_tty_signal_guard`）的 `linuxArm64` cinterop 陈旧未再生成，需后续补全 target 生成（本地只建 linuxX64）。

### L6.6 读写缓冲核对 + reset 幂等加固（`164d1c70`）
- **审计结论**：
  - **控制序列写已正确**：`TtyTerminal.write(s: String)`（`TtyTerminal.kt`）自带完整写循环——partial write 前进 offset、`-1` 抛 `EofException`；C 层 `mosaic_utils_write` 单次 `write()` 返回部分计数原样回传，无需改动。
  - **主渲染不经 Tty**：帧经 `FrameWriter` 按 8192 字节切块 → `writeScreenBytes` → `flushScreenBytes`。native 侧 `writeScreenBytes` 自带循环；jvm 侧 `System.out.write` 为 PrintStream 原子全量写，无 partial 问题。
  - **幂等性唯一真实边界 bug**：`reset()` 在 `close()` 后无指针守卫——native/jvm/jvmJdk22 三处 close 后指针已置 null/0，但 reset 仍把空指针传给 C `mosaic_tty_reset`（内部直解 `tty->sigwinch`）→ 悬垂/SEGV。C 层 `mosaic_tty_reset`/`mosaic_tty_free` 各步已守卫、本身幂等。
- **修复**：三平台 `reset()` 补 close 后指针守卫（jvm `ptr != 0L` / jvmJdk22 `ptr != NULL` / native `ptr?.let`），与 `close()` 既有守卫行为对齐——closed 后 `reset()` 为幂等空操作。
- **测试**：`TestTtyTest` 增 `closeAfterCloseIsIdempotent`、`resetAfterCloseIsIdempotent` 2 条（commonTest，jvm + linuxX64 双平台全绿）。
- **未改项（已评估）**：调用层循环、批量 flush 语义保持现状；native `writeScreenBytes` 遇错即抛、`EINTR` 不重试仅属边缘瑕疵不阻塞。Todo §9.4 P2 勾销。

### L6.5 resize 通路核对 + 加固（`59284788`）
- **审计结论**：
  - **互斥由构造保证**：`TtyTerminal` 只在 `inBandResizeEvents == false` 时 `enableWindowResizeEvents()`（装 SIGWINCH）；in-band（2048）支持时 SIGWINCH 永不安装 → **不存在重复发事件**。
  - **收敛单点**：两条通路都写 `size` StateFlow（runtime 唯一尺寸真相源）；channel 上的 `ResizeEvent` runtime 不消费，`trySend` 满仓丢弃也无害；`MutableStateFlow` 原子写 + last-write-wins，无竞态污染。
  - unsolicited `CSI 48;...t`：in-band 未支持时按 unsolicited 丢弃，不会误改尺寸。
  - in-band 通路**天然规避**「SIGWINCH 信号上下文直接调 Kotlin 回调」（该路径仅 in-band 不可用时启用），更安全。
- **边界隐患 + 修复**：in-band 支持但终端**未发出初始尺寸报告**时尺寸停留 `Default(80×24)`（旧版 `replySetNoInitialValue` 固化）。新增 `receivedInBandSize` 标记（in-band 尺寸事件到达即置位），bootstrap 后若 in-band 启用却从未收到尺寸报告，用 `currentSize()`（`TIOCGWINSZ`）播种；真实 in-band 事件（含像素）到达后覆盖，不干扰正常像素路径。
- **测试**：`TtyTerminalResizeTest` 增 2（jvm + linuxX64 双平台，共 10 例全绿）：
  - `inBandResizeIgnoresPlatformResizeSignal`：in-band 激活下 `testTerminal.resize(...)`（SIGWINCH）不影响 `state.size` → 互斥契约负向固化；
  - `inBandSupportedNoInitialValueSeedsFromOsSize`：先把 PTY 调到 200×60，构造「in-band 支持但无初始报告」→ `state.size == Size(200, 60, 0, 0)`（不再是 Default）。

### L6.4 native 信号兜底：终止型信号 raw 恢复（`66c4f2d6`）
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
- **手动验证**：kitty 运行已重链的示例 kexe，另一终端对示例进程发送 `SIGSEGV` → 终端恢复非 raw、进程按信号退出（`$?`=139）。

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

### L6.2 DEC 2027 探测 → `graphemeClustering`（`b84c0dd9`）
- **探测**：DECRPM 批次补 `CSI ?2027$p`（沿用既有模式：`ansi.kt` `graphemeClusteringMode=2027` + Enable/Disable）；`DecModeReportEvent` 2027 分支：`isSupported` → 置 `graphemeClustering`，当前为 reset 时发 `CSI ?2027h` 启用并记 `toggleGraphemeClustering`，退出 `CSI ?2027l` 恢复；set/permanentlySet 不重复启用。
- **公开 API**：`Terminal.Capabilities` 新增只读 `graphemeClustering: Boolean`（文档标语义 + xterm 链接）；`TestTerminal`（默认 true）/`NonInteractiveTerminal`（false）补齐；`mosaic-terminal.api/.klib.api` 已 dump（`TtyTerminal` 为 private，tty-terminal 无公开 API 变化）。
- **宽度口径落法（决策）**：只做「能力暴露 + 终端侧 enable」，**不做逐 rune 后备策略**。理由：① 逐 rune 后备只对不支持簇处理的旧终端有意义，且会让 ZWJ 族回退多格（视觉倒退）；② 本架构整帧 cell 定位，不依赖终端光标跟随，无需 `reanchorWideLine` 式漂移补偿；③ 能力字段即未来切换的开关，需要时再实现。
- **测试**：`TtyTerminalDec2027Test`（5 例：reset→启用+退出恢复 / 已 set / permanentlySet / NotRecognized / permanentlyReset）+ `EventParserCsiDecModeReportEventTest` 增 2027 解析例。
- 诊断：`tools/terminal-capability` 反射打印会自动多出 `graphemeClustering` 一行。

### L6.1 移除 `Tty.Callback.onKey/onMouse` 死回调（`8c489971`）
- **决策**：`Callback.onKey/onMouse` 判定为死回调并**移除**（todo §9.4 P1）。依据：C 头 `MosaicTtyCallbackOnKey/OnMouse` 为 `// TODO params` 占位、从未被调用；Windows C 层把 `KEY_EVENT`/`MOUSE_EVENT` 经 `ReadFile` 转成 VT 转义序列走共享 `EventParser`（回调仅用于 `FOCUS_EVENT`/`WINDOW_BUFFER_SIZE_EVENT`）；唯一实现 `EventParserTtyCallback` 也仅是空实现。实现它们只会建第二条重复分发通道。
- **改动（8 文件）**：`mosaic-tty.h` 删 2 typedef + 2 struct 字段（`MosaicTtyCallback` 5→3 字段）；`Tty.kt` 四份接口声明与 `staticCFunction` 分配（commonMain/jvmMain/nativeMain/jvmJdk22）；`com_jakewharton_mosaic_tty_Jni.c` 删 jmethodID 查找 + `invokeOnKey/Mouse` + 字段赋值；`TtyTest.kt` 删 `@Ignore` 遗留测试；`EventParserTtyCallback.kt` 删空实现。
- **构建踩坑（重要）**：cklib 的 **C 增量编译不跟踪头文件变化**——改 `mosaic-tty.h` 后若不 `:mosaic-tty:clean` 重编，旧/新 struct 布局偏移错位会导致 `onResize` 回调崩溃（表现为 native `resizeCallback` 测试进程异常退出）。改 C 头后必须 clean 重编。
- **环境备注**：Gradle daemon **未配置代理**，`zigDownload`（Zig 0.15.1，JNI 交叉编译工具链）直连 `ziglang.org` 在墙内被卡；已手动走代理下载到 `mosaic-tty/build/zig`（`zig` 可执行文件）使 `zigDownload` up-to-date，此后构建正常。`mosaic-tty:clean` 会清掉它，需重新放置或重新下载。
- **验证**：`mosaic-tty`/`mosaic-tty-terminal`/`mosaic-runtime` 的 jvmTest + linuxX64Test 全绿；`mosaic-terminal`/`mosaic-testing`/`mosaic-animation`/samples jvmTest 全绿；FFM(jdk22)/cinterop/JNI(Zig) 三套绑定从头文件再生成并编译通过。

### L5.6 特殊键/修饰核对（`56a55c3e`）
- **核对结论**：PUA 特殊键映射完整（方向/Insert/Delete/Page/Home/End/F1~F35），未映射码点回落到 Unicode 字符串而非发半真键名；`text` 关联文本——CSI u 无文本段时为 `null`，`toKeyEventOrNull` 的 `text ?: codepoint映射` 链无冲突（箭头等特殊键 `text` 恒 null 才走码点映射）。
- **行为修复**：`compat.kt` `toKeyEventOrNull` 由「仅 press」改为「press + repeat 投递、release 丢弃」。动机：kitty 协议开启 `reportEventTypes` 后长按只发 `press+repeat+release`，原实现丢 repeat 使长按只出单字符，与普通终端（legacy 原始字节自动重复）行为不一致；repeat 并入 press 恢复一致性。
- **测试**：`CompatTest`（新）8 条——legacy 控制键命名（Tab/Enter/Escape/Backspace）、可打印与 CJK 码点、PUA 特殊键映射（含 F17=57380）、repeat 投递、release 丢弃、关联文本优先、修饰键透传、未知码点回落。
- 无公开 API 变化（全部 internal）。

### L5.1 鼠标消费（`bccb50d9`）
- `layout/MouseEvent.kt`：runtime `MouseEvent`（Type/Button 枚举 + 修饰键）+ `Modifier.onMouseEvent`（返回 Boolean 消费）；`toRuntimeMouseEvent()` 把 terminal `MouseEvent` 转型。
- `layout/Node.kt`：`MouseLayer` 进层链；`MosaicNode.sendMouseEvent` 做**绝对坐标命中测试**（越过界直接 false；子节点**逆序**优先 = z 序在上先收，再回退自身 modifier）；`BottomLayer` 终结。
- `mosaic.kt`：`runMosaic*` 增 `mouse: Boolean = false`；帧监听循环分发 terminal `MouseEvent→runtime`。顺带把 `BottomLayer.sendKeyEvent` 挪到 doMeasure 前。
- `mosaic-tty-terminal`：`mouse` opt-in → 同步写 `mouseEnable`（SGR 1006 + press/release 1000 + **all-motion 1003**），teardown 写 `mouseDisable`；无回复不干扰能力探测。
- 修复：`runMosaicMain(content, mouse)` 曾把 content 放前导致尾随 lambda 绑定到 Boolean（demo/robot 编译失败）→ 改为 `(mouse, paste, content)`，content 恒在最后。

### L5.2 粘贴通路（`bccb50d9`）
- `layout/PasteEvent.kt`：`Modifier.onPaste { text -> Boolean }`，**树序向下分发**（祖先先于子，首个消费即止）。
- `mosaic.kt` 帧监听循环：`BracketedPasteEvent(start=true)` 起缓冲 StringBuilder，期间 `KeyboardEvent` 只累积 `text`（**不当作按键分发**、不判 Ctrl-C），`start=false` 时把整串经 `rootNode.sendPasteEvent` 分发。
- 终端：`paste: Boolean = false` opt-in → DECSET 2004；`TtyTerminal` 引导期同步写、teardown 写 disable。
- 修 bug：`PasteModifierElement` 属性与成员同名 `onPaste`，函数名优先解析 → 自递归栈溢出；改字段为 `handler`。
- `TestMosaic` 增 `sendPaste(text)`：发 start 标记 + 逐码点 KeyboardEvent（UTF-16 安全拆代理对）+ end 标记。

### L5.3 kitty 键盘协议开关（`bccb50d9`）
- `ansi.kt`：`kittyKeyboardFlags = 23`（消歧义转义 1 | 事件类型 2 | 备用键 4 | 关联文本 16，**不含**「所有键转义」bit8 → 普通字符仍原始字节上报）；`kittyKeyboardPush = "CSI>23u"`、`kittyKeyboardPop = "CSI<23u"`。
- `TtyTerminal.kt`：能力探测阶段收到 `KittyKeyboardQueryEvent`（`CSI?u` 回复）即置 `kittyKeyboard` 并**同步 push** + `toggleKittyKeyboard`；teardown 一律 pop 恢复终端原状态（push/pop 栈语义，不泄漏到 shell）。
- 运行时无改动：`toKeyEventOrNull` 只放行 press，repeat/release 解析后自然过滤，既有应用行为不变。

### L3.8 删除旧全量渲染器（`282e865b`）
- `rendering.kt` 删除 `AnsiRendering` 类（全量 `cursorUp` + 逐行重写协议），`AnsiIncrementalRendering` 为唯一增量渲染器。
- 删除 `AnsiRenderingTest.kt`（14 条：首帧/清尾行/static 排序/无尾随空格/宽文本等）。其中关键行为（首帧、清旧行、宽簇正确渲染）已由 `AnsiIncrementalRenderingTest`（14 条）覆盖，无覆盖缺口。
- `MosaicTest.frameTimeChanges` 改用 `AnsiIncrementalRendering`（渲染器对该测试无行为影响）。

### L4.1 Text 簇级安全软换行（`18428264`）
- `TextLayout.measure(maxWidth)`：把入向约束纳入排版；`value.splitByLines()` 分段后，贪心按**字素簇边界**累计换行（`forEachGraphemeClusterRange` + `graphemeClusterWidth`），簇宽度与绘制/测量同一口径。
  - 簇完整不拆：ZWJ emoji 家庭、组合字符、变体选择符、区域旗帜永远整簇落入一行；超宽簇独占一行（允许溢出）。
  - 空段落保持（`\n\n`/前导/尾随换行不丢行数）；单段能放下时原样返回（对既有 `split("\n")` 行为零回归）。
  - `lastMaxWidth` 缓存 + `dirty`：值或约束任一变化即重排，父宽变化不会用到陈旧行。
- `ui/Layout.kt`：内部无内容 `Layout` 的 `NoContentMeasurePolicy` 增加 `constraints` 参数并透传，`Text` 借此读到父级 `maxWidth`。
- `ui/Text.kt`：两个 `Text` 重载增加 `softWrap: Boolean = false`（默认保持原行为不强启换行），开启时按 `constraints.maxWidth` 软换行；`Constraints.Infinity` 视为无约束。
- 测试：`TextLayoutWrapTest`（ASCII/CJK/空段落/ZWJ 家庭/组合字符/旗帜/超宽簇/AnnotatedString 样式保留）+ `TextWrapRenderingTest`（`Modifier.width()+softWrap` 的端到端快照，含默认不换行回归断言）。

### L4.2 Text `maxLines` / `overflow`（`68d19e72`）
- `ui/TextOverflow.kt`：新增 `TextOverflow.Clip` / `TextOverflow.Ellipsis` 公开枚举。
- `TextLayout.measure(maxWidth, maxLines, overflow)`：换行后若行数超过 `maxLines`，`Clip` 取前 N 行整行；`Ellipsis` 用 `clipTo(maxWidth-1)+"…"` 截断可见末行——`clipTo` 逐簇累计（整簇保留或整簇丢弃，绝不半簇）；无宽度预算（`Int.MAX_VALUE`）时退化为 Clip。
- `lastMaxLines` / `lastOverflow` 纳入重排判定：任一变即重排。
- `ui/Text.kt`：两个重载增加 `maxLines: Int = Int.MAX_VALUE`、`overflow: TextOverflow = TextOverflow.Clip`（Compose 同默认，默认不改变既有输出）。
- 测试：`ellipsisKeepsOverflowingClusterWhole`（ZWJ 家庭整簇保留 + 省略号）、`ellipsisDropsWholeClusterWhenItCannotFit`（超宽整簇丢弃）、Clip/无宽度回落/ASCII-CJK 截断；渲染端 `maxLinesClipsRemainingLines`、`maxLinesWithEllipsisTruncatesLastLine` 快照。

### L1.1 字素簇分割（`9c0e0724`）
- `text/grapheme/GraphemeBreak.kt`：UAX #29 边界判定；`GraphemeBreakTables.kt`：生成用区间表。
- `text/grapheme/Graphemes.kt`：簇迭代/区间 API（`forEachGraphemeClusterRange` 等）。
- 官方套件：`GraphemeBreakTest.txt`（1121 行）+ `GraphemeBreakOfficialTest.kt`（jvmTest）。
- 生成器：`scripts/grapheme.py`（联网下载需代理；**必须在临时目录运行**，下载产物落当前目录）。

### L1.2 簇级列宽（`cfa64a46`）
- `text/grapheme/GraphemeWidth.kt` + `strWidth(CharSequence, from, to)`：按扩展字素簇计量，取代逐码点累加，作为唯一权威口径。
- `TextLayout` 测量切换到簇级宽度；`text/unicodewidth/`（本地 vendored 宽度表，归属注释已中性化）被簇级层复用。

### L2.1 cell 模型升级（`a99e5100`）
- `TextPixel(text, width, isPlaceholder)` + `markAsContinuation()`：首格存整簇文本与显式列宽，宽字符续列用占位。
- 这是"簇字符串 + 显式列宽"载体的落点，为测/绘口径统一与部分覆盖清理打基础。

### L3.1 按簇绘制（`4c368769`）
- `DrawScope.drawText` 以 `graphemeClusterWidth` 逐簇推进：整簇写入首格、续格预留占位，surface 只发首格。

### L3.2 行级增量渲染（`ef1fe8e7`，默认渲染器）
- `AnsiIncrementalRendering`（`rendering.kt:82`）替换全量 `AnsiRendering` 成为默认（`mosaic.kt:62`，`MOSAIC_DEBUG_RENDERING=true` 走 `DebugRendering`）。
- 协议：
  - 同尺寸且无 static：只对变化行发 `CSI {line};1H` + `clearLine` + 行内容；无变化帧仅出同步包裹（近零字节）。
  - 为保跨帧不变量：任一增量帧有行变化时末尾追加 `CSI {height+1};1H`，把光标固定到"输出下方"，与全量帧末尾 `\r\n` 语义一致，避免下一帧全量重画 `cursorUp` 相对定位错行。
  - 高度变化 / static 文本存在或变化：退化为全量重画（`cursorUp` + 逐行 `clearLine` / static 用 `clearDisplay` + 高度变矮时清尾），沿用旧渲染器协议。
  - 性能：复用 `stringBuilder`；行字符串每帧以 `List<String>` 保存用于比对（**L3.3 起已被 cell 快照替代**）。

### L3.3 cell 级增量差分（`264f637d`）
- `AnsiIncrementalRendering` 由「行字符串比对」升级为「**cell 网格比对**」：保留上一帧 cell 快照（双缓冲复用数组 + `copyFrom` 原位拷贝，零分配），跳过未变行。
- 变化行逐列对比新旧 cell 收集变化区间，**扩展到整个字素簇边界**后 `CSI r;cH` + 片段渲染（`appendRowRange`：区间内空 cell 输出空格、样式相对默认态、段末带样式则复位），未变前缀/后缀绝不重发。
- 稀疏/稠密二择：变化格数*2 ≥ 行内容范围 → 整行 `goto;clearLine` 回退（避免删尾输出一长串空格）；否则就地分段时间片重画。
- 首帧/尺寸变化/static 存在或变化 → 仍走全量重画，协议不变；增量帧末尾同样把光标 park 到输出下方一行。

### L3.4 宽格 diff 右缘防护 + 覆盖回归（`55dfaae1`）
- 结论先行：L3.3 的「cell 网格比对 + 占位列身份区分 + 变化区间簇边界扩展 + 区间合并」结构上已保证**真实布局下的覆盖清理**——宽簇头被窄内容覆盖时，其续列（占位态）必然随 diff 进入变更区间，且窄→宽/宽→窄/相邻宽簇/整体换文本均无半格残留。调研中担忧的「覆盖残留」未能在真实 cell 转换中复现，故本步重点是**右缘防护**与**把保证固化为回归**。
- **右缘防护（唯一代码改动）**：`appendRowRange` 增加 `rowEnd`（行绝对右边界）参数，写入宽簇前判定 `columnIndex + width > rowEnd`，是则用**同风格空格**占满剩余列（不写簇文本），杜绝宽簇在末列导致终端换行/滚动。稠密整行（`appendRowTo`）与稀疏片段（`renderRowDiff`）两条路径统一走该写器，均传 `rowEnd`。
  - 既有 `wideClusterAtRowEndTrimsPlaceholder` / `ansiRenderingWrapsClusterOnce` 语义不变：宽簇**整簇放得下**时不触发防护、照常渲染。
- **虚拟终端 `VirtualTerminal`（commonTest 辅助）**：解析渲染器输出的 ANSI（`goto`/`clearLine`/`clearDisplay`/SGR 简化为是否非默认/按簇宽写文本），维护屏幕网格；宽簇同实例共享首列与续列，头列被覆盖而未清理续列时 `displayRow` 以 `◌` 暴露（`hasResidue()` 判半格残留）。
- **回归 `CellDiffRenderingTest`（15 条，绕过布局直接用 crafted surface + 假 Mosaic 驱动渲染器）**：宽→窄覆盖清续列、窄→宽（强制宽）整簇重写、宽行变窄行清除痕迹、相邻宽簇边界变化、宽簇右移一列、ZWJ 家庭整体替换、keycap 整体替换、宽簇纯样式变更、恰好贴右缘的宽簇保留、非首行右缘拦截、第二行宽簇变更、内容尾部删除、整行收缩清尾、真实 resize（宽度变窄）清旧列。
- 提交：`55dfaae1`；JVM + Native（linuxX64）全绿。

### L3.5 static 输出行级清理（`736dd9a9`）
- `AnsiIncrementalRendering` 全量重画分支（`sizeChanged || staticChanged || static != null`）弃用一键 `clearDisplay`，改为**逐行 `clearLine`**：
  - `cursorUp(prevHeight)` 相对当前光标上移（static 输出可能已使屏幕滚动，用相对定位而非绝对坐标）。
  - static 各行先 `clearLine` 再重写，并计入被替换行数 `rowsReplaced`；随后 dynamic 行同样先 `clearLine` 再写。
  - 变矮后剩余的旧行不再 `clearDisplay` 整屏，而是 `while (rowsReplaced < prevHeight)` 逐行 `clearLine + \r\n`，避免清掉屏幕上无关内容。
  - 语义不变型：static 行数恰好铺满旧区（无 leftover）时输出与旧协议一致（`staticFramesRedrawFromTop` 等）。
- 测试：`AnsiIncrementalRenderingTest` 累计 16 条。改动既有 4 条（`shorterFrameClearsOldLines`/`staticFramesRedrawFromTop`/`staticWithHeightChangeClearsWholeArea` 由 `clearDisplay` 改为逐行 `clearLine`，补尾随空行），新增 2 条：`multiLineStaticClearsItsLinesIndividually`（多行 static 逐行清理）、`staticAndShorterDynamicClearLeftoverRowsPerLine`（static + 变矮 dynamic 的 leftover 行逐行清理）。


### L3.6 无同步渲染时的闪烁防护（`e1e17349`）
- `AnsiIncrementalRendering.render` 拆出统一帧包裹逻辑：有同步输出能力（DEC 2026）时保持 `enable … disable` 包整帧；无该能力且 `cursorVisibility`（可切换光标）时改用**光标 hide/show 包裹**：帧首 `CSI ? 25 l`（hide）、帧尾 `CSI ? 25 h`（show），终端实时重绘时光标跨行移动不再叠加闪烁。
- 空帧处理：本次无任何输出（同内容帧）时不发 hide/show（`setLength(frameStart)` 回滚已插的 hide），避免空包裹导致光标闪。
- 无光标切换能力的终端（`cursorVisibility=false`）不发包裹，退化为裸帧；该分支也天然绕过「启动时 hide 一次 + 每帧 show」的策略差异，行为保持最小变更。
- 新增常量：`mosaic-runtime/ansi.kt` 的 `cursorMode=25`/`hideCursor`/`showCursor`。启动期 `cursorDisable` 单次隐藏、退出恢复的既有逻辑不动。


### L3.7 输出批量/分片写（`70b4067b`）
- 引入 `FrameWriter`（`mosaic-runtime/FrameWriter.kt`）：渲染结果 `CharSequence`（复用 StringBuilder 载体）→ **增量 UTF-8 编码进固定 8KB 缓冲 → 分块写出**，取代原 `print(rendering.render(...).toString())` 的每帧整帧 String 拷贝 + 单次巨型写。
- 编码按**码点原子落块**：读 ASCII/代理对判码点后整体编码，装不进缓冲就先 flush 再写，保证多字节码点永不被分到两个块（无半字符）；孤立代理替换为 `?`，与 `String.encodeToByteArray` 行为一致。
- 写口 expect/actual：common 声明 `writeScreenBytes`/`flushScreenBytes`，JVM 走 `System.out.write(byte[],off,len)` + 每帧 `flush()`（保留实时上屏，且字节直通规避 `file.encoding` 问题）；Native 走 `platform.posix.write(STDOUT_FILENO, …)` 循环写（处理部分写），flush 为空实现。
- `mosaic.kt` 的 `onDraw` 改为 `frameWriter.write(rendering.render(rootNode))`；`chunkSize` 与两个写出 sink 可注入，便于纯 commonTest 断言字节流。
- 测试 `FrameWriterTest`（9 条）：ASCII 单块 / 空帧零写但每帧 flush / 码点跨块原子性 / 多字节字符整块 defer / 代理对读前视 / 超块输入多块 / 孤立代理替换 / chunkSize<4 拒绝 / 跨帧缓冲复用。


### L4.3 Text `textAlign`（`94c37ff7`）
- `Text`（String 与 AnnotatedString 两个重载）新增 `textAlign: Alignment.Horizontal = Alignment.Start`，复用既有 `Alignment.Horizontal.align(size, space)` 做行内偏移，不引入新枚举。
- 语义：有**有界入向宽度**（`it.maxWidth != Infinity`，无论 `softWrap` 与否）且 `textAlign != Start` 时，文本框按对齐宽度撑开为 `max(width, alignmentWidth)`，每行按 `align(lineWidth, boxWidth)` 求列偏移后 `drawText(row, column, …)` 绘制；`Start` 或缺宽度时保持紧凑内容宽（零回归）。
- `TextLayout` 增加 `alignmentWidth` 属性（`measure` 新参数，纳入脏缓存键）：不可用（Infinity）→ 无对齐；超宽行（宽簇独占行）以 `coerceAtLeast(0)` 兜底不会画负列。
- 测试 `TextAlignRenderingTest`（7 条）：居中/右对齐于给定宽、Start 保持紧凑、无界宽对齐无效果、软换行行居中、居中对齐把盒子撑宽影响 Row 后续兄弟、AnnotatedString 对齐。


### L4.4 Text `scroll` 查看窗口（`1e99eb40`）
- `Text`（两个重载）新增 `scroll: Int = 0`：先完整排版（wrap 到 `maxWidth`），再以 `scroll` 选**首个可见行**，之后最多显示 `maxLines` 行窗口；随 `overflow` 决定窗口方式——`Clip` 取窗口，`Ellipsis` 在「窗口末尾仍有内容」时对可见末行加省略号。
- `scroll=0` 严格等价旧逻辑（同一分支），负值 clamp 到 0，`scroll` 超出末尾时 clamp 到最后一行使视图不空，`maxOfOrNull ?: 0` 兜底空结果；`scroll` 纳入 `TextLayout` 脏缓存键。
- 语义定调（与 Mosaic Text 高度自适应一致）：窗口行数 = min(剩余行, `maxLines`)，内容不满窗口时盒子不高/空——如需固定窗口配合外层高度约束。
- 测试：`TextLayoutWrapTest` 增 7 条（窗口偏移/超尾/无界行数/Ellipsis 跟随内容/换行+对齐数据不冲突/负值 clamp/滚动到末行不空）；`TextScrollRenderingTest` 3 条渲染快照（窗口、越界显示末段、Ellipsis）。

### L4.6 `BoxWithConstraints`（`92297096`）
- 移植 Compose 同名 API：`BoxWithConstraints(modifier, contentAlignment, propagateMinConstraints, content)`；`BoxWithConstraintsScope` 暴露 `constraints`/`minWidth`/`maxWidth`/`minHeight`/`maxHeight`/`hasBounded*`/`hasFixed*`（Int 像素）+ 复用 `BoxScope` 的 `align`/`matchParentSize`。
- 实现：**单槽位子组合**——`Composition(applier, parent)` + `rememberCompositionContext()`（`InternalComposeApi`），内容在**首次 measure 时**用传入约束组合，约束变化时 `setContent` 以新约束重组合（无首帧空跑）；子组合根仅作结构容器，测量策略**直接测量其孩子**，因此 `matchParentSize`/对齐与 `Box` 语义一致。
- 失效联动：子组合每次应用变更，通过 `version` snapshot 状态 +1；测量第一阶段读取该状态，使父布局 `needLayout` 被触发，重新排版。
- 边界：无界约束 → 按内容收敛（与 `Box` 一致）；`fillMaxSize`/`weight` 等外部 modifier 照常生效。
- 可见性调整：`Box` 的 `BoxScopeInstance`/`AlignModifier`/`BoxParentData`/`boxParentData`/`matchesParentSize`/`placeInBox` 由 private 放开为 internal（无行为变化）。
- 测试：`BoxWithConstraintsTest` 7 条（读 `maxWidth` 定宽、min/有界标志、2/3 比例、父尺寸变化重组合、matchParentSize+居中、无界收敛、状态驱动动态列数）。
### L4.7 布局缓存（`4af27661`）
- 动机：任何重组/布局态变更都会置 `needLayout` → 每帧全树重跑 `measureAndPlace`；把「相同 `Constraints` + 未失效」的测量缓存下来，只重测受影响路径，缓解高频重排与同构重布局。
- 实现：`MosaicNode.measure`（`layout/Node.kt`）以 `(Constraints, layoutVersion)` 为键缓存最近一次测量结果，命中时整子树跳过测量（place 每帧照常执行）；同版本/同约束下行程式测量 O(1)。`measureCount`/`cacheHitCount` 计数器供测试观测。
- 失效（`invalidateLayoutVersion()` 对节点及其**祖先链**冒泡递增版本）：
  1. **结构变化**：`setModifier`、`measurePolicy` setter、applier 增删移子节点（`MosaicNodeApplier` 开始维护 `MosaicNode.parent` 链）。
  2. **状态依赖**：测量 miss 时用 `Snapshot.observe` 把子树测量期读到的 snapshot 状态收进 `measureDependencies`；`MosaicComposition` 的 apply 观察器执行 `invalidateLayoutCaches` 整树扫，命中节点的依赖即冒泡失效。
- 保留整树 `readStatesOnLayout` 观察，专门覆盖 **placement 阶段**才读的状态（如 `offset {}` 的 changeable offset）：此类变化仍需触发 layout pass 以重算位置，但测量全部命中。
- 与 Compose 对比：思想一致（constraints+脏标记 ≈ version、测量期收依赖、结构失效）；工程差异在失效定位——Compose 用 `SnapshotStateObserver` 的「状态→节点」反向索引近似 O(1) 唤醒，并裁剪「不影响父尺寸/未放置」子树；本项目当前为整树扫 + 祖先链全 bump。正确性等价，性能余量在 apply 查扫与子树裁剪（后续优化项，可参考反向索引方案）。
- 测试：`LayoutCacheTest` 4 条（单子树测量态变化不重测兄弟、插入兄弟不重测既有子、placement-only 变化跳过测量、约束变化强制重测）。
- 验证：JVM 520/0、linuxX64 519/0，`samples` 的 Linux 编译通过。


### L7.20 边框 / 分隔绘制原语（`layout/Border.kt`，`392b7efa`）
- **定位**：落地 todo §15「边框 / 分隔绘制」——最外层组合组件系列的又一块；依赖 L4 布局 + 绘制原语（DrawScope/drawBehind）。
- **`Modifier.border(width=1, color=Unspecified)`**：同一 element 同时实现 `LayoutModifier` + `DrawModifier`（Node 折叠时各建 `LayoutLayer` + `DrawLayer`，绘制在内容之前——边框在底层）。
  - 测量：内容按 `constraints.offset(-2w, -2w)` 内凹后加回，`box = constrain(content+2w)`，内容 `place(width, width)`——内容被边框环包围，边框不占内容空间（符合 Compose border 语义）。
  - 绘制：四个角 `┌┐└┘` + 上/下边条 `─`（从 (1,0)/(1,boxH-w) 起，`run=boxW-2` 宽、`w` 行）+ 左/右边条 `│`（`span=boxH-2` 高、`w` 列）——`w>1` 时边条跨整行/整列实心环；`w=0` 不画、测量尺寸不增。全部 `drawRect(Fill, foreground=color)`，`Color.Unspecified` 不打前景色（终端默认）。
  - 陷阱已修：`ContentDrawScope.draw()` 内 `this.width` 是 **DrawScope 节点宽**，会遮蔽 `BorderModifier.width`（边框厚度）成员——必须 `this@BorderModifier.width` 取厚度，否则 width=2 会把边条画成满屏。
- **`HorizontalDivider` / `VerticalDivider`（`thickness=1`、`color`、`startIndent/endIndent=0`、`modifier`）**：`Layout` + `drawBehind` 叶子。
  - 测量：主向占满（`hasBoundedWidth ? maxWidth : minWidth`；竖同理按高度），次向 `coerceIn` 到 `thickness`；未知主向退 min（配合 fillMaxWidth 语义）。
  - 绘制：铺 `─`（横）/ `│`（竖）矩形，左/上留 `startIndent`、右/下留 `endIndent`，都 `coerceAtLeast(0)`。
- **测试 `BorderTest`（16 条，commonTest）**：内容内凹 + 环线精确帧、单字符、`border(0)` 不画且尺寸不变、`border(2)` 边条实心、`fillMaxWidth` 整行边框、与 `padding` 组合（外 padding 顶/底各多 1 空行）、彩色边框输出 TRUECOLOR 前景 SGR、`Unspecified` 无前景 SGR、边框内 `clickable` 仍可点（界内触发/界外不触发）、横/竖分隔线厚度与缩进、负参数 `require` 抛 `IllegalArgumentException`（border 走非组合路径、divider 组合期 setContent 抛）。
- **showcase 第 9 页 Border**：`Page.Border` + `1`-`9` 键切换 + `BorderScreen`（边框变体一行并列 / 边框+内边距 / 嵌套边框 / 横分隔线含缩进 / 竖分隔线含缩进）。
- 验证：runtime jvm 699 / native 697，showcase jvm/native 各 9，apiCheck 通过，kexe 已重链。



### L7.21 动画容器（`mosaic-animation/EnterExit.kt|AnimatedVisibility.kt|Crossfade.kt`，`a35bfe01`）
- **定位**：落地 todo §15「动画容器」——`mosaic-animation` 此前只有核心值动画（Transition/值动画/spring/tween），缺装配层容器。
- **终端约束 → 设计决策**：终端无 alpha 合成 / graphicsLayer / scale，故：
  - `Crossfade` 不用淡入淡出，改「**水平推进**」：同 Layout 叠层、容器 `clipToBounds()`，旧状态层 0→-W 滑出，新状态层 +W→0 滑入（W=该层测量宽度）；每层 `createDeferredAnimation(IntOffset)` + 布局修饰器在 measure 读 `.value`（挂在 runtime 布局阶段的 `Snapshot.observe` 下 → 动画态每帧重排重绘）。
  - `AnimatedVisibility` 用 expand/shrink（`IntSize` 驱动布局尺寸→**影响兄弟布局**）+ slide（`IntOffset` 驱动内容位移）；fade/scale/veil 省略（无 alpha），`clipToBounds` 在尺寸动画期间裁剪溢出。
- **`EnterTransition` / `ExitTransition`**：sealed + `internal data: TransitionData(slide?, changeSize?)`；`+` 组合/优先级合并；`None` 常量。工厂：`expandIn/shrinkOut/expandVH/shrinkVH`（对齐映射 Horizontal→CenterStart/End/Center、Vertical→TopCenter/BottomCenter/Center）+ `slideIn/Out`、`slide*Horizontally/Vertically`（默认 -fullHeight/2）。KDoc 全部中性描述，无任何第三方名。
- **`AnimatedVisibility` 装配**：`updateTransition(visible)` → `createChildTransition`（`targetEnterExit`：从未可见→PreEnter，曾可见→PostExit）→ `AnimatedEnterExitImpl` 在 `visible(current)||visible(target)` 时组合单内容 `Layout`（`AnimatedEnterExitMeasurePolicy`：取子项最大 w/h、TopStart 对齐）+ `createModifier`（trackActiveEnter/Exit 保持被中断的过渡引用；按需建 size/slide DeferredAnimation）。
  - **退出后再移除**：Compose 有 `exitFinished` 便捷层；本仓库无——自建 `Transition<EnterExitState>.exitFinished`（current==PostExit && target==PostExit）+ `produceState { snapshotFlow { exitFinished }.collect }` 在退出完成**后一帧**才把内容从树里移除。
  - `createModifier`/`trackActiveEnter` 参照上游算法（无源码引用，重新实现）。
- **`Crossfade` 装配**：`currentlyVisible` 状态列表 + `contentMap`；`current==target` 时收敛回单层；target 变化时按 `contentKey` 去重重建层表（`entering = state==target` 决定滑入方向），`key(contentKey(it))` 独立槽位。
- **测试（commonTest，新增 mosaic-testing 驱动）**：`CrossfadeTest`（5）+ `AnimatedVisibilityTest`（6）——经 `runMosaicTest`+`awaitSnapshot` 逐帧推帧；关键经验：本 runtime **空闲即超时**（`awaitSnapshot` 1s 虚拟超时 = 「已稳定」），`pump()` 捕获 `TimeoutCancellationException` 判稳定；expandVertically 单行内容在单元粒度下会「先空后显」（tween 前 ~73% 高度仍取整为 0），断言用多行内容观察中间帧；Cell 粒度下中间帧是「新旧字符同排混合」而非双串共存。
- **showcase**：Animation 页增「4. 页面推进（Crossfade，1.5s/页）」+「5. 展开/收起（AnimatedVisibility，2s 周期）」；`samples/showcase/build.gradle` 增 `mosaicAnimation` 依赖；`ShowcaseTest` 增 1 条冒烟。
- 验证：`mosaic-animation` jvmTest / linuxX64Test 13 各全绿；showcase jvmTest 全绿并按既有流程重链 `bin/linuxX64/showcase.kexe`；`apiDump`（.api/.klib.api）已生成新公共 API。


### L7.22 TtyTerminal 关停竞态修复（`TtyTerminal.kt`，`21291c1c`）
- **现象**：native kexe 偶发退出报 `Uncaught Kotlin exception: com.jakewharton.mosaic.tty.IOException: 9` 后 SIGABRT；栈 = `EventParser#next()` → `Tty.read` 抛 EBADF。
- **根因**：`withTerminal` 退出顺序是 `terminal.use{}`（`close()` → teardown + cancel interrupt job + **一次** interruptRead 唤醒）紧接着 `tty.use{}` 的 `tty.close()` 释放底层 fd（tty fd + 中断管道）。读取输入的事件协程（`scope.launch(Dispatchers.IO)`，阻塞在 `select()/read()`）与 fd 释放**无同步**：fd 已释放后 read 才执行 → `mosaic_utils_read` 的 `select()`/`read()` 返回 EBADF → native `Tty.read` 抛 `IOException("9")`，读取循环无捕获 → 异常逃逸 `coroutineScope` → 未捕获 → SIGABRT；更糟时序 fd 号被复用 → read 永久挂起（压力测试复现：400 次退出卡死）。
- **修复**：把「唤醒读取协程」从一次性 interrupt 升级为**等它真正跑完**——`close()` 在 teardown 后 `runBlocking { withTimeoutOrNull(1s) { while (!readerJob.isCompleted) { runCatching{ tty.interruptRead() }; delay(1) } } }`；超时再 `readerJob.cancel()` + 最后一次 interruptRead 兜底（能解除卡在 send 的协程、或再次唤醒阻塞在原生 read 的线程）。由此 `tty.close()` 释放 fd 时读取线程已彻底离开描述符，EBADF 崩溃与 fd 复用挂起都不可能。
- **兜底（防御层）**：读取循环对 `parser.next()` 的 `IOException`（终端关闭/hangup EIO 等）按「输入已终结」静默 break；interrupt job finally 的 `interruptRead()` 加 `runCatching`，防止个别关闭路径把正常取消变成异常。
- **测试**：`TtyTerminalLifecycleTest` 新增 `shutdownNeverRacesFdFree`（200 次精确复刻 withTerminal 退出时序 `asTerminalIn→close→tty.close`，制造大量线程交错；修复前必现崩溃/挂起，修复后稳定全绿）。
- **验证**：`:mosaic-tty` / `:mosaic-tty-terminal` 的 `linuxX64Test` 与 `jvmTest` 全绿；`mosaic-runtime` native 编译通过；showcase release kexe 重链后在 PTY 多次 Ctrl-C 退出均干净（exit 0），不再出现该栈。


### L7.23 首帧全屏清屏接管（`ansi.kt` / `rendering.kt`）
- **现象**：真实 TTY 下多个示例启动后与 shell 残留重叠——「进了 raw 却没清屏」。单元测试在内存虚拟终端里跑不出（虚拟终端初始即空屏、光标在左上，真实终端光标通常停在 shell 提示符之后）。
- **根因**：首帧全量重绘从当前光标位置起逐行绘制，位置不保证在左上；且原 `clearDisplay = CSI J`（ED 0）只清「光标以下」，清不掉上方残留。
- **修复**：`ansi.kt` `clearDisplay` 改为 `CSI 2 J`（ED 2 全清）；`rendering.kt` 首帧在绘图前追加 `clearDisplay + CSI H`（全清 + 光标归位左上），此后帧继续走 cell 级差分，坐标以「已接管整屏」为前提不变。
- **测试**：6 个渲染测试首帧预期统一补 `|${clearDisplay}${CSI}H` 前缀（`AnsiIncrementalRenderingTest`19 / `TextScrollRenderingTest`3 / `TextWrapRenderingTest`4 / `TextAlignRenderingTest`7 / `SurfaceReuseTest`1 / `IsoControlRenderingTest`1，前缀与首行内容同排，`visible()` helper 剥离）。无新增断言。
- **验证**：`:mosaic-runtime` jvmTest（本次复跑 4s 全绿）与 linuxX64Test 全绿；counter kexe 重链后首帧输出带 `[2J[H`，退出后 `icanon echo` 正常恢复。
- **示例程序复查**：用户报的「切换 Tab 布局错乱」为旧产物未重链所致（18:18 旧 kexe）。新二进制（11:17）在多尺寸（80/140/150/160/200 列）、sync 开/关、慢速与 150ms 快速连按矩阵下，原始字节流经仓库 `VirtualTerminal` 与 xterm 系终端模拟器回放，所有页（Main/Cmd/Stat/Slow/Raw）最终画面均对齐，无残留错位。


### L7.24 `weight(fill)` 槽位语义核对（`ui/RowColumnMeasurementHelper.kt` / `WeightFillSemanticsTest.kt`）
- **现象（L7.18 顺带发现）**：`weight(1f)` 平均分配剩余主轴时，加权子项仍按**内容宽**报告与放置——探针槽位 80/8=10 而节点宽=标签宽 4，按内容宽顺序排布不透槽。等宽列布局塌缩、整槽点击/均分的可点击区域与视觉都错。
- **根因**：`RowColumnMeasurementHelper.measureWithoutPlacing` 对加权子项用 `placeable.mainAxisSize()`（实测内容尺寸）记账与排布；本仓库的 `Text` 不尊重 min 约束（`width()` 是首选尺寸），故 `weight(fill=true)` 时子项实测=内容宽，行/列尺寸与兄弟坐标全按内容宽累计。
- **为何不做框架级修复（已试并回退）**：曾在 `MosaicNodeLayer.measure` 把 `coercibleWidth/Height` 收敛到 `measured.coerceIn(min,max)`——7 条既有回归失败（TextWrap 溢出被裁、AspectRatio 必须能越约束报尺寸、intrinsic `requiredSize` 同理）。原因是本库「报告的 width」决定画布尺寸、而绘制/溢出按实测尺寸走，两者分离（对比参考实现中 `Placeable.width` 是 coerced 的，且其绘制同样用 coerced 后的值）；直接收敛会把「文本溢出依赖实测」的语义破坏。故改为**定点记账**。
- **修复**：新增 `childMainAxisSizes = IntArray(subSize)` 记录每个子项实际参与行/列尺寸与排布的尺寸——非加权用 `placeable.mainAxisSize()`；加权且 `fill=true` 用 granted slot（`childMainAxisSize`），`weightedSpace` 记账同步切换；`fill=false` 仍用 `placeable.mainAxisSize()`。`mainAxisPositions` 的 `arrange` 改吃这些「槽位尺寸」→ 兄弟按槽位背靠背摆放、Row/Column 总尺寸=槽位总和（`fillMaxWidth` 下撑满），子节点自身宽度保持内容宽（标签不被拉伸）。
- **测试**：`WeightFillSemanticsTest`（commonTest，5 条，见台账表）。要点：用 `Spacer(Modifier.width(6))` 而非 `Text(modifier=width(6))`——`width()` 是首选尺寸、Text 忽略 min 报实测宽，Spacer 在固定约束下才真占 6。
- **验证**：`:mosaic-runtime` jvmTest 全绿（既有 TextWrap/AspectRatio/intrinsic 与新增 5 条一并通过）。

### L7.25 intrinsic 测量缓存（`layout/Node.kt` / `IntrinsicCacheTest.kt`）
- **背景**：`min/maxIntrinsicWidth/Height` 已透传并可用，但每次查询都从查询点**重走整棵子树**（不参与布局缓存），同一「kind+参数、结构未变」的重复查询会重复计算。
- **实现**：`MosaicNode` 增单槽 intrinsic memo（与布局缓存 L4.7 同款 LRU-of-1 风格）——以（kind, 参数, `layoutVersion`）为键；命中直接返回，不重走子树。三个正确性关键点：
  ① **依赖记录**：重算包在 `Snapshot.observe(readObserver = measureDependencies::add)` 里 → intrinsic 读取的 snapshot 状态记入**该节点** `measureDependencies` → 状态变化照常走 `invalidateLayoutCaches` 失效，memo 不陈旧；
  ② **实际 measure 丢弃**：节点真实 measure（缓存未命中）时置 `cachedIntrinsicVersion = -1`——measure 的 `measureDependencies.clear()` 可能冲掉仅 intrinsic 读的状态，若不丢 memo 会留陈旧条目；
  ③ **门控共用**：memo 与布局缓存的失效完全同源（`layoutVersion` + `measureDependencies`），不新增失效路径。
- **已知取舍**：单槽只缓存最后一个（kind,参数）；min/max 交替同参仍各自重算（不互相驱逐即可命中，留多槽作为后续可选项）。
- **测试**：`IntrinsicCacheTest`（commonTest，3 条，用计数 `MeasurePolicy` 验证底层 intrinsic 实际调用次数）：
  ① 同参 3 连查 → 每轮父 measure 内只重算 1 次；② 不同参数（7 与 8）→ 各自重算（参数不能互替）；③ 状态变更不陈旧——子 policy 仅 `minIntrinsicWidth` 读 `mutableStateOf`（measure 不读），改值后 `awaitSnapshot` 行宽 10→20，隔离验证「memo 的依赖记录」而非 measure 路径兜底。
- **验证**：既有 `IntrinsicTest` 回归 + `mosaic-runtime` jvmTest / linuxX64Test 全绿（默认 configuration-cache 下 native 会因 cklib 位码任务序列化失败，native 用 `--no-configuration-cache` 且带代理）。

### L7.26 ANSI 16/256 颜色约简核对（`ansi.kt` / `AnsiColorReductionTest.kt`）
- **背景**：`toAnsi16Code`/`toAnsi256Code` 是移植自第三方参考的约简算法，todo §7 登记「核对与参考/终端行为一致性」，且 ansi.kt 里残留参考的归属 URL 注释（红线：项目内不得出现第三方名，需清理）。
- **核对方法**：用本地参考实施（仅开发期用，未进仓库）对一批向量解出权威预期值，与本实现逐点比对——16 色取整/亮色阈值、256 灰阶分段、6x6x6 cube 取整全部一致（实现即忠实移植）。
- **实现**：仅清理 ansi.kt 归属注释、改为中性语义说明（两个函数各一段 KDoc：16 色「最高通道 ≈100% 选亮集、过半通道翻转位」；256「等通道灰阶 + 彩色 cube 每通道 `/255*5` 取整」）。无行为改动。
- **测试**：`AnsiColorReductionTest`（commonTest，4 条，~60 向量金表）锁定语义：16 色黑白/亮暗/半强度翻转（127→30、128→37、253→31、254→91）/最高通道 30% 特判；256 灰阶端点 8→232、247→255、248→255、249→231 与内部采样；cube 纯色（255,0,0→196、0,0,255→21…）、取整临界（76→step1、77→step2；127→88、128→124；0,0,127→18、0,0,128→19）、跨通道二三级组合。
- **验证**：mosaic-runtime jvmTest 与 linuxX64Test 全绿。

### L7.27 端到端 golden（`EndToEndGoldenTest.kt`）
- **定位**：todo §13 P3「端到端 golden」。原本计划 script/pty 真终端快照，本仓库已有内存 `VirtualTerminal`（解释增量渲染器发的 ANSI、维护屏幕网格），直接在 `runMosaicTest` 里把每帧输出回放进去，断言**最终屏幕状态**——比单帧字节流断言更贴近「用户看到的」，且确定性强、可 native/JVM 双跑。
- **harness**：`settleInto` 循环 `awaitSnapshot()` 帧并 `feed` 进 24×80 VirtualTerminal，直到屏幕不再变化；利用 harness「空闲即 1s 虚拟超时 = 已稳定」的标准语义（捕获 `TimeoutCancellationException` 停止）。`Screen(rows, residue)`：rows = 每行右 trim 后去尾部空行，residue = `hasResidue()`（宽格半格残留检测）。
- **场景（4 条）**：
  ① 宽字符混排（中英/盒线字符）→ 屏幕=逻辑文本、无残留；
  ② weight 槽位列——`Box(width 80)` 内 `Row(fillMaxWidth)` + 1:2:1 weight → 槽 20/40/20、x=0/20/60，标签保持内容宽（端到端印证 L7.24）；
  ③ `maxLines=2` 裁剪窗口 → 只显示前两行；
  ④ 两帧增量 diff（"AAAA/BBBB"→"你好/CCCC"）→ 最终屏幕正确、无残留。
- **踩坑记录**：测试 harness 的**组合根以无界约束测量**（内容尺寸，非终端 80×24）——① `Row(fillMaxWidth)` 在无界下宽 0、`drawTo` 跳过 0 宽子树导致整行不画，需显式 `Box(width 80)` 给权重分空间；② `Modifier.width(10)`/`requiredWidth(10)` 在内容溢出时仍报内容宽（本库无 coerced-width 层），横向容器裁剪在无界根下无法体现，故裁剪场景改走 Text 级 `maxLines`。这些是运行时真实语义，非 harness 缺陷。
- **验证**：mosaic-runtime jvmTest 与 linuxX64Test 全绿。

### L7.28 README / CHANGELOG（发布文档）
- **定位**：todo §14 P2「README/CHANGELOG：能力矩阵与已知限制」。README 此前只有 Intro/Usage/Samples/FAQ，无能力与限制说明。
- **README**：`Jump to` 加 Capabilities；Samples 之后、FAQ 之前新增「Capabilities」段——Text & Unicode（字素簇度量/绘制、DEC 2027 探测启用与本地后备）、Rendering（cell 级增量 diff、同步输出包裹、snapshot 渲染器）、Layout（约束族/weight/fill 分数/BoxWithConstraints/布局缓存/Text 重载）、Interactivity（键盘+kitty 协议、鼠标、paste、OSC 52、焦点）、Animation（值动画+Crossfade/进出容器）；「Known limitations」——非 UTF-8 locale 不支持、须真 TTY（非 IDE/Gradle）、Windows/macOS 靠 CI、**组合根无界测量 → `fillMaxWidth` 需显式容器**（本会话 L7.27 实证）。
- **CHANGELOG**：`[Unreleased]` 的 New 列表补齐重构的用户可见能力（宽字符、增量渲染、Text 重载、比例布局、指针/paste/剪贴板/kitty 键盘、焦点、边框、动画容器），保留上游既有条目。
- **红线**：全部措辞中性，未新增任何第三方库/项目名；README 里上游既有的归属引用与调研文档已归入合并 main 前统一清理项。
- **验证**：仅文档（README/CHANGELOG），无代码/测试改动，不需跑测试集。


### L7.29 值动画/转换器族核对 + 单元测试（`mosaic-animation`，无生产代码改动）
- **定位**：todo §12 P3「API 完整性——跟踪上游 `Animatable/AnimateAsState` API 与内部 `AnimationVector` 表维护、评估新增 `animateColorAsState`（线性 RGB）等」。
- **核对结论**：`animate*AsState` 家族已齐全（Float/Int/IntOffset/IntSize/Color + 泛型 `animateValueAsState`）；终端目标无 Dp/Size/Offset/Rect，不存在缺失变体。`Color.ValueConverter` 已落地为 **Oklab**（感知均匀空间）——黑→白向量中点 (0.5,0,0) → sRGB≈(99,99,99)，而朴素 sRGB 直接 lerp 为 128、线性 RGB lerp 为 188；Oklab 中点更符合视觉均匀，优于 todo 原设想的「线性 RGB」，维持现状不改码。
- **测试经验（关键）**：`animateValueAsState` 返回的 `State` 的 `toString()` 是内部 `AnimationState(...)` 而非目标值——渲染/断言必须走 `state.value`（初始直接把 `Text(state.toString())` 会得到 AnimationState 字符串，解析全部错位）。测试钟按「值不变则补帧推进 timeNanos」步进，`pump(N)` 与真实秒数非线性对应；收敛断言用 `settle(fallback = frames.last())`——`pump` 已把动画推完时 settle 首帧即空闲超时返回 fallback，否则丢失最后快照。
- **测试**：`VectorConvertersTest`（6 条：Float/Int/IntOffset/IntSize 往返 + IntSize 负值钳 0 + Color 容差往返 + Oklab 中点带 90..108 精确断言）；`AnimateColorAsStateTest`（4 条：Color 端到端走 Oklab 中灰后精确收敛 + finishedListener 恰一次 / 中途重定向 / Float·Int 收敛）。mosaic-animation jvmTest 与 linuxX64Test 各 23 全绿，apiCheck 通过。

### L7.30 帧时钟集成核对（`mosaic-runtime`，审计 + 补 1 条测试）
- **定位**：todo §12 P3「帧时钟集成——动画走 BroadcastFrameClock；核对与 delay(1) 帧循环的节流/低电量行为」。
- **核对结论**：
  1. **动画确实走 BroadcastFrameClock**：`MosaicComposition` 在 composeContext 提供内部时钟 `internalClock`，`startFrameListener` 在每个 external 帧里 `internalClock.sendFrame(nanos)`，动画 `withFrameNanos`（`SuspendAnimation.kt`）解析到 `internalClock`；external 帧由外层 `runMosaicComposition` 循环 `clock.sendFrame(nanoTime())` 产生——动画时间 = 真实单调钟，两钟同源无漂移。
  2. **`delay(1)` ~1000fps 已被 L7.3 自适应节流取代**：`hasPendingFrameWork()`（含 `internalClock.hasAwaiters`）为真走 16ms（~60fps），否则 50ms（~20fps）；空闲帧不触发重绘（既有 `FrameThrottleTest`）。
  3. **低电量行为**：空闲 20fps 有界轮询 = 输入延迟 ≤50ms 的刻意取舍（每帧排空事件 + 忙碌判定一次 `sendFrame`，开销=纯唤醒无渲染）；进一步到零轮询需事件驱动全休眠（阻塞等待输入），登记为后续可选、不进本轮。
- **补测（闭合「空闲→动画」交接缺口）**：`animationStartingAfterIdleKeepsHighFrameRate`——先让聚合空闲 200ms（落 20fps）再启动 30 帧动画，断言 <1.4s（≈50ms 尾随空闲 tick + 490ms 60fps），若空闲循环未切回主动节流则会 ~1.55s，两档可区分。jvmTest / linuxX64Test 双跑通过。
- **验证**：mosaic-runtime jvmTest 与 linuxX64Test 全绿。

### L7.31 硬件光标 API（`ui/Cursor.kt` / `rendering.kt` / `ansi.kt`，`b96a8c64`）
- **定位**：todo §2 P3「光标 API」。此前只有可见性开关（启动探测 `CSI ?25$p` 后隐藏、退出恢复；无同步输出时逐帧 hide/show），无法把真实终端光标放到指定格、选形状/闪烁；`BasicTextField` 只能用反显色块模拟 caret。
- **API**：`Cursor(modifier, shape = Block, blink = true)` 是零尺寸布局标记——放在 `Row` 里 `Text` 之后即文本末尾，或用 `Modifier.offset` 选格；位置取 `onGloballyPositioned` 的根坐标，padding/对齐/滚动天然生效。`CursorShape` = Block/Underline/Bar，与 `blink` 组合映射 DECSCUSR 1..6。多光标同时放置时「最后放置者胜」，离开组合即释放。
- **管线**：`CursorState`（普通字段 + `needDraw` 回调）由 `MosaicComposition` 作为 `LocalCursor` 提供；`Cursor` 在 placement 写位置、`SideEffect` 写形状、`DisposableEffect` 释放。`draw()` 把请求挂到复用的 `TextSurface` 上，渲染器直接读取——公共 `Mosaic`/`Rendering` 接口与测试 `SnapshotStrategy` 均无需改动。
- **渲染**：帧尾发 `CUP(row,col)` + 形状（仅变化时）+ `CSI ?25h`。绘制会把光标带到最后写入位置，所以有输出的帧必须重定位；非同步路径的逐帧 hide/show 也要求每帧重新 show。越界（根之外）不显示；目标落在宽簇续列时向左吸附到簇头；请求消失时 `hide` + `CSI 0 SP q`；进程退出时若确实显示过光标，`runMosaicComposition` 的 `finally` 追加默认形状复位（可见性由 tty 层 teardown 恢复）。
- **限制（KDoc 已写明）**：静态日志行占据组合根上方的屏幕空间，未计入 CUP 偏移；嵌套滚动容器自身的裁剪不参与可见性判断，只按根边界判越界。
- **测试**：`CursorRenderingTest` 10 条（见台账表）。用 `VirtualTerminal` 回放断言光标落点，并用 `ByteCollector` 断言退出复位序列；JVM 726 / linuxX64 724 全绿；`apiDump` 产出 JVM `.api` + `.klib.api` 增量。
- **顺带发现（已修复，L7.46）**：当时记录「7 处既有 spotless 失败」，实际为 10 处 compose lint（runtime 8 + animation 1 + samples/bench 1）+ 全仓库格式漂移；L7.46 已逐条抑制/重排并 `spotlessApply`，`spotlessCheck` 全绿。

### L7.32 TTY 三绑定一致性（`mosaic-tty`，`65631043`）
- **背景**：JNI（`jvmMain`）/ FFM（`jvmJdk22`）/ cinterop（`nativeMain`）共享同一 C 库，但包装层各写各的：越界区间在 JNI 会直接落到 native 内存；关闭后调用未定义（可能空指针）；回调释放顺序不一致；FFM 关闭先关回调 arena 再释放原生；native `reset` 吞错；JNI/FFM 进入 raw mode 不装信号兜底。
- **契约测试（`TtyContractTest`，commonTest）**：越界 offset/count → `IndexOutOfBoundsException`；关闭后除 `reset()`/`close()`（幂等 no-op）外 → `IllegalStateException`；`read`/`write` 按 offset/count 精确工作；`readWithTimeout` 空闲/非阻塞返回 0、有数据返回计数；`interruptRead` 唤醒阻塞读并使下次读返回 0；0x00..0xFF 字节往返（Windows 控制台测试桩仅 ASCII，跳过该用例，注释说明）。
- **实现对齐**：`TtyArguments.checkReadWriteArguments` 三绑定共用；每绑定 `requireOpen()`；三绑定 `setCallback` 均先 native 摘除再释放旧回调；JNI 回调初始化失败路径补 `DeleteGlobalRef`/`free`；FFM 关闭顺序改为先 `mosaic_tty_free` 再关回调 arena；native `reset` 传播错误；JNI/FFM `enableRawMode` 后调用 `mosaic_tty_enable_signal_guard`（C 侧只接管仍为默认 disposition 的信号，JVM 自有处理器不受影响；Windows 为 no-op 桩）。
- **验证口径**：`mosaic-tty` JVM 116 / linuxX64 122（native 多 6 条 SignalGuardTest）；FFM 用 `installJvmTestDistribution` 在 JDK 25 跑完整 116 条——多版本 jar 的 `META-INF/versions/22` 生效（已用 `Tty.class` 资源路径确认）；`mosaic-tty-terminal` JVM 269 / linuxX64 268 回归；`:mosaic-tty:spotlessCheck` 通过。EINTR 由共享 C 层处理，未做信号注入。
- **顺带修复**：`TestTtyTest.stdinErrTtySettingFalse` 原本绑定 `stderrIsTty = true` 并断言 true（假阳性），改为 false/false。

### L7.33 `onGloballyPositioned` 内容未放置修复（`layout/OnGloballyPositioned.kt`，`b8cdc33e`）
- **现象**：给 LazyColumn 每一行加 `Modifier.onGloballyPositioned` 统计行号后，所有行都画到左上角、屏幕内容互相覆盖。
- **根因**：该 modifier 的 `measure` 自建 `layout(w,h) { ... }`，placement 块只计算并回调坐标，**没有 `placeable.place(0,0)`**。子内容与整棵子树保持未放置状态（x/y 默认 0），绘制从 canvas 原点开始，祖先给该节点分配的偏移完全不生效；其它 pass-through `LayoutModifier` 都会在 placement 块中放置子项。
- **修复**：placement 块先 `placeable.place(0, 0)`，再读取本层 `x/y` 回报（坐标语义不变）。
- **回归**：`contentRemainsAtItsPlacedPosition`（Row 中 `Text("----")` + 带 modifier 的 `Box { Text("X") }`，修复前实际 `X---`，修复后 `----X`）与 `lazyColumnRowsStayOnTheirRows`（4 行 LazyColumn 各就各位）。`OnGloballyPositionedTest` 7 条全绿。
- **验证**：mosaic-runtime JVM 728 / linuxX64 726 全绿；既有位置/尺寸/重复上报语义用例均回归通过。

### L7.34 备用屏生命周期（`TtyTerminal.kt` / `ansi.kt` / `terminal.kt` / `mosaic.kt`，`43c85bb1`）
- **动机**：终端应用此前需自写进出「备用屏」的转义序列；退出路径一旦漏写恢复序列，TUI 内容会留在主屏/scrollback 中。把备用屏纳入库的 TTY 生命周期后，正常关闭、取消、信号收尾都由同一份幂等 teardown 兜底。
- **公共 API**：`runMosaic` / `runMosaicBlocking` / `runMosaicMain` 均新增 `alternateScreen: Boolean = false`（`content` 仍为最后一个参数以保留尾随 lambda），默认 false 完全维持既有内联行为；KDoc 同步说明。参数经内部 `withTerminal` 透传到 `Tty.asTerminalIn`（同名默认参数）。`Terminal` 能力接口不变。
- **序列常量**：`ansi.kt` 新增 `alternateScreenMode = 1049` / `alternateScreenEnable`（`CSI ?1049h`）/ `alternateScreenDisable`（`CSI ?1049l`），业务代码不再散落字符串。
- **进入时序**：仅在真实绑定 `Tty` 时生效——`enableRawMode()` 成功后、任何能力探测/控制序列（首个 DA1 为 `CSI 0c`）之前写 enter；非交互策略（`Ignore` / `AssumeAndIgnore`）走 `NonInteractiveTerminal`，不会写入任何备用屏序列。
- **退出时序**：teardown 仍由 `teardownRan`（`AtomicBoolean`）保证只执行一次；顺序为 `setCallback(null)` → 恢复 theme/in-band resize/focus/cursor/mouse/paste/kitty keyboard/grapheme 等所有被 toggle 的模式 → 写 leave → `reset()`。`reset()` 只恢复 termios 与信号 disposition（含 SIGWINCH/崩溃兜底），不向终端写任何字节，因此放在 leave 之后是安全的；leave 是 teardown 的最后一笔终端输出，之后不再写（注释已写明）。正常 close、重复 close、作用域取消/信号触发的 finalization hook 都走这一条路径。
- **测试**：`TtyTerminalAlternateScreenTest`（3 条）——enter 严格早于首个探测输出且 close 时 leave；恢复 cursor 模式后 leave 且为最后输出（逐字节相等）；alternateScreen=false 时 setup/teardown 均不出现 1049。`TtyTerminalLifecycleTest` 增 2 条——close 两次只 leave 一次（按序列出现次数断言）；取消作用域（模拟信号/shutdown 的 finalization hook 路径，未调用 close）仍 leave。测试全部走现有 TestTerminal/PTY 桩。
- **验证**：`:mosaic-tty-terminal:jvmTest` JVM 274 全绿 / `:mosaic-tty-terminal:linuxX64Test` 273 全绿；`:mosaic-runtime:jvmTest` 728 全绿；`:mosaic-tty-terminal:apiDump` / `:mosaic-runtime:apiDump` 更新 JVM `.api` + `.klib.api`，随后两模块 `apiCheck` 通过。
- **已知限制**：① SIGSEGV/SIGILL/SIGPIPE 等硬崩溃只经过 C 级 async-safe 的 termios 恢复，不执行 Kotlin teardown，可能来不及写 leave（终端由进程退出语义兜底）；正常 SIGINT/SIGTERM 与协程取消会 leave。② `asTerminalIn` 若在写完 enter、尚未构造出 teardown 前的极端初始化失败路径上中止（如内存/原生回调分配失败），enter 没有配对的 leave；正常能力探测失败/超时不会中止。
- **Handoff**：调用方终端应用可删除自写的备用屏进出封装（如应用侧 `AlternateScreen`），改为 `runMosaicBlocking(alternateScreen = true, ...) { ... }`（或 `runMosaicMain` / `runMosaic` 同名参数），其余行为不变；未显式开启的应用保持默认内联渲染。

### L7.35 惯性滚动状态（`layout/MomentumScrollState.kt`，`8532ef1e`）
- **定位**：给需要“滚轮一格后继续滑行”的应用提供 opt-in 物理状态；库本身不启动协程或帧循环，由应用在自己的 frame loop 里推进。
- **模型**：`position`（行，snapshot state）+ `velocity`（行/秒）。`impulse(rows)` 把格数转为 `rows × friction` 的初速度，并按 `maxSpeed` 截断；`advance(dt, maxPosition)` 用 `position += velocity * dt` 积分，`velocity *= e^(-friction*dt)`，低于 `minSpeed` 或撞到 `0..maxPosition` 边界时清零。
- **默认参数**：`friction=3`、`maxSpeed=60`、`minSpeed=0.5`；`impulse(3)` 初速 9 行/秒，按指数衰减总位移约 3 行，与滚轮一格一致。`dt` 上限 50ms，避免卡顿帧瞬移。
- **语义修复**：`advance(dt=0)`（含负值）此前返回 `false` 但保留 velocity，调用方会误以为滑动结束；现只做 clamp 并返回“是否仍在运动”，仍在滑动的 fling 会在下一非零帧继续。
- **测试**：`MomentumScrollStateTest` 17 条——默认 3 行行程、初速上限/累积、`jumpTo` 清速、边界 clamp、零/负 `maxPosition`、dt 上限、`dt=0` 保活、非法构造参数、snapshot 可观察、应用帧循环把 `position` 四舍五入映射到布局。
- **验证**：mosaic-runtime JVM 745 / linuxX64 743 全绿；`apiDump` + `apiCheck` 通过（新增公开类，`.api`/`.klib.api` 已更新）。

### L7.36 双击检测工具（`layout/ClickTracker.kt`，`284565b6`）
- **定位**：给直接消费鼠标事件（`onMouseEvent`）的行选中场景提供双击判定；纯 Kotlin 状态，不依赖框架、不启动定时器，也不新增 `Modifier.clickable`（应用仍自行把 `press` 的返回值映射到选择/激活）。
- **API**：`ClickTracker(doubleClickMillis: Long = 400L)`，构造时 `require(doubleClickMillis >= 0)`；`press(index, nowMillis)` 在同一下标且与上次按下的间隔落在 `0..doubleClickMillis`（含边界）时返回 true；每次调用都更新「上次下标/时间」，因此三连击会返回两次 true；`nowMillis` 回退时不匹配，并把该次按下作为新基准；`reset()` 清空基准，下一次 `press` 必为 false。间隔计算先判非负，顺带避免时间戳极端溢出误判。
- **测试**：`ClickTrackerTest` 6 条——同下标窗口内含边界（并验证窗口从上次按下起算）、不同下标、超出窗口、时钟回拨、`reset()`、负窗口拒绝。
- **验证**：`:mosaic-runtime:jvmTest --tests "com.jakewharton.mosaic.layout.ClickTrackerTest"` 6/6 通过；`:mosaic-runtime:apiDump` 更新 JVM `.api` + `.klib.api` 后 `apiCheck` 通过；`:mosaic-runtime:compileKotlinLinuxX64` 通过；按时间盒要求**跳过** `linuxX64Test`。
- **Handoff**：应用在行内 `onMouseEvent` 收到按下时调用 `tracker.press(rowIndex, nowMillis)`，true 即双击；行内容/指针离开时 `reset()`。L7.37 已在此基础上为 `Modifier.clickable` 加入可选双击。

### L7.37 clickable 跨重组加固 + 可选双击（`layout/PointerInput.kt`，`a935b802`）
- **定位**：让 `clickable` 可用于按行选择的列表/表格——点击不再因按下后发生重组、节点实例替换或元素位于子组合中而丢失；同时把 L7.36 的双击能力接入声明式修饰符。
- **根因与复现**：旧释放判定同时依赖 `target.node.isAttachedTo(root)` 和原节点的**当前**边界。子组合（如 `BoxWithConstraints`）的根节点没有挂 `parent`，该判定恒为 false；先加 `clickableInsideBoxWithConstraintsClicks` 回归，修复前 `expected 1 but was 0`。另确认 applier 的 `remove` 不清理 `parent`，该判定对已移除节点反而可能为 true，不能作为存活依据。
- **修复**：按下时只捕获元素 `state` 与节点当时的边界（left/top/right/bottom），释放时检查 `state.enabled` 且坐标落在**按下边界**内，然后调用捕获的 state 回调；节点实例是否更换、父链是否还在都不再参与。`hoverable`/`clickable` 的 state 由 `DisposableEffect` 在组合移除时 `dispose()`（置 `enabled=false`），因此禁用或已移除的目标不会在释放时触发；越界/禁用/移除同时清空双击配对。
- **双击 API**：`clickable(enabled = true, onClickLabel: String? = null, onDoubleClick: (() -> Unit)? = null, onClick: () -> Unit)`（`onClick` 保持末位以支持尾随 lambda）。首个释放调用 `onClick`；同元素 400ms 内（`ClickTracker` 默认窗口，按两次释放时刻计）的第二个释放只调用 `onDoubleClick`；第三个快速释放重新起配对（`onClick`）。`onDoubleClick == null` 时每次释放都是 `onClick`。每元素独立 tracker，不同元素不配对；禁用/移除/越界会 `reset()`。
- **行为取舍**：命中区固定为按下瞬间边界，按住期间元素移动不会把释放改判到新位置；双击窗口固定 400ms，暂不暴露配置参数。
- **测试**：`PointerInputTest` 23 条（原 12 + 新增 11），覆盖节点替换（集成 + 直接 state/新节点）、子组合点击、按下后移除、按下后禁用、双击窗口内/超窗/三连击/不同元素、集成双击；`BoxWithConstraints` 用例修复前失败、修复后通过。
- **验证**：`:mosaic-runtime:jvmTest --tests "com.jakewharton.mosaic.layout.PointerInputTest"` 23/23 通过；`:mosaic-runtime:apiDump` 更新 JVM `.api` + `.klib.api` 后 `apiCheck` 通过；`:mosaic-runtime:compileKotlinLinuxX64` 通过；按时间盒要求**跳过** `linuxX64Test`。

### L7.38 标签栏组件 TabRow（`ui/TabRow.kt`，`d5bf3b19`）
- **定位**：替换应用私有的 `Row + hoverable + clickable + Text` 标签栏；组件只负责渲染与点击回调，选中状态、滚动、键盘导航仍由应用管理（无状态、可复用）。
- **API**：
  - `TabRowColors(container, selectedBackground, selectedContent, content, hoverBackground, divider)`：`@Immutable` 数据类，六项全部默认 `Color.Unspecified`（终端调色板中性）；`container` 只用于标签本身，行背景保持透明。
  - `TabRow(tabs: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, colors: TabRowColors = TabRowColors(), divider: String? = "│", trailing: (@Composable RowScope.() -> Unit)? = null)`。
- **实现**：`Row` 内逐标签输出可选分隔符 + `Text(" label ")`；`Modifier.clickable { onSelect(index) }`（已选中标签同样回调）与 `Modifier.hoverable` 复用既有指针原语，无手写命中测试；背景按「选中 > 悬停 > 容器」、内容色按「选中 > 普通」选择，分隔符只取 `colors.divider`。悬停态用单个 `hoveredIndex` 而非每标签 `remember`，避免标签增删/分隔符开关造成槽位漂移；`selectedIndex` 越界时不选中任何标签且不抛错。
- **尾部槽位**：`trailing` 以当前 `RowScope` 调用，可直接 `Spacer(Modifier.weight(1f)) + Text(...)` 占满剩余宽度并右对齐；组件不额外包裹 `Box`、不强制权重，避免 `RowScope` 与真实父级不一致。
- **测试**：`TabRowTest` 7 条——标签/分隔/尾部权重精确到 40 列（25 列 spacer + 4 列提示）、选中与非选中背景/内容色及分隔符色（TRUECOLOR SGR）、点击第二个标签回调 `1`、点击已选中标签仍回调 `0`、悬停非选中标签切换背景且离开恢复容器色、`divider = null` 相邻排布、空标签列表仅渲染 `trailing`（越界 `selectedIndex` 不崩）。
- **验证**：`:mosaic-runtime:jvmTest --tests "com.jakewharton.mosaic.ui.TabRowTest"` 7/7；`:mosaic-runtime:apiDump` 分步更新 JVM `.api` + `.klib.api` 后 `apiCheck` 通过；`:mosaic-runtime:compileKotlinLinuxX64` 通过；按时间盒要求**跳过** `linuxX64Test`，未跑全量 JVM 套件；`:mosaic-runtime:spotlessKotlinCheck` 仍有既有失败，新增两文件已按格式修正（未触碰无关文件）。

### L7.39 非模态浮层第一阶段 OverlayHost + Popup（`ui/OverlayHost.kt` / `ui/Popup.kt`，`19c291a1`）
- **定位**：落实 [dialog-overlay-design.md](dialog-overlay-design.md) 方案 C 的 P1（非模态可见闭环）。只做布局/绘制/命中与最小鼠标语义；modal 输入门控、焦点域、Esc/backdrop 明确留给 P2，P3 再做打磨与 golden。
- **API**：
  - `OverlayHost(overlay: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit)`。
  - `Popup(alignment: Alignment = Alignment.TopStart, offset: IntOffset = IntOffset.Zero, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit)`。
- **布局**：host 根是 `Box(modifier)`，先组合/测量 `content()`，再（仅 `overlay != null` 时）放一个 `Modifier.matchParentSize()` 的 `OverlaySlot` Layout。base 仍自行声明画布尺寸，host 不向 base 传无界约束，也不把 overlay 内容计入 host 尺寸；overlay 槽尺寸取 `constraints.minWidth/minHeight`（即 Box matchParentSize 给的 base 尺寸），base 0×0 时槽也是 0×0（0 尺寸层被绘制遍历跳过，不崩）。
- **层与放置**：overlay 槽每个直接子节点即一层。`Popup` 用 `OverlayLayerModifier`（内部 `ParentDataModifier`）携带 `alignment + offset`；槽先按内容尺寸测量各层（min 0），再按 `alignment.align(layerSize, baseSize) + offset` 放置。层 children 顺序即绘制顺序，后者覆盖前者；负 offset 允许越出 base（绘制/命中按实际节点边界）。
- **鼠标（P1）**：复用既有「children 反序命中 + 节点 bounds」派发——base 是先声明的 child，overlay 槽在后，因此层先命中、未命中再回落 base。`Popup` 层挂 `onMouseEvent { true }` 吞掉内容未消费的事件；另加一个 no-op `clickable`（内部注释说明），因为跨节点 `PointerState.preDispatch` 的 press/release 捕获不走 mouse dispatch，需要让 pointer 预检把 popup 视为按下目标，才能保证「点在 popup 内时 base clickable 不触发」。popup 外不挂 shield，事件正常落到 base（非模态点穿）。未改 `PointerInput.kt` / `Node.kt` 的派发算法。
- **单 host 约束**：`MosaicComposition` 提供 internal `LocalOverlayHostRegistry`；`OverlayHost` 在 `DisposableEffect` 中 claim/release，第二个或嵌套 host 抛 `IllegalStateException`（信息含正确用法）。替换 `setContent` 时旧 effect 先 dispose、新 host 再 claim，不会误报。
- **明确不做（P2/P3）**：键盘与粘贴仍按树序、base 可先消费（P2 根入口门控）；无 modal 屏障/backdrop/焦点域/焦点保存恢复/Tab 域/Esc 关闭（P2）；无 hover 屏蔽、按压捕获取消、关闭后 hover 重算、zIndex、动画/示例（P2/P3）。P1 的非模态 Popup 不移动焦点，内容里的 `BasicTextField` 需应用自行 `FocusRequester`。
- **测试**：`OverlayHostTest` 6 条——`overlay = null` 与裸 content 帧逐字符一致；host/槽/base 尺寸（slot=base、overlay 不撑大 host）；0×0 base → 0×0 槽 + 空帧；嵌套 host 抛错；第二个 host 抛错；键盘仍按树序（base 先拿到键，锁定 P1 行为）。`PopupTest` 5 条——base 上叠加 + TopStart offset 绘制/位置；BottomEnd + 负 offset；双层绘制顺序（`BBAA`）与命中取顶层、仅下层覆盖处下层接收；popup 内点击被 shield（base mouse/click 均 0）、popup 外回落 base（mouse 2 次 press+release、click 1 次）；popup 内容 clickable 先于 shield（内容 1 次、base 0）。
- **验证**：`:mosaic-runtime:jvmTest --tests "...OverlayHostTest" --tests "...PopupTest"` 11/11；`:mosaic-runtime:apiDump` 后 `apiCheck` 通过（JVM `.api` + `.klib.api`）；`:mosaic-runtime:compileKotlinLinuxX64` 通过；按时间盒**跳过** `linuxX64Test` 与全量 JVM 套件。构建环境补充：原生工具链下载任务（`:mosaic-tty:zigDownload`）在本机网络下极慢，已手工经代理把所需工具链预置到 `mosaic-tty/build/zig`，并以 `-x :mosaic-tty:zigDownload` 运行验证；未改仓库构建脚本。
- **限制**：① `PointerState` 的 hover/焦点命中仍遍历整棵树，指针停在 popup 上时 base 的 hover 仍可能 enter、按下 popup 仍可能把焦点给 base（P2 的候选 roots 一并修）；② 若 press 在 popup 外、release 在 popup 内，click 归属仍在按下时的 base 元素（P1 不做层变化时的按压重算）；③ 单 host 是「每个应用组合树（含子组合）」一次，`StaticEffect` 等子组合共享同一 registry；④ 只有带 `OverlayLayerModifier` 的层（当前仅 `Popup`）会按 alignment/offset 放置，自定义 overlay 直接子节点默认落 (0,0)。

### L7.40 弹窗/浮层 P2：模态输入门控 + Dialog（`layout/OverlayHostState.kt` / `ui/Dialog.kt` / `ui/OverlayBackdrop.kt`，`1149eb06`）
- **定位**：落地 [dialog-overlay-design.md](dialog-overlay-design.md) 方案 C 的 P2。P1 已解决「画上去、点得对」；P2 解决「modal 打开后 base 不再收到 key/mouse/paste/焦点」，并补 `Dialog`、`OverlayBackdrop`、Esc/backdrop 关闭。
- **新增公共 API**：
  - `Dialog(onDismissRequest, modifier, dismissOnBackdropClick = true, dismissOnBack = true, backdrop = OverlayBackdrop.Solid(), content: @Composable ColumnScope.() -> Unit)`。
  - `Popup(alignment, offset, modifier, modal = false, backdrop = OverlayBackdrop.None, onDismissRequest = null, content: @Composable ColumnScope.() -> Unit)`（P1 四个参数不变，新增参数全部有默认值）。
  - `OverlayBackdrop`：`None` / `Solid(background = Color.Unspecified)`。
- **层元数据与测量**：内部 `OverlayLayerParentData(alignment, offset, modal, fillParent)` 由 `OverlayLayerModifier`（`ParentDataModifier`）携带；槽对 `fillParent` 层用 `Constraints.fixed(baseW, baseH)` 测量并放 (0,0)，其余仍按内容尺寸 + alignment/offset。`Dialog` 与 `Popup(modal = true)` 都是 fillParent + modal；`Dialog` 内部先画 backdrop、再把内容按 `Alignment.Center` 放置；modal Popup 把内容按自身 alignment + offset 放在 base 内。
- **根入口门控**：`OverlayHostState` 同时实现 `LayoutModifier` 与内部 `OverlayHostAttach`，在 `MosaicNode.setModifier` 时拿到 host 节点，并在首次 measure 时把自身登记到组合根 `MosaicNode.overlayHost`。根 `sendKeyEvent`/`sendPasteEvent` 从后往前遍历 layer，未消费即遇到 modal 停止（不落 base），否则继续并最终到 base；`sendMouseEvent` 用 `rootsForMouse()`（顶层→…→最上层 modal，无 modal 时最后加 base）做 `PointerState.preDispatch` 与派发。没有 overlay 时完全走旧路径。
- **同步时序**：layer 结构来自子组合作用域，host 自身不一定会重组。`MosaicNodeApplier.onBeginChanges` 把 root 上的 host 置 dirty；`MosaicComposition.performLayout` 与根 key/mouse/paste 入口在读取层前调用 `syncLayers()`。层列表变化时取消已捕获的 press（不触发 click）并清空 hover；modal 数量变化时更新焦点栈。`MosaicNodeApplier.remove` 现在清空被移除节点的 `parent`，使「modal 关闭时恢复的焦点目标已从 base 移除」能正确判为失效。
- **Dialog 交互**：backdrop 是填充 base 的 `Box`（`overlayBackdrop` 绘制 + 可点击），内容外面另包一层 `onMouseEvent { true }` + no-op `clickable` + `hoverable` 的 shield；内容优先、内容未处理也不落到 backdrop。按下/释放在内容内外跨区时不算 backdrop click：backdrop 的 clickable 在释放时检查内容是否处于 hover（`hoverable` 在 pointer 预检里随 release 路径更新）。Esc 是 layer 根上的 post `onKeyEvent`，内容可先消费；`dismissOnBack`/`dismissOnBackdropClick` 分别控制两条关闭路径。
- **Backdrop 清屏**：终端没有 alpha。`OverlayBackdrop.Solid` 不能只写空格（会残留 base 的背景/下划线）。新增 `TextSurface.clearRect` 与 `TextCanvasDrawScope.clear`：对矩形内每个 cell 执行 `reset()`（字符/前景/背景/文字样式/下划线/下划线色），指定背景色时再写 `background`；标记 written 防本帧懒复位。`Solid(Unspecified)` = 回到终端默认空白，`Solid(color)` = 实色遮罩。`None` 不画任何东西、base 完整可见。
- **焦点域与恢复**：`FocusManager` 增加 modal 深度栈：打开 modal 时保存当前 `focusedNode` 并清空；关闭时 LIFO 恢复（目标需仍 attached、enabled、traversable）。`activeFocusRoots()` 返回顶层→最上层 modal 的 layer 列表；Tab 只在域内收集 focusable，域内为空时 Tab 仍被消费（旧的无 host 路径保持「无 focusable 不消费」）；`FocusRequester.requestFocus` 在 modal 期间忽略域外目标。`PointerState.preDispatch` 改为接受候选 roots，只从第一个命中的 root 收集 hover/按压/点击聚焦路径，所以 modal 外的 base 不会被 hover 或点击聚焦。
- **测试**：`DialogTest` 13 条——modal key/paste 屏障、Esc 内容未消费/已消费、backdrop 点击只在内容外且 press/release 跨区不误触、`None` 保留 base、`Solid()` 清字符+红底+下划线、`Solid(Red)` 铺红底且其余样式复位、焦点保存/恢复、关闭时目标已移除、Tab 只在 modal 两点循环、modal 无 focusable 时 Tab 被消费、打开 modal 取消 press + 清 hover、双层 modal 的 LIFO 键盘屏障。`PopupTest` 新增 modal Popup 填满 base、base key/click 不可达、内容 click 仍优先。`OverlayHostTest` 的非模态键盘用例改为「先给 popup、未消费再落 base」。
- **验证**：定向 25/25；全量 `:mosaic-runtime:jvmTest` **794/794**、`:mosaic-runtime:linuxX64Test --no-configuration-cache` **792/792**；`:mosaic-runtime:apiDump` 后 `apiCheck` 通过；`:mosaic-runtime:compileKotlinLinuxX64` 通过；`spotlessKotlinCheck` 仍只剩既有违规，新增文件已按格式修正（文件级 `@file:Suppress("ktlint:compose:param-order-check")` 保留设计要求的参数顺序）。构建环境仍用预置原生工具链 + `-x :mosaic-tty:zigDownload`（未改构建脚本）。
- **限制**：① host 登记在它所属的 `root()`；若放在 `BoxWithConstraints` 等子组合里，子组合根没有父链，主组合根的入口门控看不到它（P1 遗留的单 host/子组合边界，建议 host 放在组合根）；② `Solid` 的 cell reset 目前只覆盖整块 backdrop；若未来用于非整屏矩形，宽字符簇跨边界仍需按簇头/续列处理；③ 无 dim/invert 后处理、无非 modal 点击外部关闭、无关闭后 hover 重算、无 focus 目标的跨 modal 非 LIFO 对齐（设计 §11 开放问题）；④ `Dialog` 不画边框/背景，需要时调用方自行加 `Modifier.border`/`Modifier.background`。

### L7.41 浮层 P3 验收：端到端 golden + showcase Overlay 页（`EndToEndGoldenTest.kt` / `samples/showcase`，`164872d1`）
- **定位**：P1/P2 的测试都停在节点/单帧层；本轮补「用户最终看到的屏幕」与可直接运行的示例。只加测试与示例页，不改 runtime 公共 API。
- **Golden（`EndToEndGoldenTest` 增 3 条）**：
  - `dialogBackdropErasesBaseAndCentersContent`：80×24 base 上先画 `base content`，打开默认 `Solid` Dialog（`Rename`）。最终屏幕第 0 行为空（base 被擦除），第 12 行 `Rename` 居中于第 37 列；`VirtualTerminal` 无半格残留。
  - `popupDrawsAboveBaseAndLeavesRestVisible`：20×5 base 上 `base`，非模态 Popup 在 offset (2,1) 画 `menu`；屏幕为 `base` / `  menu`，其余空行被 trim。
  - `closingDialogRestoresBaseScreen`：同一 `OverlayHost` 先开 Dialog 再切回 `overlay = null`，增量渲染后屏幕恢复 `base content`，验证层移除后的重绘/diff。
  - 复用 `settleInto` 的「等待空闲超时 = 稳定」语义；断言 `VirtualTerminal.hasResidue() == false`。
- **Showcase Overlay 页**：
  - `ShowcaseApp` 整个 shell（header + page area + tab bar + hint）包进唯一的 `OverlayHost`；状态提升到 shell：`showPopup`、`showDialog`、`baseClicks`。
  - 非模态 `Popup` 放在 (4,3)，`onDismissRequest` 走 Esc；模态 `Dialog` 默认 `Solid`，覆盖整个 app（含 header/tab bar），Esc/backdrop 关闭；base 页提供「打开 Popup / 打开 Dialog / 点击 base 计数」三个 clickable 文本。
  - `Page` 增加 `Overlay`（位于 Unicode 之后，便于标签可见）；`when` 分支传入回调；新增 `screens/OverlayScreen.kt` 只负责说明与按钮，不自己创建 host。
  - 页面 smoke：`ShowcaseTest.overlayPageShowsPopupAndDialogControls` 用编号键 `2` 切到 Overlay，断言 `当前页: Overlay`、`[打开 Popup]`、`[打开 Dialog]`、`[点击 base: 0]`。
- **验证**：全量 `:mosaic-runtime:jvmTest` **797/797**、`:mosaic-runtime:linuxX64Test --no-configuration-cache` **795/795**（各含 3 条新 golden）；定向 `:samples:showcase:jvmTest --tests "example.ShowcaseTest.overlayPageShowsPopupAndDialogControls"` 通过；`:samples:showcase:compileKotlinLinuxX64` 通过；公共 API 未变，`apiCheck` 仍通过。
- **既有失败（已由 L7.42 修复）**：`ShowcaseTest.unicodePageShowsWideAndEmojiContent` 当时失败（`你好，世界！` 左缘首字被尾部 `│` 覆盖）；用 `47ce4adf` 单独 worktree 复跑同样失败，确认与浮层无关，随后定位为测试夹具的无界根问题并在 L7.42 修复。
- **P3 剩余**：非整屏 `Solid` 的宽字符簇边缘、关闭后 hover 重算、`Cursor` 活动域可见性、dim/invert 后处理、非 modal 点击外部关闭、子组合 host 登记、非 LIFO 焦点对齐。

### L7.42 showcase Unicode 测试夹具修复（`samples/showcase/.../ShowcaseTest.kt`，`14126970`）
- **现象**：`ShowcaseTest.unicodePageShowsWideAndEmojiContent` 的 PlainText 快照里，`你好，世界！` 变成 `│好，世界！`，每个 `Line` 的首字被尾部 `│` 覆盖；其它行同样丢首字。
- **根因**：`UnicodeScreen.Line` 用 `Row(Modifier.fillMaxWidth()) + Text(Modifier.weight(1f)) + Text("│")` 把分隔符推到右端。测试 harness 的根按**无界约束**测量（库的既有契约），`fillMaxWidth`/weight 在无界根上没有可分配宽度：weight 内容得到 0 宽，尾部 `│` 被放在第 0 列，绘制时覆盖首字。`ShowcaseApp` 运行时会给页面 `Modifier.width(terminal.size.columns)`，所以真实终端不受影响。
- **修复**：测试改为与 shell 运行时一致——`Box(Modifier.width(Terminal.Size.Default.columns)) { UnicodeScreen() }`，让 `fillMaxWidth`/weight 有 80 列可分配。未改 runtime、未改页面布局。
- **验证**：`:samples:showcase:jvmTest` **11/11**（含 Unicode 与 Overlay 页）；`:samples:showcase:compileKotlinLinuxX64` 通过；新文件无 spotless 格式/lint 问题（showcase 其它既有格式违规未动）。

### L7.43 浮层 P3 打磨三项（hover 重算 / 子组合 host / Cursor 活动域，`bb61db22`）
- **1. modal 关闭后 hover 重算**：`PointerState` 记录最后一次鼠标坐标（所有 `preDispatch` 都会更新），新增 `rehover(roots)`——只从当前顶层命中 root 收集 pointer entry 并 `reconcileHover`，不派发 click、不移动焦点。`OverlayHostState.syncLayers` 在 layer 列表或 modal 数量变化时先 `resetPointerSessions()`（取消 press + 清 hover），再 `root.rehoverOverlay()` 用最后坐标重算。`performLayout` 把 `syncLayers()` 放到 `measureAndPlace()` 之后，因此新打开 Dialog 的层已有真实 bounds，指针停在其内容上时也能立即进入 hover，而不是等下一次鼠标事件。指针不动打开/关闭 Dialog 后，base 的悬停项会退出/恢复高亮；不会产生 click 或焦点变化。
- **2. 子组合中的 host 注册**：`BoxWithConstraints` 的子组合根此前只 `node.children.add(root)`、没有 `parent`，导致 `root()` 停在子根，主组合根 `overlayHost` 看不到它。现在创建子组合时设 `root.parent = node`，`dispose` 时清空；`MosaicNodeApplier.onBeginChanges` 用 `root.root().overlayHost?.markDirty()` 标记顶层 host（子组合变更也能触发主根同步）。因此 host 放在 `BoxWithConstraints` 内时 modal key/mouse/paste 屏障仍生效，DisposableEffect 释放与单 host 约束不变。
- **3. Cursor 活动域**：`Cursor` 现在把自身节点通过通用内部 `MosaicNodeAttach`（`setModifier` 识别）登记到 `CursorState` 的 token→node 表；`CursorState.isVisible(root)` 用 `root.isInActiveFocusScope(node)` 判断。`MosaicComposition.draw` 仅在 `active && isVisible(rootNode)` 时把 cursor 请求写入 surface，否则 `cursorShape = null`。modal 打开时 base 的 `Cursor` 隐藏（请求仍保留），关闭后同帧恢复；modal 层内的 `Cursor` 仍可见。非 modal 层与无 host 场景保持原行为。
- **测试**：`DialogTest.openingModalRehoversIntoDialogContent`（base hover→open：base exit、dialog content enter；close：反向恢复）；`DialogTest.closingModalRehoversAtLastPointerPosition`（motion→open→close，hovered true/false/true，click 0、焦点 null）；`DialogTest.modalInsideBoxWithConstraintsStillGatesInput`（子组合内 dialog：key/paste 到 dialog、base click 0）；`CursorActiveDomainTest` 3 条（base cursor 打开 modal 时 `TextSurface.cursorShape == null`、关闭恢复非 null；dialog 内 cursor 打开时非 null；`RenderingSnapshots` 下 modal 打开帧不再包含 `showCursor`、关闭后重新包含）。既有 `BoxWithConstraintsTest` / `BoxWithConstraintsRenderingTest` / `CursorRenderingTest` 全绿。
- **验证**：全量 `:mosaic-runtime:jvmTest` **803/803**、`:mosaic-runtime:linuxX64Test --no-configuration-cache` **801/801**、`:samples:showcase:jvmTest` 11/11；`:mosaic-runtime:apiDump` → `apiCheck`（无公共 API 变化）；`:mosaic-runtime:compileKotlinLinuxX64` 通过；新文件无 spotless 格式/lint 问题（Node/mosaic/PointerInput/BoxWithConstraints 的既有格式/lint 违规未动）。
- **P3 剩余**：非整屏 `Solid` 宽字符簇边缘、dim/invert 后处理、非 modal 点击外部关闭、非 LIFO 焦点对齐。

### L7.44 浮层剩余正确性：宽簇边界清除 + 非 LIFO 焦点栈（`surface.kt` / `Focus.kt` / `OverlayHostState.kt`，`fed2ecda`）
- **1. 非整屏 Solid 的宽字符簇边缘**：`TextSurface.clearRect` 不再逐 cell 盲清。每一行先按起始列判断：若起点落在 `isPlaceholder` 的续列，向左走到簇头；随后扫描矩形内出现的宽簇头（`width > 1`），把右边界扩到 `head + width`。因此「只覆盖续列」「从簇中间开始」「在簇中间结束」「只覆盖簇头」都会清掉完整簇，相邻字符不受影响；标记 written 与背景色逻辑不变。全屏 Solid 走同一路径，行为不变。裁剪仍先夹到 canvas/clip，簇扩展只在纹理内部（最多到 canvas 宽），避免越界。
- **2. 非 LIFO 焦点栈**：`FocusManager` 的焦点保存从「按深度压 `MosaicNode?`」改为 `ModalFocusEntry(owner, saved)` 列表，owner 是该 modal layer 节点。`OverlayHostState` 每次同步把当前 modal layer 列表（底→顶）传给 `updateModals`：
  - 新层：记录当前 `focusedNode` 并清空焦点；
  - 存在的层：保留原 entry，并按当前顺序重排；
  - 移除的层：只删除自己的 entry；若是非栈顶移除，则保留上层焦点（仍有效时），并把被移除层的 saved 放入 `discardedFocusSaves` 作为后续兜底；
  - 全部关闭：按「当前移除层 top→bottom，再 discarded 最近→最早」找第一个仍 attached/enabled/traversable 且在活动域内的目标恢复；否则置空。`check(modalFocusEntries.owner == modals)` 保证栈与层严格对齐（native 上也无需 opt-in）。
  - `OverlayHostState.unregister` 走 `updateModals(emptyList())`，同样清理并恢复。
- **测试**：
  - `OverlayBackdropTest` 6 条：CJK 行「中A🙂B」上，矩形仅覆盖 CJK 续列 / 仅覆盖簇头 / 仅覆盖 emoji 续列 / 仅覆盖 emoji 簇头 / 仅覆盖 A / 全宽；逐 cell 断言整簇清空、相邻字符/placeholder 不变，并通过 `VirtualTerminal.hasResidue() == false`；全宽清除后 render 为空。
  - `DialogTest.nonLifoModalCloseKeepsUpperFocusAndFallsBackToBase`：base 聚焦 → A 打开（A 聚焦）→ B 打开（B 聚焦）→ 先关 A（B 焦点保留）→ 再关 B（B 的 saved 指向已移除 A，回退到 A 保存的 base）。既有 LIFO focus 保存/目标移除/空域 Tab 测试全绿。
- **验证**：全量 `:mosaic-runtime:jvmTest` **810/810**、`:mosaic-runtime:linuxX64Test --no-configuration-cache` **808/808**、`:samples:showcase:jvmTest` 11/11；`:mosaic-runtime:apiCheck`（无公共 API 变化）；`:mosaic-runtime:compileKotlinLinuxX64` 通过；新文件无新增 spotless 格式/lint 问题（Focus 的两处既有 expression-body 违规未动）。
- **剩余 P3（未做，按指示留设计）**：dim/invert 后处理、非 modal 点击外部关闭。

### L7.45 Solid backdrop 的绘制原点偏移修复（`layout/DrawScope.kt`，`b1256722`）
- **现象**：OverlayHost 放在内容区（host y=1，上方 row0 是 Header、row29 是 TabRow）时，打开默认 `Solid` Dialog 会把 row0 Header 擦掉、清到 host 下方之外；Dialog 内容居中位置正确、host 之后的 TabRow 保留。
- **根因**：`TextCanvasDrawScope.clear` 把局部原点算成 `-canvas.translationX/Y`。`DrawLayer` 在绘制前把 `translation` 设为节点的绝对 x/y，局部 (0,0) 对应绝对 `(translationX, translationY)`；取负号后 host 在 y=1 时清矩形变成 top=-1，`clearRect` 夹到 0 后从 row0 开始清，正好擦掉 Header。
- **修复**：`clear` 直接用 `canvas.translationX/Y` 作为 left/top，right/bottom = translation + scope width/height，再交给 `TextSurface.clearRect`（仍做 canvas/clip 夹取与宽簇扩边）。没有新增 API；偏移完全来自绘制 scope 的 translation，不靠猜。
- **回归测试**：
  - `EndToEndGoldenTest.solidDialogOnlyClearsHostAreaBetweenHeaderAndFooter`：20×6 Column 中 row0 `HEADER`、rows1..4 为 host（20×4）、row5 `TAB`；打开 Dialog 后屏幕为 `HEADER` / 空 / 空 / 居中 `Dialog` / 空 / `TAB`，Header 与 Tab 均保留，host 中间按 Solid 清空。
  - `OverlayBackdropTest.clearUsesDrawScopeTranslationAsAbsoluteOrigin`：把 surface 的 translation 设为 (2,1)，2×1 scope 的 clear 只清 row1 col2..3，其余格子保持 `X`；直接锁定「用 translation 而非负值」。
  - 既有全屏 host 的 `dialogBackdropErasesBaseAndCentersContent` golden 不变。
- **验证**：全量 `:mosaic-runtime:jvmTest` **812/812**、`:mosaic-runtime:linuxX64Test --no-configuration-cache` **810/810**、`:samples:showcase:jvmTest` 11/11；`:mosaic-runtime:apiCheck`（无公共 API 变化）；`:mosaic-runtime:compileKotlinLinuxX64` 通过；新增/修改文件无新增 spotless 格式/lint 问题（DrawScope 的既有 `fullyVisible` 格式违规未动）。
- **剩余 P3 设计项**：dim/invert 后处理、非 modal 点击外部关闭。

### L7.46 合并前格式清理（全仓库，`e236784d`）
- **实际范围与旧记录差异**：文档 §2/§6.2 旧写「7 处既有 spotless 失败」。实际运行发现：
  - 根项目 `:spotlessCheck` 只覆盖根工程，单独通过；`:mosaic-runtime:spotlessKotlinCheck` 才是真实门槛。
  - runtime 有 **8 处 compose lint**：Border 参数顺序、PointerInput `composed`、BoxWithConstraintsNode 参数顺序、BWC 渲染测试命名、Lifecycle 测试可变参数、IntrinsicCache/PointerInput/BWC 测试的 `mutableStateOf<Int>` 自动装箱。
  - animation 1 处（AnimatedVisibilityImpl 参数顺序）、samples/bench 1 处（Main 自动装箱）。
  - 另有 **62 个文件**的格式漂移（换行/缩进/尾随逗号/import 顺序/分支空行），集中在 runtime 的 `Tables.kt`、`GraphemeBreakTables.kt` 等大表与测试。
- **处理**：
  - 对公开 API（`HorizontalDivider`）与无法自动修复的 compose 规则用文件级 `@file:Suppress("ktlint:compose:...")`（Border param-order、PointerInput composed、BWC 渲染命名、三个 autoboxing 测试文件、bench Main、AnimatedVisibility）保留行为与 API；`mutable-params` 只在测试 helper 上函数级抑制。
  - 私有 `BoxWithConstraintsNode` 的参数顺序按规则重排（`state` 在 `modifier` 前），更新唯一调用点；无公共 API 变化。
  - `./gradlew spotlessApply` 完成 62 文件格式修正；随后 `spotlessCheck`、runtime/animation/bench 的 `spotlessKotlinCheck` 全绿。
- **diff 审查**：`git diff --check` 无 whitespace error；`git diff -w` 的剩余变化仅为尾随逗号、括号换行、import 顺序与 when 分支空行；无逻辑/注释语义改动。公开 API 未变（`apiCheck` 通过）。
- **验证**：`:mosaic-runtime:jvmTest` **812/812**、`:mosaic-animation:jvmTest` 23/23、`:samples:showcase:jvmTest` 11/11；`:mosaic-runtime:compileKotlinLinuxX64` 通过；`:spotlessCheck` / `:mosaic-runtime:spotlessKotlinCheck` / `:mosaic-animation:spotlessKotlinCheck` / `:samples:bench:spotlessKotlinCheck` 全绿。
- **剩余合并清理项**：上游示例目录标识是否重命名（§2/§6.2）；无其它已知 spotless 失败。

## 6. 当前状态 / 下一步候选

### 6.1 当前状态（2026-10-22）

- 分支 `main`（`refactor` 同步），L7.46 代码提交 `e236784d`（合并前格式清理）；代码提交后工作树干净，本台账变更另行提交。
- **红线清理（2026-10-21，提交 `77b645fa`）**：5 个含第三方名的提交信息已重写（其后所有提交哈希随之变化，台账/待办中的引用已全部重映射）；3 份调研文档已移出仓库（存档于仓库外）；README/CHANGELOG 的归属引用已中性化。
- **L1~L7 主干全部完成**：L1.1~L1.2 / L2.1 / L3.1~L3.8 / L4.1~L4.7 / L5.1~L5.6 / L6.1~L6.8 / L7.1~L7.46（含 L7.16.1）均已归档，详见 §4 台账与 §5 要点。
- 回归基线（2026-10-23）：`mosaic-runtime` **JVM 812 / linuxX64 810**；`mosaic-tty` JVM 116 / linuxX64 122（FFM 分发 JDK 25 亦 116）；`mosaic-tty-terminal` JVM 274 / linuxX64 273；`mosaic-animation` 各 23；`mosaic-terminal` / `mosaic-testing` / `samples/showcase`（11/11）双平台全绿。
- L7.38 时间盒验证：只跑新增定向 JVM 测试（`TabRowTest` 7/7）与 `:mosaic-runtime:compileKotlinLinuxX64`（`apiDump` / `apiCheck` 通过），未重跑全量 JVM/Native 套件，故上述基线数值保持不变。
- L7.39 时间盒验证：只跑新增定向 JVM 测试（`OverlayHostTest` 6/6 + `PopupTest` 5/5 = 11/11）、`:mosaic-runtime:apiDump` → `apiCheck` 与 `:mosaic-runtime:compileKotlinLinuxX64`；按时间盒要求**跳过** `linuxX64Test` 与全量 JVM 套件，故当时基线数值仍按 745/743 记录（新增 11 条未计入全量）。
- L7.40 验证：定向 overlay 测试 25/25（`DialogTest` 13 + `PopupTest` 6 + `OverlayHostTest` 6）、全量 `:mosaic-runtime:jvmTest` 794/794、`:mosaic-runtime:linuxX64Test --no-configuration-cache` 792/792、`:mosaic-runtime:apiDump` → `apiCheck`、`:mosaic-runtime:compileKotlinLinuxX64` 全绿。
- L7.41 验证：全量 `:mosaic-runtime:jvmTest` 797/797、`:mosaic-runtime:linuxX64Test --no-configuration-cache` 795/795；定向 `ShowcaseTest.overlayPageShowsPopupAndDialogControls` 通过；`:samples:showcase:compileKotlinLinuxX64` 通过；API 未变。
- L7.42 验证：`:samples:showcase:jvmTest` 11/11（含 Unicode 与 Overlay 页）；`:samples:showcase:compileKotlinLinuxX64` 通过；新增/修改测试文件无 spotless 格式或 lint 问题（showcase 其它既有格式违规未动）。
- L7.43 验证：全量 `:mosaic-runtime:jvmTest` 803/803、`:mosaic-runtime:linuxX64Test --no-configuration-cache` 801/801、`:samples:showcase:jvmTest` 11/11；新增 hover/BWC/Cursor 6 条定向测试通过；`:mosaic-runtime:apiDump` → `apiCheck`（无公共 API 变化）；`:mosaic-runtime:compileKotlinLinuxX64` 通过。
- L7.44 验证：全量 `:mosaic-runtime:jvmTest` 810/810、`:mosaic-runtime:linuxX64Test --no-configuration-cache` 808/808、`:samples:showcase:jvmTest` 11/11；新增宽簇/非 LIFO 7 条定向测试通过；`:mosaic-runtime:apiCheck`（无公共 API 变化）；`:mosaic-runtime:compileKotlinLinuxX64` 通过。
- L7.45 验证：全量 `:mosaic-runtime:jvmTest` 812/812、`:mosaic-runtime:linuxX64Test --no-configuration-cache` 810/810、`:samples:showcase:jvmTest` 11/11；新增 golden + translation 定向测试通过；`:mosaic-runtime:apiCheck`（无公共 API 变化）；`:mosaic-runtime:compileKotlinLinuxX64` 通过。
- L7.46 验证（纯格式）：`:spotlessCheck`、`:mosaic-runtime:spotlessKotlinCheck`、`:mosaic-animation:spotlessKotlinCheck`、`:samples:bench:spotlessKotlinCheck` 全绿；`:mosaic-runtime:jvmTest` 812/812、`:mosaic-animation:jvmTest` 23/23、`:samples:showcase:jvmTest` 11/11；`:mosaic-runtime:compileKotlinLinuxX64` 通过；公共 API 未变。
- L7.46 之后浮层只剩 P3 设计项（dim/invert 后处理、非 modal 点击外部关闭），见 todo §15 与设计文档 §9/§11；合并清理只剩上游示例目录标识。

### 6.2 下一步候选（需确认后，一次一项）

1. **合并收尾（待确认）**：上游示例目录标识是否重命名；spotless 已在 L7.46（`e236784d`）全绿，无待修格式项。
2. **本机可验证的功能候选**：浮层 P3 剩余设计项（dim/invert 后处理、非 modal 点击外部关闭；见设计文档 §9 P3、§11 与 todo §15）、`LazyRow`（与 L7.16 对称）、第一方 widget 集（table/chart/scrollbar；tabs 已由 L7.38 完成，todo §6 P3）、输入侧组合字符（todo §3 P3）。
3. **需 CI/远程环境**：macOS 特判（todo §9.2 P2）、Windows/ConPTY（§9.3 P1）、Windows 输入多路复用（§9.3 P1）、`linuxArm64` cinterop；本机不推进。
4. **性能可选项（非必须）**：逐格写入本体（`writeCluster` 11 字段 + `canvas[row,col]` 边界/平移）仍占 draw-idle 大头，候选「样式打包成 primitive」或「收敛局部 cell 引用」；当前性能已远超目标，优先级低于功能与清理。

### 6.3 历史状态 / 决策记录（2026-10-17 ~ 2026-10-20，保留供恢复上下文）

- 历史记录（2026-10-18）：**L7 性能「绘制热路径低分配」**（当时 12 项，后续全部归档，见 L7.2；L7.3/L7.4 亦已归档）：
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
  E/F（`c142441a`）、G（`c70cf0f0`）、H+GC 记要（`9c2761ce`）已归档；帧节流本轮归档。
  当时候选：
  - 逐格写入本体：`writeCluster`（每格 11 字段含 5 个样式对象写）+ `canvas[row,col]` bounds-check/translation 仍占 draw-idle 大头——候选「样式 Color/TextStyle 打包成 primitive」或「遍历收敛局部 cell 引用」。
  - 或转 L7 收尾（异常安全/帧节流/示例），性能已远超既定目标。
- 当时现状（2026-10-18）：`refactor` 分支，HEAD `4af27661`，JVM 520 + Native 519 全绿。上面功能步骤已归档。
- 现状（2026-10-17 归档后）：HEAD `56a55c3e`，JVM 与 linuxX64 四模块（runtime/terminal/tty/tty-terminal）全绿，全 samples 编译通过，示例 kexe 已重链（工作树演示不含提交）。**L5 输入/事件已全部完成**：L5.1 鼠标消费 / L5.2 粘贴通路 / L5.3 kitty 键盘协议开关 / L5.4 clipboard（OSC 52）/**L5.5 事件背压与 Motion 合并** / L5.6 特殊键核对与 repeat 投递。
- 现状（2026-10-17 归档后）：**L6 tty 适配已收官（L6.1~L6.8 全归档，HEAD `7ba3a7ef`）**：死回调移除 / DEC 2027 / raw 核对 / native 信号兜底 / resize 通路 / 读写缓冲 / 生命周期异常安全 / 探测清单完备性。tty / tty-terminal 两平台测试全绿。L6 余项（macOS/Windows/三套绑定一致性/`linuxArm64` cinterop）需远程环境，登记不改码。
- 决策（2026-10-17，已归档）：**L6 按「可本机验证优先」推进**——已做 L6.2 DEC 2027（§10 P1）；剩余建议顺序：raw mode 核对（§9.2 P1）、信号处理（§9.2 P2）；Windows（§9.3）与三套绑定一致性（§9.1）需 CI/远程后做。
- 决策（2026-10-17，已提交）：**外部组件待办整体推迟**——滚动容器 / 惰性列表 / 输入控件 / 鼠标交互 / 焦点 / 边框 / 定位回调 / 动画容器 / 第一方 widget 集属最外层组合组件，依赖内部核心（L4 布局、L5 输入、L6 tty 适配）先重构到位；新增 `docs/todo.md` §15 仅登记不实现，优先级统一排在内部重构之后。
- 决策（2026-10-17，已提交）：**布局缓存落地即完成 L4 布局层主干**——剩余可选优化（apply 失效查扫反向索引、未放置子树裁剪）留待后续需要时再做；L4 对外的下一候选是 intrinsic 复用（与布局缓存合并）或直接进入 **L5 输入**。
- 决策（2026-10-17，已归档）：**L5 输入走"全量 opt-in + 能力探测后自动启用"**——鼠标/粘贴经 `runMosaic*` 参数显式开启；kitty 键盘协议在探测到支持后自动 push、退出 pop；repeat/release 事件解析后由 runtime 按 press-only 过滤，既有应用零行为变化。
- 当时候选（需用户确认后实施，建议一次一项）：
  1. **`§9.2 P2` macOS 特判**：`TIOCGWINSZ`/termios 的 macOS 差异（`HUPCL`/`c_cc`）——需 macOS 环境，暂不可本机验证。
  2. 之后 Windows（§9.3）与三套绑定一致性（§9.1）需 CI/远程；本机可做候选见 todo §9.4/§9.5 剩余项。

### 6.4 调研记录：布局“比例”的 Compose 语法（L4.5，未落地，方向调整）
- 背景：原计划做“约束扩展”（Row/Column 补 `percentage`/`ratio` 弹性，参照线性布局 Constraint 优先级）。
- 调研（依据 androidx `foundation-layout` 1.10.4 源码，本地 Gradle 缓存可复核）：Compose **没有** `Modifier.percentage()`/`Modifier.ratio()` 这类“占剩余空间百分比”的 modifier；布局比例的官方语法只有三种，且 mosaic 已具备前两种：
  1. `Modifier.weight(weight, fill=true)`：Row/Column 内“扣除无 weight 子项后平分剩余主轴”的唯一一等公民；等宽列 = 每列 `weight(1f)`。
  2. `Modifier.fillMaxWidth/H/S(fraction)`：占父级传入 `maxWidth` 的 `fraction`（`Infinity` 时无效果）——即 Compose 的“百分比”。官方示例用 `fillMaxSize(0.5f)` 表示“占一半”。mosaic 已有（`layout/Size.kt`）。
  3. `BoxWithConstraints`：需要“固定 + 精确比例 + 剩余细分”手算的官方标准答法——子组合在 measure 阶段读取 `constraints`/`maxWidth` 后自定尺寸。
- 结论/决策：**自造 `percentage`/`ratio` 不符合 Compose 语法，已撤销，不进入 API**；这类需求用 `weight` + `fillMaxWidth(fraction)` 覆盖。`BoxWithConstraints` 曾评估需要通用 runtime 级 subcomposition（`Compose` 子实例 + slot 复用/回收），后按 L4.6 以**最小单槽位子组合**实现落地（`92297096`，见第 48 行台账），无需先行抽象通用 API。
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
