package com.jakewharton.mosaic.testing

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.Mosaic
import com.jakewharton.mosaic.terminal.AnsiLevel
import com.jakewharton.mosaic.terminal.BracketedPasteEvent
import com.jakewharton.mosaic.terminal.ClipboardEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.terminal.MouseEvent
import com.jakewharton.mosaic.terminal.Terminal
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

public suspend fun runMosaicTest(
	capabilities: Terminal.Capabilities = TestTerminal.Capabilities(),
	block: suspend TestMosaic<String>.() -> Unit,
) {
	runMosaicTest(PlainTextSnapshots, capabilities, block)
}

public fun interface SnapshotStrategy<T> {
	public fun create(mosaic: Mosaic): T
}

public object MosaicSnapshots : SnapshotStrategy<Mosaic> {
	override fun create(mosaic: Mosaic): Mosaic = mosaic
}

public suspend fun <T, R> runMosaicTest(
	snapshotStrategy: SnapshotStrategy<T>,
	capabilities: Terminal.Capabilities = TestTerminal.Capabilities(),
	block: suspend TestMosaic<T>.() -> R,
): R {
	val tester = RealTestMosaic(
		coroutineContext = currentCoroutineContext(),
		snapshotStrategy = snapshotStrategy,
		capabilities = capabilities,
	)
	val result = block.invoke(tester)
	tester.cancel()
	return result
}

public interface TestMosaic<T> : Mosaic {
	public fun setContentAndSnapshot(content: @Composable () -> Unit): T
	public suspend fun awaitSnapshot(duration: Duration = 1.seconds): T

	public fun sendKeyEvent(keyEvent: KeyboardEvent)
	public fun sendMouseEvent(mouseEvent: MouseEvent)

	/**
	 * Focuses or blurs the terminal, which drives the composition's lifecycle through
	 * ON_RESUME/ON_PAUSE just as a real focus event would.
	 */
	public fun sendFocusEvent(focused: Boolean)

	/** Sends a bracketed paste (start marker, then one key event per code point, then end marker). */
	public fun sendPaste(text: String)

	/** Delivers an OSC 52 clipboard reply, which satisfies a pending [com.jakewharton.mosaic.ui.platform.Clipboard.getText]. */
	public fun sendClipboardReply(text: String?, selection: ClipboardEvent.Selection = ClipboardEvent.Selection.Clipboard)
	public val state: TestTerminal.State
}

private class RealTestMosaic<T>(
	coroutineContext: CoroutineContext,
	private val snapshotStrategy: SnapshotStrategy<T>,
	capabilities: Terminal.Capabilities,
) : TestMosaic<T> {
	private val testTerminal = TestTerminal(capabilities = capabilities)
	override val state get() = testTerminal.state

	private var timeNanos = 0L
	private val frameDelay = 1.seconds / 60
	private var contentSet = false
	private var hasChanges = false

	private val clock = BroadcastFrameClock()
	val mosaic = Mosaic(
		coroutineContext = coroutineContext + clock,
		onDraw = { hasChanges = true },
		terminal = testTerminal,
	)

	override fun setContent(content: @Composable () -> Unit) {
		contentSet = true
		mosaic.setContent(content)
	}

	override fun setContentAndSnapshot(content: @Composable () -> Unit): T {
		setContent(content)
		check(hasChanges)
		hasChanges = false
		return snapshotStrategy.create(mosaic)
	}

	override suspend fun awaitSnapshot(duration: Duration): T {
		check(contentSet) { "setContent must be called first!" }

		// Await changes, sending at least one frame while we wait.
		withTimeout(duration) {
			while (true) {
				clock.sendFrame(timeNanos)
				if (hasChanges) break

				timeNanos += frameDelay.inWholeNanoseconds
				delay(frameDelay)
			}
		}

		hasChanges = false
		return snapshotStrategy.create(mosaic)
	}

	override fun sendKeyEvent(keyEvent: KeyboardEvent) {
		testTerminal.events.trySend(keyEvent)
	}

	override fun sendMouseEvent(mouseEvent: MouseEvent) {
		testTerminal.events.trySend(mouseEvent)
	}

	override fun sendFocusEvent(focused: Boolean) {
		testTerminal.state.focused.value = focused
	}

	override fun sendPaste(text: String) {
		testTerminal.events.trySend(BracketedPasteEvent(start = true))
		for (char in text.splitIntoCodePoints()) {
			testTerminal.events.trySend(
				KeyboardEvent(
					// The concrete value is unused while accumulating paste text; only [text] matters.
					codepoint = char.first().code,
					text = char,
				),
			)
		}
		testTerminal.events.trySend(BracketedPasteEvent(start = false))
	}

	override fun sendClipboardReply(text: String?, selection: ClipboardEvent.Selection) {
		testTerminal.events.trySend(ClipboardEvent(selection, text))
	}

	/** Splits into UTF-16-safe code point strings so surrogate pairs survive as a single unit. */
	private fun String.splitIntoCodePoints(): List<String> {
		val result = mutableListOf<String>()
		var index = 0
		while (index < length) {
			val first = this[index]
			if (first.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
				result += substring(index, index + 2)
				index += 2
			} else {
				result += first.toString()
				index += 1
			}
		}
		return result
	}

	override fun draw() = mosaic.draw()
	override fun static() = mosaic.static()
	override fun dumpNodes() = mosaic.dumpNodes()

	override suspend fun awaitComplete() = mosaic.awaitComplete()
	override fun cancel() = mosaic.cancel()
}

internal object PlainTextSnapshots : SnapshotStrategy<String> {
	override fun create(mosaic: Mosaic): String {
		return mosaic.draw().render(AnsiLevel.NONE, false)
	}
}
