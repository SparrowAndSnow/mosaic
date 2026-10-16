# Unicode 宽度支持 — TODO / 遗留问题

> 记录 wide-char 改造相关的待办与已知问题。审查基准：
> 引入改造的提交 `7354a45f`（layout/render/input 宽字符支持）、`326d5971`（采用 unicode-width-kotlin）、
> `3ce27084`（本地 vendor）+ `2e0d3b2f`（生成器）。调研背景见 `docs/unicode-width-research.md`。
>
> 审查方式：在 `TextSurface` + `TextCanvasDrawScope.drawText` 上做逐码点输出验证。
> 结论：`drawText` 的"每码点一格 + `isWideContinuation` 续列 + surface 跳格"模型对**单码点宽字符**正确
> （`A中B` → `U+0041 U+4E2D U+0042`，4 格 OK），但对**多码点字素簇**是错的。

## Bug（渲染正确性）

- [ ] **P1 ZWJ 序列丢连接符（family/序列 emoji 崩坏）**
  - 根因：`drawText` 逐码点 `charWidth(ZWJ)=0` → 不推进列，下一成员覆盖同格的 ZWJ。
  - 实测：输入 `U+1F468 U+200D U+1F469 U+200D U+1F467` → 输出字节 `U+1F468 U+1F469 U+1F467`（ZWJ 全丢）。
  - 后果：终端按 3 个独立 emoji 渲染（6 格），而布局用 `unicodeWidth()` 测 2 格 → 后续内容重叠；近右缘 `canvas[row, col]` 越界 `check` 崩溃。
  - 位置：`mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/layout/DrawScope.kt:278`、`295`。
  - 方案：按字素簇渲染（见"修复方向"A）。

- [ ] **P1 keycap 全限定形式丢 VS16**
  - 根因：`# U+FE0F U+20E3` 中 `FE0F`、`20E3` 均 w=0，挤进同一格，`FE0F` 被覆盖。
  - 实测：输入 `U+0023 U+FE0F U+20E3` → 输出 `U+0023 U+20E3`；`#️⃣` 与 `#⃣` 渲染结果完全一样。
  - 后果：测宽 2（`TextLayoutWidthTest` 已锁），但渲染语义退化为 1 格文本 keycap。
  - 位置：同上 `DrawScope.kt`。

- [ ] **P2 `emojiOverrideRanges` 测量/绘制两层不一致**
  - 根因：`charWidth` 带 override（`CharWidth.kt`），而 `TextLayout.textWidth()` 用字符串级 `unicodeWidth()`（无 override）。
  - 实测：`charWidth(0x1F321)=2`、`unicodeWidth("🌡")=1`。
  - 后果：被覆盖块内 `Emoji=Yes && Presentation=No` 的约 91 个码点布局 1 格、绘制 2 格，右侧兄弟内容压到第二格。
  - 位置：`mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/text/CharWidth.kt:3`、`TextLayout.kt:58`。

## 边界 / 健壮性

- [ ] 宽字符落在最后一列：`DrawScope.kt:297` 的 `continuationCol < width` 直接跳过，不预留第二格，字形向右溢出且无裁剪兜底。
- [ ] `isWideContinuation` 生命周期：仅"同格被写指定码点"时复位（`DrawScope.kt:331`）；`drawRect` 用 `UnspecifiedCodePoint` 填充背景不复位。当前被 `render` skip + `AnsiRendering` 每行 `clearLine`（`rendering.kt`）掩盖，属隐藏耦合。
- [ ] `drawSolidRect` clamp 是 node-local（clamp 到 `width`/`height`），叠加 `translationX/Y` 后右缘越界仍会抛 `check`；越界从"必崩"变为"多数静默裁剪、右缘仍崩"。

## 性能

- [ ] `AnsiRendering` 每帧全量重建画布；`drawText` 逐码点 `charWidth` = 三层表查找 + `emojiOverrideRanges` 二分（ASCII 也跑）。可加 `codepoint >= 0x1F000` 短路或按码点缓存。属可优化项，非瓶颈。

## 测试缺口

- [ ] 现有 `AnsiRenderingTest` 只锁 `A中B` 单宽字符；无 ZWJ / keycap / override 的渲染级断言，以上 Bug 1-3 测不出来。建议先补渲染回归测试复现，再修。
- [ ] `TextLayoutWidthTest` 只锁测量值（`👨👩👧`=2），不验证绘制输出，测量/绘制不一致无覆盖。

## 修复方向

> 方案 A 的实现模型已对照主流 cell-grid TUI 实证（**Ratatui**、**BubbleTea v2/ultraviolet**，
> 两者均"整字素簇落一格 + 续格预留"），详见 `docs/tui-arch-comparison.md`。实施时吸收参考实现的设计要点：

- **A（推荐，治本）**：`drawText` 按扩展字素簇迭代，用簇子串 `unicodeWidth()` 定占格；把簇完整码点（含 ZWJ/VS16）写进簇首格，其后 w-1 格仅预留续列。需把 `TextPixel` 从单 `Int` 码点升级为"簇字符串 + 显式 Width"（`surface` 只发首格、跳续列）。可同时修 Bug 1/2，并使绘制与布局测宽严格一致。
  - 占位格语义用"宽度 0"而非独立布尔标志（参考 `ultraviolet` 的 `Width==0` placeholder），并做"部分覆盖时清理/修复整段宽格"的生命周期管理；注意防"多种检测口径不一致"（参考其 `widecell_placeholder_test`）。
  - 宽格落在最后一列时裁到行宽并补同风格空格（参考 `ultraviolet` 的右缘处理；Ratatui 放不下就不写）。
  - 测宽与绘制统一走簇级 `unicodeWidth`（消除 P2 的两层口径）。
- **B（最小改动，治标）**：`w==0` 码点改为"并入当前格"而非覆盖，可保住 ZWJ/VS16；但家庭 emoji 仍画成 6 格。
- **Bug 3**：随 A 一起，使 draw 与 measure 统一走串级宽（不推荐单独在 `TextLayout` 加 override，会和 `©`=1 / `☀︎`=1 的锁定语义冲突）。
- **（可选，后续）** 暴露终端宽度口径选择：簇折叠（`GraphemeWidth`，等同现 `unicode-width`）vs 逐 rune（`WcWidth`，兼容无 DEC 2027 的终端）；当前按簇折叠即可。
