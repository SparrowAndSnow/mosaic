# Mosaic 常用写法

所有片段默认在 runMosaicMain/runMosaicBlocking 的 content 里；省略了局部状态/回调声明（如 selected、onQuit），按上下文补全。导入：

    import com.jakewharton.mosaic.modifier.Modifier
    import com.jakewharton.mosaic.layout.*
    import com.jakewharton.mosaic.ui.*

## 1. 全屏应用骨架

    fun main() = runMosaicMain(mouse = true, paste = true) {
        App()
    }

    @Composable
    fun App() {
        LaunchedEffect(Unit) { awaitCancellation() }   // 保活
        val size = LocalTerminalState.current.size
        Column(Modifier.width(size.columns).height(size.rows)) {
            Text("Title")
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Text("Body", softWrap = true)
            }
            Text("q 退出")
        }
    }

要点：根必须有界；没有 awaitCancellation 的静态界面首帧后会退出。

## 2. 页头/内容/页脚

    Column(Modifier.fillMaxSize()) {
        Text("Header", textStyle = TextStyle.Bold)
        HorizontalDivider()
        Box(Modifier.fillMaxWidth().weight(1f).padding(1)) {
            Text("scrollable/body content", softWrap = true)
        }
        HorizontalDivider()
        Text("Footer", textStyle = TextStyle.Dim)
    }

Column 本身也要有界（外层已由第 1 节提供）。HorizontalDivider 在无界宽度下会塌成 0。

## 3. 左侧栏 + 主区

    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(24).fillMaxHeight()) {
            Text("Menu")
            Text("  Item A")
            Text("  Item B")
        }
        VerticalDivider()
        Box(Modifier.weight(1f).fillMaxHeight().padding(1)) {
            Text("Main", softWrap = true)
        }
    }

侧栏固定 width，主区 weight(1f)；两者都需要父级有界高度。

## 4. 等分列 / 比例列

    Row(Modifier.fillMaxWidth()) {
        Text("A", Modifier.weight(1f).background(Color.Red))
        Text("B", Modifier.weight(2f).background(Color.Green))
        Text("C", Modifier.weight(1f).background(Color.Blue))
    }

- weight 只分配“扣除固定子项后的剩余主轴”。
- 子项内容宽不会自动撑满槽位；需要背景/点击区域覆盖整槽时再加 fillMaxWidth()。
- fill = false 时按内容宽放置，未用槽位不回收。

精确比例（固定 + 百分比混合）用 BoxWithConstraints：

    BoxWithConstraints(Modifier.fillMaxWidth().height(10)) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(maxWidth / 4).fillMaxHeight().background(Color.Red))
            Box(Modifier.width(maxWidth / 2).fillMaxHeight().background(Color.Green))
            Box(Modifier.width(maxWidth / 4).fillMaxHeight().background(Color.Blue))
        }
    }

先判断 hasBoundedWidth，避免 maxWidth == Constraints.Infinity。

## 5. 滚动 + 滚动条

    val scroll = rememberScrollState()
    Column(Modifier.height(10).fillMaxWidth()) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            repeat(100) { Text("Line " + it) }
        }
        Text("scroll: " + scroll.value + "/" + scroll.maxValue)
    }

- 滚动容器必须有界高度（这里 weight(1f)）。
- 滚轮自动可用（每格 3 行），不需要额外 onMouseEvent。
- 横向长行用 horizontalScroll，同理需要宽度。
- 自己画滚动条可以用 scroll.value / scroll.maxValue 比例。

## 6. 大列表 LazyColumn

    val state = rememberScrollState()
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(12),
        state = state,
        itemHeight = 1,
    ) {
        items(1000) { index ->
            Text("Row " + index)
        }
    }

- itemHeight 固定；多行项要把 itemHeight 设为实际行数，或把内容压成一行。
- 只组合视口附近项；不要依赖所有 item 的副作用执行。
- 视口高度必须显式给（height/weight）。
- 没有 LazyRow；横向列表用 Row + horizontalScroll。

## 7. 多字段表单

    @Composable
    fun Form() {
        var name by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var submitted by remember { mutableStateOf<String?>(null) }
        val nameFocus = remember { FocusRequester() }

        LaunchedEffect(Unit) { nameFocus.requestFocus() }

        Column(Modifier.padding(1)) {
            Row {
                Text("Name:  ")
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.width(20).focusRequester(nameFocus),
                    onDone = { submitted = "name=" + name },
                )
            }
            Row {
                Text("Email: ")
                BasicTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.width(20),
                    onDone = { submitted = "email=" + email },
                )
            }
            Text(submitted ?: "Tab / 点击切换字段，Enter 提交")
        }
    }

- 字段自带 focusable；Tab/Shift+Tab 或点击切换。
- 只有聚焦字段消费输入并画反显光标。
- onDone 不提供时 Enter 会继续冒泡。
- 字段末尾预留 1 列；容器别比字段窄。

## 8. 可点击 / 悬停的 Tab

    @Composable
    fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth()) {
            listOf("One", "Two", "Three").forEachIndexed { index, label ->
                var hovered by remember { mutableStateOf(false) }
                val active = index == selected
                Text(
                    " " + label + " ",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(index) }
                        .hoverable { hovered = it }
                        .background(
                            when {
                                active -> Color.Cyan
                                hovered -> Color.Blue
                                else -> Color.Unspecified
                            },
                        ),
                    color = if (active) Color.Black else Color.White,
                )
            }
        }
    }

clickable 是按下/松开同界；hoverable 的 leave 在下一次鼠标事件才报告。鼠标需要 runMosaic(mouse = true)。

## 9. 键盘导航与 repeat 过滤

    Column(
        Modifier
            .width(40).height(10)
            .onKeyEvent { event ->
                when (event.key) {
                    "ArrowDown" -> {
                        if (!event.repeat) selected = (selected + 1).coerceAtMost(n - 1)
                        true
                    }
                    "ArrowUp" -> {
                        if (!event.repeat) selected = (selected - 1).coerceAtLeast(0)
                        true
                    }
                    "q" -> { onQuit(); true }   // onQuit 里取消承载 runMosaic 的 Job
                    else -> false
                }
            },
    ) { ... }

- repeat = true 是长按重复；分页/切换/翻页通常只处理首次 press。
- 有 focusable 时 Tab 会被焦点系统消费；想自己用 Tab 要先避免可聚焦子项或使用 onPreviewKeyEvent。
- Ctrl-C 不消费时默认退出；想自定义就先在 onKeyEvent 返回 true。
- 需要应用级循环时用 CoroutineScope/Job 取消，不要只改布尔值等重绘。

## 10. 硬件光标

    Box(Modifier.width(20).height(1)) {
        Text("Name: abc")
        Cursor(Modifier.offset(x = 9), shape = CursorShape.Bar)
    }

- 位置来自布局；Row 里放在 Text 后面就是末尾。
- 只在光标能力终端显示；无能力时是 no-op。
- 静态日志存在时行坐标可能偏；优先用 BasicTextField 的反显光标。
- 光标 cell 必须在组合根范围内；超出会隐藏。
- 同时只应有一个 Cursor。

## 11. 静态日志

    val logger = LocalStaticLogger.current
    LaunchedEffect(Unit) {
        logger += "started at " + now()
        logger.log("another line")
    }

- 输出出现在动态区上方，不会被后续帧擦除。
- StaticEffect { ... } 只组合一次；里面不要放 LaunchedEffect。
- 大量日志会占用屏幕顶部；动态区整体下移。

## 12. 页面切换动画

    Crossfade(targetState = page) { current ->
        when (current) {
            Page.Home -> HomeScreen()
            Page.Settings -> SettingsScreen()
        }
    }

- Crossfade 是水平推进（旧页左滑出、新页右滑入），容器会裁剪；需要宽度有界。
- 切回同一个 target 不会重复动画。

    AnimatedVisibility(visible = expanded) {
        Text("details")
    }

- 默认 expandVertically/shrinkVertically，会改变布局尺寸并挤开兄弟。
- 需要滑动用 slideInVertically()/slideOutVertically()；无 fade/scale。

## 13. 文本换行/截断

    Text(
        "很长的中英混排文本 ...",
        modifier = Modifier.width(30),
        softWrap = true,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )

- 单行省略：maxLines = 1 + softWrap = true + Ellipsis。
- 需要硬裁剪而不是省略：外层 clipToBounds()。
- textAlign = Alignment.CenterHorizontally/End 需要对齐宽度 > 行宽。

## 14. 自定义绘制

    Box(
        Modifier.width(10).height(3).drawBehind {
            drawRect('█', foreground = Color.Blue)
            drawText(1, 1, "OK", foreground = Color.White)
            drawRect('─', foreground = Color.Cyan, topLeft = IntOffset(0, 2), size = IntSize(width, 1))
        },
    )

- DrawScope 坐标是节点本地；width/height 是该节点尺寸。
- drawText 是簇级宽度，和 Text 一致。
- 需要包住子内容时实现 DrawModifier + ContentDrawScope.drawContent()。

## 15. 读取终端尺寸与主题

    val terminal = LocalTerminalState.current
    val columns = terminal.size.columns
    val rows = terminal.size.rows
    val theme = terminal.theme     // Unknown / Light / Dark
    val focused = terminal.focused

- 非交互终端默认 80x24；像素宽高可能为 0。
- resize 会更新 size 并触发重组。
- 主题/焦点只在终端声明支持时变化。

## 16. 剪贴板

    val clipboard = LocalClipboard.current
    LaunchedEffect(Unit) {
        clipboard.setText("copied")
        val text = clipboard.getText()   // 2s 超时/被拒为 null
    }

- 读写都是 suspend；getText 会等 OSC 52 回复。
- 终端不支持时写入静默忽略，读取返回 null。
- 测试中用 sendClipboardReply() 和 TestTerminal.clipboardWrites 断言。

## 17. 退出与取消

    // onQuit 的实现：取消承载 runMosaic 的 Job（不是只改一个 boolean 等自己结束）
    "q" -> { onQuit(); true }

最简单的退出仍是 Ctrl-C（未被消费时组合取消）。需要程序化退出时取消承载 runMosaic 的 Job；不要只改状态等进程自己结束——run-until-idle 只会在没有待处理帧/effect 时返回。

## 18. 颜色快速参考

- 终端默认：Color.Unspecified。
- 常用：Color.Black/Red/Green/Blue/Yellow/Magenta/Cyan/White；Color(r, g, b)。
- 样式：TextStyle.Bold/Dim/Italic/Strikethrough/Invert，组合用加号。
- 下划线：UnderlineStyle.Straight/Double/Curly/Dotted/Dashed。
- 能力不足时自动降级到 16/256 色；不要假设 24bit。

## 19. 自定义布局（少用）

    Layout(
        content = { Text("left"); Text("right") },
        modifier = Modifier.fillMaxWidth(),
        measurePolicy = { measurables, constraints ->
            val a = measurables[0].measure(constraints.copy(minWidth = 0))
            val b = measurables[1].measure(constraints.copy(minWidth = 0))
            layout(constraints.maxWidth, maxOf(a.height, b.height)) {
                a.place(0, 0)
                b.place(constraints.maxWidth - b.width, 0)
            }
        },
    )

- 大多数需求用 Box/Row/Column/weight/BoxWithConstraints 就够；自定义布局只在布局算法确实特殊时用。
- 自定义测量要遵守 Constraints，并考虑无界（Infinity）情况。
- 需要 parent data 时用 Modifier.layoutId，在 MeasurePolicy 里通过 parentData 读取。

## 20. 自检清单

- 根有界了吗？没有的话 fill/weight 都不可信。
- 静态界面保活了吗？没有的话只渲染一帧。
- Text 需要换行/截断吗？softWrap + maxLines + overflow。
- 滚动/列表的视口有界吗？
- 交互 opt-in（mouse/paste）开了吗？
- 光标/静态日志同时用了吗？同时用要接受坐标偏移。
- 鼠标坐标是绝对屏幕坐标，点击区域与视觉一致吗？
- 在真实终端里跑过了吗？
