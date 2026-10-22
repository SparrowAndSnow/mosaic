# Mosaic 全项目 TODO / 改进清单

> 归档日期：2026-10-16，分支 `feature/unicode-wide-character-fixes`
> 定位：**全项目功能节点级** TODO（原专项「Unicode 宽度支持」并入 §3）。
> 覆盖：架构 / 渲染 / 缓冲 / 布局 / 文本 / 样式 / 颜色 / 输入 / 能力探测 / tty 适配 / 生命周期 / 动画 / 测试 / 发布。
> 优先级：**P0**=正确性/回归优先；**P1**=高价值优化；**P2**=增强/打磨；**P3**=长期/可选。
> 参考基准：本仓库既有测试与台账（调研文档已在合并前移出仓库）。

> **对账更新：2026-10-22（八）** L7.39 非模态浮层 P1 已归档（`19c291a1`）：`OverlayHost`（base 先测先画 + `matchParentSize` overlay 槽、直接子节点即 layer、单组合仅一个 host）+ 非模态 `Popup`（内容尺寸、base 相对 alignment + offset、鼠标自顶向下命中与层内 shield、popup 外回落 base）。键盘/粘贴仍按树序，modal/backdrop/焦点域/Esc 明确留给 P2；要点见 progress §5。
> **对账更新：2026-10-22（七）** L7.38 标签栏组件 `TabRow`（`ui/TabRow.kt`）已归档（`d5bf3b19`）：单行标签 + 可选 1 格分隔符 + `RowScope` 尾部槽位；`TabRowColors` 六色全部默认 `Color.Unspecified`，不绑定应用调色板；选中/滚动/键盘仍由应用管理。要点见 progress §5。
> **对账更新：2026-10-22（六）** L7.37 `Modifier.clickable` 跨重组按压捕获加固 + 可选双击已归档（`a935b802`）：释放不再依赖节点实例/父链存活，修复子组合与节点替换场景的丢点击；新增 `onDoubleClick`（同元素 400ms 窗口；首击 `onClick`、次击仅 `onDoubleClick`；禁用/移除不触发）。要点见 progress §5。
> **对账更新：2026-10-22（五）** L7.36 双击检测工具（`layout/ClickTracker.kt`）已归档；纯 Kotlin 小工具，配合既有 `onMouseEvent` 做同下标双击判定（默认 400ms 窗口），当时未引入 `Modifier.clickable`（L7.37 已接入）。
> **对账更新：2026-10-22（四）** L7.35 惯性滚动状态（`MomentumScrollState`，`8532ef1e`）已归档；库只提供物理状态 + `advance`，应用自驱 frame loop，默认滚轮一格约 3 行。
> **对账更新：2026-10-22（三）** L7.34 备用屏生命周期已归档（`43c85bb1`，见 §11）；`runMosaic*` 可选 `alternateScreen`，库接管进出序列并在 teardown 中幂等 leave。
> **对账更新：2026-10-22** L7.33 `onGloballyPositioned` 内容未放置修复已归档（Row/LazyColumn 回归）；L7.21~L7.32 归档记录保持不变，L1~L7 主干完成；本文件余下未勾项即后续候选（P1/P2/P3），台账进度以 refactor-progress 为准。
> **对账更新：2026-10-19** L7.20 边框/分隔绘制已归档（`392b7efa`，见 §15）；台账进度以 refactor-progress 为准。
> **对账更新：2026-10-18（二）** 全面对账完成（见 §14「台账同步」✅）：过时项已勾销——static 逐行清理（L3.5）、DEC 2027 探测（L6.2）、右缘裁剪/部分覆盖（L3.4 + L7.2 + L7.10）、drawSolidRect 越界（L7.10）、画布复用（L7.2）；并刷新 §0 矩阵与运行模型/tty 行。
> **对账更新：2026-10-18（refactor 分支）** L7.10/L7.11 已归档（真实终端渲染回归 + showcase 演示样本）。
> **对账更新：2026-10-17（refactor 分支）**
> - 本清单是**总 backlog**；执行进度以 `docs/refactor-progress.md`（推进台账）为准，本文件只标记「已落地 ✅（提交）／仍待办」。
> - ✅ 项对应的重构提交见各条目，实现细节见台账 §5。
> - 第三方库/项目名清理（本文件部分条目含名称）仍按计划在合并回 main 前统一处理。

## 0. 功能域全景矩阵

| 功能域 | 现状 | 最短板 | 借鉴对象 | 优先级 |
| --- | --- | --- | --- | --- |
| 运行模型 | Compose 声明式 + Snapshot 脏标记 + 自适应帧节流（L7.3） | — | — | — |
| 渲染 | cell 级差分（L3.3）+ 右缘/覆盖回归（L3.4）+ static 逐行清理（L3.5）+ 全量重绘不带上滚（L7.10） | — | — | — |
| 缓冲 | `TextPixel` = 簇字符串 + 显式列宽（L2.1）+ 越界裁剪（L7.10） | 部分覆盖/右缘已闭环（L3.4 论证 + L7.2 懒复位） | — | — |
| 宽字符 | 簇级列宽唯一口径（L1.2/L3.1）+ DEC 2027 探测（L6.2） | 逐 rune 后备与光标漂移补偿未做 | — | **P3** |
| 布局 | weight + fillMaxWidth(fraction) + BoxWithConstraints（L4.5~L4.6）+ 布局缓存（L4.7） | gap/overlap 可选 | — | P3 |
| 文本 | AnnotatedString + TextLayout | ellipsis/alignment/scroll 已落地（L4.2~L4.4） | 主流终端 UI 的段落滚动 | P2 |
| 颜色 | 16/256/24bit 输出 + env 探测 | 探测可提升（profile/OSC4） | colorprofile | P3 |
| 输入 | 鼠标/粘贴/clipboard/kitty 键盘全消费（L5.1~L5.6） | 声明式 CellMotion/AllMotion 分档未做（固定 1003） | — | P3 |
| 能力探测 | DA1 + 批量 DECRPM/DCS/OSC + 1s 兜底；DEC 2027（L6.2）+ 探测清单（L6.8） | 超时降级可观测；逐 rune 后备 | — | P3 |
| tty 适配 | JNI/FFM/cinterop 三绑定 + C(posix/windows) + Zig；信号/resize/raw 已核对（L6） | 三套绑定一致性 / Windows 路径待环境验证 | — | **P1** |
| 生命周期 | 异常恢复（L7.4）+ native 信号兜底（L6.4）+ 非交互提示（L7.8）+ 备用屏（L7.34） | 前后台/失焦降帧联动 | — | P3 |
| 动画 | mosaic-animation（Compose port） | 跟随 androidx 升级 | Compose upstream | P3 |
| 测试 | 快照 + TestTerminal + 簇/渲染级断言 + 能力矩阵（L7.9）+ bench（L7.1） | 端到端 golden | — | P3 |

---

## 1. 架构 / 运行模型（mosaic-runtime `mosaic.kt`）

- [x] **P2 帧循环** ✅（L7.3）：`runMosaicComposition` 帧循环按负载自适应——活跃（重组/布局/绘制/动画等待中）~60fps、空闲 ~20fps，替代原 ~1000fps 恒发帧；`MosaicComposition.hasPendingFrameWork()` 判定 + `yield()` 消除动画重注册竞态；空闲帧不触发重绘（`FrameThrottleTest` 3 条）。
- [x] **P2 异常/panic 恢复** ✅（L7.4）：`runMosaicComposition` 捕获非取消异常→写纯文本错误帧（堆栈+节点树）→`cancel()` 收队→rethrow；终端状态恢复由 `withTerminal` 双 `use` 保证；正常取消不报错、不写错误帧（`FrameErrorVisibilityTest` 2 条）。
- [x] **P2 多实例 / 复用** ✅（L7.6，`f679993c`）：awaitComplete 语义明确化——静态无 effect 内容按 run-until-idle 自然返回；无限 effect 保活期间阻塞，仅 `cancel()` 释放；`setContent` 二次调用替换内容并取消旧 content 的 effect；原 "Re-evaluate" TODO 替换为结论化说明（`LifecycleSemanticsTest` 3 条）。
- [ ] **P3 运行时对齐**：`mosaic-animation/` 是手写 Compose animation port，需跟随 androidx 版本（`AnimateAsState`/`Animatable` 等）并评估是否上 `AnimationVector` 表维护成本。
- [ ] **P3 生命周期集成**：`LifecycleOwner`/`LocalTerminalState` 已接入；补齐前后台/焦点与动画节流的联动（如失焦时降帧）。

## 2. 渲染管线（`rendering.kt` / `surface.kt`）

- [x] **P1 行级增量输出** ✅（L3.2，`ef1fe8e7`）：`AnsiIncrementalRendering` 为默认渲染器，按行对比跳过未变行、绝对定位 + `clearLine` 重写变化行；高度变化 / static 存在退化为全量重画。
- [x] **P1 行内段差分** ✅（L3.3，`264f637d`）：cell 级 diff，变化区间按字素簇边界切分后 `goto` + 片段重写；稀疏段就地 patch、稠密/删尾整行 `goto;clearLine` 回退。
- [x] **P1 旧渲染器去重** ✅（L3.8，`282e865b`）：删除全量 `AnsiRendering` 与其专属测试（410 行），`AnsiIncrementalRendering` 为唯一增量渲染器；关键行为覆盖已由 `AnsiIncrementalRenderingTest` 承接。
- [x] **P2 宽格 diff 右缘防护 + 覆盖回归** ✅（L3.4，`55dfaae1`）：`appendRowRange` 加 `rowEnd`，宽簇在末列放不下时以同风格空格占位（杜绝换行）；新增 `VirtualTerminal`（解析 ANSI→屏幕网格）与 `CellDiffRenderingTest`（15 条，覆盖宽→窄/窄→宽/ZWJ/keycap/相邻宽簇/右缘/resize）。调研中"覆盖残留"经 crafted-cell 全场景验证为**结构上已排除**（占位身份区分 + 簇边界扩展 + 区间合并），无需额外逻辑改动。
- [x] **P0 全量重绘不复位上滚** ✅（L7.10，`27d038a0`）：首帧 / resize / static 全量重绘帧末尾不再带 `\r\n`——真实终端末行收到换行=整屏上滚一行，此后所有绝对坐标 diff 全部错位（双 tabBar / 上一页残留；内存 `VirtualTerminal` 不模拟滚动所以先测不出）；改为行间 CRLF、末行不终止；`VirtualTerminal` 补 `CSI F` 回列 0。回归：`FullRedrawNoScrollTest`。
- [x] **P2 静态输出优化** ✅（L3.5，`736dd9a9`）：static 帧改为**逐行 `clearLine` + 重写**（替代整屏 `clearDisplay` 兜底），静态/活区混合同规处理；`AnsiIncrementalRenderingTest` 增至 16 条。
- [x] **P2 闪烁防护** ✅（L3.6，`e1e17349`）：synchronized output（DEC 2026）按能力包裹整帧；无该能力且可切换光标时改为「光标 hide/show 包裹」（帧首 `hide` / 帧尾 `show`），空帧不包裹避免闪；`cursorVisibility=false` 时退化为裸帧。启动期 hide 一次/退出恢复逻辑不变。
- [x] **P2 输出缓冲** ✅（L3.7，`70b4067b`）：`render` 结果不再 `.toString()` + 单次 `print`，改由 `FrameWriter` 增量 UTF-8 编码到固定 8KB 缓冲并分块写出（码点原子落块、代理对读前视、孤立代理替换 `?`）；JVM `System.out.write` + 每帧 flush，Native `posix.write(STDOUT)` 循环写。
- [x] **P3 光标 API** ✅（L7.31，`b96a8c64`）：`Cursor(modifier, shape, blink)` 为零尺寸布局标记（`Modifier.offset` 选格，位置来自根坐标，padding/对齐/滚动自然生效）；`CursorShape`（Block/Underline/Bar × blink）→ DECSCUSR；渲染器帧尾 CUP + 形状 + show，越界不显示、宽簇续列吸附簇头、移出组合即 hide 并复位形状、退出复位默认形状；仅 `cursorVisibility` 能力下生效。限制：静态日志行未计入偏移、嵌套滚动裁剪不感知（KDoc 已写明）。`CursorRenderingTest` 10 条。

## 3. Unicode / 宽字符（原专项，含全部已知问题）

> 基准：引入改造 `7354a45f`（layout/render/input）、`cef427b8`（宽度表实现）、`528c0f67`（vendor）+ `3b081378`（生成器）。
> 结论：`drawText`"每码点一格 + isWideContinuation + 跳格"对单码点宽字符正确，对多码点字素簇错误。
> 状态：核心问题已由簇级重写修复（L1.2 度量 / L2.1 cell / L3.1 绘制）；本节保留原描述作背景，✅ 为已落地、未 ✅ 为仍待办。

### Bug（渲染正确性）
- [x] **P0 P1 ZWJ 序列丢连接符** ✅（L3.1，`4c368769` 按簇绘制后不再逐码点覆盖）。背景：逐码点 `charWidth(ZWJ)=0` → 成员互相覆盖、`👨👩👧` 画成 3 个独立 emoji。
- [x] **P0 P1 keycap 丢 VS16** ✅（L3.1）：整簇写入首格，VS16/组合符不再丢。背景：原 `FE0F/20E3` w=0 被覆盖、`#️⃣` 与 `#⃣` 渲染相同。
- [x] **P1 P2 override 测/绘两层不一致** ✅（L1.2/L2.1 统一到簇级 `graphemeWidth` 单一口径）。背景：原 `CharWidth.kt` 带 `emojiOverrideRanges` 而 `TextLayout` 无 override，同块「布局 1 格、绘制 2 格」。

### 边界 / 健壮性
- [x] **右缘裁剪** ✅（L3.4 `appendRowRange` 放不下以同风格空格占位/不写 + L7.10 簇/画布越界裁剪）：宽簇头落在末列不换行、续列不越界；内部缓冲末列“半格”不外泄（diff 层以空格归一）。`CellDiffRenderingTest` 右缘 3 例 + `TextMeasuredDrawTest` 越界 2 例。
- [x] **P1 `isWideContinuation` 生命周期** ✅（L2.1 起改为显式「簇字符串 + 列宽占位」cell，不再依赖该标志生命周期）。
- [x] **`drawSolidRect`/簇写入越界裁剪** ✅（L7.10，`27d038a0`）：clamp 改到 canvas 边界（含 placement 平移）——越出屏幕的绘制不再抛错、右缘不再崩；`OffsetTest` 越界改裁剪 + `TextMeasuredDrawTest` 增 2 例。

### 修复方向
- [x] **P0 方案 A（治本）** ✅（核心已落地）：L2.1 cell 升级为「簇字符串 + 显式列宽」、L3.1 `drawText` 按扩展字素簇迭代（整簇写首格、续格预留占位、surface 只发首格）、L1.2 测/绘统一簇级宽度。剩余子项见下方未勾条目。
  - [x] 部分覆盖清理：已由 L3.4 crafted-cell 全场景验证为**结构上已排除**（占位身份区分 + 簇边界扩展 + 区间合并），`CellDiffRenderingTest` 宽→窄/窄→宽/整行清理 3 例锁定；叠加 L7.2 cell 级懒复位保证同帧旧格不残留，无需额外逻辑改动。
  - [x] 右缘裁剪：见上「边界 / 健壮性」✅（L3.4/L7.10）。
- [x] **P1 DEC 2027（UnicodeCore）探测** ✅（L6.2，`b84c0dd9`，见 §10）：`CSI ?2027$p` 探测 → `Capabilities.graphemeClustering`，支持时 `CSI ?2027h` 启用（退出恢复）。剩余：逐 rune 后备宽度口径与光标漂移补偿未做（口径落法见 progress §6，P3）。
- [x] **P3 方案 B（最小改动，治标）** ✅（已被方案 A 取代，不再实施）：原案 `w==0` 并入当前格，家庭 emoji 仍画 6 格，留档。
- [ ] **P3 输入侧组合字符**：IME/预组合输入（grapheme 重组）在 `EventParser` 层如何表示/透传（当前只有码点流）。

## 4. 缓冲 / cell 模型（`surface.kt`）
- [x] **P0 升级 `TextPixel`** ✅（L2.1，`a99e5100`）：`Int codePoint + Boolean isWideContinuation` → `String content + Int width`（含 `markAsContinuation()`；`appendRowTo` 跳格逻辑已同步）。
- [x] **P1 部分覆盖清理** ✅（L3.4 结构论证 + L7.2 cell 级懒复位）：宽格头/续列被覆盖不再留半格——占位身份区分 + 簇边界扩展 + 区间合并（`CellDiffRenderingTest` 3 例）+ 帧末 `resetCellsNotWritten` 清同帧未写旧格。
- [x] **P2 画布复用/内存** ✅（L7.2 ①/⑧）：`TextSurface` 不再每帧 `new`（`reset()` 复用）+ cell 级懒复位（`SurfaceReuseTest`/`SurfaceLazyResetTest`）；剩余「跨帧差分保留」未做（现为每帧全量重绘后 diff，收益有限，P3）。
- [x] **P2 ISO 控制字符处理** ✅（L7.12，`ab05f570`）：C0/DEL/C1 控制符按「过滤控制符」口径净化为**非打印**——0 列、不绘制、不进 ANSI 流（原 1 列 + 原样发出会在真实终端触发 CR/BS/TAB/ESC，打乱帧）；**不做 tab 展开**（需列上下文，破坏测/绘唯一口径不变量）；`\n` 保持行分隔。回归：`IsoControlRenderingTest`（7）+ `GraphemeWidthTest` 增 1。

## 5. 布局（`layout/` + `ui/`）
- [x] **P1 Text 内建排版（簇级安全换行）** ✅（L4.1，`18428264`）：`Text` 参数化 `softWrap`（默认 false 不回归），开启时按入向 `maxWidth` 约束在**字素簇边界**软换行；ZWJ 家庭/组合字符/变体/旗帜永不拆开，超宽簇独占行，空段落保留；约束变化即时重排。测试：`TextLayoutWrapTest` + `TextWrapRenderingTest`。
- [x] **P2 换行/截断/对齐/滚动参数** ✅（L4.2 `68d19e72` + L4.3 `94c37ff7` + L4.4 `1e99eb40`）：`Text` 增加 `maxLines`/`overflow`（Clip/Ellipsis 簇安全截断）、`textAlign`（Start/Center/End，有界宽行偏移、Start/无界零回归）、`scroll`（垂直查看窗口，配合 `maxLines` 滚动切片，越界 clamp 不至于空窗）。
- [x] **P2 约束扩展（比例布局）** ✅（方向见台账 L4.5 调研、实现 L4.6 `92297096`）：比例/百分比**不新增自造 modifier**，遵循 Compose 语法——等分/占剩余用 `weight`（已有）、占父级可用比例用 `fillMaxWidth/H/S(fraction)`（已有）、精确混合比例走 `BoxWithConstraints`（单槽位子组合实现）。剩余可选：gap/overlap（间距/负重叠）。
- [x] **P2 布局缓存** ✅（L4.7，`4af27661`）：`needLayout` 时不再全树重跑测量——每个节点以（Constraints, layoutVersion）为键缓存最近一次测量，命中即整子树跳过；失效覆盖结构变化与测量期读取的 snapshot 状态。单槽位 memo（LRU-of-1）已够用，缓解高频重排与同构重布局。剩余优化：apply 失效查扫改反向索引、未放置子树裁剪（台账 L4.7）。
- [x] **P0 BoxWithConstraints 0×0 空白** ✅（L7.10，`27d038a0`）：子组合根被量成 0×0，绘制遍历跳过零尺寸容器 → 整棵比例子树一笔不画（Layout 页第 4 节空白；节点树/尺寸均正常）。修复为对 subRoot 补一次真实 measure（结果丢弃，matchParentSize / alignment 语义不变）；回归 `BoxWithConstraintsRenderingTest`。
- [x] **P2 Modifier 缺口** ✅（L7.13，`118c1a2e`）：约束便捷族早已齐备——`widthIn/heightIn/sizeIn`、`requiredWidth/Height/Size(+In)`、`fillMaxWidth/H/S(fraction)`、`wrapContent*/defaultMinSize`（`layout/Size.kt`）；真实缺口 `onGloballyPositioned` 放置回调已于 L7.13 补齐（根坐标+尺寸，仅变化时触发）。`margin` 明确不补（Compose 无 margin）。
- [x] **P2 intrinsic 复用** ✅（L7.25，`d51f3ae4`）：`min/maxIntrinsicWidth/Height` 已在每个节点加**单槽 memo**（与布局缓存同 `layoutVersion` 门控、同一 snapshot 依赖口径），重复同参查询不再重走子树；实际 measure 时丢弃 memo 保证不陈旧。剩余：多槽（min/max 交替同参还未互不驱逐）。
- [x] **P3 焦点管理** ✅（L7.19）：`focusable`/`onFocusChanged`/`FocusRequester` + 组合级 `FocusManager`（Tab/Shift+Tab 环形遍历、点击聚焦、锚点祖先 hasFocus）。**方向键导航原语**（箭头遍历 focus 环）仍缺——Compose 无通用实现、主流 TUI 靠各自状态机，属可选项。
- [x] **P3 `weight(fill)` 语义核对** ✅（L7.24，`3edfd316`）：`weight(fill=true)` 的加权子节点按**槽位尺寸**记账与放置（fill=false 与非加权仍用实测内容尺寸），子节点自身宽度保持内容宽不拉伸——Row/Column 总尺寸=槽位总和，等宽列不再塌缩、整槽点击区域恢复。决策见台账 L7.24：框架级「报告宽度收敛到约束」不可行，改为定点记账。

## 6. 文本 / 样式（`text/` + `ui/Text.kt`）
- [x] **P1 测/绘口径统一** ✅（L1.2/L2.1）：`TextLayout` 与 `drawText` 已共用簇级宽度；勿在 `TextLayout` 单独加 override（会和 `©=1/☀︎=1` 锁定语义冲突）。
- [ ] **P2 `AnnotatedString` 维护**：470 行手写富文本 + `SpanStyleRange`；核对与 androidx compose ui text 语义差异（合并/覆盖规则），或评估是否足够维持现状。
- [x] **P2 换行/截断/对齐/滚动 API** ✅（L4.1~L4.4）：`softWrap`/`maxLines`/`overflow`/`textAlign`/`scroll` 全部落地。
- [ ] **P3 widget 补齐**：标签栏已由 L7.38 `TabRow`（`ui/TabRow.kt`）落地——单行可点击/悬停标签、选中与悬停背景、可选 1 格分隔符、`RowScope` 尾部槽位，默认配色中性（详见 progress §5）；Chart/Gauge/Table/Scrollbar 等仍未做，确定是否继续做第一方 widget 集（影响发布节奏）。

## 7. 颜色（`ansi.kt` / `Color.kt`）
- [x] **P3 `toAnsi16Code/toAnsi256Code`** ✅（L7.26，`27849ee1`）：约简算法已与参考行为逐点核对一致并清掉归属注释；语义用金表测试锁定（16 色取整/亮色判定、256 灰阶分段 232..255、6x6x6 cube 每通道 `/255*5` 取整及其临界点 76/77、127/128 等）。
- [ ] **P3 探测增强**：`detectAnsiLevel` 现为 env 启发式（`COLORTERM/TERM/WT_SESSION/ConEmuANSI`）；可加 colorprofile 式 `TERM_PROGRAM` 特判与 **OSC 4 调色板查询**（染主题色），并统一 16/256 判定。
- [ ] **P3 主题/调色板**：`SystemThemeEvent` 已解析；可提供 `LocalTerminalState.theme` 驱动的默认样式容器（另见 §10）。

## 8. 输入 / 事件（`mosaic-tty-terminal` + `mosaic-terminal`）
- [x] **P1 鼠标消费** ✅（L5.1，`bccb50d9`）：`Modifier.onMouseEvent`（返回 Boolean 消费）+ 绝对坐标命中测试（z 序在上先收，回退祖先）；终端 1006 + 1000/1003 opt-in（`runMosaic*` `mouse=true`）。注：主流 TUI 式「CellMotion/AllMotion 声明式分档」未做（现固定 all-motion 1003），需要时再接。
- [x] **P1 粘贴通路** ✅（L5.2，`bccb50d9`；+L7.5 未终止 paste 的 Ctrl-C 逃生口）：`Modifier.onPaste`（祖先先于子树）+ DECSET 2004 opt-in；帧循环将 paste 内容累积为单串，期间不触发按键。注：文本插入原语（光标/IME）归 §15 输入控件。
- [x] **P2 kitty 键盘协议开关** ✅（L5.3，`bccb50d9`）：探测 `kittyKeyboard` 后自动 push `CSI>23u`、退出 pop 恢复；`toKeyEventOrNull` 只放行 press，repeat/release 过滤。注：无 `KeyboardEnhancementsMsg` 二次确认（探测期 `CSI?u` 回复已确认支持）。
- [x] **P2 clipboard（OSC 52）** ✅（L5.4，`9af01ce1`）：`ClipboardEvent` 解析（base64/UTF-8；`!`→null）+ `Terminal.requestClipboardRead`/`writeClipboard` + runtime `LocalClipboard`（suspend `getText/setText`，读=请求→await 回复 2s 超时）。
- [x] **P2 事件背压/策略** ✅（L5.5，`bf564374`）：`events` channel 64 改 `SUSPEND` + 生产者 `send`（零丢弃）；帧循环批内 `Motion/Drag` 合并只留最新坐标；`TtyTerminalBackpressureTest`（500 字符 0 丢失）+ `MouseEventTest` 增 3 条。
- [x] **P3 特殊键/修饰** ✅（L5.6，`56a55c3e`）：PUA 特殊键映射验证（F1~F35 等）；`repeat` 并入 `press` 投递（恢复长按自动重复一致性）、`release` 丢弃；`text` 关联文本优先于码点（CSI u 无文本段时 null，无冲突）。`CompatTest` 8 条。

## 9. tty 适配（大头：三套绑定 + C 双平台 + Zig）
> 现状：`mosaic-tty` = Kotlin `expect/actual` + 底层 C（`src/commonMain/c/mosaic-{tty,streams,utils,test}-{posix,windows}.c`，约 2.4k 行）+ 三套宿主绑定
> （`jvmMain` **JNI**(`com_jakewharton_mosaic_tty_Jni.c`)、`jvmJdk22` **FFM/jextract**(`Libmosaic`)、`nativeMain` **cinterop**）+ Zig 0.15.1 交叉编译。

### 9.1 宿主绑定
- [x] **P1 三套绑定一致性** ✅（L7.32，`65631043`）：`TtyContractTest`（11 条）统一锁定缓冲区间（越界→`IndexOutOfBoundsException`）、关闭后调用（除 `reset`/`close` 幂等外→`IllegalStateException`）、offset/count 语义、超时/非阻塞/中断返回 0、0x00..0xFF 字节往返（Windows 控制台桩仅 ASCII，跳过）；JNI/FFM 补齐参数校验与关闭守卫、三绑定回调「先摘除后释放」、JNI 回调初始化失败清理、FFM 先释放原生再关回调 arena、native `reset` 错误传播、JNI/FFM 进入 raw mode 补装与 native 一致的 POSIX 信号兜底；修正 `TestTtyTest` stderr 断言笔误。验证：JNI 116 / native 122 / FFM 分发（JDK 25 多版本 jar）116 全绿；tty-terminal 269/268 回归；`:mosaic-tty:spotlessCheck` 通过。EINTR 由共享 C 层处理，未做信号注入。
- [ ] **P2 JNI 维护**：`Jni.c` 1140 行手写 JNI + 回调对象生命周期（`ttyCallbackInit/Free`）；核对全局引用泄漏/线程（`setCallback` 可在任意线程触发，文档已声明）。
- [ ] **P2 FFM (jextract) 同步**：JDK22+ 走 `Libmosaic`；`jextract.libraries.register('mosaic')` 头文件变化需重生成，构建里确认了生成物与源码同步（发布产物不漂移）。
- [ ] **P3 Zig 工具链**：Zig 0.15.1 仅用于交叉编译 C；评估升级锁定、缓存目录（`.zig-cache`）gitignore 状态与 CI 可重复性。

### 9.2 POSIX（linux / macos）
- [x] **P1 raw mode 核对** ✅（L6.3，审计无代码改动）：`mosaic-tty-posix.c` 清位与 glibc `cfmakeraw(3)` 权威定义**逐位一致**（8 iffags、OPOST、lflag ECHO/ECHONL/ICANON/ISIG/IEXTEN、CS8、VMIN=1/VTIME=0）；`IUTF8`/`HUPCL`/`CLOCAL` 保留正确（raw 已去 ICANON，该位无影响）；TCSAFLUSH + 失败恢复完备。对照表见 progress §5 L6.3。
- [x] **P1 resize** ✅（L6.5，`59284788`）：`SIGWINCH` 与 in-band（2048）互斥由构造保证（in-band 支持则不装 SIGWINCH handler），收敛 `size` StateFlow 单点；补「in-band 无初始报告时用 `currentSize()` 播种」＋两平台互斥/播种回归测试。详见 progress §5 L6.5。
- [ ] **P2 macOS 特判**：`TIOCGWINSZ`/termios 在 macOS 的差异（`HUPCL`/`c_cc`）、终端类型差异与 Linux 不同的行为。
- [x] **P2 信号处理加强** ✅（L6.4，`66c4f2d6`）：SIGINT/SIGTERM 已由生命周期收尾机制覆盖（native POSIX 信号处理器→teardown→重发信号 / JVM shutdown hook）；补 **`SIGSEGV/SIGILL/SIGPIPE` 的 C 级兜底**（async-safe 恢复 termios + 唤醒 reader + 保留终止语义重发），随 native raw mode 生命周期装/卸。详见 progress §5 L6.4。
- [ ] **P3 非 UTF-8 locale**：`read`/`write` 假定 UTF-8；核对 locale 非 UTF-8 时字节流的解析与写回（或明确文档限制）。

### 9.3 Windows
- [ ] **P1 ConPTY / VT**：raw mode 用 `GetConsoleMode/SetConsoleMode` + `ENABLE_VIRTUAL_TERMINAL_INPUT/PROCESSING`（已有）；核对失败回退（非 VT 终端）、ConPTY 与真实 console 差异。
- [ ] **P1 输入多路复用**：Windows 需在 `ReadConsoleInput` 循环里分发 `KEY_EVENT`/`MOUSE_EVENT`/`WINDOW_BUFFER_SIZE_EVENT`/`FOCUS_EVENT`（已有分支）；核对中文字符（宽字符 `KEY_EVENT` 的 `WCHAR`）与 UTF-8 转换、Ime 持有状态。
- [ ] **P2 光标/颜色**：`ENABLE_VIRTUAL_TERMINAL_PROCESSING` 下的 ANSI 输出与 24bit；核对 Windows Terminal 与 conhost 差异。
- [ ] **P3 codepage**：`chcp 65001`/`CP_UTF8` 处理与恢复（若不做需文档化）。
- [ ] **P3 focus 回调**：仅 Windows 提供 `onFocus`；跨平台对等（POSIX 走 DECRPM focus 事件）已在 `TtyTerminal` 层补齐——保持两个通路语义一致。

### 9.4 抽象 / API
- [x] **P1 `Callback.onKey/onMouse` 空挂** ✅（L6.1，`8c489971`）：判定为死回调并**移除**——C 头 `ONKey/ONMouse` 为 `// TODO params` 占位从未调用；Windows C 层将 `KEY/MOUSE_EVENT` 转 VT 字节流走共享 `EventParser`（回调仅用于 Focus/Resize）；实现类只有空实现。三套绑定（JNI/FFM/cinterop）+ `mosaic-tty.h` 结构同步删除，测试同步清理。
- [ ] **P2 单例绑定约束**：`Tty.tryBind` 同时只能一个实例（文档已声明）；评估是否在错误路径上更友好。
- [x] **P2 读写缓冲** ✅（L6.6，`164d1c70`）：审计结论——控制序列写层（`TtyTerminal.write(s)`）已有完整循环、主渲染走 `FrameWriter`/`writeScreenBytes`（native 循环、jvm PrintStream 原子全量）；唯一边界 bug 为 `reset()` 在 `close()` 后无指针守卫（三平台悬垂），已补守卫使其 closed 后幂等空操作，并增 close/reset-after-close 幂等测试 2 条。
- [ ] **P3 测试桩对齐**：`mosaic-test-{posix,windows}.c`（800+ 行）真实度；`TerminalTester` 覆盖能力矩阵（见 §13）。

## 10. 能力探测（`TtyTerminal.kt`）
- [x] **P1 DEC 2027** ✅（L6.2，`b84c0dd9`）：补 `CSI ?2027$p` 探测 → `Capabilities.graphemeClustering`；支持时自动 `CSI ?2027h` 启用（退出恢复）、set/permanentlySet 不重复；`TtyTerminalDec2027Test`（5 态）+ `EventParser` 2027 例。宽度口径落法见 progress §6 决策：能力暴露 + 终端侧启用，逐 rune 后备未做（理由已记录）。
- [x] **P2 探测列表完备性** ✅（L6.8，`7ba3a7ef`）：审计对账 13 项探测全对应暴露能力；theme 996/997 确认非笔误；「逐项重查」评估驳回（不响应终端逐项同样不响应 + N+1 轮询 + 破坏安全契约）；新增 `TtyTerminalProbeInventoryTest` 锁定完整探测清单防失。
- [x] **P2 超时兜底** ✅（L7.14，`7b5aee1f`）：`withTimeoutOrNull(1s)` 已有；无响应终端降级路径不再静默——超时即向 stderr 打英文提示（含卡住阶段），`MOSAIC_TTY_TERMINAL_DEBUG=true` 追加已确认能力清单；`stderrPrint` 双平台实现（L7.8 提示同构）；`TtyTerminalBootstrapTimeoutNoticeTest`（4）+ `TtyTerminalTimeoutNoticeJvmTest`（1 真实超时 stderr 捕获）。
- [ ] **P3 env 兜底**：`detectAnsiLevel` 扩展 `TERM_PROGRAM`（iTerm2/WezTerm…）与 kitty/OSC 12 兜底（按终端程序探测颜色的思路）。

## 11. 生命周期 / 集成
- [x] **P1 异常安全** ✅（L6.7，`d6926d77`）：teardown 提为幂等函数（`AtomicBoolean` 单次），`close()` 同步先跑再 `closeJob.cancel()`，保证先于 `tty.close()` 完成恢复；finalization hook 复用同一 teardown 兜底信号/shutdown 路径。异常/取消/信号三条路径核实全覆盖；消除对 `Unconfined` 内联续传的隐性依赖。
- [x] **P2 非交互策略** ✅（L7.8，`b60dd5b8`）：`withTerminal` 落到 `NonInteractiveTerminal`（`Ignore` 绑定失败 / `AssumeAndIgnore`）时先向 stderr 打 `NonInteractiveNotice`（JVM `System.err` / native posix `STDERR_FILENO`），能力降级不再静默。
- [x] **P2 备用屏生命周期** ✅（L7.34，`43c85bb1`）：`runMosaic` / `runMosaicBlocking` / `runMosaicMain` 增 `alternateScreen: Boolean = false` 并经 `withTerminal` → `Tty.asTerminalIn` 透传；真实 TTY 上在 raw mode 后、能力探测前写 enter（`CSI ?1049h`），幂等 teardown 先恢复全部被 toggle 的模式再写 leave（`CSI ?1049l`），正常 close/取消/信号收尾统一覆盖，非交互/绑定失败不发送。`ansi.kt` 集中常量；`TtyTerminalAlternateScreenTest` 3 条 + `TtyTerminalLifecycleTest` 增 2 条（幂等/取消）。已知限制：硬崩溃仅 C 级 termios 恢复、可能来不及 leave。
- [ ] **P3 静态日志**：`StaticEffect`/`static()` 与整帧渲染并发写 stdout 的原子性（`rendering.kt` 已考虑 `clearDisplay` 兜底）。

## 12. 动画（`mosaic-animation/`）
- [x] **P3 帧时钟集成** ✅（L7.30，`fb5d2b2b`）：动画走内部 `BroadcastFrameClock`（`startFrameListener` 逐外帧 `internalClock.sendFrame(nanos)`，时间=单调钟）；`delay(1)` 恒发已被 L7.3 自适应节流取代（动画忙 `hasAwaiters`→60fps / 空闲 20fps、空闲帧不重绘）；低电量=空闲 20fps 有界轮询（输入延迟 ≤50ms 取舍）。补「空闲→动画切回 60fps」测试 1 条（`FrameThrottleTest`）。剩余可选：事件驱动全休眠（阻塞等输入、零轮询）。
- [x] **P3 API 完整性** ✅（L7.29，`10bd9d22`）：`Animatable/AnimateAsState/…` port 自 androidx；跟踪上游 API 与内部 `AnimationVector` 表维护；评估新增 `animateColorAsState`（线性 RGB）等——**结论：家族齐全（Float/Int/IntOffset/IntSize/Color），`Color.VectorConverter` 已用 Oklab 感知空间（黑→白中点 sRGB≈99 ≪ 朴素 128 / 线性 188），优于线性 RGB，维持现状**；`VectorConvertersTest`(6) + `AnimateColorAsStateTest`(4) 补齐单测，jvm/native 全绿。
- [ ] **P3 与渲染增量联动**：动画高频帧目前每帧整帧重建（§2 未做时），是行级增量改造的最大受益者。

## 13. 测试 / 质量
- [x] **P1 宽字符渲染级回归** ✅（L3.4，`55dfaae1`）：`CellDiffRenderingTest` 补齐 ZWJ / keycap / 宽→窄覆盖清理 / 窄→宽强制宽 / 相邻宽簇 / 右缘 / resize 的**端到端屏幕态断言**（经 `VirtualTerminal` 重放）。剩余：不确定宽（VS16 结尾）在真实终端与预测口径分叉的探测与挂接（归 L6 DEC 2027）。
- [x] **P1 `TextLayoutWidthTest`** ✅（L7.7，`01d5d04d`）：增加「测量与绘制一致」逐列对拍断言——按测量 `LineMeasure` 逐列核对绘制（簇头列文本 / 宽簇 placeholder 续列 / 右沿 == totalWidth / `layout.width` == 最宽行），覆盖 ascii/cjk/ZWJ/flags/keycap/VS16/内嵌空格/softWrap/Clip/Ellipsis/scroll/Annotated。
- [x] **P2 快照体系扩展** ✅（L7.9，`a9467f04`）：`mosaic-testing` 增加 `CapabilityMatrix`（ansiLevel × kittyUnderline × synchronizedOutput × 2027 代表性组合）+ `runMosaicTestForEach`；`TestMosaic` 补齐焦点事件注入（`sendFocusEvent` → 生命周期 ON_PAUSE/ON_RESUME）。
- [x] **P2 性能基准** ✅（L7.1）：`samples/bench`（commonMain 单一驱动）对 render 单行 delta / 全量重建 / layout-pass / EventParser 建模，JVM+native 对照（见 `docs/benchmarks.md`）。
- [x] **P3 端到端 golden** ✅（L7.27，`0754a318`）：新增 `EndToEndGoldenTest`——组合→测量→绘制→ANSI 帧→内存 `VirtualTerminal` 回放，断言**最终屏幕状态**（用户所见），非单帧字节流。已有 4 场景（宽字符混排 / weight 槽位列 / maxLines 裁剪窗口 / 增量 diff 后屏幕）。剩余（可选）：真实 pty 快照（script/pty 回放）留待 CI。

## 14. 文档 / 发布
- [x] **P2 README/CHANGELOG** ✅（L7.28，`6070c929`）：README 增「Capabilities & Known limitations」段（字素簇文本/增量渲染/布局/交互/动画 + 非 UTF-8、真 TTY、组合根无界测量等限制）；CHANGELOG [Unreleased] 补重构带来的用户可见能力条目。措辞全程中性（零第三方名）。
- [ ] **P3 API 稳定性**：0.x 阶段明确 breaking 窗口；§4/§3 方案 A 涉及 `TextPixel`（internal）不破坏 public API，但 `Text`/`Modifier` 新增入口需同步 `CHANGELOG`。
- [x] **P3 示例补齐（基础）** ✅（L7.11，`7aa7f3ec`）：`samples/showcase`（仅公共 API）落地 Unicode / Text / Layout / Input / Animation 五页演示——鼠标 / 粘贴 / 剪贴板 / 滚动 / 自动滚动+Ellipsis / 字符串截断（Clip vs Ellipsis）/ 动画均已示例化；剩余按需补齐。
- [x] **P2 台账同步** ✅（2026-10-18 对账）：逐条核对待办与台账/代码，勾销过时项（static 清理 L3.5、DEC 2027 L6.2、右缘/覆盖 L3.4 + L7.10、drawSolidRect 越界 L7.10、画布复用 L7.2）；后续每次归档步骤后同步 ✅ 与提交号。

---

## 15. 外部组件待办（只在内部核心重构完成后实现）

> 决策（2026-10-17，`refactor` 分支）：这些是**最外层的组合组件**，依赖内部核心（L4 布局、L5 输入、L6 tty 适配）先重构到位；当前阶段**只登记不实现**。优先级统一排在内部重构之后，具体顺序待内部核心完成后重排。实现原则：先实现 + 单元测试 + 用户确认，再归档。

- [x] **滚动容器（最大布局缺口）** ✅（L7.15，`cc62a232`）：`ScrollState`（`value`/`maxValue` 快照态 + `scrollBy`/`scrollTo` clamp + `rememberScrollState`）+ `verticalScroll`/`horizontalScroll`（滚动轴无界测量、视口=约束、内容位移取负，clip 在外 offset 在内）+ `clipToBounds()` + 画布裁剪矩形；顺带修复绘制三处（簇裁剪后续列卡死 / `maxValue` 快照可观察 / 格子首写重置消除背景残留），滚轮内建（一格 3 行）；showcase 第 6 页演示三类容器；要点见 progress §5。
- [x] **惰性列表** ✅（L7.16，`f4b6f9a1`；L7.16.1，`e9ca8239` 根因修复）：`LazyColumn`（仅组合可视区子项、代价 O(视口) 与总数无关）+ `LazyListScope` `items(count)`/`items(List)` + 固定 `itemHeight` 槽位；复用滚动容器/裁剪/滚轮；`KeyEvent` 增 `repeat` 标志（L7.16.1 补 `CSI <key>;<mod>:<type>~` 解析、撤销 600ms 时间窗兜底，Page 键只响应全新 press）；showcase 第 7 页（500 行）。`LazyRow` 仍待办；要点见 progress §5。
- [x] **输入控件** ✅（L7.17，`7d07e645`）：`BasicTextField`（单行可编辑 + 反显块光标 + IME/粘贴 + 字素级编辑），受控组件、内部文本镜像防连击丢字；showcase 第 8 页 Field。多字段/点击聚焦待 §15「焦点 / 键盘导航原语」。
- [x] **鼠标交互** ✅（L7.18 + L7.37）：`Modifier.hoverable(onHoverChanged)`（enter/leave 会话级对账）+ `Modifier.clickable(enabled, onClickLabel, onDoubleClick, onClick)`（主键按下捕获 state 与按下边界，界内松开触发，越界/禁用/移除取消；可选双击：首击 `onClick`、同元素 400ms 内次击仅 `onDoubleClick`）；showcase TabBar 已改由二者驱动；跨重组加固与双击要点见 progress §5。
- [x] **焦点 / 键盘导航原语** ✅（L7.19）：`focusable(enabled)`（Tab/Shift+Tab 环形遍历 + 点击聚焦）+ `onFocusChanged { FocusState }`（hasFocus=自/后代、isFocused=自身）+ `FocusRequester`（程序化 requestFocus）；`BasicTextField` 已接入——仅聚焦的字段响应输入/显示光标，多字段可同屏；showcase Field 页双字段 + 聚焦状态行；要点见 progress §5。
- [x] **边框 / 分隔绘制** ✅（L7.20，`392b7efa`）：`Modifier.border(width=1, color)`（`LayoutModifier` 内凹 + `DrawModifier` 角/边条，width>1 实心环）+ `HorizontalDivider`/`VerticalDivider`（thickness/color/startIndent/endIndent，占满约束主向）；要点见 progress §5。
- [x] **定位回调** ✅（L7.13，`118c1a2e`）：`Modifier.onGloballyPositioned { position, size -> }`（根坐标 + 尺寸，仅变化时触发）。
- [x] **动画容器** ✅（L7.21）：`Crossfade`（水平推进叠层：旧页左滑出 / 新页右滑入，容器裁剪）+ `AnimatedVisibility`（`EnterTransition`/`ExitTransition` + `EnterExitState`，expand/shrink 尺寸动画 + slide 位移动画，fade 因终端无 alpha 而省略，退出完成后内容才移除）+ `MutableTransitionState` 变体；showcase Animation 页增补两段演示；要点见 progress §5。
- [x] **第一方 widget 集（标签栏）** ✅（L7.38，`d5bf3b19`）：`TabRow` 落地（todo §6 已有 P3 条目）；charts / tables / scrollbars 仍待办。
- [ ] **P3 弹窗 / 浮层**：设计见 [dialog-overlay-design.md](dialog-overlay-design.md)（方案 C）。**P1 非模态已完成** ✅（L7.39，`19c291a1`）：`OverlayHost` + 非模态 `Popup`（base/overlay 槽测量绘制、层序、alignment + offset、popup 内鼠标屏蔽与外部回落、单 host 约束；键盘/粘贴仍按树序）。**P2 下一步**：`Dialog`、`Popup(modal = true)`、`OverlayBackdrop`、根入口 key/mouse/paste 门控与 modal 屏障、`FocusManager` 活动域/焦点保存恢复、Esc。**P3 打磨**：hover 屏蔽、按压捕获取消、关闭后 hover 重算、光标可见性、golden 与示例。
- [ ] 备注：`margin` **不是**缺口——Compose 没有 `margin`，间距用 `padding` 或外层间隔表达。
