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

> 下表为**低分配改造后**数字（surface 复用 + 绘制测宽 ASCII 快路径 + 宽度状态机 raw-Int 打包）。
> 改造前基线见下文「优化记录」。

| 负载 | JVM | Native(kexe) | 说明 |
| --- | --- | --- | --- |
| `render-draw-idle` | ~177 µs | ~354 µs | **native ≈ 2.0×（原 2.4×）** |
| `render-one-row-delta` | ~327 µs | ~548 µs | native ≈ 1.7× |
| `render-full-rebuild` | ~493 µs | ~1,058 µs | native ≈ 2.1× |
| `layout-pass` | ~330 µs | ~623 µs | native ≈ 1.9× |
| `event-parser` | ~174 µs | ~132 µs | native ≈ 0.76×（更快，读数偏噪） |

## 优化记录（L7.2 绘制热路径低分配，2026-10-18）

| 改造 | native `render-draw-idle` | 说明 |
| --- | --- | --- |
| 改造前基线 | ~510 µs | 每帧 `new TextSurface` = 4800 个 TextPixel 对象；每簇 `strWidth` 逐码点返回 `Pair<Int,WidthInfo>`（每码点分配） |
| +每帧复用 TextSurface（`reset()` 清空） | ~467 µs | 消除 4800 对象/帧；native -8.4%，JVM -5% |
| +`strWidth` printable-ASCII 快路径 | ~388 µs | 纯 ASCII 范围直接 `to-from`，跳过逐码点 Pair 状态机；native -17%（vs 复用后）、-24%（vs 原始） |
| +宽度状态机 raw-Int 打包（`packResult` 打包 width+raw 到单 Int，`widthOf=(p.x shl 28) shr 28` 恢复） | ~354 µs | 状态机/查找不再分配 `Pair<Int,WidthInfo>`；12 个 raw 版谓词直读打包值；native -8.8%（vs ASCII 后）、-31%（vs 原始） |
- JVM 对照：~209 → ~177 µs；native/JVM 比值 2.4× → **2.0×**。
- 新增 `PackedWidthEquivalenceTest`（JVM）：以原对象版改名 `*Ref` 作 oracle，全 BMP×43 状态 + 天文区重放回放断言一致（~560 万次对比全等）。
- 剩余分配热点（JFR 采样证实）：`drawText` 每簇 `text.substring()` 仍是最大单项。下一步候选：单元格改存「源串+区间」以在绘制热路径消除 substring（涉及 surface 数据模型，需单测+确认后实施）。

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
  - 本库 JVM：0.34–0.50ms；本库 native：0.70–1.28ms。
  - 结论：JVM 与 Rust 系相当、快于 Go 系；native 落在 Go 系与 Rust 系之间（约 1.1–3×），仍然
    全体远小于 60fps 预算。测量口径差异见注意事项。
- **结论**：**native 达到主流实时渲染标准**——最坏全屏重建在 60fps 预算内剩余 12× 余量。

## 注意事项

- 本基准测的是应用侧成本（重组+布局+绘制），**不含** ANSI diff 序列化与真实 tty 写 I/O
  （internal 渲染器，sample 不可见；旧 JVM-only 全含 diff 的读数高约 25–30%）。
  对照实现为参考：Rust 系数字为纯 draw/diff（无内容生成、无状态簿记）；Go 系与本库同口径
  （含内容生成）；故 native 相对 Rust 的实际差距会小于表面数字。
- **native 慢点已定位：绘制/布局核心 ≈2.4×，重组层 ≈1.3×，字节解析反而快**（0.39×）。
  说明「native 不该比 JVM 慢」成立——差距源自我们热路径代码对 K/N 不够友好（分配 + 虚调用 +
  内联受限），**低分配改造（§2/§5）是追上 JVM 的直接手段**，是本项目性能主战场。
- JVM 侧首次构建会触发 `mosaic-tty:zigDownload`（下载 JNI 用的 Zig 工具链），
  需能访问 `downloads.ziglang.org`；网络被墙时该任务会卡死，可改用离线 classpath 直接
  `java -cp ... example.MainKt`（见 src 内记录）。
