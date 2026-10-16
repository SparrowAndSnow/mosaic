# 主流 TUI 的 Unicode/宽字符架构调研 — 与 Mosaic 对比

> 注：这是第一轮宽字符视角的对比纪要；逐功能（架构/渲染/布局/样式/输入/能力探测/测试/性能）的深度审计见
> `docs/tui-arch-deep-comparison.md`。
>
> 目的：`DrawScope`/`TextPixel`/`TextSurface` 的宽字符处理存在问题（见 `docs/todo.md`），
> 调研同类 cell-grid 型 TUI 的实现方式，为 Mosaic 的修复（todo 方案 A）定标。
> 对比对象拉到本地（`/tmp/ratatui-clone`、`/tmp/bubbletea-clone`、`/tmp/uv`、`/tmp/ansi-x`）：
> - [Ratatui](https://github.com/ratatui/ratatui)（Rust，cell buffer + diff 渲染）
> - [BubbleTea v2](https://github.com/charmbracelet/bubbletea)（Go；渲染层 `charmbracelet/ultraviolet`，宽度 `charmbracelet/x/ansi`）
> - 宽度口径参考：`rivo/uniseg`、`go-runewidth`、`clipperhouse/displaywidth`、我们本地的 `unicode-rs/unicode-width`（上游生成器）

## 总览

| 维度 | Mosaic（现状） | Ratatui | BubbleTea v2 (ultraviolet) |
| --- | --- | --- | --- |
| 缓冲模型 | cell 网格（`TextSurface`） | cell 网格（`Buffer`） | cell 网格（`ScreenBuffer`/`Line`） |
| 每格内容 | 单个 `Int` 码点 + `isWideContinuation: Boolean` | 字素簇字符串 `Option<CompactString>` + `skip: bool` | 字素簇字符串 `Content string` + 显式 `Width int` |
| 写入粒度 | 逐**码点**迭代，`charWidth(cp)` 定宽 | 逐**字素簇**（`UnicodeSegmentation::graphemes`） | 逐**字素簇** |
| 测宽函数 | `TextLayout` 用字符串级 `unicodeWidth`（与绘制不同函数） | 测/绘统一用 `unicode-width::UnicodeWidthStr` | 测/绘统一用可插拔 `WidthMethod` |
| 宽格续列 | 独立布尔标志，仅在"同格重写指定码点"时复位 | `skip` + 写后 `reset()` 续格 + diff 游标推进 | `Width==0` 占位格；覆盖时主动清理整段宽格 |
| 右缘裁剪 | 无（续列出界就丢，字符溢出画布） | `remaining_width` 放不下就不写 | 超宽时写同风格空格并截断 |
| 终端宽口径 | 未区分 | 按 `unicode-width`（序列折叠成 2） | 可切换：`GraphemeWidth`（簇折叠）vs `WcWidth`（逐 rune，兼容无 DEC 2027 的终端） |

## Ratatui（与 Mosaic 最像：cell + diff）

`ratatui-core/src/buffer/cell.rs`:
- `Cell { symbol: Option<CompactString>, skip: bool, ... }`；注释明确 "accepts unicode grapheme clusters which might take up more than one cell"。
- `skip`：diff 时跳过的占位格。

`ratatui-core/src/buffer/buffer.rs` `set_stringn()`（核心）:
```rust
UnicodeSegmentation::graphemes(string, true)
    .filter(非控制符)
    .map(|symbol| (symbol, symbol.cell_width()))   // 簇字符串的宽度
    .filter(|(_, w)| *w > 0)                        // 丢零宽
for (symbol, width) in graphemes {
    self[(x, y)].set_symbol(symbol)...;             // 整簇写进一格
    let next = x + width;
    while x < next { self[(x, y)].reset(); x += 1; } // 续格 reset 为空格
}
```
关键：**整簇（含 ZWJ/VS16）落在一个 cell**，续格全部 `reset()`。→ Mosaic 丢 ZWJ/VS16 的根因（单 Int 格 + 逐码点）在此天然不存在。

`ratatui-core/src/buffer/diff.rs`：多宽字符的 diff 是重头——
- 跳过 `skip` 格（宽字符的续列）。
- 按 `cell_width()` 推进终端游标 `pos += width-1`，避免续列被误覆盖。
- 处理"不确定宽度"符号（如以 VS16 结尾的 emoji，某些终端画 1 格画 2 格），先清预留列再写。
- 处理"宽字形未变但样式变"时续列上的旧样式残留。

`ratatui-core/src/buffer/cell_width.rs`：在 `unicode-width` 之上对 `U+FF9E/U+FF9F` 做 **+1 终端补偿**（半角浊音符号在终端上各自占一格）——我们 vendor 的 16.0 表已直接把这两个算成 1，方向一致。

## BubbleTea v2 / ultraviolet / x/ansi

`charmbracelet/ultraviolet` `cell.go`:
```go
type Cell struct {
    Content string // 单个字素簇
    Style Style
    Link  Link
    Width int      // 簇的等宽列数（set 时算好存起来）
}
func (c *Cell) isWidePlaceholder() bool { return c != nil && c.Width == 0 }
```

`buffer.go` `Line.Set()`（宽格占位 + 生命周期）:
- 宽格写完后 `for j := 1; j < cw { l[x+j] = Cell{} }` 打 `Width==0` 占位格。
- **覆盖头列或占位列时，反向找到所属宽格并把整段清空**（`l[x+j].Empty()`），避免残留半格。
- 右缘超宽：`if x+cw > lineWidth { 写同风格空格 }`（裁剪 + 补白）。
- `Line.String()` 跳过 `IsZero()`（含占位格）。

`x/ansi` 宽度口径（`width.go`/`method.go`/`wcwidth.go`）:
- `StringWidth` = `GraphemeWidth`：按簇测宽（`clipperhouse/displaywidth`）。
- `StringWidthWc` = `WcWidth`：逐 rune 求和（`go-runewidth` + Mn/Me/Cf 置零表）。
- 注释明确指出：ZWJ 家族在支持 **DEC 2027（Grapheme Clustering Mode）**的终端上按簇折叠（2 格），不支持时终端逐码点进格（`👨👩👧` 画 8 格）。BubbleTea 让调用方选口径。
- BubbleTea v2 的 curses renderer 直接使用 `uv.ScreenBuffer`，并把 `cellbuf.Method == ansi.GraphemeWidth` 纳入渲染逻辑。

`widecell_placeholder_test.go` 甚至复现了一个真实回归：占位格继承宽格 Style 后，`IsZero()` 不再能识别它而 `Width==0` 仍可——两个检测路径出现分歧导致 diff 把插入点选进宽字形中间。**这正是 Mosaic `isWideContinuation` 布尔标志那类隐患在参考实现里的翻版**，它们用"显式 Width 字段 + 回归测试"来兜底。

## 对照 Mosaic：问题映射

| Mosaic 问题（`docs/todo.md`） | 对照参考实现 |
| --- | --- |
| P1 ZWJ 丢连接符（`DrawScope.kt:278/295`） | 参考实现逐簇写整簇字符串，不可能丢 ZWJ |
| P1 keycap 丢 VS16 | 同上；`set_stringn`/`Line.Set` 以簇为单位 |
| P2 override emoji 测/绘不一致（`CharWidth.kt` vs `TextLayout.kt:58`） | 两者都"测/绘用同一宽度函数"，不存在两套口径 |
| 续列标志生命周期/残留 | ultraviolet 用 `Width==0` 占位格 + 覆盖时清理整段；Ratatui 用 `skip` + diff 游标推进 |
| 宽字符位于最后一列溢出 | ultraviolet 右缘裁到行宽并写同风格空格；Ratatui 放不下就不写 |
| （潜在）DEC 2027 终端差异性 | 参考实现暴露两种宽度口径（`GraphemeWidth`/`WcWidth`）供选择 |

## 结论 / 对 Mosaic 的启示

1. **todo 方案 A 得到两大参考实现证实**：cell 应存"字素簇字符串"，绘制按簇迭代、整簇落一格、续列按簇宽预留；`TextPixel` 从 `Int codePoint + Boolean` 升级为 `String content + Int width`（或 `IntArray`）。这与 Ratatui、ultraviolet 的模型一致。
2. **测宽与绘制应共用同一函数**（簇级 `unicodeWidth`），从源头消除 P2 的两层不一致。
3. **占位格用"宽度为 0 的显式占位"而非布尔标志**，并做"部分覆盖时清理/修复整段宽格"的生命周期管理；参考实现的 `widecell_placeholder_test` 说明要防"两种检测口径不一致"。
4. **右缘裁剪**：宽格放不下时补同风格空格，避免溢出。
5. 可后续暴露宽度口径选择（簇折叠 vs 逐 rune），以兼容不支持 DEC 2027 的终端；Mosaic 当前按簇折叠（与 `unicode-width` 一致）即可。

## 源码参考位置

- Ratatui：`ratatui-core/src/buffer/cell.rs`、`buffer.rs`（`set_stringn`）、`diff.rs`、`cell_width.rs`
- ultraviolet：`cell.go`、`buffer.go`（`Line.Set`）、`widecell_placeholder_test.go`
- x/ansi：`width.go`、`method.go`、`wcwidth.go`、`parser_decode.go`（`FirstGraphemeCluster`）
- BubbleTea v2：`cursed_renderer.go`（`uv.ScreenBuffer` + `cellbuf.Method`）
