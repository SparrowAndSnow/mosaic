package example

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.Mosaic
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.ClipboardEvent
import com.jakewharton.mosaic.terminal.Event
import com.jakewharton.mosaic.terminal.Terminal
import com.jakewharton.mosaic.tty.TestTerminal
import com.jakewharton.mosaic.tty.terminal.EventParser
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToLong
import kotlin.time.TimeSource
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/**
 * Shared benchmark driver; the JVM and native executables run the identical workloads.
 *
 * Render workloads drive a live [Mosaic] composition and time the full recompose + layout + draw
 * pipeline (mirroring production where the renderer first composes the surface and then diffs it).
 */
fun main(args: Array<String>) = runBlocking {
	println("mosaic benchmark")
	if (args.isNotEmpty() && args[0] == "drawidle") {
		benchmarkRenderAndLayoutDrawIdleOnly()
	} else if (args.isNotEmpty() && args[0] == "fullrebuild") {
		benchmarkRenderAndLayoutFullRebuildOnly()
	} else if (args.isNotEmpty() && args[0] == "cjk") {
		benchmarkCjk()
	} else {
		benchmarkRenderAndLayout()
		benchmarkParser()
	}
	println("done")
}

private suspend fun benchmarkRenderAndLayoutFullRebuildOnly() {
	val harness = benchHarness()
	val status = mutableStateOf(0)
	harness.seed { Dashboard(status.value, rebuild = true) }
	benchmark("render-full-rebuild", iterations = 2_000, warmup = 300) {
		status.value++
		harness.driveFrame()
	}
	harness.mosaic.cancel()
}

/** Same load shape as [Dashboard] but real CJK/emoji content to expose the non-ASCII path cost. */
@Composable
private fun DashboardCjk(status: Int, rebuild: Boolean = false) {
	Column {
		Text("「服务器监控」面板 线程32 进程1024 在线 👨‍👩‍👧‍👦")
		for (i in 0 until 30) {
			val value = if (rebuild) i * 7 + status else i * 7
			Text("第${i.toString().padStart(2)}行 指标${value.toString().padStart(4)} ${"▉".repeat(i % 5 + 1)} 状态正常")
		}
		Text("状态=$status CPU=42% 内存=1.2G 温度=38℃")
		Text("── 尾部 ──")
	}
}

private suspend fun benchmarkCjk() {
	val harness = benchHarness()
	val status = mutableStateOf(0)

	harness.seed { DashboardCjk(status.value) }
	benchmark("render-draw-idle-cjk", iterations = 10_000, warmup = 1_000) {
		harness.mosaic.draw()
		harness.mosaic.static()
	}

	harness.seed { DashboardCjk(status.value, rebuild = true) }
	benchmark("render-full-rebuild-cjk", iterations = 2_000, warmup = 300) {
		status.value++
		harness.driveFrame()
	}
	harness.mosaic.cancel()
}

private suspend fun benchmarkRenderAndLayoutDrawIdleOnly() {
	val harness = benchHarness()
	val status = mutableStateOf(0)
	harness.seed { Dashboard(status.value) }
	benchmark("render-draw-idle", iterations = 10_000, warmup = 1_000) {
		harness.mosaic.draw()
		harness.mosaic.static()
	}
	harness.mosaic.cancel()
}

/** Fake terminal with realistic capabilities so rendering behaves like production. */
private class BenchTerminal : Terminal {
	override val name: String? = "bench"
	override val interactive: Boolean = true
	override val capabilities: Terminal.Capabilities = BenchCapabilities
	override val state: Terminal.State = BenchState
	override val events: Channel<Event> = Channel(Channel.UNLIMITED)
	override fun close() {}
	override fun requestClipboardRead(selection: ClipboardEvent.Selection) {}
	override fun writeClipboard(text: String, selection: ClipboardEvent.Selection) {}
}

private object BenchState : Terminal.State {
	override val focused = MutableStateFlow(true)
	override val theme = MutableStateFlow(Terminal.Theme.Dark)
	override val size = MutableStateFlow(Terminal.Size(120, 40, 0, 0))
}

private object BenchCapabilities : Terminal.Capabilities {
	override val ansiLevel = AnsiLevel.TRUECOLOR
	override val cursorVisibility = true
	override val focusEvents = true
	override val inBandResizeEvents = true
	override val kittyGraphics = true
	override val kittyKeyboard = true
	override val kittyNotifications = true
	override val kittyPointerShape = true
	override val kittyTextSizingScale = true
	override val kittyTextSizingWidth = true
	override val kittyUnderline = true
	override val graphemeClustering = true
	override val synchronizedOutput = true
	override val themeEvents = true
}

private class BooleanFlag {
	var set = false
}

/**
 * A live [Mosaic] composition whose onDraw mirrors the first half of production rendering
 * (composing the surface), plus the shared frame clock. [drive] applies one frame.
 */
private class BenchHarness(
	val clock: BroadcastFrameClock,
	val mosaic: Mosaic,
	val drawFlag: BooleanFlag,
) {
	private var timeNanos = 0L

	/** Sends frames until the pending recomposition was applied (layout + draw). */
	suspend fun driveFrame() {
		var guard = 0
		while (!drawFlag.set) {
			if (++guard > 1_000) throw IllegalStateException("frame never applied")
			clock.sendFrame(timeNanos)
			timeNanos += 16_666_667L
			yield()
		}
		drawFlag.set = false
	}

	/** Fully applies [content] once and draws it as the previous frame. */
	suspend fun seed(content: @Composable () -> Unit) {
		mosaic.setContent(content)
		driveFrame()
	}
}

private suspend fun benchHarness(): BenchHarness {
	val terminal = BenchTerminal()
	val drawFlag = BooleanFlag()
	val clock = BroadcastFrameClock()
	val mosaic = Mosaic(
		coroutineContext = coroutineContext + clock,
		onDraw = { mosaic ->
			// Mirror production: the renderer first composes the surface (measure + place + draw)
			// and collects static output before diffing against the previous frame.
			drawFlag.set = true
			mosaic.draw()
			mosaic.static()
		},
		terminal = terminal,
	)
	return BenchHarness(clock, mosaic, drawFlag)
}

/** A realistic 120x40 dashboard: header, 30 data rows, one mutating status row, footer. */
@Composable
private fun Dashboard(status: Int, rebuild: Boolean = false) {
	Column {
		Text("Mosaic benchmark  rows=30 cols=120")
		for (i in 0 until 30) {
			val value = if (rebuild) i * 7 + status else i * 7
			Text("row ${i.toString().padStart(2)} value ${value.toString().padStart(4)} ${"·".repeat(i % 5 + 1)}")
		}
		Text("status=$status cpu=42% mem=1.2G")
		Text("footer")
	}
}

private suspend fun benchmarkRenderAndLayout() {
	val harness = benchHarness()
	val status = mutableStateOf(0)

	// Idle tree, no frames: isolates the layout + draw core (no recompose/snapshot bookkeeping).
	harness.seed { Dashboard(status.value) }
	benchmark("render-draw-idle", iterations = 10_000, warmup = 1_000) {
		harness.mosaic.draw()
		harness.mosaic.static()
	}

	// Single status row changes per frame; times the light update path (行级增量).
	benchmark("render-one-row-delta", iterations = 2_000, warmup = 300) {
		status.value++
		harness.driveFrame()
	}

	// Every data row changes per frame; times the full-redraw path.
	harness.seed { Dashboard(status.value, rebuild = true) }
	benchmark("render-full-rebuild", iterations = 2_000, warmup = 300) {
		status.value++
		harness.driveFrame()
	}

	// Switching tree shape forces recompose + measureAndPlace every frame.
	val condensed = mutableStateOf(false)
	harness.seed {
		if (condensed.value) Text("condensed") else Dashboard(status.value)
	}
	benchmark("layout-pass", iterations = 1_000, warmup = 200) {
		condensed.value = !condensed.value
		harness.driveFrame()
	}

	harness.mosaic.cancel()
}

private suspend fun benchmarkParser() {
	// A deterministic stream of plain characters: every byte is exactly one KeyboardEvent, so the
	// event count is known by construction and next() never blocks waiting for more input. The
	// tty read feeds the parser through the same real PTY path as production.
	TestTerminal.bind().use { testTerminal ->
		// The freshly-bound PTY is in canonical mode where read() blocks until a newline, so put
		// it into raw mode (like a real TtyTerminal does) before parsing.
		testTerminal.tty.enableRawMode()
		val parser = EventParser(testTerminal.tty)
		val alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
			.encodeToByteArray()
		val batch = ByteArray(alphabet.size * 20) // 720 events, larger than the parser buffer.
		var offset = 0
		while (offset < batch.size) {
			alphabet.copyInto(batch, offset)
			offset += alphabet.size
		}
		val eventCount = batch.size // One KeyboardEvent per byte.

		testTerminal.writeTty(batch, 0, batch.size)
		repeat(eventCount) { parser.next() } // Warm up outside the measured region.

		benchmark("event-parser", iterations = 30, warmup = 5) {
			testTerminal.writeTty(batch, 0, batch.size)
			repeat(eventCount) { parser.next() }
			eventCount
		}
	}
}

/**
 * Warms up, then reports best-of-races per-op cost in ns/op. For frame workloads the time covers
 * the full recompose + layout + draw pipeline of a live composition.
 */
private suspend fun benchmark(name: String, iterations: Int, warmup: Int, block: suspend () -> Any? = {}) {
	repeat(warmup) { block() }

	var best = Long.MAX_VALUE
	val rounds = 3
	repeat(rounds) {
		yield() // Drain pending work so each round starts from an idle event loop.
		val start = TimeSource.Monotonic.markNow()
		repeat(iterations) { block() }
		val elapsed = start.elapsedNow().inWholeNanoseconds
		if (elapsed < best) best = elapsed
	}
	val perOp = best.toDouble() / iterations
	printBenchResult(name, perOp)
}

private fun printBenchResult(name: String, perOpNanos: Double) {
	val rounded = perOpNanos.roundToLong()
	val digits = rounded.toString()
	val grouped = StringBuilder(digits.length + digits.length / 3)
	for (i in digits.indices) {
		if (i > 0 && (digits.length - i) % 3 == 0) grouped.append(',')
		grouped.append(digits[i])
	}
	println("$name: $grouped ns/op")
}
