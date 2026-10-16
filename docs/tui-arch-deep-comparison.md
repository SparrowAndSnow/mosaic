# 深度调研：Ratatui 与 BubbleTea v2 的架构与功能实现对照（对比 Mosaic）

> 归档日期：2026-10-16，分支 `feature/unicode-wide-character-fixes`
> 定位：在 `docs/tui-arch-comparison.md`（宽字符视角的浅对比）基础上，做**逐功能审计级**的深挖：
> 架构、渲染管线、缓冲/cell 模型、diff/增量输出、布局、文本/样式、颜色、输入/事件、
> 能力探测、生命周期、测试、性能。每一节都以「参考实现怎么做 → Mosaic 现状 → 可借鉴点」展开。
>
> 参考源码（已拉到本地，保留）：
> - `/tmp/ratatui-clone` — Ratatui（`ratatui-core/` + `ratatui-widgets/`），workspace 化后的最新主干
> - `/tmp/bubbletea-clone` — BubbleTea v2（`tea.go`、`cursed_renderer.go`…）
> - `/tmp/uv` — `charmbracelet/ultraviolet`（BubbleTea v2 的 cell buffer + 终端 diff 渲染库）
> - `/tmp/ansi-x` — `charmbracelet/x`（含 `ansi/` 宽度算法与序列编码）
> - Mosaic 源码：`mosaic-runtime/`、`mosaic-terminal/`、`mosaic-tty/`、`mosaic-tty-terminal/`、`mosaic-animation/`、`mosaic-testing/`

---

## 0. 三个库的一句话定位

| 库 | 语言 | 编程模型 | 渲染数据流 |
| --- | --- | --- | --- |
| Mosaic | Kotlin | Compose 声明式：状态→重组→测量/放置→绘制 | `Snapshot` 观察变化 → `recompose` → `measureAndPlace` → `rootNode.draw()` → `AnsiRendering` 全量重建 ANSI → stdout |
| Ratatui | Rust | 命令式：应用每帧调 `terminal.draw(\|frame\|…)` | 回调内把 widget 画进当前 `Buffer` → `flush` 对前后双缓冲做 **diff** → 后端输出变更格 |
| BubbleTea v2 | Go | Elm 式：`Init/Update/View` + `Msg/Cmd` 单向数据流 | `Update` 产新 `View` → renderer 在 60fps ticker 上 `flush` → `uv.TerminalRenderer` 对屏幕模型做 **diff** → 增量输出 |

三者都收敛到同一个基础模型：**cell 网格 + 字素簇 + 列宽**，差异在「谁驱动重绘」与「重绘粒度」。

---

## 1. 总体架构与编程模型

### 1.1 Mosaic：Compose 声明式
- `runMosaic` → `withTerminal` 绑定 `Tty`/`Terminal` → `MosaicComposition`（`mosaic.kt`）承载 Compose `Recomposer`/`Composition`/`AbstractApplier`。
- 状态变化经 `Snapshot.registerApplyObserver`（`mosaic.kt` 的 `registerSnapshotApplyObserver`）分两级脏标记：读了哪些状态决定 `needLayout`（在 `Snapshot.observe` 里跑 `measureAndPlace`）还是仅 `needDraw`。
- 帧驱动：`BroadcastFrameClock` + `while(true){ clock.sendFrame(); delay(1) }`（约 1000fps 顺延循环，注释"1000 FPS should be enough for anybody"）。
- 关键输入循环 `startFrameListener`：每帧先 drain 键盘事件（`rootNode.sendKeyEvent`，Ctrl+C 退出），再发帧。**没有按需/限帧输出**——只要状态没变，`needLayout`/`needDraw` 都不会真重绘。

### 1.2 Ratatui：命令式、显式每帧 draw
- `ratatui-core/src/terminal.rs`：`Terminal<B>`（见 `terminal.rs` 结构体）持有 `buffers: [Buffer; 2]` 双缓冲、`current` 下标、viewport 状态、光标追踪、`frame_count`。
- 每帧流程（`terminal.rs` "Rendering Pipeline"）：autoresize → `Frame`（当前 Buffer 的可变视图）→ 运行回调（应用把所有 widget 画进去）→ `flush` 做 buffer diff 写后端 → 光标可见性/位置 → `swap_buffers` → 后端 flush。
- 每帧从**空 buffer** 开始，期待应用全量重画；diff 只让终端收到变化。Cursor 位置由 `Frame::set_cursor_position` 请求，flush 后统一应用（`terminal.rs` 光标状态机）。

### 1.3 BubbleTea v2：Elm，View 即 diff 输入
- `Model.Init/Update/View`（`tea.go`）；`Msg = uv.Event`；`Cmd func() Msg` 表示副作用。
- `Program`（`tea.go`）持有 `msgs chan`、input/resize/command/signals 各 handler 的 `channelHandlers`；`eventLoop` 用 `select` 多路复用（`ctx.Done`/`errs`/`msgs`）。
- 渲染解耦：`p.render(model)` 只把 `View()` 存进 renderer（`cursed_renderer.go` 的 `render` 只 `s.view = v`）；**独立 goroutine**（`tea.go` `startRenderer` + 60fps `time.NewTicker`）调 `flush`：
  ```go
  case <-p.ticker.C:
      p.flush(); p.renderer.flush(false)
  ```
- 因此 BubbleTea 是**帧节流 + 全量重算 diff**；Mosaic 是**状态驱动、帧时钟尽力而为**；Ratatui 是**应用主动每帧 draw + 双缓冲 diff**。

### 可借鉴
- Mosaic 靠 Compose 已能跳过无变化帧；但它没有「全屏双缓冲 + diff」这一层，下一节展开。
- BubbleTea 的 ticker（60fps 封顶）比 Mosaic 的 `delay(1)` 空转更省电、更可预期；Mosaic 可考虑对外暴露 FPS 上限。

---

## 2. 缓冲与 cell 模型

### 2.1 Mosaic：单 Int 码点 + 布尔续列标志
- `TextSurface`/`TextPixel`（`surface.kt`）：`TextPixel { codePoint: Int, isWideContinuation: Boolean, bg/fg/textStyle/underlineStyle/underlineColor }`。
- 写入粒度：逐**码点**（`DrawScope.drawText`，`DrawScope.kt`）。`charWidth(cp)` 定宽，宽字符在后续格只打 `isWideContinuation = true`。
- `appendRowTo` 输出时 **跳过续列格**（`if (pixel.isWideContinuation) { columnIndex++; continue }`），只输出首格码点；遇到单码点宽字符这能画出 2 列。
- 已知缺陷（详见 `docs/todo.md`）：多码点字素簇（ZWJ/VS16/keycap）逐码点写，w=0 的成员被覆盖丢失；续列标志生命周期仅在同格重写时复位；测宽（`TextLayout` 用串级 `unicodeWidth`）与绘制（`charWidth` + emoji override）两套口径不一致。

### 2.2 Ratatui：整簇字符串一格
- `ratatui-core/src/buffer/cell.rs`：`Cell { symbol: Option<CompactString>, skip: bool, … }`，注释明确 "accepts unicode grapheme clusters which might take up more than one cell"。
- `buffer.rs` `set_stringn`：`UnicodeSegmentation::graphemes` 迭代 → 过滤控制符/零宽 → **整簇写进首格**，后续 `reset()` 占位；`remaining_width` 决定是否放得下（右缘截断）。
- `skip` 标志：告诉 diff 此为宽字符续列，跳过 diff / 推进游标。

### 2.3 BubbleTea/uv：整簇字符串 + 显式宽度
- `cell.go`：`Cell { Content string, Style Style, Link Link, Width int }`；`isWidePlaceholder()` = `Width == 0`。
- `buffer.go` `Line.Set()`：写簇时 `for j:=1; j<cw { l[x+j] = Cell{} }` 打 `Width==0` 占位；**覆盖头列/占位列时反向找所属宽格并整段清空**（防半格残留）；右缘超宽写同风格空格 + 截断；`Line.String()` 跳过 `IsZero()` 格。
- `widecell_placeholder_test.go` 复现过真实回归：占位格继承 Style 后 `IsZero()` 与 `Width==0` 两个检测路径分歧，diff 把插入点选进宽格中间——说明**占位检测口径必须唯一**。

### 对比结论
| 项 | Mosaic | Ratatui | uv |
| --- | --- | --- | --- |
| 每格内容 | `Int` 码点 | 字素簇字符串 | 字素簇字符串 |
| 宽格续列 | 布尔标志 | `skip`（+`reset()` 占位） | `Width==0` 占位格 + 覆盖清理 |
| 写入粒度 | 码点 | 簇 | 簇 |
| 宽格生命周期 | 无清理 | 写后 reset；diff 里推进 | 覆盖时整段清理 |
| 右缘 | 溢出/丢弃 | 放不下不写 | 补同风格空格 |

> Mosaic 若走方案 A（`docs/todo.md`）：cell 应从「Int + Boolean」升级为「簇字符串 + 显式列宽」，这是三家中两家已实证的模型。

---

## 3. diff 与增量输出

### 3.1 Mosaic：无 diff，全量重建
- `rendering.kt` `AnsiRendering.render`：一次生成**整帧**字符串——`CSI n F` 回到上次输出顶部 → 每行先 `clearLine`（`CSI K`）→ `appendRowTo` → 行数变少时 `clearDisplay`；能力支持时用 **synchronized output**（DEC 2026）包裹整帧。
- 特点：终端不参与任何增量计算，正确性最稳、与终端无关；代价是每帧全量输出 + 每行先清，带宽与闪烁开销大（有 synchronized output 时闪烁可被压制）。

### 3.2 Ratatui：双缓冲 BufferDiff
- `buffer/diff.rs`（978 行）：`DiffState` 直接生成后端操作，核心难点全部围绕多宽字符：
  - 跳过 `skip` 格（宽字符续列）；
  - 按 `cell_width()` 推进终端游标（`pos += width - 1`），避免续列被误覆盖；
  - `ForcedWidth`：旧格窄新格宽时强制写满，避免残留；
  - **不确定宽度符号**（如 VS16 结尾的 emoji，终端画 1 画 2 不定）：先清预留列再写；
  - "宽字形未变但样式变"时续列旧样式残留的处理。
- `cell_width.rs`：`unicode-width` 之上对 `U+FF9E/U+FF9F` 做 +1 补偿（终端上各占一格）——Mosaic vendor 的 16.0 表已直接按 1，方向一致。

### 3.3 BubbleTea/uv：屏幕模型级 diff（三种传输手段 + 成本启发）
- 两层：`ScreenBuffer`（cellbuf，应用可见内容，每帧 `Clear()` + `content.Draw()` 重画）→ 其 `RenderBuffer`（含 `Touched[i].FirstCell/LastCell` 脏行记录）→ `TerminalRenderer.Render`（`terminal_renderer.go`）。
- `Render`：`touchedLines == 0 && !clear && !termResized` 直接返回（帧级短路）。`reconcileSize` 处理 resize；行数变小时 `clearBelow` 擦残留；fullscreen 且非 Windows 时 `scrollOptimize`（`DECSTBM` 滚动区优化）。
- 行内 diff `transformLine(newbuf, y)`（`terminal_renderer.go:953`）：找出首末变化格，然后按**成本启发式**在几个手段里挑便宜的：
  - 直接写格 `putRange/emitRange`（后者对连续暴力相同单元格用 `ECH`/`REP` 压缩）；
  - 补/删格 `insertCells`（`ICH`）/ `deleteCells`（`DCH`）；
  - `clearToEnd`（`EL`）或 `EraseLineLeft`；
  - 例如 `oLastCell-nLastCell > el0Cost` 时用 EL 而非逐个清。
- 输出单元 `putAttrCell`：`Width==0` 占位格**直接不写**；`updatePen` 计算新旧样式差，只发变化序列（`newStyle.Diff(&oldStyle)`）；`putCellLR` 针对右下角格来回切 auto wrap 防滚动；`canClearWith` 用 BCE 假设判断能否用 `EL` 清行。
- `reanchorWideLine`：当未协商 DEC 2027 时，终端的宽度模型可能与本地不一致，导致光标漂移——用一次绝对移动把光标锚回本行，把漂移限制在一行内。

### 对比结论
| 项 | Mosaic | Ratatui | uv |
| --- | --- | --- | --- |
| diff 存在性 | 无（整帧） | 有（双缓冲 BufferDiff） | 有（旧屏幕模型 vs 新 RenderBuffer） |
| 增量手段 | — | 移动+写格 | 写格/ECH/REP/ICH/DCH/EL 成本择优 |
| 宽格 diff | 不参与（每行全清） | skip + 游标推进 + ForcedWidth | Width==0 不写 + 覆盖清理 |
| 闪烁防护 | synchronized output 包整帧 | 由后端/应用侧（synchronized output） | synchronized output + 光标 hide/show 包裹 |

> 最值得迁移的单一能力是**行级脏标记 + EL 清尾**：Mosaic 全帧重建时每行都 `clearLine`，若能改为「只重绘内容变化的行、行内只写变化段」即可显著降带宽（尤其大屏）。Ratatui 的 `skip/ForcedWidth/不确定宽` 三件套与 uv 的 `Width==0` 是同一问题的两种解法，方案 A 可直接借鉴 Ratatui 的 diff 语义。

---

## 4. 布局系统

### 4.1 Mosaic：Compose measure/layout 机制
- `layout/Node.kt`：`MosaicNode` 持有 `MosaicNodeLayer` 链（`LayoutLayer`/`DrawLayer`/`KeyLayer`/`BottomLayer`），`doMeasure→placeChildren→drawTo→sendKeyEvent` 沿链走；`translationX/Y` 实现相对定位。
- UI：`Row`/`Column`（`RowColumnMeasurementHelper.kt`）支持 **weight**（先量零权重子级、再按权重均分剩余主轴 + 余数分配）+ `Arrangement`（spacing/spaceBetween…）+ `crossAxisAlignment`；`Box`（`Box.kt`）支持 `contentAlignment` + `propagateMinConstraints`；`Modifier` 链含 `padding/offset/aspectRatio/size/weight/drawBehind/onKeyEvent/…`。
- 约束：`Constraints`（`ui/unit/Constraints.kt`，min/max 双界）。**没有** wrap、Alignment 指文本对齐，文本不换行不滚动。
- 性能：每次 `needLayout` 重跑 `measureAndPlace`（全树）；无布局缓存。

### 4.2 Ratatui：线性约束求解器（kasuari）
- `layout/layout.rs`（2931 行）+ `flex.rs` + `constraint.rs`：`Layout::split(area)` 用 **kasuari 线性约束求解器**（`Solver::add_constraint`，见 `try_split`）把 constraints 转成带优先级的线性方程组：
  - 优先级 `Min > Max > Length > Percentage > Ratio > Fill`（`flex.rs` 文档）；
  - 变量建模：每个段 = [start,end] 两个 `Variable`，外加间隔段（spacer），`configure_*_constraints` 逐步加 REQUIRED / 软约束；
  - `Flex` 模式（`Legacy/Start/End/Center/SpaceBetween/SpaceAround/SpaceEvenly`）+ `Spacing`（间距/负重叠）；
  - **LRU 线程本地缓存**（feature `layout-cache`）：相同 constraints+area 直接复用结果，避免重复求解。
- 输出 `Rect`/`Rects`，支持 `constraint`、`margin`、`direction`、`flex`、`spacing`、`split_with_spacers`。

### 4.3 BubbleTea v2：无内建布局
- BubbleTea 本身**没有布局引擎**：`View` 只有一行行 `Content`（styled string）与元信息。行布局由外部库（lipgloss）拼装字符串完成；uv 只负责把字符串按宽度铺进 cellbuf。

### 对比结论
| 项 | Mosaic | Ratatui | BubbleTea |
| --- | --- | --- | --- |
| 布局模型 | Compose 约束 measure/place，声明式 | 一次性线性约束求解，可组合 | 无（靠外部拼串） |
| 弹性分配 | Row/Column weight，Arrangement | Constraint 优先级 + Flex 模式 + Spacing | — |
| 换行/滚动 | 无内建 | Paragraph `wrap`(textwrap)+`scroll` | 外部（lipgloss） |
| 缓存 | 无 | LRU thread-local（可选 feature） | — |
| 灵活性 | 任意声明式嵌套 + Modifier | 单次 `split`（不支持嵌套自动布局，widget 自己再 split） | 自由（但无布局） |

> Mosaic 的声明式布局是三者里表达力最强、也是唯一支持交互式递归布局的（Ratatui 需 widget 自己嵌套 split，BubbleTea 无布局）。Ratatui 的 **LRU 布局缓存**与 Constraint 优先级思想，是 Mosaic 的 Row/Column weight 覆盖不到的场景（Mosaic 无 `percentage`/`fill`/`ratio` 语义，`weight` 是其近似）。

---

## 5. 文本与样式模型

### 5.1 Mosaic
- `text/AnnotatedString.kt`（470 行）：自实现带 `SpanStyle` 的富文本（无 androidx core）。
- `TextLayout.kt`：测量用 `unicodeWidth()`（串级、无 emoji override）；`Text.kt` 的 `drawBehind` 里按行 `drawText`（逐码点、`charWidth` 带 emoji override）。→ **测量与绘制两层口径不一致**（P2，`docs/todo.md`）。
- 无 wrap/ellipsis/scroll；多行靠 `\n` split。

### 5.2 Ratatui
- `text/text.rs`：`Text = Vec<Line>`、`Line = Vec<Span>`、`Span { content, style }`；`Line::width` 用 `UnicodeWidthStr::width`（与绘制同一库）。
- `paragraph.rs`（1396 行）：`wrap(Wrap{trim})`（基于 textwrap 的字素簇安全换行）、`alignment`（`Alignment` 枚举，空白分配）、`scroll`（y/x 偏移）——**把"文本排版"内建在 widget 里**。
- style：`style/style.rs` `Style` + `stylize.rs` `Styled` trait（链式 DSL）+ `palette/`（tailwind/material）+ 颜色降采样。

### 5.3 BubbleTea/uv
- `styled.go`：`StyledString` 把带 ANSI 的字符串解析成 segment（文本+样式+链接跨度）；`content.Draw(s.cellbuf, bounds)` 按宽度铺格、按行定位；换行按 `\n`，不 wrap。
- 高亮由外部（lipgloss/chroma）生成 ANSI，uv 再做"ANSI → cell 样式"还原。测宽统一走可插拔 `WidthMethod`（`GraphemeWidth` 簇级 / `WcWidth` 逐 rune）。

### 对比结论
- Ratatui 与 uv 都是**解析/渲染对象里只存样式跨度，绘制时才铺格**，且测宽与绘制共用同一函数；Mosaic 唯一存在「测/绘两套口径」的，修法见 `docs/todo.md` 方案 A（统一走簇级 `unicodeWidth`）。
- 换行/滚动：Ratatui 内建（grapheme 安全），Mosaic 与 BubbleTea 都需要应用/外部处理。Mosaic 若要补，应以 Ratatui `paragraph` 的簇级 wrap 为参照，避免按码点硬断。

---

## 6. 颜色与样式输出

### 6.1 Mosaic
- `surface.kt appendRowTo`：逐格比较前后格的颜色/样式，只发**变化的属性**（fg/bg/underline color 按 `AnsiLevel` 分 ANSI16/ANSI256/TRUECOLOR 编码；text style 位开/关；kitty underline `4:2..5` 按能力开）；行尾按最后一个格是否有样式来决定是否 `ansiReset`。
- `toAnsi16Code/toAnsi256Code`（`ansi.kt`）从 colormath 移植。
- `AnsiRendering` 每帧每行 `clearLine` 兜底 → 行级样式残留少，但也不做跨帧样式复用。

### 6.2 Ratatui
- `Style` 合并（`patch` 语义）；`Color` 枚举（`color.rs` 含 `Reset/Black../Rgb/Gray/Indexed`）；`anstyle.rs` 负责把颜色降到后端支持档位；输出发生在 `Backend::draw` 逐 cell，颜色序列由 crossterm 生成。

### 6.3 BubbleTea/uv
- `colorprofile`（cursed_renderer 用 `setColorProfile`）探测真彩档位；`updatePen` 把每个 cell 的 `Style`/`Link` 与"当前笔"比较，只写**样式的差异序列**（`ConvertStyle` 按 profile 降采样），并对链接做相同的惰性 diff。

### 对比结论
- 三家的行内方形一致：**记录目标样式，输出时只发变化段**。Mosaic 的特殊点在"每帧全量重建 + 每行清尾"，导致跨帧样式优化（uv 的 pen diff）未被利用；引入 diff 后可顺手拿到样式增量。

---

## 7. 输入 / 事件处理

### 7.1 Mosaic
- `mosaic-tty-terminal/EventParser.kt`（951 行）+ `bytes.kt`（202 行）手写字节级解析：kitty 键盘（含 `[u` 扩展、PUA 特殊键码）、mouse SGR/X10、bracketed paste（OSC 52）、focus、DA1/DA2/DA3、DSR、cursor position、kitty graphics/pointer/notification、system theme、resize、terminal color。
- `Event.kt`：`KeyboardEvent`（kitty 语义：shift/alt/ctrl/super/hyper/meta/capsLock/numLock + Press/Repeat/Release + 关联文本）、`MouseEvent`（x/y/type/button/mod）、`FocusEvent`、`BracketedPasteEvent`…。
- 但 **runtime 层只消费键盘**（`mosaic.kt startFrameListener` 的 `rootNode.sendKeyEvent`）；鼠标/粘贴被解析进 events channel 后无 UI 消费者（`rg` 确认 `mosaic-runtime` 无 `MouseEvent` 引用）。
- 键盘事件流方向：`KeyboardEvent.toKeyEventOrNull` → `KeyLayer`（`onPreKeyEvent → child → onKeyEvent`）沿 Modifier 链分发。

### 7.2 Ratatui
- **无内建输入**：渲染库只管输出；键盘/鼠标/粘贴由后端（crossterm/termion/termwiz）事件提供，应用自行消费。

### 7.3 BubbleTea v2
- 输入解码在 uv：`decoder.go`（2106 行）封装 `x/ansi/parser` 状态机 → 产出 `KeyPressEvent/KeyReleaseEvent/MouseXXX/Paste/Focus/WindowSize/…`；`key_table.go` 维护按键映射。
- BubbleTea 层 `import.go` 的 `translateInputEvent` 映射为 `KeyMsg/MouseMsg/PasteMsg/…`。
- 鼠标模式（None/CellMotion/AllMotion，`mouse.go`）与键盘增强（`keyboard.go`：ReportEventTypes/ReportAlternateKeys/ReportAllKeysAsEscapeCodes/ReportAssociatedText）由 `View` 声明、`flush` 时按需切换协议，并可处理 `KeyboardEnhancementsMsg` 回报。

### 对比结论
| 项 | Mosaic | Ratatui | BubbleTea |
| --- | --- | --- | --- |
| 事件解析 | 自研 EventParser（kitty/mouse/paste/focus/resize…） | 不负责 | uv decoder（x/ansi/parser） |
| UI 层消费 | 仅键盘 | 无 | 键盘/鼠标/粘贴/焦点/clipboard/颜色查询 |
| 鼠标 | 解析但未用 | 无 | 完整（含模式切换） |
| 键盘增强 | kitty 协议已解析；UI 未显式开关 | 无 | 声明式（View.KeyboardEnhancements）+ 能力回报 |

> Mosaic 的解析层其实比 BubbleTea 更全（kitty graphics/pointer/notification、DA1..3、theme），缺的是**上层消费**：mouse/paste 事件已能进 channel，只差 `Modifier`/widget 层的 API 与协议切换时机（参考 BubbleTea 在 `flush` 里按 View 声明切换 mouse/focus/kitty 模式的做法）。

---

## 8. 终端能力探测

### 8.1 Mosaic（最重）
- `TtyTerminal.kt asTerminalIn`：进入 raw mode → 发 `CSI c`（DA1）→ 按 `stage` 状态机（`StageDeviceAttributes→StageCapabilityQueries→StageDefaultQueries→StageNormalOperation`）批量发：`DECRPM`（cursor/focus/synchronizedOutput/theme/inBandResize）+ kitty graphics/keyboard/notifications/pointer/textSizing/underline + xterm version + DSR 收尾。
- 1 秒 `withTimeoutOrNull` 兜底；结果落到 `Terminal.Capabilities`（13 项）。**注意：未探测 DEC 2027（UnicodeCore）**——它是宽字符绘制口径的关键。

### 8.2 Ratatui
- core 不做探测；进入/退出、raw mode、alt screen 由 `ratatui` crate 的 `init/restore/run` 处理，后端（crossterm）负责颜色档位探测。

### 8.3 BubbleTea v2（最少、按需）
- 启动只 `RequestModeSynchronizedOutput + RequestModeUnicodeCore`（`tea.go` startup→`ModeReportMsg` 处理 2026 →`setSyncdUpdates(true)`；2027 → `setWidthMethod(ansi.GraphemeWidth)`）。
- 颜色用 `colorprofile.Detect`（环境变量 + 可选 OSC 查询，`tea.go:1111`）。
- 其余全部**按需**：bracketed paste / mouse / focus / kitty keyboard / 窗口标题等在 `flush` 里与 `lastView` 比较后切换，无 DA1 全面扫描。

### 对比结论
| 能力 | Mosaic | Ratatui | BubbleTea |
| --- | --- | --- | --- |
| 综合探测 | DA1 + 批量 DECRPM/DCS/OSC + 1s 超时 | 无 | 仅 2026/2027 + colorprofile |
| 颜色档位 | env 启发式（`detectAnsiLevel`） | 后端 | colorprofile（env+OSC） |
| synchronized output | DECRPM 探测 | 后端 | DECRQM 探测 |
| **DEC 2027 UnicodeCore** | **未探测** | 无此概念（按 unicode-width 序列折叠） | DECRQM 探测，决定宽度口径 |

> Mosaic 的能力探测是三家最完备的，缺两件事：
> 1. **DEC 2027 探测与宽度口径挂接**（BubbleTea 的 `setWidthMethod` 思路）：无 2027 的终端上，ZWJ 族 emoji 会逐码点进格，Mosaic 目前无条件按簇折叠，会在老终端上错位；可暴露 `WcWidth`（逐 rune）后备口径。
> 2. 探测结果应该像 BubbleTea 那样驱动渲染策略（例如 2027 关闭时走 `reanchorWideLine` 式的漂移补偿）。

---

## 9. 生命周期 / 退出恢复

### 9.1 Mosaic
- `withTerminal`（`terminal.kt`）+ `Tty` 的 `withFinalizationHook`（`TtyTerminal.kt`）：关闭时 `setCallback(null)`、关 focus/inBandResize/systemTheme、恢复 cursor、`reset()`（离开 raw 模式/alt screen/粘贴模式）。
- `NonInteractivePolicy`（`Exit/Throw/Return/Ignore/AssumeAndIgnore`）与 `NonInteractiveTerminal` 假实现。

### 9.2 Ratatui
- `ratatui` crate：`init/restore/run` + **panic hook 包装**（入口安装自定义 hook 前先恢复终端），`Terminal::drop` 恢复隐藏光标。

### 9.3 BubbleTea v2
- `shutdown(killed)` 前先渲染终态；`stopRenderer` 可选 flush 最后一帧；恢复 alt screen/keyboard raw 状态；`channelHandlers.shutdown()` 等所有 handler 退出；`disableCatchPanics` 可关 panic 恢复。

### 对比结论
三家都把「退出恢复」做成强约束；Mosaic 的 `withFinalizationHook` + NonInteractiveTerminal 甚至在**非交互环境**下也可以跑（BubbleTea 需要 TTY）。这属于 Mosaic 的既有优势，文档仅作记录。

---

## 10. 测试体系

| 项 | Mosaic | Ratatui | BubbleTea |
| --- | --- | --- | --- |
| 快照 | `mosaic-testing` `PlainTextSnapshots`（ansiLevel=NONE）+ `MosaicSnapshots` | `assert_buffer!` 逐 cell 断言 + 封面示例 golden | `screen_test.go`/mock writer 断言输出 |
| 事件回放 | `TestMosaic.sendKeyEvent` + `awaitSnapshot`（60fps 模拟时钟） | TestBackend 记录输出 | 构造桩终端喂字节 |
| 终端模拟 | `TestTerminal`（固定 Capabilities，可注状态） | `TestBackend` 模拟终端 | `TestTTY` |
| 渲染正确性缺口 | 无 ZWJ/keycap/override 渲染级断言（`docs/todo.md`） | 宽字符/不确定宽有专门 diff 测试 | `widecell_placeholder_test.go` 专门防占位口径分歧 |

> 教训：Ratatui 与 uv 都针对「多宽字符 diff」写了**一整套回归**（skip/ForcedWidth/不确定宽/占位口径），Mosaic 目前渲染级测试只锁 `A中B`。修方案 A 时应补「ZWJ/keycap/override、宽格覆盖清理、右缘裁剪」的渲染断言（`AnsiRenderingTest`）。

---

## 11. 性能手法汇总

| 手法 | Mosaic | Ratatui | BubbleTea/uv |
| --- | --- | --- | --- |
| 跳过无变化帧 | Compose 脏标记（needLayout/needDraw） | 每帧全画但 diff 省输出 | 帧比较 `viewEquals` 短路 + ticker 限帧 |
| 增量输出 | 无 | BufferDiff | transformLine 全手段择优 |
| 文本宽度 | vendor 表 + emoji override 二分 | `unicode-width` 表 | displaywidth/go-runewidth + 可插拔口径 |
| 布局缓存 | 无 | LRU thread-local | —（无布局） |
| 细粒度传输优化 | 无 | 游标移动 + 写格 | ECH/REP/ICH/DCH/EL 成本择优、hard tabs、backspace、DECSTBM 滚动区 |
| 闪烁防护 | synchronized output 整帧 | 无内建 | synchronized output / cursor hide-show 包裹 |

---

## 12. 宽字符专项结论（承接 `docs/tui-arch-comparison.md`）

综合两轮调研，三家收敛于同一实现事实，Mosaic 的差距可精确列出：

1. **cell 存簇字符串 + 显式 Width**（Ratatui `Cell`、uv `Cell`）；Mosaic 的 `Int + Boolean` 是唯一落伍模型。→ 方案 A。
2. **测宽与绘制共用同一宽度函数**（Ratatui `UnicodeWidthStr`、uv `WidthMethod`）；Mosaic 的两层口径（P2）是三家唯一有此问题者。
3. **占位检测口径唯一**（uv `Width==0` 与 `IsZero` 的回归教训）；Mosaic 的 `isWideContinuation` 布尔标志 + "仅重写复位" 生命周期不完整（`drawRect` 背景填充不复位）。
4. **右缘裁剪**：uv 补同风格空格、Ratatui 放不下不写；Mosaic 当前丢弃/溢出。
5. **DEC 2027 口径挂接**：BubbleTea 按探测切换 `GraphemeWidth`/`WcWidth`，并有 `reanchorWideLine` 漂移补偿；Mosaic 未探测 2027。
6. **diff 层**：Ratatui/uv 都有，Mosaic 全帧。即便不引入全量 diff，至少应做"行级脏 + EL 清尾"。

---

## 13. Mosaic 可借鉴清单（按优先级）

- **[P0] 宽字符方案 A（`docs/todo.md`）**：cell 升级为「簇字符串 + 列宽」；占位语义用 `Width==0` + 覆盖清理；测/绘统一簇级 `unicodeWidth`；右缘裁到行宽。两参考实现均验证此模型。
- **[P1] 行级增量输出**：从"每帧整帧重建 + 每行 clearLine"升级为"脏行标记 + 行内写变化段 + `EL` 清尾"，可先把 `AnsiRendering` 改成保留上一帧 `TextSurface` 做行/段 diff。闪烁仍用 synchronized output。
- **[P1] DEC 2027 探测**：在 `TtyTerminal` 能力探测中加入 `CSI?2027$p`，暴露 `unicodeCore` 能力；能力缺失时提供逐 rune 口径后备（`WcWidth` 等价物），并把宽度口径像 uv 一样做成可插拔 `Method`。
- **[P2] 布局缓存**：`measureAndPlace` 结果按 constraints+size 的 LRU（参照 Ratatui layout-cache），降低高频 resize/同构重排成本。
- **[P2] 上层消费鼠标/粘贴**：解析层已齐，补 mouse modifier（`Modifier.onMouseEvent`）+ 粘贴，并像 BubbleTea 在需要时切换 SGR/kitty 协议。
- **[P2] 测试补齐**：`AnsiRenderingTest` 增加 ZWJ/keycap/override/宽格覆盖/右缘裁剪的渲染级断言；`TextLayoutWidthTest` 增加绘制一致性断言。

## 源码参考位置（速查）

- Mosaic：`mosaic-runtime/.../mosaic.kt`、`rendering.kt`、`surface.kt`、`layout/DrawScope.kt`、`layout/Node.kt`、`ui/RowColumnMeasurementHelper.kt`、`text/TextLayout.kt`、`text/CharWidth.kt`、`mosaic-tty-terminal/.../TtyTerminal.kt`、`EventParser.kt`、`mosaic-terminal/.../Event.kt`、`mosaic-testing/.../TestMosaic.kt`
- Ratatui：`ratatui-core/src/terminal.rs`、`buffer/{cell,buffer,diff,cell_width}.rs`、`layout/{layout,flex,constraint}.rs`、`ratatui-widgets/src/paragraph.rs`、`text/{text,line,span}.rs`、`style/{style,color,stylize}.rs`
- BubbleTea：`tea.go`（Program/eventLoop/startup/flush）、`cursed_renderer.go`、`mouse.go`、`key.go`、`keyboard.go`、`screen.go`
- uv：`cell.go`、`buffer.go`、`terminal_screen.go`、`terminal_renderer.go`（`Render/transformLine/putAttrCell/updatePen/reanchorWideLine`）、`styled.go`、`decoder.go`
- x/ansi：`width.go`、`method.go`、`wcwidth.go`
