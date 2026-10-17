package com.jakewharton.mosaic

import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.testing.TestTerminal
import com.jakewharton.mosaic.ui.Text
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Locks in the fatal-error path of the frame loop: a failure in recomposition/layout/draw writes a
 * visible plain-text error frame and still propagates (fail-fast, so the tty use-block restores the
 * terminal), while a normal cancellation writes no error frame.
 */
class FrameErrorVisibilityTest {

	@Test fun fatalErrorWritesErrorFrameAndPropagates() = runBlocking {
		val terminal = TestTerminal()
		val output = ByteCollector()
		val writer = FrameWriter(writeBytes = output::appendBytes, flushBytes = {})

		var propagated: Throwable? = null
		try {
			runMosaicComposition(
				terminal = terminal,
				rendering = AnsiIncrementalRendering(terminal.capabilities),
				content = {
					Text("ok")
					Text("boom", modifier = Modifier.drawBehind { error("kaboom") })
				},
				frameWriter = writer,
			)
		} catch (t: Throwable) {
			propagated = t
		}

		val text = output.text
		assertNotNull(propagated, "the original failure must propagate, not be swallowed")
		assertTrue(propagated.message?.contains("kaboom") == true)
		assertContains(text, "Mosaic error")
		assertContains(text, "kaboom")
		assertContains(text, "NODES")
	}

	@Test fun normalCancellationWritesNoErrorFrame() = runBlocking {
		val terminal = TestTerminal()
		val output = ByteCollector()
		val writer = FrameWriter(writeBytes = output::appendBytes, flushBytes = {})

		val composition = launch {
			runMosaicComposition(
				terminal = terminal,
				rendering = AnsiIncrementalRendering(terminal.capabilities),
				frameWriter = writer,
				content = { Text("steady") },
			)
		}
		delay(300)
		composition.cancel()
		composition.join() // Cancellation is normal shutdown, not a fatal error.

		val text = output.text
		assertTrue("Mosaic error" !in text, "cancellation is not a fatal error:\n$text")
	}
}

/** Accumulates the byte stream a [FrameWriter] would have written to the terminal. */
private class ByteCollector {
	private val bytes = ArrayList<Byte>()

	fun appendBytes(buffer: ByteArray, offset: Int, count: Int) {
		for (i in offset until offset + count) {
			bytes += buffer[i]
		}
	}

	val text: String get() = ByteArray(bytes.size) { bytes[it] }.decodeToString()
}
