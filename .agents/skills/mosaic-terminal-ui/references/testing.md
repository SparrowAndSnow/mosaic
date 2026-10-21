# Mosaic 测试指南

写测试前先选层：纯逻辑、节点树、渲染帧、最终屏幕、真实 TTY。层级越低越快，层级越高越接近用户所见。

## 1. 测试分层与位置

| 层 | 工具 | 放哪 | 适合 |
| --- | --- | --- | --- |
| 纯单元 | kotlin.test / assertk | commonTest | 文本切分、布局算法、状态机、编辑器 |
| 节点树 | NodeSnapshots / DumpSnapshots | commonTest | 测量尺寸、节点位置、组合结构 |
| 渲染帧 | RenderingSnapshots + AnsiIncrementalRendering | commonTest | ANSI 序列、差分、光标、静态 |
| 屏幕态 | VirtualTerminal 回放 | commonTest | 宽字符残留、最终画面、端到端 golden |
| 真实 TTY | mosaic-tty TestTerminal（PTY） | mosaic-tty commonTest | 绑定、读写、回调、raw mode |
| 终端实现 | TtyTerminal + TestTerminal | mosaic-tty-terminal commonTest | 探测、事件解析、生命周期 |

多数 UI 行为测试放 mosaic-runtime/src/commonTest/kotlin/com/jakewharton/mosaic/；只适合 JVM 的大文件（官方字素套件等）放 src/jvmTest/。

## 2. 最小快照测试

    @Test fun rendersText() = runTest {
        runMosaicTest {
            setContent {
                Text("hello")
            }
            assertThat(awaitSnapshot()).isEqualTo("hello")
        }
    }

runMosaicTest 的默认 SnapshotStrategy 是 PlainTextSnapshots：对 draw() 结果按 AnsiLevel.NONE 渲染纯文本，适合断言“内容是什么”，不包含 ANSI。

TestMosaic 提供：

    fun setContent(content: @Composable () -> Unit)
    fun setContentAndSnapshot(content: @Composable () -> Unit): T
    suspend fun awaitSnapshot(duration = 1.seconds): T
    fun sendKeyEvent(keyEvent)
    fun sendMouseEvent(mouseEvent)
    fun sendPaste(text)
    fun sendFocusEvent(focused)
    fun sendClipboardReply(text, selection)
    val state: TestTerminal.State

setContentAndSnapshot 用于首帧；awaitSnapshot 等下一次绘制。改状态后再 awaitSnapshot。

## 3. 状态变化与 awaitSnapshot

    var count by mutableIntStateOf(0)
    runMosaicTest {
        setContent { Text("count = " + count) }
        assertThat(awaitSnapshot()).isEqualTo("count = 0")

        count = 1
        assertThat(awaitSnapshot()).isEqualTo("count = 1")
    }

注意：awaitSnapshot 等的是“有绘制发生”，不是任意状态变化。只要没有 requestLayout/requestDraw，它就会等到超时。如果测试里改了无关状态，确保它真的被绘制读取。

## 4. 渲染级断言

RenderingSnapshots、AnsiIncrementalRendering、TestTerminal.Capabilities 都是 internal，在 mosaic-runtime 的 commonTest 可直接用：

    private val caps = TestTerminal.Capabilities(synchronizedOutput = false)

    @Test fun emitsCursor() = runTest {
        runMosaicTest(RenderingSnapshots(AnsiIncrementalRendering(caps))) {
            setContent {
                Box(Modifier.size(10, 3)) {
                    Text("abc")
                    Cursor(Modifier.offset(x = 2, y = 1))
                }
            }
            val frame = awaitSnapshot().toString()
            assertThat(frame).contains("\u001B[2;3H")
        }
    }

- 同一个 runMosaicTest block 里渲染器实例是复用的，跨 snapshot 会走差分；断言“第二帧只包含变化”时正合适。
- 想比较完整帧就新建 renderer，或每帧用新 runMosaicTest。
- ANSI 序列断言容易过拟合；优先断言 VirtualTerminal 的屏幕态。

## 5. 屏幕态断言（推荐做端到端）

VirtualTerminal 解析渲染器发出的 CUP/EL/ED/SGR 等，维护屏幕网格：

    val vt = VirtualTerminal(rows = 3, columns = 10)
    vt.feed(frame)
    assertThat(vt.displayRow(0).trimEnd()).isEqualTo("abc")
    assertThat(vt.cursorRow).isEqualTo(1)
    assertThat(vt.cursorColumn).isEqualTo(2)
    assertThat(vt.hasResidue()).isFalse()

常见 golden 模式：

- settleInto 循环：不断 awaitSnapshot 并 feed，直到屏幕不再变化（或 1s 空闲超时）。
- Screen(rows, residue)：每行 rtrim、去尾部空行，再和期望文本比较。
- 宽字符残留：displayRow 会把孤立续列显示为 ◌；hasResidue() 检测。

参考 EndToEndGoldenTest、CellDiffRenderingTest、FullRedrawNoScrollTest、CursorRenderingTest。

VirtualTerminal 不模拟真实终端滚动：全量重绘末行不能带 CRLF 这类问题要靠专门测试（FullRedrawNoScrollTest）覆盖，不能只靠内存终端。

## 6. 交互测试

    runMosaicTest {
        var clicks = 0
        setContent {
            Box(Modifier.size(5, 2).clickable { clicks++ }) { Text("hit") }
        }
        awaitSnapshot()
        // sendMouseEvent 接收 terminal.MouseEvent（绝对坐标），不是 layout.MouseEvent。
        sendMouseEvent(MouseEvent(x = 1, y = 0, type = MouseEvent.Type.Press, button = MouseEvent.Button.Left))
        sendMouseEvent(MouseEvent(x = 1, y = 0, type = MouseEvent.Type.Release, button = MouseEvent.Button.Left))
        awaitSnapshot()
        assertThat(clicks).isEqualTo(1)
    }

- 键：sendKeyEvent 接收的是 terminal.KeyboardEvent，不是运行时 KeyEvent。文本键：KeyboardEvent('a'.code, text = "a")；特殊键用对应私有区码点（如 57351 = ArrowRight）。底层节点测试也可以直接 root.sendKeyEvent(layout.KeyEvent("ArrowRight"))。
- 粘贴：sendPaste("text") 模拟 bracketed paste 的开始/逐字符/结束。
- 焦点：sendFocusEvent(true/false) 驱动 lifecycle ON_RESUME/ON_PAUSE。
- 剪贴板：sendClipboardReply("text") 满足挂起的 getText()；写入断言用 TestTerminal.clipboardWrites。
- 鼠标坐标是绝对屏幕坐标；点击/悬停建议直接测 clickable/hoverable 行为，而不是手写命中逻辑。

需要保活的交互：runTest 的虚拟时间不驱动真实终端帧；如果逻辑依赖无限 withFrameNanos，使用真实协程调度（Dispatchers.Default）或 Ticker，并用 withTimeout 包住断言。

## 7. 能力矩阵

    runMosaicTestForEach { capabilities ->
        setContent { ... }
        val frame = awaitSnapshot()
        // 对 7 组能力分别断言
    }

CapabilityMatrix.defaults 覆盖 ANSI 16/256/24bit、kitty underline 开关、synchronized output 开关、DEC 2027 开关、全关终端。适合证明“布局/内容不随能力变化”，或验证降级路径。

## 8. TTY 层测试

mosaic-tty 的 TestTerminal 是真实 PTY（Windows 为 console + fake streams）：

    val terminal = TestTerminal.bind()
    val tty = terminal.tty
    tty.enableRawMode()
    terminal.writeTty("hello".encodeToByteArray(), 0, 5)
    val buffer = ByteArray(5)
    tty.read(buffer, 0, 5)
    ...
    tty.close()
    terminal.close()

- writeTty 是“向 TTY 输入”，tty.read 读它；tty.write 是“TTY 输出”，用 readTty/readTtyWithTimeout 读。
- 必须 finally/AfterTest 关闭，否则全局单例会阻止下一个测试。
- 读超时/中断：readWithTimeout(..., 20) 返回 0；interruptRead 唤醒阻塞 read 并返回 0。
- 回调：setCallback 后 enableWindowResizeEvents，resize 会触发 onResize；setCallback(null) 后不再投递。
- Windows 上事件只在 read/readWithTimeout 期间处理，测试要在事件后做一次 write/read roundtrip。
- 高字节（0x00..0xFF）往返用例在 Windows 控制台桩上跳过，因为桩只保留 ASCII。

mosaic-tty-terminal 的测试用 TerminalTester/TtyTerminal + TestTerminal 驱动能力探测与事件解析；参考 TtyTerminalDec2027Test、EventParser*Test、TtyTerminalLifecycleTest。

## 9. 内部测试辅助

mosaic-runtime commonTest 里可用的 internal 工具：

- PlainTextSnapshots：纯文本。
- RenderingSnapshots(rendering)：渲染器输出。
- NodeSnapshots：MosaicNode 根；配合 size/position 扩展。
- DumpSnapshots：dumpNodes 字符串。
- nodeByTestTag(tag)：按 internal testTag 找节点。
- VirtualTerminal、replaceLineEndingsWithCRLF、wrapWithAnsiSynchronizedUpdate 等。
- testTag 是 internal；nodeByTestTag 只在同模块测试里可用。
- MosaicSnapshots：直接拿 Mosaic 实例做 draw()/static()/dumpNodes() 断言。
- TestTerminal（mosaic-testing）：可控 capabilities、事件通道、剪贴板记录。

这些不在公共 API 里；应用代码不要依赖。

## 10. 运行命令

    ./gradlew :mosaic-runtime:jvmTest
    ./gradlew :mosaic-runtime:linuxX64Test --no-configuration-cache
    ./gradlew :mosaic-tty:jvmTest
    ./gradlew :mosaic-tty:linuxX64Test --no-configuration-cache
    ./gradlew :mosaic-tty-terminal:jvmTest
    ./gradlew :mosaic-tty-terminal:linuxX64Test --no-configuration-cache
    ./gradlew :mosaic-animation:jvmTest
    ./gradlew :mosaic-animation:linuxX64Test --no-configuration-cache
    ./gradlew :mosaic-testing:jvmTest
    ./gradlew :samples:showcase:jvmTest
    ./gradlew :samples:showcase:linuxX64Test --no-configuration-cache

单个测试类：

    ./gradlew :mosaic-runtime:jvmTest --tests 'com.jakewharton.mosaic.CursorRenderingTest'

Native 必须带 --no-configuration-cache（klib 位码任务无法序列化）。JDK 22+ 的 FFM 路径：

    ./gradlew :mosaic-tty:installJvmTestDistribution
    JAVA_HOME=<jdk22+> mosaic-tty/build/install/jvmTest/bin/mosaic-tty-test

## 11. 常见测试坑

- 断言整帧字符串太脆弱：优先 screen state；必须断言序列时只断言关键片段。
- 同一测试里 renderer 复用会差分；需要全量帧时新建渲染器或新测试。
- awaitSnapshot 超时通常是“没有绘制”，不是测试框架坏了；检查状态是否真的被 draw 读取。
- 静态内容可能让 awaitComplete 立即返回，掩盖退出路径；用 Ticker/无限帧保活。
- runTest 的 delay 是虚拟时间；真实终端/帧回调不按虚拟时间走。涉及 withFrameNanos 的测试用 TestFrameClock 或真实调度。
- 全局单例：mosaic-tty 同时只能 bind 一个 TestTerminal；每个测试都要 close。
- 不要用真实终端跑单测；PTY 测试已经覆盖绑定/读写/回调。
- 能力开关会影响 ANSI/裁剪/字素行为；跨能力断言用 CapabilityMatrix，不要假设默认全支持。
- 新增 public API 后先 apiDump，再跑测试；否则 apiCheck 会失败。
- Windows 控制台桩与 POSIX PTY 语义不同（事件时机、ASCII 字节），跨平台断言要看 isWindows() 分支。
