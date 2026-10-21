# Mosaic 踩坑清单

每条按「现象 → 原因 → 做法」写。遇到布局/渲染异常时先对照这里。

## 1. 全屏布局失效（fillMaxWidth / weight 没反应）

现象：所有内容挤成内容宽；weight 等分列塌缩；水平居中没有效果。
原因：组合根按无界约束测量，根没有可分配的最大宽度/高度。
做法：在应用最外层显式声明画布：

    val size = LocalTerminalState.current.size
    Column(Modifier.width(size.columns).height(size.rows)) { ... }

根有界后，内部的 fillMaxWidth()、weight()、fillMaxHeight(0.5f)、BoxWithConstraints 才会正常。窗口 resize 时 LocalTerminalState.current.size 会变化，重组后自动更新。

## 2. Text 的 width 不会裁剪

现象：Text 加了 Modifier.width(10)，长文本仍然画出 10 格以外，覆盖旁边内容。
原因：Text 报告内容宽；width/requiredWidth 是首选尺寸，没有“报告宽收敛到约束”的层。
做法：
- 需要换行：Text(..., softWrap = true) 并给有界宽度。
- 需要截断：maxLines = n + overflow = Clip/Ellipsis。
- 需要硬裁剪：外层 Box(Modifier.width(10).clipToBounds())，或放在裁剪容器里。
- 横向滚动/长行展示用 horizontalScroll。

## 3. 长文本不换行

现象：父容器有宽度，但 Text 还是一行长。
原因：Text 的 softWrap 默认是 false。
做法：显式 softWrap = true；maxWidth 必须是有界的（父级 width/fillMaxWidth）。空段落会保留；超宽字素簇独占一行。

## 4. maxLines / overflow 组合不对

- 只设 maxLines 不设 overflow：多余行被丢弃，最后一行保持完整（Clip）。
- 设 overflow = Ellipsis：最后可见行末尾加省略号。
- maxLines = 1 + softWrap = true + Ellipsis 是实现“单行省略”的标准写法。
- 没有 softWrap 时，maxLines 只对已有的换行符生效。

## 5. textAlign 看起来没用

原因：Start 在无界/内容宽等于行宽时不产生偏移；Center/End 需要“对齐宽度”大于行内容宽。
做法：给 Text 一个明确宽度（Modifier.width(n) 或父级 fillMaxWidth），再用 textAlign = Alignment.CenterHorizontally / End。

## 6. 控制字符、tab、换行

- C0（0x00-0x1F）、DEL、C1（0x80-0x9F）按非打印处理：0 宽、不绘制、不进 ANSI。不要把 TAB 当缩进，它不会展开。
- 只有换行符会分行；CR 不会（且被当控制符过滤）。
- 需要列对齐请自己补空格或用 Text 的 textAlign，不要依赖 TAB。
- 日志/外部输入进 Text 前最好过滤控制字符。

## 7. weight 与 fill 的真实语义

- weight 只存在于 RowScope / ColumnScope，别在普通 Modifier 上找。
- weight(1f, fill = true)：子项获得槽位尺寸，但子节点自身内容宽不会被拉伸；要让内容撑满槽位，再给子项 fillMaxWidth()/fillMaxHeight()。
- weight(..., fill = false)：按内容尺寸排布，未用完的槽位不回收给兄弟。
- Row/Column 的尺寸=槽位总和；父级无界时 weight 没有可分配空间。

## 8. BoxWithConstraints 的边界

- content 在 measure 阶段惰性组合，约束变化会重组；不要在 content 外依赖 maxWidth 的普通变量。
- maxWidth/maxHeight 可能是 Constraints.Infinity；先判断 hasBoundedWidth/Height 再算比例。
- 它的子组合根在绘制遍历里曾出现 0x0 问题，已修复；如果你手写子组合/自定义布局，注意零尺寸容器不会被绘制。

## 9. border / padding / background 的顺序

- border 会把内容四边各缩 width 格；width = 0 不画且不缩。
- padding 在 border 外还是内，取决于链式顺序：Modifier.padding(1).border() 与 Modifier.border().padding(1) 结果不同。
- background 画在内容后面；想给整个盒子（含 border）上色，background 放在 border 外层。
- HorizontalDivider 需要父级有界宽度，否则塌成 0；VerticalDivider 同理需要高度。

## 10. offset 不是布局

- offset 只改变放置/绘制位置，不影响测量和兄弟排布，也不扩大父级尺寸。
- 越界内容需要父级 clipToBounds() 或滚动容器裁剪。
- 用 offset 做命中测试时，clickable 的边界仍是放置后的边界，通常符合预期；但“视觉在 A、布局在 B”会让点击区域与视觉不一致。

## 11. 滚动容器必须有界

现象：verticalScroll 不滚，或内容高度等于整棵内容。
原因：滚动需要“视口高度”小于“内容高度”。父级 unbounded 时视口=内容，maxValue=0。
做法：给滚动容器固定 height 或 weight(1f)（在 Column 里）。横向滚动同理需要 width。
另外：maxValue 在首次 measure 后才有效；滚动条/比例计算要容忍初始 0。

## 12. LazyColumn 的固定槽高

- itemHeight 是像素/行到 item index 的唯一映射；项内容超过 itemHeight 会被裁。
- 多行项要么设 itemHeight = 实际行数，要么把内容压到一行。
- 视口高度必须由父级限定；只靠终端行数猜测不可靠。
- 只组合视口附近项；不要假设所有 item 的副作用都会执行。
- 没有 LazyRow；横向列表用 horizontalScroll + Row。

## 13. 应用一启动就退出

现象：只显示一帧就回到 shell。
原因：runMosaic 是 run-until-idle；静态内容没有常驻 effect。
做法：LaunchedEffect(Unit) { awaitCancellation() }，或保留一个无限动画/事件循环/通道等待。Ctrl-C 仍然可用（未被消费的 ctrl-C 会取消组合）。
测试里同理：awaitComplete 会因空闲自然返回；要验证 Ctrl-C 行为需要 Ticker/无限 withFrameNanos 保活。

## 14. 鼠标/粘贴不工作

- runMosaic(mouse = true, paste = true)（或 runMosaicBlocking/Main 同名参数）没开。
- 终端本身不支持/没上报对应能力；能力在 Terminal.Capabilities 里可查。
- 鼠标坐标是绝对屏幕 cell 坐标，不是节点本地坐标；节点内判断要自己减位置（或直接用 clickable/hoverable）。
- hover 的 leave 要等下一次鼠标事件，静止指针移出不会立即回调。
- 粘贴需要 bracketed paste 支持；未终止的 paste 里 Ctrl-C 是逃生口。

## 15. 键盘/焦点

- 没有 focusable 时 Tab 不会自动被消费，可能被应用自己处理；有 focusable 时 Tab/Shift+Tab 做环形遍历并消费，repeat 不连滚。
- BasicTextField 只处理左/右/Home/End/Backspace/Delete；上/下/PageUp/PageDown/Tab/Escape 返回 false，交还应用。
- 多字段必须用 focusable + 点击/Tab/FocusRequester 聚焦；未聚焦字段不会编辑，也不显示光标。
- onPreviewKeyEvent 祖先先于子；onKeyEvent 从焦点向上冒泡。返回 true 即消费。
- KeyEvent.repeat = true 表示长按重复；分页/切换类操作要过滤，避免一次按压连翻。

## 16. BasicTextField 的受控语义

- value 由调用方持有；onValueChange 里不要基于闭包里的旧 value 计算（同帧可能连续多个事件）。组件内部有镜像，但你的状态更新仍要即时写回。
- 单行，不支持多行；需要多行自己拆多个字段或写新控件。
- 末尾会预留 1 列给光标；放进过窄容器时光标可能被裁。
- onDone 只有提供时才消费 Enter；不提供时 Enter 继续冒泡。
- enabled = false 不显示光标、不消费键；readOnly 可移动光标但不能编辑。

## 17. 硬件光标 Cursor

- 零尺寸标记，必须落在组合根内的某个 cell；在根外会隐藏。
- 只在终端 cursorVisibility 能力下显示；无该能力时是 no-op。
- 位置来自布局：放在 Text 后（Row）或 Modifier.offset 选格；滚动会让它跟着动。
- 静态日志在根上方占行，CUP 行坐标不会补偿，可能上移；同时用静态日志时优先用 cell 反显光标。
- 嵌套滚动容器自己的裁剪不参与判断：光标可能出现在被裁剪区域对应的屏幕坐标。保持在可见视口内使用。
- 同时放多个 Cursor 时最后放置者胜；离开组合会隐藏并复位形状；进程退出会复位默认形状。
- BasicTextField 用的是字符反显，不是硬件光标；不要混用两套 caret。

## 18. 静态日志

- LocalStaticLogger 的输出在动态区上方；StaticEffect 只组合一次，里面的 LaunchedEffect 不启动（SideEffect/DisposableEffect 会执行并释放）。
- 静态输出会占据屏幕顶部行，使动态区整体下移；硬件光标、绝对坐标假设都可能偏。
- 日志很多时注意屏幕滚动；不要把静态日志当动态区域用。

## 19. 动画

- animate*AsState 返回 State，取值必须 .value；toString 得到的是内部状态字符串。
- 终端没有 alpha/scale；不要找 fade/scale 动画。Crossfade 是水平推进，AnimatedVisibility 支持 expand/shrink/slide。
- Crossfade 需要容器有界宽度；它自己会 clip。
- AnimatedVisibility 的 expand/shrink 会改变布局尺寸，兄弟会被挤开；退出动画结束前内容仍在树里。
- 帧率：动画等待中约 60fps，空闲约 20fps；不要用 delay 自己驱动。需要逐帧逻辑用 withFrameNanos。
- Color 的插值是感知空间（Oklab）；断言中间色时不要按线性 RGB 算。

## 20. 终端/颜色/能力

- 必须真实 TTY：Gradle run、IDE 内、CI 管道里没有交互 TTY，ANSI 也可能被吞。用 installDist 后直接跑二进制。
- 非 UTF-8 locale 不在支持范围；Windows/macOS 行为主要靠 CI 覆盖。
- 颜色会自动降级到 16/256/24bit；Color.Unspecified 继承，TextStyle.Empty 才是显式清空。
- focused/theme 只有终端声明支持才会变；尺寸像素值可能是 0。
- 剪贴板走 OSC 52，读有 2s 超时；终端可能拒绝或静默忽略。
- DEC 2027（字素簇）和 kitty 键盘协议会在支持时自动启用/恢复，应用不要自己再发这些序列。

## 21. 测试相关

- runMosaicTest 默认 PlainTextSnapshots；渲染级断言要显式用 RenderingSnapshots(AnsiIncrementalRendering(caps))（internal，测试同模块可用）。
- awaitSnapshot 等待“有绘制发生”（onDraw），不是等待任意状态变化；如果状态变了但没触发绘制，会超时。
- 完全静态内容会让 awaitComplete 自然返回，可能掩盖“是否由某个事件触发退出”的问题；用 Ticker/无限 withFrameNanos 保活。
- 测试里的时间可能是虚拟时间（runTest）与真实帧时钟混合；不要假设 wall-clock 与帧数线性对应。
- VirtualTerminal 只模拟渲染器实际发出的序列；它不模拟真实终端滚动，所以“末行换行导致上滚”这类问题要用 FullRedraw 类测试专门覆盖。
- Windows 控制台测试桩只保留 ASCII 字节；高字节往返用例在 Windows 跳过是预期行为。
- Native 测试带 --no-configuration-cache；FFM 路径用 installJvmTestDistribution 在 JDK 22+ 跑。

## 22. 工程/质量

- 新增 public API 必须跑 apiDump 并提交 .api/.klib.api；不要手改 dump。
- 只跑改动模块的 spotlessCheck；runtime 有 7 处既有 compose 规则失败，不是你的改动。
- 公共 API 0.x 仍可能 breaking；新增入口同步 CHANGELOG。
- 仓库红线：不引入外部参考库/项目名；不恢复已移出仓库的调研文档。
- samples 里的上游示例目录名属于已知未处理项；不要在新代码/文档里继续扩散这些名字。
