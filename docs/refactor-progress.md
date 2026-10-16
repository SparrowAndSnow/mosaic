# Mosaic 重构进度台账（refactor 分支）

> 用途：记录「自下而上重构」的进度与已完成步骤的实现要点，保证会话上下文压缩后仍可恢复工作状态与下一步。
> 分支：`refactor` · 当前 HEAD：`9c622d53` · 创建/更新：2026-10-17
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
| L3 渲染管线 | 按簇绘制 → 增量输出 → 差分/缓冲/静态/闪烁 | §2 | 进行中（L3.3、L3.8 完成） |
| L4 布局 | 约束/测量/缓存/排版/Modifier | §5、§6 | 进行中（L4.1 完成） |
| L5 输入/事件 | 鼠标/粘贴/剪贴板/背压 | §8 | 未开始 |
| L6 能力探测/tty 适配 | DEC 2027、三套绑定、raw、信号、Windows | §9、§10 | 未开始（大头） |
| L7 生命周期/动画/测试/发布 | 异常安全、帧节流、测试矩阵、示例 | §11~§14 | 未开始 |

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
| L3 | L3.4 | 全屏双缓冲 diff | — | — | ⬜ 未开始 |
| L3 | L3.5 | static 输出行级清理（替代一键 `clearDisplay`） | — | — | ⬜ 未开始 |
| L3 | L3.6 | 无同步渲染时的闪烁防护（光标包裹） | — | — | ⬜ 未开始 |
| L3 | L3.7 | 输出批量/分片写，避免大字符串抖动 | — | — | ⬜ 未开始 |
## 5. 已完成步骤的实现要点（供恢复上下文）

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

## 6. 当前状态 / 下一步候选

- 现状：`refactor` 分支，HEAD `9c622d53`，JVM + Native 全绿。上面功能步骤已归档。
- 下一步候选（需用户确认后实施，建议一次一项）：
  1. L3.4 双缓冲 diff。
  2. L4 续项（Text `maxLines` / `overflow=Ellipsis|Clip` / 对齐）或进入 L5 输入。
  3. L6 tty 适配 / 能力探测（大头，见 todo §9/§10）。

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
