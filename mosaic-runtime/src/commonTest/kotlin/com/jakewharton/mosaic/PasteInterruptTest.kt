package com.jakewharton.mosaic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import com.jakewharton.mosaic.terminal.BracketedPasteEvent
import com.jakewharton.mosaic.terminal.KeyboardEvent
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

/**
 * Locks in the bracketed-paste escape hatch: while a paste has begun but never ends (the terminal
 * sent the start marker but no end marker), Ctrl-C must still tear down the composition instead of
 * being swallowed as paste content. A ticker keeps the composition alive so a natural idle shutdown
 * is not mistaken for the Ctrl-C exit, and ordinary paste characters never trigger the exit.
 */
class PasteInterruptTest {

	private fun testTerminal() = TestTerminal()

	private fun silentWriter() = FrameWriter(writeBytes = { _, _, _ -> }, flushBytes = {})

	@Test fun ctrlCInterruptsUnterminatedPaste() = runBlocking {
		val terminal = testTerminal()
		val exited = CompletableDeferred<Unit>()
		val composition = launch {
			runMosaicComposition(
				terminal = terminal,
				rendering = AnsiIncrementalRendering(terminal.capabilities),
				frameWriter = silentWriter(),
				content = { Ticker(); Text("paste") },
			)
			exited.complete(Unit)
		}

		yield()
		delay(50.milliseconds) // Let the first frames apply and the ticker run.
		terminal.events.trySend(BracketedPasteEvent(start = true))
		terminal.events.trySend(KeyboardEvent(codepoint = 'a'.code, text = "a"))
		delay(50.milliseconds) // Let the frame loop see the unterminated paste.
		terminal.events.trySend(
			KeyboardEvent(codepoint = 'c'.code, text = "c", modifiers = KeyboardEvent.ModifierCtrl),
		)

		withTimeout(3.seconds) { exited.await() } // Fails (times out) if Ctrl-C was swallowed.
		composition.join()
	}

	@Test fun ordinaryPasteCharactersDoNotExit() = runBlocking {
		val terminal = testTerminal()
		val exited = CompletableDeferred<Unit>()
		val composition = launch {
			runMosaicComposition(
				terminal = terminal,
				rendering = AnsiIncrementalRendering(terminal.capabilities),
				frameWriter = silentWriter(),
				content = { Ticker(); Text("paste") },
			)
			exited.complete(Unit)
		}

		yield()
		delay(50.milliseconds)
		terminal.events.trySend(BracketedPasteEvent(start = true))
		terminal.events.trySend(KeyboardEvent(codepoint = 'a'.code, text = "a"))
		terminal.events.trySend(KeyboardEvent(codepoint = 'b'.code, text = "b"))

		// Plain paste content must keep accumulating and never force an exit.
		assertNull(withTimeoutOrNull(400.milliseconds) { exited.await() })
		composition.cancel()
		composition.join()
	}
}

/** Keeps the composition alive so the tests cannot be satisfied by a natural idle shutdown. */
@Composable
private fun Ticker() {
	LaunchedEffect(Unit) {
		while (true) {
			withFrameNanos { }
		}
	}
}
