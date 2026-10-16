# Unicode 字符宽度支持调研

> 归档日期：2026-10-16
> 分支：`feature/unicode-wide-character-fixes`
> 目的：为修复 Mosaic 的 Unicode 宽字符支持（布局 / 渲染 / 输入）提供事实依据与实现方案。

## 1. 结论速览

- 所有现代 TUI / 终端实现最终都收敛到同一模型：**单码点宽度（UAX#11 East Asian Width）+ 零宽集合 + 一层「序列/字素簇」级规则**。
- 单码点宽度共识：
  - NEUTRAL / NARROW -> 1 列；
  - WIDE / FULLWIDTH -> 2 列；
  - AMBIGUOUS -> 非 CJK 语境按 1（`string-width` 默认 `ambiguousIsNarrow`、`go-runewidth`/`unicode-width` 默认同此；CJK 语境有 `width_cjk()` 可选 2）；
  - COMBINING / DEFAULT_IGNORABLE / 控制码（非 DEL/BS） -> 0。
- 序列/字素簇级规则（现代实现都会覆盖）：
  - emoji 展示序列（+VS16）宽 2、文本展示序列（+VS15）宽 1；
  - ZWJ emoji 序列整体宽 2（不能逐码点累加）；
  - 旗帜 = 两个 Regional Indicator 配对宽 2（单 RI 宽 1）；
  - 肤色修饰符（1F3FB..1F3FF）并入 base 不额外占列。
- Mosaic 现状（`mosaic-runtime/.../text/CharWidth.kt`）= 手写区间表 + 逐码点求和：**缺序列/字素簇语义、表不精确、渲染层未消费列宽**。

## 2. 实测基线（Node `string-width`，作为对照基准）

| 输入 | 期望宽度 | 说明 |
|---|---|---|
| `中` / `각` / `Ａ` | 2 | CJK / Hangul 音节 / 全角 |
| `ｱ` / `à` | 1 | 半角 / 预组合拉丁 |
| `e`+U+0301 | 1 | 组合字符不额外占列 |
| `\u200B` `\u00AD` | 0 | ZWSP / 软连字符（Default_Ignorable） |
| `#`+U+20E3 | 2 | keycap 序列 |
| `©` / `☀`（无 VS16） | 1 | 文本展示 |
| `©️` / `☀️`（+VS16） | 2 | emoji 展示 |
| `👨‍👩‍👧`（多个 ZWJ） | 2 | ZWJ emoji 序列整体 |
| `🇨🇳` / 单 `🇨` | 2 / 1 | 旗帜配对 / 单 RI |
| `가`（L+V jamo） | 2 | Hangul Jamo 折叠 |

## 3. 各实现调研

### 3.1 Rust `unicode-rs/unicode-width`（ratatui/crossterm 依赖）
- 权威 UAX#11 严格实现（本次基于 0.2.2，Unicode 16.0）：单码点查生成表，字符串级带特例（`\r\n`=1、ZWJ emoji 序列=2、Arabic Lam-Alef / 引号+FE00-02 / Tifinagh 等连字规则）。
- `Ambiguous` 默认 1，`width_cjk()` 另给 2。
- 表格 `tables.rs`（~23k 行）由脚本从 Unicode 官方数据生成。

### 3.2 `ratatui` 渲染集成
- `Line::width` = 各 Span 宽度求和（内部用 unicode-width）。
- 渲染用 `unicode-segmentation::graphemes` 切簇，`symbol.width()` 取列宽；**宽字符只占第 1 格，后续列 `reset()` 占位**（`src/buffer/buffer.rs` 的 `set_stringn`），diff 时按两侧宽度取大值跳过。
- 布局 / 对齐 / 截断全部按“列宽”而非码点数。

### 3.3 Go `go-runewidth`（bubbletea/tview 用）
- `RuneWidth` 用懒构建的双平面 LUT（`[2][0x110000]byte`，EA 与 non-EA 两套）；`EastAsianWidth` 由 locale 决定（Windows 看 ConsoleCP，Windows Terminal 特判关闭）。
- 提供 `Truncate` / `Wrap` / `FillLeft/Right` 等基于宽度的 API。

### 3.4 Node `string-width`（ink 用，最现代）
- 用 `Intl.Segmenter` 按字素簇切分，再套多层规则：RGI emoji、ZWJ 序列（2+ Extended_Pictographic）、Hangul Jamo 折叠、spacing marks / 全半角 Forms 尾随宽度、Default_Ignorable 归零。
- 提供对齐模块（`get-east-asian-width`）。

### 3.5 `mordant`（ajalt，JVM，生成表 + 二分 + Trie）
- `internal/cellwidth.kt`：`cellWidth(cp)` ASCII 快路径 + 二分生成区间表；控制码 0、DEL/BS -1。
- `stringCellWidth(str)`：逐码点 + `EMOJI_SEQUENCES` IntTrie 状态机（生成自 emoji-zwj-sequences 的 RGI_ZWJ + emoji-sequences 的 RGI_Modifier），完整序列整体结算 2。
- `scripts/generate_cellwidth_table.py`：数据源 = UCD latest `DerivedGeneralCategory`（Me/Mn/Cc->0）+ `EastAsianWidth`（F/W->2）+ 手写 CF 零宽表 + 手写 emoji override 块；表外默认 1。
- `Span.cellWidth` 用 `by lazy` 缓存，wrap/table/progressbar 统一消费。
- **注意坑**：Trie 状态机无“字符串结束时未闭合序列”的收尾结算，且 `sumSinceZwj += width` 出现两次（同一码点双加）——源码推导 `"👍"`=0、`"中👍"`=1（应分别为 2、3）；现有测试未覆盖。照抄需修正或改 grapheme-first。

### 3.6 `unicode-width-kotlin`（KotlinMania，Apache-2.0）
- Rust `unicode-width` 的 KMP 逐行移植（`// port-lint` 溯源），标为 in-progress。
- `Tables.kt`（~10.5k 行）由 `scripts/unicode.py` 生成；`WidthInfo` 把 ZWJ / VS16 / ligature-transparent 编码进 raw 值，即连 Rust 的 emoji/ZWJ/连字规则一并带来。
- API：`Int.unicodeWidth()` / `Int.unicodeWidthCjk()`、`width(codePoint): Int?`，全部实现在 `commonMain`（全平台同一张表）。
- 已发布 Maven Central `io.github.kotlinmania:unicode-width-kotlin`（**可作为 Mosaic 直接依赖替代手写 CharWidth**；接入前需核验最新版本/成熟度）。

### 3.7 `kotter`（varabyte，Apache-2.0）
- `kotter/src/.../terminal/TextMetrics.kt`：`wcwidth(ucs)` = 手写组合/宽表 + `bisearch` 二分（combining 表注明源自 Unicode 16.0 `UnicodeData.txt`：Mn+Me+Cf - U+00AD + U+1160-11FF + U+200B + U+1F3FB-1F3FF）。
- 轻量自实现字素簇：RI 配对成旗帜、ZWJ 连带下一码元、零宽扩展符（`wcwidth==0` 判定）都并入一簇。
- `calculateClusterWidth`：簇内扫 **VS16->2、VS15->1**，否则返回首个 base 的 wcwidth。
- 附 `graphemeClusterLengthAt` / `graphemeStartIndex`（反向定位，供光标/截断）、`renderWidthOf(range)`（范围测量避免重复 substring）。
- 是 Mosaic 渲染/光标集成层的最好模板。

### 3.8 Mosaic 现状与差距
- `mosaic-runtime/src/commonMain/kotlin/com/jakewharton/mosaic/text/CharWidth.kt`：手写 zeroWidth + wide 区间表 + 二分。
- 差距（对照 §2 实测）：
  - 无 emoji presentation：`©️☀️` 仍算 1（应 2）；ZWJ 序列 `👨‍👩‍👧` 累加成 6（应 2）。
  - Hangul 只标 L jamo 与音节段，vowel/trailing jamo（U+1160..11FF）漏为 1 -> `가` 算 3。
  - RI 整段标 2 -> 单 `🇨`=2（应 1）、旗帜=4（应 2）。
  - keycap `#`+U+20E3：U+20E3 不在任何表 -> 算 1（应 2）。
  - 无字素簇概念、无 VS15/16、无控制/制表符专门处理、表非 Unicode 数据生成（易过时）。

## 4. Kotlin 生态辅助库

### 4.1 `kotlin-codepoints`（de.cketti.unicode，Mosaic 已依赖 0.11.0）
- 纯 UTF-16 <-> 码点工具（`CodePoints`、`String.codePointAt`、`Appendable.appendCodePoint` 等），**不含宽度/EAW**。
- Mosaic 用途：按码点遍历/重建文本（`TextLayout.measure()`、surface 渲染），正确处理代理对。是码点层基座。
- 同仓库有 `kotlin-codepoints-deluxe`（增强 API，0.10.0），仍不含宽度。

### 4.2 `ktunstrnorm`（iseki0，MIT）/ `doistx-normalize`（Doist，MIT）
- 都是 KMP 的 **UAX#15 规范化（NFC/NFD/NFKC/NFKD）包装，零数据、纯调平台 API**，不含宽度：
  - JVM：`java.text.Normalizer`；JS/Wasm：`String.prototype.normalize`；Apple：Foundation/CoreFoundation；Windows：`Normaliz.dll`。
  - Linux/Native 是差异点：`ktunstrnorm` 运行时 `dlopen` ICU（`libicu*/libicuuc.so.{60..79}`）+ `unorm2_*`；`doistx` 编译时直链系统 `libunistring`（>=0.9.7）。
- `ktunstrnorm` 未发布 Maven Central（自建）；`doistx` 已发布 `com.doist.x:normalize`。
- 对 Mosaic 的价值：**输入/比较层可选做 NFC 归一**（JVM 直接用 `java.text.Normalizer` 即可）；渲染/宽度路径**不要 NFKC**（会变字形/宽度，破坏所见即所得）。这两个库不解决宽度问题。

### 4.3 附带发现：libunistring 的宽度 API
- 本机 `/usr/include/uniwidth.h` 提供 `uc_width` / `u8_width` / `u32_width`（wcwidth 等价物，内部为 libunistring 生成的 EAW 表，CJK locale 时 Ambiguous->2）——可作为 Linux Native 上「现成宽度库」的另一选项。

## 5. 修复方案

### 路线 A（首选，外部依赖）
- 直接依赖 `io.github.kotlinmania:unicode-width-kotlin` 替代手写查询层，配合 kotter 式字素簇/VS 处理做序列级与渲染集成。
- 优点：表跟 Unicode 版本走、含全部高级规则、KMP 全平台。风险：上游标 in-progress，需先核验坐标/版本/行为并备份行为测试。

### 路线 B（次选，保持 0 依赖自维护）
- 采用「mordant 生成表 + kotter 字素簇」组合：
  1. **M1 码点级精确表**：抄 `mordant` 的 `generate_cellwidth_table.py` 思路（数据源 UCD latest：Mn/Me/Cc->0、F/W->2、手写 CF 零宽 + emoji override），生成 Kotlin 区间表，二分 + ASCII 快路径；（可选）锁 Unicode 版本并写生成脚本入仓。
  2. **M2 序列/字素簇级**：自实现轻量字素簇（RI 配对、ZWJ、零宽扩展），`calculateClusterWidth` 处理 VS15/16；emoji/ZWJ 序列整体 2。
  3. **M3 渲染集成**：布局 `TextLayout.measure()` 改按列宽；surface 渲染宽字符第二列占位/reset（学 ratatui `set_stringn`）；对齐/截断/填充按列宽；提供 `graphemeClusterLengthAt`/`graphemeStartIndex` 供光标定位。
  4. **M4 输入侧**：`mosaic-tty-terminal` 解码后按簇组装，避免半个 emoji/组合符单独成事件；宽度计算两侧一致。
  5. **M5 测试**：表驱动对照 §2 实测基线 + 现有布局/渲染快照。

### 取舍与不做
- 不实现 Arabic/Tifinagh 等深度脚本连字（终端 UI 收益低），文档标注「已知不支持」。
- Ambiguous 暂默认按 1（对齐非 CJK 语境），按需再做 `*Cjk()` 开关。
- 不做 go-runewidth 式 2MB LUT（Kotlin/JS 内存代价大）；区间二分 O(log n) 足够。

## 6. 参考来源

- Rust `unicode-rs/unicode-width`：https://github.com/unicode-rs/unicode-width
- `ratatui`：https://github.com/ratatui/ratatui
- `go-runewidth`：https://github.com/mattn/go-runewidth
- `string-width`（sindresorhus）：https://github.com/sindresorhus/string-width
- `mordant`：https://github.com/ajalt/mordant
- `unicode-width-kotlin`：https://github.com/KotlinMania/unicode-width-kotlin
- `kotter`：https://github.com/varabyte/kotter
- `kotlin-codepoints`：https://github.com/cketti/kotlin-codepoints
- `ktunstrnorm`：https://github.com/iseki0/ktunstrnorm
- `doistx-normalize`：https://github.com/Doist/doistx-normalize
- UAX#11 East Asian Width：https://www.unicode.org/reports/tr11/

## 7. 路线 A 集成记录（更新于 2026-10-16）

- 已接入 `io.github.kotlinmania:unicode-width-kotlin:0.1.1`（mosaic-runtime）：`CharWidth.kt` 委托 `Int.unicodeWidth()`（控制符 `?: 1`）+ 8 段小 emoji override 表（Mahjong/Playing Cards/Chess/Xiangqi/Misc/Emoticons/Transport/Supplemental/Extended-A 按 2，对齐 mordant）。
- `TextLayout.kt` 测宽改用字符串级 `String.unicodeWidth()`（AnnotatedString 走 `.text`），获得旗帜/ZWJ/VS16/Hangul Jamo 序列规则。
- 行为验证（jvm 实测）：`👨‍👩‍👧`=2、`🇨🇳`=2、单 RI=1、`©`=1 / `©️`=2、`☀️`=2 / `☀︎`=1、`각`=2 / `가`=2、`A中B`=4。
- 已更新 `CharWidthTest`（reserved/Ambiguous 边界按库修正，单 RI=1），新增 `TextLayoutWidthTest` 锁定字符串级行为。全仓 `jvmTest` 通过。
- **已知缺口（0.1.1 未实现的字符串规则）**：keycap 序列 `#`+U+20E3 返回 1（应为 2）；control/tab 宽度按 1（unicode-width 对 control 的宽度为 0/1 语义；Mosaic 按字符渲染 tab，另行处理）。若需要可在此层补 keycap override，或等上游完善。
- 尚未做：输入侧（`mosaic-tty-terminal`）簇组装（M4）、非 JVM 平台适配验证、渲染层宽字符第二列占位的行为测试（占位逻辑已存在于 `DrawScope.kt`/`surface.kt`）。
