# 微基准（L7.1）

> 用途：① 验证 **native 性能能否达到主流实时标准**（60fps 帧预算）；② 作为重构前后（§2 渲染 / §5 布局）性能对照基线。
> 对应 `docs/todo.md` §13 P2「性能基准」。
> JVM 与 native 跑**完全相同的 commonMain 驱动**，逐平台独立读数、纵向对比。

## 运行

```sh
# JVM
./gradlew :samples:bench:runJvm

# Native（linux x64 release kexe）
./gradlew :samples:bench:linkReleaseExecutableLinuxX64
./samples/bench/build/bin/linuxX64/releaseExecutable/mosaic-benchmark.kexe
```

实现位于 `samples/bench/src/commonMain/kotlin/example/Main.kt`（四个 render 负载 + 一个 parser 负载，
best-of-3 rounds）。render 负载驱动真实 `Mosaic` 组合，`onDraw` 里做 `draw()` + `static()`
（镜像生产渲染器计算屏幕内容的阶段），计时覆盖 **重组 + 布局 + 绘制** 整条 pipeline。

## 负载说明

| 负载 | 测量内容 |
| --- | --- |
| `render-draw-idle` | 已组好的静止树直接 `draw()`：只测 **布局+绘制核心**（无重组） |
| `render-one-row-delta` | 每帧只有状态行变化：轻量增量更新路径 |
| `render-full-rebuild` | 每帧 30 行数据全变：全量重建路径（最坏情况） |
| `layout-pass` | 每帧切换树形：强制重组 + measureAndPlace |
| `event-parser` | 真实 PTY 720 字节字符流（每字节=1 `KeyboardEvent`），跨缓冲区边界 |

## 基线（2026-10-18 · linux x64 · JDK25 / K/N release）

> 下表为**低分配 + 热路径改造后**数字（surface 复用 / ASCII 快路径 / raw-Int 打包 / 源串-区间单元格 / 簇切分 ASCII 快路径 / 对齐列懒算 / 布局-绘制共用簇测量 / cell 级懒复位）。
> 改造前基线见下文「优化记录」。

| 负载 | JVM | Native(kexe) | 说明 |
| --- | --- | --- | --- |
| `render-draw-idle` | ~31 µs | **~52 µs** | **native ≈ 1.7×（原 2.4×；绝对 -90%）** |
| `render-one-row-delta` | ~185 µs | ~240 µs | native ≈ 1.3× |
| `render-full-rebuild` | ~297 µs | ~707 µs | native ≈ 2.4×（绝对 -84%） |
| `layout-pass` | ~240 µs | ~462 µs | native ≈ 1.9× |
| `event-parser` | ~174 µs | ~134 µs | native ≈ 0.77× |

## 优化记录（L7.2 绘制热路径低分配，2026-10-18）

| 改造 | native `render-draw-idle` | 说明 |
| --- | --- | --- |
| 改造前基线 | ~510 µs | 每帧 `new TextSurface` = 4800 个 TextPixel 对象；每簇 `strWidth` 逐码点返回 `Pair<Int,WidthInfo>`（每码点分配） |
| +每帧复用 TextSurface（`reset()` 清空） | ~467 µs | 消除 4800 对象/帧；native -8.4%，JVM -5% |
| +`strWidth` printable-ASCII 快路径 | ~388 µs | 纯 ASCII 范围直接 `to-from`，跳过逐码点 Pair 状态机；native -17%（vs 复用后）、-24%（vs 原始） |
| +宽度状态机 raw-Int 打包（`packResult` 打包 width+raw 到单 Int，`widthOf=(p.x shl 28) shr 28` 恢复） | ~354 µs | 状态机/查找不再分配 `Pair<Int,WidthInfo>`；12 个 raw 版谓词直读打包值；native -8.8%（vs ASCII 后）、-31%（vs 原始） |
| +单元格「源串+区间」模型（TextPixel 存 `textSource`/`textStart`/`textEnd`，`drawText` 不再逐簇 `substring`；渲染/差分按区间读出与比较） | ~326 µs | 消除绘制热路径**最后一处每簇分配**；`CellSliceTest` 锁 source 身份/偏移/零长续格/copyFrom/跨源相等；native -8%（vs 打包后）、-36%（vs 原始） |
| `graphemeClusterWidth` 上层簇切分 ASCII 快路径（`forEachGraphemeClusterRange` 对 printable-ASCII/tab 单码点跳过 `gcbClassOf`/`isExtendedPictographic`/`inCbClassOf`/`codepointAt`；GB9b Prepend 例外已排除） | ~205 µs | **gprofng 采样定位**：簇切分族原占 draw-idle ~65%；native -37%、JVM -37%；`GraphemeFastPathTest`（ref 对拍 6 条）+ 官方套件回归 |
| 对齐列懒算（`alignedColumn` 改懒 lambda，Start/无界不再每行 `graphemeWidth()`） | ~129 µs | **根因**：`Text.drawBehind` 默认 Start 对齐也每帧白算整行宽度；native -37%、JVM -41%；全负载回归绿 |
| 布局/绘制共用一次簇测量（`LineMeasure`：measure 期切分+测宽每行一次并缓存，`Text` 经 `drawMeasuredText` 复用；`alignedColumn` 复用 `totalWidth`） | ~129→~65 µs | native **-50%**、JVM ~58→~38 µs（-34%）；`TextMeasuredDrawTest` 对拍两路径逐格/ANSI 一致（5 条） |
| cell 级懒复位（`beginDraw` 清 per-cell written 位，`resetCellsNotWritten` 只复位本帧未写格；替代全量 4800 格 6 字段 reset——**量化确认全量 reset 占 draw-idle ~37%**） | ~65→~52 µs | native **-20%**、JVM ~37→~31 µs（-16%）；`SurfaceLazyResetTest`（5 条：未写格/跨帧残留/样式+placeholder 清理/同行移出格/宽格延续）+ `MosaicTest` 移格回归 |
- JVM 对照：~209 → ~31 µs（原始 **-85%**）；native ~510 → ~52 µs（原始 **-90%**）。
- 结论：native draw-idle **~52µs**（同内容下约为 Rust 系纯渲染参照 ~224µs 的四分之一）；剩余热点扁平（遍历+逐格写 ~50%、GC ~9%），无单一可再 C 化的巨大点。

**阶段拆分（2026-10-18）**

- 绘制核心权重最高：`render-draw-idle`（纯布局+绘制）native ~510µs，占完整帧 ~70%。
- 重组层（完整帧 − draw-idle）≈ JVM 163µs / native 212µs → native 仅 **~1.3×**，并非主因。
- 结论：native 与 JVM 的差距**集中在「布局+绘制」热路径（我们自己的代码）约 2.4×**，
  不在 Compose 重组层。原因方向：K/N 对分配/虚调用不友好（无逃逸分析、ARC 记账、AOT 内联受限）。

> 阶段拆分里的 510µs/2.4× 为改造前读数；改造后绘制核心已回落至 ~388µs / 2.07×。

## 与主流标准对照

- **帧预算**：主流交互式 TUI 按 60fps（≈16.6ms/帧）或更低刷新。native 最坏情况
  `render-full-rebuild` ≈ 1.28ms，只占 60fps 预算的 **~8%**；常规增量帧 ~0.7ms，占 ~4%。
  至少有 **>10× 余量**留给应用自身逻辑。
- **输入吞吐**：native `event-parser` ≈ 0.19 µs/事件 ≈ **~520 万事件/秒**，粘贴大段文本/滚动风暴
  不可能成为瓶颈（配合 L5.5 背压保证不丢事件）。
- **同机实测参照（2026-10-18 · 本机 i5-10210U，内容同为 120×40/33 行 dashboard）**：
  - 命令式 Rust 系（widget 直绘 + 缓冲 diff）：渲染 ≈ 0.22ms/帧 + 全缓冲 diff ≈ 0.15ms。
  - 命令式 Go 系（内容生成 + 解析 + diff + 输出全链路）：单行更新 ≈ 0.62ms、全量重建 ≈ 0.88ms。
  - 本库 JVM：draw-idle ~0.030ms、全量重建 ~0.39–0.44ms（JIT 热身后逐次波动）；本库 native：
    draw-idle ~0.054ms、全量重建 ~0.70ms（读数稳定）。
  - 结论（改造后）：**native draw-idle（布局+绘制）已稳超 Rust 系纯渲染参照**（~0.054 vs ~0.22ms）；
    native 全量重建 ~0.70ms vs Go 系全链路 ~0.88ms（快 ~20%）；量纲差异见注意事项——含重组的
    native 全帧只比为纯渲染的 Rust 系参照慢 ~3×、比同口径 Go 系更快。
- **结论**：**native 已达到甚至反超主流参照**——最坏全屏重建在 60fps 预算内剩余 ~23× 余量。

## 注意事项

- 本基准测的是应用侧成本（重组+布局+绘制），**不含** ANSI diff 序列化与真实 tty 写 I/O
  （internal 渲染器，sample 不可见；旧 JVM-only 全含 diff 的读数高约 25–30%）。
  对照实现为参考：Rust 系数字为纯 draw/diff（无内容生成、无状态簿记）；Go 系与本库同口径
  （含内容生成）；故 native 相对 Rust 的实际差距会小于表面数字。
- **native 慢点已定位：绘制/布局核心 ≈2.4×，重组层 ≈1.3×，字节解析反而快**（0.39×）。
  说明「native 不该比 JVM 慢」成立——差距源自我们热路径代码对 K/N 不够友好（分配 + 虚调用 +
  内联受限），**低分配改造（§2/§5）是追上 JVM 的直接手段**，是本项目性能主战场。
- **native 与 JVM 的剩余差距在重组层（Compose）+ K/N 内存管理，GC 调优无效**：
  `render-full-rebuild`（每帧 30 行全变）native ~700µs vs JVM ~390–440µs，是差距最大的负载。
  gprofng 采样该负载：GC Sweep ≈31%、Compose 重组 ≈10%、libc ≈10%、自家布局+绘制 <10%。
  JVM 靠 HotSpot 逃逸分析 + 标量替换 + 分代 GC 把重组期分配的临时对象压到近零成本；
  K/N 无逃逸分析、ARC + 整堆 Mark&Sweep，同样的重组分配变成真实 GC 压力。
  **已试 K/N GC 参数（2026-10-18）**：`gc=stms`（默认，基线 ~700µs）/ `gc=ms`（~757µs，更差）/
  `gcSchedulerType=Aggressive`（链接后运行挂死，病态）。默认调度器即 Adaptive，无再试价值。
  结论：**GC 参数无收益，已恢复默认**；差距是架构性的，不在库代码，无法用 `-Xbinary` 追平。
  后续若想收窄此差距，方向只剩降低 Compose 重组本身分配（制约在外层），或维持 JVM 为主部署。
- JVM 侧首次构建会触发 `mosaic-tty:zigDownload`（下载 JNI 用的 Zig 工具链），
  需能访问 `downloads.ziglang.org`；网络被墙时该任务会卡死，可改用离线 classpath 直接
  `java -cp ... example.MainKt`（见 src 内记录）。
