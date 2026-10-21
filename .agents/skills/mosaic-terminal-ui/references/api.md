# Mosaic API 参考

所有 API 都在 com.jakewharton.mosaic 下：UI 组件在 ui 包，Modifier 在 layout 包，Modifier 本身在 modifier 包。本文件按主题列出签名与语义；踩坑细节见 pitfalls.md。

## 1. 入口与组合局部

    fun runMosaicMain(mouse = false, paste = false, content: @Composable () -> Unit)

    fun runMosaicBlocking(
        onNonInteractive: NonInteractivePolicy = Exit,
        mouse = false,
        paste = false,
        content: @Composable () -> Unit,
    ): Boolean

    suspend fun runMosaic(
        onNonInteractive: NonInteractivePolicy = Exit,
        mouse = false,
        paste = false,
        content: @Composable () -> Unit,
    ): Boolean

- runMosaicMain：给 fun main() 用的阻塞入口，非 TTY 行为固定为退出。
- runMosaicBlocking：可指定 NonInteractivePolicy，返回是否真正运行。
- runMosaic：suspend 版本。
- NonInteractivePolicy：Exit（默认，提示并非零退出）、Throw、Return、Ignore（惰性终端继续跑）、AssumeAndIgnore（不检测直接 Ignore）。
- 运行语义是 run-until-idle：静态内容首帧稳定后立即返回；交互应用要自己保活。

组合局部：

- LocalTerminalState：TerminalState（focused、theme、size）。
- LocalClipboard：Clipboard，getText/setText 都是 suspend。
- LocalStaticLogger：StaticLogger，向动态区上方追加永久输出。
- LocalLifecycleOwner：焦点变化驱动 ON_PAUSE/ON_RESUME。

Mosaic / TextCanvas 是运行时接口，普通应用不需要实现。

## 2. 布局与 Modifier

### 容器

    @Composable fun Box(modifier = Modifier, contentAlignment = Alignment.TopStart,
        propagateMinConstraints = false, content: @Composable BoxScope.() -> Unit)

    @Composable fun Row(modifier = Modifier,
        horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
        verticalAlignment: Alignment.Vertical = Alignment.Top,
        content: @Composable RowScope.() -> Unit)

    @Composable fun Column(modifier = Modifier,
        verticalArrangement: Arrangement.Vertical = Arrangement.Top,
        horizontalAlignment: Alignment.Horizontal = Alignment.Start,
        content: @Composable ColumnScope.() -> Unit)

    @Composable fun BoxWithConstraints(modifier = Modifier,
        contentAlignment = Alignment.TopStart, propagateMinConstraints = false,
        content: @Composable BoxWithConstraintsScope.() -> Unit)

    @Composable fun Spacer(modifier = Modifier)

    @Composable fun Filler(char: Char, modifier = Modifier,
        foreground = Color.Unspecified, background = Color.Unspecified,
        textStyle = TextStyle.Unspecified)

- RowScope / ColumnScope 提供 Modifier.weight(weight, fill = true) 与 Modifier.align(...)。
- BoxWithConstraintsScope 暴露 constraints、minWidth、maxWidth、minHeight、maxHeight、hasBoundedWidth/Height、hasFixedWidth/Height。
- HorizontalDivider(thickness = 1, color, startIndent = 0, endIndent = 0, modifier)；VerticalDivider 对称；无界主轴上会塌成 0。
- Layout(content, modifier, debugInfo, measurePolicy) 是自定义布局入口；MeasurePolicy 在 measure 阶段拿到 List<Measurable> 与 Constraints。

Alignment 常量：TopStart、TopCenter、TopEnd、CenterStart、Center、CenterEnd、BottomStart、BottomCenter、BottomEnd；另有 Top/CenterVertically/Bottom、Start/CenterHorizontally/End。
Arrangement 常量：Start、End、Top、Bottom、Center、SpaceEvenly、SpaceBetween、SpaceAround；带间距版本如 Arrangement.spacedBy(n)。

### 尺寸与间距

    Modifier.width(Int) / height(Int) / size(Int) / size(width, height) / size(IntSize)
    Modifier.widthIn(min = 0, max = Infinity) / heightIn / sizeIn
    Modifier.requiredWidth / requiredHeight / requiredSize / requiredWidthIn / ...
    Modifier.fillMaxWidth(fraction = 1f) / fillMaxHeight / fillMaxSize
    Modifier.wrapContentWidth(align, unbounded = false) / wrapContentHeight / wrapContentSize
    Modifier.defaultMinSize(minWidth = 0, minHeight = 0)
    Modifier.padding(all) / padding(horizontal, vertical) / padding(start, top, end, bottom)
    Modifier.offset(x = 0, y = 0) / offset(offset: () -> IntOffset)
    Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = false)

- width/size 是首选尺寸；严格尺寸用 required*（可越出父约束）。
- fillMaxWidth(fraction) 依赖父级有界 maxWidth；Infinity 时无效果。
- offset 只移动放置位置，不改变测量尺寸。
- padding 顺序影响包住关系；border 会把内容向内缩 width 格。

### 绘制与外观

    Modifier.background(color)
    Modifier.border(width = 1, color = Color.Unspecified)
    Modifier.clipToBounds()
    Modifier.drawBehind(onDraw: DrawScope.() -> Unit)
    Modifier.onGloballyPositioned { position: IntOffset, size: IntSize -> }
    Modifier.layoutId(id)

DrawScope 坐标是节点本地坐标，可调用：

    drawRect(codePoint = 0x2588, foreground = Color.Red)
    drawRect('─', foreground = Color.Cyan, topLeft = IntOffset(1, 0), size = IntSize(width - 2, 1))
    drawText(0, 0, "text", foreground = Color.White, background = Color.Unspecified)

需要先画背景再画内容时实现 DrawModifier，在 ContentDrawScope.draw() 最后调用 drawContent()。drawText 使用簇级宽度，与 Text 一致。

### 交互 Modifier

- Modifier.clickable(enabled = true, onClickLabel = null, onClick)：主键界内按下并在界内松开才触发；越界或换节点取消。
- Modifier.hoverable(enabled = true, onHoverChanged: (Boolean) -> Unit)：进入/离开；离开在下一次鼠标事件报告；不消费事件。
- Modifier.onMouseEvent { event -> Boolean }：命中路径最具体者先收到，返回 true 消费。
- Modifier.focusable(enabled = true)：参与 Tab/Shift+Tab 环，主键点击聚焦。
- Modifier.onFocusChanged { FocusState }：hasFocus 含后代，isFocused 仅自身。
- Modifier.focusRequester(FocusRequester)：程序化聚焦，必须同时有 focusable。
- Modifier.onKeyEvent { event -> Boolean }：从焦点向上冒泡，返回 true 消费。
- Modifier.onPreviewKeyEvent：祖先在向下阶段先拦截。
- Modifier.onPaste { text -> Boolean }：祖先先于子树，需要 paste = true。
- testTag 是 internal 测试工具，不在公共 API。

## 3. 文本与样式

    @Composable fun Text(
        value: String,                    // 或 AnnotatedString
        modifier = Modifier,
        softWrap = false,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        textAlign = Alignment.Start,
        scroll = 0,
        color = Color.Unspecified,
        background = Color.Unspecified,
        textStyle = TextStyle.Unspecified,
        underlineStyle = UnderlineStyle.Unspecified,
        underlineColor = Color.Unspecified,
    )

- softWrap：按字素簇边界软换行，默认关闭。
- maxLines + overflow：Clip 保留完整可见行；Ellipsis 在最后可见行末尾加省略号。
- textAlign：只在有界宽/对齐宽度时产生位移；Start 无偏移。
- scroll：从第几行开始显示；越界会 clamp。
- 文本按扩展字素簇测量与绘制：CJK、ZWJ、旗帜、keycap、组合序列作为整体；C0/C1/DEL 控制符宽度 0、不绘制、不进 ANSI；换行符分行；tab 不展开。

样式：

- Color：Color.Unspecified、Black/Red/Green/Blue/Yellow/Magenta/Cyan/White；也可 Color(r, g, b)（0..255 或 0f..1f）。
- TextStyle：值类，支持加号与 contains；Bold、Dim、Italic、Strikethrough、Invert；TextStyle.Empty 清空，TextStyle.Unspecified 继承。
- UnderlineStyle：None、Straight、Double、Curly、Dotted、Dashed、Unspecified。
- TextOverflow：Clip、Ellipsis。

富文本：

    val text = buildAnnotatedString {
        append("normal ")
        pushStyle(SpanStyle(color = Color.Red, textStyle = TextStyle.Bold))
        append("red bold")
        pop()
    }
    Text(text, softWrap = true)

SpanStyle(color, background, textStyle, underlineStyle, underlineColor) 支持 merge 与加号；Builder 提供 append、addStyle、pushStyle/pop。

## 4. 滚动与列表

    @Stable class ScrollState(initial = 0) {
        var value: Int        // 当前偏移
        var maxValue: Int     // 内容减视口，measure 后更新
        fun scrollBy(delta: Int): Int
        fun scrollTo(target: Int)
    }
    @Composable fun rememberScrollState(initial = 0): ScrollState
    Modifier.verticalScroll(state, enabled = true)
    Modifier.horizontalScroll(state, enabled = true)

- 滚动容器在滚动轴给内容无界约束、报告视口尺寸；父级必须提供有界视口（height/weight 等）。
- 滚轮内建：每格 3 行，方向与方向键一致。
- maxValue 在首次 measure 前是 0，别在组合期拿它做分支。

    @Composable fun LazyColumn(
        modifier = Modifier,
        state = rememberScrollState(),
        itemHeight = 1,
        beforeLookahead = 2,
        afterLookahead = 2,
        content: @Composable LazyListScope.() -> Unit,
    )
    interface LazyListScope {
        fun items(count: Int, itemContent: @Composable (index: Int) -> Unit)
        fun <T> items(items: List<T>, itemContent: @Composable (item: T) -> Unit)
    }

- 只组合视口附近项；itemHeight 是固定槽高，内容超出会被裁。
- 视口高度必须由父级限定；当前没有 LazyRow。

## 5. 输入、焦点、剪贴板

    class KeyEvent(
        val key: String,
        val alt = false, val ctrl = false, val shift = false,
        val repeat = false,
    )

key 的取值：可打印字符/IME 文本为原字符串；特殊键名为 Tab、Enter、Escape、Backspace、ArrowLeft/Right/Up/Down、Insert、Delete、PageUp、PageDown、Home、End、F1..F35。release 被丢弃；press 与 repeat 都投递（repeat = true）。

    class MouseEvent(
        val x: Int, val y: Int,             // 绝对屏幕 cell 坐标
        val type: Type,                     // Drag / Motion / Release / Press
        val button = Button.None,           // Left/Middle/Right/None/WheelUp/WheelDown/Button8..11
        val shift = false, val alt = false, val ctrl = false,
    )
    interface Clipboard {
        suspend fun getText(): String?      // OSC 52，2s 超时/被拒返回 null
        suspend fun setText(text: String)   // 空串清空
    }

焦点：

- focusable 让节点进入 Tab 环；没有 focusable 时 Tab 不被消费，留给应用。
- 点击可聚焦节点会把焦点移过去；点击非 focusable 不改焦点。
- FocusRequester.requestFocus() 需要目标同时有 focusable；通常放在 LaunchedEffect 里首帧聚焦。
- FocusState.hasFocus 对祖先也成立，isFocused 只对自身成立。

BasicTextField：

    @Composable fun BasicTextField(
        value: String,
        onValueChange: (String) -> Unit,
        modifier = Modifier,
        enabled = true,
        readOnly = false,
        textStyle = TextStyle.Unspecified,
        color = Color.Unspecified,
        background = Color.Unspecified,
        onDone: (() -> Unit)? = null,
    )

- 受控组件；内部有文本镜像以处理同帧连击；只在聚焦时响应输入、显示反显光标。
- 单行；末尾预留 1 列放光标；onDone 在 Enter 时调用，否则 Enter 不消费。
- 左/右/Home/End/Backspace/Delete 由字段处理；上/下/PageUp/PageDown/Tab/Escape 交还应用。
- 粘贴在聚焦且非只读时插入光标处；Ctrl 组合不吞。

## 6. 终端状态与能力

    class TerminalState(
        val focused: Boolean,
        val theme: Terminal.Theme,   // Unknown / Light / Dark
        val size: Terminal.Size,     // columns, rows, width, height；默认 80x24，像素未知为 0
    )
    val LocalTerminalState: ProvidableCompositionLocal<TerminalState>

Terminal.Capabilities 常用字段：ansiLevel、cursorVisibility、focusEvents、inBandResizeEvents、kittyGraphics、kittyKeyboard、kittyNotifications、kittyPointerShape、kittyTextSizingScale/Width、kittyUnderline、graphemeClustering（DEC 2027）、synchronizedOutput、themeEvents。能力由终端层探测，应用只读。

- AnsiLevel：NONE / ANSI16 / ANSI256 / TRUECOLOR；颜色会自动降级。
- Color.Unspecified 表示继承/终端默认；显式清空样式用 TextStyle.Empty。
- focused/theme 只在终端声明支持后变化；非交互终端尺寸为默认值。

## 7. 动画（mosaic-animation）

    @Composable fun animateFloatAsState(targetValue, animationSpec = default,
        visibilityThreshold = 0.01f, label, finishedListener): State<Float>
    @Composable fun animateIntAsState(targetValue, ...): State<Int>
    @Composable fun animateIntOffsetAsState(targetValue, ...): State<IntOffset>
    @Composable fun animateIntSizeAsState(targetValue, ...): State<IntSize>
    @Composable fun animateColorAsState(targetValue, ...): State<Color>
    @Composable fun <T, V : AnimationVector> animateValueAsState(targetValue, typeConverter, ...): State<T>

- State.value 才是值；不要对整个 State 做 toString。
- Color 走感知空间插值（Oklab），不是朴素 RGB 线性插值。
- Animatable<T, V>：可命令式 animateTo/snapTo/stop；Float/Color 等有内置构造。
- 帧时钟由组合内部驱动：动画等待中约 60fps，空闲约 20fps；不要自己 delay 轮询。

    @Composable fun <T> Crossfade(targetState: T, modifier = Modifier,
        animationSpec: FiniteAnimationSpec<IntOffset> = tween(), label = "Crossfade",
        content: @Composable (T) -> Unit)

    @Composable fun AnimatedVisibility(visible: Boolean, modifier = Modifier,
        enter: EnterTransition = expandVertically(),
        exit: ExitTransition = shrinkVertically(),
        label = "AnimatedVisibility",
        content: @Composable AnimatedVisibilityScope.() -> Unit)
    // 另有 MutableTransitionState<Boolean> 变体

- Crossfade 是水平推进：旧内容向左滑出、新内容从右滑入（终端无 alpha），容器会裁剪。
- AnimatedVisibility 的 expand/shrink 会真的改变布局尺寸并把兄弟挤开；slide 只移位；无 fade/scale。
- Enter/Exit 函数：expandIn、shrinkOut、expandHorizontally/Vertically、shrinkHorizontally/Vertically、slideIn/slideOut、slideInHorizontally/Vertically、slideOutHorizontally/Vertically；可用加号组合。

## 8. 硬件光标与静态日志

    enum class CursorShape { Block, Underline, Bar }

    @Composable fun Cursor(modifier = Modifier,
        shape = CursorShape.Block, blink = true)

- 零尺寸布局标记：放在目标位置（Row 里 Text 之后，或 Modifier.offset(x, y)）。
- 只在终端 cursorVisibility 为 true 且落在组合根内时显示；离开组合会隐藏并复位形状。
- 限制：多 Cursor 同时放置时最后放置者胜；静态日志会让动态区整体下移（光标行偏移不补偿）；嵌套滚动容器的裁剪不参与判断；BasicTextField 用的是反显 cell，不是硬件光标。

静态日志：

    LocalStaticLogger.current.log("line")
    LocalStaticLogger.current += "line"
    LocalStaticLogger.current.log(canvas)   // TextCanvas

StaticEffect { ... } 只组合一次，输出到动态区上方；里面的 LaunchedEffect 不会启动，SideEffect/DisposableEffect 会执行并释放。

## 9. 测试接口

见 testing.md。公共入口：runMosaicTest、SnapshotStrategy、TestMosaic、TestTerminal（mosaic-testing）、CapabilityMatrix、runMosaicTestForEach。
